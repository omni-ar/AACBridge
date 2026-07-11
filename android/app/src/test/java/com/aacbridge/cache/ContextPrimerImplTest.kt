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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests for ContextPrimerImpl.
 *
 * Verifies:
 * 1. runInference() is called before saveKVCache()
 * 2. Atomic write: .tmp created then renamed to .bin
 * 3. Save failure cleans up .tmp file
 * 4. Unknown stateId returns false
 * 5. Engine mutex is acquired during priming
 */
class ContextPrimerImplTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var bridge: FakeLlamaBridge
    private lateinit var adapter: LlamaBridgeAdapter
    private lateinit var repository: FakeStateRepository
    private lateinit var engineLock: ReentrantLock
    private lateinit var primer: ContextPrimerImpl

    private val testStates = listOf(
        ContextState(
            stateId = "home_morning",
            expectedTime = 8.0,
            lat = 0.0,
            lng = 0.0,
            bleDevices = emptyMap()
        )
    )

    @Before
    fun setUp() {
        bridge = FakeLlamaBridge()
        adapter = bridge
        repository = FakeStateRepository(testStates)
        engineLock = ReentrantLock()
        primer = ContextPrimerImpl(
            repository = repository,
            bridge = adapter,
            engineLock = engineLock,
            modelReady = AtomicBoolean(true)
        )
    }

    @Test
    fun `successful prime creates bin file via atomic rename`() =
        runTest {
            val cacheDir = tempDir.newFolder("kv_cache")
            val binPath = File(cacheDir, "home_morning.bin").absolutePath

            val success = primer.primeAndSave(
                stateId = "home_morning",
                seqId = 0,
                filePath = binPath
            )

            assertTrue("primeAndSave must succeed", success)

            // prefillOnly was called
            assertEquals(1, bridge.prefillCallCount)
            assertTrue(
                "Prompt must contain stateId context",
                bridge.prefillPrompts[0].contains("home_morning")
            )

            // saveKVCache was called with .tmp path
            assertEquals(1, bridge.saveCallCount)
            assertTrue(
                "Save must target .tmp file",
                bridge.savedStates[0]!!.endsWith(".tmp")
            )

            // .bin file exists (atomic rename succeeded)
            assertTrue(
                ".bin file must exist after priming",
                File(binPath).exists()
            )

            // .tmp file was cleaned up (renamed away)
            assertFalse(
                ".tmp file must not exist after rename",
                File("$binPath.tmp").exists()
            )
        }

    @Test
    fun `prefillOnly called before saveKVCache`() =
        runTest {
            val cacheDir = tempDir.newFolder("kv_cache_order")
            val binPath = File(cacheDir, "home_morning.bin").absolutePath

            val callOrder = mutableListOf<String>()

            val orderTrackingAdapter = object : LlamaBridgeAdapter {
                override fun loadKVCache(filepath: String, seqId: Int): Boolean {
                    callOrder.add("loadKVCache")
                    return true
                }
                override fun clearKVCache() {}
                override fun prefillOnly(prompt: String): Boolean {
                    callOrder.add("prefillOnly")
                    return true
                }
                override fun resumeInference(prompt: String) = ""
                override fun saveKVCache(filepath: String, seqId: Int): Boolean {
                    callOrder.add("saveKVCache")
                    java.io.File(filepath).apply {
                        parentFile?.mkdirs()
                        writeText("fake")
                    }
                    return true
                }
                override fun runInference(prompt: String): String {
                    callOrder.add("runInference")
                    return "test"
                }
            }

            val orderPrimer = ContextPrimerImpl(
                repository = repository,
                bridge = orderTrackingAdapter,
                engineLock = engineLock
            )

            orderPrimer.primeAndSave("home_morning", 0, binPath)

            assertEquals(
                "prefillOnly must be called first",
                listOf("prefillOnly", "saveKVCache"),
                callOrder
            )
        }

    @Test
    fun `unknown stateId returns false without calling bridge`() =
        runTest {
            val success = primer.primeAndSave(
                stateId = "nonexistent_state",
                seqId = 0,
                filePath = "/tmp/nonexistent.bin"
            )

            assertFalse("Unknown stateId must return false", success)
            assertEquals(
                "runInference must NOT be called",
                0,
                bridge.inferenceCallCount
            )
            assertEquals(
                "saveKVCache must NOT be called",
                0,
                bridge.saveCallCount
            )
        }

    @Test
    fun `save failure cleans up tmp and returns false`() =
        runTest {
            bridge.shouldFailSave = true

            val cacheDir = tempDir.newFolder("kv_cache_fail")
            val binPath = File(cacheDir, "home_morning.bin").absolutePath

            val success = primer.primeAndSave(
                stateId = "home_morning",
                seqId = 0,
                filePath = binPath
            )

            assertFalse("Must return false on save failure", success)

            // prefillOnly was still called (prefill attempted)
            assertEquals(1, bridge.prefillCallCount)

            // .bin must NOT exist
            assertFalse(
                ".bin must not exist on failure",
                File(binPath).exists()
            )

            // .tmp must be cleaned up
            assertFalse(
                ".tmp must be cleaned up on failure",
                File("$binPath.tmp").exists()
            )
        }

    @Test
    fun `engine lock is held during priming`() =
        runTest {
            val cacheDir = tempDir.newFolder("kv_cache_lock")
            val binPath = File(cacheDir, "home_morning.bin").absolutePath

            var lockHeldDuringPrefill = false
            var lockHeldDuringSave = false

            val lockCheckAdapter = object : LlamaBridgeAdapter {
                override fun loadKVCache(filepath: String, seqId: Int) = true
                override fun clearKVCache() {}
                override fun prefillOnly(prompt: String): Boolean {
                    lockHeldDuringPrefill = engineLock.isHeldByCurrentThread
                    return true
                }
                override fun resumeInference(prompt: String) = ""
                override fun saveKVCache(filepath: String, seqId: Int): Boolean {
                    lockHeldDuringSave = engineLock.isHeldByCurrentThread
                    java.io.File(filepath).apply {
                        parentFile?.mkdirs()
                        writeText("fake")
                    }
                    return true
                }
                override fun runInference(prompt: String): String {
                    return "test"
                }
            }

            val lockPrimer = ContextPrimerImpl(
                repository = repository,
                bridge = lockCheckAdapter,
                engineLock = engineLock
            )

            lockPrimer.primeAndSave("home_morning", 0, binPath)

            assertTrue(
                "Engine lock must be held during prefillOnly",
                lockHeldDuringPrefill
            )
            assertTrue(
                "Engine lock must be held during saveKVCache",
                lockHeldDuringSave
            )
        }

    @Test
    fun `priming fails when model is not ready`() =
        runTest {
            val notReadyPrimer = ContextPrimerImpl(
                repository = repository,
                bridge = adapter,
                engineLock = engineLock,
                modelReady = AtomicBoolean(false)
            )

            val cacheDir = tempDir.newFolder("kv_cache_not_ready")
            val binPath = File(cacheDir, "home_morning.bin").absolutePath

            val success = notReadyPrimer.primeAndSave(
                stateId = "home_morning",
                seqId = 0,
                filePath = binPath
            )

            assertFalse(
                "Priming must fail when model is not ready",
                success
            )
            assertEquals(
                "runInference must NOT be called",
                0,
                bridge.inferenceCallCount
            )
            assertEquals(
                "saveKVCache must NOT be called",
                0,
                bridge.saveCallCount
            )
        }
}
