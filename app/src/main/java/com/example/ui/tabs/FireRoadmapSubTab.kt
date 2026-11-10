package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.ActionChecklistCard
import com.example.ui.components.HouseholdTimelineCard
import com.example.ui.components.PhasedPreRetirementBaristaCard
import com.example.ui.components.TwoBucketLiquidityBridgeCard
import com.example.ui.components.MetricInfo
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.roundTo10k
import com.example.util.Formatters.roundTo1k

@Composable
fun RoadmapAndGoalsSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    var selectedView by rememberSaveable { mutableIntStateOf(0) } // 0 = Action Checklist & Roadmap, 1 = Household Timeline & Goals
    val views = listOf("Action & Tasks", "Household Timeline")
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
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (selectedView) {
            0 -> FireRoadmapSubTab(state, actionStates, onToggleAction, onUpdateSettings, onShowInfo = onShowInfo)
            1 -> HouseholdTimelineSubTab(state, onUpdateSettings, onShowInfo = onShowInfo)
        }
    }
}

@Composable
fun HouseholdTimelineSubTab(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("household_timeline_subtab"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HouseholdTimelineCard(settings = state.settings, state = state, onUpdateSettings = onUpdateSettings)
        LifeGoalsSimulatorCard(state = state, onUpdateSettings = onUpdateSettings, onShowInfo = onShowInfo)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FireRoadmapSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val currentYear = state.settings.baseYear
    val activeFirePoint = if (state.settings.isSingleHousehold) state.fireSinglePoint else state.fireDualPoint
    val targetSubtitle = if (activeFirePoint != null) {
        "Target: Year ${activeFirePoint.year} (${activeFirePoint.year - currentYear} yrs to goal)"
    } else {
        "Target: Beyond 35y horizon (${currentYear + 35}+)"
    }
    val targetWorth = roundTo10k(state.fireBaseTargetToday)
    val monthlyPassiveIncome = roundTo1k((targetWorth * (state.settings.safeWithdrawalRatePct / 100.0)) / 12.0)
    val investableNetWorth = state.currentLiquidPortfolio + state.currentPensionPortfolio

    val primaryProgress = if (targetWorth > 0) ((investableNetWorth / targetWorth) * 100.0).coerceIn(0.0, 100.0) else 0.0

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
            shape = RoundedCornerShape(18.dp),
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
                                text = targetSubtitle,
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
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = fmtCZK(investableNetWorth),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
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
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = fmtCZK(targetWorth),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
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
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = fmtCZK(monthlyPassiveIncome),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
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

        // 2. FIRE Milestones Hierarchy & Two-Bucket Liquidity
        FireMilestonesComparisonCard(state = state, onUpdateSettings = onUpdateSettings, onShowInfo = onShowInfo)
        PhasedPreRetirementBaristaCard(state = state, onUpdateSettings = onUpdateSettings)
        TwoBucketLiquidityBridgeCard(state = state, onShowInfo = onShowInfo)

        // 3. High-Leverage Execution Checklist
        ActionChecklistCard(
            currentYear = currentYear,
            actionStates = actionStates,
            completedCount = completedActionsCount,
            state = state,
            onToggleAction = onToggleAction,
            onShowInfo = onShowInfo
        )
    }
}
