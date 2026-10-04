package com.aacbridge.router

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Prediction evaluation test suite.
 *
 * Evaluates prediction accuracy across multiple routing
 * configurations using the production StateRouter and
 * independently generated controlled sensor traces.
 *
 * Configurations tested:
 * 1. FULL: time + GPS + BLE (production configuration)
 * 2. TIME_ONLY: GPS/BLE unavailable
 * 3. GPS_ONLY: BLE unavailable, time weight zeroed
 * 4. BLE_ONLY: GPS unavailable, time weight zeroed
 * 5. RANDOM: uniform random baseline
 *
 * All traces use seed=42 for reproducibility.
 *
 * IMPORTANT: These are controlled/synthetic traces,
 * not real-world sensor recordings.
 */
class PredictionEvaluationTest {

    private lateinit var states: List<ContextState>
    private lateinit var generator: TraceGenerator

    // Production configuration
    private val timeScorer = TimeScorer(sigmaHours = 2.0)
    private val gpsScorer = GPSScorer(lambdaKm = 0.1, lambdaAccuracyMeters = 50.0)
    private val bleScorer = BLEScorer(r0 = -70.0, k = 0.1)

    private val fullRouter = StateRouter(timeScorer, gpsScorer, bleScorer)

    companion object {
        private const val NUM_EPISODES = 100
        private const val SEED = 42L
    }

    @Before
    fun setup() {
        states = listOf(
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
        generator = TraceGenerator(seed = SEED)
    }

    // ================================================================
    // FULL FUSION (production configuration)
    // ================================================================

    @Test
    fun `FULL fusion prediction evaluation`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "FULL_FUSION",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsNoiseKm = 0.05,
            gpsDropoutProb = 0.1,
            bleDetectionProb = 0.8,
            bleDropoutProb = 0.15
        )
        printResults(results)
        writeResultsCsv(results, "full_fusion")

        // Sanity: full fusion should beat random
        assertTrue("Hit@1 should exceed random baseline (20%)",
            results.hit1 > 0.20)
        assertTrue("Hit@3 should exceed random baseline (60%)",
            results.hit3 > 0.60)
    }

    // ================================================================
    // TIME-ONLY (GPS and BLE unavailable)
    // ================================================================

    @Test
    fun `TIME_ONLY prediction evaluation`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "TIME_ONLY",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsDropoutProb = 1.0,  // No GPS
            bleDropoutProb = 1.0   // No BLE
        )
        printResults(results)
        writeResultsCsv(results, "time_only")
    }

    // ================================================================
    // GPS-ONLY (BLE unavailable, TIME STILL ACTIVE via wt=0.4)
    // NOTE: This is NOT a true single-modality ablation.
    // Time weight leaks into scoring. See GPS_ONLY_TRUE below.
    // ================================================================

    @Test
    fun `GPS_ONLY prediction evaluation with time leakage`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "GPS_WITH_TIME",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsNoiseKm = 0.05,
            gpsDropoutProb = 0.05,
            bleDropoutProb = 1.0  // No BLE
        )
        printResults(results)
        writeResultsCsv(results, "gps_with_time")
    }

    // ================================================================
    // TRUE GPS-ONLY (time weight near-zero, BLE unavailable)
    // ================================================================

    @Test
    fun `TRUE GPS_ONLY prediction evaluation`() {
        // Create router with near-zero time weight to isolate GPS
        val gpsOnlyRouter = StateRouter(
            timeScorer = timeScorer,
            gpsScorer = gpsScorer,
            bleScorer = bleScorer,
            timeBaselineWeightOverride = 0.001  // epsilon to avoid div-by-zero
        )
        val evaluator = PredictionEvaluator(gpsOnlyRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "GPS_ONLY",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsNoiseKm = 0.05,
            gpsDropoutProb = 0.05,
            bleDropoutProb = 1.0  // No BLE
        )
        printResults(results)
        writeResultsCsv(results, "gps_only")
    }

    // ================================================================
    // BLE-ONLY (GPS unavailable, TIME STILL ACTIVE via wt=0.4)
    // NOTE: This is NOT a true single-modality ablation.
    // ================================================================

    @Test
    fun `BLE_ONLY prediction evaluation with time leakage`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "BLE_WITH_TIME",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsDropoutProb = 1.0,  // No GPS
            bleDetectionProb = 0.8,
            bleDropoutProb = 0.15
        )
        printResults(results)
        writeResultsCsv(results, "ble_with_time")
    }

    // ================================================================
    // TRUE BLE-ONLY (time weight near-zero, GPS unavailable)
    // ================================================================

    @Test
    fun `TRUE BLE_ONLY prediction evaluation`() {
        val bleOnlyRouter = StateRouter(
            timeScorer = timeScorer,
            gpsScorer = gpsScorer,
            bleScorer = bleScorer,
            timeBaselineWeightOverride = 0.001
        )
        val evaluator = PredictionEvaluator(bleOnlyRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "BLE_ONLY",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsDropoutProb = 1.0,  // No GPS
            bleDetectionProb = 0.8,
            bleDropoutProb = 0.15
        )
        printResults(results)
        writeResultsCsv(results, "ble_only")
    }

    // ================================================================
    // RANDOM BASELINE
    // ================================================================

    @Test
    fun `RANDOM baseline prediction evaluation`() {
        val rng = java.util.Random(SEED)
        val stateIds = states.map { it.stateId }

        var totalInteractions = 0
        var hit1 = 0
        var hit3 = 0
        var coldMiss = 0

        for (episode in 0 until NUM_EPISODES) {
            val trace = generator.generateDayTrace(episodeId = episode)
            val residentSet = mutableListOf<String>()

            for (point in trace) {
                // Random selection
                val shuffled = stateIds.shuffled(rng)
                val randomTop3 = shuffled.take(3)
                val randomTop1 = shuffled.first()

                // Update resident set
                residentSet.clear()
                residentSet.addAll(randomTop3)

                totalInteractions++
                if (randomTop1 == point.groundTruthStateId) hit1++
                if (point.groundTruthStateId in randomTop3) hit3++
                if (point.groundTruthStateId !in residentSet) coldMiss++
            }
        }

        val results = PredictionResults(
            configName = "RANDOM",
            episodes = emptyList(),
            totalInteractions = totalInteractions,
            hit1 = hit1.toDouble() / totalInteractions,
            hit3 = hit3.toDouble() / totalInteractions,
            coldMissRate = coldMiss.toDouble() / totalInteractions,
            wrongContextRate = 0.0,
            meanContextSwitches = 0.0
        )
        printResults(results)
        writeResultsCsv(results, "random")

        // Analytical baselines for 5 states / 3 slots
        // Random Hit@1 = 1/5 = 0.20, Random Hit@3 = 3/5 = 0.60
        println("  Analytical uniform Hit@1 = 0.200 (1/5)")
        println("  Analytical uniform Hit@3 = 0.600 (3/5)")
    }

    // ================================================================
    // NOISY CONDITIONS (stress test)
    // ================================================================

    @Test
    fun `FULL fusion under high noise`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "FULL_HIGH_NOISE",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 1.5,
            gpsNoiseKm = 0.2,
            gpsDropoutProb = 0.3,
            gpsAccuracyMean = 50.0,
            gpsAccuracySd = 30.0,
            bleDetectionProb = 0.5,
            bleDropoutProb = 0.4
        )
        printResults(results)
        writeResultsCsv(results, "full_high_noise")
    }

    // ================================================================
    // GPS SPOOFED CONDITION (security evaluation)
    // ================================================================

    @Test
    fun `GPS spoofed to wrong location`() {
        // Simulate GPS reporting hospital coordinates when user is at home
        val spoofedGenerator = object {
            fun generateSpoofedTrace(episodeId: Int): List<TracePoint> {
                val baseTrace = generator.generateDayTrace(episodeId = episodeId)
                val rng = java.util.Random(SEED + episodeId + 1000)

                return baseTrace.map { point ->
                    if (point.groundTruthStateId.startsWith("home")) {
                        // Spoof GPS to hospital location
                        val spoofedSnapshot = SensorSnapshot(
                            currentHourDecimal = point.snapshot.currentHourDecimal,
                            location = GpsLocation(
                                lat = 28.5672 + rng.nextGaussian() * 0.0001,
                                lng = 77.2100 + rng.nextGaussian() * 0.0001,
                                accuracyMeters = 5.0f
                            ),
                            detectedBleDevices = point.snapshot.detectedBleDevices
                        )
                        point.copy(snapshot = spoofedSnapshot)
                    } else {
                        point
                    }
                }
            }
        }

        val evaluator = PredictionEvaluator(fullRouter, states)
        var totalInteractions = 0
        var hit1 = 0
        var hit3 = 0
        var coldMiss = 0
        var wrongContext = 0
        var contextSwitches = 0

        for (episode in 0 until NUM_EPISODES) {
            val trace = spoofedGenerator.generateSpoofedTrace(episode)
            val result = evaluator.evaluateEpisode(trace, episode)
            totalInteractions += result.totalInteractions
            hit1 += result.hit1Count
            hit3 += result.hit3Count
            coldMiss += result.coldMissCount
            wrongContext += result.wrongContextCount
            contextSwitches += result.contextSwitchCount
        }

        val results = PredictionResults(
            configName = "GPS_SPOOFED",
            episodes = emptyList(),
            totalInteractions = totalInteractions,
            hit1 = hit1.toDouble() / totalInteractions,
            hit3 = hit3.toDouble() / totalInteractions,
            coldMissRate = coldMiss.toDouble() / totalInteractions,
            wrongContextRate = wrongContext.toDouble() / totalInteractions,
            meanContextSwitches = contextSwitches.toDouble() / NUM_EPISODES
        )
        printResults(results)
        writeResultsCsv(results, "gps_spoofed")
    }

    // ================================================================
    // STALE GPS CONDITION
    // ================================================================

    @Test
    fun `FULL fusion with stale GPS`() {
        val evaluator = PredictionEvaluator(fullRouter, states)
        // Simulate high GPS dropout = stale/unavailable fixes
        val results = evaluator.evaluate(
            generator = generator,
            numEpisodes = NUM_EPISODES,
            configName = "STALE_GPS",
            interactionIntervalMinutes = 15.0,
            timeNoiseHours = 0.5,
            gpsDropoutProb = 0.7,  // 70% GPS unavailable
            bleDetectionProb = 0.8,
            bleDropoutProb = 0.15
        )
        printResults(results)
        writeResultsCsv(results, "stale_gps")
    }

    // ================================================================
    // HYSTERESIS MARGIN SWEEP (DriftDetector evaluation)
    // ================================================================

    @Test
    fun `HYSTERESIS sweep across delta values`() {
        val deltas = listOf(0.00, 0.05, 0.10, 0.15, 0.20)
        println("\n=== HYSTERESIS SWEEP (HIGH NOISE) ===")
        println("delta,switches_per_day,hit1,hit3_residency,cold_miss")

        for (delta in deltas) {
            var totalInteractions = 0
            var totalHit1 = 0
            var totalHit3 = 0
            var totalColdMiss = 0
            var totalSwaps = 0

            for (episode in 0 until NUM_EPISODES) {
                val trace = generator.generateDayTrace(
                    episodeId = episode,
                    interactionIntervalMinutes = 15.0,
                    timeNoiseHours = 1.5,
                    gpsNoiseKm = 0.2,
                    gpsDropoutProb = 0.3,
                    gpsAccuracyMean = 50.0,
                    gpsAccuracySd = 30.0,
                    bleDetectionProb = 0.5,
                    bleDropoutProb = 0.4
                )

                val residentStates = mutableListOf<String>()

                for (point in trace) {
                    val scored = fullRouter.getScoredStates(point.snapshot, states)
                    val scoreMap = scored.toMap()
                    val best = scored.firstOrNull() ?: continue
                    val top1 = best.first

                    // Populate initial resident states
                    if (residentStates.size < 3) {
                        for (s in scored.take(3)) {
                            if (s.first !in residentStates && residentStates.size < 3) {
                                residentStates.add(s.first)
                            }
                        }
                    } else if (top1 !in residentStates) {
                        val weakest = residentStates.minByOrNull { scoreMap[it] ?: 0.0 }
                        val weakestScore = if (weakest != null) scoreMap[weakest] ?: 0.0 else 0.0
                        if ((best.second - weakestScore) > delta) {
                            residentStates.remove(weakest)
                            residentStates.add(top1)
                            totalSwaps++
                        }
                    }

                    totalInteractions++
                    val gt = point.groundTruthStateId
                    if (top1 == gt) totalHit1++
                    if (gt in residentStates) totalHit3++
                    if (gt !in residentStates) totalColdMiss++
                }
            }

            val swapsPerDay = totalSwaps.toDouble() / NUM_EPISODES
            val hit1 = totalHit1.toDouble() / totalInteractions
            val hit3 = totalHit3.toDouble() / totalInteractions
            val cold = totalColdMiss.toDouble() / totalInteractions

            println(String.format("DELTA,%.2f,%.2f,%.4f,%.4f,%.4f", delta, swapsPerDay, hit1, hit3, cold))
        }
    }

    // ================================================================
    // STATE DISTRIBUTION ANALYSIS
    // ================================================================

    @Test
    fun `state distribution analysis`() {
        val allTracePoints = (0 until NUM_EPISODES).flatMap { i ->
            generator.generateDayTrace(episodeId = i)
        }

        val distribution = allTracePoints
            .groupBy { it.groundTruthStateId }
            .mapValues { (_, points) -> points.size }

        val total = allTracePoints.size

        println("\n=== STATE DISTRIBUTION ===")
        println("Total interactions: $total")
        for ((stateId, count) in distribution.entries.sortedByDescending { it.value }) {
            val pct = count.toDouble() / total * 100
            println("  $stateId: $count (${String.format("%.1f", pct)}%)")
        }

        // GPS coordinate sharing analysis
        println("\n=== GPS COORDINATE SHARING ===")
        println("  home_morning, home_evening, caregiver_visit: SAME GPS (28.6139, 77.2090)")
        println("  hospital_ward, therapy_room: SIMILAR GPS (28.5672, ~77.210x)")
        println("  GPS alone CANNOT distinguish home_morning vs home_evening vs caregiver_visit")
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private fun printResults(results: PredictionResults) {
        println("\n=== ${results.configName} ===")
        println("  Total interactions: ${results.totalInteractions}")
        println("  Hit@1:     ${String.format("%.4f", results.hit1)} (${String.format("%.1f", results.hit1 * 100)}%)")
        println("  Hit@3:     ${String.format("%.4f", results.hit3)} (${String.format("%.1f", results.hit3 * 100)}%)")
        println("  Cold miss: ${String.format("%.4f", results.coldMissRate)} (${String.format("%.1f", results.coldMissRate * 100)}%)")
        println("  Wrong ctx: ${String.format("%.4f", results.wrongContextRate)} (${String.format("%.1f", results.wrongContextRate * 100)}%)")
        if (results.episodes.isNotEmpty()) {
            println("  Ctx switches/day: ${String.format("%.1f", results.meanContextSwitches)}")
        }
    }

    private fun writeResultsCsv(results: PredictionResults, filename: String) {
        // Write to test output for extraction
        println("CSV,$filename,${results.configName},${results.totalInteractions},${String.format("%.6f", results.hit1)},${String.format("%.6f", results.hit3)},${String.format("%.6f", results.coldMissRate)},${String.format("%.6f", results.wrongContextRate)},${String.format("%.2f", results.meanContextSwitches)}")
    }
}
