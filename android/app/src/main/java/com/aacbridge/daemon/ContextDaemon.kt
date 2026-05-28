package com.aacbridge.daemon

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aacbridge.AACBridgeApplication
import kotlinx.coroutines.*

/**
 * Lightweight Android orchestration layer around
 * the existing ActiveSweep implementation.
 *
 * IMPORTANT:
 * This service does NOT duplicate GPS/BLE logic.
 * ActiveSweep already owns context acquisition.
 *
 * ContextDaemon only orchestrates the execution
 * lifecycle of the existing backend pipeline:
 *
 * ContextDaemon
 * -> ActiveSweep
 * -> StateRouter
 * -> KVCacheManager
 *
 * Responsibilities:
 * - lifecycle-safe coroutine orchestration
 * - periodic context sweeps via ActiveSweep
 * - safe cancellation handling
 * - low-memory-safe execution
 * - battery-conscious scheduling
 */
class ContextDaemon : Service() {

    companion object {
        private const val TAG = "ContextDaemon"

        /**
         * Sweep interval in milliseconds.
         *
         * 60 seconds balances battery consumption
         * against context freshness for AAC usage.
         */
        private const val SWEEP_INTERVAL_MS = 60_000L
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var sweepJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ContextDaemon created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ContextDaemon started")
        startPeriodicSweep()
        return START_STICKY
    }

    /**
     * Launches periodic ActiveSweep execution.
     *
     * Each sweep delegates entirely to the existing
     * ActiveSweep implementation which handles:
     * - GPS acquisition
     * - BLE scanning
     * - SensorSnapshot construction
     * - StateRouter scoring
     * - KVCacheManager loading
     */
    private fun startPeriodicSweep() {
        if (sweepJob?.isActive == true) return

        sweepJob = serviceScope.launch {
            while (isActive) {
                try {
                    val app = application as? AACBridgeApplication
                    if (app != null) {
                        Log.d(TAG, "Executing periodic context sweep...")
                        app.appContainer.activeSweep.executeSweep()
                        Log.d(TAG, "Context sweep completed")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Context sweep failed", e)
                }
                delay(SWEEP_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory — pausing sweep")
        sweepJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "ContextDaemon destroyed")
    }
}
