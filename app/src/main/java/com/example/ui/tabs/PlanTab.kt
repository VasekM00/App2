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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean>,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    initialSubTab: Int = 0,
    onSubTabSelected: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    val subTabs = listOf("Tax & Pension (DIP/DPS)", "Roadmap & Timeline")
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
