package com.example.ui.tabs

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
import androidx.compose.ui.text.style.TextOverflow
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("plan_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> PensionSubTab(state)
            1 -> RoadmapAndGoalsSubTab(state, actionStates, onToggleAction, onUpdateSettings)
        }
    }
}

@Composable
private fun RoadmapAndGoalsSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        when (selectedView) {
            0 -> FireRoadmapSubTab(state, actionStates, onToggleAction, onUpdateSettings)
            1 -> LifeGoalsSimulatorSubTab(state, onUpdateSettings)
        }
    }
}

@Composable
private fun FireRoadmapSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
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
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp),
                            maxLines = 2,
                            softWrap = true
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
                            text = "Target Capital (Today)",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp),
                            maxLines = 2,
                            softWrap = true
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
                            text = "Monthly SWR Flow",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp),
                            maxLines = 2,
                            softWrap = true
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
            FireMilestonesComparisonCard(state = state, onUpdateSettings = onUpdateSettings)
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
                    "Maximize DIP contributions (CZK 48k/yr tax deduction)",
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
                        label = { Text("Target Capital (CZK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentSavedStr,
                        onValueChange = { currentSavedStr = it },
                        label = { Text("Current Savings (CZK)") },
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
private fun PensionSubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()
    val dps = state.dps
    val currentSubsidy = FinancialEngine.dpsSubsidy(state.settings.dpsOwnContributionMonthly, state.settings.primaryAge, state.settings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // KPI Highlights
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val itemWidth = Modifier.fillMaxWidth(0.48f)

            KpiCard(
                title = "State Subsidy Match",
                value = if (dps.youthSubsidyActive) "40% (Youth)" else "20%",
                hint = if (dps.youthSubsidyActive) "Youth match active" else "Standard rate active",
                accentColor = BrandTeal,
                modifier = itemWidth
            )

            KpiCard(
                title = "Monthly Subsidy",
                value = fmtCZK(currentSubsidy),
                hint = "On ${fmtCZK(state.settings.dpsOwnContributionMonthly)} deposit",
                accentColor = BrandGold,
                modifier = itemWidth
            )

            KpiCard(
                title = "Annual DIP Tax Shield",
                value = fmtCZK(state.taxReturnHelper.dipSaving),
                hint = "Direct tax saving / yr",
                accentColor = GoodGreen,
                modifier = itemWidth
            )

            KpiCard(
                title = "DPS at Age 60",
                value = fmtCompact(dps.dpsBalance),
                hint = "Projected pension wealth",
                accentColor = BrandTeal,
                modifier = itemWidth
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DIP Scenarios Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "DIP Tax Deduction Matrix",
                    subtitle = "Tax deductions under the 48,000 CZK combined annual ceiling",
                    badgeText = "DIP SHIELD",
                    accentColor = GoodGreen
                )

                Spacer(modifier = Modifier.height(14.dp))

                state.dip.scenarios.forEach { scenario ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${fmtCZK(scenario.monthly)} / month",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Annual deposit: ${fmtCZK(scenario.annual)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        ColorPill(
                            text = "Saves ${fmtCZK(scenario.annualTaxSaved)}/yr",
                            color = BrandTeal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 8.dp,
                            verticalPadding = 4.dp
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun FireMilestonesComparisonCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
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
            levelIndex = 1
        ),
        MilestoneConfig(
            milestone = milestones.leanFire,
            accentColor = BrandBlue,
            icon = Icons.Default.Home,
            shortLabel = "Lean",
            levelIndex = 2
        ),
        MilestoneConfig(
            milestone = milestones.standardFire,
            accentColor = GoodGreen,
            icon = Icons.Default.Shield,
            shortLabel = "Standard",
            levelIndex = 3
        ),
        MilestoneConfig(
            milestone = milestones.fatFire,
            accentColor = BrandGold,
            icon = Icons.Default.Diamond,
            shortLabel = "Fat",
            levelIndex = 4
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = m.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                            overflow = TextOverflow.Ellipsis,
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
                            overflow = TextOverflow.Ellipsis,
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
                                    fontSize = 10.sp
                                ),
                                maxLines = 2,
                                softWrap = true
                            )
                            Text(
                                text = fmtCZK(activeTarget),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Monthly SWR Flow",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                ),
                                maxLines = 2,
                                softWrap = true
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
                                text = if (activeM.isAchieved) "Portfolio Surplus" else "Remaining Gap",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                ),
                                maxLines = 2,
                                softWrap = true
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
    val levelIndex: Int
)


