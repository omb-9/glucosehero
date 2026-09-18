package com.omb9.glucosehero.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.omb9.glucosehero.ui.theme.Spacing

/**
 * Hero markdown styled from [MaterialTheme] tokens so Light, AMOLED, System,
 * and every accent stay readable. Tables are width-capped to the parent so
 * glucose summary tables scroll horizontally instead of stretching the page.
 */
@Composable
fun ChatMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val colors = markdownColor(
        text = scheme.onSurface,
        codeBackground = scheme.surfaceContainerHigh,
        inlineCodeBackground = scheme.surfaceContainerHigh,
        dividerColor = scheme.outlineVariant,
        tableBackground = scheme.surfaceContainer,
    )
    val mdTypography = markdownTypography(
        h1 = typography.titleLarge,
        h2 = typography.titleMedium,
        h3 = typography.titleMedium,
        h4 = typography.titleMedium,
        h5 = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        h6 = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        text = typography.bodyLarge.copy(color = scheme.onSurface),
        code = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        inlineCode = typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        quote = typography.bodyLarge.copy(color = scheme.onSurfaceVariant),
        paragraph = typography.bodyLarge.copy(color = scheme.onSurface),
        ordered = typography.bodyLarge.copy(color = scheme.onSurface),
        bullet = typography.bodyLarge.copy(color = scheme.onSurface),
        list = typography.bodyLarge.copy(color = scheme.onSurface),
        table = typography.bodyMedium.copy(color = scheme.onSurface),
        textLink = androidx.compose.ui.text.TextLinkStyles(
            style = typography.bodyLarge.copy(
                color = scheme.primary,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ).toSpanStyle(),
        ),
    )
    val components = markdownComponents(
        codeFence = {
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(Spacing.sm)) {
                    MarkdownCodeFence(it.content, it.node, style = it.typography.code)
                }
            }
        },
        codeBlock = {
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(Spacing.sm)) {
                    MarkdownCodeBlock(it.content, it.node, style = it.typography.code)
                }
            }
        },
        table = {
            Box(Modifier.fillMaxWidth()) {
                MarkdownTable(it.content, it.node, style = it.typography.table)
            }
        },
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Markdown(
            content = content,
            colors = colors,
            typography = mdTypography,
            dimens = markdownDimens(tableMaxWidth = maxWidth),
            components = components,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
