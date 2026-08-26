package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandBlue
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.CashFlowProjectionChart
import com.example.ui.components.DcaTrajectoryBarChart
import com.example.ui.components.DcaAllocationBreakdownBar
import kotlin.math.abs

fun nextYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size == 2) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (y != null && m != null && m in 1..12) {
            return if (m >= 12) {
                String.format(Locale.ROOT, "%04d-%02d", y + 1, 1)
            } else {
                String.format(Locale.ROOT, "%04d-%02d", y, m + 1)
            }
        }
    }
    val now = java.util.Calendar.getInstance()
    return String.format(
        Locale.ROOT,
        "%04d-%02d",
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH) + 1
    )
}

fun prevYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size == 2) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (y != null && m != null && m in 1..12) {
            return if (m <= 1) {
                String.format(Locale.ROOT, "%04d-%02d", y - 1, 12)
            } else {
                String.format(Locale.ROOT, "%04d-%02d", y, m - 1)
            }
        }
    }
    return ym
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    val subTabs = listOf("Budget & Incomes", "Monthly Ledger")
    var showAddDialog by remember { mutableStateOf(false) }
    var duplicateFromEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    val infoState = rememberMetricInfoState()

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("cashflow_tab")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(
                selectedTabIndex = selectedSubTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSubTab == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedSubTab = index
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 2,
                                softWrap = true
                            )
                        },
                        modifier = Modifier.testTag("cashflow_subtab_$index")
                    )
                }
            }

            when (selectedSubTab) {
                0 -> BudgetAndIncomesSubTab(
                    state = state,
                    ledgerEntries = ledgerEntries,
                    onShowInfo = { infoState.show(it) }
                )
                1 -> LedgerSubTab(
                    state = state,
                    entries = ledgerEntries,
                    onAddClick = {
                        duplicateFromEntry = null
                        showAddDialog = true
                    },
                    onDuplicateEntry = { entry ->
                        duplicateFromEntry = entry
                        showAddDialog = true
                    },
                    onEditEntry = { entry -> editingEntry = entry },
                    onDelete = onDeleteLedgerEntry,
                    onImportCsv = onImportCsv
                )
            }
        }

        if (selectedSubTab == 1) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    duplicateFromEntry = null
                    showAddDialog = true
                },
                containerColor = BrandTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .testTag("fab_add_ledger_entry")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Entry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showAddDialog) {
        AddLedgerEntryDialog(
            state = state,
            entries = ledgerEntries,
            duplicateFrom = duplicateFromEntry,
            onDismiss = {
                showAddDialog = false
                duplicateFromEntry = null
            },
            onSave = { ym, incV, incE, incU, expR, expL, notes ->
                onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes)
                showAddDialog = false
                duplicateFromEntry = null
            }
        )
    }

    editingEntry?.let { entry ->
        AddLedgerEntryDialog(
            state = state,
            entries = ledgerEntries,
            initialEntry = entry,
            onDismiss = { editingEntry = null },
            onSave = { ym, incV, incE, incU, expR, expL, notes ->
                onUpdateLedgerEntry(
                    entry.copy(
                        yearMonth = ym,
                        incVaclav = incV,
                        incEleonora = incE,
                        incUnforeseen = incU,
                        expRent = expR,
                        expGroceries = expL,
                        expOther = 0.0,
                        notes = notes
                    )
                )
                editingEntry = null
            }
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}

private object CashFlowMetricInfos {
    val totalIncome = MetricInfo(
        title = "Total Monthly Inflows",
        category = "Cash Flow Engine",
        formulaOrRule = "Net Salaries + Parental Allowance + Meal Vouchers + Family Gifts",
        explanation = "Combined monthly liquid inflows available to the household. Reflects true cash generation after Czech personal income taxes (15%/23%), social security (7.1%), and health insurance (4.5%).",
        statutoryReference = "Act No. 586/1992 Coll. (ZDP)",
        practicalImplication = "Maximizing tax-exempt meal vouchers and allowances increases take-home cash without pushing you into the 23% progressive tax bracket.",
        accentColor = Color(0xFF16A34A)
    )

    val livingExpenses = MetricInfo(
        title = "Total Living Expenses",
        category = "Budget & Burn Rate",
        formulaOrRule = "Rent + Groceries + Lifestyle + Children + Discretionary",
        explanation = "Your household's monthly operational burn rate. Lowering permanent baseline living expenses has a dual effect: it immediately increases your monthly savings rate and permanently lowers your required total FIRE target capital.",
        practicalImplication = "Every 1,000 CZK/month reduction in permanent living costs lowers your required FIRE nest egg by ~342,000 CZK at a 3.5% SWR.",
        accentColor = Color(0xFF0F766E)
    )

    val surplus = MetricInfo(
        title = "Net Monthly Cash Surplus",
        category = "Capital Generation",
        formulaOrRule = "Total Monthly Inflows - Total Living Expenses",
        explanation = "The net uncommitted capital generated by the household every month. This funds your automated monthly DCA investments (ETFs, DIP, DPS) and expands your liquid emergency reserve.",
        practicalImplication = "Maintaining a positive surplus even during single-earner or parental leave periods guarantees your long-term compound trajectory remains intact.",
        accentColor = Color(0xFF0F766E)
    )

    val vaclavSalary = MetricInfo(
        title = "Václav's Net Salary",
        category = "Earned Take-Home Pay",
        formulaOrRule = "Gross Salary - 15% Tax (less 2,570 CZK credit) - 7.1% Social - 4.5% Health",
        explanation = "Your take-home net salary after statutory employee deductions and basic taxpayer tax credit (§ 35ba(1)(a) ZDP).",
        statutoryReference = "§ 35ba odst. 1 písm. a) Act No. 586/1992 Coll.",
        accentColor = Color(0xFF0F766E)
    )

    val eleonoraSalary = MetricInfo(
        title = "Eleonora's Net Salary",
        category = "Earned Take-Home Pay",
        formulaOrRule = "Gross Salary - 15% Tax - 7.1% Social - 4.5% Health",
        explanation = "Projected net salary upon returning to employment with annual compounding career growth.",
        accentColor = Color(0xFFD97706)
    )

    val mealVouchers = MetricInfo(
        title = "Meal Voucher Cash Allowance",
        category = "Czech Tax Optimization",
        formulaOrRule = "§ 6 odst. 9 písm. b) ZDP · Non-taxable cash allowance",
        explanation = "Monetary meal allowance (stravenkový paušál) paid by employer directly into your bank account. 100% exempt from personal income tax, health insurance, and social security up to the statutory daily limit.",
        statutoryReference = "§ 6 odst. 9 písm. b) Act No. 586/1992 Coll.",
        practicalImplication = "Pure net tax-free cash equivalent to an additional ~3,000 CZK gross salary without tax drag.",
        accentColor = Color(0xFF0F766E)
    )

    val parentalBenefit = MetricInfo(
        title = "State Parental Allowance",
        category = "State Social Support",
        formulaOrRule = "350,000 CZK total entitlement · 100% tax exempt",
        explanation = "State family benefit for parents caring full-time for the youngest child. Fully tax-exempt under § 4(1)(j) ZDP. Crucially, parental allowance does NOT count into the 68,000 CZK/year spouse own income limit for the spouse tax credit (§ 35ba(1)(b)).",
        statutoryReference = "Act No. 117/1995 Coll. & § 4 odst. 1 písm. j) ZDP",
        practicalImplication = "Because parental allowance is excluded from the 68k CZK income ceiling, Václav can claim the full 24,840 CZK/yr spouse tax credit while Eleonora is on leave (for children under 3).",
        accentColor = Color(0xFFD97706)
    )

    val lecturing = MetricInfo(
        title = "Eleonora's Lecturing Income",
        category = "Academic & Side Income",
        formulaOrRule = "Independent lecturing, teaching & advisory inflows",
        explanation = "Part-time academic lecturing income. Note that lecturing income counts toward the 68,000 CZK annual spouse income ceiling for tax credit eligibility.",
        statutoryReference = "§ 35ba odst. 1 písm. b) ZDP",
        accentColor = Color(0xFFD97706)
    )

    val familyGift = MetricInfo(
        title = "Family Support Gift",
        category = "Tax Exemption",
        formulaOrRule = "§ 10 odst. 3 písm. c) ZDP · Direct Lineage Exemption",
        explanation = "Gifts and financial contributions between direct-line relatives (parents, children, grandparents) and spouses are completely exempt from Czech personal income tax without monetary ceiling.",
        statutoryReference = "§ 10 odst. 3 písm. c) Act No. 586/1992 Coll.",
        practicalImplication = "Family gifts directly supplement monthly DCA investment flows with 0% tax liability.",
        accentColor = Color(0xFF0F766E)
    )

    val dcaInvestments = MetricInfo(
        title = "Automated Monthly DCA",
        category = "Investment Compounding",
        formulaOrRule = "Monthly ETF/Brokerage + DIP + DPS Deposits",
        explanation = "Automated dollar-cost averaging into diversified global equity index funds and tax-sheltered retirement vehicles. Eliminates market timing risk by steadily purchasing shares through market cycles.",
        practicalImplication = "Consistently investing monthly surplus accelerates net worth compounding and sequence-of-returns protection.",
        accentColor = Color(0xFF0F766E)
    )

    val dipDeduction = MetricInfo(
        title = "DIP (Dlouhodobý investiční produkt)",
        category = "Retirement Tax Shield",
        formulaOrRule = "§ 15a ZDP · Up to 48,000 CZK/yr tax deduction",
        explanation = "Czech long-term investment product enabling investments into index ETFs with pre-tax income. Combined 48k CZK annual ceiling with DPS provides 7,200 CZK (at 15% rate) or 11,040 CZK (at 23% rate) in direct annual tax savings.",
        statutoryReference = "§ 15a Act No. 586/1992 Coll.",
        practicalImplication = "Reinvesting tax savings into your ETF portfolio creates a compound tax alpha on your retirement nest egg.",
        accentColor = Color(0xFF16A34A)
    )

    val dpsSubsidy = MetricInfo(
        title = "DPS (Doplňkové penzijní spoření)",
        category = "State Subsidy & Pension",
        formulaOrRule = "20% standard / 40% youth match + § 15(5) ZDP deduction",
        explanation = "Supplementary pension savings with direct state matching. Under 30 receives 40% state match up to 680 CZK/mo. Contributions over 1,700 CZK/mo qualify for additional personal income tax deduction.",
        statutoryReference = "Act No. 427/2011 Coll.",
        practicalImplication = "Combines guaranteed state subsidy returns with long-term compound equity growth in dynamic participation funds.",
        accentColor = Color(0xFF0F766E)
    )

    val savingsRate = MetricInfo(
        title = "Household Savings Rate",
        category = "FIRE Velocity",
        formulaOrRule = "(Total Inflows - Living Expenses) / Total Inflows",
        explanation = "The single most powerful determinant of your early retirement date. A 50%+ savings rate guarantees financial independence in ~15 years; a 65%+ rate cuts the timeline to ~10 years.",
        practicalImplication = "Every 5% boost in savings rate compounds exponentially into faster FIRE milestones.",
        accentColor = Color(0xFF0F766E)
    )
}

@Composable
private fun IncomeSubTab(
    state: FullCalculationState,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome

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
                Spacer(modifier = Modifier.height(16.dp))

                IncomeRow(label = "Václav's Net Salary", value = fmtCZK(inc.vaclavNet), info = CashFlowMetricInfos.vaclavSalary, onShowInfo = onShowInfo)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (inc.eleonoraSalary > 0.0) {
                    IncomeRow(label = "Eleonora's Net Salary", value = fmtCZK(inc.eleonoraSalary), info = CashFlowMetricInfos.eleonoraSalary, onShowInfo = onShowInfo)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                if (inc.benefit > 0.0) {
                    IncomeRow(label = "Eleonora's Parental Allowance", value = fmtCZK(inc.benefit), info = CashFlowMetricInfos.parentalBenefit, onShowInfo = onShowInfo)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                if (inc.lecturing > 0.0) {
                    IncomeRow(label = "Eleonora's Lecturing", value = fmtCZK(inc.lecturing), info = CashFlowMetricInfos.lecturing, onShowInfo = onShowInfo)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                IncomeRow(label = "Meal Vouchers (Václav)", value = fmtCZK(inc.vouchers), info = CashFlowMetricInfos.mealVouchers, onShowInfo = onShowInfo)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Family Support Gift", value = fmtCZK(inc.gift), info = CashFlowMetricInfos.familyGift, onShowInfo = onShowInfo)
                if (state.settings.vOtherInflowsMonthly > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Václav's Other Inflows", value = fmtCZK(state.settings.vOtherInflowsMonthly))
                }
                if (!state.settings.isSingleHousehold && state.settings.eOtherInflowsMonthly > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora's Other Inflows", value = fmtCZK(state.settings.eOtherInflowsMonthly))
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

        Spacer(modifier = Modifier.height(16.dp))

        // Visual DCA Trajectory Bar Chart & Cash Flow Projections
        var chartViewIndex by remember { mutableIntStateOf(0) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { chartViewIndex = 0 },
                label = { Text("DCA Bar Chart", fontSize = 12.sp, fontWeight = if (chartViewIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (chartViewIndex == 0) BrandTeal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = if (chartViewIndex == 0) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).testTag("chip_dca_bar_chart")
            )
            AssistChip(
                onClick = { chartViewIndex = 1 },
                label = { Text("Line Trajectory", fontSize = 12.sp, fontWeight = if (chartViewIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (chartViewIndex == 1) BrandGold.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = if (chartViewIndex == 1) BrandGold else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).testTag("chip_line_trajectory")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (chartViewIndex == 0) {
            DcaTrajectoryBarChart(
                data = state.dualTrajectory,
                settings = state.settings
            )
        } else {
            CashFlowProjectionChart(data = state.dualTrajectory)
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
                        val c1 = com.example.domain.FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s)
                        if (c1 > 0) {
                            ExpenseItem("Child 1 Expenses (Age ${s.baseYear - s.child1BirthYear})", c1)
                        }
                    }
                    if (s.child2Enabled) {
                        val c2 = com.example.domain.FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s)
                        if (c2 > 0) {
                            ExpenseItem("Child 2 Expenses (Age ${s.baseYear - s.child2BirthYear})", c2)
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
    highlightColor: androidx.compose.ui.graphics.Color? = null,
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
private fun LedgerSubTab(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity>,
    onAddClick: () -> Unit,
    onDuplicateEntry: (LedgerEntryEntity) -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportCsv(it) }
    }

    val sortedEntries = remember(entries) {
        entries.sortedByDescending { it.yearMonth }
    }
    val latestEntry = remember(sortedEntries) {
        sortedEntries.firstOrNull()
    }

    // Interactive Month Carousel Selection
    var selectedYm by remember(sortedEntries) {
        mutableStateOf(sortedEntries.firstOrNull()?.yearMonth ?: "")
    }

    val activeEntry = remember(selectedYm, sortedEntries) {
        sortedEntries.find { it.yearMonth == selectedYm } ?: sortedEntries.firstOrNull()
    }

    val baselineExp = state.totalLivingCostMonthly
    val baselineInc = state.currentIncome.totalMonthly
    val baselineSurplus = baselineInc - baselineExp

    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar: Header + Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Monthly Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (sortedEntries.isNotEmpty()) {
                    ColorPill(
                        text = "${sortedEntries.size} MO",
                        color = BrandTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (latestEntry != null) {
                    AssistChip(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDuplicateEntry(latestEntry)
                        },
                        label = { Text("Copy Latest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = BrandTeal.copy(alpha = 0.12f),
                            labelColor = BrandTeal
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("duplicate_latest_button")
                    )
                }
                TextButton(
                    onClick = { launcher.launch("text/comma-separated-values") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("import_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", fontSize = 11.5.sp)
                }
            }
        }

        if (sortedEntries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandTeal.copy(alpha = 0.1f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No monthly records logged yet",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Track your actual salary and living spending vs budget month-by-month to observe real FIRE velocity.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log First Month", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Horizontal Month Pill Selector Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sortedEntries, key = { it.id }) { entry ->
                    val isSelected = entry.yearMonth == (activeEntry?.yearMonth ?: "")
                    val net = (entry.incVaclav + entry.incEleonora + entry.incUnforeseen) - (entry.expRent + entry.expGroceries + entry.expOther)
                    val netColor = if (net >= 0) GoodGreen else BadRed

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedYm = entry.yearMonth
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("month_chip_${entry.yearMonth}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = entry.yearMonth,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = (if (net >= 0) "+" else "") + fmtCompact(net),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else netColor
                                )
                            )
                        }
                    }
                }
            }

            // Active Month Featured Showcase Card
            if (activeEntry != null) {
                ActiveMonthOverviewCard(
                    state = state,
                    entry = activeEntry,
                    baselineInc = baselineInc,
                    baselineExp = baselineExp,
                    baselineSurplus = baselineSurplus,
                    onEdit = { onEditEntry(activeEntry) },
                    onDuplicate = { onDuplicateEntry(activeEntry) },
                    onDelete = { onDelete(activeEntry.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 6 Months Inflows vs Outflows Visualizer
            LedgerChart(
                entries = sortedEntries,
                selectedYm = activeEntry?.yearMonth ?: "",
                onSelectMonth = { ym -> selectedYm = ym },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
        }
    }
}

@Composable
private fun ActiveMonthOverviewCard(
    state: FullCalculationState,
    entry: LedgerEntryEntity,
    baselineInc: Double,
    baselineExp: Double,
    baselineSurplus: Double,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalInc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    val totalExp = entry.expRent + entry.expGroceries + entry.expOther
    val netFlow = totalInc - totalExp
    val savingsRate = if (totalInc > 0) (netFlow.coerceAtLeast(0.0) / totalInc) * 100.0 else 0.0

    val expDiff = totalExp - baselineExp
    val incDiff = totalInc - baselineInc
    val surplusDiff = netFlow - baselineSurplus

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record") },
            text = { Text("Are you sure you want to delete the record for ${entry.yearMonth}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_ledger_card_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row with YearMonth, Edit & Delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPill(
                        text = entry.yearMonth,
                        color = BrandTeal,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp
                    )
                    ColorPill(
                        text = "${savingsRate.toInt()}% SAVED",
                        color = if (savingsRate >= 40) GoodGreen else BrandGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("edit_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Month",
                            tint = BrandTeal,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(32.dp).testTag("duplicate_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp).testTag("delete_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Metric Inflow / Outflow / Net Grid (Single Row, 0 scroll)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incomes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                        Text(
                            text = "Incomes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmtCompact(totalInc),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GoodGreen,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // Expenses
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadRed.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmtCompact(totalExp),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BadRed,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // Net Surplus
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                        Text(
                            text = "Net Savings",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = (if (netFlow >= 0) "+ " else "") + fmtCompact(netFlow),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = netColor,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Variance vs Target Strip (Rent vs Living details)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Rent: ${fmtCompact(entry.expRent)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(
                            text = "Living: ${fmtCompact(entry.expGroceries + entry.expOther)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    // Delta badge vs Budget
                    val surplusBadgeColor = if (surplusDiff >= 0) GoodGreen else BadRed
                    Text(
                        text = "${if (surplusDiff >= 0) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = surplusBadgeColor
                        )
                    )
                }
            }

            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AddLedgerEntryDialog(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity> = emptyList(),
    initialEntry: LedgerEntryEntity? = null,
    duplicateFrom: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, String) -> Unit
) {
    val defaultRent = state.settings.rentMonthly.toInt().toString()
    val baselineLiving = (state.settings.groceriesMonthly + state.settings.cafesMonthly + state.settings.entertainmentMonthly + state.settings.otherDiscretionaryMonthly).toInt().toString()
    val defaultVaclav = state.currentIncome.vaclavNet.toInt().toString()
    val defaultEleonora = state.currentIncome.eleonoraSalary.takeIf { it > 0 }?.toInt()?.toString()
        ?: state.currentIncome.benefit.toInt().toString()

    val sourceEntry = initialEntry ?: duplicateFrom
    val defaultYm = if (duplicateFrom != null) {
        nextYearMonth(duplicateFrom.yearMonth)
    } else if (initialEntry != null) {
        initialEntry.yearMonth
    } else {
        val latest = entries.maxByOrNull { it.yearMonth }
        if (latest != null) nextYearMonth(latest.yearMonth) else nextYearMonth("")
    }

    var ym by remember { mutableStateOf(defaultYm) }
    var incV by remember { mutableStateOf(sourceEntry?.incVaclav?.toInt()?.toString() ?: defaultVaclav) }
    var incE by remember { mutableStateOf(sourceEntry?.incEleonora?.toInt()?.toString() ?: defaultEleonora) }
    var incU by remember { mutableStateOf(sourceEntry?.incUnforeseen?.toInt()?.toString() ?: "0") }
    var expR by remember { mutableStateOf(sourceEntry?.expRent?.toInt()?.toString() ?: defaultRent) }
    var expL by remember { mutableStateOf(sourceEntry?.let { (it.expGroceries + it.expOther).toInt().toString() } ?: baselineLiving) }
    var notes by remember { mutableStateOf(sourceEntry?.notes ?: "") }
    var isRentCustom by remember { mutableStateOf(sourceEntry != null && sourceEntry.expRent > 0 && sourceEntry.expRent != state.settings.rentMonthly) }

    val latestEntry = remember(entries) { entries.maxByOrNull { it.yearMonth } }
    val primaryName = state.settings.primaryName.ifBlank { "Primary Earner" }
    val spouseName = state.settings.spouseName.ifBlank { "Spouse / Partner" }
    val isSingle = state.settings.isSingleHousehold

    // Live calculations
    val valIncV = incV.toDoubleOrNull() ?: 0.0
    val valIncE = if (!isSingle) (incE.toDoubleOrNull() ?: 0.0) else 0.0
    val valIncU = incU.toDoubleOrNull() ?: 0.0
    val totalInflows = valIncV + valIncE + valIncU

    val valExpR = expR.toDoubleOrNull() ?: 0.0
    val valExpL = expL.toDoubleOrNull() ?: 0.0
    val totalExpenses = valExpR + valExpL

    val netFlow = totalInflows - totalExpenses
    val baselineExp = state.totalLivingCostMonthly
    val baselineInc = state.currentIncome.totalMonthly
    val baselineSurplus = baselineInc - baselineExp
    val surplusDiff = netFlow - baselineSurplus

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (initialEntry != null) "Edit Record"
                        else if (duplicateFrom != null) "Duplicate Record"
                        else "New Monthly Record",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Actual earnings & spending",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    )
                }
                ColorPill(
                    text = ym.ifBlank { "LEDGER" },
                    color = BrandTeal,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 8.dp,
                    verticalPadding = 3.dp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Period Selector & Quick Presets Strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { ym = prevYearMonth(ym) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous Month",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = ym,
                                    onValueChange = { ym = it },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .width(110.dp)
                                        .testTag("ledger_input_ym")
                                )

                                IconButton(
                                    onClick = { ym = nextYearMonth(ym) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next Month",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Presets in the same strip
                            if (initialEntry == null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    if (latestEntry != null) {
                                        AssistChip(
                                            onClick = {
                                                ym = nextYearMonth(latestEntry.yearMonth)
                                                incV = latestEntry.incVaclav.toInt().toString()
                                                incE = latestEntry.incEleonora.toInt().toString()
                                                incU = latestEntry.incUnforeseen.toInt().toString()
                                                expR = latestEntry.expRent.toInt().toString()
                                                expL = (latestEntry.expGroceries + latestEntry.expOther).toInt().toString()
                                                notes = latestEntry.notes
                                            },
                                            label = { Text("Copy ${latestEntry.yearMonth}", fontSize = 10.5.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = BrandTeal.copy(alpha = 0.12f),
                                                labelColor = BrandTeal
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    AssistChip(
                                        onClick = {
                                            incV = defaultVaclav
                                            incE = defaultEleonora
                                            incU = "0"
                                            expR = defaultRent
                                            expL = baselineLiving
                                            isRentCustom = false
                                        },
                                        label = { Text("Budget", fontSize = 10.5.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    AssistChip(
                                        onClick = {
                                            incV = "0"
                                            incE = "0"
                                            incU = "0"
                                            expR = "0"
                                            expL = "0"
                                            isRentCustom = true
                                            notes = ""
                                        },
                                        label = { Text("Clear", fontSize = 10.5.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Incomes Group (Compact)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoodGreen.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Incomes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "+ " + fmtCZK(totalInflows),
                                color = GoodGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        if (!isSingle) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = incV,
                                    onValueChange = { incV = it },
                                    label = { Text(primaryName, maxLines = 1) },
                                    suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ledger_input_inc_v")
                                )
                                OutlinedTextField(
                                    value = incE,
                                    onValueChange = { incE = it },
                                    label = { Text(spouseName, maxLines = 1) },
                                    suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ledger_input_inc_e")
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = incV,
                                onValueChange = { incV = it },
                                label = { Text("$primaryName Net Income") },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_input_inc_v")
                            )
                        }

                        OutlinedTextField(
                            value = incU,
                            onValueChange = { incU = it },
                            label = { Text("Other Inflows / Bonuses (optional)") },
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. Living Expenses Group (Compact)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BadRed.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Expenses",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "- " + fmtCZK(totalExpenses),
                                color = BadRed,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        // Variable Living Expenses (Groceries & Lifestyle)
                        OutlinedTextField(
                            value = expL,
                            onValueChange = { expL = it },
                            label = { Text("Variable Living Expenses (Groceries, Dining, Bills)") },
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ledger_input_exp_living")
                        )

                        // Fixed Housing / Rent (Ultra compact row)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRentCustom) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isRentCustom) BrandGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Housing & Rent: ${fmtCZK(valExpR)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        )
                                        if (!isRentCustom) {
                                            Text(
                                                text = "(Fixed)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            if (isRentCustom) {
                                                expR = defaultRent
                                                isRentCustom = false
                                            } else {
                                                isRentCustom = true
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = if (isRentCustom) "Reset" else "Edit",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = BrandTeal,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                if (isRentCustom) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = expR,
                                        onValueChange = { expR = it },
                                        label = { Text("Rent Override") },
                                        suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ledger_input_exp_rent")
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Live Net Flow Strip (Single-line)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Net Cash Flow:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            ColorPill(
                                text = (if (netFlow >= 0) "+ " else "") + fmtCZK(netFlow),
                                color = if (netFlow >= 0) GoodGreen else BadRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        Text(
                            text = "${if (surplusDiff >= 0) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (surplusDiff >= 0) BrandTeal else BadRed
                            )
                        )
                    }
                }

                // 5. Notes / Comments Field (Compact)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Memos (optional)") },
                    placeholder = { Text("e.g. Travel, bonus, car service...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ym,
                        incV.toDoubleOrNull() ?: 0.0,
                        incE.toDoubleOrNull() ?: 0.0,
                        incU.toDoubleOrNull() ?: 0.0,
                        expR.toDoubleOrNull() ?: 0.0,
                        expL.toDoubleOrNull() ?: 0.0,
                        notes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_ledger_entry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (initialEntry != null) "Update Record" else "Save Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", fontSize = 13.sp)
            }
        }
    )
}

@Composable
fun LedgerChart(
    entries: List<LedgerEntryEntity>,
    selectedYm: String = "",
    onSelectMonth: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val sorted = entries.sortedBy { it.yearMonth }.takeLast(6)
    val maxVal = sorted.maxOf { maxOf(it.incVaclav + it.incEleonora + it.incUnforeseen, it.expRent + it.expGroceries + it.expOther) }.coerceAtLeast(100.0)

    val incColor = GoodGreen
    val expColor = BadRed
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "6-Month Trend",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    ColorPill(
                        text = "FLOW",
                        color = BrandTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 5.dp,
                        verticalPadding = 2.dp
                    )
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(GoodGreen, CircleShape))
                        Text("Incomes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(BadRed, CircleShape))
                        Text("Expenses", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                sorted.forEach { entry ->
                    val isSelected = entry.yearMonth == selectedYm
                    val inc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
                    val exp = entry.expRent + entry.expGroceries + entry.expOther
                    val incRatio = (inc / maxVal).toFloat().coerceIn(0.05f, 1f)
                    val expRatio = (exp / maxVal).toFloat().coerceIn(0.05f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) BrandTeal.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMonth(entry.yearMonth)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(incRatio)
                                    .background(
                                        incColor.copy(alpha = if (isSelected || selectedYm.isEmpty()) 1f else 0.45f),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(expRatio)
                                    .background(
                                        expColor.copy(alpha = if (isSelected || selectedYm.isEmpty()) 1f else 0.45f),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.yearMonth.takeLast(2),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetAndIncomesSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onShowInfo: (MetricInfo) -> Unit = {}
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
            0 -> SummarySubTab(state = state, ledgerEntries = ledgerEntries, onShowInfo = onShowInfo)
            1 -> IncomeSubTab(state = state, onShowInfo = onShowInfo)
            2 -> SpendingSubTab(state = state, onShowInfo = onShowInfo)
        }
    }
}

@Composable
private fun SummarySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val availableMonths = remember(ledgerEntries) {
        listOf("Current Baseline") + ledgerEntries.sortedByDescending { it.yearMonth }.map { it.yearMonth }
    }
    var selectedMonth by remember { mutableStateOf("Current Baseline") }

    val entry = remember(selectedMonth, ledgerEntries) {
        if (selectedMonth == "Current Baseline") null
        else ledgerEntries.find { it.yearMonth == selectedMonth }
    }

    val totalInc = if (entry != null) {
        entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    } else {
        state.currentIncome.totalMonthly
    }

    val totalExp = if (entry != null) {
        entry.expRent + entry.expGroceries + entry.expOther
    } else {
        state.totalLivingCostMonthly
    }

    val netFlow = totalInc - totalExp
    val savingsRatePct = if (totalInc > 0) (netFlow / totalInc) * 100.0 else 0.0
    val investmentsMonthly = state.investMonthlyTotal
    val unallocatedSurplus = (netFlow - investmentsMonthly).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Month Selector Chips
        if (availableMonths.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Period:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableMonths.forEach { month ->
                        val isSelected = month == selectedMonth
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMonth = month }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = month,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }

        // 1. Key Metrics Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = if (selectedMonth == "Current Baseline") "Monthly Cash Flow Summary" else "Summary for $selectedMonth",
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
                    investments = investmentsMonthly,
                    unallocated = unallocatedSurplus
                )
            }
        }

        // Monthly Difference / Variance vs Baseline Budget Indicator Card (when viewing a specific recorded month)
        if (entry != null) {
            val baselineInc = state.currentIncome.totalMonthly
            val baselineExp = state.totalLivingCostMonthly
            val baselineSurplus = baselineInc - baselineExp
            val incDiff = totalInc - baselineInc
            val expDiff = totalExp - baselineExp
            val surplusDiff = netFlow - baselineSurplus

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CardHeaderPill(
                        title = "Variance vs Baseline Budget",
                        subtitle = "Performance deviation against baseline target",
                        badgeText = if (surplusDiff >= 0) "+${fmtCompact(surplusDiff)} Net" else "${fmtCompact(surplusDiff)} Net",
                        accentColor = if (surplusDiff >= 0) GoodGreen else MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStatBox(
                            label = "Income Variance",
                            value = "${if (incDiff >= 0) "+" else ""}${fmtCZK(incDiff)}",
                            valueColor = if (incDiff >= 0) GoodGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricStatBox(
                            label = "Expense Variance",
                            value = "${if (expDiff > 0) "+" else ""}${fmtCZK(expDiff)}",
                            valueColor = if (expDiff <= 0) GoodGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricStatBox(
                            label = "Surplus Variance",
                            value = "${if (surplusDiff >= 0) "+" else ""}${fmtCZK(surplusDiff)}",
                            valueColor = if (surplusDiff >= 0) BrandTeal else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (surplusDiff >= 0) {
                            "In $selectedMonth, you saved ${fmtCZK(abs(expDiff))} ${if (expDiff <= 0) "more" else "less"} on expenses and had a net surplus ${fmtCZK(surplusDiff)} above plan."
                        } else {
                            "In $selectedMonth, net cash surplus was ${fmtCZK(abs(surplusDiff))} below your baseline target."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Income Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Income Breakdown",
                    subtitle = "Monthly take-home earnings & cash inflows",
                    badgeText = "INCOME",
                    accentColor = GoodGreen
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (entry != null) {
                    IncomeRow(label = "Václav Net Income", value = fmtCZK(entry.incVaclav))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora Net Income", value = fmtCZK(entry.incEleonora))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Other / Unforeseen Income", value = fmtCZK(entry.incUnforeseen))
                } else {
                    val inc = state.currentIncome
                    IncomeRow(label = "Václav's Net Salary", value = fmtCZK(inc.vaclavNet))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    if (inc.eleonoraSalary > 0.0) {
                        IncomeRow(label = "Eleonora's Net Salary", value = fmtCZK(inc.eleonoraSalary))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (inc.benefit > 0.0) {
                        IncomeRow(label = "Eleonora's Parental Allowance", value = fmtCZK(inc.benefit))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (inc.lecturing > 0.0) {
                        IncomeRow(label = "Eleonora's Lecturing", value = fmtCZK(inc.lecturing))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    IncomeRow(label = "Meal Vouchers (Václav)", value = fmtCZK(inc.vouchers))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Family Support Gift", value = fmtCZK(inc.gift))
                    if (state.settings.vOtherInflowsMonthly > 0.0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        IncomeRow(label = "Václav's Other Inflows", value = fmtCZK(state.settings.vOtherInflowsMonthly))
                    }
                    if (!state.settings.isSingleHousehold && state.settings.eOtherInflowsMonthly > 0.0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        IncomeRow(label = "Eleonora's Other Inflows", value = fmtCZK(state.settings.eOtherInflowsMonthly))
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL INCOME",
                    value = fmtCZK(totalInc),
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Living Expenses Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Living Expenses Breakdown",
                    subtitle = "Monthly necessities, lifestyle & child allocations",
                    badgeText = "EXPENSES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (entry != null) {
                    ExpenseItem("Housing & Rent (Fixed)", entry.expRent)
                    ExpenseItem("Variable Living Expenses (Groceries & Lifestyle)", entry.expGroceries + entry.expOther)
                } else {
                    val s = state.settings
                    val deletedSet = com.example.domain.parseDeletedCategories(s.deletedCategoriesJson)
                    if (!deletedSet.contains("rent")) ExpenseItem("Rent / Housing", s.rentMonthly)
                    if (!deletedSet.contains("groceries")) ExpenseItem("Groceries & Daily Living", s.groceriesMonthly)
                    if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                        ExpenseItem("Other Discretionary", s.otherDiscretionaryMonthly)
                    }
                    if (!deletedSet.contains("cafes")) ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                    if (!deletedSet.contains("therapy")) ExpenseItem("Therapy / Physio", s.therapyMonthly)
                    if (!deletedSet.contains("charity")) ExpenseItem("Charity", s.charityMonthly)
                    if (!deletedSet.contains("entertainment")) ExpenseItem("Entertainment", s.entertainmentMonthly)
                    if (!deletedSet.contains("transport")) ExpenseItem("Transport", s.transportMonthly)
                    if (!deletedSet.contains("subscriptions")) ExpenseItem("Subscriptions", s.subscriptionsMonthly)

                    if (s.childExpensesEnabled) {
                        if (s.child1Enabled) {
                            val c1 = com.example.domain.FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s)
                            if (c1 > 0) ExpenseItem("Child 1 Expenses (Age ${s.baseYear - s.child1BirthYear})", c1)
                        }
                        if (s.child2Enabled) {
                            val c2 = com.example.domain.FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s)
                            if (c2 > 0) ExpenseItem("Child 2 Expenses (Age ${s.baseYear - s.child2BirthYear})", c2)
                        }
                    }

                    val customCategories = com.example.domain.parseCustomExpenses(s.customExpensesJson)
                    customCategories.forEach { customItem ->
                        ExpenseItem(customItem.name, customItem.amount)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                ExpenseItem("TOTAL LIVING EXPENSES", totalExp, isBold = true)
            }
        }

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

                val s = state.settings
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

@Composable
private fun MetricStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    softWrap = true
                )
                if (info != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
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
    color: androidx.compose.ui.graphics.Color,
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
