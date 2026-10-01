package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.DcaAllocationBreakdownBar
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.min

@Composable
internal fun BudgetAndIncomesSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onUpdateSettings: (SettingsEntity) -> Unit = {}
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Summary & Allocations, 1 = Income Details, 2 = Expense Details
    val sections = listOf("Overview", "Incomes", "Expenses")
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEachIndexed { index, name ->
                val isSelected = selectedSection == index
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedSection = index
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("budget_section_$index")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> SummarySubTab(
                state = state,
                ledgerEntries = ledgerEntries,
                allImportedTransactions = allImportedTransactions,
                dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
                onDismissSubscription = onDismissSubscription,
                onRestoreSubscription = onRestoreSubscription,
                onClearAllDismissedSubscriptions = onClearAllDismissedSubscriptions,
                onShowInfo = onShowInfo
            )
            1 -> IncomeSubTab(
                state = state,
                ledgerEntries = ledgerEntries,
                onShowInfo = onShowInfo,
                onUpdateSettings = onUpdateSettings
            )
            2 -> SpendingSubTab(state = state, onShowInfo = onShowInfo)
        }
    }
}

@Composable
private fun SummarySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val s = state.settings

    val totalInc = state.currentIncome.totalMonthly
    val totalExp = state.totalLivingCostMonthly
    val netFlow = totalInc - totalExp
    val personalInvestMonthly = s.portuDcaMonthly + (if (!s.isSingleHousehold) s.ePortuDcaMonthly else 0.0) +
            s.dipContributionMonthly + (if (!s.isSingleHousehold) s.eDipContributionMonthly else 0.0) +
            s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
    val unallocatedSurplus = (netFlow - personalInvestMonthly).coerceAtLeast(0.0)
    val investmentsMonthly = state.investMonthlyTotal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 1. Key Metrics Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Cash Flow Summary",
                    subtitle = "Income, living expenses & net monthly surplus · Tap for info",
                    badgeText = "SUMMARY",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricStatBox(
                        label = "Total Income",
                        value = fmtCZK(totalInc),
                        info = CashFlowMetricInfos.totalIncome,
                        onShowInfo = onShowInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Living Expenses",
                        value = fmtCZK(totalExp),
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Surplus",
                        value = fmtCZK(netFlow),
                        valueColor = if (netFlow >= 0) BrandTeal else MaterialTheme.colorScheme.error,
                        info = CashFlowMetricInfos.surplus,
                        onShowInfo = onShowInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Visual Cash Allocation Bar
                CashAllocationBar(
                    totalIncome = totalInc,
                    expenses = totalExp,
                    investments = personalInvestMonthly,
                    unallocated = unallocatedSurplus
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Freedom Days Metric Banner
        val liquidPortfolio = state.settings.liquidPortfolioCurrent +
            (if (!state.settings.isSingleHousehold) state.settings.eLiquidPortfolioCurrent else 0.0)
        val effectiveSavings = if (netFlow < 0.0) netFlow else state.investMonthlyTotal
        com.example.ui.components.FreedomDaysBanner(
            monthlySavings = effectiveSavings,
            monthlyLivingCost = state.totalLivingCostMonthly,
            portfolioBalance = liquidPortfolio
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Subscription & Fixed Debit Auditor Card
        com.example.ui.components.SubscriptionAuditorCard(
            transactions = allImportedTransactions,
            swrPct = state.settings.safeWithdrawalRatePct,
            dismissedMerchantKeys = dismissedSubscriptionMerchants,
            onDismissMerchant = onDismissSubscription,
            onRestoreMerchant = onRestoreSubscription,
            onClearAllDismissed = onClearAllDismissedSubscriptions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Monthly Savings & Investments Allocation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Savings & Investment Allocations",
                    subtitle = "Automated wealth accumulation flows",
                    badgeText = "DCA FLOW",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                var isFirstRow = true

                if (s.portuDcaMonthly > 0) {
                    IncomeRow(label = "Brokerage / ETF (${s.primaryName})", value = fmtCZK(s.portuDcaMonthly))
                    isFirstRow = false
                }
                if (!s.isSingleHousehold && s.ePortuDcaMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Brokerage / ETF (${s.spouseName})", value = fmtCZK(s.ePortuDcaMonthly))
                    isFirstRow = false
                }
                if (s.dipContributionMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "DIP Retirement (${s.primaryName})", value = fmtCZK(s.dipContributionMonthly))
                    isFirstRow = false
                }
                if (!s.isSingleHousehold && s.eDipContributionMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "DIP Retirement (${s.spouseName})", value = fmtCZK(s.eDipContributionMonthly))
                    isFirstRow = false
                }
                if (s.dpsOwnContributionMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "DPS Pension (${s.primaryName})", value = fmtCZK(s.dpsOwnContributionMonthly))
                    isFirstRow = false
                }
                if (!s.isSingleHousehold && s.eDpsOwnContributionMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "DPS Pension (${s.spouseName})", value = fmtCZK(s.eDpsOwnContributionMonthly))
                    isFirstRow = false
                }
                val vEmpMonthly = min(s.employerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)
                if (vEmpMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Employer Pension Match (${s.primaryName})", value = fmtCZK(vEmpMonthly))
                    isFirstRow = false
                }
                val eEmpMonthly = if (!s.isSingleHousehold) min(s.eEmployerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0) else 0.0
                if (eEmpMonthly > 0) {
                    if (!isFirstRow) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Employer Pension Match (${s.spouseName})", value = fmtCZK(eEmpMonthly))
                    isFirstRow = false
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY ALLOCATIONS",
                    value = fmtCZK(investmentsMonthly),
                    isBold = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                DcaAllocationBreakdownBar(settings = state.settings)
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

private data class EditIncomeFieldData(
    val title: String,
    val description: String,
    val currentValue: Double,
    val onConfirm: (Double) -> Unit
)

@Composable
private fun EditableIncomeRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    badgeText: String? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
            .padding(vertical = 2.dp)
    } else Modifier.padding(vertical = 2.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(clickModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                ColorPill(
                    text = badgeText,
                    color = GoodGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 5.dp,
                    verticalPadding = 1.5.dp,
                    cornerRadius = 4.dp
                )
            }
            if (info != null && onShowInfo != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info on $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (onEdit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit $label",
                        tint = BrandTeal,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome
    val s = state.settings

    var editFieldData by remember { mutableStateOf<EditIncomeFieldData?>(null) }
    var editFieldText by remember { mutableStateOf("") }

    val now = remember { java.util.Calendar.getInstance() }
    val currentYear = now.get(java.util.Calendar.YEAR)
    val currentMonth = now.get(java.util.Calendar.MONTH) + 1
    val currentYm = String.format(java.util.Locale.ROOT, "%04d-%02d", currentYear, currentMonth)
    val activeLedger = remember(ledgerEntries, currentYm) {
        ledgerEntries.find { it.yearMonth == currentYm } ?: ledgerEntries.maxByOrNull { it.yearMonth }
    }
    val scheduledLumpSums = remember(s.customLumpSumsJson, currentYear, currentMonth) {
        FinancialEngine.lumpSumsForMonth(currentYear, currentMonth, s)
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
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Income Breakdown",
                    subtitle = "Combined household inflows · Tap row for tax info",
                    badgeText = "INFLOWS",
                    accentColor = GoodGreen
                )

                if (activeLedger != null && (activeLedger.incVaclav > 0.0 || activeLedger.incEleonora > 0.0 || activeLedger.incUnforeseen > 0.0)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorPill(
                        text = "SYNCED WITH LEDGER (${activeLedger.yearMonth})",
                        color = BrandTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp,
                        cornerRadius = 6.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Václav
                Text(
                    text = "VÁCLAV'S INFLOWS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                EditableIncomeRow(
                    label = "Václav's Net Salary",
                    value = fmtCZK(inc.vaclavNet),
                    info = CashFlowMetricInfos.vaclavSalary,
                    onShowInfo = onShowInfo,
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vSalary.toInt().toString()
                            editFieldData = EditIncomeFieldData(
                                title = "Edit Václav's Net Salary",
                                description = "Update baseline monthly net take-home salary. Recalculates projections, savings rate, and cash flows across the app.",
                                currentValue = s.vSalary,
                                onConfirm = { onUpdateSettings(s.copy(vSalary = it)) }
                            )
                        }
                    } else null
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                EditableIncomeRow(
                    label = "Václav's Other Inflows",
                    value = fmtCZK(inc.vaclavOther),
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vOtherInflowsMonthly.toInt().toString()
                            editFieldData = EditIncomeFieldData(
                                title = "Edit Václav's Other Inflows",
                                description = "Update monthly side-hustle, freelance, or other recurring personal inflows for Václav.",
                                currentValue = s.vOtherInflowsMonthly,
                                onConfirm = { onUpdateSettings(s.copy(vOtherInflowsMonthly = it)) }
                            )
                        }
                    } else null
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                EditableIncomeRow(
                    label = "Václav's Gifts",
                    value = fmtCZK(inc.vaclavGifts),
                    badgeText = "EXEMPT",
                    info = CashFlowMetricInfos.vaclavGifts,
                    onShowInfo = onShowInfo,
                    onEdit = if (onUpdateSettings != null) {
                        {
                            editFieldText = s.vGiftsMonthly.toInt().toString()
                            editFieldData = EditIncomeFieldData(
                                title = "Edit Václav's Gifts",
                                description = "Update monthly tax-exempt family gifts received by Václav (§ 10 odst. 3 písm. c ZDP). Completely tax-free and exempt from income taxes.",
                                currentValue = s.vGiftsMonthly,
                                onConfirm = { onUpdateSettings(s.copy(vGiftsMonthly = it, familyGiftMonthly = it + s.eGiftsMonthly)) }
                            )
                        }
                    } else null
                )

                if (!s.isSingleHousehold) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Section 2: Eleonora
                    Text(
                        text = "ELEONORA'S INFLOWS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = BrandGold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (inc.eleonoraSalary > 0.0) {
                        EditableIncomeRow(
                            label = "Eleonora's Net Salary",
                            value = fmtCZK(inc.eleonoraSalary),
                            info = CashFlowMetricInfos.eleonoraSalary,
                            onShowInfo = onShowInfo,
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = s.eStartingSalary.toInt().toString()
                                    editFieldData = EditIncomeFieldData(
                                        title = "Edit Eleonora's Net Salary",
                                        description = "Update baseline monthly net take-home salary for Eleonora.",
                                        currentValue = s.eStartingSalary,
                                        onConfirm = { onUpdateSettings(s.copy(eStartingSalary = it)) }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    if (inc.benefit > 0.0) {
                        EditableIncomeRow(
                            label = "Eleonora's Parental Allowance",
                            value = fmtCZK(inc.benefit),
                            badgeText = "EXEMPT",
                            info = CashFlowMetricInfos.parentalBenefit,
                            onShowInfo = onShowInfo,
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = s.eParentalAllowanceMonthly.toInt().toString()
                                    editFieldData = EditIncomeFieldData(
                                        title = "Edit Eleonora's Parental Allowance",
                                        description = "Update state parental allowance (rodičovský příspěvek). Tax-exempt and excluded from the 68,000 CZK spouse income test.",
                                        currentValue = s.eParentalAllowanceMonthly,
                                        onConfirm = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    if (inc.lecturing > 0.0) {
                        EditableIncomeRow(
                            label = "Eleonora's Lecturing",
                            value = fmtCZK(inc.lecturing),
                            info = CashFlowMetricInfos.lecturing,
                            onShowInfo = onShowInfo,
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = s.eLecturingMonthly.toInt().toString()
                                    editFieldData = EditIncomeFieldData(
                                        title = "Edit Eleonora's Lecturing Income",
                                        description = "Update monthly part-time academic lecturing income.",
                                        currentValue = s.eLecturingMonthly,
                                        onConfirm = { onUpdateSettings(s.copy(eLecturingMonthly = it)) }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    EditableIncomeRow(
                        label = "Eleonora's Other Inflows",
                        value = fmtCZK(inc.eleonoraOther),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.eOtherInflowsMonthly.toInt().toString()
                                editFieldData = EditIncomeFieldData(
                                    title = "Edit Eleonora's Other Inflows",
                                    description = "Update monthly side-hustle or other recurring inflows for Eleonora.",
                                    currentValue = s.eOtherInflowsMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) }
                                )
                            }
                        } else null
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    EditableIncomeRow(
                        label = "Eleonora's Gifts",
                        value = fmtCZK(inc.eleonoraGifts),
                        badgeText = "EXEMPT",
                        info = CashFlowMetricInfos.eleonoraGifts,
                        onShowInfo = onShowInfo,
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.eGiftsMonthly.toInt().toString()
                                editFieldData = EditIncomeFieldData(
                                    title = "Edit Eleonora's Gifts",
                                    description = "Update monthly tax-exempt family gifts received by Eleonora (§ 10 odst. 3 písm. c ZDP). 100% tax-free and excluded from spouse income ceiling.",
                                    currentValue = s.eGiftsMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(eGiftsMonthly = it, familyGiftMonthly = s.vGiftsMonthly + it)) }
                                )
                            }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                // Section 3: Benefits & Bonuses
                Text(
                    text = "BENEFITS & BONUSES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                EditableIncomeRow(
                    label = "Meal Vouchers (Václav)",
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        IncomeRow(
                            label = "${item.name} (${currentMonth}/$currentYear Bonus)",
                            value = fmtCZK(displayAmt),
                            isBold = false
                        )
                    }
                }

                if (unforeseenLump > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(
                        label = "Unforeseen Inflow (${currentMonth}/$currentYear Ledger)",
                        value = fmtCZK(unforeseenLump),
                        isBold = false
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY INCOME",
                    value = fmtCZK(inc.totalMonthly),
                    isBold = true,
                    info = CashFlowMetricInfos.totalIncome,
                    onShowInfo = onShowInfo
                )
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun IncomeRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = if (info != null) 2.dp else 0.dp),
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
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun SpendingSubTab(
    state: FullCalculationState,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val deletedSet = remember(s.deletedCategoriesJson) {
        com.example.domain.parseDeletedCategories(s.deletedCategoriesJson)
    }
    val customCategories = remember(s.customExpensesJson) {
        com.example.domain.parseCustomExpenses(s.customExpensesJson)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Living Costs",
                    subtitle = "Baseline budget & essential expenses",
                    badgeText = "EXPENSES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!deletedSet.contains("rent")) {
                    ExpenseItem("Rent / Housing", s.rentMonthly)
                }
                if (!deletedSet.contains("groceries")) {
                    ExpenseItem("Groceries & Daily Living", s.groceriesMonthly)
                }
                if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                    ExpenseItem("Other Discretionary", s.otherDiscretionaryMonthly)
                }
                if (!deletedSet.contains("cafes")) {
                    ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                }
                if (!deletedSet.contains("therapy")) {
                    ExpenseItem("Therapy / Physio", s.therapyMonthly)
                }
                if (!deletedSet.contains("charity")) {
                    ExpenseItem("Charity", s.charityMonthly)
                }
                if (!deletedSet.contains("entertainment")) {
                    ExpenseItem("Entertainment", s.entertainmentMonthly)
                }
                if (!deletedSet.contains("transport")) {
                    ExpenseItem("Transport", s.transportMonthly)
                }
                if (!deletedSet.contains("subscriptions")) {
                    ExpenseItem("Subscriptions", s.subscriptionsMonthly)
                }

                if (customCategories.isNotEmpty()) {
                    customCategories.forEach { item ->
                        ExpenseItem(item.name, item.amount)
                    }
                }

                if (s.childExpensesEnabled) {
                    if (s.child1Enabled) {
                        val isEmbedded1 = s.currentChildCostsInBaseline && s.child1BirthYear <= s.baseYear
                        val c1 = FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s)
                        if (c1 > 0) {
                            if (isEmbedded1) {
                                ExpenseItem(
                                    label = "Child 1 (Embedded in groceries & rent)",
                                    value = 0.0,
                                    badgeText = "IN BASELINE",
                                    info = CashFlowMetricInfos.childExpenses,
                                    onShowInfo = onShowInfo
                                )
                            } else {
                                ExpenseItem("Child 1 Expenses (Age ${s.baseYear - s.child1BirthYear})", c1, info = CashFlowMetricInfos.childExpenses, onShowInfo = onShowInfo)
                            }
                        }
                    }
                    if (s.child2Enabled) {
                        val isEmbedded2 = s.currentChildCostsInBaseline && s.child2BirthYear <= s.baseYear
                        val c2 = FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s)
                        if (c2 > 0) {
                            if (isEmbedded2) {
                                ExpenseItem(
                                    label = "Child 2 (Embedded in groceries & rent)",
                                    value = 0.0,
                                    badgeText = "IN BASELINE",
                                    info = CashFlowMetricInfos.childExpenses,
                                    onShowInfo = onShowInfo
                                )
                            } else {
                                ExpenseItem("Child 2 Expenses (Age ${s.baseYear - s.child2BirthYear})", c2, info = CashFlowMetricInfos.childExpenses, onShowInfo = onShowInfo)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                ExpenseItem("TOTAL LIVING COSTS", state.totalLivingCostMonthly, isBold = true, info = CashFlowMetricInfos.livingExpenses, onShowInfo = onShowInfo)
                Spacer(modifier = Modifier.height(8.dp))
                ExpenseItem(
                    "MONTHLY INVESTABLE SURPLUS",
                    state.currentIncome.totalMonthly - state.totalLivingCostMonthly,
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = CashFlowMetricInfos.surplus,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ExpenseItem(
    label: String,
    value: Double,
    isBold: Boolean = false,
    highlightColor: Color? = null,
    badgeText: String? = null,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodySmall
            )
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                ColorPill(
                    text = badgeText,
                    color = BrandTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 5.dp,
                    verticalPadding = 1.dp
                )
            }
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
            text = fmtCZK(value),
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun MetricStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier.infoTapHold(info, onShowInfo)
    } else Modifier

    Surface(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.5.sp,
                    color = valueColor
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CashAllocationBar(
    totalIncome: Double,
    expenses: Double,
    investments: Double,
    unallocated: Double
) {
    val totalSpend = expenses + investments
    val normalizer = if (totalIncome > 0) maxOf(totalIncome, totalSpend) else 1.0
    val expRatio = (expenses / normalizer).coerceIn(0.0, 1.0).toFloat()
    val invRatio = (investments / normalizer).coerceIn(0.0, 1.0).toFloat()
    val unallocRatio = (unallocated.coerceAtLeast(0.0) / normalizer).coerceIn(0.0, 1.0).toFloat()

    val expColor = BadRed
    val invColor = BrandTeal
    val unallocColor = WarnAmber

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPill(
                text = "INCOME ALLOCATION",
                color = BrandTeal,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 6.dp,
                verticalPadding = 2.dp,
                cornerRadius = 6.dp
            )
            ColorPill(
                text = "${fmtCompact(totalIncome)} Total",
                color = BrandTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                horizontalPadding = 8.dp,
                verticalPadding = 2.5.dp,
                cornerRadius = 8.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (expRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(expRatio)
                        .fillMaxHeight()
                        .background(expColor, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                )
            }
            if (invRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(invRatio)
                        .fillMaxHeight()
                        .background(invColor)
                )
            }
            if (unallocRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(unallocRatio)
                        .fillMaxHeight()
                        .background(unallocColor, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AllocationPillBox(
                label = "Living Costs",
                amount = expenses,
                ratio = expRatio,
                color = expColor
            )
            AllocationPillBox(
                label = "DCA & Investing",
                amount = investments,
                ratio = invRatio,
                color = invColor
            )
            if (unallocated > 0) {
                AllocationPillBox(
                    label = "Surplus Buffer",
                    amount = unallocated,
                    ratio = unallocRatio,
                    color = unallocColor
                )
            }
        }
    }
}

@Composable
private fun AllocationPillBox(
    label: String,
    amount: Double,
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "${fmtCZK(amount)} · ${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = color
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
