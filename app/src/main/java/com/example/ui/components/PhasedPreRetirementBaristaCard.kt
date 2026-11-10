package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
fun PhasedPreRetirementBaristaCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val baristaMilestone = state.fireMilestones.baristaFire
    val isEnabled = s.baristaTransitionEnabled

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("phased_barista_transition_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(BrandGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Coffee,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Barista / Part-Time Ramp",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Phased pre-retirement transition step",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onUpdateSettings?.invoke(s.copy(baristaTransitionEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandTeal
                    ),
                    modifier = Modifier.testTag("barista_transition_toggle")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation & Milestone status
            Text(
                text = "Model shifting from full-time careers to passion/part-time work prior to full FIRE. In this stage, part-time earnings cover living expenses while invested assets continue compounding untouched.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Milestone progress chip
            if (baristaMilestone != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (baristaMilestone.isAchieved) GoodGreen.copy(alpha = 0.12f) else BrandGold.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Barista Target: ${fmtCZK(baristaMilestone.targetAmountToday)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        )
                        Text(
                            text = if (baristaMilestone.isAchieved) "Achieved (Year ${baristaMilestone.estimatedYear ?: s.baseYear})"
                                   else "${baristaMilestone.progressPct.toInt()}% (Est. ${baristaMilestone.estimatedYear ?: "TBD"})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (baristaMilestone.isAchieved) GoodGreen else BrandGold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(14.dp))

                // Editable Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Year
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Year",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var yearText by remember(s.baristaStartYear) { mutableStateOf(s.baristaStartYear.toString()) }
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = {
                                yearText = it
                                val parsed = it.toIntOrNull()
                                if (parsed != null && parsed in 2026..2070) {
                                    onUpdateSettings?.invoke(s.copy(baristaStartYear = parsed))
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth().testTag("barista_start_year_input")
                        )
                    }

                    // Monthly Part-Time Income
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "Monthly Net Income",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var incomeText by remember(s.baristaMonthlyIncome) { mutableStateOf(s.baristaMonthlyIncome.toInt().toString()) }
                        OutlinedTextField(
                            value = incomeText,
                            onValueChange = {
                                incomeText = it
                                val parsed = it.toDoubleOrNull()
                                if (parsed != null && parsed >= 0.0) {
                                    onUpdateSettings?.invoke(s.copy(baristaMonthlyIncome = parsed))
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth().testTag("barista_monthly_income_input")
                        )
                    }

                    // Monthly Portfolio Savings
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Savings / mo",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var savingsText by remember(s.baristaSavingsMonthly) { mutableStateOf(s.baristaSavingsMonthly.toInt().toString()) }
                        OutlinedTextField(
                            value = savingsText,
                            onValueChange = {
                                savingsText = it
                                val parsed = it.toDoubleOrNull()
                                if (parsed != null && parsed >= 0.0) {
                                    onUpdateSettings?.invoke(s.copy(baristaSavingsMonthly = parsed))
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth().testTag("barista_monthly_savings_input")
                        )
                    }
                }
            }
        }
    }
}
