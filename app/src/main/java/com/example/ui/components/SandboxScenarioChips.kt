package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FireScenarioPreset
import com.example.domain.PresetProfiles
import com.example.ui.theme.BrandTeal

/**
 * Compact single-select chips for the What-If Sandbox scenario presets.
 * Presets are relative transforms of the saved plan (see [PresetProfiles]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SandboxScenarioChips(
    activePresetId: String?,
    onSelectPreset: (FireScenarioPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePreset = PresetProfiles.ALL_PRESETS.find { it.id == activePresetId }

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetProfiles.ALL_PRESETS.forEach { preset ->
                val isSelected = activePresetId == preset.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = {
                        Text(
                            text = preset.title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                        selectedLabelColor = BrandTeal,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = if (isSelected) BorderStroke(1.dp, BrandTeal) else null,
                    modifier = Modifier.testTag("sandbox_preset_${preset.id}")
                )
            }
        }

        if (activePreset != null && activePreset.id != PresetProfiles.PLAN_BASELINE.id) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BrandTeal.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activePreset.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}
