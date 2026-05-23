package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateRouterTest {

    private val router = StateRouter(
        timeScorer = TimeScorer(),
        gpsScorer = GPSScorer(),
        bleScorer = BLEScorer()
    )

    @Test
    fun `time becomes full fallback when gps and ble unavailable`() {

        val snapshot = SensorSnapshot(
            currentHourDecimal = 10.0,
            location = null,
            detectedBleDevices = emptyMap()
        )

        val state = ContextState(
            stateId = "morning_home",
            expectedTime = 10.0,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )

        val score = router.calculateStateScore(
            snapshot = snapshot,
            state = state
        )

        /*
         * Since:
         * wg = 0
         * wb = 0
         *
         * alpha becomes 1.0 after normalization.
         *
         * Final score should equal S_time.
         */
        assertEquals(
            1.0,
            score,
            1e-9
        )
    }

    @Test
    fun `dead states below threshold are culled`() {

        val snapshot = SensorSnapshot(
            currentHourDecimal = 12.0,
            location = null,
            detectedBleDevices = emptyMap()
        )

        /*
         * Extremely poor temporal match:
         * distance = 12 hours
         *
         * Gaussian decay should collapse below:
         * DEAD_STATE_THRESHOLD = 0.01
         */
        val deadState = ContextState(
            stateId = "irrelevant_state",
            expectedTime = 0.0,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )

        val results = router.getTopContextIds(
            snapshot = snapshot,
            states = listOf(deadState)
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `highest scoring states are returned in descending order`() {

        val snapshot = SensorSnapshot(
            currentHourDecimal = 9.0,
            location = null,
            detectedBleDevices = emptyMap()
        )

        val bestState = ContextState(
            stateId = "best_match",
            expectedTime = 9.0,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )

        val weakState = ContextState(
            stateId = "weak_match",
            expectedTime = 15.0,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )

        val results = router.getTopContextIds(
            snapshot = snapshot,
            states = listOf(
                weakState,
                bestState
            )
        )

        assertEquals(
            "best_match",
            results.first()
        )
    }

    @Test
    fun `result count respects hardware config limit`() {

        val snapshot = SensorSnapshot(
            currentHourDecimal = 8.0,
            location = null,
            detectedBleDevices = emptyMap()
        )

        val states = (1..10).map { index ->
            ContextState(
                stateId = "state_$index",
                expectedTime = 8.0,
                lat = 0.0,
                lng = 0.0,
                bleDevices = emptyMap()
            )
        }

        val results = router.getTopContextIds(
            snapshot = snapshot,
            states = states
        )

        assertEquals(
            HardwareConfig.MAX_ACTIVE_KV_STATES,
            results.size
        )
    }
}