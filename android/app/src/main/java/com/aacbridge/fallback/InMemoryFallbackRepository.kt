package com.aacbridge.fallback

/**
 * Temporary in-memory fallback repository.
 *
 * Exists ONLY until SQLite / Room persistence
 * layer is integrated.
 */
class InMemoryFallbackRepository : FallbackRepository {

    private val responses =
        mapOf(
            "help" to "Please help me.",
            "confirm" to "Yes.",
            "reject" to "No.",
            "call-help" to "I need assistance.",
            "select" to "Please select that option.",
            "scroll" to "Please continue scrolling."
        )

    override suspend fun getFallbackResponse(
        intent: String,
        stateId: String?
    ): String? {

        return responses[intent]
    }
}