package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandTeal

@Composable
fun ProjectionsTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    actionStates: Map<String, Boolean> = emptyMap(),
    onApplySettings: (SettingsEntity) -> Unit = {},
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit = { _, _, _ -> },
    initialSubTab: Int = 0,
    onSubTabSelected: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 4)) }
    val subTabs = listOf("Forecast", "What-If", "Stress", "Pension", "Goals")
    val infoState = rememberMetricInfoState()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("projections_tab")
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                subTabs.forEachIndexed { index, title ->
                    val isSelected = selectedSubTab == index
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedSubTab = index
                                    onSubTabSelected?.invoke(index)
                                }
                            )
                            .testTag("projections_subtab_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        when (selectedSubTab) {
            0 -> TrajectorySubTab(
                state = state,
                ledgerEntries = ledgerEntries,
                onOpenSandbox = { selectedSubTab = 1; onSubTabSelected?.invoke(1) },
                onShowInfo = { infoState.show(it) }
            )
            1 -> WhatIfSandboxSubTab(state, ledgerEntries, actionStates, onApplySettings) { infoState.show(it) }
            2 -> MonteCarloAndStressSubTab(state = state, onShowInfo = { infoState.show(it) })
            3 -> PensionSubTab(state = state, onShowInfo = { infoState.show(it) })
            4 -> RoadmapAndGoalsSubTab(state, actionStates, onToggleAction, onApplySettings, onReduceSettings) { infoState.show(it) }
        }
    }

    MetricInfoDialog(info = infoState.currentInfo, onDismiss = { infoState.dismiss() })
}
