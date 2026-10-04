package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidencyPolicyTest {

    private fun ranked(vararg p: Pair<String, Double>) = p.toList()

    @Test
    fun `fills free slots in rank order without eviction`() {
        val plan = ResidencyPolicy.plan(
            ranked("a" to 0.9, "b" to 0.8, "c" to 0.7, "d" to 0.6),
            resident = emptySet(), capacity = 3, margin = 0.1
        )
        assertEquals(listOf("a", "b", "c"), plan.toLoad)
        assertTrue(plan.toEvict.isEmpty())
    }

    @Test
    fun `no change when top candidates already resident`() {
        val plan = ResidencyPolicy.plan(
            ranked("a" to 0.9, "b" to 0.8, "c" to 0.7),
            resident = setOf("a", "b", "c"), capacity = 3, margin = 0.1
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `replaces weakest resident only when advantage exceeds margin`() {
        val r = ranked("a" to 0.9, "b" to 0.8, "d" to 0.75, "c" to 0.70)

        val blocked = ResidencyPolicy.plan(r, setOf("a", "b", "c"), capacity = 3, margin = 0.10)
        assertTrue("0.05 advantage must not pass a 0.10 margin", blocked.isEmpty)

        val allowed = ResidencyPolicy.plan(r, setOf("a", "b", "c"), capacity = 3, margin = 0.0)
        assertEquals(listOf("c"), allowed.toEvict)
        assertEquals(listOf("d"), allowed.toLoad)
    }

    @Test
    fun `advantage equal to margin does not swap`() {
        val plan = ResidencyPolicy.plan(
            ranked("a" to 0.9, "b" to 0.8, "d" to 0.5, "c" to 0.25),
            resident = setOf("a", "b", "c"), capacity = 3, margin = 0.25
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `resident below dead-state threshold scores zero and is evicted first`() {
        val plan = ResidencyPolicy.plan(
            ranked("a" to 0.9, "b" to 0.8, "d" to 0.3),
            resident = setOf("a", "b", "gone"), capacity = 3, margin = 0.1
        )
        assertEquals(listOf("gone"), plan.toEvict)
        assertEquals(listOf("d"), plan.toLoad)
    }

    @Test
    fun `never evicts a state that is itself a candidate`() {
        val plan = ResidencyPolicy.plan(
            ranked("x" to 0.95, "a" to 0.9, "y" to 0.85, "b" to 0.1),
            resident = setOf("a", "b", "c"), capacity = 3, margin = 0.0
        )
        assertTrue("a" !in plan.toEvict)
        assertEquals(listOf("x", "y"), plan.toLoad)
        assertEquals(2, plan.toEvict.size)
    }

    @Test
    fun `maxCandidates limits loads to the top state`() {
        val plan = ResidencyPolicy.plan(
            ranked("x" to 0.9, "y" to 0.85, "a" to 0.2),
            resident = setOf("a", "b", "c"), capacity = 3, margin = 0.1, maxCandidates = 1
        )
        assertEquals(listOf("x"), plan.toLoad)
        assertEquals(1, plan.toEvict.size)
    }

    @Test
    fun `selection picks highest ranked resident or null`() {
        val r = ranked("a" to 0.9, "b" to 0.8, "c" to 0.7)
        assertEquals("b", ResidencyPolicy.selectForInference(r, setOf("b", "c")))
        assertNull(ResidencyPolicy.selectForInference(r, setOf("z")))
        assertNull(ResidencyPolicy.selectForInference(emptyList(), setOf("a")))
    }
}
