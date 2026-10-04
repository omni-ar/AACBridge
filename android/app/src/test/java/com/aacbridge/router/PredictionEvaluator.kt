package com.aacbridge.router

/**
 * Controlled sensor trace generator and prediction evaluator.
 *
 * PURPOSE:
 * Evaluates the StateRouter's prediction accuracy under
 * controlled/synthetic sensor conditions to measure:
 * - Hit@1: top-ranked state matches ground truth
 * - Hit@3: ground truth is in the top-3 resident set
 * - Cold miss: ground truth not resident at interaction time
 * - Wrong-context rate: inference uses incorrect context
 *
 * METHODOLOGY:
 * The trace generator produces SensorSnapshot sequences with
 * independently sampled noise, NOT generated from the scoring
 * functions, to avoid circular evaluation.
 *
 * The actual production StateRouter is used for scoring.
 *
 * TRACE STRUCTURE:
 * Each trace episode represents one day (24 hours) of
 * simulated AAC device usage. The ground-truth context
 * follows a deterministic schedule with configurable
 * transition times. Sensor observations are independently
 * perturbed.
 *
 * IMPORTANT:
 * These traces are controlled/synthetic. They are NOT
 * real-world sensor recordings from AAC users.
 */

/**
 * Single point in a sensor trace with ground-truth label.
 */
data class TracePoint(
    val timestampHour: Double,
    val groundTruthStateId: String,
    val snapshot: SensorSnapshot
)

/**
 * Results from evaluating a single trace episode.
 */
data class EpisodeResult(
    val episodeId: Int,
    val totalInteractions: Int,
    val hit1Count: Int,
    val hit3Count: Int,
    val coldMissCount: Int,
    val wrongContextCount: Int,
    val contextSwitchCount: Int
) {
    val hit1Rate: Double get() = if (totalInteractions > 0) hit1Count.toDouble() / totalInteractions else 0.0
    val hit3Rate: Double get() = if (totalInteractions > 0) hit3Count.toDouble() / totalInteractions else 0.0
    val coldMissRate: Double get() = if (totalInteractions > 0) coldMissCount.toDouble() / totalInteractions else 0.0
}

/**
 * Aggregate results across all episodes for a single
 * routing configuration.
 */
data class PredictionResults(
    val configName: String,
    val episodes: List<EpisodeResult>,
    val totalInteractions: Int,
    val hit1: Double,
    val hit3: Double,
    val coldMissRate: Double,
    val wrongContextRate: Double,
    val meanContextSwitches: Double
)

/**
 * Generates controlled sensor traces independently
 * of the scoring functions.
 *
 * Noise model:
 * - Time: uniform perturbation ±timeNoiseHours
 * - GPS: independent Gaussian displacement in lat/lng
 * - GPS accuracy: log-normal distribution
 * - BLE: independent per-device detection probability
 *        with RSSI drawn from Gaussian around anchor
 * - Missing sensors: configurable dropout probability
 */
class TraceGenerator(
    private val seed: Long = 42L
) {
    /**
     * Ground-truth daily schedule.
     *
     * Each entry: (startHour, endHour, stateId)
     *
     * The schedule covers a representative day for
     * an AAC user with the five configured contexts.
     */
    private val dailySchedule = listOf(
        Triple(6.0, 10.0, "home_morning"),
        Triple(10.0, 13.0, "hospital_ward"),
        Triple(13.0, 15.5, "therapy_room"),
        Triple(15.5, 18.0, "caregiver_visit"),
        Triple(18.0, 22.0, "home_evening")
    )

    /**
     * Anchor coordinates for each context state.
     * Must match SeededStateRepository exactly.
     */
    private val stateAnchors = mapOf(
        "home_morning" to Pair(28.6139, 77.2090),
        "home_evening" to Pair(28.6139, 77.2090),
        "hospital_ward" to Pair(28.5672, 77.2100),
        "therapy_room" to Pair(28.5672, 77.2105),
        "caregiver_visit" to Pair(28.6139, 77.2090)
    )

    /**
     * BLE anchor devices per state.
     * Must match SeededStateRepository exactly.
     */
    private val stateBleAnchors = mapOf(
        "home_morning" to listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
        "home_evening" to listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:03"),
        "hospital_ward" to listOf("AA:BB:CC:DD:EE:04", "AA:BB:CC:DD:EE:05"),
        "therapy_room" to listOf("AA:BB:CC:DD:EE:06"),
        "caregiver_visit" to listOf("AA:BB:CC:DD:EE:07", "AA:BB:CC:DD:EE:01")
    )

    /**
     * Generates a single day's trace.
     *
     * @param interactionIntervalMinutes Time between simulated user interactions
     * @param timeNoiseHours Max time perturbation (uniform ±)
     * @param gpsNoiseKm GPS displacement noise std dev in km
     * @param gpsDropoutProb Probability GPS is unavailable
     * @param gpsAccuracyMean Mean reported GPS accuracy (meters)
     * @param gpsAccuracySd Std dev of reported GPS accuracy
     * @param bleDetectionProb Per-device BLE detection probability
     * @param bleRssiMean Mean RSSI for detected devices (dBm)
     * @param bleRssiSd RSSI noise std dev
     * @param bleDropoutProb Probability all BLE is unavailable
     * @param spuriousBleProb Probability of a spurious non-anchor BLE device appearing
     */
    fun generateDayTrace(
        episodeId: Int,
        interactionIntervalMinutes: Double = 15.0,
        timeNoiseHours: Double = 0.5,
        gpsNoiseKm: Double = 0.05,
        gpsDropoutProb: Double = 0.1,
        gpsAccuracyMean: Double = 15.0,
        gpsAccuracySd: Double = 10.0,
        bleDetectionProb: Double = 0.8,
        bleRssiMean: Double = -65.0,
        bleRssiSd: Double = 10.0,
        bleDropoutProb: Double = 0.15,
        spuriousBleProb: Double = 0.1
    ): List<TracePoint> {

        val rng = java.util.Random(seed + episodeId)
        val points = mutableListOf<TracePoint>()

        // Generate interaction points throughout the day
        var currentHour = 6.0
        while (currentHour < 22.0) {
            // Find ground-truth state for this time
            val groundTruth = dailySchedule.find {
                currentHour >= it.first && currentHour < it.second
            } ?: continue

            val stateId = groundTruth.third

            // Time observation: independent uniform perturbation
            val observedHour = currentHour +
                (rng.nextDouble() * 2.0 - 1.0) * timeNoiseHours

            // GPS observation
            val gpsLocation = if (rng.nextDouble() > gpsDropoutProb) {
                val anchor = stateAnchors[stateId]!!
                // Independent Gaussian displacement (~0.05km ≈ 0.00045 degrees)
                val latNoise = rng.nextGaussian() * gpsNoiseKm / 111.0
                val lngNoise = rng.nextGaussian() * gpsNoiseKm / 111.0
                val accuracy = (gpsAccuracyMean +
                    rng.nextGaussian() * gpsAccuracySd)
                    .coerceIn(3.0, 200.0)

                GpsLocation(
                    lat = (anchor.first + latNoise).coerceIn(-90.0, 90.0),
                    lng = (anchor.second + lngNoise).coerceIn(-180.0, 180.0),
                    accuracyMeters = accuracy.toFloat()
                )
            } else {
                null
            }

            // BLE observation
            val bleDevices = if (rng.nextDouble() > bleDropoutProb) {
                val anchors = stateBleAnchors[stateId] ?: emptyList()
                val detected = mutableMapOf<String, Rssi>()

                for (mac in anchors) {
                    if (rng.nextDouble() < bleDetectionProb) {
                        val rssi = (bleRssiMean +
                            rng.nextGaussian() * bleRssiSd)
                            .toInt()
                            .coerceIn(-127, -1)
                        detected[mac] = Rssi(rssi)
                    }
                }

                // Occasionally add a spurious non-anchor device
                if (rng.nextDouble() < spuriousBleProb) {
                    val spuriousMac = "FF:FF:FF:FF:FF:%02X".format(rng.nextInt(256))
                    val rssi = (-80 + rng.nextGaussian() * 10.0)
                        .toInt().coerceIn(-127, -1)
                    detected[spuriousMac] = Rssi(rssi)
                }

                detected
            } else {
                emptyMap()
            }

            val snapshot = SensorSnapshot(
                currentHourDecimal = observedHour,
                location = gpsLocation,
                detectedBleDevices = bleDevices
            )

            points.add(TracePoint(
                timestampHour = currentHour,
                groundTruthStateId = stateId,
                snapshot = snapshot
            ))

            currentHour += interactionIntervalMinutes / 60.0
        }

        return points
    }
}

/**
 * Evaluates the prediction accuracy of a StateRouter
 * configuration against generated traces.
 */
class PredictionEvaluator(
    private val router: StateRouter,
    private val states: List<ContextState>,
    private val maxResidentStates: Int = HardwareConfig.MAX_ACTIVE_KV_STATES
) {

    /**
     * Evaluates a single trace episode.
     *
     * Simulates the resident-set behavior:
     * - Router selects top-K states
     * - Resident set is updated (simulating LRU)
     * - At each interaction, check if ground-truth is resident
     */
    fun evaluateEpisode(
        trace: List<TracePoint>,
        episodeId: Int
    ): EpisodeResult {
        // Simulated resident set
        val residentStates = mutableListOf<String>()
        var hit1 = 0
        var hit3 = 0
        var coldMiss = 0
        var wrongContext = 0
        var contextSwitches = 0
        var lastTopState: String? = null

        for (point in trace) {
            // Score all states using the production router
            val scored = router.getScoredStates(
                snapshot = point.snapshot,
                states = states
            )

            val topK = scored.take(maxResidentStates).map { it.first }
            val top1 = scored.firstOrNull()?.first

            // Update resident set (simplified LRU simulation)
            for (stateId in topK) {
                if (stateId !in residentStates) {
                    if (residentStates.size >= maxResidentStates) {
                        // Evict the state not in topK that was least recently added
                        val toEvict = residentStates.firstOrNull { it !in topK }
                        if (toEvict != null) {
                            residentStates.remove(toEvict)
                        }
                    }
                    if (residentStates.size < maxResidentStates) {
                        residentStates.add(stateId)
                    }
                }
            }

            // Track context switches
            if (top1 != null && top1 != lastTopState) {
                if (lastTopState != null) contextSwitches++
                lastTopState = top1
            }

            // Evaluate prediction quality
            val gt = point.groundTruthStateId

            if (top1 == gt) hit1++
            // Hit@3 = ground truth is actually resident in the cache (not top-3 ranking)
            if (gt in residentStates) hit3++
            if (gt !in residentStates) coldMiss++
            if (top1 != null && top1 != gt && gt in residentStates) wrongContext++
        }

        return EpisodeResult(
            episodeId = episodeId,
            totalInteractions = trace.size,
            hit1Count = hit1,
            hit3Count = hit3,
            coldMissCount = coldMiss,
            wrongContextCount = wrongContext,
            contextSwitchCount = contextSwitches
        )
    }

    /**
     * Evaluates multiple episodes and aggregates results.
     */
    fun evaluate(
        generator: TraceGenerator,
        numEpisodes: Int,
        configName: String,
        interactionIntervalMinutes: Double = 15.0,
        timeNoiseHours: Double = 0.5,
        gpsNoiseKm: Double = 0.05,
        gpsDropoutProb: Double = 0.1,
        gpsAccuracyMean: Double = 15.0,
        gpsAccuracySd: Double = 10.0,
        bleDetectionProb: Double = 0.8,
        bleRssiMean: Double = -65.0,
        bleRssiSd: Double = 10.0,
        bleDropoutProb: Double = 0.15,
        spuriousBleProb: Double = 0.1
    ): PredictionResults {
        val episodes = (0 until numEpisodes).map { i ->
            val trace = generator.generateDayTrace(
                episodeId = i,
                interactionIntervalMinutes = interactionIntervalMinutes,
                timeNoiseHours = timeNoiseHours,
                gpsNoiseKm = gpsNoiseKm,
                gpsDropoutProb = gpsDropoutProb,
                gpsAccuracyMean = gpsAccuracyMean,
                gpsAccuracySd = gpsAccuracySd,
                bleDetectionProb = bleDetectionProb,
                bleRssiMean = bleRssiMean,
                bleRssiSd = bleRssiSd,
                bleDropoutProb = bleDropoutProb,
                spuriousBleProb = spuriousBleProb
            )
            evaluateEpisode(trace, i)
        }

        val totalInteractions = episodes.sumOf { it.totalInteractions }
        val totalHit1 = episodes.sumOf { it.hit1Count }
        val totalHit3 = episodes.sumOf { it.hit3Count }
        val totalCold = episodes.sumOf { it.coldMissCount }
        val totalWrong = episodes.sumOf { it.wrongContextCount }

        return PredictionResults(
            configName = configName,
            episodes = episodes,
            totalInteractions = totalInteractions,
            hit1 = if (totalInteractions > 0) totalHit1.toDouble() / totalInteractions else 0.0,
            hit3 = if (totalInteractions > 0) totalHit3.toDouble() / totalInteractions else 0.0,
            coldMissRate = if (totalInteractions > 0) totalCold.toDouble() / totalInteractions else 0.0,
            wrongContextRate = if (totalInteractions > 0) totalWrong.toDouble() / totalInteractions else 0.0,
            meanContextSwitches = episodes.map { it.contextSwitchCount.toDouble() }.average()
        )
    }
}
