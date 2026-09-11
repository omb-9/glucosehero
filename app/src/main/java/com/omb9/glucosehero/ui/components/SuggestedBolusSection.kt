package com.omb9.glucosehero.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.DosingProfileIssue
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.ui.settings.dosingProfileIssueText
import com.omb9.glucosehero.util.BolusDisplayBreakdown
import com.omb9.glucosehero.util.BolusDisplayFormatter
import com.omb9.glucosehero.util.BolusRecommendation
import com.omb9.glucosehero.util.WhyThisNumberCopy

/**
 * Smart Bolus headline plus a collapsed "Why this number" block.
 *
 * Arithmetic comes from [BolusRecommendation.Ready.breakdown]; this
 * composable only formats and renders those terms.
 */
@Composable
fun SuggestedBolusSection(
    recommendation: BolusRecommendation,
    issues: List<DosingProfileIssue>,
    unit: GlucoseUnit,
    use24HourTime: Boolean,
    onUseSuggestion: () -> Unit,
    onOpenDosingProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refusedIssues = when (recommendation) {
        is BolusRecommendation.Refused -> recommendation.issues
        else -> issues
    }
    if (refusedIssues.isNotEmpty() || recommendation is BolusRecommendation.Refused) {
        BolusRefusedExplanation(
            issues = refusedIssues,
            onOpenDosingProfile = onOpenDosingProfile,
            modifier = modifier,
        )
        return
    }
    val ready = recommendation as? BolusRecommendation.Ready ?: return
    val display = BolusDisplayFormatter.format(
        breakdown = ready.breakdown,
        unit = unit,
        segmentStart = ready.segment.start,
        segmentEnd = ready.segmentEnd,
        use24HourTime = use24HourTime,
    )
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.suggested_bolus, display.totalText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUseSuggestion) {
                Text(stringResource(R.string.use_suggestion))
            }
        }
        WhyThisNumberToggle(expanded = expanded, onToggle = { expanded = !expanded })
        AnimatedVisibility(visible = expanded) {
            BolusWhyThisNumberBody(
                display = display,
                onOpenDosingProfile = onOpenDosingProfile,
            )
        }
    }
}

@Composable
internal fun WhyThisNumberToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = stringResource(
        if (expanded) R.string.why_this_number_hide else R.string.why_this_number_show,
    )
    TextButton(
        onClick = onToggle,
        modifier = modifier.semantics { contentDescription = action },
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Text(stringResource(R.string.why_this_number))
    }
}

@Composable
private fun BolusRefusedExplanation(
    issues: List<DosingProfileIssue>,
    onOpenDosingProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val issueTexts = ArrayList<String>(issues.size)
    for (issue in issues) {
        issueTexts += dosingProfileIssueText(issue)
    }
    val spoken = WhyThisNumberCopy.spokenSentence(
        listOf(stringResource(R.string.bolus_recommendation_refused)) +
            issueTexts +
            stringResource(R.string.bolus_open_dosing_profile),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(
                text = stringResource(R.string.bolus_recommendation_refused),
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
        TextButton(onClick = onOpenDosingProfile) {
            Text(stringResource(R.string.bolus_open_dosing_profile))
        }
    }
}

@Composable
private fun BolusWhyThisNumberBody(
    display: BolusDisplayBreakdown,
    onOpenDosingProfile: () -> Unit,
) {
    val spoken = bolusWhySpokenSentence(display)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            if (display.showMealLine) {
                Text(
                    text = stringResource(
                        R.string.bolus_why_meal,
                        display.carbsGramsText,
                        display.cirText,
                        display.mealDoseText,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (display.showCorrectionLine) {
                if (display.correctionIsNegative) {
                    Text(
                        text = stringResource(
                            R.string.bolus_why_correction_subtract,
                            display.targetText,
                            display.glucoseText,
                            display.isfText,
                            display.correctionAbsText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.bolus_why_correction_add,
                            display.glucoseText,
                            display.targetText,
                            display.isfText,
                            display.correctionAbsText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (display.showIobLine) {
                Text(
                    text = stringResource(R.string.bolus_why_iob, display.iobText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when {
                    display.zeroFloorApplied -> stringResource(
                        R.string.bolus_why_zero_floor,
                        display.rawNetText,
                    )
                    display.roundingStepShown -> stringResource(
                        R.string.bolus_why_rounding,
                        display.termsNetText,
                        display.totalText,
                    )
                    else -> stringResource(R.string.bolus_why_total, display.totalText)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.bolus_why_segment,
                    display.isfWithUnit,
                    display.segmentStartText,
                    display.segmentEndText,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.clinical_test_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenDosingProfile) {
            Text(stringResource(R.string.bolus_open_dosing_profile))
        }
    }
}

@Composable
internal fun bolusWhySpokenSentence(display: BolusDisplayBreakdown): String {
    val clauses = buildList {
        if (display.showMealLine) {
            add(
                stringResource(
                    R.string.bolus_why_a11y_meal,
                    display.carbsGramsText,
                    display.cirText,
                    display.mealDoseText,
                ),
            )
        }
        if (display.showCorrectionLine) {
            if (display.correctionIsNegative) {
                add(
                    stringResource(
                        R.string.bolus_why_a11y_correction_subtract,
                        display.targetText,
                        display.glucoseText,
                        display.isfText,
                        display.correctionAbsText,
                    ),
                )
            } else {
                add(
                    stringResource(
                        R.string.bolus_why_a11y_correction_add,
                        display.glucoseText,
                        display.targetText,
                        display.isfText,
                        display.correctionAbsText,
                    ),
                )
            }
        }
        if (display.showIobLine) {
            add(stringResource(R.string.bolus_why_a11y_iob, display.iobText))
        }
        when {
            display.zeroFloorApplied -> add(
                stringResource(R.string.bolus_why_a11y_zero_floor, display.rawNetText),
            )
            display.roundingStepShown -> add(
                stringResource(
                    R.string.bolus_why_a11y_rounding,
                    display.termsNetText,
                    display.totalText,
                ),
            )
            else -> add(stringResource(R.string.bolus_why_a11y_total, display.totalText))
        }
        add(
            stringResource(
                R.string.bolus_why_a11y_segment,
                display.segmentStartText,
                display.segmentEndText,
                display.isfWithUnit,
            ),
        )
        add(stringResource(R.string.clinical_test_disclaimer))
    }
    return WhyThisNumberCopy.spokenSentence(clauses)
}
