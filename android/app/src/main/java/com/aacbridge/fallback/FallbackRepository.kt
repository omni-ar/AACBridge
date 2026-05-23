package com.aacbridge.fallback

/**
 * Persistence contract for ultra-fast
 * generic AAC fallback responses.
 *
 * Backed by:
 * - SQLite
 * - Room
 * - pre-seeded response table
 */
interface FallbackRepository {

    /**
     * Returns generic low-latency fallback response.
     *
     * Example:
     * Intent: "help"
     * Response: "Please help me."
     *
     * Must remain:
     * deterministic
     * lightweight
     * sub-50ms
     */
    suspend fun getFallbackResponse(
        intent: String,
        stateId: String?
    ): String?
}