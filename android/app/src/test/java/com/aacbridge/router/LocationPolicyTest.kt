package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPolicyTest {

    private val now = 10_000_000L // elapsedRealtime, ms

    private fun eval(
        lat: Double = 28.6139,
        lng: Double = 77.2090,
        acc: Float = 15f,
        hasAcc: Boolean = true,
        mock: Boolean = false,
        ageMs: Long = 5_000L,
        fixNanos: Long = (now - ageMs) * 1_000_000L
    ) = LocationPolicy.evaluate(
        latitude = lat,
        longitude = lng,
        accuracyMeters = acc,
        hasAccuracy = hasAcc,
        isMock = mock,
        fixElapsedRealtimeNanos = fixNanos,
        nowElapsedRealtimeMs = now
    )

    private fun rejected(d: LocationPolicy.Decision) =
        (d as LocationPolicy.Decision.Rejected).reason

    @Test
    fun `normal fix accepted`() {
        val d = eval()
        assertTrue(d is LocationPolicy.Decision.Accepted)
        assertEquals(5_000L, (d as LocationPolicy.Decision.Accepted).ageMs)
        assertEquals(15f, d.location.accuracyMeters)
    }

    @Test
    fun `mock fix rejected even if otherwise valid`() {
        assertEquals(LocationPolicy.Rejection.MOCK, rejected(eval(mock = true)))
    }

    @Test
    fun `staleness boundary`() {
        val max = LocationPolicy.MAX_FIX_AGE_MS
        assertTrue(eval(ageMs = max) is LocationPolicy.Decision.Accepted)
        assertEquals(LocationPolicy.Rejection.STALE, rejected(eval(ageMs = max + 1)))
    }

    @Test
    fun `missing and future timestamps rejected`() {
        assertEquals(LocationPolicy.Rejection.NO_TIMESTAMP, rejected(eval(fixNanos = 0L)))
        assertEquals(LocationPolicy.Rejection.FUTURE_TIMESTAMP, rejected(eval(ageMs = -5_000L)))
        // Small skew tolerated
        assertTrue(eval(ageMs = -500L) is LocationPolicy.Decision.Accepted)
    }

    @Test
    fun `accuracy bounds`() {
        assertEquals(LocationPolicy.Rejection.ACCURACY_TOO_FINE, rejected(eval(acc = 0.5f)))
        assertEquals(LocationPolicy.Rejection.ACCURACY_TOO_COARSE, rejected(eval(acc = 800f)))
        assertEquals(LocationPolicy.Rejection.NO_ACCURACY, rejected(eval(hasAcc = false)))
        assertEquals(LocationPolicy.Rejection.NO_ACCURACY, rejected(eval(acc = Float.NaN)))
        assertTrue(eval(acc = LocationPolicy.MIN_PLAUSIBLE_ACCURACY_M) is LocationPolicy.Decision.Accepted)
        assertTrue(eval(acc = LocationPolicy.MAX_USABLE_ACCURACY_M) is LocationPolicy.Decision.Accepted)
    }

    @Test
    fun `invalid coordinates rejected`() {
        assertEquals(LocationPolicy.Rejection.INVALID_COORDINATES, rejected(eval(lat = 91.0)))
        assertEquals(LocationPolicy.Rejection.INVALID_COORDINATES, rejected(eval(lng = Double.NaN)))
    }

    @Test
    fun `plausible spoof with realistic accuracy is accepted`() {
        // Documents the limit: the policy cannot detect a
        // forged fix that is fresh, unflagged and reports 5 m.
        assertTrue(eval(lat = 28.5672, lng = 77.2100, acc = 5f) is LocationPolicy.Decision.Accepted)
    }
}
