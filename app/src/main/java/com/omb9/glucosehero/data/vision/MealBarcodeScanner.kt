package com.omb9.glucosehero.data.vision

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Thin adapter over Google ML Kit's barcode scanning Vision API.
 *
 * This is deliberately the only class that talks to ML Kit so a future
 * nutrition-lookup call (e.g. Open Food Facts by UPC) can replace or augment
 * the returned barcode values without touching the Compose layer.
 */
class MealBarcodeScanner {

    /**
     * Scans [uri] and returns the raw, non-blank barcode values found.
     *
     * Runs on [Dispatchers.IO] because ML Kit's [com.google.mlkit.vision.barcode.BarcodeScanner]
     * performs image decoding + inference off the main thread and exposes its
     * result through a blocking-capable Task.
     */
    suspend fun scanBarcodes(context: Context, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            val scanner = BarcodeScanning.getClient()
            try {
                val image = InputImage.fromFilePath(context, uri)
                scanner.process(image)
                    .await()
                    .mapNotNull { barcode ->
                        barcode.rawValue?.takeIf { it.isNotBlank() }
                    }
            } catch (_: Exception) {
                // A photo without a machine-readable code is expected; treat it
                // the same as an unreadable image so the UI can show a gentle
                // "no barcode detected" state rather than crashing the sheet.
                emptyList()
            } finally {
                scanner.close()
            }
        }
}
