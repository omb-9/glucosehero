package com.omb9.glucosehero.ui.insights

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

private const val SHARE_FILE_NAME = "tag-impact-share.png"
private const val SHARE_CHOOSER_TITLE = "Share tag insight"
private const val SHARE_FAILED_MESSAGE = "Couldn't share this card"
internal const val TAG_IMPACT_WATERMARK = "GlucoseHero"

/**
 * Off-screen Compose capture host. When [item] is set, the tag card is recorded
 * into a [androidx.compose.ui.graphics.layer.GraphicsLayer], encoded as PNG,
 * and handed to [Intent.ACTION_SEND] via the app's cache FileProvider.
 *
 * The bitmap contains only the tag insight (aggregate stats already on the
 * card) plus the GlucoseHero watermark. No profile, email, dates of birth,
 * chat, API keys, or raw glucose timelines are composed.
 */
@Composable
fun TagImpactShareCapture(
    item: TagImpactUi?,
    maxAbsDelta: Double,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(item) {
        if (item == null) return@LaunchedEffect
        var captured: Bitmap? = null
        var frames = 0
        while (captured == null && frames < 8) {
            withFrameNanos { }
            frames++
            captured = runCatching {
                val image = graphicsLayer.toImageBitmap()
                if (image.width <= 1 || image.height <= 1) return@runCatching null
                image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
        }
        val bitmap = captured
        if (bitmap == null) {
            Toast.makeText(context, SHARE_FAILED_MESSAGE, Toast.LENGTH_SHORT).show()
        } else {
            try {
                shareTagImpactBitmap(context, bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, SHARE_FAILED_MESSAGE, Toast.LENGTH_SHORT).show()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        onFinished()
    }

    if (item == null) return

    Box(
        modifier = modifier
            .offset(x = (-10_000).dp)
            .width(360.dp)
            .clearAndSetSemantics { }
            .drawWithContent {
                if (size.width < 2f || size.height < 2f) return@drawWithContent
                graphicsLayer.record(
                    size = IntSize(size.width.toInt(), size.height.toInt()),
                ) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        TagImpactShareTheme {
            TagImpactShareRenderable(
                item = item,
                maxAbsDelta = maxAbsDelta,
            )
        }
    }
}

/**
 * Writes [bitmap] to cache and launches the system share sheet. Filename is
 * fixed (no user identifiers). URI is served through FileProvider, matching
 * clinical export sharing on the Stats screen.
 */
internal suspend fun shareTagImpactBitmap(context: Context, bitmap: Bitmap) {
    val appContext = context.applicationContext
    val file = withContext(Dispatchers.IO) {
        val outFile = File(appContext.cacheDir, SHARE_FILE_NAME)
        FileOutputStream(outFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Failed to compress tag impact bitmap"
            }
        }
        outFile
    }
    val uri = FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(SHARE_CHOOSER_TITLE, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, SHARE_CHOOSER_TITLE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

@Composable
internal fun TagImpactShareTheme(content: @Composable () -> Unit) {
    val colors = tagImpactShareColors()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.container,
            primaryContainer = colors.accent.copy(alpha = 0.14f),
            onPrimaryContainer = colors.accent,
            secondary = colors.accent,
            onSecondary = colors.container,
            background = colors.container,
            onBackground = colors.onSurface,
            surface = colors.container,
            onSurface = colors.onSurface,
            surfaceVariant = colors.container,
            onSurfaceVariant = colors.onSurfaceVariant,
            surfaceContainer = colors.container,
            surfaceContainerLow = colors.container,
            surfaceContainerHigh = colors.container,
            surfaceContainerHighest = colors.container,
            outline = colors.outline,
            outlineVariant = colors.outline,
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

/**
 * Share poster: insight card (no share/dismiss chrome) on a true-black canvas
 * with the app mark and name. Aggregate tag stats only.
 */
@Composable
internal fun TagImpactShareRenderable(
    item: TagImpactUi,
    maxAbsDelta: Double,
    modifier: Modifier = Modifier,
) {
    val colors = tagImpactShareColors()
    Column(
        modifier = modifier
            .background(colors.container)
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
    ) {
        TagImpactCard(
            item = item,
            maxAbsDelta = maxAbsDelta,
            onShare = null,
            onDismiss = null,
            colors = colors,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_glucosehero),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = TAG_IMPACT_WATERMARK,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TagImpactShareRenderablePreview() {
    TagImpactShareTheme {
        TagImpactShareRenderable(
            item = TagImpactUi(
                tag = "#pizza",
                kind = TagKind.HASHTAG,
                occurrences = 11,
                medianDeltaMgdl = 85.0,
                p25DeltaMgdl = 40.0,
                p75DeltaMgdl = 120.0,
                avgCarbsGrams = 62.0,
                avgBolusUnits = 4.2,
                unit = GlucoseUnit.MGDL,
            ),
            maxAbsDelta = 85.0,
        )
    }
}
