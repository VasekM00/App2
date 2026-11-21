package com.example.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull
import kotlin.math.roundToInt

@Composable
fun PhasedPreRetirementBaristaCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val baristaMilestone = state.fireMilestones.baristaFire
    val isEnabled = s.baristaTransitionEnabled

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("phased_barista_transition_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            // Header with Switch
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
                            .size(36.dp)
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Barista / Part-Time Ramp",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = "Phased pre-retirement transition step",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enabled ->
                        if (onUpdateSettings != null || onReduceSettings != null) {
                            dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings) {
                                it.copy(baristaTransitionEnabled = enabled)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnBrandTeal,
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
                    shape = RoundedCornerShape(12.dp),
                    color = if (baristaMilestone.isAchieved) GoodGreen.copy(alpha = 0.12f) else BrandGold.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (baristaMilestone.isAchieved) GoodGreen.copy(alpha = 0.3f) else BrandGold.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val safeTargetToday = baristaMilestone.targetAmountToday.finiteOrZero()
                        val safeProgressPct = baristaMilestone.progressPct.finiteOrZero()
                        Text(
                            text = "Barista Target: ${fmtCZK(safeTargetToday)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            text = if (baristaMilestone.isAchieved) "Achieved (Year ${baristaMilestone.estimatedYear ?: s.baseYear})"
                                   else "${safeProgressPct.roundToInt()}% (Est. ${baristaMilestone.estimatedYear ?: "TBD"})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                color = if (baristaMilestone.isAchieved) GoodGreen else BrandGold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Editable Fields: Responsive 2-tier layout so labels and amounts never squish
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start Year
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start Year",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            var yearText by rememberSaveable { mutableStateOf(s.baristaStartYear.toString()) }
                            var yearFocused by remember { mutableStateOf(false) }
                            LaunchedEffect(s.baristaStartYear) {
                                if (!yearFocused && yearText.toIntOrNull() != s.baristaStartYear) {
                                    yearText = s.baristaStartYear.toString()
                                }
                            }
                            OutlinedTextField(
                                value = yearText,
                                onValueChange = {
                                    yearText = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null && parsed in 1950..2200 && (onUpdateSettings != null || onReduceSettings != null)) {
                                        dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings) { cur ->
                                            cur.copy(baristaStartYear = parsed)
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { yearFocused = it.isFocused }
                                    .testTag("barista_start_year_input")
                            )
                        }

                        // Monthly Part-Time Income
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monthly Net Income",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            var incomeText by rememberSaveable { mutableStateOf(fmtEditableAmount(s.baristaMonthlyIncome)) }
                            var incomeFocused by remember { mutableStateOf(false) }
                            LaunchedEffect(s.baristaMonthlyIncome) {
                                if (!incomeFocused && incomeText.parseUserAmountOrNull() != s.baristaMonthlyIncome) {
                                    incomeText = fmtEditableAmount(s.baristaMonthlyIncome)
                                }
                            }
                            OutlinedTextField(
                                value = incomeText,
                                onValueChange = {
                                    incomeText = it
                                    val parsed = it.parseUserAmountOrNull()?.takeIf { v -> v.isFinite() && v >= 0.0 }
                                    if (parsed != null && (onUpdateSettings != null || onReduceSettings != null)) {
                                        dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings) { cur ->
                                            cur.copy(baristaMonthlyIncome = parsed)
                                        }
                                    }
                                },
                                singleLine = true,
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { incomeFocused = it.isFocused }
                                    .testTag("barista_monthly_income_input")
                            )
                        }
                    }

                    // Monthly Portfolio Savings
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Monthly Portfolio Savings",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var savingsText by rememberSaveable { mutableStateOf(fmtEditableAmount(s.baristaSavingsMonthly)) }
                        var savingsFocused by remember { mutableStateOf(false) }
                        LaunchedEffect(s.baristaSavingsMonthly) {
                            if (!savingsFocused && savingsText.parseUserAmountOrNull() != s.baristaSavingsMonthly) {
                                savingsText = fmtEditableAmount(s.baristaSavingsMonthly)
                            }
                        }
                        OutlinedTextField(
                            value = savingsText,
                            onValueChange = {
                                savingsText = it
                                val parsed = it.parseUserAmountOrNull()?.takeIf { v -> v.isFinite() && v >= 0.0 }
                                if (parsed != null && (onUpdateSettings != null || onReduceSettings != null)) {
                                    dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings) { cur ->
                                        cur.copy(baristaSavingsMonthly = parsed)
                                    }
                                }
                            },
                            singleLine = true,
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { savingsFocused = it.isFocused }
                                .testTag("barista_monthly_savings_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val safeLivingMonthly = state.totalLivingCostMonthly.finiteOrZero()
                val safeBaristaIncome = s.baristaMonthlyIncome.finiteOrZero()
                val safeBaristaSavings = s.baristaSavingsMonthly.finiteOrZero()
                val livingBalance = safeBaristaIncome - safeLivingMonthly
                val netAfterSavings = livingBalance - safeBaristaSavings
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (livingBalance >= 0.0) GoodGreen else BrandGold).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, (if (livingBalance >= 0.0) GoodGreen else BrandGold).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            netAfterSavings >= 0.0 ->
                                "Barista Cash Flow: +${fmtCZK(netAfterSavings)}/month surplus above living costs (${fmtCZK(safeLivingMonthly)}) and planned savings (${fmtCZK(safeBaristaSavings)})."
                            livingBalance >= 0.0 ->
                                "Barista Cash Flow: Covers 100% of living costs (${fmtCZK(safeLivingMonthly)}) with ${fmtCZK(livingBalance)}/month left to invest (${fmtCZK(-netAfterSavings)}/month below savings target)."
                            else ->
                                "Barista Coverage: Covers ${fmtCZK(safeBaristaIncome)} of ${fmtCZK(safeLivingMonthly)} living costs; ${fmtCZK(-livingBalance)}/month drawn from portfolio bridge."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
