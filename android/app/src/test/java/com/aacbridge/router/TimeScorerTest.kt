package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeScorerTest {

    @Test
    fun `23 vs 1 uses circular distance not linear`() {

        val scorer = TimeScorer()

        val score = scorer.score(
            currentHourDecimal = 23.0,
            anchorHourDecimal = 1.0
        )

        // distance = 2 hours, not 22
        assertTrue(score > 0.5)
    }

    @Test
    fun `25_5 normalizes to 1_5 without throwing`() {

        val scorer = TimeScorer()

        val score = scorer.score(
            currentHourDecimal = 25.5,
            anchorHourDecimal = 1.5
        )

        assertEquals(
            1.0,
            score,
            1e-9
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NaN input throws`() {

        val scorer = TimeScorer()

        scorer.score(
            currentHourDecimal = Double.NaN,
            anchorHourDecimal = 1.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sigma zero throws on construction`() {

        TimeScorer(
            sigmaHours = 0.0
        )
    }
}