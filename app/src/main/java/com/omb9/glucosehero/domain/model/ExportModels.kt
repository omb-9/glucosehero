package com.omb9.glucosehero.domain.model

import java.io.File

/** Clinical export formats offered by the share sheet. */
enum class ExportFormat(val displayName: String, val mimeType: String) {
    PDF("Export as PDF", "application/pdf"),
    CSV("Export as CSV", "text/csv"),
}

/**
 * A generated report sitting in the app's cache directory, ready to be turned
 * into a `content://` URI by [androidx.core.content.FileProvider].
 */
data class ExportedFile(
    val file: File,
    val mimeType: String,
)
