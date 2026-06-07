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
import kotlin.concurrent.withLock
import com.aacbridge.gaze.GazeTracker
import com.aacbridge.gaze.CalibrationManager
import com.aacbridge.gaze.DwellOverlayView
import com.aacbridge.fusion.FusionInput
import com.aacbridge.inference.LlamaBridge
import kotlinx.coroutines.*
import java.util.Locale
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.Executors

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
        // JNI bootstrap with correct initialization ordering:
        //
        // 1. initializeBackend()  — ggml allocator setup
        // 2. initializeModel()    — GGUF load + ctx creation
        // 3. modelReady = true    — gate for daemon/primer
        // 4. Start ContextDaemon  — ONLY after model is ready
        // 5. Smoke-test inference — validates full pipeline
        //
        // CRITICAL: ContextDaemon must NOT start before model
        // is initialized. Previous code started it immediately
        // in onCreate(), causing SIGBUS when the daemon's sweep
        // called runInference() on a null native context.
        // =====================================================
        thread {
            try {
                val app = application as AACBridgeApplication
                val lock = app.appContainer.engineLock

                // --- Step 1: Backend init ---
                Log.d(TAG, "[INIT] Step 1: initializeBackend() starting")
                updateStatus("MODEL: Initializing backend...")

                lock.withLock {
                    LlamaBridge.initializeBackend()
                }

                Log.d(TAG, "[INIT] Step 1: initializeBackend() complete")

                // --- Step 2: Model load ---
                Log.d(TAG, "[INIT] Step 2: initializeModel() starting")
                updateStatus("MODEL: Loading model...")

                val initialized = lock.withLock {
                    LlamaBridge.initializeModel(
                        "/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf"
                    )
                }

                Log.d(TAG, "[INIT] Step 2: initializeModel() = $initialized")

                if (!initialized) {
                    Log.e(TAG, "[INIT] Model initialization failed — daemon will NOT start")
                    updateStatus("MODEL: Failed")
                    return@thread
                }

                // --- Step 3: Model ready gate ---
                app.appContainer.modelReady.set(true)
                Log.d(TAG, "[INIT] Step 3: modelReady = true")
                updateStatus("MODEL: Loaded")

                // --- Step 4: Start ContextDaemon ---
                Log.d(TAG, "[INIT] Step 4: Starting ContextDaemon")
                try {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, ContextDaemon::class.java)
                    )
                    runOnUiThread { daemonStatusText.text = "DAEMON: Running" }
                    Log.d(TAG, "[INIT] Step 4: ContextDaemon started")
                } catch (e: Exception) {
                    Log.e(TAG, "[INIT] Failed to start ContextDaemon", e)
                }

                // --- Step 5: Smoke-test inference ---
                Log.d(TAG, "[INIT] Step 5: Smoke-test inference starting")

                val output = lock.withLock {
                    LlamaBridge.runInference("User: Hello\nAssistant:")
                }

                Log.d(TAG, "[INIT] Step 5: Inference output: $output")
                Log.d(TAG, "[INIT] Step 5: Inference completed successfully")

                updateStatus("MODEL: Ready")

            } catch (e: Exception) {
                Log.e(TAG, "Inference pipeline crashed", e)
                updateStatus("MODEL: Offline (fallback active)")
            }
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
        val app = application as AACBridgeApplication
        val calibrationManager = CalibrationManager(this)
        
        val dwellOverlay = DwellOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                gravity = Gravity.CENTER
            }
        }
        
        val rootGroup = findViewById<android.view.ViewGroup>(android.R.id.content)
        rootGroup.addView(dwellOverlay)
        
        gazeTracker = GazeTracker(
            context = this,
            onGazeIntent = { intentLabel ->
                runOnUiThread { handleIntent(intentLabel) }
            },
            onGazeVector = { vector ->
                // Wire fusion model inference
                // Provide mock EMG embedding since Medha's hardware isn't connected yet
                val mockEmg = FloatArray(64) { 0f }
                val input = FusionInput(mockEmg, vector, "unknown", System.currentTimeMillis())
                try {
                    val fusionResult = app.appContainer.fusionInference.fuse(input)
                    // Throttled: fusion runs at ~30Hz but log at ≤1Hz
                    // to prevent logcat buffer saturation
                } catch (e: Exception) {
                    Log.e(TAG, "Fusion inference failed", e)
                }
            },
            onDwellProgress = { progress ->
                runOnUiThread { dwellOverlay.updateProgress(progress) }
            },
            onOcclusionStateChanged = { occluded ->
                runOnUiThread {
                    if (occluded) {
                        updateStatus("GAZE: Camera Occluded")
                    } else {
                        updateStatus("MODEL: Ready")
                    }
                }
            },
            calibrationManager = calibrationManager
        )
        startCameraAnalysis()
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        gazeTracker?.processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e(TAG, "Camera analysis binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
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