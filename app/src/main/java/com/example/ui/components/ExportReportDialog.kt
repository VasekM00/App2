package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    val fiProgress = if (state.fireBaseTargetToday > 0) {
        (state.netWorthTotal / state.fireBaseTargetToday * 100.0).coerceAtMost(100.0)
    } else 0.0

    val reportText = buildString {
        appendLine("==============================================")
        appendLine(" PERSONAL FINANCE TRACKER - FINANCIAL REPORT")
        appendLine(" Country: Czech Republic 🇨🇿")
        appendLine(" Base Year: ${state.settings.baseYear} | Primary Age: ${state.settings.primaryAge}")
        appendLine("==============================================")
        appendLine()
        appendLine("📊 EXECUTIVE SUMMARY")
        appendLine("----------------------------------------------")
        appendLine("• Current Net Worth: ${fmtCZK(state.netWorthTotal)}")
        appendLine("• Base FIRE Target Capital: ${fmtCZK(state.fireBaseTargetToday)}")
        appendLine("• FI Progress: ${String.format("%.1f%%", fiProgress)}")
        appendLine("• Monthly Household Income: ${fmtCZK(state.currentIncome.totalMonthly)}")
        appendLine("• Monthly Household Expenses: ${fmtCZK(state.totalLivingCostMonthly)}")
        appendLine("• Monthly Investment Total: ${fmtCZK(state.investMonthlyTotal)}")
        appendLine("• Savings Rate: ${String.format("%.1f%%", state.savingsRatePct)}")
        appendLine()
        appendLine("🇨🇿 CZECH TAX & PENSIJKO REFORM OPTIMIZATION")
        appendLine("----------------------------------------------")
        appendLine("• Annual Tax Saved via DIP & Pension: ${fmtCZK(state.dip.taxSavedYear)}")
        appendLine("• State Pension Subsidy Total: ${fmtCZK(state.dps.subsidyTotal)}")
        appendLine()
        appendLine("📈 MONTE CARLO & PROJECTION METRICS")
        appendLine("----------------------------------------------")
        appendLine("• 30-Year Monte Carlo Success Rate: ${String.format("%.1f%%", state.monteCarlo.successRatePct)}")
        appendLine("• Safe Withdrawal Rate (SWR): ${state.settings.safeWithdrawalRatePct}%")
        appendLine("• Target Retirement Age: ${state.fireDualPoint?.age ?: ">60"}")
        appendLine("==============================================")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Financial Summary Report",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Export or copy your personal financial independence status, tax optimization results, and projection summary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reportText,
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
                    val clip = ClipData.newPlainText("Personal Finance Report", reportText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Report copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.testTag("copy_report_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Report")
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
