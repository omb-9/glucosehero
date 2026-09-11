package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.DosingBounds
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.DosingProfileValidation
import com.omb9.glucosehero.domain.model.DosingSegment
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Editor for a 24-hour ISF/CIR/target schedule. DIA stays a separate global
 * control. Invalid drafts are shown with inline errors and are never saved.
 *
 * FEATURE: dosing-profiles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DosingProfileEditor(
    load: DosingProfileLoad,
    use24HourTime: Boolean,
    onSave: (DosingProfile) -> Unit,
    explainerSeen: Boolean,
    onExplainerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stored = when (load) {
        is DosingProfileLoad.Valid -> load.profile
        is DosingProfileLoad.Invalid -> load.editorDraft
            ?: DosingProfile.single(BolusSettings(diaHours = load.diaHours))
    }
    var draft by remember(stored.diaHours, stored.segments) { mutableStateOf(stored) }
    val validation = draft.validate()
    val issues = when (validation) {
        is DosingProfileValidation.Invalid -> validation.issues
        is DosingProfileValidation.Valid -> emptyList()
    }

    LaunchedEffect(draft) {
        when (val v = draft.validate()) {
            is DosingProfileValidation.Valid -> onSave(v.profile)
            is DosingProfileValidation.Invalid -> Unit
        }
    }

    if (!explainerSeen) {
        AlertDialog(
            onDismissRequest = onExplainerDismiss,
            title = { Text(stringResource(R.string.dosing_profile_explainer_title)) },
            text = { Text(stringResource(R.string.dosing_profile_explainer_body)) },
            confirmButton = {
                TextButton(onClick = onExplainerDismiss) {
                    Text(stringResource(R.string.dosing_profile_explainer_confirm))
                }
            },
        )
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.dosing_profile_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dosing_profile_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (load is DosingProfileLoad.Invalid) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dosing_profile_invalid_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        DosingProfileChart(profile = draft)
        Spacer(Modifier.height(12.dp))
        draft.segments.forEachIndexed { index, segment ->
            SegmentRow(
                index = index,
                segment = segment,
                canDelete = index > 0,
                use24HourTime = use24HourTime,
                onChange = { updated ->
                    val next = draft.segments.toMutableList()
                    next[index] = updated
                    draft = draft.copy(segments = next.sortedBy { it.start })
                },
                onDelete = {
                    if (index == 0) return@SegmentRow
                    draft = draft.copy(segments = draft.segments.filterIndexed { i, _ -> i != index })
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                val last = draft.segments.lastOrNull() ?: return@Button
                val start = nextAvailableStart(draft.segments)
                draft = draft.copy(
                    segments = (draft.segments + last.copy(start = start))
                        .sortedBy { it.start },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.dosing_profile_add_segment))
        }
        if (issues.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dosing_profile_save_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            issues.forEach { issue ->
                Text(
                    text = dosingProfileIssueText(issue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentRow(
    index: Int,
    segment: DosingSegment,
    canDelete: Boolean,
    use24HourTime: Boolean,
    onChange: (DosingSegment) -> Unit,
    onDelete: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var isfText by remember(segment.isfMgdl) { mutableStateOf(formatNumber(segment.isfMgdl)) }
    var cirText by remember(segment.cirRatio) { mutableStateOf(formatNumber(segment.cirRatio)) }
    var targetText by remember(segment.targetGlucoseMgdl) {
        mutableStateOf(formatNumber(segment.targetGlucoseMgdl))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (index > 0) showPicker = true },
                    enabled = index > 0,
                ) {
                    Text(
                        text = stringResource(R.string.dosing_profile_start_time) +
                            " " + formatStart(segment.start, use24HourTime),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(
                            if (canDelete) {
                                R.string.dosing_profile_delete_segment
                            } else {
                                R.string.dosing_profile_midnight_locked
                            },
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = isfText,
                    onValueChange = { text ->
                        isfText = text
                        text.toFloatOrNull()?.let { onChange(segment.copy(isfMgdl = it)) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.dosing_profile_isf)) },
                    suffix = { Text(stringResource(R.string.dosing_profile_isf_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cirText,
                    onValueChange = { text ->
                        cirText = text
                        text.toFloatOrNull()?.let { onChange(segment.copy(cirRatio = it)) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.dosing_profile_cir)) },
                    suffix = { Text(stringResource(R.string.dosing_profile_cir_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = targetText,
                onValueChange = { text ->
                    targetText = text
                    text.toFloatOrNull()?.let { onChange(segment.copy(targetGlucoseMgdl = it)) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.dosing_profile_target)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
    }
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = segment.start.hour,
            initialMinute = segment.start.minute,
            is24Hour = use24HourTime,
        )
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.dosing_profile_pick_time))
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = state)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPicker = false }) {
                            Text(stringResource(R.string.dosing_profile_cancel))
                        }
                        TextButton(
                            onClick = {
                                onChange(
                                    segment.copy(start = LocalTime.of(state.hour, state.minute)),
                                )
                                showPicker = false
                            },
                        ) {
                            Text(stringResource(R.string.dosing_profile_confirm_time))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DosingProfileChart(profile: DosingProfile) {
    val isfColor = MaterialTheme.colorScheme.primary
    val cirColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Text(
            text = stringResource(R.string.dosing_profile_chart_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val w = size.width
            val h = size.height
            drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
            if (profile.segments.isEmpty()) return@Canvas
            drawStep(profile, isfColor, DosingBounds.MIN_ISF_MGDL, DosingBounds.MAX_ISF_MGDL) {
                it.isfMgdl
            }
            drawStep(profile, cirColor, DosingBounds.MIN_CIR_RATIO, DosingBounds.MAX_CIR_RATIO) {
                it.cirRatio
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.dosing_profile_chart_isf),
                style = MaterialTheme.typography.labelSmall,
                color = isfColor,
            )
            Text(
                text = stringResource(R.string.dosing_profile_chart_cir),
                style = MaterialTheme.typography.labelSmall,
                color = cirColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStep(
    profile: DosingProfile,
    color: Color,
    min: Float,
    max: Float,
    value: (DosingSegment) -> Float,
) {
    val nanosDay = 24L * 60 * 60 * 1_000_000_000L
    val path = Path()
    val segs = profile.segments
    fun xOf(t: LocalTime) = (t.toNanoOfDay().toFloat() / nanosDay) * size.width
    fun yOf(v: Float): Float {
        val n = ((v - min) / (max - min)).coerceIn(0f, 1f)
        return size.height * (1f - n)
    }
    var first = true
    segs.forEachIndexed { i, seg ->
        val x0 = xOf(seg.start)
        val x1 = if (i + 1 < segs.size) xOf(segs[i + 1].start) else size.width
        val y = yOf(value(seg))
        if (first) {
            path.moveTo(x0, y)
            first = false
        } else {
            path.lineTo(x0, y)
        }
        path.lineTo(x1, y)
    }
    drawPath(path, color, style = Stroke(width = 4f))
}

private fun nextAvailableStart(segments: List<DosingSegment>): LocalTime {
    val used = segments.map { it.start }.toSet()
    var hour = 6
    while (hour < 24) {
        val candidate = LocalTime.of(hour, 0)
        if (candidate !in used) return candidate
        hour += 1
    }
    return LocalTime.of(23, 30)
}

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

private fun formatStart(time: LocalTime, use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
