package com.aacbridge.daemon

import android.content.Context
import android.location.LocationManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aacbridge.AACBridgeApplication
import com.aacbridge.router.HardwareConfig
import com.aacbridge.router.SensorSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager daemon for semantic
 * environment drift tracking.
 *
 * Polling frequency:
 * 15 minutes.
 *
 * Applies the shared hysteresis rule
 * (ResidencyPolicy, HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN)
 * to the best BLE-blind candidate.
 */
class DriftDetector(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {

        private const val WORK_NAME =
            "AAC_Drift_Detector"

        /**
         * Registers periodic drift polling.
         *
         * Call once from:
         * AACBridgeApplication.onCreate()
         */
        fun schedule(
            context: Context
        ) {

            val request =
                PeriodicWorkRequestBuilder<DriftDetector>(
                    15,
                    TimeUnit.MINUTES
                ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    /**
     * Executes periodic drift evaluation.
     *
     * METHODOLOGY NOTE:
     *
     * This evaluation intentionally uses a lightweight
     * BLE-blind snapshot to protect battery during
     * 15-minute periodic polling.
     *
     * Consequently:
     * - absolute context scores may be temporarily lower
     * - BLE-rich contexts may be underestimated
     *
     * However:
     * both candidate and resident states are evaluated
     * against the SAME BLE-blind snapshot.
     *
     * Therefore:
     * hysteresis score deltas remain mathematically valid.
     */
    override suspend fun doWork(): Result {

        return withContext(Dispatchers.IO) {

            // -----------------------------------------
            // 1. Dependency resolution
            // -----------------------------------------

            val app =
                applicationContext
                        as? AACBridgeApplication
                    ?: return@withContext Result.failure()

            val container =
                app.appContainer

            val router =
                container.stateRouter

            val cacheManager =
                container.kvCacheManager

            val repository =
                container.repository

            val locationManager =
                applicationContext.getSystemService(
                    Context.LOCATION_SERVICE
                ) as LocationManager

            // -----------------------------------------
            // 2. Lightweight snapshot acquisition
            // -----------------------------------------

            val location =
                LocationSource(locationManager).acquire()

            val calendar =
                Calendar.getInstance()

            val currentHourDecimal =
                calendar.get(Calendar.HOUR_OF_DAY) +
                        (calendar.get(Calendar.MINUTE) / 60.0)

            val snapshot =
                SensorSnapshot(
                    currentHourDecimal = currentHourDecimal,
                    location = location,

                    // intentional BLE bypass
                    detectedBleDevices = emptyMap()
                )

            // -----------------------------------------
            // 3. Score all states
            // -----------------------------------------

            val availableStates =
                repository.getAllContextStates()

            val scoredStates =
                router.getScoredStates(
                    snapshot = snapshot,
                    states = availableStates
                )

            val bestCandidate =
                scoredStates.firstOrNull()
                    ?: return@withContext Result.success()

            // -----------------------------------------
            // 4. Abort if already resident
            // -----------------------------------------

            if (
                cacheManager.isStateResident(
                    bestCandidate.first
                )
            ) {
                return@withContext Result.success()
            }

            // -----------------------------------------
            // 5. Hysteresis-gated replacement
            // -----------------------------------------
            //
            // Same rule as ActiveSweep (ResidencyPolicy),
            // restricted to the single best candidate: the
            // BLE-blind snapshot is only trusted to add the
            // top state, not to reshuffle the whole set.

            cacheManager.updateResidency(
                ranked = scoredStates,
                margin = HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN,
                maxCandidates = 1
            )

            Result.success()
        }
    }
}