package com.aacbridge.daemon

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import com.aacbridge.cache.KVCacheManager
import com.aacbridge.cache.StateRepository
import com.aacbridge.router.*
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class ActiveSweep(
    private val locationSource: LocationSource,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val stateRouter: StateRouter,
    private val cacheManager: KVCacheManager,
    private val repository: StateRepository,
    private val latestRanking: LatestRanking
) {

    companion object {
        private const val TAG = "ActiveSweep"
        private const val BLE_SCAN_WINDOW_MS = 3000L
    }

    suspend fun executeSweep() = coroutineScope {

        val locationDeferred =
            async {
                locationSource.acquire()
            }

        val bleDeferred =
            async {
                fetchBleTopology()
            }

        val location =
            locationDeferred.await()

        val bleDevices =
            bleDeferred.await()

        val calendar =
            Calendar.getInstance()

        val currentHourDecimal =
            calendar.get(Calendar.HOUR_OF_DAY) +
                    (calendar.get(Calendar.MINUTE) / 60.0)

        val snapshot =
            SensorSnapshot(
                currentHourDecimal = currentHourDecimal,
                location = location,
                detectedBleDevices = bleDevices
            )

        val ranked =
            stateRouter.getScoredStates(
                snapshot = snapshot,
                states = repository.getAllContextStates()
            )

        latestRanking.publish(ranked)

        /*
         * Hysteresis-gated replacement (ResidencyPolicy).
         * Previously this loaded the raw top-k every 60 s,
         * so the margin applied only in the 15-minute
         * DriftDetector and noisy sweeps could churn the
         * cache freely in between.
         */
        val plan =
            if (ranked.isNotEmpty()) cacheManager.updateResidency(ranked)
            else ResidencyPolicy.Plan(emptyList(), emptyList())

        // Structured line for on-device trace collection.
        Log.i(
            TAG,
            "SWEEP,hour=%.3f,gps=%s,gps_acc=%s,ble_n=%d,top1=%s,top1_score=%.4f,loaded=%s,evicted=%s,resident=%s".format(
                Locale.US,
                currentHourDecimal,
                location != null,
                location?.accuracyMeters?.toString() ?: "",
                bleDevices.size,
                ranked.firstOrNull()?.first ?: "",
                ranked.firstOrNull()?.second ?: 0.0,
                plan.toLoad.joinToString("|"),
                plan.toEvict.joinToString("|"),
                cacheManager.residentStateIds().sorted().joinToString("|")
            )
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBleTopology(): Map<String, Rssi> {

        val scanner =
            bluetoothAdapter?.bluetoothLeScanner
                ?: return emptyMap()

        val detected =
            ConcurrentHashMap<String, Rssi>()

        val callback =
            object : ScanCallback() {

                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult?
                ) {

                    result?.device?.address?.let { mac ->

                        val rssiValue =
                            result.rssi

                        if (rssiValue <= 0) {
                            detected[mac] =
                                Rssi(rssiValue)
                        }
                    }
                }
            }

        try {

            withTimeoutOrNull(BLE_SCAN_WINDOW_MS) {

                suspendCancellableCoroutine<Unit> { continuation ->

                    continuation.invokeOnCancellation {

                        try {
                            scanner.stopScan(callback)
                        } catch (_: Exception) {
                        }
                    }

                    try {

                        scanner.startScan(callback)

                    } catch (_: Exception) {

                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }

        } finally {

            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
        }

        return detected.toMap()
    }
}
