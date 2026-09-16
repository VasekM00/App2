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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.example.ui.components.StatementImportReviewDialog
import com.example.ui.components.YoYRetrospectiveCard
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandBlue
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
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
import com.example.ui.components.DcaAllocationBreakdownBar
import kotlin.math.min

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
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String, Double, Double, Double) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    pendingStatementImport: com.example.util.StatementParseSummary? = null,
    onConfirmStatementImport: (com.example.util.StatementParseSummary) -> Unit = {},
    onDismissStatementImport: () -> Unit = {},
    onUpdateTransactionCategory: ((Int, com.example.util.BankTransactionType, Boolean) -> Unit)? = null,
    activeAuditReport: com.example.util.CrossStatementAuditReport? = null,
    onShowAuditReport: ((String) -> Unit)? = null,
    onDismissAuditReport: () -> Unit = {},
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    lastImportTimestamp: Long? = null,
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
    initialSubTab: Int = 0,
    onUpdateSettings: (SettingsEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportCsv(it) }
    }

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
                    allImportedTransactions = allImportedTransactions,
                    onShowInfo = { infoState.show(it) },
                    onUpdateSettings = onUpdateSettings
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
                    onTriggerImportCsv = { csvLauncher.launch("*/*") },
                    onShowInfo = { infoState.show(it) },
                    onShowAuditReport = onShowAuditReport,
                    importedBankSourcesByMonth = importedBankSourcesByMonth,
                    lastImportTimestamp = lastImportTimestamp
                )
            }
        }

        if (selectedSubTab == 1 && ledgerEntries.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    duplicateFromEntry = null
                    showAddDialog = true
                },
                containerColor = BrandTeal,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 22.dp)
                    .size(52.dp)
                    .testTag("fab_add_ledger_entry")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Entry",
                    modifier = Modifier.size(24.dp)
                )
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
            onTriggerImportCsv = { csvLauncher.launch("*/*") },
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve)
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
            onTriggerImportCsv = { csvLauncher.launch("*/*") },
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                val prevTotalLiving = entry.expGroceries + entry.expOther
                val (newGroceries, newOther) = if (entry.expOther > 0.0 && prevTotalLiving > 0.0) {
                    val otherRatio = (entry.expOther / prevTotalLiving).coerceIn(0.0, 1.0)
                    val calculatedOther = kotlin.math.round(expL * otherRatio)
                    (expL - calculatedOther) to calculatedOther
                } else {
                    expL to 0.0
                }
                onUpdateLedgerEntry(
                    entry.copy(
                        yearMonth = ym,
                        incVaclav = incV,
                        incEleonora = incE,
                        incUnforeseen = incU,
                        expRent = expR,
                        expGroceries = newGroceries,
                        expOther = newOther,
                        notes = notes,
                        portfolioBalanceAtMonthEnd = balPortu,
                        pensionBalanceAtMonthEnd = balPension,
                        emergencyReserveAtMonthEnd = balReserve
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

    pendingStatementImport?.let { summary ->
        StatementImportReviewDialog(
            summary = summary,
            onConfirm = onConfirmStatementImport,
            onDismiss = onDismissStatementImport,
            onShowNettingAudit = {
                onShowAuditReport?.invoke(summary.yearMonth)
            },
            onUpdateTransactionCategory = onUpdateTransactionCategory
        )
    }

    activeAuditReport?.let { report ->
        com.example.ui.components.CrossAccountNettingAuditDialog(
            auditReport = report,
            onDismiss = onDismissAuditReport
        )
    }
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
        formulaOrRule = "§ 15a ZDP · Up to 48 000 CZK annual tax deduction",
        explanation = "Czech long-term investment product enabling investments into index ETFs with pre-tax income. Combined 48 000 CZK annual ceiling with DPS provides 7 200 CZK (at 15% rate) or 11 040 CZK (at 23% rate) in direct annual tax savings.",
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

    fun ledgerIncomes(entry: LedgerEntryEntity, baselineInc: Double) = MetricInfo(
        title = "Monthly Inflows · ${entry.yearMonth}",
        category = "Actual Bank Record",
        formulaOrRule = "Václav (${fmtCZK(entry.incVaclav)}) + Eleonora (${fmtCZK(entry.incEleonora)}) + Other (${fmtCZK(entry.incUnforeseen)})",
        explanation = "Total liquid cash deposited into your household accounts during ${entry.yearMonth}. Inflows reflect net take-home pay after Czech income taxes (15%/23%), social security (7.1%), and health insurance (4.5%).",
        practicalImplication = if (entry.incVaclav + entry.incEleonora + entry.incUnforeseen >= baselineInc)
            "Inflows exceeded your standard monthly budget baseline (${fmtCZK(baselineInc)}) by ${fmtCZK((entry.incVaclav + entry.incEleonora + entry.incUnforeseen) - baselineInc)}."
        else
            "Inflows were ${fmtCZK(baselineInc - (entry.incVaclav + entry.incEleonora + entry.incUnforeseen))} below your baseline budget (${fmtCZK(baselineInc)}).",
        accentColor = Color(0xFF16A34A)
    )

    fun ledgerExpenses(entry: LedgerEntryEntity, baselineExp: Double) = MetricInfo(
        title = "Monthly Living Expenses · ${entry.yearMonth}",
        category = "Actual Bank Record",
        formulaOrRule = "Rent (${fmtCZK(entry.expRent)}) + Groceries (${fmtCZK(entry.expGroceries)}) + Other Living (${fmtCZK(entry.expOther)})",
        explanation = "Combined operational burn rate logged for ${entry.yearMonth}. Groceries and day-to-day spending are tracked separately from housing rent to highlight discretionary lifestyle inflation.",
        practicalImplication = if (entry.expRent + entry.expGroceries + entry.expOther <= baselineExp)
            "Total spending remained ${fmtCZK(baselineExp - (entry.expRent + entry.expGroceries + entry.expOther))} below your baseline plan (${fmtCZK(baselineExp)}), creating extra investment capacity."
        else
            "Spending exceeded your baseline living plan (${fmtCZK(baselineExp)}) by ${fmtCZK((entry.expRent + entry.expGroceries + entry.expOther) - baselineExp)}.",
        accentColor = Color(0xFFDC2626)
    )

    fun ledgerNetFlow(entry: LedgerEntryEntity, totalInc: Double, totalExp: Double, netFlow: Double, savingsRate: Double) = MetricInfo(
        title = "Net Cash Flow · ${entry.yearMonth}",
        category = "Actual Capital Generation",
        formulaOrRule = "Total Inflows (${fmtCZK(totalInc)}) - Living Expenses (${fmtCZK(totalExp)}) = ${fmtCZK(netFlow)}",
        explanation = "Net uncommitted liquidity produced by the household in ${entry.yearMonth}. Your savings rate for this month was ${savingsRate.toInt()}%.",
        practicalImplication = "This cash surplus funds your automated ETF DCA allocations (Portu, DIP, DPS) and expands your liquid emergency reserve.",
        accentColor = Color(0xFF0F766E)
    )

    fun ledgerBudgetVariance(diff: Double, baselineSurplus: Double, actualNet: Double) = MetricInfo(
        title = "vs Baseline Budget Performance",
        category = "Monthly Variance Analysis",
        formulaOrRule = "Actual Net (${fmtCZK(actualNet)}) - Baseline Plan Net (${fmtCZK(baselineSurplus)}) = ${if (diff >= 0) "+" else ""}${fmtCZK(diff)}",
        explanation = "Measures how much more (or less) uncommitted capital your household generated this month compared to your planned baseline financial budget.",
        practicalImplication = if (diff >= 0)
            "Positive budget outperformance. You generated ${fmtCZK(diff)} more capital than planned, accelerating your FIRE timeline."
        else
            "Negative variance of ${fmtCZK(-diff)}. Review discretionary lifestyle spending or unexpected expenses to re-align with budget.",
        accentColor = if (diff >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
    )

    fun ledgerMonthEndWealth(entry: LedgerEntryEntity) = MetricInfo(
        title = "Month-End Wealth Snapshot · ${entry.yearMonth}",
        category = "Net Worth Point-in-Time",
        formulaOrRule = "Reserve (${fmtCZK(entry.emergencyReserveAtMonthEnd)}) + Portu (${fmtCZK(entry.portfolioBalanceAtMonthEnd)}) + Pension (${fmtCZK(entry.pensionBalanceAtMonthEnd)})",
        explanation = "Your point-in-time closing balance of liquid safety reserves and tracked investment portfolios as of the end of ${entry.yearMonth}.",
        practicalImplication = "Tracking your month-end cash reserve ensures your emergency runway (6+ months) remains protected as market values fluctuate.",
        accentColor = Color(0xFFD97706)
    )

    fun ledgerBankImport(entry: LedgerEntryEntity) = MetricInfo(
        title = "Bank Statement Source & Provenance",
        category = "Automated Import Record",
        formulaOrRule = entry.notes.ifBlank { "Direct Bank Statement Import" },
        explanation = "This month's figures were automatically populated from your bank statement. Inflows, grocery purchases, and retirement contributions were categorized, and internal transfers between Moneta, ČSOB, and mBank were netted out to prevent double counting.",
        practicalImplication = "The closing accounting balance was automatically stored as your liquid emergency reserve for this month.",
        accentColor = Color(0xFF0F766E)
    )
}

@Composable
private fun IncomeSubTab(
    state: FullCalculationState,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome
    val s = state.settings

    var showEditSalaryDialog by remember { mutableStateOf(false) }
    var editedSalaryText by remember { mutableStateOf(s.vSalary.toInt().toString()) }

    val now = remember { java.util.Calendar.getInstance() }
    val currentYear = now.get(java.util.Calendar.YEAR)
    val currentMonth = now.get(java.util.Calendar.MONTH) + 1
    val scheduledLumpSums = remember(s.customLumpSumsJson) {
        FinancialEngine.lumpSumsForMonth(currentYear, currentMonth, s)
    }

    if (showEditSalaryDialog && onUpdateSettings != null) {
        AlertDialog(
            onDismissRequest = { showEditSalaryDialog = false },
            title = { Text("Edit Václav's Net Salary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Update baseline monthly net take-home salary. This recalculates projections, savings rate, and cash flows across the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedSalaryText,
                        onValueChange = { editedSalaryText = it },
                        label = { Text("Monthly Net Salary (CZK)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = editedSalaryText.replace(',', '.').trim()
                        val amt = sanitized.toDoubleOrNull() ?: s.vSalary
                        if (amt >= 0.0) {
                            onUpdateSettings(s.copy(vSalary = amt))
                        }
                        showEditSalaryDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSalaryDialog = false }) {
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
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (onShowInfo != null) Modifier.infoTapHold(CashFlowMetricInfos.vaclavSalary, onShowInfo)
                                else Modifier
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Václav's Net Salary",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info on Václav's Net Salary",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fmtCZK(s.vSalary),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                        if (onUpdateSettings != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    editedSalaryText = s.vSalary.toInt().toString()
                                    showEditSalaryDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Václav's Salary",
                                    tint = BrandTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
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
                if (s.vOtherInflowsMonthly > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Václav's Other Inflows", value = fmtCZK(s.vOtherInflowsMonthly))
                }
                if (s.eOtherInflowsMonthly > 0.0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora's Other Inflows", value = fmtCZK(s.eOtherInflowsMonthly))
                }

                if (scheduledLumpSums.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    for (item in scheduledLumpSums) {
                        IncomeRow(
                            label = "${item.name} (${currentMonth}/$currentYear Bonus)",
                            value = fmtCZK(item.amount),
                            isBold = false
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
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
private fun bankBadgeColor(bankName: String): Color = when {
    bankName.contains("Moneta", ignoreCase = true) -> BrandTeal
    bankName.contains("ČSOB", ignoreCase = true) || bankName.contains("CSOB", ignoreCase = true) -> BrandGold
    bankName.contains("mBank", ignoreCase = true) -> BrandBlue
    bankName.contains("Sporitelna", ignoreCase = true) -> Color(0xFF00539B)
    bankName.contains("Komercni", ignoreCase = true) -> Color(0xFFB0231E)
    bankName.contains("Fio", ignoreCase = true) -> Color(0xFF1E5AA8)
    bankName.contains("Raiffeisen", ignoreCase = true) -> Color(0xFFFFC400)
    bankName.contains("Air Bank", ignoreCase = true) -> Color(0xFF73B827)
    bankName.contains("UniCredit", ignoreCase = true) -> Color(0xFFE4002B)
    bankName.contains("Creditas", ignoreCase = true) -> Color(0xFF00695C)
    bankName.contains("Revolut", ignoreCase = true) -> Color(0xFF7B1FA2)
    bankName.contains("Wise", ignoreCase = true) -> Color(0xFF7BB661)
    bankName.contains("GENERIC", ignoreCase = true) -> Color(0xFF607D8B)
    else -> BrandTeal
}

@Composable
private fun LedgerSubTab(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity>,
    onAddClick: () -> Unit,
    onDuplicateEntry: (LedgerEntryEntity) -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onTriggerImportCsv: () -> Unit,
    onShowInfo: (MetricInfo) -> Unit = {},
    onShowAuditReport: ((String) -> Unit)? = null,
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    lastImportTimestamp: Long? = null
) {
    val sortedEntries = remember(entries) {
        entries.sortedByDescending { it.yearMonth }
    }
    val latestEntry = remember(sortedEntries) {
        sortedEntries.firstOrNull()
    }

    val daysSinceLastImport = remember(lastImportTimestamp, entries) {
        val ts = lastImportTimestamp ?: run {
            val latest = entries.maxOfOrNull { it.yearMonth }
            if (latest != null) {
                try {
                    val ym = java.time.YearMonth.parse(latest)
                    val endOfMonth = ym.atEndOfMonth().atTime(23, 59)
                    val zone = java.time.ZoneId.systemDefault()
                    endOfMonth.atZone(zone).toInstant().toEpochMilli()
                } catch (e: Exception) { null }
            } else null
        }
        if (ts != null && ts > 0L) {
            val diffMs = System.currentTimeMillis() - ts
            (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else null
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "Monthly Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                FilledTonalButton(
                    onClick = onTriggerImportCsv,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandTeal.copy(alpha = 0.15f),
                        contentColor = BrandTeal
                    ),
                    modifier = Modifier.testTag("import_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Import Statement",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Import",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (latestEntry != null) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDuplicateEntry(latestEntry)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("duplicate_latest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Latest",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Copy",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onAddClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandTeal.copy(alpha = 0.12f),
                        contentColor = BrandTeal
                    ),
                    modifier = Modifier.testTag("toolbar_add_entry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Entry",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "New",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (daysSinceLastImport != null && daysSinceLastImport >= 25) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BrandGold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onTriggerImportCsv() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(BrandGold, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Last statement import: $daysSinceLastImport days ago",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Import fresh",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandGold
                        )
                    )
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            if (sortedEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandTeal.copy(alpha = 0.1f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No monthly records logged yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Track your actual salary and living spending vs budget month-by-month to observe real FIRE velocity.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons Side-by-Side
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onAddClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            modifier = Modifier.testTag("log_first_month_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = onTriggerImportCsv,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, BrandTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTeal),
                            modifier = Modifier.testTag("empty_state_import_csv_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Statement", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Supported Bank Statement Formats Guide Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Direct Bank Statement Import",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            Text(
                                text = "Import official monthly PDF or CSV statements from any Czech bank — Moneta, ČSOB, mBank, Česká spořitelna, Komerční banka, Fio, Air Bank, Raiffeisenbank, UniCredit, Creditas, Revolut and more:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandTeal.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Moneta", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandTeal)
                                        Text("Václav", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandGold.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ČSOB", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandGold)
                                        Text("Eleonora", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("mBank", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Shared", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Text(
                                text = "Internal transfers between your own accounts (any bank) are automatically matched and netted out to prevent double counting.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
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
                                val chipBanks = importedBankSourcesByMonth[entry.yearMonth] ?: emptySet()
                                if (chipBanks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        chipBanks.sorted().forEach { bank ->
                                            val badgeText = when {
                                                bank.contains("MONETA", ignoreCase = true) -> "MON"
                                                bank.contains("CSOB", ignoreCase = true) || bank.contains("ČSOB", ignoreCase = true) -> "ČSOB"
                                                bank.contains("MBANK", ignoreCase = true) -> "mB"
                                                bank.contains("CESKA_SPORITELNA", ignoreCase = true) -> "ČS"
                                                bank.contains("KOMERCNI_BANKA", ignoreCase = true) -> "KB"
                                                bank.contains("FIO", ignoreCase = true) -> "Fio"
                                                bank.contains("RAIFFEISENBANK", ignoreCase = true) -> "RB"
                                                bank.contains("AIR_BANK", ignoreCase = true) -> "Air"
                                                bank.contains("UNICREDIT", ignoreCase = true) -> "UC"
                                                bank.contains("CREDITAS", ignoreCase = true) -> "CR"
                                                bank.contains("REVOLUT", ignoreCase = true) -> "Rev"
                                                bank.contains("WISE", ignoreCase = true) -> "Wise"
                                                else -> bank.take(3).uppercase()
                                            }
                                            ColorPill(
                                                text = badgeText,
                                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else bankBadgeColor(bank),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                horizontalPadding = 3.dp,
                                                verticalPadding = 0.5.dp
                                            )
                                        }
                                    }
                                }
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
                        onShowInfo = onShowInfo,
                        onShowAudit = { onShowAuditReport?.invoke(activeEntry.yearMonth) },
                        importedBankSourcesByMonth = importedBankSourcesByMonth,
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

                // Year-Over-Year (YoY) Retrospective Card
                YoYRetrospectiveCard(
                    ledgerEntries = sortedEntries,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(110.dp)) // padding for FAB
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    onShowInfo: (MetricInfo) -> Unit = {},
    onShowAudit: (() -> Unit)? = null,
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row with YearMonth, Savings Pill, Bank Badges & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
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
                    val activeBanks = importedBankSourcesByMonth[entry.yearMonth] ?: emptySet()
                    activeBanks.sorted().forEach { bank ->
                        val displayName = when {
                            bank.contains("MONETA", ignoreCase = true) -> "Moneta"
                            bank.contains("CSOB", ignoreCase = true) || bank.contains("ČSOB", ignoreCase = true) -> "ČSOB"
                            bank.contains("MBANK", ignoreCase = true) -> "mBank"
                            bank.contains("CESKA_SPORITELNA", ignoreCase = true) -> "Česká spořitelna"
                            bank.contains("KOMERCNI_BANKA", ignoreCase = true) -> "Komerční banka"
                            bank.contains("FIO", ignoreCase = true) -> "Fio banka"
                            bank.contains("RAIFFEISENBANK", ignoreCase = true) -> "Raiffeisenbank"
                            bank.contains("AIR_BANK", ignoreCase = true) -> "Air Bank"
                            bank.contains("UNICREDIT", ignoreCase = true) -> "UniCredit"
                            bank.contains("CREDITAS", ignoreCase = true) -> "Banka Creditas"
                            bank.contains("REVOLUT", ignoreCase = true) -> "Revolut"
                            bank.contains("WISE", ignoreCase = true) -> "Wise"
                            else -> bank
                        }
                        ColorPill(
                            text = displayName,
                            color = bankBadgeColor(bank),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 5.dp,
                            verticalPadding = 1.5.dp
                        )
                    }
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

            // 3-Metric Inflow / Outflow / Net Grid (Tap-Enabled with Info Modals)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incomes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerIncomes(entry, baselineInc), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inflows",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(totalInc),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GoodGreen,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Expenses
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerExpenses(entry, baselineExp), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expenses",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(totalExp),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BadRed,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Net Cash Flow
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerNetFlow(entry, totalInc, totalExp, netFlow, savingsRate), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Flow",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = (if (netFlow >= 0) "+ " else "") + fmtCompact(netFlow),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = netColor,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Net Cash Flow vs Budget Performance Strip
            val isSurplusPositive = surplusDiff >= 0
            val varianceColor = if (isSurplusPositive) GoodGreen else BadRed
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = varianceColor.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, varianceColor.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .infoTapHold(CashFlowMetricInfos.ledgerBudgetVariance(surplusDiff, baselineSurplus, netFlow), onShowInfo)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSurplusPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = varianceColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "vs Baseline Budget",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Budget Info",
                                tint = varianceColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        ColorPill(
                            text = "${if (isSurplusPositive) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                            color = varianceColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }

                    Text(
                        text = "Rent ${fmtCompact(entry.expRent)} · Living ${fmtCompact(entry.expGroceries + entry.expOther)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // DCA Investment Flow tracking row
            if (entry.notes.contains("Invested:", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                val invFull = entry.notes.substringAfter("Invested: ").substringBefore(" |")
                val invTotalStr = invFull.substringBefore(" (").trim()
                val invBreakdown = invFull.substringAfter("(", "").substringBefore(")")

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Tracked Investments (DCA)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Text(
                                text = invTotalStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandBlue,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        if (invBreakdown.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                invBreakdown.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { part ->
                                    ColorPill(
                                        text = part,
                                        color = BrandBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        horizontalPadding = 5.dp,
                                        verticalPadding = 1.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (entry.totalNetWorthAtMonthEnd > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerMonthEndWealth(entry), onShowInfo)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = BrandGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Month-End Wealth Snapshot",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Wealth Info",
                                    tint = BrandGold.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = fmtCZK(entry.totalNetWorthAtMonthEnd),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandGold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (entry.portfolioBalanceAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Portu: " + fmtCompact(entry.portfolioBalanceAtMonthEnd),
                                    color = BrandGold,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                            if (entry.pensionBalanceAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Pension: " + fmtCompact(entry.pensionBalanceAtMonthEnd),
                                    color = BrandGold,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                            if (entry.emergencyReserveAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Reserve: " + fmtCompact(entry.emergencyReserveAtMonthEnd),
                                    color = BrandTeal,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                        }
                    }
                }
            }

            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val isBankImport = entry.notes.contains("Imported from", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isBankImport) BrandTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isBankImport) BrandTeal.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isBankImport && onShowAudit != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onShowAudit() }
                            } else if (isBankImport) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .infoTapHold(CashFlowMetricInfos.ledgerBankImport(entry), onShowInfo)
                            } else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isBankImport) Icons.Default.AccountBalance else Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                                tint = if (isBankImport) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isBankImport) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBankImport) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isBankImport) {
                            if (onShowAudit != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandTeal.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = "Audit Proof",
                                            tint = BrandTeal,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Audit Proof",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.5.sp,
                                                color = BrandTeal,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Info",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = BrandTeal.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
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
    onTriggerImportCsv: () -> Unit = {},
    onSave: (String, Double, Double, Double, Double, Double, String, Double, Double, Double) -> Unit
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
    var balPortu by remember { mutableStateOf(sourceEntry?.portfolioBalanceAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var balPension by remember { mutableStateOf(sourceEntry?.pensionBalanceAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var balReserve by remember { mutableStateOf(sourceEntry?.emergencyReserveAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }

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
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Period Selector & Quick Presets Strip (Compact)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { ym = prevYearMonth(ym) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            OutlinedTextField(
                                value = ym,
                                onValueChange = { ym = it },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier
                                    .width(88.dp)
                                    .testTag("ledger_input_ym")
                            )
                            IconButton(
                                onClick = { ym = nextYearMonth(ym) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Presets Chips Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (latestEntry != null && initialEntry == null) {
                                AssistChip(
                                    onClick = {
                                        ym = nextYearMonth(latestEntry.yearMonth)
                                        incV = latestEntry.incVaclav.toInt().toString()
                                        incE = latestEntry.incEleonora.toInt().toString()
                                        incU = latestEntry.incUnforeseen.toInt().toString()
                                        expR = latestEntry.expRent.toInt().toString()
                                        expL = (latestEntry.expGroceries + latestEntry.expOther).toInt().toString()
                                        notes = latestEntry.notes
                                        balPortu = latestEntry.portfolioBalanceAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                        balPension = latestEntry.pensionBalanceAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                        balReserve = latestEntry.emergencyReserveAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                    },
                                    label = { Text("Copy", fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = BrandTeal.copy(alpha = 0.12f),
                                        labelColor = BrandTeal
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                            AssistChip(
                                onClick = {
                                    incV = defaultVaclav
                                    incE = defaultEleonora
                                    incU = "0"
                                    expR = defaultRent
                                    expL = baselineLiving
                                },
                                label = { Text("Budget", fontSize = 10.sp) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            )
                            AssistChip(
                                onClick = {
                                    onDismiss()
                                    onTriggerImportCsv()
                                },
                                label = { Text("Import", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = BrandGold.copy(alpha = 0.15f),
                                    labelColor = BrandGold
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            )
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                    label = { Text(primaryName, fontSize = 11.sp) },
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
                                    label = { Text(spouseName, fontSize = 11.sp) },
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
                                label = { Text("$primaryName Net", fontSize = 11.sp) },
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
                            label = { Text("Other Inflows / Bonuses (optional)", fontSize = 11.sp) },
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
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

                        // Expenses Fields: Housing/Rent and Variable Living
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = expR,
                                onValueChange = { expR = it },
                                label = { Text("Rent / Housing", fontSize = 11.sp) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_exp_rent")
                            )
                            OutlinedTextField(
                                value = expL,
                                onValueChange = { expL = it },
                                label = { Text("Variable Living", fontSize = 11.sp) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_exp_living")
                            )
                        }
                    }
                }

                // 4. Live Net Cash Flow & Budget Variance Strip
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                val varianceColor = if (surplusDiff >= 0) GoodGreen else BadRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (netFlow >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = netColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Net Cash Flow",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Text(
                                text = (if (netFlow >= 0) "+ " else "") + fmtCZK(netFlow),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = netColor
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "vs Baseline Budget",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            ColorPill(
                                text = "${if (surplusDiff >= 0) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                                color = varianceColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                    }
                }

                // 5. Month-End Wealth Snapshot (Compact, Unclipped)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val vPortu = balPortu.toDoubleOrNull() ?: 0.0
                        val vPension = balPension.toDoubleOrNull() ?: 0.0
                        val vReserve = balReserve.toDoubleOrNull() ?: 0.0
                        val vTotalNetWorth = vPortu + vPension + vReserve

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Month-End Wealth Snapshot",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                            if (vTotalNetWorth > 0) {
                                ColorPill(
                                    text = fmtCZK(vTotalNetWorth),
                                    color = BrandGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            } else {
                                Text(
                                    text = "Optional",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // 3 Fields Layout:
                        // Row 1: Portu and Reserve side-by-side (labels fit with no ellipses)
                        // Row 2: Pension (DIP + DPS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = balPortu,
                                onValueChange = { balPortu = it },
                                label = { Text("Portu", fontSize = 11.sp) },
                                placeholder = { Text("Broker", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_bal_portu")
                            )
                            OutlinedTextField(
                                value = balReserve,
                                onValueChange = { balReserve = it },
                                label = { Text("Reserve", fontSize = 11.sp) },
                                placeholder = { Text("Cash fund", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_bal_reserve")
                            )
                        }

                        OutlinedTextField(
                            value = balPension,
                            onValueChange = { balPension = it },
                            label = { Text("Pension (DIP + DPS)", fontSize = 11.sp) },
                            placeholder = { Text("Combined total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ledger_input_bal_pension")
                        )
                    }
                }

                // 6. Notes / Comments Field (Compact)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Memos (optional)", fontSize = 11.sp) },
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
                        notes,
                        balPortu.toDoubleOrNull() ?: 0.0,
                        balPension.toDoubleOrNull() ?: 0.0,
                        balReserve.toDoubleOrNull() ?: 0.0
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_ledger_entry_button")
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
            TextButton(onClick = onDismiss) {
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
    val sorted = remember(entries) { entries.sortedBy { it.yearMonth }.takeLast(6) }
    if (sorted.isEmpty()) return

    val maxVal = remember(sorted) {
        sorted.maxOf { maxOf(it.incVaclav + it.incEleonora + it.incUnforeseen, it.expRent + it.expGroceries + it.expOther) }.coerceAtLeast(100.0) * 1.15
    }

    val haptic = LocalHapticFeedback.current
    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textPx = with(LocalDensity.current) { 10.sp.toPx() }
    val textPaint = remember(textPaintColor, textPx) {
        android.graphics.Paint().apply {
            color = textPaintColor
            textSize = textPx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val selectedEntry = remember(sorted, selectedYm) {
        sorted.find { it.yearMonth == selectedYm } ?: sorted.lastOrNull()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header Row with Title & Legend
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
                        color = cTeal,
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
                        Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                        Text("Incomes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(cRed, CircleShape))
                        Text("Expenses", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas
            val paddingHorizontal = 36f
            val paddingTop = 12f
            val paddingBottom = 38f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sorted) {
                            detectTapGestures { offset ->
                                val plotW = size.width - (paddingHorizontal * 2)
                                val stepX = if (sorted.size > 1) plotW / (sorted.size - 1) else plotW
                                val clickX = (offset.x - paddingHorizontal).coerceAtLeast(0f)
                                val tappedIdx = if (sorted.size > 1) {
                                    (clickX / stepX + 0.5f).toInt().coerceIn(0, sorted.size - 1)
                                } else 0
                                val target = sorted[tappedIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMonth(target.yearMonth)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - (paddingHorizontal * 2)
                    val plotH = h - paddingTop - paddingBottom

                    fun yPix(value: Double): Float =
                        paddingTop + (plotH - plotH * (value / maxVal).coerceIn(0.0, 1.0)).toFloat()

                    // Horizontal Grid lines (0%, 50%, 100%)
                    for (i in 0..2) {
                        val y = paddingTop + (plotH * i / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingHorizontal, y),
                            end = Offset(w - paddingHorizontal, y),
                            strokeWidth = 1f
                        )
                    }

                    val stepX = if (sorted.size > 1) plotW / (sorted.size - 1) else plotW

                    // Paths for Income and Expenses
                    val incPath = Path()
                    val expPath = Path()

                    val incPoints = mutableListOf<Offset>()
                    val expPoints = mutableListOf<Offset>()

                    sorted.forEachIndexed { i, entry ->
                        val x = paddingHorizontal + (i * stepX)
                        val inc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
                        val exp = entry.expRent + entry.expGroceries + entry.expOther

                        val yInc = yPix(inc)
                        val yExp = yPix(exp)

                        incPoints.add(Offset(x, yInc))
                        expPoints.add(Offset(x, yExp))

                        if (i == 0) {
                            incPath.moveTo(x, yInc)
                            expPath.moveTo(x, yExp)
                        } else {
                            incPath.lineTo(x, yInc)
                            expPath.lineTo(x, yExp)
                        }
                    }

                    // Draw Selected Month vertical guideline
                    val selectedIdx = sorted.indexOfFirst { it.yearMonth == selectedYm }
                    if (selectedIdx >= 0) {
                        val sx = paddingHorizontal + (selectedIdx * stepX)
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, paddingTop),
                            end = Offset(sx, paddingTop + plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // Draw Income line
                    drawPath(
                        path = incPath,
                        color = cGreen,
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )

                    // Draw Expense line
                    drawPath(
                        path = expPath,
                        color = cRed,
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )

                    // Draw points and month labels
                    sorted.forEachIndexed { i, entry ->
                        val ptInc = incPoints[i]
                        val ptExp = expPoints[i]
                        val isSel = entry.yearMonth == selectedYm

                        // Income dot
                        drawCircle(color = cGreen, radius = if (isSel) 6f else 4f, center = ptInc)
                        drawCircle(color = Color.White, radius = if (isSel) 3f else 2f, center = ptInc)

                        // Expense dot
                        drawCircle(color = cRed, radius = if (isSel) 6f else 4f, center = ptExp)
                        drawCircle(color = Color.White, radius = if (isSel) 3f else 2f, center = ptExp)

                        // X-axis Month label
                        val parts = entry.yearMonth.split("-")
                        val monthLabel = if (parts.size == 2) {
                            val mNum = parts[1].toIntOrNull() ?: 1
                            val mNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                            if (mNum in 1..12) "${mNames[mNum - 1]} '${parts[0].takeLast(2)}" else parts[1]
                        } else entry.yearMonth.takeLast(2)

                        drawContext.canvas.nativeCanvas.drawText(
                            monthLabel,
                            ptInc.x,
                            paddingTop + plotH + 24f,
                            textPaint
                        )
                    }
                }
            }

            // Interactive Month Snapshot bar
            if (selectedEntry != null) {
                val inc = selectedEntry.incVaclav + selectedEntry.incEleonora + selectedEntry.incUnforeseen
                val exp = selectedEntry.expRent + selectedEntry.expGroceries + selectedEntry.expOther
                val net = inc - exp
                val isPositive = net >= 0

                val parts = selectedEntry.yearMonth.split("-")
                val mLabel = if (parts.size == 2) {
                    val mNum = parts[1].toIntOrNull() ?: 1
                    val mNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    if (mNum in 1..12) "${mNames[mNum - 1]} ${parts[0]}" else selectedEntry.yearMonth
                } else selectedEntry.yearMonth

                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "In: +${fmtCompact(inc)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = GoodGreen, fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Out: -${fmtCompact(exp)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = BadRed, fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Net: ${if (isPositive) "+" else ""}${fmtCompact(net)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isPositive) GoodGreen else BadRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
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
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
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
                onShowInfo = onShowInfo
            )
            1 -> IncomeSubTab(state = state, onShowInfo = onShowInfo, onUpdateSettings = onUpdateSettings)
            2 -> SpendingSubTab(state = state, onShowInfo = onShowInfo)
        }
    }
}

@Composable
private fun SummarySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
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
            swrPct = state.settings.safeWithdrawalRatePct
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
