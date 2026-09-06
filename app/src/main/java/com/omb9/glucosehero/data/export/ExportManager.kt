package com.omb9.glucosehero.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.withClip
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.ExportedFile
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates shareable clinical export files (PDF / CSV) from the last 90 days
 * of logged entries.
 *
 * All disk I/O is performed on [Dispatchers.IO]. Files are written into the
 * app's cache directory, which is exposed to other apps through the
 * `FileProvider` configured in `filepaths.xml`.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryRepository: EntryRepository,
    private val settingsRepository: SettingsRepository,
    private val entryDao: EntryDao,
) {

    suspend fun generate(format: ExportFormat): ExportedFile = when (format) {
        ExportFormat.PDF -> generatePdf()
        ExportFormat.CSV -> generateCsv()
    }

    suspend fun generateCsv(): ExportedFile = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, CSV_FILE_NAME)
        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writeCsvHeader(writer)
            var offset = 0
            while (true) {
                val page = entryDao.pageSinceByTimestampForExport(
                    exportWindowStart(),
                    CSV_PAGE_SIZE,
                    offset,
                )
                if (page.isEmpty()) break
                page.forEach { writeCsvRow(writer, it) }
                offset += page.size
                if (page.size < CSV_PAGE_SIZE) break
            }
        }
        ExportedFile(file = file, mimeType = ExportFormat.CSV.mimeType)
    }

    suspend fun generatePdf(): ExportedFile = withContext(Dispatchers.IO) {
        val entries = entryRepository.entriesSince(exportWindowStart())
        val profile = settingsRepository.profileSnapshot()
        val file = File(context.cacheDir, PDF_FILE_NAME)
        writePdf(file, entries, profile)
        ExportedFile(file = file, mimeType = ExportFormat.PDF.mimeType)
    }

    // ------------------------------------------------------------------ CSV

    private fun writeCsvHeader(writer: Writer) {
        writer.write(
            "Id,Timestamp,Glucose,Basal,Bolus,Carbs,Protein,Fat,MealDescription," +
                "MealContext,ExerciseMinutes,ExerciseIntensity,Notes\n"
        )
    }

    private fun writeCsvRow(writer: Writer, entry: EntryEntity) {
        writer.append(entry.id.toString()).append(',')
        writer.append(formatTimestamp(entry.timestamp)).append(',')
        writer.append(formatDecimal(entry.glucoseMgdl)).append(',')
        writer.append(formatDecimal(entry.insulinBasalUnits)).append(',')
        writer.append(formatDecimal(entry.insulinBolusUnits)).append(',')
        writer.append(entry.carbsGrams?.toString() ?: "").append(',')
        writer.append(entry.proteinGrams?.toString() ?: "").append(',')
        writer.append(entry.fatGrams?.toString() ?: "").append(',')
        writer.append(entry.mealDescription?.let { escapeCsv(it) } ?: "").append(',')
        writer.append(entry.mealContext?.name ?: "").append(',')
        writer.append(entry.exerciseMinutes?.toString() ?: "").append(',')
        writer.append(entry.exerciseIntensity?.name ?: "").append(',')
        writer.append(entry.note?.takeIf { it.isNotBlank() }?.let { escapeCsv(it) } ?: "")
        writer.append('\n')
    }

    /** Standard escaping so notes containing commas/quotes/newlines stay one cell. */
    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // ------------------------------------------------------------------ PDF

    private fun writePdf(
        file: File,
        entries: List<LogEvent>,
        profile: UserProfile,
    ) {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val ea1c = estimatedA1c(entries)
            var index = 0
            var pageNumber = 0

            do {
                pageNumber++
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                var y = MARGIN
                if (pageNumber == 1) {
                    y = drawReportHeader(canvas, profile, ea1c, y)
                }
                y = drawTableHeader(canvas, y)

                val bottom = PAGE_HEIGHT - MARGIN
                while (index < entries.size && y + ROW_HEIGHT <= bottom) {
                    y = drawEntryRow(canvas, entries[index], y, index % 2 == 0)
                    index++
                }

                drawFooter(canvas, pageNumber)
                document.finishPage(page)
            } while (index < entries.size)

            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun drawReportHeader(
        canvas: Canvas,
        profile: UserProfile,
        ea1c: String,
        startY: Float,
    ): Float {
        var y = startY

        canvas.drawText("GlucoseHero Clinical Report", MARGIN, y + textBaseline(titlePaint), titlePaint)
        y += 34
        canvas.drawText("90-day glucose log export", MARGIN, y + textBaseline(subtitlePaint), subtitlePaint)
        y += 30

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
        y += 24

        val patientName = profile.name.trim().takeIf { it.isNotEmpty() }
        if (patientName != null) {
            canvas.drawText("Patient: $patientName", MARGIN, y + textBaseline(bodyPaint), bodyPaint)
            y += 20
        }

        val profileDetails = listOfNotNull(
            profile.diabetesType?.trim()?.takeIf { it.isNotEmpty() }?.let { "Type: $it" },
            profile.age?.let { "Age: $it" },
        ).joinToString("   ")
        if (profileDetails.isNotBlank()) {
            canvas.drawText(profileDetails, MARGIN, y + textBaseline(bodyPaint), bodyPaint)
            y += 20
        }

        canvas.drawText("Estimated A1c (eA1c): $ea1c", MARGIN, y + textBaseline(bodyPaint), bodyPaint)
        y += 20
        canvas.drawText(
            "Generated: ${formatTimestamp(System.currentTimeMillis())}",
            MARGIN,
            y + textBaseline(bodyPaint),
            bodyPaint,
        )
        y += 20
        canvas.drawText(
            "Glucose values are reported in mg/dL",
            MARGIN,
            y + textBaseline(captionPaint),
            captionPaint,
        )
        y += 26
        return y
    }

    private fun drawTableHeader(canvas: Canvas, yStart: Float): Float {
        fillPaint(color = TABLE_HEADER_BG).let { paint ->
            canvas.drawRect(MARGIN, yStart - 4, PAGE_WIDTH - MARGIN, yStart + ROW_HEIGHT, paint)
        }
        drawCell(canvas, "Timestamp", COL_TIMESTAMP, yStart, headerPaint)
        drawCell(canvas, "Glucose", COL_GLUCOSE, yStart, headerPaint, rightAlign = true)
        drawCell(canvas, "Basal", COL_BASAL, yStart, headerPaint, rightAlign = true)
        drawCell(canvas, "Bolus", COL_BOLUS, yStart, headerPaint, rightAlign = true)
        drawCell(canvas, "Carbs", COL_CARBS, yStart, headerPaint, rightAlign = true)
        drawCell(canvas, "Notes", COL_NOTES, yStart, headerPaint)
        return yStart + ROW_HEIGHT
    }

    private fun drawEntryRow(canvas: Canvas, entry: LogEvent, yStart: Float, shaded: Boolean): Float {
        if (shaded) {
            fillPaint(color = ROW_ALT_BG).let { paint ->
                canvas.drawRect(MARGIN, yStart, PAGE_WIDTH - MARGIN, yStart + ROW_HEIGHT, paint)
            }
        }
        drawCell(canvas, formatTimestamp(entry.timestamp), COL_TIMESTAMP, yStart, cellPaint)
        drawCell(canvas, formatDecimal(entry.glucoseMgdl), COL_GLUCOSE, yStart, cellPaint, rightAlign = true)
        drawCell(canvas, formatDecimal(entry.insulinBasalUnits), COL_BASAL, yStart, cellPaint, rightAlign = true)
        drawCell(canvas, formatDecimal(entry.insulinBolusUnits), COL_BOLUS, yStart, cellPaint, rightAlign = true)
        drawCell(canvas, entry.carbsGrams?.toString() ?: "", COL_CARBS, yStart, cellPaint, rightAlign = true)
        drawCell(canvas, entry.note.orEmpty(), COL_NOTES, yStart, cellPaint)
        return yStart + ROW_HEIGHT
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val text = "Page $pageNumber"
        val width = footerPaint.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - width) / 2f, PAGE_HEIGHT - 18f, footerPaint)
    }

    private fun drawCell(
        canvas: Canvas,
        rawText: String,
        x: Float,
        y: Float,
        paint: Paint,
        rightAlign: Boolean = false,
    ) {
        val cellWidth = when (x) {
            COL_TIMESTAMP -> COL_GLUCOSE - COL_TIMESTAMP
            COL_GLUCOSE -> COL_BASAL - COL_GLUCOSE
            COL_BASAL -> COL_BOLUS - COL_BASAL
            COL_BOLUS -> COL_CARBS - COL_BOLUS
            COL_CARBS -> COL_NOTES - COL_CARBS
            else -> PAGE_WIDTH - MARGIN - COL_NOTES
        }
        val availWidth = cellWidth - CELL_PADDING * 2
        val text = if (paint.measureText(rawText) <= availWidth) {
            rawText
        } else {
            val count = paint.breakText(rawText, true, availWidth, null)
            rawText.take(count.coerceAtLeast(0)) + "\u2026"
        }
        val measured = paint.measureText(text)
        val drawX = if (rightAlign) {
            x + cellWidth - CELL_PADDING - measured
        } else {
            x + CELL_PADDING
        }
        canvas.withClip(
            left = x,
            top = y,
            right = x + cellWidth,
            bottom = y + ROW_HEIGHT,
        ) {
            drawText(text, drawX, y + textBaseline(paint), paint)
        }
    }

    // ------------------------------------------------------------- helpers

    private fun estimatedA1c(entries: List<LogEvent>): String {
        val readings = entries.mapNotNull { it.glucoseMgdl?.takeIf { value -> value.isFinite() } }
        if (readings.isEmpty()) return "N/A"
        val a1c = (readings.average() + 46.7) / 28.7
        return String.format(Locale.US, "%.1f%%", a1c)
    }

    private fun formatDecimal(value: Double?): String =
        value?.let { value ->
            if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
            }
        } ?: ""

    private fun formatTimestamp(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)

    private fun textBaseline(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return (ROW_HEIGHT - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
    }

    private fun fillPaint(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

    private fun exportWindowStart(): Long = Formatters.daysAgoMillis(EXPORT_WINDOW_DAYS)

    private companion object {
        const val EXPORT_WINDOW_DAYS = 90
        const val CSV_PAGE_SIZE = 500
        const val CSV_FILE_NAME = "GlucoseHero_Report.csv"
        const val PDF_FILE_NAME = "GlucoseHero_Report.pdf"

        // A4 portrait in PDF points (1/72 inch).
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val ROW_HEIGHT = 24f
        const val CELL_PADDING = 4f

        // Column x-offsets within the content area.
        const val COL_TIMESTAMP = 40f
        const val COL_GLUCOSE = 150f
        const val COL_BASAL = 230f
        const val COL_BOLUS = 290f
        const val COL_CARBS = 350f
        const val COL_NOTES = 400f

        val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

        val TEXT_PRIMARY = Color.rgb(33, 33, 33)
        val TEXT_SECONDARY = Color.rgb(97, 97, 97)
        val ACCENT = Color.rgb(198, 40, 40)
        val TABLE_HEADER_BG = Color.rgb(244, 244, 246)
        val ROW_ALT_BG = Color.rgb(250, 250, 252)
        val DIVIDER = Color.rgb(224, 224, 224)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 10f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 10f
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 8f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 8.5f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 8f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIVIDER
            strokeWidth = 1f
        }
    }
}