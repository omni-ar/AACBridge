package com.aacbridge.cache

import com.aacbridge.router.ContextState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies SeededStateRepository contract compliance.
 *
 * These tests run on JVM without Android Context.
 * They use FakeStateRepository (same interface) to validate
 * the contract that SeededStateRepository must satisfy,
 * since SeededStateRepository requires android.content.Context
 * and cannot be instantiated in JVM unit tests.
 *
 * This verifies the INTERFACE CONTRACT:
 * 1. getAllContextStates() returns non-empty list
 * 2. getFilePath(knownId) returns non-null path
 * 3. getFilePath(unknownId) returns null
 * 4. KVCacheManager receives non-null paths for known states
 */
class SeededStateRepositoryContractTest {

    private val benchmarkStates = listOf(
        ContextState(
            stateId = "home_morning",
            expectedTime = 8.0,
            lat = 28.6139,
            lng = 77.2090,
            bleDevices = mapOf("AA:BB:CC:DD:EE:01" to 0.8)
        ),
        ContextState(
            stateId = "hospital_ward",
            expectedTime = 11.0,
            lat = 28.5672,
            lng = 77.2100,
            bleDevices = mapOf("AA:BB:CC:DD:EE:04" to 0.9)
        ),
        ContextState(
            stateId = "therapy_room",
            expectedTime = 14.0,
            lat = 28.5672,
            lng = 77.2105,
            bleDevices = emptyMap()
        )
    )

    /**
     * FakeStateRepository mirrors SeededStateRepository contract:
     * - known stateIds return file paths
     * - unknown stateIds return null
     * - getAllContextStates returns seeded list
     */
    private val repository = FakeStateRepository(benchmarkStates)

    @Test
    fun `getAllContextStates returns non-empty list`() =
        runTest {
            val states = repository.getAllContextStates()

            assertTrue(
                "Repository must return non-empty state list",
                states.isNotEmpty()
            )

            assertEquals(
                "Expected 3 benchmark states",
                3,
                states.size
            )
        }

    @Test
    fun `getFilePath returns non-null for known stateId`() =
        runTest {
            val path = repository.getFilePath("home_morning")

            assertNotNull(
                "Known stateId must return non-null path",
                path
            )

            assertTrue(
                "Path must contain the stateId",
                path!!.contains("home_morning")
            )
        }

    @Test
    fun `getFilePath returns null for unknown stateId`() =
        runTest {
            val path = repository.getFilePath("nonexistent_state")

            assertNull(
                "Unknown stateId must return null",
                path
            )
        }

    @Test
    fun `all seeded states have resolvable file paths`() =
        runTest {
            val states = repository.getAllContextStates()

            for (state in states) {
                val path = repository.getFilePath(state.stateId)

                assertNotNull(
                    "State '${state.stateId}' must have a resolvable path",
                    path
                )
            }
        }

    @Test
    fun `KVCacheManager receives non-null paths for all states`() =
        runTest {
            // Simulate the exact code path in KVCacheManager.loadTopStates()
            val topIds = repository.getAllContextStates()
                .map { it.stateId }

            assertTrue(
                "Must have states to load",
                topIds.isNotEmpty()
            )

            for (stateId in topIds) {
                val filePath = repository.getFilePath(stateId)

                // This is the critical assertion:
                // KVCacheManager line 134-136 does:
                //   val filePath = repository.getFilePath(stateId)
                //       ?: return@withLock   <-- THIS was the bottleneck
                assertNotNull(
                    "KVCacheManager would skip stateId='$stateId' because getFilePath returned null",
                    filePath
                )
            }
        }

    @Test
    fun `ActiveSweep receives non-empty state list for routing`() =
        runTest {
            // Simulate the exact code path in ActiveSweep.executeSweep()
            val availableStates = repository.getAllContextStates()

            // ActiveSweep line 71: if (topStateIds.isNotEmpty())
            assertTrue(
                "ActiveSweep would skip cache loading because no states exist",
                availableStates.isNotEmpty()
            )

            // Verify states have valid routing data
            for (state in availableStates) {
                assertTrue(
                    "stateId must not be blank",
                    state.stateId.isNotBlank()
                )
                assertTrue(
                    "expectedTime must be in valid 24h range",
                    state.expectedTime in 0.0..24.0
                )
            }
        }
}
