@file:Suppress("InlinedApi")

package com.example.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.components.charts.finiteOrZero
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtExactCZK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportReportDialog(
    state: FullCalculationState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onExternalPickerLaunching = LocalExternalPickerGuard.current

    val snap = state.snapshot
    val sEff = state.effectiveSettings
    val fiProgress = snap.fireProgressPct.finiteOrZero()

    val reportText = remember(state) {
        buildString {
            appendLine("==============================================")
            appendLine(" PERSONAL FINANCE TRACKER - FINANCIAL REPORT")
            appendLine(" Country: Czech Republic")
            appendLine(" Base Year: ${sEff.baseYear}")
            appendLine("==============================================")
            appendLine()
            appendLine("EXECUTIVE SUMMARY")
        appendLine("• Net Worth (Total): ${fmtCZK(snap.netWorthTotal.finiteOrZero())}")
        appendLine("• Liquid Investments: ${fmtCZK(snap.investedLiquidTotal.finiteOrZero())}")
        appendLine("• Emergency Reserve: ${fmtCZK(snap.emergencyCashTotal.finiteOrZero())} (${String.format(java.util.Locale.ROOT, "%.1f", snap.emergencyRunwayMonths.finiteOrZero())} months)")
        appendLine("• Pension Portfolio (DPS + DIP): ${fmtCZK(snap.pensionTotal.finiteOrZero())}")
        appendLine("• Current FIRE Target: ${fmtCZK(snap.activeFireTargetToday.finiteOrZero())}")
        appendLine("• FIRE Progress: ${String.format(java.util.Locale.ROOT, "%.1f%%", fiProgress)}")
        if (sEff.isSingleHousehold) {
            appendLine("• Projected FIRE Year: ${snap.activeFireYear?.let { "$it" } ?: "Beyond 35 years"}")
        } else {
            val singleCanonicalYear = (state.singleTrajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded } ?: state.fireSinglePoint)?.year
            appendLine("• Projected FIRE Year (Dual Earner): ${snap.activeFireYear?.let { "$it" } ?: "Beyond 35 years"}")
            appendLine("• Projected FIRE Year (Single Earner Bridge): ${singleCanonicalYear?.let { "$it" } ?: "Beyond 35 years"}")
        }
        appendLine()
        val pName = state.settings.primaryName.ifBlank { "Primary Earner" }
        val sName = state.settings.spouseName.ifBlank { "Partner" }
        appendLine("MONTHLY CASH FLOW")
        appendLine("• $pName Net Income: ${fmtCZK(state.currentIncome.vaclavNet)}")
        if (state.currentIncome.vaclavOther > 0.0) {
            appendLine("• $pName Other Inflows: ${fmtCZK(state.currentIncome.vaclavOther)}")
        }
        if (state.currentIncome.vaclavGifts > 0.0) {
            appendLine("• $pName Tax-Exempt Gifts: ${fmtCZK(state.currentIncome.vaclavGifts)}")
        }
        if (!state.settings.isSingleHousehold) {
            if (state.currentIncome.eleonoraSalary > 0.0) {
                appendLine("• $sName Net Salary: ${fmtCZK(state.currentIncome.eleonoraSalary)}")
            }
            if (state.currentIncome.benefit > 0.0) {
                appendLine("• $sName Parental Allowance: ${fmtCZK(state.currentIncome.benefit)}")
            }
            if (state.currentIncome.lecturing > 0.0) {
                appendLine("• $sName Lecturing: ${fmtCZK(state.currentIncome.lecturing)}")
            }
            if (state.currentIncome.eleonoraOther > 0.0) {
                appendLine("• $sName Other Inflows: ${fmtCZK(state.currentIncome.eleonoraOther)}")
            }
            if (state.currentIncome.eleonoraGifts > 0.0) {
                appendLine("• $sName Tax-Exempt Gifts: ${fmtCZK(state.currentIncome.eleonoraGifts)}")
            }
        }
        appendLine("• Meal Vouchers ($pName): ${fmtCZK(state.currentIncome.vouchers)}")
        if (state.currentIncome.lumpSumMonthly > 0.0) {
            appendLine("• Lump Sums / Bonuses: ${fmtCZK(state.currentIncome.lumpSumMonthly)}")
        }
        appendLine("• Total Net Household Income: ${fmtCZK(snap.monthlyTakeHomeInflows)} monthly")
        appendLine("• Monthly Investments (DCA + Match): ${fmtCZK(snap.monthlyTotalInvesting)} (Savings rate ${String.format(java.util.Locale.ROOT, "%.1f%%", snap.savingsRatePct)})")
        appendLine()
        appendLine("CZECH TAX & PENSION REFORM")
        appendLine("• DIP Annual Deduction Base: ${fmtExactCZK(state.taxReturnHelper.retirementDeductionBase)}")
        appendLine("• DIP Annual Tax Saved: ${fmtExactCZK(state.taxReturnHelper.dipSaving)}")
        appendLine("• Pension Portfolio (DPS + DIP): ${fmtCZK(state.currentPensionPortfolio)}")
        appendLine("• DPS Annual Fee: ${String.format(java.util.Locale.ROOT, "%.2f", state.settings.dpsAnnualFeePct.finiteOrZero())}%/year (statutory cap ${String.format(java.util.Locale.ROOT, "%.2f", state.settings.dpsStatutoryFeeCapPct.finiteOrZero())}%)")
        appendLine()
        val activeMc = if (state.settings.useHistoricalBootstrap) state.historicalMonteCarlo else state.monteCarlo
        val mcModeLabel = if (state.settings.useHistoricalBootstrap) "HISTORICAL BOOTSTRAP" else "PARAMETRIC MONTE CARLO"
        appendLine("MONTE CARLO STRESS TEST ($mcModeLabel)")
        appendLine("• FIRE Success Probability: ${String.format(java.util.Locale.ROOT, "%.1f%%", activeMc.successRatePct.finiteOrZero())}")
        appendLine("• Median FIRE Year: ${activeMc.medianFireAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "N/A"}")
        appendLine("• Fastest / Best-Case (5th percentile year): ${activeMc.bestCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "N/A"}")
        appendLine("• Conservative / Late (95th percentile year): ${activeMc.worstCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "N/A"}")
        appendLine("==============================================")
        }
    }

    var exportFormat by remember { mutableStateOf("Summary") } // "Summary" or "CSV"

    val csvReportText = remember(state) {
        buildString {
            appendLine("Year,Age,Portfolio (Kč),Target (Kč),Invested Annual (Kč),Status")
            val traj = if (state.settings.isSingleHousehold) state.singleTrajectory else state.dualTrajectory
            traj.forEach { point ->
                appendLine("${point.year},${point.age},${point.totalPortfolio.finiteOrZero().toLong()},${point.target.finiteOrZero().toLong()},${point.investedAnnual.finiteOrZero().toLong()},${escapeExportCsvCell(point.status)}")
            }
        }
    }

    val activeText = if (exportFormat == "Summary") reportText else csvReportText
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export & Share",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Export your financial independence status, tax optimization numbers, and 35-year trajectory table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Format toggle chips
                ChipWrapRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalSpacing = 8.dp
                ) {
                    listOf("Summary", "CSV Trajectory").forEach { format ->
                        FilterChip(
                            selected = (exportFormat == "Summary" && format == "Summary") || (exportFormat == "CSV" && format == "CSV Trajectory"),
                            onClick = {
                                exportFormat = if (format == "Summary") "Summary" else "CSV"
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            label = { Text(format, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val shareIntent = if (exportFormat == "Summary") {
                                    com.example.util.ReportShareHelper.createShareTextIntent(
                                        text = activeText,
                                        subject = "Personal Finance Report (${sEff.baseYear})"
                                    )
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.example.util.ReportShareHelper.createShareCsvIntent(
                                            context = context,
                                            csvBytes = com.example.util.ReportShareHelper.getCsvBytesWithBom(activeText),
                                            filename = "fire_trajectory_${sEff.baseYear}.csv",
                                            subject = "FIRE Trajectory Export (${sEff.baseYear})"
                                        )
                                    }
                                }
                                onExternalPickerLaunching()
                                context.startActivity(Intent.createChooser(shareIntent, "Share Financial Report"))
                                onDismiss()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to open share sheet.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("share_report_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", maxLines = 2, softWrap = true)
                }
                Button(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(
                                if (exportFormat == "Summary") "Personal Finance Report" else "FIRE Projections CSV",
                                activeText
                            )
                            clip.description.extras = PersistableBundle().apply {
                                putBoolean(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        ClipDescription.EXTRA_IS_SENSITIVE
                                    } else {
                                        "android.content.extra.IS_SENSITIVE"
                                    },
                                    true
                                )
                            }
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "${if (exportFormat == "Summary") "Report" else "CSV"} copied to clipboard!", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Report too large for clipboard. Tap Share instead.", Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("copy_report_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy ${if (exportFormat == "Summary") "Report" else "CSV"}", maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("close_report_dialog_button")
            ) {
                Text("Close")
            }
        },
        modifier = Modifier
            .testTag("export_report_dialog")
    )
}

internal fun escapeExportCsvCell(value: String): String =
    com.example.util.ReportShareHelper.escapeCsvCell(value, delimiter = ",")
