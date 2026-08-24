package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact

@Composable
fun ExportReportDialog(
    state: FullCalculationState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val fiProgress = state.fireMilestones.standardFire.progressPct

    val reportText = buildString {
        appendLine("==============================================")
        appendLine(" PERSONAL FINANCE TRACKER - FINANCIAL REPORT")
        appendLine(" Country: Czech Republic")
        appendLine(" Base Year: ${state.settings.baseYear}")
        appendLine("==============================================")
        appendLine()
        appendLine("EXECUTIVE SUMMARY")
        appendLine("• Net Worth (Total): ${fmtCZK(state.netWorthTotal)}")
        appendLine("• Monthly Living Expenses: ${fmtCZK(state.totalLivingCostMonthly)}")
        appendLine("• Emergency Reserve: ${fmtCZK(state.settings.emergencyReserveCurrent)} (${String.format(java.util.Locale.ROOT, "%.1f", state.emergencyCoverageMonths)} months)")
        appendLine("• Current FIRE Target: ${fmtCZK(state.fireBaseTargetToday)}")
        appendLine("• FIRE Progress: ${String.format(java.util.Locale.ROOT, "%.1f%%", fiProgress)}")
        appendLine("• Projected FIRE Year (Dual): ${state.fireDualPoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y"}")
        appendLine("• Projected FIRE Year (Single): ${state.fireSinglePoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y"}")
        appendLine()
        appendLine("MONTHLY CASH FLOW")
        appendLine("• Václav Net Income: ${fmtCZK(state.currentIncome.vaclavNet)}")
        appendLine("• Eleonora Salary: ${fmtCZK(state.currentIncome.eleonoraSalary)}")
        appendLine("• Total Family Net/mo: ${fmtCZK(state.currentIncome.totalMonthly)}")
        appendLine("• Monthly Investments (DCA): ${fmtCZK(state.investMonthlyTotal)}")
        appendLine()
        appendLine("CZECH TAX & PENSION REFORM")
        appendLine("• DIP Annual Deduction Base: ${fmtCZK(state.taxReturnHelper.retirementDeductionBase)}")
        appendLine("• DIP Annual Tax Saved: ${fmtCZK(state.taxReturnHelper.dipSaving)}")
        appendLine("• DPS Pension Balance: ${fmtCZK(state.settings.dpsBalanceCurrent + state.settings.eDpsBalanceCurrent)}")
        appendLine("• DPS Statutory Fee Cap: 0.5% p.a.")
        appendLine()
        appendLine("MONTE CARLO STRESS TEST")
        appendLine("• FIRE Success Probability: ${String.format(java.util.Locale.ROOT, "%.1f%%", state.monteCarlo.successRatePct)}")
        appendLine("• Median FIRE Age: ${state.monteCarlo.medianFireAge?.let { "Age $it" } ?: "N/A"}")
        appendLine("• Fastest / Best-Case (5th percentile age): ${state.monteCarlo.bestCaseAge?.let { "Age $it" } ?: "N/A"}")
        appendLine("• Conservative / Late (95th percentile age): ${state.monteCarlo.worstCaseAge?.let { "Age $it" } ?: "N/A"}")
        appendLine("==============================================")
    }

    var exportFormat by remember { mutableStateOf("Summary") } // "Summary" or "CSV"

    val csvReportText = remember(state) {
        buildString {
            appendLine("Year,Age,Portfolio CZK,Target CZK,Invested Annual CZK,Status")
            state.dualTrajectory.forEach { point ->
                appendLine("${point.year},${point.age},${point.portfolio.toLong()},${point.target.toLong()},${point.investedAnnual.toLong()},${point.status}")
            }
        }
    }

    val activeText = if (exportFormat == "Summary") reportText else csvReportText

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
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Export your financial independence status, tax optimization numbers, and 35-year trajectory table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Format toggle chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Summary", "CSV Trajectory").forEach { format ->
                        FilterChip(
                            selected = (exportFormat == "Summary" && format == "Summary") || (exportFormat == "CSV" && format == "CSV Trajectory"),
                            onClick = {
                                exportFormat = if (format == "Summary") "Summary" else "CSV"
                            },
                            label = { Text(format, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(
                        if (exportFormat == "Summary") "Personal Finance Report" else "FIRE Projections CSV",
                        activeText
                    )
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "${if (exportFormat == "Summary") "Report" else "CSV"} copied to clipboard!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.testTag("copy_report_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy ${if (exportFormat == "Summary") "Report" else "CSV"}")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_report_dialog_button")
            ) {
                Text("Close")
            }
        },
        modifier = Modifier.testTag("export_report_dialog")
    )
}
