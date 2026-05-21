package com.aacbridge.router

/**
 * Immutable semantic context state used for predictive
 * KV cache routing.
 *
 * Represents a historically learned environment pattern:
 * - home morning
 * - hospital ward
 * - caregiver present
 * - kitchen evening
 * etc.
 *
 * Each state contains:
 * - temporal anchors
 * - spatial anchors
 * - BLE topology anchors
 *
 * StateRouter evaluates semantic similarity between
 * a live SensorSnapshot and these stored ContextStates.
 */
data class ContextState(

    /**
     * Globally unique semantic state identifier.
     */
    val stateId: String,

    /**
     * Expected anchor time in decimal hours.
     */
    val expectedTime: Double,

    /**
     * Anchor latitude.
     */
    val lat: Double,

    /**
     * Anchor longitude.
     */
    val lng: Double,

    /**
     * Expected BLE anchor devices mapped
     * to their static importance weights.
     *
     * Example:
     * - caregiver phone
     * - bedside tablet
     * - room speaker
     */
    val bleDevices: Map<String, Double>
)