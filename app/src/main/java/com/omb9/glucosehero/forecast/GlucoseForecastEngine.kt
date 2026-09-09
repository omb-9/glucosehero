package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Instant
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
 * points after they are resampled onto a 5-minute grid. Needs at least 4
 * velocity observations; otherwise the AR term is skipped.
 *
 * **Kalman.** State is `[glucose (mg/dL), velocity (mg/dL/min)]`. Measurement
 * noise `R = 9` (CGM RMSE of ~3 mg/dL). Velocity process noise is
 * `0.04 (mg/dL/min)^2` per minute so the filter tracks a trend change in
 * about 15 minutes without chasing sensor noise.
 *
 * **IOB decay.** Walsh / Scheiner activity triangle identical to
 * [IobCalculator]: 100% of a rapid-acting bolus is active at delivery, peak
 * activity around 50-75 min, 0% after [diaHours]. IOB(t) is monotonically
 * decreasing. The glucose drop over a horizon is
 * `(IOB_now - IOB_horizon) * ISF` mg/dL. The curve is approximate, not a
 * dosing recommendation.
 *
 * **Carb absorption.** Linear mixed-meal curve from
 * [CarbAbsorptionCalculator] (default 3.0 h). The glucose rise over a
 * horizon is `carbs_absorbed * (ISF / CIR)` mg/dL, because CIR grams are
 * covered by 1 U and 1 U drops glucose by ISF mg/dL.
 *
 * **Velocity blend.** `0.55 * v_kalman + 0.45 * v_ar` when AR is available,
 * otherwise Kalman alone. IOB and carb effects are added on top of this
 * kinematic projection so they are not double-counted in the AR residual.
 *
 * **Clamp.** Projections are clamped to 40-400 mg/dL. Missing CGM data
 * returns [GlucoseForecastSnapshot.insufficientData]. Missing IOB/carb
 * logs are treated as zero. This is an estimate, not a therapy recommendation.
 */
object GlucoseForecastEngine {

    const val HORIZON_MINUTES: Int = 60
    const val STEP_MINUTES: Int = 5
    const val HIGHLIGHT_MINUTES_30: Int = 30
    const val HIGHLIGHT_MINUTES_60: Int = 60
    const val LOOKBACK_MINUTES: Int = 90
    const val MIN_SAMPLES: Int = 3
    const val GRID_MINUTES: Double = 5.0
    const val MAX_AR_SAMPLES: Int = 12
    const val MIN_GLUCOSE_MGDL: Double = 40.0
    const val MAX_GLUCOSE_MGDL: Double = 400.0

    private const val KALMAN_BLEND: Double = 0.55
    private const val AR_BLEND: Double = 0.45

    fun forecast(
        input: GlucoseForecastInput,
        now: Instant = Instant.now(),
    ): GlucoseForecastSnapshot {
        val samples = input.samples
            .filter { it.glucoseMgdl.isFinite() && it.timestampMillis <= now.toEpochMilli() }
            .sortedBy { it.timestampMillis }
        val latest = samples.lastOrNull()
        val iob = IobCalculator.activeInsulinOnBoard(input.boluses, input.diaHours, now)
        val cob = CarbAbsorptionCalculator.carbsOnBoard(input.meals, input.carbActionHours, now)

        if (latest == null || samples.size < MIN_SAMPLES) {
            return GlucoseForecastSnapshot(
                generatedAtMillis = now.toEpochMilli(),
                currentMgdl = latest?.glucoseMgdl ?: 0.0,
                currentTimestampMillis = latest?.timestampMillis ?: now.toEpochMilli(),
                velocityMgdlPerMin = 0.0,
                iobUnits = iob,
                cobGrams = cob,
                points = emptyList(),
                sampleCount = samples.size,
                insufficientData = true,
            )
        }

        val lookbackStart = now.toEpochMilli() - LOOKBACK_MINUTES * 60_000L
        val recent = samples.filter { it.timestampMillis >= lookbackStart }
        val series = if (recent.size >= MIN_SAMPLES) recent else samples.takeLast(MIN_SAMPLES)

        val kalman = GlucoseKalmanFilter()
        for (i in series.indices) {
            val dtMin = if (i == 0) {
                GRID_MINUTES
            } else {
                (series[i].timestampMillis - series[i - 1].timestampMillis) / 60_000.0
            }
            kalman.update(series[i].glucoseMgdl, dtMin)
        }

        val arVelocity = fitAr2Velocity(series)
        val blendedVelocity = if (arVelocity == null) {
            kalman.velocity
        } else {
            KALMAN_BLEND * kalman.velocity + AR_BLEND * arVelocity
        }

        val mgdlPerGram = mgdlPerGramCarb(input.isfMgdl, input.cirRatio)
        val points = ArrayList<GlucoseForecastPoint>(HORIZON_MINUTES / STEP_MINUTES)
        var horizon = STEP_MINUTES
        while (horizon <= HORIZON_MINUTES) {
            val at = now.plusMillis(horizon * 60_000L)
            val kinematic = latest.glucoseMgdl + blendedVelocity * horizon
            val insulinDrop = IobCalculator.insulinAbsorbedBetween(
                input.boluses, input.diaHours, now, at,
            ) * input.isfMgdl
            val carbRise = CarbAbsorptionCalculator.carbsAbsorbedBetween(
                input.meals, input.carbActionHours, now, at,
            ) * mgdlPerGram
            val projected = (kinematic - insulinDrop + carbRise)
                .coerceIn(MIN_GLUCOSE_MGDL, MAX_GLUCOSE_MGDL)
            points += GlucoseForecastPoint(
                timestampMillis = at.toEpochMilli(),
                minutesAhead = horizon,
                glucoseMgdl = projected,
            )
            horizon += STEP_MINUTES
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
        )
    }

    /**
     * 1 g carbohydrate raises glucose by ISF/CIR mg/dL. Guard CIR <= 0.
     */
    fun mgdlPerGramCarb(isfMgdl: Double, cirRatio: Double): Double {
        if (cirRatio <= 0.0 || isfMgdl <= 0.0) return 0.0
        return isfMgdl / cirRatio
    }

    /**
     * AR(2) on 5-minute first differences. Returns the one-step-ahead velocity
     * in mg/dL per minute, or null when the series is too short or singular.
     */
    internal fun fitAr2Velocity(series: List<GlucoseForecastInput.GlucoseSample>): Double? {
        if (series.size < 5) return null
        val tail = series.takeLast(MAX_AR_SAMPLES)
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
