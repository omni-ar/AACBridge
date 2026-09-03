package com.aacbridge.daemon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aacbridge.AACBridgeApplication
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Android orchestration layer around
 * the existing ActiveSweep implementation.
 *
 * Pipeline Orchestration:
 * ContextDaemon -> ActiveSweep -> StateRouter -> KVCacheManager
 *
 * Foreground Service Justification:
 * API 34+ strict background execution limits require a foreground
 * service to ensure the daemon can proactively poll location/BLE.
 *
 * Battery/Thermal Trade-off:
 * A 60-second sweep interval (SWEEP_INTERVAL_MS) balances context
 * freshness against thermal and battery budgets.
 * 
 * IMPORTANT:
 * This service does NOT interact with LlamaBridge, inference pipelines,
 * or KV cache serialization directly.
 * It strictly schedules ActiveSweep to gather context.
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
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "AACBridgeDaemon"

        /**
         * When true, periodic sweeps are skipped.
         *
         * Set by LatencyProfiler during benchmark
         * execution to prevent engine lock contention
         * and thermal interference.
         *
         * The daemon service remains alive — only
         * the sweep payload is suppressed.
         */
        val isSweepPaused = AtomicBoolean(false)
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
        startAsForegroundService()
        startPeriodicSweep()
        return START_STICKY
    }

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AAC Context Daemon",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AAC Context Sweep")
            .setContentText("Predictive routing is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
                    if (isSweepPaused.get()) {
                        Log.d(TAG, "Sweep paused (benchmark active)")
                    } else {
                        val app = application as? AACBridgeApplication
                        if (app != null) {
                            Log.d(TAG, "Executing periodic context sweep...")
                            app.appContainer.activeSweep.executeSweep()
                            Log.d(TAG, "Context sweep completed")
                        }
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
