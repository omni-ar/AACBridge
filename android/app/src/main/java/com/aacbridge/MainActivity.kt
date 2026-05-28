package com.aacbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aacbridge.daemon.ContextDaemon
import com.aacbridge.gaze.GazeTracker
import com.aacbridge.inference.LlamaBridge
import kotlinx.coroutines.*
import java.util.Locale

/**
 * AACBridge main activity.
 *
 * PRESERVES existing JNI bootstrap flow:
 * LlamaBridge.initializeModel()
 * LlamaBridge.runInference()
 *
 * Incrementally evolved into production-grade AAC UI with:
 * - daemon integration entrypoint
 * - fallback interaction layer
 * - gaze tracking integration
 * - inference state visualization
 * - TTS output
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AACBridge"
        private const val PERMISSIONS_REQUEST = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA
        )
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var fallbackRouter: FallbackRouter
    private lateinit var speechManager: SpeechOutputManager
    private var gazeTracker: GazeTracker? = null

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var intentLogText: TextView
    private lateinit var daemonStatusText: TextView
    private lateinit var cacheStatusText: TextView
    private lateinit var inferenceTimingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize app-layer components
        fallbackRouter = FallbackRouter()
        speechManager = SpeechOutputManager(this)

        // Build UI
        setContentView(buildLayout())

        // Request runtime permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST)
        }

        // =====================================================
        // PRESERVED: Existing JNI bootstrap / smoke-test flow
        // =====================================================
        thread {
            try {
                Log.d(TAG, "Initializing model...")
                updateStatus("MODEL: Initializing...")

                val initialized = LlamaBridge.initializeModel(
                    "/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf"
                )

                Log.d(TAG, "Model initialized: $initialized")

                if (!initialized) {
                    Log.e(TAG, "Model initialization failed")
                    updateStatus("MODEL: Failed")
                    return@thread
                }

                updateStatus("MODEL: Loaded")

                Log.d(TAG, "Running inference...")

                val output = LlamaBridge.runInference("User: Hello\nAssistant:")

                Log.d(TAG, "Inference output: $output")
                Log.d(TAG, "Inference completed successfully")

                updateStatus("MODEL: Ready")

            } catch (e: Exception) {
                Log.e(TAG, "Inference pipeline crashed", e)
                updateStatus("MODEL: Offline (fallback active)")
            }
        }

        // Start ContextDaemon background service
        try {
            startService(Intent(this, ContextDaemon::class.java))
            runOnUiThread { daemonStatusText.text = "DAEMON: Running" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ContextDaemon", e)
        }

        // Initialize gaze tracker
        initGazeTracker()
    }

    /**
     * Convenience wrapper to call kotlin.concurrent.thread
     * preserving the existing code pattern from the original
     * MainActivity.
     */
    private fun thread(block: () -> Unit) {
        kotlin.concurrent.thread(block = block)
    }

    private fun initGazeTracker() {
        gazeTracker = GazeTracker(this) { intentLabel ->
            runOnUiThread { handleIntent(intentLabel) }
        }
    }

    /**
     * Handles an AAC intent from any source:
     * gaze tracking, manual button press, or mock generator.
     */
    private fun handleIntent(intentLabel: String) {
        intentLogText.text = "Intent: ${intentLabel.uppercase(Locale.ROOT)} @ ${System.currentTimeMillis()}"

        activityScope.launch {
            val response = fallbackRouter.resolveResponse(intentLabel)
            responseText.text = response
            speechManager.speak(response)

            Log.d(TAG, "Intent: $intentLabel -> Response: $response")
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread { statusText.text = status }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (!grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Some permissions denied. GPS/BLE/Camera may be limited.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =========================================================
    // UI Construction — Programmatic Material Design
    // =========================================================

    private fun buildLayout(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Header
        content.addView(TextView(this).apply {
            text = "AACBridge"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Predictive KV-Cache Edge LLM for AAC"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 32)
        })

        // Status indicators row
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        statusText = makeStatusBadge("MODEL: Loading...")
        daemonStatusText = makeStatusBadge("DAEMON: Starting")
        cacheStatusText = makeStatusBadge("CACHE: Idle")
        statusRow.addView(statusText)
        statusRow.addView(daemonStatusText)
        statusRow.addView(cacheStatusText)
        content.addView(statusRow)

        // Section: Intent Targets
        content.addView(makeSectionLabel("EYE-GAZE TARGETS (400ms Dwell)"))

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 3
            setPadding(0, 8, 0, 24)
        }
        val targets = listOf("confirm", "reject", "scroll", "select", "call-help")
        targets.forEach { target ->
            val isEmergency = target == "call-help"
            val btn = Button(this).apply {
                text = target.uppercase(Locale.ROOT)
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                val bg = GradientDrawable().apply {
                    setColor(if (isEmergency) Color.parseColor("#DC2626") else Color.parseColor("#1E293B"))
                    cornerRadius = 16f
                    setStroke(2, Color.parseColor("#475569"))
                }
                background = bg
                setOnClickListener { handleIntent(target) }
                isAllCaps = false
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = if (isEmergency) GridLayout.spec(0, 2, 1f) else GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            grid.addView(btn, params)
        }
        content.addView(grid)

        // Section: Intent Log
        content.addView(makeSectionLabel("INTENT EVENT LOG"))
        intentLogText = TextView(this).apply {
            text = "Waiting for gaze or button input..."
            setTextColor(Color.parseColor("#10B981"))
            setTypeface(Typeface.MONOSPACE)
            textSize = 12f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(24, 24, 24, 24)
        }
        content.addView(intentLogText)
        content.addView(spacer())

        // Section: AAC Response
        content.addView(makeSectionLabel("AAC RESPONSE OUTPUT"))
        responseText = TextView(this).apply {
            text = "Ready — trigger an intent to generate response."
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(24, 32, 24, 32)
            minHeight = 120
        }
        content.addView(responseText)
        content.addView(spacer())

        // Section: Inference Timing (future benchmark integration point)
        content.addView(makeSectionLabel("LATENCY METRICS"))
        inferenceTimingText = TextView(this).apply {
            text = "TTFT: -- ms  |  Cache: -- ms  |  Daemon: -- ms"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(24, 16, 24, 16)
        }
        content.addView(inferenceTimingText)

        root.addView(content)
        return root
    }

    private fun makeStatusBadge(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 8f
            }
            background = bg
            setPadding(16, 6, 16, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 12, 0) }
        }
    }

    private fun makeSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
    }

    private fun spacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        gazeTracker?.close()
        speechManager.shutdown()
    }
}