package com.omb9.glucosehero.data.vision

import android.content.Context

/**
 * Contract for scanning physical meal product barcodes.
 *
 * The scanner interface provides an abstraction over barcode detection,
 * allowing callers (e.g. Compose UI, ViewModels) to remain completely agnostic
 * of the underlying implementation (Google Play services code scanner, ML Kit,
 * or test doubles).
 */
interface MealBarcodeScanner {

    /**
     * Launches the code scanner and returns the first recognized machine-readable
     * barcode or terminal outcome.
     */
    suspend fun scanBarcodes(context: Context): MealBarcodeScanResult

    companion object {
        /**
         * Creates the default [MealBarcodeScanner] implementation backed by
         * [PlayServicesMealBarcodeScanner].
         */
        operator fun invoke(): MealBarcodeScanner = PlayServicesMealBarcodeScanner()
    }
}

/** Outcome of [MealBarcodeScanner.scanBarcodes]. */
sealed interface MealBarcodeScanResult {
    /** A recognized EAN/UPC barcode (or an empty list if rawValue was blank). */
    data class Success(val values: List<String>) : MealBarcodeScanResult

    /** The user dismissed the scanner without scanning anything. */
    data object Canceled : MealBarcodeScanResult

    /** The Play services code-scanner module is unavailable or failed to start. */
    data object Unavailable : MealBarcodeScanResult
}
