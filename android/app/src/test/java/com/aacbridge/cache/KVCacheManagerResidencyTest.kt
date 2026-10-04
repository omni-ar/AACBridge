package com.aacbridge.cache

import com.aacbridge.router.ContextState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock

/**
 * Residency behaviour added for the hysteresis-gated
 * ActiveSweep path and slot-targeted priming.
 */
class KVCacheManagerResidencyTest {

    private val ids = listOf("a", "b", "c", "d", "e")

    private fun repo(dir: File, withFiles: Boolean): StateRepository {
        if (withFiles) ids.forEach { File(dir, "$it.bin").writeText("fake") }
        return object : StateRepository {
            override suspend fun getFilePath(stateId: String) =
                File(dir, "$stateId.bin").absolutePath.takeIf { stateId in ids }
            override suspend fun getAllContextStates() =
                ids.map { ContextState(it, 8.0, 0.0, 0.0, emptyMap()) }
            override suspend fun getPromptText(stateId: String) =
                "prompt for $stateId".takeIf { stateId in ids }
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("kvres").toFile()

    @Test
    fun `loadTopStates does not evict a requested state`() = runBlocking {
        val bridge = FakeLlamaBridge()
        val manager = KVCacheManager(repo(tempDir(), true), CacheMutexRegistry(), bridge)

        manager.loadTopStates(listOf("a", "b", "c"))
        // "a" is the least recently loaded; it is requested again
        // alongside two new states and must survive.
        manager.loadTopStates(listOf("a", "d", "e"))

        assertEquals(setOf("a", "d", "e"), manager.residentStateIds())
    }

    @Test
    fun `updateResidency applies hysteresis margin`() = runBlocking {
        val bridge = FakeLlamaBridge()
        val manager = KVCacheManager(repo(tempDir(), true), CacheMutexRegistry(), bridge)

        manager.updateResidency(listOf("a" to 0.9, "b" to 0.8, "c" to 0.7), margin = 0.1)
        assertEquals(setOf("a", "b", "c"), manager.residentStateIds())

        // d beats c by 0.05 < margin: no swap.
        val blocked = manager.updateResidency(
            listOf("a" to 0.9, "b" to 0.8, "d" to 0.75, "c" to 0.70), margin = 0.1)
        assertTrue(blocked.isEmpty)
        assertEquals(setOf("a", "b", "c"), manager.residentStateIds())

        // d beats c by 0.5: swap.
        val swapped = manager.updateResidency(
            listOf("a" to 0.9, "b" to 0.8, "d" to 0.75, "c" to 0.25), margin = 0.1)
        assertEquals(listOf("c"), swapped.toEvict)
        assertEquals(setOf("a", "b", "d"), manager.residentStateIds())
        assertEquals(3, manager.getActiveStateCount() + manager.getAvailableSeqIdCount())
    }

    @Test
    fun `priming targets the allocated slot`() = runBlocking {
        val dir = tempDir()
        val bridge = FakeLlamaBridge()
        val repository = repo(dir, withFiles = false)
        val primer = ContextPrimerImpl(repository, bridge, ReentrantLock())
        val manager = KVCacheManager(repository, CacheMutexRegistry(), bridge, primer)

        manager.loadTopStates(listOf("a", "b", "c"))

        assertEquals(listOf(0, 1, 2), bridge.prefillSeqIds.sorted())
        // Each slot is saved from the slot it was primed into.
        assertEquals(setOf(0, 1, 2), bridge.savedStates.keys)
        assertEquals(setOf("a", "b", "c"), manager.residentStateIds())
    }
}
