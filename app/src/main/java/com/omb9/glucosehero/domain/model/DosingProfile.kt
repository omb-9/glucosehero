package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.util.AppJson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable

/**
 * Physiological bounds for time-of-day ISF, CIR, and target glucose, plus
 * global DIA. UI editors and [validateDosingProfile] share these constants so a
 * value the editor accepts is a value the dosing path will use.
 *
 * **ISF (mg/dL per unit): [MIN_ISF_MGDL]–[MAX_ISF_MGDL].** Pediatric
 * high-sensitivity corrections can sit near 200–400; insulin resistance can
 * sit in the low tens. The previous single slider was 10–150; those values
 * remain valid. Floor is 5 so a typed pediatric value is not rejected, and
 * so existing stored 10–150 settings migrate losslessly.
 *
 * **CIR (g/U): [MIN_CIR_RATIO]–[MAX_CIR_RATIO].** The previous slider was
 * 1–50. Floor stays 1 so an existing user at CIR 1 is not invalidated on
 * upgrade. Ceiling widens to 150 for high pediatric ratios.
 *
 * **Target (mg/dL): [MIN_TARGET_MGDL]–[MAX_TARGET_MGDL].** Matches the
 * existing target-glucose slider (60–180), which is slightly wider than a
 * 70–180 textbook range so currently stored targets remain valid.
 *
 * **DIA (hours): [MIN_DIA_HOURS]–[MAX_DIA_HOURS].** Matches the existing DIA
 * slider. DIA is global, never a per-segment field: a time-varying DIA would
 * make [com.omb9.glucosehero.util.IobCalculator]'s Walsh triangle
 * discontinuous at segment boundaries.
 *
 * Canonical unit is mg/dL. Convert only at the display edge.
 */
object DosingBounds {
    const val MIN_ISF_MGDL: Float = 5f
    const val MAX_ISF_MGDL: Float = 400f
    const val MIN_CIR_RATIO: Float = 1f
    const val MAX_CIR_RATIO: Float = 150f
    const val MIN_TARGET_MGDL: Float = 60f
    const val MAX_TARGET_MGDL: Float = 180f
    const val MIN_DIA_HOURS: Float = 2f
    const val MAX_DIA_HOURS: Float = 8f
}

/**
 * One wall-clock segment of ISF / CIR / target. Start times are local
 * wall-clock ([LocalTime]), not UTC. The segment is in effect from [start]
 * until the next segment's start, wrapping past midnight for the last
 * segment. DIA is not stored here.
 */
@Immutable
data class DosingSegment(
    val start: LocalTime,
    val isfMgdl: Float,
    val cirRatio: Float,
    val targetGlucoseMgdl: Float,
)

/**
 * Time-of-day insulin-dosing profile: ordered segments that must tile
 * 00:00–24:00 with no gaps and no overlaps, plus a single global [diaHours].
 *
 * A single segment starting at 00:00 is exactly equivalent to a flat
 * [BolusSettings] (today's four-number model). Calculators still consume
 * [BolusSettings]; this type resolves *to* that point-in-time value.
 *
 * ## Time zone and DST
 *
 * Segments are local wall-clock. Resolution uses a [ZoneId] supplied by the
 * caller; dosing paths pass [ZoneId.systemDefault] at *evaluation* time so a
 * travel-day uses the zone the device is in now, not a zone cached at
 * process start.
 *
 * **Historical instants.** [at] `(Instant, ZoneId)` uses
 * `instant.atZone(zoneId)`, which applies the zone rules *in effect at that
 * instant*, not "today's" offset. A January 07:00Z and a July 07:00Z in
 * `America/New_York` therefore resolve to different local times (EST vs EDT).
 *
 * **Spring forward (gap).** A wall-clock time that does not exist (e.g. 02:30
 * on the US spring-forward day) is resolved with [ZonedDateTime.ofLocal]:
 * the local time is shifted forward by the gap duration, then the segment
 * in effect at that adjusted instant is used. Resolution never throws.
 *
 * **Fall back (overlap).** A repeated wall-clock time (e.g. 01:30 on the US
 * fall-back day) uses the **earlier offset / first occurrence**.
 * [ZonedDateTime.ofLocal] with a null preferred offset picks the earlier valid
 * offset (typically daylight time). This is deterministic.
 *
 * **Travel.** The profile does not store the zone of each historical bolus.
 * Look-back over a DIA window uses the *same* [ZoneId] the caller passed
 * (evaluation-time system default unless overridden). Crossing a zone
 * mid-DIA therefore reinterprets earlier instants in the destination zone.
 *
 * Clocks are injected by callers. This type does not call
 * `System.currentTimeMillis()` or `Instant.now()`.
 *
 * FEATURE: dosing-profiles
 */
@Immutable
data class DosingProfile(
    val diaHours: Float,
    val segments: List<DosingSegment>,
) {
    /**
     * Segment in effect at a local wall-clock time. Last start at or before
     * [time] wins; times before the first start (should not happen when the
     * first start is 00:00) wrap to the last segment.
     *
     * Callers that need validated ISF/CIR/target must [validate] first. An
     * invalid profile must never be treated as usable; use [resolvedBolusSettings].
     */
    fun at(time: LocalTime): DosingSegment {
        check(segments.isNotEmpty()) { "DosingProfile.at requires at least one segment" }
        val nano = time.toNanoOfDay()
        val idx = segments.indexOfLast { it.start.toNanoOfDay() <= nano }
        return if (idx >= 0) segments[idx] else segments.last()
    }

    /**
     * Segment in effect at [instant] in [zoneId], using the zone rules at
     * that instant (see class KDoc).
     */
    fun at(instant: Instant, zoneId: ZoneId): DosingSegment =
        at(instant.atZone(zoneId).toLocalTime())

    /**
     * Resolves a possibly-invalid or ambiguous wall-clock on [date] in
     * [zoneId] without throwing, then returns the segment in effect there.
     */
    fun atWallClock(date: LocalDate, time: LocalTime, zoneId: ZoneId): DosingSegment =
        at(resolveWallClock(date, time, zoneId), zoneId)

    fun toBolusSettings(time: LocalTime): BolusSettings =
        at(time).toBolusSettings(diaHours)

    fun toBolusSettings(instant: Instant, zoneId: ZoneId): BolusSettings =
        at(instant, zoneId).toBolusSettings(diaHours)

    /**
     * Dosing-path entry: refuse with reasons when the profile is invalid.
     * Never substitutes defaults.
     */
    fun resolvedBolusSettings(time: LocalTime): DosingResolveResult =
        when (val validated = validate()) {
            is DosingProfileValidation.Invalid -> DosingResolveResult.Refused(validated.issues)
            is DosingProfileValidation.Valid -> {
                val segment = at(time)
                DosingResolveResult.Ok(segment.toBolusSettings(diaHours), segment)
            }
        }

    fun resolvedBolusSettings(instant: Instant, zoneId: ZoneId): DosingResolveResult =
        when (val validated = validate()) {
            is DosingProfileValidation.Invalid -> DosingResolveResult.Refused(validated.issues)
            is DosingProfileValidation.Valid -> {
                val segment = at(instant, zoneId)
                DosingResolveResult.Ok(segment.toBolusSettings(diaHours), segment)
            }
        }

    /**
     * Exclusive end of [segment]: the next start, or midnight when [segment]
     * is last (it wraps to the following day). Used to name the block that
     * was in effect without resolving time again.
     */
    fun nextSegmentStart(segment: DosingSegment): LocalTime {
        val idx = segments.indexOfFirst { it.start == segment.start }
        return if (idx >= 0 && idx + 1 < segments.size) {
            segments[idx + 1].start
        } else {
            LocalTime.MIDNIGHT
        }
    }

    fun validate(): DosingProfileValidation = validateDosingProfile(diaHours, segments)

    /** Instant of the next segment boundary after [instant], for window walks. */
    fun nextTransitionAfter(instant: Instant, zoneId: ZoneId): Instant {
        val zoned = instant.atZone(zoneId)
        val local = zoned.toLocalTime()
        val date = zoned.toLocalDate()
        val nextStart = segments.firstOrNull { it.start.isAfter(local) }?.start
        val candidate = if (nextStart != null) {
            resolveWallClock(date, nextStart, zoneId)
        } else {
            resolveWallClock(date.plusDays(1), LocalTime.MIDNIGHT, zoneId)
        }
        return if (candidate.isAfter(instant)) {
            candidate
        } else {
            instant.plusSeconds(1)
        }
    }

    /**
     * Distinct segments whose local start is in effect at some instant in
     * `[start, end]` (inclusive). Empty when [segments] is empty.
     */
    fun segmentsOverlapping(start: Instant, end: Instant, zoneId: ZoneId): List<DosingSegment> {
        if (segments.isEmpty()) return emptyList()
        if (!end.isAfter(start)) return listOf(at(start, zoneId))
        val covered = ArrayList<DosingSegment>(segments.size)
        var t = start
        var guard = 0
        while (t <= end && guard < 512) {
            val seg = at(t, zoneId)
            if (covered.none { it.start == seg.start }) covered += seg
            val next = nextTransitionAfter(t, zoneId)
            if (!next.isAfter(t)) break
            t = next
            guard++
        }
        val atEnd = at(end, zoneId)
        if (covered.none { it.start == atEnd.start }) covered += atEnd
        return covered
    }

    fun midnightBolusSettings(): BolusSettings = toBolusSettings(LocalTime.MIDNIGHT)

    companion object {
        /** Flat 24-hour profile, bit-identical to today's [BolusSettings]. */
        fun single(settings: BolusSettings): DosingProfile = DosingProfile(
            diaHours = settings.diaHours,
            segments = listOf(
                DosingSegment(
                    start = LocalTime.MIDNIGHT,
                    isfMgdl = settings.isfMgdl,
                    cirRatio = settings.cirRatio,
                    targetGlucoseMgdl = settings.targetGlucoseMgdl,
                ),
            ),
        )
    }
}

private fun DosingSegment.toBolusSettings(diaHours: Float): BolusSettings = BolusSettings(
    diaHours = diaHours,
    cirRatio = cirRatio,
    isfMgdl = isfMgdl,
    targetGlucoseMgdl = targetGlucoseMgdl,
)

/**
 * Converts a local date+time in [zoneId] to an [Instant], never throwing on
 * DST gaps or overlaps.
 *
 * - Gap: [ZonedDateTime.ofLocal] shifts the local time forward by the
 *   transition duration; the caller then resolves the segment at that
 *   adjusted instant.
 * - Overlap: a null preferred offset selects the earlier valid offset (first
 *   occurrence). Documented in [DosingProfile].
 */
fun resolveWallClock(date: LocalDate, time: LocalTime, zoneId: ZoneId): Instant =
    ZonedDateTime.ofLocal(date.atTime(time), zoneId, null).toInstant()

/** Machine-stable validation / load failure codes. UI maps these to strings. */
enum class DosingProfileIssueCode {
    EMPTY_SEGMENTS,
    FIRST_SEGMENT_NOT_MIDNIGHT,
    STARTS_NOT_STRICTLY_INCREASING,
    DUPLICATE_START,
    ISF_OUT_OF_BOUNDS,
    CIR_OUT_OF_BOUNDS,
    TARGET_OUT_OF_BOUNDS,
    DIA_OUT_OF_BOUNDS,
    UNPARSEABLE,
}

@Immutable
data class DosingProfileIssue(
    val code: DosingProfileIssueCode,
    val segmentIndex: Int? = null,
)

/** First-class validation result. Not an exception and not a silent clamp. */
sealed class DosingProfileValidation {
    data class Valid(val profile: DosingProfile) : DosingProfileValidation()
    data class Invalid(val issues: List<DosingProfileIssue>) : DosingProfileValidation()
}

/** Dosing-path resolve: use [Ok] settings only; never invent ISF/CIR/target. */
sealed class DosingResolveResult {
    data class Ok(
        val settings: BolusSettings,
        val segment: DosingSegment,
    ) : DosingResolveResult()
    data class Refused(val issues: List<DosingProfileIssue>) : DosingResolveResult()
}

/**
 * Stored-profile load. [Invalid] must never be substituted with defaults on a
 * dosing path. [diaHours] remains readable because IOB decay is global DIA.
 *
 * [editorDraft] is a best-effort unvalidated profile for the settings editor
 * so the user can repair a bad store. Dosing paths must ignore it.
 */
sealed class DosingProfileLoad {
    data class Valid(val profile: DosingProfile) : DosingProfileLoad()
    data class Invalid(
        val issues: List<DosingProfileIssue>,
        val diaHours: Float,
        val editorDraft: DosingProfile? = null,
    ) : DosingProfileLoad()

    val diaHoursOrDefault: Float
        get() = when (this) {
            is Valid -> profile.diaHours
            is Invalid -> diaHours
        }
}

fun validateDosingProfile(
    diaHours: Float,
    segments: List<DosingSegment>,
): DosingProfileValidation {
    val issues = ArrayList<DosingProfileIssue>()
    if (diaHours < DosingBounds.MIN_DIA_HOURS || diaHours > DosingBounds.MAX_DIA_HOURS ||
        !diaHours.isFinite()
    ) {
        issues += DosingProfileIssue(DosingProfileIssueCode.DIA_OUT_OF_BOUNDS)
    }
    if (segments.isEmpty()) {
        issues += DosingProfileIssue(DosingProfileIssueCode.EMPTY_SEGMENTS)
        return DosingProfileValidation.Invalid(issues)
    }
    if (segments.first().start != LocalTime.MIDNIGHT) {
        issues += DosingProfileIssue(
            DosingProfileIssueCode.FIRST_SEGMENT_NOT_MIDNIGHT,
            segmentIndex = 0,
        )
    }
    for (i in 1 until segments.size) {
        val prev = segments[i - 1].start.toNanoOfDay()
        val cur = segments[i].start.toNanoOfDay()
        when {
            cur == prev -> issues += DosingProfileIssue(
                DosingProfileIssueCode.DUPLICATE_START,
                segmentIndex = i,
            )
            cur < prev -> issues += DosingProfileIssue(
                DosingProfileIssueCode.STARTS_NOT_STRICTLY_INCREASING,
                segmentIndex = i,
            )
        }
    }
    segments.forEachIndexed { index, segment ->
        if (!segment.isfMgdl.isFinite() ||
            segment.isfMgdl < DosingBounds.MIN_ISF_MGDL ||
            segment.isfMgdl > DosingBounds.MAX_ISF_MGDL
        ) {
            issues += DosingProfileIssue(DosingProfileIssueCode.ISF_OUT_OF_BOUNDS, index)
        }
        if (!segment.cirRatio.isFinite() ||
            segment.cirRatio < DosingBounds.MIN_CIR_RATIO ||
            segment.cirRatio > DosingBounds.MAX_CIR_RATIO
        ) {
            issues += DosingProfileIssue(DosingProfileIssueCode.CIR_OUT_OF_BOUNDS, index)
        }
        if (!segment.targetGlucoseMgdl.isFinite() ||
            segment.targetGlucoseMgdl < DosingBounds.MIN_TARGET_MGDL ||
            segment.targetGlucoseMgdl > DosingBounds.MAX_TARGET_MGDL
        ) {
            issues += DosingProfileIssue(DosingProfileIssueCode.TARGET_OUT_OF_BOUNDS, index)
        }
    }
    val profile = DosingProfile(diaHours = diaHours, segments = segments)
    return if (issues.isEmpty()) {
        DosingProfileValidation.Valid(profile)
    } else {
        DosingProfileValidation.Invalid(issues)
    }
}

/**
 * JSON record stored in DataStore and backups.
 *
 * [start] is `"HH:mm"` local wall-clock. Flat DataStore keys remain the 00:00
 * segment (plus global DIA) for backward readers; they are not "current
 * resolved" values, so a backup does not depend on the time of day it was
 * written.
 *
 * FEATURE: dosing-profiles
 */
@Serializable
data class DosingSegmentRecord(
    val start: String,
    val isfMgdl: Float,
    val cirRatio: Float,
    val targetGlucoseMgdl: Float,
)

@Serializable
data class DosingProfileRecord(
    val diaHours: Float,
    val segments: List<DosingSegmentRecord> = emptyList(),
)

private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun DosingSegment.toRecord(): DosingSegmentRecord = DosingSegmentRecord(
    start = start.format(START_FORMAT),
    isfMgdl = isfMgdl,
    cirRatio = cirRatio,
    targetGlucoseMgdl = targetGlucoseMgdl,
)

fun DosingProfile.toRecord(): DosingProfileRecord = DosingProfileRecord(
    diaHours = diaHours,
    segments = segments.map { it.toRecord() },
)

fun DosingSegmentRecord.toSegmentOrNull(): DosingSegment? {
    val parsed = parseSegmentStart(start) ?: return null
    return DosingSegment(
        start = parsed,
        isfMgdl = isfMgdl,
        cirRatio = cirRatio,
        targetGlucoseMgdl = targetGlucoseMgdl,
    )
}

fun DosingProfileRecord.toProfile(): DosingProfile = DosingProfile(
    diaHours = diaHours,
    segments = segments.mapNotNull { it.toSegmentOrNull() },
)

fun parseSegmentStart(raw: String): LocalTime? {
    val trimmed = raw.trim()
    return try {
        LocalTime.parse(trimmed, START_FORMAT)
    } catch (_: DateTimeParseException) {
        try {
            LocalTime.parse(trimmed)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/**
 * DataStore / backup codec. Pure: no clock, no DataStore.
 *
 * If [profileJson] is absent, synthesizes a single 00:00 segment from the
 * four flat keys (legacy). That migration is lossless: dosing math matches
 * the previous flat [BolusSettings].
 *
 * If [profileJson] is present but unparseable or invalid, returns
 * [DosingProfileLoad.Invalid] and does **not** fall back to the flat keys
 * for ISF/CIR/target. Dosing paths must refuse.
 *
 * FEATURE: dosing-profiles
 */
object DosingProfileCodec {
    fun encode(profile: DosingProfile): String =
        AppJson.encodeToString(DosingProfileRecord.serializer(), profile.toRecord())

    fun decodeRecord(json: String): DosingProfileRecord? =
        runCatching { AppJson.decodeFromString(DosingProfileRecord.serializer(), json) }.getOrNull()

    fun load(
        profileJson: String?,
        diaHours: Float?,
        cirRatio: Float?,
        isfMgdl: Float?,
        targetGlucoseMgdl: Float?,
    ): DosingProfileLoad {
        val flatDia = diaHours ?: 4.0f
        val flat = BolusSettings(
            diaHours = flatDia,
            cirRatio = cirRatio ?: 10.0f,
            isfMgdl = isfMgdl ?: 50.0f,
            targetGlucoseMgdl = targetGlucoseMgdl ?: 100.0f,
        )
        if (profileJson.isNullOrBlank()) {
            val migrated = DosingProfile.single(flat)
            return when (val v = migrated.validate()) {
                is DosingProfileValidation.Valid -> DosingProfileLoad.Valid(v.profile)
                is DosingProfileValidation.Invalid -> DosingProfileLoad.Invalid(
                    issues = v.issues,
                    diaHours = flatDia,
                    editorDraft = migrated,
                )
            }
        }
        val record = decodeRecord(profileJson)
        if (record == null) {
            return DosingProfileLoad.Invalid(
                issues = listOf(DosingProfileIssue(DosingProfileIssueCode.UNPARSEABLE)),
                diaHours = flatDia,
                editorDraft = DosingProfile.single(flat),
            )
        }
        val parsed = record.toProfile()
        return when (val v = parsed.validate()) {
            is DosingProfileValidation.Valid -> DosingProfileLoad.Valid(v.profile)
            is DosingProfileValidation.Invalid -> DosingProfileLoad.Invalid(
                issues = v.issues,
                diaHours = parsed.diaHours,
                editorDraft = parsed,
            )
        }
    }
}
