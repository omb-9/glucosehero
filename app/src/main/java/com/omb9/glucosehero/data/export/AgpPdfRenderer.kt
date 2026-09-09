package com.omb9.glucosehero.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.util.RangeCategory
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-page, print-ready Ambulatory Glucose Profile rendered with the
 * platform [PdfDocument] + [Canvas] APIs already used by clinical CSV/PDF
 * export. Charts are drawn as vector paths, not placeholder boxes.
 *
 * Page 1: patient header, TIR stacked bar, GMI / CV% / mean.
 * Page 2: 24-hour modal-day percentile curves (10/25/50/75/90).
 * Page 3: daily min/avg/max table when the 14-day window has day-level rows.
 */
object AgpPdfRenderer {

    fun write(file: File, report: AgpReport, profile: UserProfile) {
        val document = PdfDocument()
        try {
            var pageNumber = 0
            pageNumber = drawSummaryPage(document, report, profile, pageNumber + 1)
            if (report.sufficient) {
                pageNumber = drawModalDayPage(document, report, pageNumber + 1)
                if (report.daily.isNotEmpty()) {
                    drawDailyTablePages(document, report, pageNumber + 1)
                }
            }
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun drawSummaryPage(
        document: PdfDocument,
        report: AgpReport,
        profile: UserProfile,
        pageNumber: Int,
    ): Int {
        val page = document.startPage(pageInfo(pageNumber))
        val canvas = page.canvas
        val paints = AgpPaints()
        var y = MARGIN

        y = drawTitle(canvas, paints, y, "Ambulatory Glucose Profile")
        y = drawSubtitle(
            canvas,
            paints,
            y,
            "${report.windowDays}-day clinical glucose report  ·  values in mg/dL",
        )
        y = drawDivider(canvas, paints, y)

        val patientName = profile.name.trim().takeIf { it.isNotEmpty() }
        if (patientName != null) {
            y = drawBody(canvas, paints, y, "Patient: $patientName")
        }
        val profileDetails = listOfNotNull(
            profile.diabetesType?.trim()?.takeIf { it.isNotEmpty() }?.let { "Type: $it" },
            profile.age?.let { "Age: $it" },
        ).joinToString("   ")
        if (profileDetails.isNotBlank()) {
            y = drawBody(canvas, paints, y, profileDetails)
        }
        y = drawBody(
            canvas,
            paints,
            y,
            "Reporting period: ${DATE.format(report.periodStartDate)} to ${DATE.format(report.periodEndDate)}",
        )
        y = drawBody(
            canvas,
            paints,
            y,
            "Generated: ${TIMESTAMP.format(Instant.ofEpochMilli(report.endMillis))}",
        )
        y = drawBody(
            canvas,
            paints,
            y,
            "Readings: ${report.readingCount}  ·  Active days: ${report.activeDays} of ${report.windowDays}" +
                "  ·  CGM samples: ${report.cgmReadingCount}  ·  Manual: ${report.manualReadingCount}",
        )
        y += 8f

        if (!report.sufficient) {
            y = drawInsufficient(canvas, paints, y, report)
        } else {
            report.tir?.let { y = drawTirSection(canvas, paints, y, it, report.thresholds) }
            y += 10f
            y = drawMetricRow(canvas, paints, y, report)
            y += 16f
            y = drawRangeKey(canvas, paints, y, report.thresholds)
        }

        y = max(y + 16f, PAGE_HEIGHT - MARGIN - 48f)
        drawCaption(
            canvas,
            paints,
            y,
            "Informational report only. Not a medical device. Confirm treatment decisions with a clinician.",
        )
        drawFooter(canvas, paints, pageNumber)
        document.finishPage(page)
        return pageNumber
    }

    private fun drawModalDayPage(
        document: PdfDocument,
        report: AgpReport,
        pageNumber: Int,
    ): Int {
        val page = document.startPage(pageInfo(pageNumber))
        val canvas = page.canvas
        val paints = AgpPaints()
        var y = MARGIN
        y = drawTitle(canvas, paints, y, "24-hour glucose profile")
        y = drawSubtitle(
            canvas,
            paints,
            y,
            "Modal day overlay  ·  10th / 25th / 50th / 75th / 90th percentiles  ·  15-minute bins",
        )
        y = drawDivider(canvas, paints, y)
        y = drawBody(
            canvas,
            paints,
            y,
            "Shaded bands show typical daily variation. The dark line is the median (50th percentile).",
        )
        y += 8f
        drawModalDayChart(canvas, paints, y, report)
        drawFooter(canvas, paints, pageNumber)
        document.finishPage(page)
        return pageNumber
    }

    private fun drawDailyTablePages(
        document: PdfDocument,
        report: AgpReport,
        startPage: Int,
    ): Int {
        val paints = AgpPaints()
        var pageNumber = startPage
        var index = 0
        val rows = report.daily
        while (index < rows.size) {
            val page = document.startPage(pageInfo(pageNumber))
            val canvas = page.canvas
            var y = MARGIN
            if (pageNumber == startPage) {
                y = drawTitle(canvas, paints, y, "Daily glucose summary")
                y = drawSubtitle(canvas, paints, y, "Each local calendar day in the ${report.windowDays}-day window")
                y = drawDivider(canvas, paints, y)
            }
            y = drawDailyHeader(canvas, paints, y)
            val bottom = PAGE_HEIGHT - MARGIN - 12f
            while (index < rows.size && y + ROW_HEIGHT <= bottom) {
                y = drawDailyRow(canvas, paints, rows[index], y, index % 2 == 0)
                index++
            }
            drawFooter(canvas, paints, pageNumber)
            document.finishPage(page)
            if (index < rows.size) pageNumber++
        }
        return pageNumber
    }

    private fun drawInsufficient(
        canvas: Canvas,
        paints: AgpPaints,
        startY: Float,
        report: AgpReport,
    ): Float {
        var y = startY
        val boxTop = y
        val boxBottom = y + 96f
        canvas.drawRoundRect(
            RectF(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxBottom),
            8f,
            8f,
            paints.noticeFill,
        )
        y += 18f
        canvas.drawText(
            "Not enough data for a standard AGP",
            MARGIN + 14f,
            y,
            paints.sectionTitle,
        )
        y += 18f
        val reason = report.insufficientReason
            ?: "Log glucose readings to generate this report."
        wrapText(reason, paints.body, PAGE_WIDTH - MARGIN * 2 - 28f).forEach { line ->
            canvas.drawText(line, MARGIN + 14f, y, paints.body)
            y += 14f
        }
        y += 8f
        canvas.drawText(
            "Standard AGP uses ${report.windowDays} days. Charts appear after " +
                "${AgpReportCalculator.MIN_READINGS} readings across " +
                "${AgpReportCalculator.MIN_DAYS} calendar days.",
            MARGIN + 14f,
            min(y, boxBottom - 12f),
            paints.caption,
        )
        return boxBottom + 16f
    }

    private fun drawTirSection(
        canvas: Canvas,
        paints: AgpPaints,
        startY: Float,
        tir: AgpTirPercents,
        thresholds: AgpRangeThresholds,
    ): Float {
        var y = startY
        canvas.drawText("Time in range", MARGIN, y, paints.sectionTitle)
        y += 8f
        val inRangeLabel = "${tir.inRange.roundToInt()}% in range " +
            "(${fmtBound(thresholds.targetLowMgdl)}–${fmtBound(thresholds.targetHighMgdl)} mg/dL)"
        canvas.drawText(inRangeLabel, MARGIN, y + 14f, paints.body)
        y += 24f

        val barLeft = MARGIN
        val barRight = PAGE_WIDTH - MARGIN
        val barTop = y
        val barBottom = y + TIR_BAR_HEIGHT
        val width = barRight - barLeft
        val segments = listOf(
            RangeCategory.VERY_LOW to tir.veryLow,
            RangeCategory.LOW to tir.low,
            RangeCategory.IN_RANGE to tir.inRange,
            RangeCategory.HIGH to tir.high,
            RangeCategory.VERY_HIGH to tir.veryHigh,
        )
        var x = barLeft
        for ((category, percent) in segments) {
            val segWidth = width * (percent / 100f).coerceAtLeast(0f)
            paints.tirFill.color = colorFor(category)
            if (segWidth > 0.5f) {
                canvas.drawRect(x, barTop, x + segWidth, barBottom, paints.tirFill)
                if (segWidth > 36f) {
                    val label = "${percent.roundToInt()}%"
                    val textWidth = paints.tirLabel.measureText(label)
                    canvas.drawText(
                        label,
                        x + (segWidth - textWidth) / 2f,
                        barTop + TIR_BAR_HEIGHT / 2f + 4f,
                        paints.tirLabel,
                    )
                }
            }
            x += segWidth
        }
        canvas.drawRect(barLeft, barTop, barRight, barBottom, paints.strokeThin)
        y = barBottom + 18f

        val legend = listOf(
            "Very low <${fmtBound(thresholds.veryLowMgdl)}" to RangeCategory.VERY_LOW,
            "Low" to RangeCategory.LOW,
            "In range" to RangeCategory.IN_RANGE,
            "High" to RangeCategory.HIGH,
            "Very high >${fmtBound(thresholds.veryHighMgdl)}" to RangeCategory.VERY_HIGH,
        )
        var legendX = MARGIN
        for ((label, category) in legend) {
            paints.tirFill.color = colorFor(category)
            canvas.drawRect(legendX, y - 8f, legendX + 10f, y + 2f, paints.tirFill)
            canvas.drawText(label, legendX + 14f, y, paints.caption)
            legendX += paints.caption.measureText(label) + 32f
        }
        return y + 12f
    }

    private fun drawMetricRow(
        canvas: Canvas,
        paints: AgpPaints,
        startY: Float,
        report: AgpReport,
    ): Float {
        val gap = 10f
        val boxWidth = (PAGE_WIDTH - MARGIN * 2 - gap * 3) / 4f
        val boxHeight = 72f
        val items = listOf(
            "GMI" to (report.gmiPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A"),
            "CV" to (report.cvPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A"),
            "Mean" to (report.meanMgdl?.let { String.format(Locale.US, "%.0f mg/dL", it) } ?: "N/A"),
            "TIR" to (report.tir?.inRange?.let { String.format(Locale.US, "%.0f%%", it) } ?: "N/A"),
        )
        items.forEachIndexed { index, (label, value) ->
            val left = MARGIN + index * (boxWidth + gap)
            val rect = RectF(left, startY, left + boxWidth, startY + boxHeight)
            canvas.drawRoundRect(rect, 6f, 6f, paints.cardFill)
            canvas.drawRoundRect(rect, 6f, 6f, paints.strokeThin)
            canvas.drawText(label, left + 10f, startY + 18f, paints.caption)
            canvas.drawText(value, left + 10f, startY + 48f, paints.metric)
        }
        val captionY = startY + boxHeight + 16f
        val gmiNote = "GMI (estimated A1c) uses 3.31 + 0.02392 × mean glucose (mg/dL)."
        val cvNote = "CV% is the sample standard deviation divided by the mean. Consensus target is under 36%."
        canvas.drawText(gmiNote, MARGIN, captionY, paints.caption)
        canvas.drawText(cvNote, MARGIN, captionY + 12f, paints.caption)
        return captionY + 12f
    }

    private fun drawRangeKey(
        canvas: Canvas,
        paints: AgpPaints,
        startY: Float,
        thresholds: AgpRangeThresholds,
    ): Float {
        var y = startY
        canvas.drawText("Range definitions (mg/dL)", MARGIN, y, paints.sectionTitle)
        y += 16f
        val rows = listOf(
            "Very low" to "< ${fmtBound(thresholds.veryLowMgdl)}",
            "Low" to "${fmtBound(thresholds.veryLowMgdl)} to < ${fmtBound(thresholds.targetLowMgdl)}",
            "In range" to "${fmtBound(thresholds.targetLowMgdl)} to ${fmtBound(thresholds.targetHighMgdl)}",
            "High" to "> ${fmtBound(thresholds.targetHighMgdl)} to ${fmtBound(thresholds.veryHighMgdl)}",
            "Very high" to "> ${fmtBound(thresholds.veryHighMgdl)}",
        )
        rows.forEachIndexed { index, (name, range) ->
            if (index % 2 == 0) {
                canvas.drawRect(MARGIN, y - 11f, PAGE_WIDTH - MARGIN, y + 5f, paints.rowAlt)
            }
            canvas.drawText(name, MARGIN + 6f, y, paints.body)
            canvas.drawText(range, MARGIN + 160f, y, paints.body)
            y += 16f
        }
        y += 6f
        canvas.drawText(
            "Very low / very high cutoffs are international consensus values (54 and 250 mg/dL). " +
                "In-range bounds follow this profile's target range.",
            MARGIN,
            y,
            paints.caption,
        )
        return y
    }

    private fun drawModalDayChart(
        canvas: Canvas,
        paints: AgpPaints,
        top: Float,
        report: AgpReport,
    ) {
        val left = MARGIN + 36f
        val right = PAGE_WIDTH - MARGIN - 8f
        val bottom = PAGE_HEIGHT - MARGIN - 56f
        val chartTop = top + 8f
        val yMin = 40f
        val yMax = 400f
        val thresholds = report.thresholds

        fun xForMinutes(minutes: Int): Float {
            return left + (right - left) * (minutes / (24f * 60f))
        }

        fun yForMgdl(mgdl: Double): Float {
            val clamped = mgdl.toFloat().coerceIn(yMin, yMax)
            return bottom - (bottom - chartTop) * ((clamped - yMin) / (yMax - yMin))
        }

        paints.targetBand.color = Color.parseColor("#E8F5E9")
        canvas.drawRect(
            left,
            yForMgdl(thresholds.targetHighMgdl.toDouble()),
            right,
            yForMgdl(thresholds.targetLowMgdl.toDouble()),
            paints.targetBand,
        )

        val populated = report.modalDay.filter { it.hasPercentiles }
        if (populated.size >= 2) {
            drawBand(canvas, paints.p10Fill, populated, ::xForMinutes, ::yForMgdl) { it.p10!! to it.p90!! }
            drawBand(canvas, paints.p25Fill, populated, ::xForMinutes, ::yForMgdl) { it.p25!! to it.p75!! }
            val median = Path()
            populated.forEachIndexed { index, bin ->
                val x = xForMinutes(bin.minutesFromMidnight)
                val y = yForMgdl(bin.p50!!)
                if (index == 0) median.moveTo(x, y) else median.lineTo(x, y)
            }
            canvas.drawPath(median, paints.medianStroke)
        } else {
            canvas.drawText(
                "Not enough overlapping clock-time samples to draw percentile curves.",
                left,
                (chartTop + bottom) / 2f,
                paints.body,
            )
        }

        paints.dash.color = Color.parseColor("#B71C1C")
        canvas.drawLine(left, yForMgdl(thresholds.veryLowMgdl.toDouble()), right, yForMgdl(thresholds.veryLowMgdl.toDouble()), paints.dash)
        paints.dash.color = Color.parseColor("#E65100")
        canvas.drawLine(left, yForMgdl(thresholds.veryHighMgdl.toDouble()), right, yForMgdl(thresholds.veryHighMgdl.toDouble()), paints.dash)

        canvas.drawRect(left, chartTop, right, bottom, paints.strokeThin)

        val yTicks = listOf(40, 70, 140, 180, 250, 350, 400)
        for (tick in yTicks) {
            val y = yForMgdl(tick.toDouble())
            canvas.drawLine(left - 4f, y, left, y, paints.strokeThin)
            val label = tick.toString()
            canvas.drawText(label, MARGIN, y + 3f, paints.axis)
        }
        canvas.drawText("mg/dL", MARGIN, chartTop - 6f, paints.axis)

        for (hour in 0..24 step 3) {
            val x = xForMinutes(hour * 60)
            canvas.drawLine(x, bottom, x, bottom + 4f, paints.strokeThin)
            val label = String.format(Locale.US, "%02d:00", hour % 24)
            val width = paints.axis.measureText(label)
            canvas.drawText(label, x - width / 2f, bottom + 16f, paints.axis)
        }

        var legendX = left
        val legendY = bottom + 34f
        legendSwatch(canvas, paints, legendX, legendY, paints.p10Fill.color, "10–90th")
        legendX += 72f
        legendSwatch(canvas, paints, legendX, legendY, paints.p25Fill.color, "25–75th")
        legendX += 72f
        canvas.drawLine(legendX, legendY - 3f, legendX + 18f, legendY - 3f, paints.medianStroke)
        canvas.drawText("Median", legendX + 22f, legendY, paints.caption)
        legendX += 72f
        canvas.drawText(
            "Green band: ${fmtBound(thresholds.targetLowMgdl)}–${fmtBound(thresholds.targetHighMgdl)} mg/dL",
            legendX,
            legendY,
            paints.caption,
        )
    }

    private fun drawBand(
        canvas: Canvas,
        paint: Paint,
        bins: List<ModalDayBin>,
        xForMinutes: (Int) -> Float,
        yForMgdl: (Double) -> Float,
        bounds: (ModalDayBin) -> Pair<Double, Double>,
    ) {
        val path = Path()
        bins.forEachIndexed { index, bin ->
            val x = xForMinutes(bin.minutesFromMidnight)
            val y = yForMgdl(bounds(bin).second)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in bins.indices.reversed()) {
            val bin = bins[i]
            path.lineTo(xForMinutes(bin.minutesFromMidnight), yForMgdl(bounds(bin).first))
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun legendSwatch(
        canvas: Canvas,
        paints: AgpPaints,
        x: Float,
        y: Float,
        color: Int,
        label: String,
    ) {
        val saved = paints.tirFill.color
        paints.tirFill.color = color
        canvas.drawRect(x, y - 8f, x + 12f, y + 2f, paints.tirFill)
        paints.tirFill.color = saved
        canvas.drawText(label, x + 16f, y, paints.caption)
    }

    private fun drawDailyHeader(canvas: Canvas, paints: AgpPaints, yStart: Float): Float {
        canvas.drawRect(MARGIN, yStart - 4f, PAGE_WIDTH - MARGIN, yStart + ROW_HEIGHT, paints.headerFill)
        canvas.drawText("Date", COL_DAY + 4f, yStart + 16f, paints.header)
        canvas.drawText("Readings", COL_N, yStart + 16f, paints.header)
        canvas.drawText("Avg", COL_AVG, yStart + 16f, paints.header)
        canvas.drawText("Min", COL_MIN, yStart + 16f, paints.header)
        canvas.drawText("Max", COL_MAX, yStart + 16f, paints.header)
        return yStart + ROW_HEIGHT
    }

    private fun drawDailyRow(
        canvas: Canvas,
        paints: AgpPaints,
        row: com.omb9.glucosehero.domain.model.DailyGlucoseSummary,
        yStart: Float,
        shaded: Boolean,
    ): Float {
        if (shaded) {
            canvas.drawRect(MARGIN, yStart, PAGE_WIDTH - MARGIN, yStart + ROW_HEIGHT, paints.rowAlt)
        }
        canvas.drawText(row.day, COL_DAY + 4f, yStart + 16f, paints.cell)
        canvas.drawText(row.readings.toString(), COL_N, yStart + 16f, paints.cell)
        canvas.drawText(String.format(Locale.US, "%.0f", row.avgMgdl), COL_AVG, yStart + 16f, paints.cell)
        canvas.drawText(String.format(Locale.US, "%.0f", row.minMgdl), COL_MIN, yStart + 16f, paints.cell)
        canvas.drawText(String.format(Locale.US, "%.0f", row.maxMgdl), COL_MAX, yStart + 16f, paints.cell)
        return yStart + ROW_HEIGHT
    }

    private fun drawTitle(canvas: Canvas, paints: AgpPaints, y: Float, text: String): Float {
        canvas.drawText(text, MARGIN, y + 20f, paints.title)
        return y + 28f
    }

    private fun drawSubtitle(canvas: Canvas, paints: AgpPaints, y: Float, text: String): Float {
        canvas.drawText(text, MARGIN, y + 12f, paints.subtitle)
        return y + 20f
    }

    private fun drawDivider(canvas: Canvas, paints: AgpPaints, y: Float): Float {
        canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, paints.strokeThin)
        return y + 16f
    }

    private fun drawBody(canvas: Canvas, paints: AgpPaints, y: Float, text: String): Float {
        canvas.drawText(text, MARGIN, y + 12f, paints.body)
        return y + 16f
    }

    private fun drawCaption(canvas: Canvas, paints: AgpPaints, y: Float, text: String): Float {
        wrapText(text, paints.caption, PAGE_WIDTH - MARGIN * 2).forEachIndexed { index, line ->
            canvas.drawText(line, MARGIN, y + index * 11f, paints.caption)
        }
        return y
    }

    private fun drawFooter(canvas: Canvas, paints: AgpPaints, pageNumber: Int) {
        val text = "GlucoseHero AGP  ·  Page $pageNumber"
        val width = paints.footer.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - width) / 2f, PAGE_HEIGHT - 18f, paints.footer)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun fmtBound(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    private fun colorFor(category: RangeCategory): Int = when (category) {
        RangeCategory.VERY_LOW -> Color.parseColor("#283593")
        RangeCategory.LOW -> Color.parseColor("#1565C0")
        RangeCategory.IN_RANGE -> Color.parseColor("#2E7D32")
        RangeCategory.HIGH -> Color.parseColor("#D84315")
        RangeCategory.VERY_HIGH -> Color.parseColor("#B71C1C")
    }

    private fun pageInfo(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val TIR_BAR_HEIGHT = 28f
    private const val ROW_HEIGHT = 22f
    private const val COL_DAY = 40f
    private const val COL_N = 200f
    private const val COL_AVG = 300f
    private const val COL_MIN = 400f
    private const val COL_MAX = 480f

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
}

private class AgpPaints {
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 9f
    }
    val sectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 10f
    }
    val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 8f
    }
    val metric = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 9f
    }
    val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 8f
    }
    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 8f
    }
    val tirLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val tirFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val targetBand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val p10Fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#BDBDBD")
    }
    val p25Fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#757575")
    }
    val medianStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        color = Color.parseColor("#212121")
    }
    val strokeThin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#BDBDBD")
    }
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FAFAFA")
    }
    val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F4F4F6")
    }
    val rowAlt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FAFAFC")
    }
    val noticeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFF8E1")
    }
}
