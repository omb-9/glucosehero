package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileValidation
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device 30-60 minute glucose forecast.
 *
 * Combines a 2-state constant-velocity Kalman filter, an AR(2) fit on recent
 * 5-minute first differences, remaining insulin-on-board (Walsh IOB from
 * [IobCalculator]), and remaining carb absorption. No network is required.
 *
 * ## Model assumptions (units: mg/dL unless noted)
 *
 * **AR order.** AR(2) on first differences (velocity in mg/dL per minute),
 * estimated by ordinary least squares on the last [MAX_AR_SAMPLES] CGM
 * points after they are resampled onto a 5-minute grid. A uniform grid
 * needs at least 5 glucose points (4 velocity observations, two lags);
 * otherwise the AR term is skipped.
 *
 * **Kalman.** State is `[glucose (mg/dL), velocity (mg/dL/min)]`. Measurement
 * noise `R = 9` (CGM RMSE of ~3 mg/dL). Velocity process noise is
 * `0.04 (mg/dL/min)^2` per minute so the filter tracks a trend change in
 * about 15 minutes without chasing sensor noise.
 *
 * **IOB decay.** Walsh / Scheiner activity triangle identical to
 * [IobCalculator]: 100% of a rapid-acting bolus is active at delivery, peak
 * activity around 50-75 min, 0% after [diaHours]. IOB(t) is monotonically
 * decreasing. Duration of insulin action is **global** (not time-varying).
 * The glucose drop over a horizon is the sum, over each 5-minute step, of
 * `(IOB_stepStart - IOB_stepEnd) * ISF_at_stepEnd` mg/dL. A single-segment
 * profile uses the closed form `(IOB_now - IOB_horizon) * ISF`, which is
 * bit-identical to the previous scalar engine. The curve is approximate,
 * not a dosing recommendation.
 *
 * **Carb absorption.** Linear mixed-meal curve from
 * [CarbAbsorptionCalculator] (default 3.0 h). The glucose rise over a
 * horizon is summed per step as `carbs_absorbed * (ISF / CIR)` mg/dL using
 * the segment **in effect at that projected step**, because CIR grams are
 * covered by 1 U and 1 U drops glucose by ISF mg/dL. A single-segment profile
 * uses one ISF/CIR for the whole horizon (bit-identical to the previous
 * engine).
 *
 * **Time-of-day ISF/CIR.** [GlucoseForecastInput.dosingProfile] is resolved at
 * each horizon instant via [com.omb9.glucosehero.domain.model.DosingProfile.at]
 * and the input [GlucoseForecastInput.zoneId] (evaluation-time system default
 * when omitted). An invalid profile refuses the forecast rather than
 * substituting defaults.
 *
 * FEATURE: dosing-profiles
 *
 * **Velocity blend.** `0.55 * v_kalman + 0.45 * v_ar` when AR is available,
 * otherwise Kalman alone. IOB and carb effects are added on top of this
 * kinematic projection so they are not double-counted in the AR residual.
 *
 * **Clamp.** Projections are clamped to 40-400 mg/dL. Missing CGM data
 * returns [GlucoseForecastSnapshot.insufficientData]. A newest sample older
 * than [MAX_ANCHOR_AGE_MILLIS] returns [GlucoseForecastSnapshot.staleAnchor]
 * with no projection points; that is a separate axis from insufficient data.
 * Missing IOB/carb logs are treated as zero. This is an estimate, not a
 * therapy recommendation.
 */
object GlucoseForecastEngine {

    const val HORIZON_MINUTES: Int = 60
    const val STEP_MINUTES: Int = 5
    const val HIGHLIGHT_MINUTES_30: Int = 30
    const val HIGHLIGHT_MINUTES_60: Int = 60
    const val LOOKBACK_MINUTES: Int = 90
    const val MIN_SAMPLES: Int = 3
    /**
     * Refuse to emit projection points when the newest CGM sample is older
     * than this. Twenty minutes is roughly four missed 5-minute CGM
     * intervals: long enough that a single missed reading still forecasts,
     * but a silent sensor does not keep looking live. Intentionally looser
     * than [com.omb9.glucosehero.data.cgm.GlucoseFreshness.FRESH_MAX_AGE_MILLIS]
     * (8 minutes), which is a UI caption threshold under a Wear lockstep test.
     */
    const val MAX_ANCHOR_AGE_MILLIS: Long = 20L * 60L * 1000L
    const val GRID_MINUTES: Double = 5.0
    const val MAX_AR_SAMPLES: Int = 12
    /** Gridded glucose points required for AR(2) OLS (4 velocities, two lags). */
    const val MIN_AR_GRIDDED_POINTS: Int = 5
    const val MIN_GLUCOSE_MGDL: Double = 40.0
    const val MAX_GLUCOSE_MGDL: Double = 400.0

    private const val KALMAN_BLEND: Double = 0.55
    private const val AR_BLEND: Double = 0.45
    private val SEGMENT_HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun forecast(
        input: GlucoseForecastInput,
        now: Instant = Instant.now(),
    ): GlucoseForecastSnapshot {
        val zoneId = input.zoneId ?: ZoneId.systemDefault()
        val profile = input.dosingProfile
        if (profile != null) {
            when (profile.validate()) {
                is DosingProfileValidation.Invalid -> {
                    val iob = IobCalculator.activeInsulinOnBoard(
                        input.boluses, input.diaHours, now,
                    )
                    val cob = CarbAbsorptionCalculator.carbsOnBoard(
                        input.meals, input.carbActionHours, now,
                    )
                    return GlucoseForecastSnapshot(
                        generatedAtMillis = now.toEpochMilli(),
                        currentMgdl = 0.0,
                        currentTimestampMillis = now.toEpochMilli(),
                        velocityMgdlPerMin = 0.0,
                        iobUnits = iob,
                        cobGrams = cob,
                        points = emptyList(),
                        sampleCount = 0,
                        insufficientData = true,
                        anchorAgeMillis = 0L,
                        staleAnchor = false,
                        dosingProfileInvalid = true,
                    )
                }
                is DosingProfileValidation.Valid -> Unit
            }
        }
        val perStepProfile = profile?.takeIf { it.segments.size > 1 }

        val samples = input.samples
            .filter { it.glucoseMgdl.isFinite() && it.timestampMillis <= now.toEpochMilli() }
            .sortedBy { it.timestampMillis }
        val lookbackStart = now.toEpochMilli() - LOOKBACK_MINUTES * 60_000L
        val recent = samples.filter { it.timestampMillis >= lookbackStart }
        val iob = IobCalculator.activeInsulinOnBoard(input.boluses, input.diaHours, now)
        val cob = CarbAbsorptionCalculator.carbsOnBoard(input.meals, input.carbActionHours, now)

        if (recent.size < MIN_SAMPLES) {
            val latest = recent.lastOrNull() ?: samples.lastOrNull()
            val anchorAgeMillis = if (latest != null) {
                (now.toEpochMilli() - latest.timestampMillis).coerceAtLeast(0L)
            } else {
                0L
            }
            return GlucoseForecastSnapshot(
                generatedAtMillis = now.toEpochMilli(),
                currentMgdl = latest?.glucoseMgdl ?: 0.0,
                currentTimestampMillis = latest?.timestampMillis ?: now.toEpochMilli(),
                velocityMgdlPerMin = 0.0,
                iobUnits = iob,
                cobGrams = cob,
                points = emptyList(),
                sampleCount = recent.size,
                insufficientData = true,
                anchorAgeMillis = anchorAgeMillis,
                staleAnchor = false,
            )
        }

        val latest = recent.last()
        val anchorAgeMillis = (now.toEpochMilli() - latest.timestampMillis).coerceAtLeast(0L)
        val anchorAgeMinutes = anchorAgeMillis / 60_000.0

        if (anchorAgeMillis > MAX_ANCHOR_AGE_MILLIS) {
            return GlucoseForecastSnapshot(
                generatedAtMillis = now.toEpochMilli(),
                currentMgdl = latest.glucoseMgdl,
                currentTimestampMillis = latest.timestampMillis,
                velocityMgdlPerMin = 0.0,
                iobUnits = iob,
                cobGrams = cob,
                points = emptyList(),
                sampleCount = recent.size,
                insufficientData = false,
                anchorAgeMillis = anchorAgeMillis,
                staleAnchor = true,
            )
        }

        val series = recent
        val gridded = resampleOntoGrid(series)

        val kalman = GlucoseKalmanFilter()
        for (i in gridded.indices) {
            val dtMin = if (i == 0) {
                GRID_MINUTES
            } else {
                (gridded[i].timestampMillis - gridded[i - 1].timestampMillis) / 60_000.0
            }
            kalman.update(gridded[i].glucoseMgdl, dtMin)
        }

        val arVelocity = fitAr2Velocity(gridded)
        val blendedVelocity = if (arVelocity == null) {
            kalman.velocity
        } else {
            KALMAN_BLEND * kalman.velocity + AR_BLEND * arVelocity
        }

        val mgdlPerGram = mgdlPerGramCarb(input.isfMgdl, input.cirRatio)
        val points = ArrayList<GlucoseForecastPoint>(HORIZON_MINUTES / STEP_MINUTES)
        var trendEffectMgdl30 = 0.0
        var insulinEffectMgdl30 = 0.0
        var carbEffectMgdl30 = 0.0
        var unclampedMgdl30 = 0.0
        var clampMin30 = false
        var clampMax30 = false
        var trendEffectMgdl60 = 0.0
        var insulinEffectMgdl60 = 0.0
        var carbEffectMgdl60 = 0.0
        var unclampedMgdl60 = 0.0
        var clampMin60 = false
        var clampMax60 = false
        var horizon = STEP_MINUTES
        while (horizon <= HORIZON_MINUTES) {
            val at = now.plusMillis(horizon * 60_000L)
            val trendMinutes = minutesFromAnchor(horizon.toDouble(), anchorAgeMinutes)
            val kinematic = latest.glucoseMgdl + blendedVelocity * trendMinutes
            val (insulinDrop, carbRise) = if (perStepProfile != null) {
                perStepGlucoseEffects(input, perStepProfile, zoneId, now, at)
            } else {
                val drop = IobCalculator.insulinAbsorbedBetween(
                    input.boluses, input.diaHours, now, at,
                ) * input.isfMgdl
                val rise = CarbAbsorptionCalculator.carbsAbsorbedBetween(
                    input.meals, input.carbActionHours, now, at,
                ) * mgdlPerGram
                drop to rise
            }
            val unclamped = kinematic - insulinDrop + carbRise
            val projected = unclamped.coerceIn(MIN_GLUCOSE_MGDL, MAX_GLUCOSE_MGDL)
            if (horizon == HIGHLIGHT_MINUTES_30) {
                trendEffectMgdl30 = blendedVelocity * trendMinutes
                insulinEffectMgdl30 = insulinDrop
                carbEffectMgdl30 = carbRise
                unclampedMgdl30 = unclamped
                clampMin30 = unclamped < MIN_GLUCOSE_MGDL
                clampMax30 = unclamped > MAX_GLUCOSE_MGDL
            }
            if (horizon == HIGHLIGHT_MINUTES_60) {
                trendEffectMgdl60 = blendedVelocity * trendMinutes
                insulinEffectMgdl60 = insulinDrop
                carbEffectMgdl60 = carbRise
                unclampedMgdl60 = unclamped
                clampMin60 = unclamped < MIN_GLUCOSE_MGDL
                clampMax60 = unclamped > MAX_GLUCOSE_MGDL
            }
            points += GlucoseForecastPoint(
                timestampMillis = at.toEpochMilli(),
                minutesAhead = horizon,
                glucoseMgdl = projected,
            )
            horizon += STEP_MINUTES
        }

        val horizonEnd = now.plusMillis(HORIZON_MINUTES * 60_000L)
        val dosingSegmentsUsed = if (profile != null) {
            profile.segmentsOverlapping(now, horizonEnd, zoneId).map { seg ->
                ForecastDosingSegmentUsed(
                    startHhmm = seg.start.format(SEGMENT_HHMM),
                    isfMgdl = seg.isfMgdl.toDouble(),
                    cirRatio = seg.cirRatio.toDouble(),
                )
            }
        } else {
            emptyList()
        }

        return GlucoseForecastSnapshot(
            generatedAtMillis = now.toEpochMilli(),
            currentMgdl = latest.glucoseMgdl,
            currentTimestampMillis = latest.timestampMillis,
            velocityMgdlPerMin = blendedVelocity,
            iobUnits = iob,
            cobGrams = cob,
            points = points,
            sampleCount = series.size,
            insufficientData = false,
            anchorAgeMillis = anchorAgeMillis,
            staleAnchor = false,
            trendEffectMgdl30 = trendEffectMgdl30,
            insulinEffectMgdl30 = insulinEffectMgdl30,
            carbEffectMgdl30 = carbEffectMgdl30,
            unclampedMgdl30 = unclampedMgdl30,
            clampMin30 = clampMin30,
            clampMax30 = clampMax30,
            trendEffectMgdl60 = trendEffectMgdl60,
            insulinEffectMgdl60 = insulinEffectMgdl60,
            carbEffectMgdl60 = carbEffectMgdl60,
            unclampedMgdl60 = unclampedMgdl60,
            clampMin60 = clampMin60,
            clampMax60 = clampMax60,
            horizonCrossedSegmentBoundary = dosingSegmentsUsed.size > 1,
            dosingSegmentsUsed = dosingSegmentsUsed,
        )
    }

    /**
     * Minutes from the CGM anchor timestamp to a forecast horizon, including
     * how old that anchor already is. Insulin and carb terms still run from
     * `now` forward; only the kinematic trend uses this span.
     */
    internal fun minutesFromAnchor(horizonMinutes: Double, anchorAgeMinutes: Double): Double =
        horizonMinutes + anchorAgeMinutes

    /**
     * 1 g carbohydrate raises glucose by ISF/CIR mg/dL. Guard CIR <= 0.
     */
    fun mgdlPerGramCarb(isfMgdl: Double, cirRatio: Double): Double {
        if (cirRatio <= 0.0 || isfMgdl <= 0.0) return 0.0
        return isfMgdl / cirRatio
    }

    /**
     * Linearly interpolate [series] onto a [gridMinutes] cadence aligned to the
     * last sample so the CGM anchor is exact and Kalman/AR see a uniform dt.
     */
    internal fun resampleOntoGrid(
        series: List<GlucoseForecastInput.GlucoseSample>,
        gridMinutes: Double = GRID_MINUTES,
    ): List<GlucoseForecastInput.GlucoseSample> {
        if (series.size < 2) return series
        val gridMillis = (gridMinutes * 60_000.0).toLong()
        if (gridMillis <= 0L) return series
        val start = series.first().timestampMillis
        val end = series.last().timestampMillis
        if (end <= start) return listOf(series.last())
        val out = ArrayList<GlucoseForecastInput.GlucoseSample>(
            ((end - start) / gridMillis).toInt() + 1,
        )
        var t = end
        while (t >= start) {
            out += interpolateAt(series, t)
            val next = t - gridMillis
            if (next >= t) break
            t = next
        }
        out.reverse()
        return out
    }

    private fun interpolateAt(
        series: List<GlucoseForecastInput.GlucoseSample>,
        t: Long,
    ): GlucoseForecastInput.GlucoseSample {
        val first = series.first()
        if (t <= first.timestampMillis) {
            return GlucoseForecastInput.GlucoseSample(t, first.glucoseMgdl)
        }
        val last = series.last()
        if (t >= last.timestampMillis) {
            return GlucoseForecastInput.GlucoseSample(t, last.glucoseMgdl)
        }
        for (i in 0 until series.lastIndex) {
            val a = series[i]
            val b = series[i + 1]
            if (t == a.timestampMillis) {
                return GlucoseForecastInput.GlucoseSample(t, a.glucoseMgdl)
            }
            if (t > a.timestampMillis && t <= b.timestampMillis) {
                val span = (b.timestampMillis - a.timestampMillis).toDouble()
                if (span <= 0.0) {
                    return GlucoseForecastInput.GlucoseSample(t, b.glucoseMgdl)
                }
                val w = (t - a.timestampMillis) / span
                return GlucoseForecastInput.GlucoseSample(
                    t,
                    a.glucoseMgdl + w * (b.glucoseMgdl - a.glucoseMgdl),
                )
            }
        }
        return GlucoseForecastInput.GlucoseSample(t, last.glucoseMgdl)
    }

    /**
     * AR(2) on 5-minute first differences. [series] is resampled onto
     * [GRID_MINUTES] first so lags are uniform. Returns the one-step-ahead
     * velocity in mg/dL per minute, or null when fewer than
     * [MIN_AR_GRIDDED_POINTS] gridded samples (4 velocities) or the OLS
     * system is singular.
     */
    internal fun fitAr2Velocity(series: List<GlucoseForecastInput.GlucoseSample>): Double? {
        val gridded = resampleOntoGrid(series)
        if (gridded.size < MIN_AR_GRIDDED_POINTS) return null
        val tail = gridded.takeLast(MAX_AR_SAMPLES)
        val velocities = ArrayList<Double>(tail.size - 1)
        for (i in 1 until tail.size) {
            val dtMin = (tail[i].timestampMillis - tail[i - 1].timestampMillis) / 60_000.0
            if (dtMin <= 0.1) continue
            velocities += (tail[i].glucoseMgdl - tail[i - 1].glucoseMgdl) / dtMin
        }
        if (velocities.size < 4) return null

        // OLS: v_t = a1 v_{t-1} + a2 v_{t-2}
        var s11 = 0.0
        var s12 = 0.0
        var s22 = 0.0
        var t1 = 0.0
        var t2 = 0.0
        for (t in 2 until velocities.size) {
            val x1 = velocities[t - 1]
            val x2 = velocities[t - 2]
            val y = velocities[t]
            s11 += x1 * x1
            s12 += x1 * x2
            s22 += x2 * x2
            t1 += x1 * y
            t2 += x2 * y
        }
        val det = s11 * s22 - s12 * s12
        if (abs(det) < 1e-9) return velocities.last()
        val a1 = (s22 * t1 - s12 * t2) / det
        val a2 = (s11 * t2 - s12 * t1) / det
        if (!a1.isFinite() || !a2.isFinite()) return velocities.last()
        val predicted = a1 * velocities.last() + a2 * velocities[velocities.size - 2]
        return if (predicted.isFinite()) predicted else velocities.last()
    }

    /**
     * Insulin and carb glucose effects from [from] to [to], applying the
     * ISF/CIR in effect at each 5-minute step end (and at [to]).
     */
    private fun perStepGlucoseEffects(
        input: GlucoseForecastInput,
        profile: DosingProfile,
        zoneId: ZoneId,
        from: Instant,
        to: Instant,
    ): Pair<Double, Double> {
        var t = from
        var insulinDrop = 0.0
        var carbRise = 0.0
        val stepMillis = STEP_MINUTES * 60_000L
        while (t.isBefore(to)) {
            val nextInstant = t.plusMillis(stepMillis)
            val next = if (nextInstant.isAfter(to)) to else nextInstant
            val settings = profile.toBolusSettings(next, zoneId)
            insulinDrop += IobCalculator.insulinAbsorbedBetween(
                input.boluses, input.diaHours, t, next,
            ) * settings.isfMgdl
            carbRise += CarbAbsorptionCalculator.carbsAbsorbedBetween(
                input.meals, input.carbActionHours, t, next,
            ) * mgdlPerGramCarb(settings.isfMgdl.toDouble(), settings.cirRatio.toDouble())
            t = next
        }
        return insulinDrop to carbRise
    }
}

/**
 * 2-state constant-velocity Kalman filter for glucose (mg/dL) and velocity
 * (mg/dL per minute).
 */
internal class GlucoseKalmanFilter(
    private val processVarVel: Double = 0.04,
    private val measureVar: Double = 9.0,
) {
    var glucose: Double = 0.0
        private set
    var velocity: Double = 0.0
        private set

    private var p00: Double = 100.0
    private var p01: Double = 0.0
    private var p10: Double = 0.0
    private var p11: Double = 1.0
    private var initialized: Boolean = false

    fun update(measurement: Double, dtMinutes: Double) {
        if (!initialized) {
            glucose = measurement
            velocity = 0.0
            initialized = true
            return
        }
        val dt = dtMinutes.coerceAtLeast(0.1)
        glucose += velocity * dt

        val qv = processVarVel * dt
        val qg = qv * dt * dt / 4.0
        val qgv = qv * dt / 2.0
        val np00 = p00 + dt * (p10 + p01) + dt * dt * p11 + qg
        val np01 = p01 + dt * p11 + qgv
        val np10 = p10 + dt * p11 + qgv
        val np11 = p11 + qv

        val s = np00 + measureVar
        if (s <= 0.0 || !s.isFinite()) return
        val k0 = np00 / s
        val k1 = np10 / s
        val innov = measurement - glucose
        glucose += k0 * innov
        velocity += k1 * innov
        p00 = (1.0 - k0) * np00
        p01 = (1.0 - k0) * np01
        p10 = np10 - k1 * np00
        p11 = np11 - k1 * np01
    }
}

/** RMSE helper for tests. */
internal fun rmse(actual: List<Double>, predicted: List<Double>): Double {
    require(actual.size == predicted.size && actual.isNotEmpty())
    val mean = actual.indices.sumOf { i ->
        val d = actual[i] - predicted[i]
        d * d
    } / actual.size
    return sqrt(mean)
}
