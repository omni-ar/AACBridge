package com.aacbridge.daemon

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import com.aacbridge.router.GpsLocation
import com.aacbridge.router.LocationPolicy
import com.aacbridge.router.LocationValidator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Location acquisition shared by ActiveSweep and
 * DriftDetector.
 *
 * Why this exists:
 * Both components previously read only
 * getLastKnownLocation(). Android refreshes that cache
 * only when some app actively requests location, so on an
 * otherwise idle device it is usually minutes or hours
 * old, and LocationValidator's staleness limit rejected
 * it on nearly every sweep (GPS silently absent).
 *
 * Strategy:
 * 1. Validate every provider's last-known fix and use the
 *    most accurate fix that passes. Choosing first and
 *    validating second let one rejected fix (e.g. a mock
 *    fix claiming 1 m) hide a valid fix from another
 *    provider.
 * 2. If none passes, request one fresh fix, preferring
 *    NETWORK_PROVIDER (Wi-Fi/cell, low power, sufficient at
 *    the router's ~100 m scale) over GPS_PROVIDER, bounded
 *    by [freshFixTimeoutMs].
 */
class LocationSource(
    private val locationManager: LocationManager,
    private val validator: LocationValidator = LocationValidator(),
    private val freshFixTimeoutMs: Long = FRESH_FIX_TIMEOUT_MS
) {

    companion object {
        private const val TAG = "LocationSource"

        /** Upper bound on one fresh-fix request. */
        const val FRESH_FIX_TIMEOUT_MS = 10_000L
    }

    @SuppressLint("MissingPermission")
    suspend fun acquire(): GpsLocation? {

        return try {
            bestValidLastKnown() ?: requestFreshFix()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Location acquisition failed", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestValidLastKnown(): GpsLocation? {

        val accepted = mutableListOf<GpsLocation>()

        for (provider in locationManager.getProviders(true)) {

            val fix = locationManager.getLastKnownLocation(provider) ?: continue

            when (val decision = validator.evaluate(fix)) {
                is LocationPolicy.Decision.Accepted -> accepted += decision.location
                is LocationPolicy.Decision.Rejected ->
                    Log.d(TAG, "last-known[$provider] rejected: ${decision.reason}")
            }
        }

        return accepted.minByOrNull { it.accuracyMeters }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshFix(): GpsLocation? {

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> return null
        }

        val fix = withTimeoutOrNull(freshFixTimeoutMs) {
            requestSingleFix(provider)
        } ?: return null

        return when (val decision = validator.evaluate(fix)) {
            is LocationPolicy.Decision.Accepted -> decision.location
            is LocationPolicy.Decision.Rejected -> {
                Log.d(TAG, "fresh[$provider] rejected: ${decision.reason}")
                null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(provider: String): Location? =
        suspendCancellableCoroutine { cont ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }

                val direct = Executor { it.run() }

                locationManager.getCurrentLocation(provider, signal, direct) { location ->
                    if (cont.isActive) cont.resume(location)
                }

            } else {

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }

                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }

                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
}
