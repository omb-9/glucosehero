package com.omb9.glucosehero.data.vision

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * In-memory-only image handling for the meal-photo nutrition feature.
 *
 * A captured [Bitmap] is compressed to JPEG bytes held entirely in a
 * [ByteArrayOutputStream], then Base64-encoded into a `data:` URI that can be
 * posted to Hero AI. No file is written and no image bytes touch the local
 * database — honouring the app's privacy promise.
 */
object MealPhotoCapture {

    private const val JPEG_QUALITY = 85

    /**
     * Encodes [bitmap] as a `data:image/jpeg;base64,…` URI without touching disk.
     * Returns null when compression produces no bytes.
     */
    fun toDataUri(bitmap: Bitmap): String? {
        val bytes = ByteArrayOutputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                return null
            }
            stream.toByteArray()
        }
        if (bytes.isEmpty()) return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
