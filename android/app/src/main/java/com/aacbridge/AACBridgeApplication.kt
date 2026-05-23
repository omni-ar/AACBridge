package com.aacbridge

import android.app.Application
import com.aacbridge.daemon.DriftDetector

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