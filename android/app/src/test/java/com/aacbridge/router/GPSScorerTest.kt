package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GPSScorerTest {

    @Test
    fun `same coordinates produce score one`() {

        val scorer = GPSScorer()

        val current = GpsLocation(
            lat = 28.6139,
            lng = 77.2090,
            accuracyMeters = 5f
        )

        val score = scorer.score(
            current = current,
            anchorLat = 28.6139,
            anchorLng = 77.2090
        )

        assertEquals(
            1.0,
            score,
            1e-9
        )
    }

    @Test
    fun `one kilometer distance collapses score`() {

        val scorer = GPSScorer(
            lambdaKm = 0.1
        )

        val current = GpsLocation(
            lat = 0.0,
            lng = 0.0,
            accuracyMeters = 5f
        )

        /*
         * Roughly 1km latitude delta.
         */
        val score = scorer.score(
            current = current,
            anchorLat = 0.009,
            anchorLng = 0.0
        )

        assertTrue(score < 0.001)
    }
}