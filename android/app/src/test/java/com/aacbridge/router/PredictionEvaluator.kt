package com.aacbridge.router

/**
 * Controlled sensor-trace generator and trace-driven
 * evaluator for the context router + KV residency policy.
 *
 * IMPORTANT:
 * These traces are controlled/synthetic. They are NOT
 * real-world sensor recordings from AAC users.
 *
 * What is simulated:
 * - One ActiveSweep per minute (production cadence 60 s),
 *   06:00-22:00. Each sweep scores all contexts with the
 *   production StateRouter and applies the production
 *   ResidencyPolicy (same code the device runs).
 * - One user interaction every 15 minutes. The context used
 *   is ResidencyPolicy.selectForInference (same rule as
 *   MainActivity.handleIntent).
 *
 * What is NOT simulated:
 * - Priming/restore latency: a state chosen at a sweep is
 *   assumed resident by the next minute (on-device priming
 *   takes seconds, restore milliseconds).
 * - Real schedules: the ground truth is a five-context daily
 *   routine whose boundaries are shifted per day (see
 *   TraceGenerator). The router's time anchors were authored
 *   from the same routine, so time-of-day evidence is
 *   favourable by construction.
 */

/** Ground truth for one simulated sweep. */
data class TracePoint(
    /** True wall-clock hour (device clock is exact). */
    val timestampHour: Double,
    val groundTruthStateId: String,
    val snapshot: SensorSnapshot,
    /** True if a user interaction happens at this sweep. */
    val isInteraction: Boolean
)

/** Noise / scenario parameters for one trace configuration. */
data class TraceConfig(
    /** Per-day uniform shift (+/- hours) of each context boundary. */
    val scheduleShiftHours: Double = 0.5,
    val gpsNoiseKm: Double = 0.05,
    val gpsDropoutProb: Double = 0.1,
    val gpsAccuracyMean: Double = 15.0,
    val gpsAccuracySd: Double = 10.0,
    val bleDetectionProb: Double = 0.8,
    val bleRssiMean: Double = -65.0,
    val bleRssiSd: Double = 10.0,
    val bleDropoutProb: Double = 0.15,
    val spuriousBleProb: Double = 0.1,
    val sweepMinutes: Double = 1.0,
    val interactionEveryMinutes: Double = 15.0
)

/**
 * Generates controlled sensor traces independently of the
 * scoring functions (noise is not drawn from the scorers).
 */
class TraceGenerator(
    private val seed: Long = 42L
) {

    companion object {

        /** Nominal routine: (startHour, stateId), ends at 22:00. */
        val NOMINAL_SCHEDULE = listOf(
            6.0 to "home_morning",
            10.0 to "hospital_ward",
            13.0 to "therapy_room",
            15.5 to "caregiver_visit",
            18.0 to "home_evening"
        )

        const val DAY_START = 6.0
        const val DAY_END = 22.0

        /** Must match SeededStateRepository. */
        val STATE_ANCHORS = mapOf(
            "home_morning" to Pair(28.6139, 77.2090),
            "home_evening" to Pair(28.6139, 77.2090),
            "hospital_ward" to Pair(28.5672, 77.2100),
            "therapy_room" to Pair(28.5672, 77.2105),
            "caregiver_visit" to Pair(28.6139, 77.2090)
        )

        /** Must match SeededStateRepository. */
        val STATE_BLE_ANCHORS = mapOf(
            "home_morning" to listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
            "home_evening" to listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:03"),
            "hospital_ward" to listOf("AA:BB:CC:DD:EE:04", "AA:BB:CC:DD:EE:05"),
            "therapy_room" to listOf("AA:BB:CC:DD:EE:06"),
            "caregiver_visit" to listOf("AA:BB:CC:DD:EE:07", "AA:BB:CC:DD:EE:01")
        )

        /** Minimum duration of any context after shifting. */
        private const val MIN_SEGMENT_HOURS = 0.25
    }

    /**
     * Per-day schedule: interior boundaries shifted by
     * U(-shift, +shift), kept ordered with a minimum
     * segment length.
     */
    fun daySchedule(episodeId: Int, shiftHours: Double): List<Pair<Double, String>> {
        val rng = java.util.Random(seed * 7919 + episodeId)
        val shifted = NOMINAL_SCHEDULE.mapIndexed { i, (start, id) ->
            if (i == 0) start to id
            else (start + (rng.nextDouble() * 2.0 - 1.0) * shiftHours) to id
        }.toMutableList()
        for (i in 1 until shifted.size) {
            val lo = shifted[i - 1].first + MIN_SEGMENT_HOURS
            val hi = DAY_END - MIN_SEGMENT_HOURS * (shifted.size - i)
            shifted[i] = shifted[i].first.coerceIn(lo, hi) to shifted[i].second
        }
        return shifted
    }

    fun generateDayTrace(episodeId: Int, config: TraceConfig = TraceConfig()): List<TracePoint> {

        val rng = java.util.Random(seed + episodeId)
        val schedule = daySchedule(episodeId, config.scheduleShiftHours)
        val points = mutableListOf<TracePoint>()

        val sweepsPerInteraction =
            Math.round(config.interactionEveryMinutes / config.sweepMinutes).toInt().coerceAtLeast(1)

        var step = 0
        var hour = DAY_START
        while (hour < DAY_END - 1e-9) {

            val stateId = schedule.last { hour >= it.first }.second

            val gps = if (rng.nextDouble() > config.gpsDropoutProb) {
                val anchor = STATE_ANCHORS.getValue(stateId)
                val latNoise = rng.nextGaussian() * config.gpsNoiseKm / 111.0
                val lngNoise = rng.nextGaussian() * config.gpsNoiseKm / 111.0
                val accuracy = (config.gpsAccuracyMean + rng.nextGaussian() * config.gpsAccuracySd)
                    .coerceIn(3.0, 200.0)
                GpsLocation(
                    lat = (anchor.first + latNoise).coerceIn(-90.0, 90.0),
                    lng = (anchor.second + lngNoise).coerceIn(-180.0, 180.0),
                    accuracyMeters = accuracy.toFloat()
                )
            } else null

            val ble = if (rng.nextDouble() > config.bleDropoutProb) {
                val detected = mutableMapOf<String, Rssi>()
                for (mac in STATE_BLE_ANCHORS[stateId].orEmpty()) {
                    if (rng.nextDouble() < config.bleDetectionProb) {
                        val rssi = (config.bleRssiMean + rng.nextGaussian() * config.bleRssiSd)
                            .toInt().coerceIn(-127, -1)
                        detected[mac] = Rssi(rssi)
                    }
                }
                if (rng.nextDouble() < config.spuriousBleProb) {
                    val mac = "FF:FF:FF:FF:FF:%02X".format(rng.nextInt(256))
                    detected[mac] = Rssi((-80 + rng.nextGaussian() * 10.0).toInt().coerceIn(-127, -1))
                }
                detected
            } else emptyMap()

            points += TracePoint(
                timestampHour = hour,
                groundTruthStateId = stateId,
                snapshot = SensorSnapshot(
                    currentHourDecimal = hour,
                    location = gps,
                    detectedBleDevices = ble
                ),
                isInteraction = step % sweepsPerInteraction == 0
            )

            step++
            hour = DAY_START + step * config.sweepMinutes / 60.0
        }

        return points
    }
}

/**
 * Per-interaction outcome. Exactly one applies, so
 * correct + wrong + coldMiss = interactions.
 */
enum class Outcome {
    /** Context used for inference == ground truth. */
    CORRECT,
    /** A resident context was used, but not the ground truth. */
    WRONG_CONTEXT,
    /** No ranked context resident: Tier-1 generic response. */
    COLD_MISS
}

data class EvalResult(
    val configName: String,
    val days: Int,
    val interactions: Int,
    /** Router top-1 == ground truth (prediction accuracy). */
    val top1Correct: Int,
    /** Ground truth resident at interaction time. */
    val gtResident: Int,
    /** Router produced no candidate above the dead-state threshold. */
    val noPrediction: Int,
    val correct: Int,
    val wrongContext: Int,
    val coldMiss: Int,
    /** States loaded (primed or restored) into residency. */
    val loads: Int,
    /** States evicted from residency (cache replacements). */
    val evictions: Int,
    /** False for baselines that make no prediction (oracle, reactive). */
    val predictive: Boolean = true
) {
    private fun rate(n: Int) = if (interactions > 0) n.toDouble() / interactions else 0.0
    val hit1 get() = rate(top1Correct)
    val gtResidentRate get() = rate(gtResident)
    val noPredictionRate get() = rate(noPrediction)
    val correctRate get() = rate(correct)
    val wrongContextRate get() = rate(wrongContext)
    val coldMissRate get() = rate(coldMiss)
    val loadsPerDay get() = loads.toDouble() / days
    val evictionsPerDay get() = evictions.toDouble() / days

    init {
        check(correct + wrongContext + coldMiss == interactions) {
            "$configName: outcomes do not partition interactions"
        }
    }

    companion object {
        const val CSV_HEADER =
            "config,days,interactions,hit1,gt_resident,no_prediction," +
                "served_correct,wrong_context,cold_miss,loads_per_day,evictions_per_day"
    }

    fun toCsv(): String = listOf(
        configName, days, interactions,
        if (predictive) "%.6f".format(hit1) else "NA",
        "%.6f".format(gtResidentRate),
        if (predictive) "%.6f".format(noPredictionRate) else "NA",
        "%.6f".format(correctRate), "%.6f".format(wrongContextRate), "%.6f".format(coldMissRate),
        "%.3f".format(loadsPerDay), "%.3f".format(evictionsPerDay)
    ).joinToString(",")
}

/** Accumulates per-interaction / per-sweep events. */
private class Tally(val name: String, val predictive: Boolean = true) {
    var days = 0
    var interactions = 0
    var top1 = 0
    var gtResident = 0
    var noPrediction = 0
    var correct = 0
    var wrong = 0
    var cold = 0
    var loads = 0
    var evictions = 0

    fun interaction(gt: String, top1Id: String?, resident: Set<String>, served: String?) {
        interactions++
        if (top1Id == gt) top1++
        if (top1Id == null) noPrediction++
        if (gt in resident) gtResident++
        when {
            served == null -> cold++
            served == gt -> correct++
            else -> wrong++
        }
    }

    fun result() = EvalResult(name, days, interactions, top1, gtResident, noPrediction,
        correct, wrong, cold, loads, evictions, predictive)
}

/**
 * Runs the production routing + residency rules over traces.
 */
class PredictionEvaluator(
    private val states: List<ContextState>,
    private val capacity: Int = HardwareConfig.MAX_ACTIVE_KV_STATES
) {

    /**
     * Sensor-driven CAP-KVC: production router + ResidencyPolicy.
     *
     * @param transform optional per-point snapshot rewrite
     *        (spoofing scenarios); applied before scoring.
     */
    fun evaluateRouter(
        configName: String,
        router: StateRouter,
        traces: List<List<TracePoint>>,
        margin: Double = HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN,
        transform: (TracePoint) -> SensorSnapshot = { it.snapshot }
    ): EvalResult {
        val t = Tally(configName)
        for (trace in traces) {
            t.days++
            val resident = mutableSetOf<String>()
            for (point in trace) {
                val ranked = router.getScoredStates(transform(point), states)
                applyPlan(t, resident, ResidencyPolicy.plan(ranked, resident, capacity, margin))
                if (point.isInteraction) {
                    t.interaction(
                        gt = point.groundTruthStateId,
                        top1Id = ranked.firstOrNull()?.first,
                        resident = resident,
                        served = ResidencyPolicy.selectForInference(ranked, resident)
                    )
                }
            }
        }
        return t.result()
    }

    /**
     * Random ranking each sweep (uniform scores), same
     * residency policy and selection rule.
     */
    fun evaluateRandom(
        configName: String,
        traces: List<List<TracePoint>>,
        seed: Long,
        margin: Double = HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN
    ): EvalResult {
        val rng = java.util.Random(seed)
        val ids = states.map { it.stateId }
        val t = Tally(configName)
        for (trace in traces) {
            t.days++
            val resident = mutableSetOf<String>()
            for (point in trace) {
                val ranked = ids.map { it to rng.nextDouble() }.sortedByDescending { it.second }
                applyPlan(t, resident, ResidencyPolicy.plan(ranked, resident, capacity, margin))
                if (point.isInteraction) {
                    t.interaction(point.groundTruthStateId, ranked.first().first, resident,
                        ResidencyPolicy.selectForInference(ranked, resident))
                }
            }
        }
        return t.result()
    }

    /**
     * Oracle / always-resident: the ground-truth context is
     * always resident and used. Isolates the KV-restore
     * benefit from prediction (every interaction is a hit).
     */
    fun evaluateOracle(configName: String, traces: List<List<TracePoint>>): EvalResult {
        val t = Tally(configName, predictive = false)
        for (trace in traces) {
            t.days++
            var current: String? = null
            for (point in trace) {
                val gt = point.groundTruthStateId
                if (gt != current) { t.loads++; if (current != null) t.evictions++; current = gt }
                if (point.isInteraction) t.interaction(gt, gt, setOf(gt), gt)
            }
        }
        return t.result()
    }

    /**
     * Reactive (request-driven) LRU cache, no sensing: the
     * needed context is identified only when the request
     * arrives (best case for request-driven reuse, as in
     * prefix/RAG caches). A request whose context is not
     * cached is a cold miss and that context is then
     * prefilled and inserted (LRU eviction). Cache persists
     * across days, as primed files would on the device.
     */
    fun evaluateReactiveLru(configName: String, traces: List<List<TracePoint>>): EvalResult {
        val t = Tally(configName, predictive = false)
        val lru = LinkedHashSet<String>()
        for (trace in traces) {
            t.days++
            for (point in trace) {
                if (!point.isInteraction) continue
                val gt = point.groundTruthStateId
                val resident = lru.toSet()
                t.interaction(gt, top1Id = gt, resident = resident, served = gt.takeIf { it in resident })
                if (gt in lru) {
                    lru.remove(gt); lru.add(gt)
                } else {
                    if (lru.size >= capacity) { lru.remove(lru.first()); t.evictions++ }
                    lru.add(gt); t.loads++
                }
            }
        }
        return t.result()
    }

    private fun applyPlan(t: Tally, resident: MutableSet<String>, plan: ResidencyPolicy.Plan) {
        for (id in plan.toEvict) { resident.remove(id); t.evictions++ }
        for (id in plan.toLoad) { resident.add(id); t.loads++ }
        check(resident.size <= capacity)
    }
}
