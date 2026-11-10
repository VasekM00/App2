package com.example.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionsTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    actionStates: Map<String, Boolean> = emptyMap(),
    onApplySettings: (SettingsEntity) -> Unit = {},
    initialSubTab: Int = 0,
    onSubTabSelected: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    val subTabs = listOf("Trajectory", "What-If Sandbox", "Monte Carlo")
    val infoState = rememberMetricInfoState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("projections_tab")
    ) {
        SecondaryTabRow(
            selectedTabIndex = selectedSubTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = {
                        selectedSubTab = index
                        onSubTabSelected?.invoke(index)
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
                    modifier = Modifier.testTag("projections_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> TrajectorySubTab(state = state, ledgerEntries = ledgerEntries, onShowInfo = { infoState.show(it) })
            1 -> WhatIfSandboxSubTab(
                state = state,
                ledgerEntries = ledgerEntries,
                actionStates = actionStates,
                onApplySettings = onApplySettings,
                onShowInfo = { infoState.show(it) }
            )
            2 -> MonteCarloAndStressSubTab(state = state, onShowInfo = { infoState.show(it) })
        }
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}
