package com.aacbridge.cache

import android.content.Context
import com.aacbridge.router.ContextState
import java.io.File

/**
 * Benchmarking-phase StateRepository implementation.
 *
 * PURPOSE:
 * Provides deterministic, hardcoded semantic context states
 * and resolvable KV cache file paths to unblock the
 * prefill/save/load pipeline for TTFT benchmarking.
 *
 * THIS IS NOT THE FINAL PERSISTENCE ARCHITECTURE.
 *
 * This implementation will be replaced by a Room-backed
 * repository when dynamic context management is required
 * (caregiver-configurable profiles, auto-discovered states,
 * runtime CRUD operations).
 *
 * Design rationale:
 * - Context states are static configuration for benchmarking
 * - Room adds KSP/annotation processor complexity with zero
 *   benefit at this phase
 * - StateRepository interface isolates all consumers from
 *   this implementation decision
 * - Swap to Room requires changing ONE line in AppContainer
 *
 * File path convention:
 * {context.filesDir}/kv_cache/{stateId}.bin
 *
 * This matches the expected pattern for:
 * - LlamaBridge.saveKVCache(filepath, seqId)
 * - LlamaBridge.loadKVCache(filepath, seqId)
 */
class SeededStateRepository(
    context: Context
) : StateRepository {

    /**
     * Root directory for serialized KV cache binaries.
     *
     * Created eagerly to ensure the directory exists
     * before any save/load operations attempt file IO.
     */
    private val kvCacheDir: File =
        File(context.filesDir, "kv_cache").also {
            it.mkdirs()
        }

    /**
     * Predefined semantic context states for benchmarking.
     *
     * Each state represents a historically learned
     * environment pattern with temporal, spatial, and
     * BLE topology anchors.
     *
     * These values are representative benchmarking seeds.
     * Production deployment would source these from a
     * Room database populated by caregiver configuration.
     */
    private val seededStates: List<ContextState> = listOf(

        ContextState(
            stateId = "home_morning",
            expectedTime = 8.0,
            lat = 28.6139,
            lng = 77.2090,
            bleDevices = mapOf(
                "AA:BB:CC:DD:EE:01" to 0.8,
                "AA:BB:CC:DD:EE:02" to 0.5
            )
        ),

        ContextState(
            stateId = "home_evening",
            expectedTime = 19.0,
            lat = 28.6139,
            lng = 77.2090,
            bleDevices = mapOf(
                "AA:BB:CC:DD:EE:01" to 0.8,
                "AA:BB:CC:DD:EE:03" to 0.6
            )
        ),

        ContextState(
            stateId = "hospital_ward",
            expectedTime = 11.0,
            lat = 28.5672,
            lng = 77.2100,
            bleDevices = mapOf(
                "AA:BB:CC:DD:EE:04" to 0.9,
                "AA:BB:CC:DD:EE:05" to 0.7
            )
        ),

        ContextState(
            stateId = "therapy_room",
            expectedTime = 14.0,
            lat = 28.5672,
            lng = 77.2105,
            bleDevices = mapOf(
                "AA:BB:CC:DD:EE:06" to 0.85
            )
        ),

        ContextState(
            stateId = "caregiver_visit",
            expectedTime = 16.5,
            lat = 28.6139,
            lng = 77.2090,
            bleDevices = mapOf(
                "AA:BB:CC:DD:EE:07" to 0.95,
                "AA:BB:CC:DD:EE:01" to 0.8
            )
        )
    )

    /**
     * Lookup index for O(1) state access by stateId.
     */
    private val stateIndex: Map<String, ContextState> =
        seededStates.associateBy { it.stateId }

    /**
     * Resolves semantic stateId to deterministic
     * KV cache file path.
     *
     * Returns a valid absolute path even if the .bin
     * file does not yet exist on disk. The path serves
     * as a contract for where saveKVCache() will write
     * and loadKVCache() will read.
     *
     * Returns null only if the stateId is unknown.
     *
     * @return absolute path like:
     *   /data/data/com.aacbridge/files/kv_cache/home_morning.bin
     */
    override suspend fun getFilePath(
        stateId: String
    ): String? {

        if (!stateIndex.containsKey(stateId)) {
            return null
        }

        return File(kvCacheDir, "$stateId.bin")
            .absolutePath
    }

    /**
     * Returns all predefined semantic context states.
     *
     * Defensive copy prevents external mutation.
     */
    override suspend fun getAllContextStates(): List<ContextState> {
        return seededStates.toList()
    }

    /**
     * Context prompt texts for KV cache priming.
     *
     * Each prompt establishes the semantic environment
     * so the LLM's KV cache contains pre-computed
     * attention patterns for that context.
     *
     * These are benchmarking-phase prompts.
     * Production deployment would source these from
     * caregiver-configurable profiles.
     */
    private val promptTexts: Map<String, String> = mapOf(

        "home_morning" to
            "You are a communication assistant for a person with ALS. " +
            "The user is at home in the morning. Their mother is nearby. " +
            "Common needs: breakfast requests, morning medication reminders, " +
            "greeting family members. Respond concisely.",

        "home_evening" to
            "You are a communication assistant for a person with ALS. " +
            "The user is at home in the evening. Their caregiver is present. " +
            "Common needs: dinner preferences, requesting bedtime assistance, " +
            "expressing comfort needs. Respond concisely.",

        "hospital_ward" to
            "You are a communication assistant for a person with ALS. " +
            "The user is in a hospital ward during daytime. Medical staff are nearby. " +
            "Common needs: pain level communication, requesting nurse attention, " +
            "water and comfort requests. Respond concisely.",

        "therapy_room" to
            "You are a communication assistant for a person with ALS. " +
            "The user is in a therapy session. Their therapist is present. " +
            "Common needs: exercise feedback, requesting breaks, " +
            "describing physical sensations. Respond concisely.",

        "caregiver_visit" to
            "You are a communication assistant for a person with ALS. " +
            "A caregiver is visiting the user at home. " +
            "Common needs: social conversation, schedule coordination, " +
            "expressing preferences and gratitude. Respond concisely."
    )

    /**
     * Resolves stateId to context prompt text for
     * KV cache priming (prefill).
     *
     * Returns null only if stateId is unknown.
     */
    override suspend fun getPromptText(
        stateId: String
    ): String? {
        return promptTexts[stateId]
    }
}
