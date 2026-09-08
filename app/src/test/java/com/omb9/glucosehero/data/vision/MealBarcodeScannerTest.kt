package com.omb9.glucosehero.data.vision

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MealBarcodeScannerTest {

    @Test
    fun `success result carries the scanned values`() {
        assertEquals(
            listOf("036000291452"),
            MealBarcodeScanResult.Success(listOf("036000291452")).values,
        )
    }

    @Test
    fun `canceled and unavailable are singletons`() {
        assertSame(MealBarcodeScanResult.Canceled, MealBarcodeScanResult.Canceled)
        assertSame(MealBarcodeScanResult.Unavailable, MealBarcodeScanResult.Unavailable)
    }

    @Test
    fun `companion factory defaults to the play services implementation`() {
        assertTrue(MealBarcodeScanner() is PlayServicesMealBarcodeScanner)
    }

    @Test
    fun `fake implementation satisfies the scanner contract`() {
        val result = MealBarcodeScanResult.Success(listOf("012345678905"))
        val fake = FakeMealBarcodeScanner(result)
        val scanner: MealBarcodeScanner = fake

        assertSame(fake, scanner)
        assertEquals(result, fake.result)
    }

    @Test
    fun `scanner is restricted to EAN and UPC formats and excludes QR codes`() {
        assertEquals(
            setOf(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            ),
            PlayServicesMealBarcodeScanner.BARCODE_FORMATS.toSet(),
        )
        assertFalse(Barcode.FORMAT_QR_CODE in PlayServicesMealBarcodeScanner.BARCODE_FORMATS)
    }

    private class FakeMealBarcodeScanner(
        val result: MealBarcodeScanResult,
    ) : MealBarcodeScanner {
        override suspend fun scanBarcodes(context: Context): MealBarcodeScanResult = result
    }
}
