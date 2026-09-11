package com.omb9.glucosehero.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.DosingBounds
import com.omb9.glucosehero.domain.model.DosingProfileIssue
import com.omb9.glucosehero.domain.model.DosingProfileIssueCode

@Composable
fun dosingProfileIssueText(issue: DosingProfileIssue): String {
    val suffix = issue.segmentIndex?.let { " (#${it + 1})" } ?: ""
    return when (issue.code) {
        DosingProfileIssueCode.EMPTY_SEGMENTS ->
            stringResource(R.string.dosing_issue_empty)
        DosingProfileIssueCode.FIRST_SEGMENT_NOT_MIDNIGHT ->
            stringResource(R.string.dosing_issue_not_midnight) + suffix
        DosingProfileIssueCode.STARTS_NOT_STRICTLY_INCREASING ->
            stringResource(R.string.dosing_issue_not_increasing) + suffix
        DosingProfileIssueCode.DUPLICATE_START ->
            stringResource(R.string.dosing_issue_duplicate) + suffix
        DosingProfileIssueCode.ISF_OUT_OF_BOUNDS ->
            stringResource(
                R.string.dosing_issue_isf,
                DosingBounds.MIN_ISF_MGDL.toInt().toString(),
                DosingBounds.MAX_ISF_MGDL.toInt().toString(),
            ) + suffix
        DosingProfileIssueCode.CIR_OUT_OF_BOUNDS ->
            stringResource(
                R.string.dosing_issue_cir,
                DosingBounds.MIN_CIR_RATIO.toInt().toString(),
                DosingBounds.MAX_CIR_RATIO.toInt().toString(),
            ) + suffix
        DosingProfileIssueCode.TARGET_OUT_OF_BOUNDS ->
            stringResource(
                R.string.dosing_issue_target,
                DosingBounds.MIN_TARGET_MGDL.toInt().toString(),
                DosingBounds.MAX_TARGET_MGDL.toInt().toString(),
            ) + suffix
        DosingProfileIssueCode.DIA_OUT_OF_BOUNDS ->
            stringResource(
                R.string.dosing_issue_dia,
                DosingBounds.MIN_DIA_HOURS.toInt().toString(),
                DosingBounds.MAX_DIA_HOURS.toInt().toString(),
            )
        DosingProfileIssueCode.UNPARSEABLE ->
            stringResource(R.string.dosing_issue_unparseable)
    }
}
