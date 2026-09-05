package com.omb9.glucosehero.data.vision

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin adapter over Google Play services' out-of-process code scanner.
 *
 * The scanner runs in its own Google-provided activity, so this class owns no
 * camera code and the app needs no CAMERA permission. A future nutrition
 * lookup (e.g. Open Food Facts by UPC) can consume the returned barcode values
 * without touching the Compose layer.
 */
class MealBarcodeScanner {

    /**
     * Launches Google's code scanner and returns the first machine-readable
     * EAN/UPC barcode it recognizes.
     *
     * Restricting to EAN/UPC specifically stops the scanner latching onto an
     * unrelated QR code elsewhere on the packaging.
     */
    suspend fun scanBarcodes(context: Context): MealBarcodeScanResult =
        suspendCancellableCoroutine { continuation ->
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .enableAutoZoom()
                .build()

            val task = GmsBarcodeScanning.getClient(context, options).startScan()

            task.addOnSuccessListener { barcode ->
                if (continuation.isActive) {
                    continuation.resume(
                        MealBarcodeScanResult.Success(
                            listOfNotNull(barcode.rawValue?.takeIf { it.isNotBlank() }),
                        ),
                    )
                }
            }
            task.addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.resume(MealBarcodeScanResult.Canceled)
                }
            }
            task.addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume(MealBarcodeScanResult.Unavailable)
                }
            }
        }
}

/** Outcome of [MealBarcodeScanner.scanBarcodes]. */
sealed interface MealBarcodeScanResult {
    /** A recognized EAN/UPC barcode (or an empty list if [Barcode.rawValue] was blank). */
    data class Success(val values: List<String>) : MealBarcodeScanResult

    /** The user dismissed the scanner without scanning anything. */
    data object Canceled : MealBarcodeScanResult

    /** The Play services code-scanner module is unavailable or failed to start. */
    data object Unavailable : MealBarcodeScanResult
}
