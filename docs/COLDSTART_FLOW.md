# Cold Start Recovery Flow

This document details the recovery mechanisms implemented in Phase 2 to ensure the KV cache is populated even if the background daemon fails to wake via standard geofencing triggers.

## The Problem
Standard geofencing APIs only fire when the device physically crosses a geographic boundary. If the AAC device is rebooted *while already inside* a known geofence (e.g., the user wakes up and turns on the tablet in their bedroom), the API will never trigger an entry event, leaving the KV cache completely unprimed.

## The Boot Receiver Trigger
Implemented in `BootReceiver.kt`, the system intercepts the `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` Android broadcasts.

Because BroadcastReceivers have no lifecycle and are ruthlessly killed by the Android OS after `onReceive` returns, the flow utilizes `goAsync()`. This tells the OS to keep the process alive for up to 10 seconds. The heavy lifting is delegated to a `GlobalScope.launch(Dispatchers.IO)` coroutine block, with a guaranteed `pendingResult.finish()` in the `finally` block to prevent ANRs.

## The Active Sweep
The core logic resides in `ActiveSweep.kt`. To maximize speed and minimize the cold-start TTFT impact, the system acquires hardware signals concurrently.

### Parallel Acquisition
The sweep launches two parallel `async` coroutines:
1. **GPS:** Uses `locationManager.getProviders(true)` and `getLastKnownLocation()` to iterate over all active hardware providers, explicitly selecting the one with the best accuracy.
2. **BLE:** Initiates a strict `3000ms` BLE scan utilizing `withTimeoutOrNull` wrapping a `suspendCancellableCoroutine`.

**Total Latency:** Because they run concurrently, the total hardware acquisition latency is $\max(\text{GPS\_time}, \text{3s})$, rather than $\text{GPS\_time} + \text{3s}$.

### Safe Cancellation
The BLE coroutine uses `continuation.invokeOnCancellation` to guarantee that `scanner.stopScan()` is always called, preventing detached coroutine leaks or left-open BLE radios even if the OS aggressively kills the background sweep.

## Orchestration
Once the concurrent acquisitions await completion, the data is bundled into a `SensorSnapshot`. This snapshot is passed synchronously into the `StateRouter` to rank the contexts, and the top-3 resulting `stateId`s are instantly dispatched to the `KVCacheManager` for native preloading.
