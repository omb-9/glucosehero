package com.omb9.glucosehero.data.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.EntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Human-readable Markdown log export for local knowledge tools (Obsidian and
 * similar).
 *
 * The interactive export writes one Markdown file per month into a
 * user-selected document tree, while the scheduled daily export writes a
 * single day into the app's local document directory. Every file opens with
 * YAML front matter summarising that period, followed by a table of entries.
 * Output file names are strictly lowercase kebab-case
 * (e.g. `glucose-log-2026-09.md`). Paging keeps a CGM-sized history from
 * being accumulated in memory all at once. Only log-entry fields are written;
 * API keys and other secrets never appear.
 */
@Singleton
class MarkdownExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    database: GlucoseHeroDatabase,
) {
    private val entryDao = database.entryDao()

    suspend fun exportToTree(treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val written = mutableListOf<Uri>()
        var currentMonth: String? = null
        var monthEntries = ArrayList<EntryEntity>()
        var offset = 0

        while (true) {
            val page = entryDao.pageByTimestampForExport(PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (entry in page) {
                val month = monthKey(entry.timestamp)
                if (currentMonth == null) currentMonth = month
                if (month != currentMonth) {
                    written += writeMonthFile(treeUri, currentMonth, monthEntries)
                    currentMonth = month
                    monthEntries = ArrayList()
                }
                monthEntries.add(entry)
            }

            offset += page.size
            if (page.size < PAGE_SIZE) break
        }

        if (currentMonth != null && monthEntries.isNotEmpty()) {
            written += writeMonthFile(treeUri, currentMonth, monthEntries)
        }
        written
    }

    /**
     * Writes one day of entries as a single Markdown file into the app's
     * local document directory. The file name is lowercase kebab-case, e.g.
     * `glucose-log-2026-09-07.md`. Intended for the scheduled daily export.
     */
    suspend fun exportDayToLocalStorage(day: LocalDate): File = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = readEntriesBetween(startMillis, endMillis)

        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        documentsDir.mkdirs()
        val file = File(documentsDir, markdownVaultFileName(day.toString()))

        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writeMarkdownDocument(writer, periodKey = "date", periodValue = day.toString(), entries)
        }
        file
    }

    private fun writeMonthFile(treeUri: Uri, month: String, entries: List<EntryEntity>): Uri {
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            treeUri,
            "text/markdown",
            markdownVaultFileName(month),
        ) ?: throw BackupFormatException("Couldn't create the Markdown file for $month.")

        val output = context.contentResolver.openOutputStream(documentUri)
            ?: throw BackupFormatException("Couldn't open $documentUri for writing.")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeMarkdownDocument(writer, periodKey = "month", periodValue = month, entries)
        }
        return documentUri
    }

    private suspend fun readEntriesBetween(startMillis: Long, endMillis: Long): List<EntryEntity> {
        val entries = ArrayList<EntryEntity>()
        var offset = 0
        while (true) {
            val page = entryDao.pageSinceByTimestampForExport(startMillis, PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (entry in page) {
                if (entry.timestamp >= endMillis) return entries
                entries.add(entry)
            }

            offset += page.size
            if (page.size < PAGE_SIZE) break
        }
        return entries
    }

    private fun monthKey(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MONTH_FORMATTER)

    private companion object {
        const val PAGE_SIZE = 500

        val MONTH_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
    }
}

private const val MARKDOWN_FILE_PREFIX = "glucose-log"

private val MARKDOWN_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private val MARKDOWN_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

/**
 * Strict kebab-case vault file name: lowercase letters, digits, and hyphens
 * only. [dayOrMonth] must already be `yyyy-MM` or `yyyy-MM-dd`.
 */
internal fun markdownVaultFileName(dayOrMonth: String): String = "$MARKDOWN_FILE_PREFIX-$dayOrMonth.md"

internal fun renderMarkdownVaultMonth(month: String, entries: List<EntryEntity>): String {
    val writer = java.io.StringWriter()
    writeMarkdownDocument(writer, periodKey = "month", periodValue = month, entries)
    return writer.toString()
}

internal fun writeMarkdownDocument(
    writer: Writer,
    periodKey: String,
    periodValue: String,
    entries: List<EntryEntity>,
) {
    writeVaultFrontMatter(writer, periodKey, periodValue, entries)
    writeVaultTable(writer, entries)
}

internal fun writeVaultFrontMatter(
    writer: Writer,
    periodKey: String,
    periodValue: String,
    entries: List<EntryEntity>,
) {
    val glucose = entries.mapNotNull { it.glucoseMgdl }
    val carbs = entries.mapNotNull { it.carbsGrams?.toDouble() }
    val bolus = entries.mapNotNull { it.insulinBolusUnits }
    val timestamps = entries.map { it.timestamp }

    writer.write("---\n")
    writer.write("$periodKey: $periodValue\n")
    writer.write("entries: ${entries.size}\n")
    writer.write("earliestEntry: ${timestamps.minOrNull()?.let(::formatVaultDate) ?: ""}\n")
    writer.write("latestEntry: ${timestamps.maxOrNull()?.let(::formatVaultDate) ?: ""}\n")
    writer.write("averageGlucoseMgdl: ${formatVaultDecimal(glucose.takeIf { it.isNotEmpty() }?.average())}\n")
    writer.write("averageCarbsGrams: ${formatVaultDecimal(carbs.takeIf { it.isNotEmpty() }?.average())}\n")
    writer.write("averageBolusUnits: ${formatVaultDecimal(bolus.takeIf { it.isNotEmpty() }?.average())}\n")
    writer.write("minGlucoseMgdl: ${formatVaultDecimal(glucose.minOrNull())}\n")
    writer.write("maxGlucoseMgdl: ${formatVaultDecimal(glucose.maxOrNull())}\n")
    writer.write("---\n\n")
}

private fun writeVaultTable(writer: Writer, entries: List<EntryEntity>) {
    writer.write(
        "| Timestamp | Glucose (mg/dL) | Basal | Bolus | Carbs | Protein | Fat | " +
            "Meal | Context | Exercise | Intensity | Notes |\n",
    )
    writer.write(
        "|---|---|---|---|---|---|---|---|---|---|---|---|\n"
    )

    entries.forEach { entry ->
        writer.write(
            "| ${formatVaultTimestamp(entry.timestamp)} " +
                "| ${formatVaultDecimal(entry.glucoseMgdl)} " +
                "| ${formatVaultDecimal(entry.insulinBasalUnits)} " +
                "| ${formatVaultDecimal(entry.insulinBolusUnits)} " +
                "| ${entry.carbsGrams?.toString() ?: ""} " +
                "| ${entry.proteinGrams?.toString() ?: ""} " +
                "| ${entry.fatGrams?.toString() ?: ""} " +
                "| ${sanitizeVaultCell(entry.mealDescription)} " +
                "| ${entry.mealContext?.name ?: ""} " +
                "| ${entry.exerciseMinutes?.toString() ?: ""} " +
                "| ${entry.exerciseIntensity?.name ?: ""} " +
                "| ${sanitizeVaultCell(entry.note)} |\n"
        )
    }
    writer.write("\n")
}

private fun sanitizeVaultCell(value: String?): String =
    value.orEmpty().replace("|", "\\|").replace("\n", " ").replace("\r", " ")

private fun formatVaultDecimal(value: Double?): String =
    value?.let { v ->
        if (v % 1.0 == 0.0) v.toLong().toString()
        else "%.2f".format(v).trimEnd('0').trimEnd('.')
    } ?: ""

private fun formatVaultTimestamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MARKDOWN_TIMESTAMP_FORMATTER)

private fun formatVaultDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MARKDOWN_DATE_FORMATTER)
