package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
internal fun EditableIncomeRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    badgeText: String? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) = EditableCashFlowRow(
    label = label,
    value = value,
    info = info,
    badgeText = badgeText,
    badgeColor = GoodGreen,
    onShowInfo = onShowInfo,
    onEdit = onEdit
)

@Composable
internal fun IncomeRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color? = null,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
            .padding(vertical = if (isBold) 1.5.dp else 1.dp)
    } else Modifier.padding(vertical = if (isBold) 1.5.dp else 1.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyMedium
            )
            if (info != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info on $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
            else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = valueColor ?: MaterialTheme.colorScheme.onSurface),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
internal fun IncomeSourcesCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome
    val s = state.settings

    var editFieldData by remember { mutableStateOf<EditCashFlowFieldData?>(null) }
    var editFieldText by remember { mutableStateOf("") }

    val activeYear = s.baseYear
    val activeMonth = remember { java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 }
    val currentYm = String.format(java.util.Locale.ROOT, "%04d-%02d", activeYear, activeMonth)
    val activeLedger = remember(ledgerEntries, currentYm) {
        ledgerEntries.find { it.yearMonth == currentYm } ?: ledgerEntries.maxByOrNull { it.yearMonth }
    }
    val scheduledLumpSums = remember(s.customLumpSumsJson, activeYear, activeMonth) {
        FinancialEngine.lumpSumsForMonth(activeYear, activeMonth, s)
    }
    val scheduledLumpSumTotal = scheduledLumpSums.sumOf { it.amount }
    val effectiveScheduledLumpTotal = minOf(inc.lumpSumMonthly, scheduledLumpSumTotal)
    val unforeseenLump = (inc.lumpSumMonthly - scheduledLumpSumTotal).coerceAtLeast(0.0)

    editFieldData?.let { dialog ->
        AlertDialog(
            onDismissRequest = { editFieldData = null },
            title = { Text(dialog.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dialog.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editFieldText,
                        onValueChange = { editFieldText = it },
                        label = { Text("Amount (CZK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = editFieldText.replace(',', '.').trim()
                        val amt = sanitized.toDoubleOrNull() ?: dialog.currentValue
                        if (amt >= 0.0) {
                            dialog.onConfirm(amt)
                        }
                        editFieldData = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editFieldData = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                CardHeaderPill(
                    title = "Monthly Income Breakdown",
                    subtitle = "Combined household inflows · Tap row to edit or info",
                    badgeText = "INFLOWS",
                    accentColor = GoodGreen
                )

                if (activeLedger != null && (activeLedger.incVaclav > 0.0 || activeLedger.incEleonora > 0.0 || activeLedger.incUnforeseen > 0.0)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ColorPill(
                        text = "SYNCED WITH LEDGER (${activeLedger.yearMonth})",
                        color = BrandTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 1.5.dp,
                        cornerRadius = 6.dp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val primaryName = s.primaryName.ifBlank { "Václav" }
                val spouseName = s.spouseName.ifBlank { "Eleonora" }

                // Section 1: Primary Earner
                Text(
                    text = "${primaryName.uppercase(java.util.Locale.ROOT)}'S INFLOWS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                EditableIncomeRow(
                    label = "$primaryName's Net Salary",
                    value = fmtCZK(inc.vaclavNet),
                    info = CashFlowMetricInfos.vaclavSalary,
                    onShowInfo = onShowInfo,
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vSalary.toInt().toString()
                            editFieldData = EditCashFlowFieldData(
                                title = "Edit $primaryName's Net Salary",
                                description = "Update baseline monthly net take-home salary for $primaryName. Recalculates projections, savings rate, and cash flows across the app.",
                                currentValue = s.vSalary,
                                onConfirm = { onUpdateSettings(s.copy(vSalary = it)) }
                            )
                        }
                    } else null
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))

                EditableIncomeRow(
                    label = "$primaryName's Other Inflows",
                    value = fmtCZK(inc.vaclavOther),
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vOtherInflowsMonthly.toInt().toString()
                            editFieldData = EditCashFlowFieldData(
                                title = "Edit $primaryName's Other Inflows",
                                description = "Update monthly side-hustle, freelance, or other recurring personal inflows for $primaryName.",
                                currentValue = s.vOtherInflowsMonthly,
                                onConfirm = { onUpdateSettings(s.copy(vOtherInflowsMonthly = it)) }
                            )
                        }
                    } else null
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))

                EditableIncomeRow(
                    label = "$primaryName's Gifts",
                    value = fmtCZK(inc.vaclavGifts),
                    badgeText = "EXEMPT",
                    info = CashFlowMetricInfos.vaclavGifts,
                    onShowInfo = onShowInfo,
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vGiftsMonthly.toInt().toString()
                            editFieldData = EditCashFlowFieldData(
                                title = "Edit $primaryName's Gifts",
                                description = "Update monthly tax-exempt family gifts received by $primaryName. Completely tax-free and exempt from income taxes.",
                                currentValue = s.vGiftsMonthly,
                                onConfirm = { onUpdateSettings(s.copy(vGiftsMonthly = it, familyGiftMonthly = it + s.eGiftsMonthly)) }
                            )
                        }
                    } else null
                )

                if (!s.isSingleHousehold) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(5.dp))

                    // Section 2: Partner
                    Text(
                        text = "${spouseName.uppercase(java.util.Locale.ROOT)}'S INFLOWS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = BrandGold
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))

                    EditableIncomeRow(
                        label = "$spouseName's Net Salary",
                        value = fmtCZK(inc.eleonoraSalary),
                        info = CashFlowMetricInfos.eleonoraSalary,
                        onShowInfo = onShowInfo,
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.eStartingSalary.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit $spouseName's Net Salary",
                                    description = "Update baseline monthly net take-home salary for $spouseName.",
                                    currentValue = s.eStartingSalary,
                                    onConfirm = { onUpdateSettings(s.copy(eStartingSalary = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))

                    if (inc.benefit > 0.0) {
                        EditableIncomeRow(
                            label = "$spouseName's Parental Allowance",
                            value = fmtCZK(inc.benefit),
                            badgeText = "EXEMPT",
                            info = CashFlowMetricInfos.parentalBenefit,
                            onShowInfo = onShowInfo,
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = s.eParentalAllowanceMonthly.toInt().toString()
                                    editFieldData = EditCashFlowFieldData(
                                        title = "Edit $spouseName's Parental Allowance",
                                        description = "Update state parental allowance (rodičovský příspěvek). Tax-exempt and excluded from the 68,000 CZK partner income test.",
                                        currentValue = s.eParentalAllowanceMonthly,
                                        onConfirm = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                    }

                    if (inc.lecturing > 0.0) {
                        EditableIncomeRow(
                            label = "$spouseName's Lecturing",
                            value = fmtCZK(inc.lecturing),
                            info = CashFlowMetricInfos.lecturing,
                            onShowInfo = onShowInfo,
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = s.eLecturingMonthly.toInt().toString()
                                    editFieldData = EditCashFlowFieldData(
                                        title = "Edit $spouseName's Lecturing Income",
                                        description = "Update monthly part-time academic lecturing income.",
                                        currentValue = s.eLecturingMonthly,
                                        onConfirm = { onUpdateSettings(s.copy(eLecturingMonthly = it)) }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                    }

                    EditableIncomeRow(
                        label = "$spouseName's Other Inflows",
                        value = fmtCZK(inc.eleonoraOther),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.eOtherInflowsMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit $spouseName's Other Inflows",
                                    description = "Update monthly side-hustle or other recurring inflows for $spouseName.",
                                    currentValue = s.eOtherInflowsMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) }
                                )
                            }
                        } else null
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))

                    EditableIncomeRow(
                        label = "$spouseName's Gifts",
                        value = fmtCZK(inc.eleonoraGifts),
                        badgeText = "EXEMPT",
                        info = CashFlowMetricInfos.eleonoraGifts,
                        onShowInfo = onShowInfo,
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.eGiftsMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit $spouseName's Gifts",
                                    description = "Update monthly tax-exempt family gifts received by $spouseName. 100% tax-free and excluded from partner income ceiling.",
                                    currentValue = s.eGiftsMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(eGiftsMonthly = it, familyGiftMonthly = s.vGiftsMonthly + it)) }
                                )
                            }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(5.dp))

                // Section 3: Benefits & Bonuses
                Text(
                    text = "BENEFITS & BONUSES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                EditableIncomeRow(
                    label = "Meal Vouchers ($primaryName)",
                    value = fmtCZK(inc.vouchers),
                    badgeText = "EXEMPT",
                    info = CashFlowMetricInfos.mealVouchers,
                    onShowInfo = onShowInfo
                )

                if (scheduledLumpSums.isNotEmpty() && effectiveScheduledLumpTotal > 0.0) {
                    for (item in scheduledLumpSums) {
                        val displayAmt = if (scheduledLumpSumTotal > 0.0) {
                            item.amount * (effectiveScheduledLumpTotal / scheduledLumpSumTotal)
                        } else item.amount
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                        IncomeRow(
                            label = "${item.name} (${activeMonth}/$activeYear Bonus)",
                            value = fmtCZK(displayAmt),
                            isBold = false
                        )
                    }
                }

                if (unforeseenLump > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                    IncomeRow(
                        label = "Unforeseen Inflow (${activeMonth}/$activeYear Ledger)",
                        value = fmtCZK(unforeseenLump),
                        isBold = false
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 1.5.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY INCOME",
                    value = fmtCZK(inc.totalMonthly),
                    isBold = true,
                    info = CashFlowMetricInfos.totalIncome,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                val surplus = inc.totalMonthly - state.totalLivingCostMonthly
                IncomeRow(
                    label = "NET MONTHLY SURPLUS",
                    value = fmtCZK(surplus),
                    isBold = true,
                    valueColor = if (surplus >= 0.0) GoodGreen else BadRed,
                    info = CashFlowMetricInfos.surplus,
                    onShowInfo = onShowInfo
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
