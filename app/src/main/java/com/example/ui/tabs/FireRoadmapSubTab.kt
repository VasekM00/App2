package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.roundTo10k
import com.example.util.Formatters.roundTo1k
import kotlin.math.roundToInt

@Composable
fun RoadmapAndGoalsSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    var selectedView by rememberSaveable { mutableIntStateOf(0) } // 0 = Action Checklist & Roadmap, 1 = Household Timeline & Goals
    val views = listOf("Action & Tasks", "Household Timeline")
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                views.forEachIndexed { index, name ->
                    val isSelected = selectedView == index
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedView = index
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("roadmap_view_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        when (selectedView) {
            0 -> FireRoadmapSubTab(
                state,
                actionStates,
                onToggleAction,
                onUpdateSettings,
                onShowInfo = onShowInfo,
                onReduceSettings = onReduceSettings
            )
            1 -> HouseholdTimelineSubTab(
                state,
                onUpdateSettings,
                onShowInfo = onShowInfo,
                onReduceSettings = onReduceSettings
            )
        }
    }
}

@Composable
fun HouseholdTimelineSubTab(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
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
        HouseholdTimelineCard(
            settings = state.settings,
            state = state,
            onUpdateSettings = onUpdateSettings,
            onReduceSettings = onReduceSettings
        )
        LifeGoalsSimulatorCard(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onShowInfo = onShowInfo,
            onReduceSettings = onReduceSettings
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FireRoadmapSubTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val snap = state.snapshot
    val sEff = state.effectiveSettings
    val currentYear = sEff.baseYear
    val activeFirePoint = snap.activeFirePoint
    val targetSubtitle = snap.activeFireYear?.let { fireYr ->
        val yrs = (fireYr - currentYear).coerceAtLeast(0)
        "Target: Year $fireYr ($yrs ${if (yrs == 1) "year" else "years"} to goal)"
    } ?: "Target: Beyond 35y horizon (${currentYear + 35}+)"
    val targetWorth = snap.activeFireTargetToday
    val monthlyPassiveIncome = snap.monthlyPassiveIncomeAtFire
    val investableNetWorth = snap.investedLiquidTotal + snap.pensionTotal

    val primaryProgress = snap.fireProgressPct

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
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
                                text = "Your Freedom Plan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = targetSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandTeal.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${primaryProgress.roundToInt()}% of Goal",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = BodyFontFamily,
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useCompact = maxWidth < 360.dp
                    val investableText = if (useCompact) fmtCompact(investableNetWorth) else fmtCZK(investableNetWorth)
                    val targetText = if (useCompact) fmtCompact(targetWorth) else fmtCZK(targetWorth)
                    val swrText = if (useCompact) fmtCompact(monthlyPassiveIncome) else fmtCZK(monthlyPassiveIncome)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.05f)) {
                            Text(
                                text = "Investable Capital",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp),
                                maxLines = 2,
                                softWrap = true
                            )
                            Text(
                                text = investableText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
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
                                text = targetText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = BrandTeal
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
                            )
                        }
                        Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Monthly Income",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp),
                                maxLines = 2,
                                softWrap = true
                            )
                            Text(
                                text = swrText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = GoodGreen
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
                            )
                        }
                    }
                }
            }
        }

        // 2. FIRE Milestones Hierarchy & Two-Bucket Liquidity
        FireMilestonesComparisonCard(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onShowInfo = onShowInfo,
            onReduceSettings = onReduceSettings
        )
        PhasedPreRetirementBaristaCard(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onReduceSettings = onReduceSettings
        )
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
