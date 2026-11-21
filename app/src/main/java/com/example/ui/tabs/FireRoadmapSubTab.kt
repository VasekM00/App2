package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.ActionChecklistCard
import com.example.ui.components.ColorPill
import com.example.ui.components.HouseholdTimelineCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.PhasedPreRetirementBaristaCard
import com.example.ui.components.TwoBucketLiquidityBridgeCard
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandTeal
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
    FireRoadmapSubTab(
        state = state,
        actionStates = actionStates,
        onToggleAction = onToggleAction,
        onUpdateSettings = onUpdateSettings,
        onShowInfo = onShowInfo,
        onReduceSettings = onReduceSettings
    )
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
    val targetSubtitle = snap.activeFireYear?.let { fireYr ->
        val yrs = (fireYr - currentYear).coerceAtLeast(0)
        "Target: Year $fireYr ($yrs ${if (yrs == 1) "year" else "years"} to goal)"
    } ?: "Target: Beyond 35y horizon (${currentYear + 35}+)"
    val primaryProgress = snap.fireProgressPct

    val completedActionsCount = remember(actionStates, currentYear) {
        ActionMeta.items.count { meta ->
            actionStates["${currentYear}_${meta.id}"] == true
        }
    }

    var baristaExpanded by rememberSaveable { mutableStateOf(sEff.baristaTransitionEnabled) }
    var bridgeExpanded by rememberSaveable { mutableStateOf(false) }
    var lifeGoalsExpanded by rememberSaveable { mutableStateOf(false) }
    var timelineExpanded by rememberSaveable { mutableStateOf(false) }
    var checklistExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Compact 1-row Freedom Horizon Status Bar (replaces duplicated Hero Card)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fire_roadmap_hero_card"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = targetSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                ColorPill(
                    text = "${primaryProgress.roundToInt()}% of Goal",
                    color = BrandTeal,
                    fontFamily = BodyFontFamily,
                    fontSize = 10.5.sp
                )
            }
        }

        // Primary: Interactive Independence Milestones Matrix
        FireMilestonesComparisonCard(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onShowInfo = onShowInfo,
            onReduceSettings = onReduceSettings
        )

        // Accordion: Barista / Coast Transition
        RoadmapAccordionCard(
            title = "Barista / Phased Transition",
            subtitle = if (sEff.baristaTransitionEnabled) "Active from ${sEff.baristaStartYear}" else "Model part-time work before full FIRE",
            expanded = baristaExpanded,
            onToggle = { baristaExpanded = !baristaExpanded }
        ) {
            PhasedPreRetirementBaristaCard(
                state = state,
                onUpdateSettings = onUpdateSettings,
                onReduceSettings = onReduceSettings
            )
        }

        // Accordion: Pre-60 Liquid Bridge
        RoadmapAccordionCard(
            title = "Two-Bucket Pre-60 Liquidity Bridge",
            subtitle = if (state.isLiquidBridgeFundedToday) "Pre-60 taxable bridge funded" else "Bridge liquid ETFs until pension age 60",
            expanded = bridgeExpanded,
            onToggle = { bridgeExpanded = !bridgeExpanded }
        ) {
            TwoBucketLiquidityBridgeCard(state = state, onShowInfo = onShowInfo)
        }

        // Accordion: Life Goals Simulator
        RoadmapAccordionCard(
            title = "Life Goals Simulator",
            subtitle = "Down payments, education & sabbatical milestones",
            expanded = lifeGoalsExpanded,
            onToggle = { lifeGoalsExpanded = !lifeGoalsExpanded }
        ) {
            LifeGoalsSimulatorCard(
                state = state,
                onUpdateSettings = onUpdateSettings,
                onShowInfo = onShowInfo,
                onReduceSettings = onReduceSettings
            )
        }

        // Accordion: Household Career & Income Timeline
        RoadmapAccordionCard(
            title = "Household Timeline",
            subtitle = "Salary growth, parental phases & future pension shifts",
            expanded = timelineExpanded,
            onToggle = { timelineExpanded = !timelineExpanded }
        ) {
            HouseholdTimelineCard(
                settings = state.settings,
                state = state,
                onUpdateSettings = onUpdateSettings,
                onReduceSettings = onReduceSettings
            )
        }

        // Accordion: Annual Execution Checklist
        RoadmapAccordionCard(
            title = "Annual Action Checklist ($currentYear)",
            subtitle = "$completedActionsCount / ${ActionMeta.items.size} tasks completed",
            expanded = checklistExpanded,
            onToggle = { checklistExpanded = !checklistExpanded }
        ) {
            ActionChecklistCard(
                currentYear = currentYear,
                actionStates = actionStates,
                completedCount = completedActionsCount,
                state = state,
                onToggleAction = onToggleAction,
                onShowInfo = onShowInfo
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RoadmapAccordionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}
