package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Test

class BLEScorerTest {

    @Test
    fun `empty detected map returns zero reliability`() {

        val scorer = BLEScorer()

        val reliability =
            scorer.calculateReliability(
                emptyMap()
            )

        assertEquals(
            0.0,
            reliability,
            1e-9
        )
    }

    @Test
    fun `empty registered map returns zero score`() {

        val scorer = BLEScorer()

        val score = scorer.score(
            detected = mapOf(
                "A" to Rssi(-60)
            ),
            registered = emptyMap()
        )

        assertEquals(
            0.0,
            score,
            1e-9
        )
    }

    @Test
    fun `single detected device returns weighted fraction`() {

        val scorer = BLEScorer()

        val detected = mapOf(
            "deviceA" to Rssi(-55)
        )

        val registered = mapOf(
            "deviceA" to 0.5,
            "deviceB" to 0.3,
            "deviceC" to 0.2
        )

        val score = scorer.score(
            detected = detected,
            registered = registered
        )

        assertEquals(
            0.5,
            score,
            1e-9
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive RSSI throws on construction`() {

        Rssi(1)
    }
}