package com.aacbridge.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Trace-driven evaluation of the deployed routing +
 * residency rules (see PredictionEvaluator for what is and
 * is not simulated).
 *
 * Writes:
 *   evaluation/prediction/tables/prediction_eval.csv
 *   evaluation/prediction/tables/hysteresis_eval.csv
 *   evaluation/prediction/tables/ground_truth_distribution.csv
 *
 * Seeds are fixed; rerunning reproduces the files exactly.
 *
 * IMPORTANT: controlled/synthetic traces, not recordings.
 */
class PredictionEvaluationTest {

    companion object {
        private const val NUM_DAYS = 100
        private const val SEED = 42L

        /** Single-modality ablations: time weight ~0 (not exactly 0: StateRouter requires > 0). */
        private const val ABLATION_TIME_WEIGHT = 0.001

        private val HOSPITAL = TraceGenerator.STATE_ANCHORS.getValue("hospital_ward")
        private val HOSPITAL_BEACONS = TraceGenerator.STATE_BLE_ANCHORS.getValue("hospital_ward")

        val NORMAL = TraceConfig()

        val HIGH_NOISE = TraceConfig(
            scheduleShiftHours = 1.5,
            gpsNoiseKm = 0.2,
            gpsDropoutProb = 0.3,
            gpsAccuracyMean = 50.0,
            gpsAccuracySd = 30.0,
            bleDetectionProb = 0.5,
            bleDropoutProb = 0.4
        )
    }

    private val states = listOf(
        ContextState("home_morning", 8.0, 28.6139, 77.2090,
            mapOf("AA:BB:CC:DD:EE:01" to 0.8, "AA:BB:CC:DD:EE:02" to 0.5)),
        ContextState("home_evening", 19.0, 28.6139, 77.2090,
            mapOf("AA:BB:CC:DD:EE:01" to 0.8, "AA:BB:CC:DD:EE:03" to 0.6)),
        ContextState("hospital_ward", 11.0, 28.5672, 77.2100,
            mapOf("AA:BB:CC:DD:EE:04" to 0.9, "AA:BB:CC:DD:EE:05" to 0.7)),
        ContextState("therapy_room", 14.0, 28.5672, 77.2105,
            mapOf("AA:BB:CC:DD:EE:06" to 0.85)),
        ContextState("caregiver_visit", 16.5, 28.6139, 77.2090,
            mapOf("AA:BB:CC:DD:EE:07" to 0.95, "AA:BB:CC:DD:EE:01" to 0.8))
    )

    private val timeScorer = TimeScorer(sigmaHours = 2.0)
    private val gpsScorer = GPSScorer(lambdaKm = 0.1, lambdaAccuracyMeters = 50.0)
    private val bleScorer = BLEScorer(r0 = -70.0, k = 0.1)

    private val fullRouter = StateRouter(timeScorer, gpsScorer, bleScorer)
    private val ablationRouter = StateRouter(timeScorer, gpsScorer, bleScorer,
        timeBaselineWeightOverride = ABLATION_TIME_WEIGHT)

    private val generator = TraceGenerator(seed = SEED)
    private val evaluator = PredictionEvaluator(states)

    private fun traces(config: TraceConfig) =
        (0 until NUM_DAYS).map { generator.generateDayTrace(it, config) }

    private fun isHome(p: TracePoint) = p.groundTruthStateId.startsWith("home")

    // ---------------------------------------------------------
    // Spoofing transforms (applied only while the user is at a
    // home_* context).
    // ---------------------------------------------------------

    /** Forged GPS that passes LocationValidator (e.g. rooted device). */
    private fun gpsSpoofUndetected(p: TracePoint): SensorSnapshot =
        if (!isHome(p)) p.snapshot
        else p.snapshot.copy(
            location = GpsLocation(HOSPITAL.first, HOSPITAL.second, accuracyMeters = 5.0f)
        )

    /** Mock-provider spoof caught by LocationValidator: GPS dropped. */
    private fun gpsSpoofMockRejected(p: TracePoint): SensorSnapshot =
        if (!isHome(p)) p.snapshot else p.snapshot.copy(location = null)

    /** Cloned hospital beacons advertised near the user at home. */
    private fun bleSpoof(p: TracePoint): SensorSnapshot =
        if (!isHome(p)) p.snapshot
        else p.snapshot.copy(
            detectedBleDevices = p.snapshot.detectedBleDevices +
                HOSPITAL_BEACONS.associateWith { Rssi(-55) }
        )

    @Test
    fun `prediction evaluation across baselines and scenarios`() {

        val normal = traces(NORMAL)
        val highNoise = traces(HIGH_NOISE)
        val staleGps = traces(NORMAL.copy(gpsDropoutProb = 0.7))
        val noSensors = traces(NORMAL.copy(gpsDropoutProb = 1.0, bleDropoutProb = 1.0))
        val noBle = traces(NORMAL.copy(gpsDropoutProb = 0.05, bleDropoutProb = 1.0))
        val noGps = traces(NORMAL.copy(gpsDropoutProb = 1.0))

        val results = listOf(
            evaluator.evaluateOracle("ORACLE_ALWAYS_RESIDENT", normal),
            evaluator.evaluateReactiveLru("REACTIVE_LRU_NO_SENSING", normal),
            evaluator.evaluateRandom("RANDOM_RANKING", normal, seed = SEED),
            evaluator.evaluateRouter("TIME_ONLY", fullRouter, noSensors),
            evaluator.evaluateRouter("GPS_ONLY", ablationRouter, noBle),
            evaluator.evaluateRouter("BLE_ONLY", ablationRouter, noGps),
            evaluator.evaluateRouter("FULL", fullRouter, normal),
            evaluator.evaluateRouter("FULL_NO_HYSTERESIS", fullRouter, normal, margin = 0.0),
            evaluator.evaluateRouter("FULL_STALE_GPS_70PCT", fullRouter, staleGps),
            evaluator.evaluateRouter("FULL_HIGH_NOISE", fullRouter, highNoise),
            evaluator.evaluateRouter("FULL_GPS_SPOOF_UNDETECTED", fullRouter, normal,
                transform = ::gpsSpoofUndetected),
            evaluator.evaluateRouter("FULL_GPS_SPOOF_MOCK_REJECTED", fullRouter, normal,
                transform = ::gpsSpoofMockRejected),
            evaluator.evaluateRouter("FULL_BLE_SPOOF", fullRouter, normal,
                transform = ::bleSpoof)
        )

        writeCsv("prediction_eval.csv", EvalResult.CSV_HEADER, results.map { it.toCsv() })
        println(EvalResult.CSV_HEADER)
        results.forEach { println(it.toCsv()) }

        val byName = results.associateBy { it.configName }

        for (r in results) {
            assertEquals(NUM_DAYS * 64, r.interactions)
            assertEquals(r.interactions, r.correct + r.wrongContext + r.coldMiss)
        }

        assertEquals(1.0, byName.getValue("ORACLE_ALWAYS_RESIDENT").correctRate, 0.0)
        assertTrue(byName.getValue("FULL").correctRate > byName.getValue("RANDOM_RANKING").correctRate)
    }

    @Test
    fun `hysteresis margin sweep`() {

        val margins = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.30, 0.40, 0.50, 0.70)
        val rows = mutableListOf<String>()

        for ((label, config) in listOf("NORMAL" to NORMAL, "HIGH_NOISE" to HIGH_NOISE)) {
            val t = traces(config)
            for (m in margins) {
                val r = evaluator.evaluateRouter("FULL_$label", fullRouter, t, margin = m)
                rows += "$label,%.2f,".format(m) + r.toCsv()
            }
        }

        val header = "trace,margin," + EvalResult.CSV_HEADER
        writeCsv("hysteresis_eval.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    @Test
    fun `ground truth distribution`() {

        val counts = traces(NORMAL).flatten()
            .filter { it.isInteraction }
            .groupingBy { it.groundTruthStateId }
            .eachCount()
        val total = counts.values.sum()

        val rows = counts.entries.sortedByDescending { it.value }
            .map { "${it.key},${it.value},%.6f".format(it.value.toDouble() / total) }

        writeCsv("ground_truth_distribution.csv", "state,interactions,fraction", rows)
        rows.forEach(::println)
        assertEquals(NUM_DAYS * 64, total)
    }

    // =================================================================
    // Phase 18: Multi-seed evaluation (20 seeds × 100 days)
    // =================================================================
    @Test
    fun `multi-seed evaluation with bootstrap CIs`() {
        val seeds = (1L..20L).toList()
        val rows = mutableListOf<String>()

        for (seed in seeds) {
            val gen = TraceGenerator(seed = seed)
            val dayTraces = (0 until NUM_DAYS).map { gen.generateDayTrace(it, NORMAL) }
            val eval = PredictionEvaluator(states)

            val full = eval.evaluateRouter("FULL_SEED_$seed", fullRouter, dayTraces)
            val lru = eval.evaluateReactiveLru("LRU_SEED_$seed", dayTraces)
            val random = eval.evaluateRandom("RANDOM_SEED_$seed", dayTraces, seed = seed)

            rows += "$seed,FULL,${full.correctRate},${full.wrongContextRate},${full.coldMissRate},${full.loadsPerDay}"
            rows += "$seed,LRU,${lru.correctRate},${lru.wrongContextRate},${lru.coldMissRate},${lru.loadsPerDay}"
            rows += "$seed,RANDOM,${random.correctRate},${random.wrongContextRate},${random.coldMissRate},${random.loadsPerDay}"
        }

        val header = "seed,policy,correct,wrong_context,cold_miss,loads_per_day"
        writeCsv("multi_seed_eval.csv", header, rows)
        println(header)
        rows.forEach(::println)

        // Print bootstrap summary
        val fullCorrect = rows.filter { it.contains(",FULL,") }.map {
            it.split(",")[2].toDouble()
        }
        val lruCorrect = rows.filter { it.contains(",LRU,") }.map {
            it.split(",")[2].toDouble()
        }
        println("\n--- Bootstrap Summary ---")
        println("FULL correct: mean=%.4f, min=%.4f, max=%.4f".format(
            fullCorrect.average(), fullCorrect.min(), fullCorrect.max()))
        println("LRU correct: mean=%.4f, min=%.4f, max=%.4f".format(
            lruCorrect.average(), lruCorrect.min(), lruCorrect.max()))
    }

    // =================================================================
    // Phase 20: K residency ablation (K=1, K=2, K=3)
    // =================================================================
    @Test
    fun `K residency ablation`() {
        val normal = traces(NORMAL)
        val rows = mutableListOf<String>()

        for (k in 1..3) {
            val eval = PredictionEvaluator(states, capacity = k)
            val r = eval.evaluateRouter("FULL_K$k", fullRouter, normal)
            rows += "$k,${r.correctRate},${r.wrongContextRate},${r.coldMissRate},${r.loadsPerDay},${r.evictionsPerDay}"
        }

        val header = "K,correct,wrong_context,cold_miss,loads_per_day,evictions_per_day"
        writeCsv("k_ablation.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    // =================================================================
    // Phase 19: Markov/successor baseline
    // =================================================================
    @Test
    fun `markov successor baseline`() {
        val normal = traces(NORMAL)
        val eval = PredictionEvaluator(states)

        val markov = eval.evaluateMarkov("MARKOV_SUCCESSOR", normal, trainDays = 50)
        val full = eval.evaluateRouter("FULL", fullRouter, normal)
        val lru = eval.evaluateReactiveLru("REACTIVE_LRU", normal)

        val rows = listOf(
            "FULL,${full.correctRate},${full.wrongContextRate},${full.coldMissRate},${full.loadsPerDay}",
            "MARKOV_SUCCESSOR,${markov.correctRate},${markov.wrongContextRate},${markov.coldMissRate},${markov.loadsPerDay}",
            "REACTIVE_LRU,${lru.correctRate},${lru.wrongContextRate},${lru.coldMissRate},${lru.loadsPerDay}"
        )

        val header = "policy,correct,wrong_context,cold_miss,loads_per_day"
        writeCsv("markov_baseline.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    // =================================================================
    // Phase 16: Readiness lag experiment
    // =================================================================
    @Test
    fun `readiness lag experiment`() {
        val normal = traces(NORMAL)
        val lags = listOf(0.5, 1.0, 2.0, 5.0, 15.0) // minutes after context transition
        val rows = mutableListOf<String>()

        for (lag in lags) {
            // For each day trace, find context transitions and test
            // interactions that occur at exactly 'lag' minutes after
            var totalInteractions = 0
            var correctCount = 0
            var wrongCount = 0
            var coldCount = 0

            for (dayTrace in normal) {
                val resident = mutableSetOf<String>()
                var lastTransitionHour: Double? = null
                var prevGt: String? = null

                for (point in dayTrace) {
                    val ranked = fullRouter.getScoredStates(point.snapshot, states)
                    val plan = ResidencyPolicy.plan(ranked, resident, capacity = 3)
                    for (id in plan.toEvict) resident.remove(id)
                    for (id in plan.toLoad) resident.add(id)

                    // Detect transition
                    if (prevGt != null && point.groundTruthStateId != prevGt) {
                        lastTransitionHour = point.timestampHour
                    }

                    // Check if this is an interaction at the right lag
                    if (point.isInteraction && lastTransitionHour != null) {
                        val minutesSinceTransition = (point.timestampHour - lastTransitionHour) * 60.0
                        // Within a window of [lag-0.5, lag+0.5]
                        if (minutesSinceTransition >= lag - 0.5 && minutesSinceTransition < lag + 0.5) {
                            totalInteractions++
                            val served = ResidencyPolicy.selectForInference(ranked, resident)
                            when {
                                served == null -> coldCount++
                                served == point.groundTruthStateId -> correctCount++
                                else -> wrongCount++
                            }
                        }
                    }
                    prevGt = point.groundTruthStateId
                }
            }

            if (totalInteractions > 0) {
                rows += "%.1f,%d,%.4f,%.4f,%.4f".format(
                    lag, totalInteractions,
                    correctCount.toDouble() / totalInteractions,
                    wrongCount.toDouble() / totalInteractions,
                    coldCount.toDouble() / totalInteractions
                )
            }
        }

        val header = "lag_minutes,interactions,correct,wrong_context,cold_miss"
        writeCsv("readiness_lag.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    // =================================================================
    // Phase 21: Abstention / risk-coverage sweep
    // =================================================================
    @Test
    fun `abstention risk-coverage sweep`() {
        val normal = traces(NORMAL)
        val taus = listOf(0.0, 0.02, 0.05, 0.10, 0.15, 0.20)
        val rows = mutableListOf<String>()

        for (tau in taus) {
            val eval = PredictionEvaluator(states)
            val r = eval.evaluateWithAbstention("TAU_%.2f".format(tau),
                fullRouter, normal, tau = tau)
            val coverage = r.correctRate + r.wrongContextRate // non-abstained
            rows += "%.2f,%.4f,%.4f,%.4f,%.4f".format(
                tau, coverage, r.correctRate, r.wrongContextRate, r.coldMissRate)
        }

        val header = "tau,coverage,correct,wrong_context,cold_miss"
        writeCsv("risk_coverage.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    // =================================================================
    // Phase 17: Independent trace schedules
    // =================================================================
    @Test
    fun `independent trace schedules`() {
        val seeds = listOf(100L, 200L, 300L, 400L, 500L)
        val rows = mutableListOf<String>()

        // Shifted schedules with different jitter
        val configs = listOf(
            "SHIFTED_1H" to TraceConfig(scheduleShiftHours = 1.0),
            "SHIFTED_2H" to TraceConfig(scheduleShiftHours = 2.0),
            "VARIABLE_DWELL" to TraceConfig(scheduleShiftHours = 0.5, interactionEveryMinutes = 7.0),
            "HIGH_JITTER" to TraceConfig(scheduleShiftHours = 1.5, gpsNoiseKm = 0.15)
        )

        for ((label, config) in configs) {
            for (seed in seeds) {
                val gen = TraceGenerator(seed = seed)
                val dayTraces = (0 until NUM_DAYS).map { gen.generateDayTrace(it, config) }
                val eval = PredictionEvaluator(states)
                val r = eval.evaluateRouter("${label}_SEED$seed", fullRouter, dayTraces)
                rows += "$label,$seed,${r.correctRate},${r.wrongContextRate},${r.coldMissRate},${r.loadsPerDay}"
            }
        }

        val header = "config,seed,correct,wrong_context,cold_miss,loads_per_day"
        writeCsv("independent_traces.csv", header, rows)
        println(header)
        rows.forEach(::println)
    }

    private fun writeCsv(name: String, header: String, rows: List<String>) {
        val dir = File(repoRoot(), "evaluation/prediction/tables").apply { mkdirs() }
        File(dir, name).writeText((listOf(header) + rows).joinToString("\n", postfix = "\n"))
    }

    /** Walks up from the working directory to the repo root. */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "evaluation").isDirectory && File(dir, "android").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repo root (with evaluation/ and android/) not found from ${File("").absolutePath}")
    }
}

