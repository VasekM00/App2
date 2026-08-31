package com.example.ui.tabs

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomLifeGoals
import com.example.domain.serializeCustomLifeGoals
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
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

data class LifeGoalItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val targetYear: Int,
    val targetAmountCzk: Double,
    val currentSavedCzk: Double
)

private object PlanMetricInfos {
    val coastFire = MetricInfo(
        title = "Coast FIRE Milestone",
        category = "Organic Compounding",
        formulaOrRule = "Target / (1 + Real CAGR)^Years_to_Pension",
        explanation = "The amount of invested capital needed today such that, with zero additional contributions, it will compound into your full retirement nest egg by statutory state pension age.",
        practicalImplication = "Reaching Coast FIRE eliminates survival employment pressure; you only need to earn enough to cover current living burn.",
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
        formulaOrRule = "§ 15a ZDP · Up to 48,000 CZK/yr personal tax deduction",
        explanation = "Czech long-term investment product allowing you to buy global index ETFs with pre-tax income. Up to 48,000 CZK combined with DPS saves 7,200 CZK (15% bracket) or 11,040 CZK (23% bracket) per person annually.\n\n" +
                "📊 Statutory Deduction Matrix:\n" +
                "• 1,000 CZK/mo (12k/yr) → Saves 1,800 CZK/yr (15%)\n" +
                "• 2,000 CZK/mo (24k/yr) → Saves 3,600 CZK/yr (15%)\n" +
                "• 3,000 CZK/mo (36k/yr) → Saves 5,400 CZK/yr (15%)\n" +
                "• 4,000 CZK/mo (48k/yr) → Max 7,200 CZK/yr (15%) / 11,040 CZK/yr (23%)\n\n" +
                "In two-income households, both partners can independently claim up to 48k CZK each (saving up to 14,400 CZK/yr combined).",
        statutoryReference = "§ 15a Act No. 586/1992 Coll. (Income Tax Act)",
        practicalImplication = "Requires maintaining the contract for at least 120 months (10 years) and withdrawing only after age 60 for tax-free maturity without clawbacks.",
        accentColor = Color(0xFF16A34A)
    )

    val dpsLepsiPenzijko = MetricInfo(
        title = "DPS 'Lepší Penzijko' Reform",
        category = "State Subsidy & Pension",
        formulaOrRule = "20% standard match · 40% youth match (<30 yrs) up to 680 CZK/mo",
        explanation = "State supplementary pension savings. Contributions between 500 CZK and 1,700 CZK receive direct monthly state cash subsidies. Contributions above 1,700 CZK qualify for the personal income tax deduction.",
        statutoryReference = "Act No. 427/2011 Coll. & 2024 Amendments",
        practicalImplication = "Youth under 30 get an immediate 40% guaranteed match on deposits up to 1,700 CZK/mo.",
        accentColor = Color(0xFF0F766E)
    )

    val dpsAge36 = MetricInfo(
        title = "Age 36 One-Third DPS Withdrawal",
        category = "Statutory Liquidity Option",
        formulaOrRule = "§ 12 Act No. 427/2011 Coll. · 1/3 penalty-free withdrawal",
        explanation = "Participants in DPS participation funds who reach age 36 with at least 120 months (10 years) of contributions can withdraw up to one-third of their own accumulated balances without terminating the contract or losing future entitlement.",
        statutoryReference = "§ 12 odst. 2 Act No. 427/2011 Coll.",
        practicalImplication = "Provides intermediate liquidity for home down payment or major life milestone without forfeiting the pension plan.",
        accentColor = Color(0xFF0F766E)
    )

    val etfTimeTest = MetricInfo(
        title = "3-Year ETF Time Test Exemption",
        category = "Czech Capital Gains Tax",
        formulaOrRule = "§ 4 odst. 1 písm. w) ZDP · 3-year holding test",
        explanation = "Capital gains from selling securities (stocks, ETFs like VWCE/SPPW) held by a natural person for more than 3 years are 100% exempt from Czech personal income tax, health insurance, and social security.",
        statutoryReference = "§ 4 odst. 1 písm. w) Act No. 586/1992 Coll.",
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
    val fireYear = state.fireDualPoint?.year ?: (currentYear + 10)
    val targetWorth = roundTo10k(state.fireBaseTargetToday)
    val monthlyPassiveIncome = roundTo1k((targetWorth * (state.settings.safeWithdrawalRatePct / 100.0)) / 12.0)
    val investableNetWorth = state.settings.liquidPortfolioCurrent + state.settings.eLiquidPortfolioCurrent +
            state.settings.dpsBalanceCurrent + state.settings.eDpsBalanceCurrent +
            state.settings.dipBalanceCurrent + state.settings.eDipBalanceCurrent

    val primaryProgress = if (targetWorth > 0) ((investableNetWorth / targetWorth) * 100.0).coerceIn(0.0, 100.0) else 0.0

    // Filter modes: 0 -> Milestones & Phases, 1 -> Action Checklist
    var selectedSection by remember { mutableIntStateOf(0) }
    val sectionLabels = listOf("Milestones & Phases", "Action Checklist")

    val completedActionsCount = ActionMeta.items.count { meta ->
        actionStates["${currentYear}_${meta.id}"] == true
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

        // 2. Section 1: FIRE Milestones Hierarchy & Roadmap Timeline
        if (selectedSection == 0) {
            FireMilestonesComparisonCard(state = state, onUpdateSettings = onUpdateSettings, onShowInfo = onShowInfo)
            RoadmapTimelineCard(state = state, fireYear = fireYear, targetWorth = targetWorth, monthlyPassiveIncome = monthlyPassiveIncome)
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
private fun RoadmapTimelineCard(
    state: FullCalculationState,
    fireYear: Int,
    targetWorth: Double,
    monthlyPassiveIncome: Double
) {
    val currentYear = state.settings.baseYear

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roadmap_timeline_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BrandGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "3-Phase Timeline Path",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Milestones across accumulation, maturity, and freedom",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val youthYearsLeft = maxOf(0, state.settings.dpsYouthAgeLimit - state.settings.primaryAge)
            val p1EndYear = if (youthYearsLeft > 0) currentYear + youthYearsLeft else currentYear + 3
            val p2EndYear = maxOf(p1EndYear + 1, currentYear + maxOf(1, 36 - state.settings.primaryAge))
            val p3StartYear = maxOf(p2EndYear + 1, fireYear)

            // Connected Timeline Steps
            TimelineStepItem(
                phaseNum = "1",
                title = "Capital Accumulation & Tax Shield",
                timeframe = "$currentYear – $p1EndYear",
                badgeColor = BrandTeal,
                isCurrent = true,
                isLast = false,
                keyPoints = listOf(
                    "Maximize DIP contributions (48k Kč/yr tax deduction)",
                    if (state.dps.youthSubsidyActive) "Secure 40% youth DPS state match" else "Secure DPS state match & employer match",
                    "Maintain 6-month liquid cash reserve"
                )
            )

            TimelineStepItem(
                phaseNum = "2",
                title = "Portfolio Scaling & Time-Test",
                timeframe = "${p1EndYear + 1} – $p2EndYear",
                badgeColor = BrandGold,
                isCurrent = false,
                isLast = false,
                keyPoints = listOf(
                    "Pass 3-year Czech ETF capital gains tax exemption",
                    "Unlock Age 36 penalty-free one-third DPS withdrawal (${fmtCompact(state.dps.earlyWithdrawalLimitAt36)})",
                    "Reach intermediate Lean FIRE independence barrier"
                )
            )

            TimelineStepItem(
                phaseNum = "3",
                title = "Financial Independence & Bridge",
                timeframe = "$p3StartYear+",
                badgeColor = GoodGreen,
                isCurrent = false,
                isLast = true,
                keyPoints = listOf(
                    "Target portfolio: ${fmtCompact(targetWorth)}",
                    "${fmtPct(state.settings.safeWithdrawalRatePct)} SWR bridge generates ${fmtCZK(monthlyPassiveIncome)} / month",
                    "Full autonomy to pivot, consult, or retire"
                )
            )

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun TimelineStepItem(
    phaseNum: String,
    title: String,
    timeframe: String,
    badgeColor: Color,
    isCurrent: Boolean,
    isLast: Boolean,
    keyPoints: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Left timeline column with dot and connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) badgeColor else badgeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = phaseNum,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Color.White else badgeColor,
                        fontSize = 12.sp
                    )
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(90.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right content block
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCurrent) badgeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = timeframe,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            keyPoints.forEach { point ->
                Row(
                    modifier = Modifier.padding(vertical = 1.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChecklistCard(
    currentYear: Int,
    actionStates: Map<String, Boolean>,
    completedCount: Int,
    state: FullCalculationState,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var filterMode by rememberSaveable { mutableStateOf("ALL") } // "ALL", "PENDING", "COMPLETED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("action_checklist_container"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GoodGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoodGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Execution Checklist",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "YEAR $currentYear",
                                color = GoodGreen,
                                fontSize = 9.sp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                        Text(
                            text = "High-impact tax & optimization moves for this year",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                ColorPill(
                    text = "$completedCount / ${ActionMeta.items.size} DONE",
                    color = GoodGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Filter Bar (All / Pending / Done)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pendingCount = ActionMeta.items.size - completedCount
                listOf(
                    "ALL" to "All (${ActionMeta.items.size})",
                    "PENDING" to "Pending ($pendingCount)",
                    "COMPLETED" to "Done ($completedCount)"
                ).forEach { (mode, label) ->
                    val isSelected = filterMode == mode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { filterMode = mode }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredItems = ActionMeta.items.filter { meta ->
                val isDone = actionStates["${currentYear}_${meta.id}"] == true
                when (filterMode) {
                    "PENDING" -> !isDone
                    "COMPLETED" -> isDone
                    else -> true
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filterMode == "PENDING") "All actions completed for Year $currentYear!" else "No completed actions yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                filteredItems.forEachIndexed { index, meta ->
                    val key = "${currentYear}_${meta.id}"
                    val isDone = actionStates[key] == true
                    val impact = state.actionsImpacts[meta.id] ?: 0.0

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isDone) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleAction(currentYear, meta.id, isDone)
                            }
                            .testTag("action_card_${meta.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleAction(currentYear, meta.id, isDone)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("action_cb_${meta.id}")
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    text = meta.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            if (impact > 0.0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoodGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "+${fmtCompact(impact)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = GoodGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (index < filteredItems.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
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

            Spacer(modifier = Modifier.height(96.dp))
        }
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
                        label = { Text("Goal Name (e.g. Dream Cottage)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetYearStr,
                        onValueChange = { targetYearStr = it },
                        label = { Text("Target Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAmountStr,
                        onValueChange = { targetAmountStr = it },
                        label = { Text("Target Capital") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentSavedStr,
                        onValueChange = { currentSavedStr = it },
                        label = { Text("Current Savings") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

    val vDpsAbove = max(0.0, s.dpsOwnContributionMonthly - s.dpsDeductionThresholdMonthly)
    val eDpsAbove = max(0.0, s.eDpsOwnContributionMonthly - s.dpsDeductionThresholdMonthly)

    val vDeductionAnnual = min((vDipMonthly + vDpsAbove) * 12.0, s.taxDeductionCeilingAnnual)
    val eDeductionAnnual = min((eDipMonthly + eDpsAbove) * 12.0, s.taxDeductionCeilingAnnual)
    val totalDeductionAnnual = vDeductionAnnual + eDeductionAnnual

    val vHeadroom = max(0.0, s.taxDeductionCeilingAnnual - (vDipMonthly + vDpsAbove) * 12.0)
    val eHeadroom = max(0.0, s.taxDeductionCeilingAnnual - (eDipMonthly + eDpsAbove) * 12.0)

    val yearlyTaxSaved = state.taxReturnHelper.dipSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. KPI Highlights Row (2x2 Grid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Annual Tax Refund",
                value = fmtCZK(yearlyTaxSaved),
                hint = "Direct tax saving / yr",
                accentColor = GoodGreen,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dipDeduction,
                onShowInfo = onShowInfo
            )
            KpiCard(
                title = "Pension Monthly Deposit",
                value = fmtCZK(totalMonthlyDip),
                hint = "Monthly contribution",
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
            KpiCard(
                title = "State Subsidy Match",
                value = if (dps.youthSubsidyActive) "40% (Youth)" else "20% (Standard)",
                hint = if (dps.youthSubsidyActive) {
                    "${fmtCZK(currentSubsidy)} on ${fmtCZK(s.dpsOwnContributionMonthly)}"
                } else {
                    "${fmtCZK(currentSubsidy)} on ${fmtCZK(s.dpsOwnContributionMonthly)} (40% in 2027)"
                },
                accentColor = BrandGold,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dpsLepsiPenzijko,
                onShowInfo = onShowInfo
            )
            KpiCard(
                title = "DIP + DPS at Age 60",
                value = fmtCompact(dip.dipBalanceAt60 + dps.dpsBalance),
                hint = "Projected pension wealth",
                accentColor = BrandBlue,
                modifier = Modifier.weight(1f),
                info = PlanMetricInfos.dpsAge36,
                onShowInfo = onShowInfo
            )
        }

        // 2. Main Hero Card: DIP & DPS Statutory Tax Shield
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
                    title = "Retirement Tax Shield (DIP & DPS)",
                    subtitle = "Personal tax deduction up to 48 000 Kč per earner",
                    badgeText = "TAX SHIELD",
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

                // 2 Symmetrical Hero Metric Boxes (Clean & Single-Line)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Monthly Deposit",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = fmtCZK(totalMonthlyDip),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                            if (vDipMonthly > 0 || eDipMonthly > 0) {
                                Text(
                                    text = "V: ${fmtCompact(vDipMonthly)} · E: ${fmtCompact(eDipMonthly)}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoodGreen.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Annual Tax Refund",
                                style = MaterialTheme.typography.labelSmall.copy(color = GoodGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "+${fmtCZK(yearlyTaxSaved)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GoodGreen, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = "${String.format("%.0f", s.taxRatePct)}% tax relief",
                                style = MaterialTheme.typography.labelSmall.copy(color = GoodGreen.copy(alpha = 0.85f), fontSize = 10.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Statutory 48,000 CZK Ceiling Progress Bar (Václav)
                val vUtilizedRatio = (vDeductionAnnual / s.taxDeductionCeilingAnnual).toFloat().coerceIn(0f, 1f)
                val vDpsPortionRatio = ((vDpsAbove * 12.0).coerceAtMost(s.taxDeductionCeilingAnnual) / s.taxDeductionCeilingAnnual).toFloat()
                val vDipPortionRatio = ((vDipMonthly * 12.0).coerceAtMost(s.taxDeductionCeilingAnnual - (vDpsAbove * 12.0).coerceAtMost(s.taxDeductionCeilingAnnual)) / s.taxDeductionCeilingAnnual).toFloat().coerceAtLeast(0f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Václav (48k Max)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        ColorPill(
                            text = if (vHeadroom <= 0) "100% MAXED" else "${fmtCZK(vDeductionAnnual)} / ${fmtCompact(s.taxDeductionCeilingAnnual)}",
                            color = if (vHeadroom <= 0) GoodGreen else BrandTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(0.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vDipPortionRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(vDipPortionRatio)
                                    .fillMaxHeight()
                                    .background(GoodGreen, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = if (vDpsPortionRatio > 0f || vUtilizedRatio < 1f) 0.dp else 4.dp, bottomEnd = if (vDpsPortionRatio > 0f || vUtilizedRatio < 1f) 0.dp else 4.dp))
                            )
                        }
                        if (vDpsPortionRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(vDpsPortionRatio)
                                    .fillMaxHeight()
                                    .background(BrandBlue, RoundedCornerShape(topStart = if (vDipPortionRatio > 0f) 0.dp else 4.dp, bottomStart = if (vDipPortionRatio > 0f) 0.dp else 4.dp, topEnd = if (vUtilizedRatio < 1f) 0.dp else 4.dp, bottomEnd = if (vUtilizedRatio < 1f) 0.dp else 4.dp))
                            )
                        }
                        if (1f - vUtilizedRatio > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .weight(1f - vUtilizedRatio)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIP: ${fmtCompact(vDipMonthly * 12.0)} · DPS: ${fmtCompact(vDpsAbove * 12.0)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        )
                        Text(
                            text = if (vHeadroom > 0) "Headroom: ${fmtCompact(vHeadroom)}" else "Full refund active",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (vHeadroom > 0) MaterialTheme.colorScheme.onSurfaceVariant else GoodGreen, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                        )
                    }
                }

                // Eleonora Ceiling (if contributing or employed)
                if (eDipMonthly > 0 || eDpsAbove > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val eUtilizedRatio = (eDeductionAnnual / s.taxDeductionCeilingAnnual).toFloat().coerceIn(0f, 1f)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Eleonora (48k Max)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "${fmtCZK(eDeductionAnnual)} / ${fmtCompact(s.taxDeductionCeilingAnnual)}",
                                color = BrandGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(0.5.dp)
                        ) {
                            if (eUtilizedRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(eUtilizedRatio)
                                        .fillMaxHeight()
                                        .background(BrandGold, RoundedCornerShape(4.dp))
                                )
                            }
                            if (1f - eUtilizedRatio > 0.01f) {
                                Box(modifier = Modifier.weight(1f - eUtilizedRatio).fillMaxHeight())
                            }
                        }
                    }
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
                        text = "Combined household capacity: 96 000 Kč / yr (Václav 48k + Eleonora 48k)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // 3. DIP Scenario Optimization Matrix Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeaderPill(
                    title = "DIP Deposit Optimization Matrix",
                    subtitle = "Monthly deposit tiers & annual tax deduction refund",
                    badgeText = "SCENARIOS",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dip.scenarios.forEach { sc ->
                        val isCurrentTier = abs(sc.monthly - vDipMonthly) < 1.0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentTier) BrandTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentTier) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = fmtCZK(sc.monthly),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        )
                                        if (isCurrentTier) {
                                            ColorPill(
                                                text = "CURRENT",
                                                color = BrandTeal,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                horizontalPadding = 5.dp,
                                                verticalPadding = 1.5.dp
                                            )
                                        }
                                        if (sc.monthly >= 4000.0) {
                                            ColorPill(
                                                text = "OPTIMAL MAX",
                                                color = GoodGreen,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                horizontalPadding = 5.dp,
                                                verticalPadding = 1.5.dp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${fmtCZK(sc.annual)} deposit / yr",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${fmtCZK(sc.annualTaxSaved)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (sc.annualTaxSaved > 0) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    )
                                    Text(
                                        text = if (sc.headroom > 0) "${fmtCompact(sc.headroom)} headroom" else "Maxed",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. DPS "Lepší Penzijko" State Subsidy & Fee Protection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeaderPill(
                    title = "DPS 'Lepší Penzijko' & Fee Protection",
                    subtitle = "State cash matching, statutory fee caps & early liquidity rules",
                    badgeText = "ACT NO. 427/2011",
                    accentColor = BrandGold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Youth Subsidy Rate",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (dps.youthSubsidyActive) "40% (<30 yrs)" else "20% (40% in 2027)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                            )
                            Text(
                                text = if (dps.youthSubsidyActive) "Max ${fmtCZK(s.dpsYouthSubsidyMaxMonthly)}/mo" else "Max ${fmtCZK(s.dpsStandardSubsidyMaxMonthly)}/mo",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Age 36 1/3 Liquidity",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fmtCZK(dps.earlyWithdrawalLimitAt36),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = BrandTeal, fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = "Penalty-free at 120m",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Statutory Fee Cap",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "0.50% TER",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GoodGreen)
                            )
                            Text(
                                text = "vs 1.5% legacy funds",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Statutory Compliance Checklist & Tax Alpha Rules
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeaderPill(
                    title = "Statutory Rules & Tax Exemption Framework",
                    subtitle = "Czech Act No. 586/1992 Coll. & Act No. 427/2011 Coll.",
                    badgeText = "RULES",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoodGreen, modifier = Modifier.size(16.dp))
                            Column {
                                Text("120-Month Rule & Age 60 Gate", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Contract must run for minimum 120 months (10 years) and withdrawals occur after reaching age 60 to retain 100% tax exemption.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoodGreen, modifier = Modifier.size(16.dp))
                            Column {
                                Text("100% Global Index ETF Asset Freedom", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                Text("DIP allows investing directly in global broad-market index ETFs (VWCE, SPPW) avoiding high mutual fund manager fees.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoodGreen, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Employer Contribution Exemption", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Employers can contribute up to 50,000 CZK/yr per employee into DIP/DPS completely exempt from income tax, social security, and health insurance.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun FireMilestonesComparisonCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val milestones = state.fireMilestones
    val investableNetWorth = state.settings.liquidPortfolioCurrent + state.settings.eLiquidPortfolioCurrent +
            state.settings.dpsBalanceCurrent + state.settings.eDpsBalanceCurrent +
            state.settings.dipBalanceCurrent + state.settings.eDipBalanceCurrent

    val items = listOf(
        MilestoneConfig(
            milestone = milestones.coastFire,
            accentColor = BrandTeal,
            icon = Icons.Default.Spa,
            shortLabel = "Coast",
            levelIndex = 1,
            metricInfo = PlanMetricInfos.coastFire
        ),
        MilestoneConfig(
            milestone = milestones.leanFire,
            accentColor = BrandBlue,
            icon = Icons.Default.Home,
            shortLabel = "Lean",
            levelIndex = 2,
            metricInfo = PlanMetricInfos.leanFire
        ),
        MilestoneConfig(
            milestone = milestones.standardFire,
            accentColor = GoodGreen,
            icon = Icons.Default.Shield,
            shortLabel = "Standard",
            levelIndex = 3,
            metricInfo = PlanMetricInfos.standardFire
        ),
        MilestoneConfig(
            milestone = milestones.fatFire,
            accentColor = BrandGold,
            icon = Icons.Default.Diamond,
            shortLabel = "Fat",
            levelIndex = 4,
            metricInfo = PlanMetricInfos.fatFire
        )
    )

    // Current unlocked level determination
    val currentLevel = when {
        milestones.fatFire.isAchieved -> "Level 4: Fat FIRE"
        milestones.standardFire.isAchieved -> "Level 3: Standard FIRE"
        milestones.leanFire.isAchieved -> "Level 2: Lean FIRE"
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
                badgeText = "4 TIERS",
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
                                        text = "⭐ Active Primary FIRE Goal",
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


