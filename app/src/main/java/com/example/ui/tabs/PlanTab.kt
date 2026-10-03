package com.example.ui.tabs

import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
import com.example.domain.parseCustomLifeGoals
import com.example.domain.serializeCustomLifeGoals
import com.example.ui.components.ActionChecklistCard
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.DipOptimizationMatrixCard
import com.example.ui.components.DpsOptimizationMatrixCard
import com.example.ui.components.FundsAllocatorCard
import com.example.ui.components.TwoBucketLiquidityBridgeCard
import com.example.ui.components.KpiCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.components.infoTapHold
import androidx.compose.material.icons.filled.Info
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import com.example.util.Formatters.roundTo10k
import com.example.util.Formatters.roundTo1k

private object PlanMetricInfos {
    val coastFire = MetricInfo(
        title = "Coast FIRE Milestone",
        category = "Organic Compounding",
        formulaOrRule = "Target / (1 + Real CAGR)^Years_to_Pension",
        explanation = "The amount of invested capital needed today such that, with zero additional contributions, it will compound into your full retirement nest egg by statutory state pension age.",
        practicalImplication = "Reaching Coast FIRE eliminates survival employment pressure; you only need to earn enough to cover current living burn.",
        accentColor = Color(0xFF0F766E)
    )

    val baristaFire = MetricInfo(
        title = "Barista FIRE Milestone",
        category = "Partial Independence",
        formulaOrRule = "(50% Baseline Living Costs) / SWR",
        explanation = "Financial milestone where private portfolio passive returns cover 50% of current household living expenses, with the remaining 50% covered by part-time, low-stress, freelance, or passion work.",
        practicalImplication = "Allows stepping down from high-stress corporate careers significantly earlier while portfolio capital continues compounding toward full FIRE.",
        accentColor = Color(0xFF0F766E)
    )

    val leanFire = MetricInfo(
        title = "Lean FIRE Milestone",
        category = "Essential Independence",
        formulaOrRule = "(75% Baseline Living Costs) / SWR",
        explanation = "Financial independence covering 100% of essential non-negotiable living expenses (housing, utilities, groceries, healthcare) without discretionary lifestyle costs.",
        practicalImplication = "Guarantees absolute basic survival security even in catastrophic economic scenarios.",
        accentColor = Color(0xFF0F766E)
    )

    val standardFire = MetricInfo(
        title = "Standard FIRE Target",
        category = "Full Independence",
        formulaOrRule = "(100% Living Burn - State Pension) / SWR + Bridge Deficit",
        explanation = "Full financial independence sustaining 100% of your current household lifestyle, including discretionary spending, vacations, and child expenses perpetually.",
        practicalImplication = "Private investment portfolio supports 100% of living burn during early retirement and bridges until statutory pensions arrive.",
        accentColor = Color(0xFFD97706)
    )

    val fatFire = MetricInfo(
        title = "Fat FIRE Milestone",
        category = "Abundance & Legacy",
        formulaOrRule = "(130% Enhanced Living Burn) / SWR",
        explanation = "Financial abundance providing an extra 30% spending buffer for frequent travel, luxury, major family support, and generational wealth preservation.",
        practicalImplication = "Offers maximum safety margin against prolonged stagflation or bear markets.",
        accentColor = Color(0xFFD97706)
    )

    val dipDeduction = MetricInfo(
        title = "DIP (Dlouhodobý investiční produkt)",
        category = "Retirement Tax Shield",
        formulaOrRule = "§ 15a ZDP · Up to 48 000 CZK annual personal tax deduction",
        explanation = "Czech long-term investment product allowing you to buy global index ETFs with pre-tax income. Up to 48 000 CZK combined with DPS saves 7 200 CZK (15% bracket) or 11 040 CZK (23% bracket) per person annually.\n\n" +
                "Statutory Deduction Matrix:\n" +
                "• 1 000 CZK/mo (12 000 CZK) → Saves 1 800 CZK (15%)\n" +
                "• 2 000 CZK/mo (24 000 CZK) → Saves 3 600 CZK (15%)\n" +
                "• 3 000 CZK/mo (36 000 CZK) → Saves 5 400 CZK (15%)\n" +
                "• 4 000 CZK/mo (48 000 CZK) → Max 7 200 CZK (15%) / 11 040 CZK (23%)\n\n" +
                "In two-income households, both partners can independently claim up to 48 000 CZK each (saving up to 14 400 CZK combined annually). Note: Deductions require personal taxable income (§ 15 ZDP) and cannot be transferred between spouses.",
        statutoryReference = "§ 15a Act No. 586/1992 Coll. (Income Tax Act)",
        practicalImplication = "Requires maintaining the contract for at least 120 months (10 years) and withdrawing only after age 60 for tax-free maturity without clawbacks.",
        accentColor = Color(0xFF16A34A)
    )

    val dpsLepsiPenzijko = MetricInfo(
        title = "DPS 'Lepší Penzijko' Reform (Proposed 2027)",
        category = "State Subsidy & Pension",
        formulaOrRule = "20% standard match · 40% youth match (<30 yrs) up to 680 CZK/mo",
        explanation = "State supplementary pension savings. Current statutory law provides a 20% flat match (up to 340 CZK/mo). The 40% youth match (<30 yrs) and fee caps are approved government reform proposals with planned effect from 2027.",
        statutoryReference = "Act No. 427/2011 Coll. & Proposed 2027 Reform",
        practicalImplication = "Under current law, deposits between 500 CZK and 1,700 CZK receive 20% state subsidy. Proposed reform doubles this for youth under 30.",
        accentColor = Color(0xFF0F766E)
    )

    val dpsAge36 = MetricInfo(
        title = "Age 36 One-Third DPS Withdrawal (Proposed 2027)",
        category = "Statutory Liquidity Option",
        formulaOrRule = "Proposed 2027 Reform · 1/3 penalty-free withdrawal",
        explanation = "Proposed reform feature targeting 1. 1. 2027 effect (not available under current 2026 law). Under the proposal, participants in DPS participation funds who reach age 36 with at least 120 months of contributions can withdraw up to one-third of their accumulated balance without penalty or clawbacks.",
        statutoryReference = "Government Draft Amending Act No. 427/2011 Coll. (Lepší penzijko, expected 2027)",
        practicalImplication = "Provides intermediate liquidity for home down payment or major life milestone without forfeiting the pension plan.",
        accentColor = Color(0xFF0F766E)
    )

    val etfTimeTest = MetricInfo(
        title = "3-Year ETF Time Test Exemption",
        category = "Czech Capital Gains Tax",
        formulaOrRule = "§ 4 odst. 1 písm. u) ZDP · 3-year holding test",
        explanation = "Capital gains from selling securities (stocks, ETFs like VWCE/SPPW) held by a natural person for more than 3 years are 100% exempt from Czech personal income tax, health insurance, and social security (subject to 40M CZK annual exempt ceiling under 2025+ consolidation package).",
        statutoryReference = "§ 4 odst. 1 písm. u) Act No. 586/1992 Coll.",
        practicalImplication = "Allows broad liquid ETF portfolios to compound and be liquidated during FIRE with completely tax-free cash returns.",
        accentColor = Color(0xFF16A34A)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    val subTabs = listOf("Tax & Pension (DIP/DPS)", "Roadmap & Life Goals")
    val infoState = rememberMetricInfoState()

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("plan_tab")
    ) {
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
                    modifier = Modifier.testTag("plan_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> PensionSubTab(state, onShowInfo = { infoState.show(it) })
            1 -> RoadmapAndGoalsSubTab(state, actionStates, onToggleAction, onUpdateSettings, onShowInfo = { infoState.show(it) })
        }
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}

@Composable
private fun RoadmapAndGoalsSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    var selectedView by remember { mutableIntStateOf(0) } // 0 = Action Checklist & Roadmap, 1 = Life Goals Simulator
    val views = listOf("Action Checklist & Tasks", "Life Goals Simulator")
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            views.forEachIndexed { index, name ->
                val isSelected = selectedView == index
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedView = index
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("roadmap_view_$index")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (selectedView) {
            0 -> FireRoadmapSubTab(state, actionStates, onToggleAction, onUpdateSettings, onShowInfo = onShowInfo)
            1 -> LifeGoalsSimulatorSubTab(state, onUpdateSettings)
        }
    }
}

@Composable
private fun FireRoadmapSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val currentYear = state.settings.baseYear
    val activeFirePoint = if (state.settings.isSingleHousehold) state.fireSinglePoint else state.fireDualPoint
    val fireYear = activeFirePoint?.year ?: (currentYear + 10)
    val targetWorth = roundTo10k(state.fireBaseTargetToday)
    val monthlyPassiveIncome = roundTo1k((targetWorth * (state.settings.safeWithdrawalRatePct / 100.0)) / 12.0)
    val investableNetWorth = state.currentLiquidPortfolio + state.currentPensionPortfolio

    val primaryProgress = if (targetWorth > 0) ((investableNetWorth / targetWorth) * 100.0).coerceIn(0.0, 100.0) else 0.0

    // Filter modes: 0 -> Milestones & Phases, 1 -> Action Checklist
    var selectedSection by remember { mutableIntStateOf(0) }
    val sectionLabels = listOf("Milestones & Phases", "Action Checklist")

    val completedActionsCount = remember(actionStates, currentYear) {
        ActionMeta.items.count { meta ->
            actionStates["${currentYear}_${meta.id}"] == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Executive Roadmap Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fire_roadmap_hero_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BrandTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FIRE Strategic Plan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Target: Year $fireYear (${fireYear - currentYear} yrs to goal)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandTeal.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${primaryProgress.toInt()}% of Goal",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Smooth Continuous Progress Bar (Zero trailing dots)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    val p = (primaryProgress / 100.0).toFloat().coerceIn(0f, 1f)
                    if (p > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(p)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(BrandTeal)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3 Core Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.05f)) {
                        Text(
                            text = "Investable Capital",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = fmtCZK(investableNetWorth),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Column(modifier = Modifier.weight(1.05f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Target Capital",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = fmtCZK(targetWorth),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = BrandTeal
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Monthly SWR",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = fmtCZK(monthlyPassiveIncome),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = GoodGreen
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Section Filter Chips (Pill Bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sectionLabels.forEachIndexed { index, label ->
                val isSelected = selectedSection == index
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedSection = index }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 2. Section 1: FIRE Milestones Hierarchy & Two-Bucket Liquidity
        if (selectedSection == 0) {
            FireMilestonesComparisonCard(state = state, onUpdateSettings = onUpdateSettings, onShowInfo = onShowInfo)
            TwoBucketLiquidityBridgeCard(state = state, onShowInfo = onShowInfo)
        }

        // 3. Section 2: High-Leverage Execution Checklist
        if (selectedSection == 1) {
            ActionChecklistCard(
                currentYear = currentYear,
                actionStates = actionStates,
                completedCount = completedActionsCount,
                state = state,
                onToggleAction = onToggleAction
            )
        }
    }
}

@Composable
private fun LifeGoalsSimulatorSubTab(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val baseYear = state.settings.baseYear

    val customGoalsList = remember(state.settings.customGoalsJson) {
        parseCustomLifeGoals(state.settings.customGoalsJson)
    }

    var showAddGoalDialog by remember { mutableStateOf(false) }

    // Cash flow feasibility calculations
    val monthlyNetIncome = state.currentIncome.totalMonthly
    val monthlyExpenses = state.totalLivingCostMonthly
    val monthlyNetSurplus = (monthlyNetIncome - monthlyExpenses).coerceAtLeast(0.0)
    val monthlyInvest = state.investMonthlyTotal

    val totalRequiredMonthlyGoals = customGoalsList.sumOf { goal ->
        val yrs = (goal.targetYear - baseYear).coerceAtLeast(1)
        val rem = (goal.targetAmountCzk - goal.currentSavedCzk).coerceAtLeast(0.0)
        rem / (yrs * 12.0)
    }
    val totalRemainingCapitalNeeded = customGoalsList.sumOf { (it.targetAmountCzk - it.currentSavedCzk).coerceAtLeast(0.0) }
    val totalFireDelayYears = if (monthlyInvest > 0) totalRemainingCapitalNeeded / (monthlyInvest * 12.0) else 0.0

    val surplusAfterGoals = monthlyNetSurplus - totalRequiredMonthlyGoals
    val capacityRatio = when {
        monthlyNetSurplus > 0 -> (totalRequiredMonthlyGoals / monthlyNetSurplus).coerceIn(0.0, 2.0)
        totalRequiredMonthlyGoals > 0 -> 2.0
        else -> 0.0
    }
    val isOverBudget = surplusAfterGoals < 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary & Feasibility Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Life Event & Goal Simulator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(
                        onClick = { showAddGoalDialog = true },
                        modifier = Modifier.testTag("add_goal_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Life Goal", tint = BrandTeal)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Simulate non-FIRE financial milestones. Evaluates cash flow feasibility against your monthly net income surplus.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Feasibility Capacity Meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Goal Commitment vs Net Capacity",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    ColorPill(
                        text = if (isOverBudget) "Over Capacity" else "${(capacityRatio * 100).toInt()}% Allocated",
                        color = if (isOverBudget) MaterialTheme.colorScheme.error else GoodGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val p = capacityRatio.toFloat().coerceIn(0f, 1f)
                    if (p > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(p)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (isOverBudget) MaterialTheme.colorScheme.error else BrandTeal)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Req: ${fmtCZK(totalRequiredMonthlyGoals)}/mo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Net Surplus: ${fmtCZK(monthlyNetSurplus)}/mo",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FIRE Impact Metric
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandGold.copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (monthlyInvest > 0) {
                                "Combined Goals Impact: Delays primary FIRE target by ~${String.format(java.util.Locale.ROOT, "%.1f", totalFireDelayYears)} years."
                            } else {
                                "Combined Goals Impact: No DCA flow — delay cannot be estimated."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }

        // Goals List
        customGoalsList.forEachIndexed { index, goal ->
            val yearsRemaining = (goal.targetYear - baseYear).coerceAtLeast(1)
            val monthsRemaining = yearsRemaining * 12
            val remainingAmount = (goal.targetAmountCzk - goal.currentSavedCzk).coerceAtLeast(0.0)
            val requiredMonthly = remainingAmount / monthsRemaining
            val progress = (goal.currentSavedCzk / goal.targetAmountCzk).coerceIn(0.0, 1.0)
            val goalFireDelay = if (monthlyInvest > 0) remainingAmount / (monthlyInvest * 12.0) else 0.0

            val iconVec = when (goal.iconName) {
                "home" -> Icons.Default.Home
                "school" -> Icons.Default.School
                "star" -> Icons.Default.Star
                else -> Icons.Default.Flag
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("life_goal_card_$index"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BrandTeal.copy(alpha = 0.15f))
                                    .padding(8.dp)
                            ) {
                                Icon(imageVector = iconVec, contentDescription = null, tint = BrandTeal)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Target Year: ${goal.targetYear} ($yearsRemaining yrs left)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val updated = customGoalsList.filterIndexed { i, _ -> i != index }
                                onUpdateSettings?.invoke(state.settings.copy(customGoalsJson = serializeCustomLifeGoals(updated)))
                            },
                            modifier = Modifier.testTag("delete_goal_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Goal",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Smooth Continuous Progress Bar (Zero trailing dots)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val p = progress.toFloat().coerceIn(0f, 1f)
                        if (p > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(BrandTeal)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved: ${fmtCompact(goal.currentSavedCzk)} / ${fmtCompact(goal.targetAmountCzk)} (${(progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Need: ${fmtCZK(requiredMonthly)}/mo",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ColorPill(
                        text = if (monthlyInvest > 0) "+${String.format(java.util.Locale.ROOT, "%.1f", goalFireDelay)} yrs to FIRE" else "No DCA flow",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showAddGoalDialog) {
        var goalName by remember { mutableStateOf("") }
        var targetYearStr by remember { mutableStateOf((state.settings.baseYear + 5).toString()) }
        var targetAmountStr by remember { mutableStateOf("500000") }
        var currentSavedStr by remember { mutableStateOf("50000") }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Add Custom Life Goal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Goal Name (e.g. Dream Cottage)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetYearStr,
                        onValueChange = { targetYearStr = it },
                        label = { Text("Target Year", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAmountStr,
                        onValueChange = { targetAmountStr = it },
                        label = { Text("Target Capital", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentSavedStr,
                        onValueChange = { currentSavedStr = it },
                        label = { Text("Current Savings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = goalName.ifBlank { "Custom Life Goal" }
                        val yr = targetYearStr.toIntOrNull() ?: (state.settings.baseYear + 5)
                        val targetAmt = targetAmountStr.toDoubleOrNull() ?: 500000.0
                        val savedAmt = currentSavedStr.toDoubleOrNull() ?: 0.0

                        val newItem = com.example.domain.CustomLifeGoalItem(
                            id = System.currentTimeMillis().toString(),
                            name = name,
                            iconName = "flag",
                            targetYear = yr,
                            targetAmountCzk = targetAmt,
                            currentSavedCzk = savedAmt
                        )
                        val updated = customGoalsList + newItem
                        onUpdateSettings?.invoke(state.settings.copy(customGoalsJson = serializeCustomLifeGoals(updated)))
                        showAddGoalDialog = false
                    },
                    modifier = Modifier.testTag("save_custom_goal_button")
                ) {
                    Text("Add Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PensionSubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val dps = state.dps
    val dip = state.dip
    val currentSubsidy = FinancialEngine.dpsSubsidy(s.dpsOwnContributionMonthly, s.primaryAge, s)

    val vDipMonthly = s.dipContributionMonthly
    val eDipMonthly = s.eDipContributionMonthly
    val totalMonthlyDip = vDipMonthly + eDipMonthly
    val totalAnnualDip = totalMonthlyDip * 12.0

    val vAge = s.primaryAge
    val vSubsidy = FinancialEngine.dpsSubsidy(s.dpsOwnContributionMonthly, vAge, s, s.baseYear)
    val eAge = s.baseYear - ELEONORA_BIRTH_YEAR
    val eSubsidy = if (!s.isSingleHousehold) FinancialEngine.dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear) else 0.0

    val vDpsAbove = max(0.0, s.dpsOwnContributionMonthly - s.dpsDeductionThresholdMonthly)
    val eDpsAbove = max(0.0, s.eDpsOwnContributionMonthly - s.dpsDeductionThresholdMonthly)

    val vDeductionAnnual = min((vDipMonthly + vDpsAbove) * 12.0, s.taxDeductionCeilingAnnual)
    val eDeductionAnnual = min((eDipMonthly + eDpsAbove) * 12.0, s.taxDeductionCeilingAnnual)
    val totalDeductionAnnual = vDeductionAnnual + eDeductionAnnual

    val vHeadroom = max(0.0, s.taxDeductionCeilingAnnual - (vDipMonthly + vDpsAbove) * 12.0)
    val eHeadroom = max(0.0, s.taxDeductionCeilingAnnual - (eDipMonthly + eDpsAbove) * 12.0)

    val vHasIncome = FinancialEngine.vaclavSalaryMonthly(s.baseYear, s) > 0.0
    val eHasIncome = !s.isSingleHousehold && FinancialEngine.eleonoraSalaryMonthly(s.baseYear, s) > 0.0

    val vSubsidyMaxAnnual = if (vAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) {
        s.dpsYouthSubsidyMaxMonthly * 12.0
    } else {
        s.dpsStandardSubsidyMaxMonthly * 12.0
    }
    val eSubsidyMaxAnnual = if (!s.isSingleHousehold) {
        if (eAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) {
            s.dpsYouthSubsidyMaxMonthly * 12.0
        } else {
            s.dpsStandardSubsidyMaxMonthly * 12.0
        }
    } else 0.0

    val vOptimalDepositAnnual = s.dpsDeductionThresholdMonthly * 12.0 + (if (vHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    val eOptimalDepositAnnual = if (!s.isSingleHousehold) {
        s.dpsDeductionThresholdMonthly * 12.0 + (if (eHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    } else 0.0
    val totalOptimalDepositAnnual = vOptimalDepositAnnual + eOptimalDepositAnnual

    val maxCombinedBenefit = (vSubsidyMaxAnnual + eSubsidyMaxAnnual) +
        ((if (vHasIncome) s.taxDeductionCeilingAnnual else 0.0) + (if (eHasIncome) s.taxDeductionCeilingAnnual else 0.0)) * (s.taxRatePct / 100.0)

    val yearlyTaxSaved = state.taxReturnHelper.dipSaving
    val totalSubsidyAnnual = (vSubsidy + eSubsidy) * 12.0
    val totalGovBenefitAnnual = totalSubsidyAnnual + yearlyTaxSaved

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. KPI Highlights Hero Grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Annual Tax Refund",
                value = "+${fmtCZK(yearlyTaxSaved)}",
                hint = "${String.format(java.util.Locale.getDefault(), "%.0f", s.taxRatePct)}% relief via tax return",
                accentColor = GoodGreen,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dipDeduction,
                onShowInfo = onShowInfo
            )
            val isSingle = s.isSingleHousehold
            val totalMonthlyRetirement = vDipMonthly + (if (!isSingle) eDipMonthly else 0.0) + s.dpsOwnContributionMonthly + (if (!isSingle) s.eDpsOwnContributionMonthly else 0.0)
            val vTotalRetirement = vDipMonthly + s.dpsOwnContributionMonthly
            val eTotalRetirement = if (!isSingle) eDipMonthly + s.eDpsOwnContributionMonthly else 0.0
            KpiCard(
                title = "Monthly Deposit",
                value = fmtCZK(totalMonthlyRetirement),
                hint = if (s.isSingleHousehold) "DIP: ${fmtCompact(vDipMonthly)} · DPS: ${fmtCompact(s.dpsOwnContributionMonthly)}" else "V: ${fmtCompact(vTotalRetirement)} · E: ${fmtCompact(eTotalRetirement)}",
                accentColor = BrandTeal,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dipDeduction,
                onShowInfo = onShowInfo
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val eAge = s.baseYear - ELEONORA_BIRTH_YEAR
            val totalSubsidyMonthly = currentSubsidy + (if (!s.isSingleHousehold) FinancialEngine.dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear) else 0.0)
            val dpsTotalDeposit = s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
            KpiCard(
                title = "State Subsidy Match",
                value = if (dps.youthSubsidyActive) "40% (Youth)" else "20% Standard",
                hint = if (dps.youthSubsidyActive) {
                    "${fmtCZK(totalSubsidyMonthly)}/mo on ${fmtCompact(dpsTotalDeposit)}"
                } else {
                    "${fmtCZK(totalSubsidyMonthly)}/mo (40% in 2027)"
                },
                accentColor = BrandGold,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dpsLepsiPenzijko,
                onShowInfo = onShowInfo
            )
            val dpsNetReturnPct = max(0.0, s.dpsGrossReturnPct - min(s.dpsAnnualFeePct, s.dpsStatutoryFeeCapPct))
            KpiCard(
                title = "DIP + DPS at Age 60",
                value = fmtCompact(dip.dipBalanceAt60 + dps.dpsBalance),
                hint = "DIP ${String.format(java.util.Locale.getDefault(), "%.1f", s.portfolioNominalReturnPct)}% · DPS ${String.format(java.util.Locale.getDefault(), "%.1f", dpsNetReturnPct)}% net",
                accentColor = BrandBlue,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dpsAge36,
                onShowInfo = onShowInfo
            )
        }

        // 2. Main Hero Card: DIP & DPS Two-Stage Statutory Optimization (Subsidy + Tax Shield)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .infoTapHold(PlanMetricInfos.dipDeduction, onShowInfo)
                .testTag("dip_summary_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeaderPill(
                    title = "Retirement Optimization (DIP & DPS)",
                    subtitle = "Two-stage optimization: state cash match & personal tax shield",
                    badgeText = "OPTIMIZATION",
                    accentColor = GoodGreen,
                    trailingContent = {
                        IconButton(
                            onClick = { onShowInfo(PlanMetricInfos.dipDeduction) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "DIP Details",
                                tint = GoodGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3 Symmetrical Metric Boxes: Subsidy, Tax Refund, Total Benefit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandGold.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "State Subsidy",
                                style = MaterialTheme.typography.labelSmall.copy(color = BrandGold, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "+${fmtCZK(totalSubsidyAnnual)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandGold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            )
                            Text(
                                text = "+${fmtCZK(vSubsidy + eSubsidy)}/mo cash",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoodGreen.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Tax Refund",
                                style = MaterialTheme.typography.labelSmall.copy(color = GoodGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "+${fmtCZK(yearlyTaxSaved)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GoodGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            )
                            Text(
                                text = "${String.format(java.util.Locale.getDefault(), "%.0f", s.taxRatePct)}% tax relief",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandTeal.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Total Benefit",
                                style = MaterialTheme.typography.labelSmall.copy(color = BrandTeal, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "+${fmtCZK(totalGovBenefitAnnual)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandTeal, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            )
                            Text(
                                text = "Annual net benefit",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Two-Stage Optimization Bar: Primary (Václav)
                PensionOptimizationBar(
                    name = if (s.primaryName.isNotBlank()) s.primaryName else "Václav",
                    dpsMonthly = s.dpsOwnContributionMonthly,
                    dipMonthly = vDipMonthly,
                    subsidyMonthly = vSubsidy,
                    subsidyCapMonthly = s.dpsDeductionThresholdMonthly,
                    taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                    taxRatePct = s.taxRatePct,
                    hasTaxableIncome = vHasIncome,
                    maxSubsidyAnnual = vSubsidyMaxAnnual
                )

                // Two-Stage Optimization Bar: Spouse (Eleonora) — visible in dual-income mode
                if (!s.isSingleHousehold) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PensionOptimizationBar(
                        name = if (s.spouseName.isNotBlank()) s.spouseName else "Eleonora",
                        dpsMonthly = s.eDpsOwnContributionMonthly,
                        dipMonthly = eDipMonthly,
                        subsidyMonthly = eSubsidy,
                        subsidyCapMonthly = s.dpsDeductionThresholdMonthly,
                        taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                        taxRatePct = s.taxRatePct,
                        hasTaxableIncome = eHasIncome,
                        maxSubsidyAnnual = eSubsidyMaxAnnual
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GoodGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (s.isSingleHousehold) {
                            "Optimal annual deposit: ${fmtCZK(vOptimalDepositAnnual)} (20 400 Kč DPS + 48 000 Kč DIP) · Max benefit: +${fmtCZK(maxCombinedBenefit)}"
                        } else if (!eHasIncome) {
                            "Household optimal deposit: ${fmtCZK(totalOptimalDepositAnnual)} (40 800 Kč DPS + 48 000 Kč DIP for ${if (s.primaryName.isNotBlank()) s.primaryName else "Václav"}) · Max benefit: +${fmtCZK(maxCombinedBenefit)}"
                        } else {
                            "Dual-earner optimal deposit: ${fmtCZK(totalOptimalDepositAnnual)} (40 800 Kč DPS + 96 000 Kč DIP) · Max benefit: +${fmtCZK(maxCombinedBenefit)}"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                if (!s.isSingleHousehold && !eHasIncome) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${if (s.spouseName.isNotBlank()) s.spouseName else "Eleonora"}'s tax deduction is inactive during parental leave (deductions are personal and non-transferable); DPS subsidies apply to both.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
        }

        FundsAllocatorCard(state = state, onShowInfo = onShowInfo)

        TwoBucketLiquidityBridgeCard(state = state, onShowInfo = onShowInfo)

        DipOptimizationMatrixCard(state = state)

        DpsOptimizationMatrixCard(state = state)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PensionOptimizationBar(
    name: String,
    dpsMonthly: Double,
    dipMonthly: Double,
    subsidyMonthly: Double,
    subsidyCapMonthly: Double = 1700.0,
    taxShieldCapAnnual: Double = 48000.0,
    taxRatePct: Double = 15.0,
    hasTaxableIncome: Boolean = true,
    maxSubsidyAnnual: Double = 4080.0,
    modifier: Modifier = Modifier
) {
    val effectiveTaxShieldCapAnnual = if (hasTaxableIncome) taxShieldCapAnnual else 0.0
    val taxShieldCapMonthly = effectiveTaxShieldCapAnnual / 12.0
    val totalTargetMonthly = subsidyCapMonthly + taxShieldCapMonthly

    val subsidyDeposit = min(dpsMonthly, subsidyCapMonthly)
    val subsidyRatio = (subsidyDeposit / subsidyCapMonthly).toFloat().coerceIn(0f, 1f)

    val dpsAbove = max(0.0, dpsMonthly - subsidyCapMonthly)
    val taxShieldMonthly = dipMonthly + dpsAbove
    val taxShieldDeposit = if (hasTaxableIncome) min(taxShieldMonthly, taxShieldCapMonthly) else 0.0
    val taxShieldRatio = if (hasTaxableIncome && taxShieldCapMonthly > 0.0) {
        (taxShieldDeposit / taxShieldCapMonthly).toFloat().coerceIn(0f, 1f)
    } else 0f

    val totalOptimizedMonthly = subsidyDeposit + taxShieldDeposit
    val isMaxed = totalOptimizedMonthly >= (totalTargetMonthly - 0.5)
    val isActive = dpsMonthly > 0.0 || (hasTaxableIncome && dipMonthly > 0.0)

    val annualSubsidy = subsidyMonthly * 12.0
    val annualTaxSaved = if (hasTaxableIncome) taxShieldDeposit * 12.0 * (taxRatePct / 100.0) else 0.0
    val totalAnnualBenefit = annualSubsidy + annualTaxSaved
    val maxPotentialBenefit = maxSubsidyAnnual + (if (hasTaxableIncome) taxShieldCapAnnual * (taxRatePct / 100.0) else 0.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (isActive) {
                ColorPill(
                    text = if (isMaxed) "100% MAXED" else "${fmtCZK(totalOptimizedMonthly)} / ${fmtCompact(totalTargetMonthly)}/mo",
                    color = if (isMaxed) GoodGreen else BrandTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp
                )
            } else {
                ColorPill(
                    text = "0 / ${fmtCompact(totalTargetMonthly)} — UNUSED",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Dual Stage Segmented Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stage 1: DPS Subsidy Stage (0 - 1,700 CZK)
            Box(
                modifier = Modifier
                    .weight(17f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                if (subsidyRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(subsidyRatio)
                            .fillMaxHeight()
                            .background(BrandGold)
                    )
                }
            }

            // Stage 2: Tax Shield Stage (+4,000 CZK)
            Box(
                modifier = Modifier
                    .weight(40f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp))
                    .background(
                        if (hasTaxableIncome) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
            ) {
                if (taxShieldRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(taxShieldRatio)
                            .fillMaxHeight()
                            .background(GoodGreen)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Stage breakdown indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(BrandGold)
                )
                Text(
                    text = "DPS: ${fmtCZK(subsidyDeposit)} / 1 700",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (hasTaxableIncome) GoodGreen else MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    text = if (hasTaxableIncome) {
                        "Deduction: ${fmtCZK(taxShieldDeposit)} / 4 000"
                    } else {
                        "Deduction: Inactive (no tax base)"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isActive) "Annual benefit: +${fmtCZK(totalAnnualBenefit)}" else "Max benefit: +${fmtCZK(maxPotentialBenefit)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isActive) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            )
            val headroomMonthly = max(0.0, totalTargetMonthly - totalOptimizedMonthly)
            Text(
                text = if (headroomMonthly <= 0.5) "Fully optimized" else "Headroom: ${fmtCZK(headroomMonthly)}/mo",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun FireMilestonesComparisonCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val milestones = state.fireMilestones
    val investableNetWorth = state.currentLiquidPortfolio + state.currentPensionPortfolio

    val baristaMilestone = milestones.baristaFire
    val items = buildList {
        add(
            MilestoneConfig(
                milestone = milestones.coastFire,
                accentColor = BrandTeal,
                icon = Icons.Default.Spa,
                shortLabel = "Coast",
                levelIndex = 1,
                metricInfo = PlanMetricInfos.coastFire
            )
        )
        if (baristaMilestone != null) {
            add(
                MilestoneConfig(
                    milestone = baristaMilestone,
                    accentColor = Color(0xFF0F766E),
                    icon = Icons.Default.LocalCafe,
                    shortLabel = "Barista",
                    levelIndex = 2,
                    metricInfo = PlanMetricInfos.baristaFire
                )
            )
        }
        add(
            MilestoneConfig(
                milestone = milestones.leanFire,
                accentColor = BrandBlue,
                icon = Icons.Default.Home,
                shortLabel = "Lean",
                levelIndex = if (baristaMilestone != null) 3 else 2,
                metricInfo = PlanMetricInfos.leanFire
            )
        )
        add(
            MilestoneConfig(
                milestone = milestones.standardFire,
                accentColor = GoodGreen,
                icon = Icons.Default.Shield,
                shortLabel = "Standard",
                levelIndex = if (baristaMilestone != null) 4 else 3,
                metricInfo = PlanMetricInfos.standardFire
            )
        )
        add(
            MilestoneConfig(
                milestone = milestones.fatFire,
                accentColor = BrandGold,
                icon = Icons.Default.Diamond,
                shortLabel = "Fat",
                levelIndex = if (baristaMilestone != null) 5 else 4,
                metricInfo = PlanMetricInfos.fatFire
            )
        )
    }

    // Current unlocked level determination
    val currentLevel = when {
        milestones.fatFire.isAchieved -> "Level ${items.size}: Fat FIRE"
        milestones.standardFire.isAchieved -> "Level ${if (baristaMilestone != null) 4 else 3}: Standard FIRE"
        milestones.leanFire.isAchieved -> "Level ${if (baristaMilestone != null) 3 else 2}: Lean FIRE"
        baristaMilestone?.isAchieved == true -> "Level 2: Barista FIRE"
        milestones.coastFire.isAchieved -> "Level 1: Coast FIRE"
        else -> "Level 0: Accumulation"
    }

    val defaultTargetId = items.firstOrNull { !it.milestone.isAchieved }?.milestone?.id ?: items.lastOrNull()?.milestone?.id ?: "standard"
    var selectedMilestoneId by remember { mutableStateOf(defaultTargetId) }
    val activeConfig = items.find { it.milestone.id == selectedMilestoneId } ?: items.firstOrNull() ?: items[0]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fire_milestones_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "FIRE Milestone Matrix",
                subtitle = "Capital requirements & passive cash flow comparison",
                badgeText = "${items.size} TIERS",
                badgeColor = BrandGold,
                icon = Icons.Default.Flag,
                accentColor = BrandGold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Current Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentLevel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ColorPill(
                        text = "Net: ${fmtCompact(investableNetWorth)}",
                        color = BrandTeal,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        horizontalPadding = 7.dp,
                        verticalPadding = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Matrix Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.15f)
                )
                Text(
                    text = "TARGET",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.15f)
                )
                Text(
                    text = "SWR",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = "STATUS",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(0.85f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(4.dp))

            // Matrix Table Rows
            items.forEach { config ->
                val isSelected = selectedMilestoneId == config.milestone.id
                val m = config.milestone
                val roundedTarget = roundTo10k(m.targetAmountToday)
                val roundedSWR = roundTo1k(m.monthlyPassiveIncome)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) config.accentColor.copy(alpha = 0.12f)
                    else if (m.isAchieved) GoodGreen.copy(alpha = 0.05f)
                    else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, config.accentColor.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedMilestoneId = m.id
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tier Name + Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.15f)
                        ) {
                            Icon(
                                imageVector = config.icon,
                                contentDescription = null,
                                tint = config.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = config.shortLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) config.accentColor else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = m.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Target Capital
                        Text(
                            text = fmtCompact(roundedTarget),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1.15f)
                        )

                        // SWR
                        Text(
                            text = fmtCompact(roundedSWR),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = config.accentColor
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1.1f)
                        )

                        // Status / ETA
                        Box(
                            modifier = Modifier.weight(0.85f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (m.isAchieved) {
                                ColorPill(
                                    text = "DONE",
                                    color = GoodGreen,
                                    icon = Icons.Default.Check,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            } else {
                                ColorPill(
                                    text = m.estimatedAge?.let { "Age $it" } ?: "Age ${state.settings.primaryAge + 35}+",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Tier Detail Card
            val activeM = activeConfig.milestone
            val activeTarget = roundTo10k(activeM.targetAmountToday)
            val activeGap = (activeTarget - investableNetWorth).coerceAtLeast(0.0)
            val progressFloat = (activeM.progressPct / 100.0).toFloat().coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = activeConfig.accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, activeConfig.accentColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Active Target: ${activeM.name}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (onShowInfo != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info on ${activeM.name}",
                                    tint = activeConfig.accentColor.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onShowInfo(activeConfig.metricInfo) }
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            ColorPill(
                                text = "${activeM.progressPct.toInt()}%",
                                color = if (activeM.isAchieved) GoodGreen else activeConfig.accentColor,
                                fontSize = 9.5.sp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        if (activeM.isAchieved) {
                            ColorPill(
                                text = "+${fmtCompact(investableNetWorth - activeTarget)} Surplus",
                                color = GoodGreen,
                                fontSize = 10.sp,
                                horizontalPadding = 7.dp,
                                verticalPadding = 3.dp
                            )
                        } else {
                            Text(
                                text = "${fmtCompact(activeGap)} gap",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // Smooth Continuous Progress Bar (Zero trailing dots)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        val p = progressFloat.coerceIn(0f, 1f)
                        if (p > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (activeM.isAchieved) GoodGreen else activeConfig.accentColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Detail Metrics Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Target Capital",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = fmtCZK(activeTarget),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Monthly SWR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = fmtCZK(roundTo1k(activeM.monthlyPassiveIncome)),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = activeConfig.accentColor
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (activeM.isAchieved) "Surplus" else "Remaining Gap",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (activeM.isAchieved) "+${fmtCZK(investableNetWorth - activeTarget)}" else fmtCZK(activeGap),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = if (activeM.isAchieved) GoodGreen else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activeM.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (activeM.id == "coast") {
                            "• Compound interest alone turns current capital into full financial independence without future savings."
                        } else if (activeM.isAchieved) {
                            "• Milestone unlocked! Your current portfolio exceeds this threshold."
                        } else {
                            "• At ${fmtPct(state.settings.safeWithdrawalRatePct)} SWR, reaching ${fmtCZK(activeTarget)} generates sustainable passive cash flow of ${fmtCZK(roundTo1k(activeM.monthlyPassiveIncome))} / month."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    if (onUpdateSettings != null && activeM.id != "coast") {
                        val isCurrentOverride = state.settings.fireTargetOverride > 0 &&
                                (kotlin.math.abs(state.settings.fireTargetOverride - activeTarget) < 1.0)

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = activeConfig.accentColor.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrentOverride) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ColorPill(
                                        text = "Active Primary FIRE Goal",
                                        color = BrandGold,
                                        fontSize = 10.sp,
                                        horizontalPadding = 7.dp,
                                        verticalPadding = 3.dp
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onUpdateSettings(state.settings.copy(fireTargetOverride = 0.0))
                                    }
                                ) {
                                    Text(
                                        text = "Reset to Auto",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Make this your target:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    )
                                )
                                Button(
                                    onClick = {
                                        onUpdateSettings(state.settings.copy(fireTargetOverride = activeTarget))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = activeConfig.accentColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Set as Primary Goal (${fmtCompact(activeTarget)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

private data class MilestoneConfig(
    val milestone: com.example.domain.FireMilestone,
    val accentColor: Color,
    val icon: ImageVector,
    val shortLabel: String,
    val levelIndex: Int,
    val metricInfo: MetricInfo
)


