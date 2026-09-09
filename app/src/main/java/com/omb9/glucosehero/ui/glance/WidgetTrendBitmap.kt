package com.omb9.glucosehero.ui.glance

import com.omb9.glucosehero.domain.model.GlucosePointRow
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Caps Glance / RemoteViews payload so a home-screen widget cannot trip
 * Binder's ~1 MB [android.os.TransactionTooLargeException].
 *
 * The mini-trend is a **fixed-size** compressed PNG (not a hi-res canvas and
 * not a Glance list of readings). Callers must pass the PNG (or a Bitmap
 * decoded from it), never the raw reading list, into the Glance composition.
 */
object WidgetTrendBitmap {
    /** Last hour of 5-minute CGM is 12 points; never send more than this. */
    const val MAX_TREND_POINTS: Int = 12

    /** Trend window matching [MAX_TREND_POINTS] at typical CGM cadence. */
    const val TREND_WINDOW_MS: Long = 60L * 60L * 1000L

    /** Encoded sparkline width in pixels, fixed before the Glance update. */
    const val WIDTH_PX: Int = 120

    /** Encoded sparkline height in pixels, fixed before the Glance update. */
    const val HEIGHT_PX: Int = 36

    /** Compressed PNG budget. Uncompressed ARGB would be 17 KB; stay smaller. */
    const val MAX_PNG_BYTES: Int = 8 * 1024

    /**
     * Conservative cap on text + PNG + decoded bitmap bytes placed on the
     * binder transaction. Far below the 1 MB system limit.
     */
    const val MAX_PAYLOAD_BYTES: Int = 64 * 1024

    private const val BINDER_LIMIT_BYTES: Int = 1 * 1024 * 1024
    private const val LINE_R: Int = 0xFF
    private const val LINE_G: Int = 0x52
    private const val LINE_B: Int = 0x52

    fun limitReadings(
        points: List<GlucosePointRow>,
        nowMillis: Long,
        windowMs: Long = TREND_WINDOW_MS,
        maxPoints: Int = MAX_TREND_POINTS,
    ): List<GlucosePointRow> {
        val start = nowMillis - windowMs
        return points
            .asSequence()
            .filter { it.timestamp in start..nowMillis && it.glucoseMgdl.isFinite() }
            .sortedBy { it.timestamp }
            .toList()
            .takeLast(maxPoints.coerceAtLeast(0))
    }

    /**
     * Worst-case binder payload: UTF-16 widget strings, the compressed PNG,
     * and an uncompressed ARGB copy Glance may parcel alongside it.
     */
    fun estimateBinderBytes(pngBytes: Int, extraTextChars: Int = 160): Int {
        val uncompressedArgb = WIDTH_PX * HEIGHT_PX * 4
        return pngBytes + uncompressedArgb + extraTextChars * 2 + 1024
    }

    fun isWithinBinderBudget(pngBytes: Int, extraTextChars: Int = 160): Boolean {
        val estimated = estimateBinderBytes(pngBytes, extraTextChars)
        return pngBytes <= MAX_PNG_BYTES &&
            estimated <= MAX_PAYLOAD_BYTES &&
            estimated < BINDER_LIMIT_BYTES
    }

    /**
     * Renders [points] (already limited) into a fixed [WIDTH_PX]×[HEIGHT_PX]
     * RGB PNG. Returns an empty array when there is nothing to draw or when
     * the compressed result would exceed [MAX_PNG_BYTES].
     */
    fun encodePng(points: List<GlucosePointRow>): ByteArray {
        if (points.size < 2) return ByteArray(0)
        val rgb = renderSparklineRgb(points)
        val png = encodeRgbPng(WIDTH_PX, HEIGHT_PX, rgb)
        return if (png.size <= MAX_PNG_BYTES) png else ByteArray(0)
    }

    internal fun pngWidth(png: ByteArray): Int = readPngInt(png, 16)

    internal fun pngHeight(png: ByteArray): Int = readPngInt(png, 20)

    private fun readPngInt(png: ByteArray, offset: Int): Int {
        require(png.size >= offset + 4) { "PNG is too short to read IHDR." }
        return (png[offset].toInt() and 0xFF shl 24) or
            (png[offset + 1].toInt() and 0xFF shl 16) or
            (png[offset + 2].toInt() and 0xFF shl 8) or
            (png[offset + 3].toInt() and 0xFF)
    }

    private fun renderSparklineRgb(points: List<GlucosePointRow>): ByteArray {
        val rgb = ByteArray(WIDTH_PX * HEIGHT_PX * 3)
        val xs = IntArray(points.size)
        val ys = IntArray(points.size)
        val minG = points.minOf { it.glucoseMgdl }
        val maxG = points.maxOf { it.glucoseMgdl }
        val span = (maxG - minG).let { if (it < 1.0) 1.0 else it }
        val minT = points.first().timestamp.toDouble()
        val maxT = points.last().timestamp.toDouble()
        val tSpan = (maxT - minT).let { if (it < 1.0) 1.0 else it }
        val padY = 2
        val usableH = (HEIGHT_PX - 1 - padY * 2).coerceAtLeast(1)
        for (i in points.indices) {
            val nx = ((points[i].timestamp - minT) / tSpan).coerceIn(0.0, 1.0)
            val ny = ((points[i].glucoseMgdl - minG) / span).coerceIn(0.0, 1.0)
            xs[i] = (nx * (WIDTH_PX - 1)).toInt().coerceIn(0, WIDTH_PX - 1)
            ys[i] = (HEIGHT_PX - 1 - padY - (ny * usableH)).toInt()
                .coerceIn(0, HEIGHT_PX - 1)
        }
        for (i in 0 until points.lastIndex) {
            drawLine(rgb, xs[i], ys[i], xs[i + 1], ys[i + 1])
        }
        return rgb
    }

    private fun drawLine(rgb: ByteArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            stamp(rgb, x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun stamp(rgb: ByteArray, x: Int, y: Int) {
        put(rgb, x, y)
        put(rgb, x + 1, y)
        put(rgb, x, y + 1)
    }

    private fun put(rgb: ByteArray, x: Int, y: Int) {
        if (x !in 0 until WIDTH_PX || y !in 0 until HEIGHT_PX) return
        val i = (y * WIDTH_PX + x) * 3
        rgb[i] = LINE_R.toByte()
        rgb[i + 1] = LINE_G.toByte()
        rgb[i + 2] = LINE_B.toByte()
    }

    private fun encodeRgbPng(width: Int, height: Int, rgb: ByteArray): ByteArray {
        val stride = width * 3
        val raw = ByteArray((stride + 1) * height)
        for (y in 0 until height) {
            val dest = y * (stride + 1)
            raw[dest] = 0
            System.arraycopy(rgb, y * stride, raw, dest + 1, stride)
        }
        val deflated = deflate(raw)
        val out = ByteArrayOutputStream(deflated.size + 80)
        out.write(PNG_SIGNATURE)
        writeChunk(out, "IHDR", ihdr(width, height))
        writeChunk(out, "IDAT", deflated)
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun ihdr(width: Int, height: Int): ByteArray {
        val data = ByteArray(13)
        writeInt(data, 0, width)
        writeInt(data, 4, height)
        data[8] = 8
        data[9] = 2
        return data
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeIntTo(out, data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeIntTo(out, crc.value.toInt())
    }

    private fun writeInt(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = (value ushr 24).toByte()
        dest[offset + 1] = (value ushr 16).toByte()
        dest[offset + 2] = (value ushr 8).toByte()
        dest[offset + 3] = value.toByte()
    }

    private fun writeIntTo(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buf = ByteArray(512)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private val PNG_SIGNATURE: ByteArray =
        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
}
