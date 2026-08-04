package com.example.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState

data class FireScenarioPreset(
    val id: String,
    val title: String,
    val iconEmoji: String,
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

    val presets = listOf(
        FireScenarioPreset(
            id = "lean_fire",
            title = "Lean FIRE",
            iconEmoji = "🌿",
            description = "Lean 25k CZK/mo lifestyle · 4% SWR",
            applyPreset = { s ->
                s.copy(
                    lifestyleCostAtFireMonthly = 25000.0,
                    safeWithdrawalRatePct = 4.0
                )
            }
        ),
        FireScenarioPreset(
            id = "standard_fire",
            title = "Standard FIRE",
            iconEmoji = "⚖️",
            description = "Standard 33k CZK/mo lifestyle · 3.75% SWR",
            applyPreset = { s ->
                s.copy(
                    lifestyleCostAtFireMonthly = 33000.0,
                    safeWithdrawalRatePct = 3.75
                )
            }
        ),
        FireScenarioPreset(
            id = "fat_fire",
            title = "Fat FIRE",
            iconEmoji = "💎",
            description = "Fat 50k CZK/mo lifestyle · 3.5% SWR",
            applyPreset = { s ->
                s.copy(
                    lifestyleCostAtFireMonthly = 50000.0,
                    safeWithdrawalRatePct = 3.5
                )
            }
        ),
        FireScenarioPreset(
            id = "coast_fire",
            title = "Coast FIRE",
            iconEmoji = "🏖️",
            description = "Coast mode · Zero new monthly portfolio contributions",
            applyPreset = { s ->
                s.copy(
                    portuDcaMonthly = 0.0,
                    dipContributionMonthly = 0.0,
                    dpsOwnContributionMonthly = 0.0
                )
            }
        ),
        FireScenarioPreset(
            id = "reform_max",
            title = "Reform Max",
            iconEmoji = "🇨🇿",
            description = "Max Czech Tax DIP (4k) & Penzijko State Match (1.7k)",
            applyPreset = { s ->
                s.copy(
                    dipContributionMonthly = 4000.0,
                    dpsOwnContributionMonthly = 1700.0,
                    employerRetirementAnnual = 48000.0
                )
            }
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scenario_simulator_chips_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick FIRE Scenario Presets",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "1-Click Apply",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            selectedScenarioId = preset.id
                            val newSettings = preset.applyPreset(state.settings)
                            onApplySettings(newSettings)
                        },
                        label = {
                            Text(
                                text = "${preset.iconEmoji} ${preset.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("scenario_chip_${preset.id}")
                    )
                }
            }

            selectedScenarioId?.let { id ->
                presets.find { it.id == id }?.let { activePreset ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Active Preset: ${activePreset.description}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
