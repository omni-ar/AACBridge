package com.aacbridge

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.aacbridge.inference.LlamaBridge
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AACBridge"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        thread {

            try {

                Log.d(TAG, "Initializing model...")

                val initialized = LlamaBridge.initializeModel(
                    "/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf"
                )

                Log.d(TAG, "Model initialized: $initialized")

                if (!initialized) {
                    Log.e(TAG, "Model initialization failed")
                    return@thread
                }

                Log.d(TAG, "Running inference...")

                val output = LlamaBridge.runInference("User: Hello\nAssistant:")

                Log.d(TAG, "Inference output: $output")

                Log.d(TAG, "Inference completed successfully")

            } catch (e: Exception) {

                Log.e(TAG, "Inference pipeline crashed", e)
            }
        }
    }
}