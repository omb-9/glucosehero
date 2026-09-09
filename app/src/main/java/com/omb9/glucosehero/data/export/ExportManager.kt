package com.omb9.glucosehero.data.export

import android.content.Context
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.ExportWhitelist
import com.omb9.glucosehero.domain.model.ExportedFile
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * Generates shareable clinical export files from Room glucose history.
 *
 * PDF is a print-ready Ambulatory Glucose Profile drawn with the platform
 * [android.graphics.pdf.PdfDocument] Canvas API (no third-party PDF library).
 * CSV remains a 90-day log dump of [ExportWhitelist] entry columns only
 * (no settings, tokens, or Keystore material).
 *
 * All disk I/O is performed on [Dispatchers.IO]. Files are written into the
 * app's cache directory, which is exposed to other apps through the
 * `FileProvider` configured in `filepaths.xml`.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
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
        val now = System.currentTimeMillis()
        val since = now - AgpReportCalculator.WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val settings = settingsRepository.settings.first()
        val profile = settingsRepository.profileSnapshot()
        val points = entryDao.glucoseReadingPointsSince(since)
        val cgmCount = entryDao.cgmReadingCountSince(since)
        val manualCount = entryDao.manualReadingCountSince(since)
        val daily = entryDao.dailySummaries(since, limit = AgpReportCalculator.WINDOW_DAYS)
        val report = AgpReportCalculator.build(
            points = points,
            thresholds = AgpRangeThresholds(
                targetLowMgdl = settings.targetLowMgdl,
                targetHighMgdl = settings.targetHighMgdl,
            ),
            cgmReadingCount = cgmCount,
            manualReadingCount = manualCount,
            daily = daily,
            windowEndMillis = now,
        )
        val file = File(context.cacheDir, PDF_FILE_NAME)
        AgpPdfRenderer.write(file, report, profile)
        ExportedFile(file = file, mimeType = ExportFormat.PDF.mimeType)
    }

    // ------------------------------------------------------------------ CSV

    private fun writeCsvHeader(writer: Writer) {
        writer.write(ExportWhitelist.csvColumns.joinToString(",") + "\n")
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

    // ------------------------------------------------------------- helpers

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

    private fun exportWindowStart(): Long = Formatters.daysAgoMillis(EXPORT_WINDOW_DAYS)

    private companion object {
        const val EXPORT_WINDOW_DAYS = 90
        const val CSV_PAGE_SIZE = 500
        const val CSV_FILE_NAME = "GlucoseHero_Report.csv"
        const val PDF_FILE_NAME = "GlucoseHero_AGP.pdf"

        val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }
}