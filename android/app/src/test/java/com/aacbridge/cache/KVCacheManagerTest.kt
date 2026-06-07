package com.aacbridge.cache

import com.aacbridge.inference.LlamaBridgeAdapter
import com.aacbridge.router.ContextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class KVCacheManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = tempDir.newFolder("kv_cache")
    }

    /**
     * Creates deterministic test state.
     */
    private fun createState(
        id: String,
        expectedTime: Double = 8.0
    ): ContextState {

        return ContextState(
            stateId = id,
            expectedTime = expectedTime,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )
    }

    /**
     * Creates a repository returning temp paths + creates
     * actual .bin files so File.exists() returns true.
     */
    private fun createRepoWithFiles(
        states: List<ContextState>
    ): StateRepository {
        // Create actual .bin files for each state
        for (state in states) {
            File(cacheDir, "${state.stateId}.bin")
                .writeText("fake")
        }
        val stateMap = states.associateBy { it.stateId }
        return object : StateRepository {
            override suspend fun getFilePath(stateId: String): String? {
                return if (stateMap.containsKey(stateId))
                    File(cacheDir, "$stateId.bin").absolutePath
                else null
            }
            override suspend fun getAllContextStates() = states
            override suspend fun getPromptText(stateId: String): String? {
                return if (stateMap.containsKey(stateId))
                    "Test context for $stateId"
                else null
            }
        }
    }

    @Test
    fun `eviction waits for refCount drain before recycling seqId`() =
    runTest {
        val repository = createRepoWithFiles(
            listOf(createState("A"), createState("B"), createState("C"), createState("D"))
        )
        val bridge = FakeLlamaBridge()
        val manager = KVCacheManager(
            repository = repository,
            mutexRegistry = CacheMutexRegistry(),
            jniBridge = object : LlamaBridgeAdapter {
                override fun loadKVCache(filepath: String, seqId: Int) =
                    bridge.loadKVCache(filepath, seqId)
                override fun saveKVCache(filepath: String, seqId: Int) =
                    bridge.saveKVCache(filepath, seqId)
                override fun runInference(prompt: String) =
                    bridge.runInference(prompt)
            }
        )

        manager.loadTopStates(listOf("A", "B", "C"))
        assertEquals(3, manager.getActiveStateCount())

        val acquired = manager.acquireStateForInference("A")
        assertNotNull(acquired)
        val originalSeqId = acquired!!.seqId

        Thread.sleep(10)
        manager.acquireStateForInference("B")?.let { manager.releaseState("B") }
        manager.acquireStateForInference("C")?.let { manager.releaseState("C") }

        manager.releaseState("A")
        manager.loadTopStates(listOf("D"))

        assertFalse(manager.isStateResident("A"))
        assertTrue(manager.isStateResident("D"))
        assertEquals(
            File(cacheDir, "D.bin").absolutePath,
            bridge.loadedStates[originalSeqId]
        )
        assertEquals(3, manager.getActiveStateCount() + manager.getAvailableSeqIdCount())
    }

    @Test
fun `failed JNI load returns seqId to pool`() =
    runTest {

        val repository =
            createRepoWithFiles(
                listOf(
                    createState("A")
                )
            )

        val bridge =
            FakeLlamaBridge().apply {
                shouldFailLoad = true
            }

        val manager =
            KVCacheManager(
                repository = repository,
                mutexRegistry = CacheMutexRegistry(),

                jniBridge = object :
                    LlamaBridgeAdapter {

                    override fun loadKVCache(
                        filepath: String,
                        seqId: Int
                    ): Boolean {

                        return bridge.loadKVCache(
                            filepath,
                            seqId
                        )
                    }
                    override fun saveKVCache(filepath: String, seqId: Int) =
                        bridge.saveKVCache(filepath, seqId)
                    override fun runInference(prompt: String) =
                        bridge.runInference(prompt)
                }
            )

        val initialAvailable =
            manager.getAvailableSeqIdCount()

        manager.loadTopStates(
            listOf("A")
        )

        // No active residency should exist
        assertFalse(
            manager.isStateResident("A")
        )

        // seqId must be returned safely
        assertEquals(
            initialAvailable,
            manager.getAvailableSeqIdCount()
        )

        // JNI load attempted exactly once
        assertEquals(
            1,
            bridge.loadCallCount
        )
    }

    @Test
fun `resident state is not redundantly reloaded`() =
    runTest {

        val repository =
            createRepoWithFiles(
                listOf(
                    createState("A")
                )
            )

        val bridge =
            FakeLlamaBridge()

        val manager =
            KVCacheManager(
                repository = repository,
                mutexRegistry = CacheMutexRegistry(),

                jniBridge = object :
                    LlamaBridgeAdapter {

                    override fun loadKVCache(
                        filepath: String,
                        seqId: Int
                    ): Boolean {

                        return bridge.loadKVCache(
                            filepath,
                            seqId
                        )
                    }
                    override fun saveKVCache(filepath: String, seqId: Int) =
                        bridge.saveKVCache(filepath, seqId)
                    override fun runInference(prompt: String) =
                        bridge.runInference(prompt)
                }
            )

        manager.loadTopStates(
            listOf("A")
        )

        manager.loadTopStates(
            listOf("A")
        )

        // JNI load must happen only once
        assertEquals(
            1,
            bridge.loadCallCount
        )

        // residency remains valid
        assertTrue(
            manager.isStateResident("A")
        )

        assertEquals(
            1,
            manager.getActiveStateCount()
        )
    }

    @Test
fun `state loads successfully into available seqId`() =
    runTest {

        val repository =
            createRepoWithFiles(
                listOf(
                    createState("A")
                )
            )

        val bridge =
            FakeLlamaBridge()

        val manager =
            KVCacheManager(
                repository = repository,
                mutexRegistry = CacheMutexRegistry(),

                jniBridge = object :
                    LlamaBridgeAdapter {

                    override fun loadKVCache(
                        filepath: String,
                        seqId: Int
                    ): Boolean {

                        return bridge.loadKVCache(
                            filepath,
                            seqId
                        )
                    }
                    override fun saveKVCache(filepath: String, seqId: Int) =
                        bridge.saveKVCache(filepath, seqId)
                    override fun runInference(prompt: String) =
                        bridge.runInference(prompt)
                }
            )

        val initialAvailable =
            manager.getAvailableSeqIdCount()

        manager.loadTopStates(
            listOf("A")
        )

        assertTrue(
            manager.isStateResident("A")
        )

        // One slot consumed
        assertEquals(
            initialAvailable - 1,
            manager.getAvailableSeqIdCount()
        )

        assertEquals(
            1,
            manager.getActiveStateCount()
        )

        // JNI invoked exactly once
        assertEquals(
            1,
            bridge.loadCallCount
        )

        // seqId ownership established
        assertTrue(
            bridge.loadedStates.isNotEmpty()
        )
    }
}