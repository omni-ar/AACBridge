package com.aacbridge.daemon

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.location.Location
import android.location.LocationManager
import com.aacbridge.cache.KVCacheManager
import com.aacbridge.cache.StateRepository
import com.aacbridge.router.*
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class ActiveSweep(
    private val locationManager: LocationManager,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val stateRouter: StateRouter,
    private val cacheManager: KVCacheManager,
    private val repository: StateRepository
) {

    companion object {
        private const val BLE_SCAN_WINDOW_MS = 3000L
    }

    suspend fun executeSweep() = coroutineScope {

        val locationDeferred =
            async {
                fetchLastKnownLocation()
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

        val availableStates =
            repository.getAllContextStates()

        val topStateIds =
            stateRouter.getTopContextIds(
                snapshot = snapshot,
                states = availableStates,
                limit = HardwareConfig.MAX_ACTIVE_KV_STATES
            )

        if (topStateIds.isNotEmpty()) {
            cacheManager.loadTopStates(topStateIds)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastKnownLocation(): GpsLocation? {

        return try {

            val providers =
                locationManager.getProviders(true)

            var bestLocation: Location? = null

            for (provider in providers) {

                val location =
                    locationManager
                        .getLastKnownLocation(provider)
                        ?: continue

                if (
                    bestLocation == null ||
                    location.accuracy < bestLocation.accuracy
                ) {
                    bestLocation = location
                }
            }

            bestLocation?.let {

                GpsLocation(
                    lat = it.latitude,
                    lng = it.longitude,
                    accuracyMeters = it.accuracy
                )
            }

        } catch (e: Exception) {
            null
        }
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