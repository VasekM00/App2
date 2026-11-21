package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.theme.BadRed
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import com.example.data.SettingsEntity
import com.example.util.Formatters.fmtCompact

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyReserveWidget(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onSelectReserveMode: ((String) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val resolvedMode = state.settings.emergencyReserveMode.ifBlank { "6M" }
    var selectedTargetMode by remember(state.settings.emergencyReserveMode, resolvedMode) {
        mutableStateOf(resolvedMode)
    }

    val monthlyExpense = state.totalLivingCostMonthly.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    val currentLiquidCash = state.currentEmergencyReserve.takeIf { it.isFinite() } ?: 0.0

    val targetAmount = if (selectedTargetMode == state.settings.emergencyReserveMode &&
        state.effectiveEmergencyReserveTargetToday.isFinite() &&
        state.effectiveEmergencyReserveTargetToday > 0.0
    ) {
        state.effectiveEmergencyReserveTargetToday
    } else {
        FinancialEngine.computeEmergencyReserveTargetForMode(
            settings = state.settings,
            totalLivingMonthly = monthlyExpense,
            mode = selectedTargetMode
        )
    }

    val safeTarget = targetAmount.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    val progress = ((currentLiquidCash / safeTarget).takeIf { it.isFinite() } ?: 0.0)
        .coerceIn(0.0, 1.0)
        .toFloat()
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "emergency_reserve_progress"
    )

    val cGreen = GoodGreen
    val cAmber = WarnAmber
    val cRed = BadRed

    val rawPct = ((currentLiquidCash / safeTarget) * 100.0).takeIf { it.isFinite() } ?: 0.0
    val pctCovered = rawPct.roundToInt().coerceIn(0, 100)

    val (statusText, statusColor, statusIcon) = when {
        currentLiquidCash >= safeTarget -> Triple("Fully Funded ($pctCovered%)", cGreen, Icons.Default.CheckCircle)
        currentLiquidCash >= (safeTarget * 0.5) -> Triple("Building ($pctCovered%)", cAmber, Icons.Default.Info)
        else -> Triple("Low Reserve ($pctCovered%)", cRed, Icons.Default.Warning)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val reserveInfo = remember(currentLiquidCash, state.totalLivingCostMonthly, primaryColor) {
        val burnLabel = if (state.totalLivingCostMonthly > 0.0) fmtCompact(state.totalLivingCostMonthly) else "0\u00A0Kč"
        MetricInfo(
            title = "Emergency Runway & Buffer",
            category = "Liquidity & Risk Buffer",
            formulaOrRule = "Runway = Liquid Reserve (${fmtCompact(currentLiquidCash)}) ÷ Monthly Burn ($burnLabel)",
            explanation = "Measures how many months your household can sustain full baseline expenditures with zero incoming salary. Storing this cash in high-yield savings (e.g. ČNB repo-rate linked accounts) insulates your long-term equity DCA portfolio from forced liquidation during market downturns.",
            practicalImplication = "A 6-month buffer provides peace of mind while eliminating sequence-of-returns destruction during sudden market corrections.",
            accentColor = primaryColor
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("emergency_reserve_widget"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onShowInfo != null) Modifier.infoTapHold(reserveInfo, onShowInfo)
                        else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Emergency Fund",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Pill Badge (Actionable reserve status)
                ColorPill(
                    text = statusText,
                    color = statusColor,
                    icon = statusIcon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BodyFontFamily,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    info = reserveInfo,
                    onShowInfo = onShowInfo
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar & Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current: ${fmtCompact(currentLiquidCash)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 2,
                    softWrap = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Target: ${fmtCompact(targetAmount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = BodyFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    softWrap = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Smooth Continuous Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (animProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Target Selector Chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Runway goal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    softWrap = true
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("3M" to "3 months", "6M" to "6 months", "9M" to "9 months", "12M" to "12 months").forEach { (mode, labelText) ->
                        FilterChip(
                            selected = selectedTargetMode == mode,
                            onClick = {
                                selectedTargetMode = mode
                                if (onSelectReserveMode != null) {
                                    onSelectReserveMode(mode)
                                } else if (onReduceSettings != null) {
                                    onReduceSettings { it.copy(emergencyReserveMode = mode) }
                                } else {
                                    onUpdateSettings?.invoke(state.settings.copy(emergencyReserveMode = mode))
                                }
                            },
                            label = {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = labelText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = BodyFontFamily,
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("reserve_chip_${mode.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}
