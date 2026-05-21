package com.aacbridge.router

/**
 * Immutable live sensor snapshot consumed by StateRouter.
 *
 * Represents the current environmental context observed
 * by the device at routing time.
 *
 * IMPORTANT:
 * This is intentionally a pure data container:
 * - immutable
 * - Android-independent
 * - thread-safe
 *
 * No Android framework types are allowed here.
 */
data class SensorSnapshot(

    /**
     * Current local time in decimal hours.
     *
     * Example:
     * 13.5 = 1:30 PM
     */
    val currentHourDecimal: Double,

    /**
     * Current GPS location.
     *
     * Nullable because:
     * - indoor GPS may fail
     * - permissions may be denied
     * - location subsystem may temporarily stall
     */
    val location: GpsLocation?,

    /**
     * Currently visible BLE devices mapped
     * to their RSSI readings.
     */
    val detectedBleDevices: Map<String, Rssi>
)