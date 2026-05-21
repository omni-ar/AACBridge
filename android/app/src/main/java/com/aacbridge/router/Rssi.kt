package com.aacbridge.router

/**
 * Strongly-typed BLE RSSI signal strength.
 *
 * Encapsulates a validated RSSI reading from the Android BLE stack.
 *
 * Why this exists instead of using raw Int:
 * - Prevents invalid signal values from entering routing math
 * - Makes scorer contracts explicit
 * - Improves type safety across BLE scoring logic
 * - Avoids "primitive obsession" in the routing layer
 *
 * RSSI semantics:
 * - Measured in dBm
 * - Typical BLE range:
 *      ~ -30 dBm  -> extremely strong / very close
 *      ~ -70 dBm  -> moderate indoor signal
 *      ~ -100 dBm -> near disconnect threshold
 *
 * Contract:
 * Value MUST remain within physically plausible BLE limits.
 */
@JvmInline
value class Rssi(val value: Int) {

    init {

        require(value in -127..0) {
            "RSSI must be within [-127, 0] dBm, received: $value"
        }
    }
}