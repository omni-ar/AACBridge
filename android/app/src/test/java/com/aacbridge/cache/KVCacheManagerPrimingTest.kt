package com.aacbridge.cache

import com.aacbridge.inference.LlamaBridgeAdapter
import com.aacbridge.router.ContextState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.locks.ReentrantLock

/**
 * Integration tests verifying KVCacheManager's priming
 * and loading behavior.
 *
 * Verifies:
 * 1. Missing .bin → triggers ContextPrimer (priming path)
 * 2. Existing .bin → loads via loadKVCache (fast path)
 * 3. No priming when .bin already exists
 * 4. Second execution skips priming for cached states
 */
class KVCacheManagerPrimingTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var bridge: FakeLlamaBridge
    private lateinit var adapter: LlamaBridgeAdapter
    private lateinit var cacheDir: File
    private lateinit var engineLock: ReentrantLock

    private fun createState(id: String) = ContextState(
        stateId = id,
        expectedTime = 8.0,
        lat = 0.0,
        lng = 0.0,
        bleDevices = emptyMap()
    )

    /**
     * Repository that returns paths under the temp
     * directory so we can control file existence.
     */
    private inner class TempDirRepository(
        states: List<ContextState>
    ) : StateRepository {

        private val stateMap = states.associateBy { it.stateId }

        override suspend fun getFilePath(stateId: String): String? {
            return if (stateMap.containsKey(stateId))
                File(cacheDir, "$stateId.bin").absolutePath
            else null
        }

        override suspend fun getAllContextStates(): List<ContextState> {
            return stateMap.values.toList()
        }

        override suspend fun getPromptText(stateId: String): String? {
            return if (stateMap.containsKey(stateId))
                "Test context for $stateId"
            else null
        }
    }

    @Before
    fun setUp() {
        bridge = FakeLlamaBridge()
        adapter = bridge
        cacheDir = tempDir.newFolder("kv_cache")
        engineLock = ReentrantLock()
    }

    @Test
    fun `missing bin triggers priming path`() = runTest {
        val repository = TempDirRepository(listOf(createState("A")))
        val primer = ContextPrimerImpl(
            repository = repository,
            bridge = adapter,
            engineLock = engineLock
        )
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = adapter,
            contextPrimer = primer,
            engineLock = engineLock
        )

        // File does NOT exist
        assertFalse(File(cacheDir, "A.bin").exists())

        manager.loadTopStates(listOf("A"))

        // Priming must have been triggered
        assertEquals(
            "prefillOnly must be called for priming",
            1, bridge.prefillCallCount
        )
        assertEquals(
            "saveKVCache must be called to persist",
            1, bridge.saveCallCount
        )

        // .bin file now exists (atomic rename)
        assertTrue(
            ".bin must be created after priming",
            File(cacheDir, "A.bin").exists()
        )

        // State must be resident
        assertTrue(
            "State must be resident after priming",
            manager.isStateResident("A")
        )

        // loadKVCache must NOT have been called (priming path, not load path)
        assertEquals(
            "loadKVCache must NOT be called during priming",
            0, bridge.loadCallCount
        )
    }

    @Test
    fun `existing bin loads directly without priming`() = runTest {
        val repository = TempDirRepository(listOf(createState("A")))
        val primer = ContextPrimerImpl(
            repository = repository,
            bridge = adapter,
            engineLock = engineLock
        )
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = adapter,
            contextPrimer = primer,
            engineLock = engineLock
        )

        // Create .bin file BEFORE loading
        val binFile = File(cacheDir, "A.bin")
        binFile.writeText("fake cache data")
        assertTrue(binFile.exists())

        manager.loadTopStates(listOf("A"))

        // loadKVCache must have been called (fast path)
        assertEquals(
            "loadKVCache must be called for existing file",
            1, bridge.loadCallCount
        )

        // Priming must NOT have been triggered
        assertEquals(
            "runInference must NOT be called when .bin exists",
            0, bridge.inferenceCallCount
        )
        assertEquals(
            "saveKVCache must NOT be called when .bin exists",
            0, bridge.saveCallCount
        )

        // State must be resident
        assertTrue(
            "State must be resident after loading",
            manager.isStateResident("A")
        )
    }

    @Test
    fun `second execution skips priming for cached state`() = runTest {
        val repository = TempDirRepository(listOf(createState("A")))
        val primer = ContextPrimerImpl(
            repository = repository,
            bridge = adapter,
            engineLock = engineLock
        )
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = adapter,
            contextPrimer = primer,
            engineLock = engineLock
        )

        // First load: primes
        manager.loadTopStates(listOf("A"))
        assertEquals(1, bridge.prefillCallCount)
        assertTrue(manager.isStateResident("A"))

        // Second load: state already resident, skip everything
        manager.loadTopStates(listOf("A"))
        assertEquals(
            "No second prefill for already-resident state",
            1, bridge.prefillCallCount
        )
        assertEquals(
            "No second save for already-resident state",
            1, bridge.saveCallCount
        )
    }

    @Test
    fun `priming failure returns seqId to pool`() = runTest {
        bridge.shouldFailSave = true

        val repository = TempDirRepository(listOf(createState("A")))
        val primer = ContextPrimerImpl(
            repository = repository,
            bridge = adapter,
            engineLock = engineLock
        )
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = adapter,
            contextPrimer = primer,
            engineLock = engineLock
        )

        val initialAvailable = manager.getAvailableSeqIdCount()

        manager.loadTopStates(listOf("A"))

        // State must NOT be resident
        assertFalse(
            "State must not be resident on priming failure",
            manager.isStateResident("A")
        )

        // seqId must be returned to pool
        assertEquals(
            "seqId must be returned on priming failure",
            initialAvailable,
            manager.getAvailableSeqIdCount()
        )
    }

    @Test
    fun `engine lock protects loadKVCache calls`() = runTest {
        var lockHeldDuringLoad = false

        val lockCheckAdapter = object : LlamaBridgeAdapter {
            override fun loadKVCache(filepath: String, seqId: Int): Boolean {
                lockHeldDuringLoad = engineLock.isHeldByCurrentThread
                return true
            }
            override fun clearKVCache() {}
            override fun prefillOnly(prompt: String) = true
            override fun resumeInference(prompt: String) = ""
            override fun saveKVCache(filepath: String, seqId: Int) = true
            override fun runInference(prompt: String) = "test"
        }

        val repository = TempDirRepository(listOf(createState("A")))
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = lockCheckAdapter,
            engineLock = engineLock
        )

        // Create .bin file to trigger load path
        File(cacheDir, "A.bin").writeText("fake cache data")

        manager.loadTopStates(listOf("A"))

        assertTrue(
            "Engine lock must be held during loadKVCache",
            lockHeldDuringLoad
        )
    }
}
