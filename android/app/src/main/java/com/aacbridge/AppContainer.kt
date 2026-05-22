package com.aacbridge

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import com.aacbridge.cache.CacheMutexRegistry
import com.aacbridge.cache.InMemoryStateRepository
import com.aacbridge.cache.KVCacheManager
import com.aacbridge.daemon.ActiveSweep
import com.aacbridge.inference.LlamaBridge
import com.aacbridge.router.BLEScorer
import com.aacbridge.router.GPSScorer
import com.aacbridge.router.StateRouter
import com.aacbridge.router.TimeScorer

/**
 * Lightweight manual dependency injection container.
 *
 * IMPORTANT:
 * This intentionally avoids:
 * - Hilt
 * - Dagger
 * - Koin
 *
 * Reason:
 * Startup latency and memory overhead must remain minimal
 * for edge-device AAC execution.
 */
class AppContainer(
    private val application: AACBridgeApplication
) {

    /**
     * Android system services.
     */
    private val locationManager =
        application.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    private val bluetoothManager =
        application.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    /**
     * Repository layer.
     *
     * Temporary in-memory implementation until
     * Room persistence layer is finalized.
     */
    val repository =
        InMemoryStateRepository()

    /**
     * Router scorers.
     */
    private val timeScorer =
        TimeScorer()

    private val gpsScorer =
        GPSScorer()

    private val bleScorer =
        BLEScorer()

    /**
     * Core routing engine.
     */
    val stateRouter =
        StateRouter(
            timeScorer = timeScorer,
            gpsScorer = gpsScorer,
            bleScorer = bleScorer
        )

    /**
     * Per-state lock registry.
     */
    private val mutexRegistry =
        CacheMutexRegistry()

    /**
     * Native KV cache orchestration layer.
     */
    val kvCacheManager =
        KVCacheManager(
            repository = repository,
            mutexRegistry = mutexRegistry,
            jniBridge = LlamaBridge
        )

    /**
     * Background context acquisition sweep.
     */
    val activeSweep =
        ActiveSweep(
            locationManager = locationManager,
            bluetoothAdapter = bluetoothManager.adapter,
            stateRouter = stateRouter,
            cacheManager = kvCacheManager,
            repository = repository
        )
}