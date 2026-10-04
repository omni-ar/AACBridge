package com.aacbridge

import android.app.Application
import com.aacbridge.daemon.DriftDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Root Android application object.
 *
 * Owns the dependency graph lifetime for:
 * - routing
 * - cache orchestration
 * - daemon services
 * - JNI bridge access
 *
 * Single instance for entire process lifetime.
 */
class AACBridgeApplication : Application() {

    /**
     * Global dependency container.
     *
     * Lazy initialization prevents unnecessary
     * startup allocations before first access.
     */
    lateinit var appContainer: AppContainer
        private set

    /**
     * Process-lifetime scope for background work that must
     * outlive an Activity (e.g. Tier-2 priming after a cold
     * miss).
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appContainer =
            AppContainer(
                application = this
            )
            
        // 1. START THE DAEMON HERE
        DriftDetector.schedule(this)
    }
}