package com.aacbridge

import android.app.Application
import com.aacbridge.inference.LlamaBridge

class Application : Application() {

    override fun onCreate() {
        super.onCreate()

        LlamaBridge.initializeBackend()
    }
}