# Cold Start Recovery & Cache Lifecycle Flow

This document details the recovery mechanisms and verified KV cache lifecycles implemented in Phase 2 to ensure the KV cache is consistently populated, serialized, and safely restored.

## The Problem
Standard geofencing APIs only fire when the device physically crosses a geographic boundary. If the AAC device is rebooted *while already inside* a known geofence, the API will never trigger an entry event, leaving the KV cache completely unprimed. Furthermore, if a precomputed cache is not explicitly serialized to the filesystem, cold starts have no historical state to load.

## The Boot Receiver Trigger
Implemented in `BootReceiver.kt`, the system intercepts `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` Android broadcasts. Because BroadcastReceivers are ruthlessly killed by the OS, the flow utilizes `goAsync()` to grant the application time to wake the `ContextDaemon`.

## Resident State Handling & Lifecycle
The core lifecycle is formally verified and implemented via `ContextPrimerImpl`. The verified state pipeline is:

1. **`prime`:** The daemon actively sweeps the environment (GPS, BLE, Time), scores the current states via `StateRouter`, and determines the top-3 candidate states. For missing states, it loads the LLM and pre-computes the KV cache in native memory.
2. **`saveKVCache`:** Crucially, immediately after priming, the JNI boundary `saveKVCache` function is explicitly invoked.
3. **`.bin` Generation:** The native `llama.cpp` sequence memory is serialized directly to a binary file (`*.bin`) on the Android filesystem.
4. **`loadKVCache`:** On application restart or subsequent context shifts, the resident state is immediately available. `loadKVCache` deserializes the `.bin` file directly back into native memory.

This guarantees that the active context survives application death and memory pressure.

## The Active Sweep
To maximize speed and minimize cold-start TTFT impact, `ActiveSweep.kt` acquires hardware signals concurrently.
1. **GPS:** Uses `locationManager.getProviders(true)` and `getLastKnownLocation()`.
2. **BLE:** Initiates a strict `3000ms` BLE scan utilizing `withTimeoutOrNull`.

The data is bundled into a `SensorSnapshot`, ranked by the `StateRouter`, and dispatched to the `KVCacheManager` for native preloading.
