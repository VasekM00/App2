package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandTeal

data class FireScenarioPreset(
    val id: String,
    val title: String,
    val description: String,
    val applyPreset: (SettingsEntity) -> SettingsEntity
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioSimulatorChips(
    state: FullCalculationState,
    onApplySettings: (SettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedScenarioId by remember { mutableStateOf<String?>(null) }
    var baselineSettings by remember { mutableStateOf(state.settings) }
    val haptic = LocalHapticFeedback.current

    // Keep baseline settings in sync with current user settings when no scenario is active
    if (selectedScenarioId == null) {
        baselineSettings = state.settings
    }

    val presets = listOf(
        FireScenarioPreset(
            id = "base_plan",
            title = "Base Plan (2 Children)",
            description = "Child 1 (2024) & Child 2 (Jan 2027) · Eleonora 2030 (25k) · +37.5k/yr tax credit · 7% return",
            applyPreset = { s ->
                s.copy(
                    childExpensesEnabled = true,
                    child1Enabled = true,
                    child1BirthYear = 2024,
                    child2Enabled = true,
                    child2BirthYear = 2027,
                    eReturnYear = 2030,
                    eStartingSalary = 25000.0,
                    eReinvestedPct = 75.0,
                    portfolioNominalReturnPct = 7.0,
                    cpiInflationPct = 3.0,
                    safeWithdrawalRatePct = 4.0,
                    portuDcaMonthly = 14000.0,
                    vRaiseAnnual = 1300.0,
                    rentGrowthPct = 3.0,
                    monteCarloVolatilityPct = 15.0
                )
            }
        ),
        FireScenarioPreset(
            id = "career_boost",
            title = "Career Acceleration (2030)",
            description = "Eleonora returns 2030 with 38k net · Vaclav raises +2.5k/yr · 85% Eleonora reinvestment",
            applyPreset = { s ->
                s.copy(
                    childExpensesEnabled = true,
                    child1Enabled = true,
                    child1BirthYear = 2024,
                    child2Enabled = true,
                    child2BirthYear = 2027,
                    eReturnYear = 2030,
                    eStartingSalary = 38000.0,
                    eReinvestedPct = 85.0,
                    vRaiseAnnual = 2500.0,
                    portuDcaMonthly = 18000.0,
                    portfolioNominalReturnPct = 7.5
                )
            }
        ),
        FireScenarioPreset(
            id = "stagflation",
            title = "Czech Stagflation",
            description = "5.0% CPI inflation · 5.0% rent growth · 5.5% equity return · 18% volatility",
            applyPreset = { s ->
                s.copy(
                    childExpensesEnabled = true,
                    child1Enabled = true,
                    child1BirthYear = 2024,
                    child2Enabled = true,
                    child2BirthYear = 2027,
                    eReturnYear = 2030,
                    eStartingSalary = 25000.0,
                    cpiInflationPct = 5.0,
                    rentGrowthPct = 5.0,
                    portfolioNominalReturnPct = 5.5,
                    monteCarloVolatilityPct = 18.0
                )
            }
        ),
        FireScenarioPreset(
            id = "coast_fire",
            title = "Coast FIRE at 40",
            description = "Shift to coast mode at age 40 · 0 CZK new DCA · Portfolio compounds untouched",
            applyPreset = { s ->
                s.copy(
                    childExpensesEnabled = true,
                    child1Enabled = true,
                    child1BirthYear = 2024,
                    child2Enabled = true,
                    child2BirthYear = 2027,
                    eReturnYear = 2030,
                    eStartingSalary = 25000.0,
                    portuDcaMonthly = 0.0,
                    dipContributionMonthly = 0.0,
                    dpsOwnContributionMonthly = 0.0,
                    lifestyleCostAtFireMonthly = 35000.0,
                    safeWithdrawalRatePct = 3.5,
                    portfolioNominalReturnPct = 6.5
                )
            }
        )
    )

    val activePreset = remember(selectedScenarioId) {
        presets.find { it.id == selectedScenarioId }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scenario_simulator_chips_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Macro & Life Scenario Presets",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = if (selectedScenarioId != null) "Tap active to revert" else "1-Tap Apply",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = BrandTeal
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = selectedScenarioId == preset.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isSelected) {
                                // 2nd tap: unselect and restore original baseline settings
                                selectedScenarioId = null
                                onApplySettings(baselineSettings)
                            } else {
                                // 1st tap: snapshot original baseline and apply selected preset
                                if (selectedScenarioId == null) {
                                    baselineSettings = state.settings
                                }
                                selectedScenarioId = preset.id
                                val newSettings = preset.applyPreset(baselineSettings)
                                onApplySettings(newSettings)
                            }
                        },
                        label = {
                            Text(
                                text = preset.title,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                            selectedLabelColor = BrandTeal,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (isSelected) BorderStroke(1.dp, BrandTeal) else null,
                        modifier = Modifier.testTag("scenario_chip_${preset.id}")
                    )
                }
            }

            if (activePreset != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandTeal.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Active: ${activePreset.description}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                        Text(
                            text = "Tap to reset",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
