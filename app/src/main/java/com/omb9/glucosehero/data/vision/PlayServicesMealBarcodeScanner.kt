package com.omb9.glucosehero.data.vision

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [MealBarcodeScanner] backed by Google Play services' out-of-process code
 * scanner, restricted to EAN/UPC formats so packaging QR codes are ignored.
 */
class PlayServicesMealBarcodeScanner @Inject constructor() : MealBarcodeScanner {

    override suspend fun scanBarcodes(context: Context): MealBarcodeScanResult =
        suspendCancellableCoroutine { continuation ->
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    BARCODE_FORMATS[0],
                    *BARCODE_FORMATS.copyOfRange(1, BARCODE_FORMATS.size),
                )
                .enableAutoZoom()
                .build()

            val started = runCatching {
                GmsBarcodeScanning.getClient(context, options).startScan()
            }

            if (started.isSuccess) {
                val task = started.getOrThrow()

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
                task.addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        val canceledByUser =
                            (exception is MlKitException) &&
                            (exception.errorCode == MlKitException.CODE_SCANNER_CANCELLED)
                        continuation.resume(
                            if (canceledByUser) {
                                MealBarcodeScanResult.Canceled
                            } else {
                                MealBarcodeScanResult.Unavailable
                            },
                        )
                    }
                }
            } else if (continuation.isActive) {
                continuation.resume(MealBarcodeScanResult.Unavailable)
            }
        }

    companion object {
        /**
         * EAN/UPC only. Keeping this list explicit and testable guarantees the
         * scanner never latches onto a QR code elsewhere on the packaging.
         */
        val BARCODE_FORMATS = intArrayOf(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
    }
}
