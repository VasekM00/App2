package com.example.ui.components.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtExactCZK
import kotlin.math.min
import kotlin.math.roundToInt

enum class WealthLayerType {
    LIQUID,
    DIP,
    DPS,
    CASH
}

private data class DcaStreamItem(
    val label: String,
    val amount: Double,
    val color: Color,
    val layerType: WealthLayerType,
    val subtitle: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DcaAllocationBreakdownBar(
    settings: SettingsEntity,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isSingle = settings.isSingleHousehold
    val portuV = settings.portuDcaMonthly.finiteOrZero().coerceAtLeast(0.0)
    val portuE = if (!isSingle) settings.ePortuDcaMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0
    val dipV = settings.dipContributionMonthly.finiteOrZero().coerceAtLeast(0.0)
    val dipE = if (!isSingle) settings.eDipContributionMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0
    val dpsV = settings.dpsOwnContributionMonthly.finiteOrZero().coerceAtLeast(0.0)
    val dpsE = if (!isSingle) settings.eDpsOwnContributionMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0
    val exemptionMonthlyCap = (settings.employerRetirementExemptionAnnual.finiteOrZero().coerceAtLeast(0.0)) / 12.0
    val empV = min(settings.employerRetirementMonthly.finiteOrZero().coerceAtLeast(0.0), exemptionMonthlyCap)
    val empE = if (!isSingle) min(settings.eEmployerRetirementMonthly.finiteOrZero().coerceAtLeast(0.0), exemptionMonthlyCap) else 0.0

    // Emergency cash monthly allocation toward building target buffer
    val cashMonthly = FinancialEngine.monthlyEmergencyReserveContribution(settings).finiteOrZero()

    val isDark = LocalIsDarkTheme.current
    val cPortuV = BrandTeal
    val cPortuE = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val cDipV = GoodGreen
    val cDipE = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    val cDpsV = BrandBlue
    val cDpsE = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    val cEmpV = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
    val cEmpE = if (isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
    val cCash = BrandGold

    // Interactive Layer Visibility Toggles
    var showLiquid by remember { mutableStateOf(true) }
    var showDip by remember { mutableStateOf(true) }
    var showDps by remember { mutableStateOf(true) }
    var showCash by remember { mutableStateOf(true) }

    val animLiquid by animateFloatAsState(
        targetValue = if (showLiquid) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "animLiquid"
    )
    val animDip by animateFloatAsState(
        targetValue = if (showDip) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "animDip"
    )
    val animDps by animateFloatAsState(
        targetValue = if (showDps) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "animDps"
    )
    val animCash by animateFloatAsState(
        targetValue = if (showCash) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "animCash"
    )

    val streams = remember(
        settings, isSingle, portuV, portuE, dipV, dipE, dpsV, dpsE, empV, empE, cashMonthly,
        isDark, cPortuV, cPortuE, cDipV, cDipE, cDpsV, cDpsE, cEmpV, cEmpE, cCash
    ) {
        buildList {
            if (portuV > 0.0) {
                add(DcaStreamItem(label = "Liquid ETF - ${settings.primaryName.ifBlank { "Primary" }}", amount = portuV, color = cPortuV, layerType = WealthLayerType.LIQUID))
            }
            if (portuE > 0.0) {
                add(DcaStreamItem(label = "Liquid ETF - ${settings.spouseName.ifBlank { "Partner" }}", amount = portuE, color = cPortuE, layerType = WealthLayerType.LIQUID))
            }
            if (dipV > 0.0) {
                add(DcaStreamItem(label = "DIP - ${settings.primaryName}", amount = dipV, color = cDipV, layerType = WealthLayerType.DIP))
            }
            if (dipE > 0.0) {
                add(DcaStreamItem(label = "DIP - ${settings.spouseName}", amount = dipE, color = cDipE, layerType = WealthLayerType.DIP))
            }
            if (dpsV > 0.0) {
                add(DcaStreamItem(label = "DPS - ${settings.primaryName}", amount = dpsV, color = cDpsV, layerType = WealthLayerType.DPS))
            }
            if (dpsE > 0.0) {
                add(DcaStreamItem(label = "DPS - ${settings.spouseName}", amount = dpsE, color = cDpsE, layerType = WealthLayerType.DPS))
            }
            if (empV > 0.0) {
                add(DcaStreamItem(label = "Employer Pension Match - ${settings.primaryName}", amount = empV, color = cEmpV, layerType = WealthLayerType.DPS))
            }
            if (empE > 0.0) {
                add(DcaStreamItem(label = "Employer Pension Match - ${settings.spouseName}", amount = empE, color = cEmpE, layerType = WealthLayerType.DPS))
            }
            if (cashMonthly > 0.0) {
                val isManualReserve = settings.emergencyReserveMonthlyOverride.isFinite() &&
                    settings.emergencyReserveMonthlyOverride >= 0.0
                val reserveSubtitle = if (isManualReserve) {
                    "Manual monthly reserve allocation"
                } else {
                    val reserveTarget = FinancialEngine.effectiveEmergencyReserveTarget(settings).finiteOrZero()
                    val reserveCurrent = settings.emergencyReserveCurrent.finiteOrZero().coerceAtLeast(0.0)
                    "Auto: (${fmtCompact(reserveTarget)} target − ${fmtCompact(reserveCurrent)}) ÷ 24 months"
                }
                add(
                    DcaStreamItem(
                        label = "Emergency Reserve Contribution",
                        amount = cashMonthly,
                        color = cCash,
                        layerType = WealthLayerType.CASH,
                        subtitle = reserveSubtitle
                    )
                )
            }
        }
    }

    fun getStreamFactor(layer: WealthLayerType): Float = when (layer) {
        WealthLayerType.LIQUID -> animLiquid
        WealthLayerType.DIP -> animDip
        WealthLayerType.DPS -> animDps
        WealthLayerType.CASH -> animCash
    }

    val totalDca = streams.sumOf {
        (it.amount * getStreamFactor(it.layerType).toDouble()).finiteOrZero().coerceAtLeast(0.0)
    }.coerceAtLeast(0.0)
    if (streams.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Interactive Legend Filter Chips for Wealth Layers
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            WealthLayerFilterChip(
                label = "Liquid ETF",
                color = BrandTeal,
                isChecked = showLiquid,
                onToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showLiquid = !showLiquid
                },
                modifier = Modifier.testTag("filter_chip_liquid")
            )
            WealthLayerFilterChip(
                label = "DIP (Pension)",
                color = GoodGreen,
                isChecked = showDip,
                onToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDip = !showDip
                },
                modifier = Modifier.testTag("filter_chip_dip")
            )
            WealthLayerFilterChip(
                label = "DPS (Pension)",
                color = BrandBlue,
                isChecked = showDps,
                onToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDps = !showDps
                },
                modifier = Modifier.testTag("filter_chip_dps")
            )
            if (cashMonthly > 0.0) {
                WealthLayerFilterChip(
                    label = "Emergency Reserve",
                    color = cCash,
                    isChecked = showCash,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCash = !showCash
                    },
                    modifier = Modifier.testTag("filter_chip_cash")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Smoothly Animated Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(2.dp)
                .clip(RoundedCornerShape(5.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (totalDca > 0.0) {
                streams.forEach { stream ->
                    val factor = getStreamFactor(stream.layerType).finiteOrZero().coerceIn(0f, 1f)
                    val effectiveAmt = (stream.amount * factor).finiteOrZero().coerceAtLeast(0.0)
                    val ratio = safeFraction(effectiveAmt, totalDca)
                    if (ratio > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(ratio)
                                .fillMaxHeight()
                                .background(
                                    stream.color.copy(alpha = (0.35f + 0.65f * factor).coerceIn(0f, 1f))
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Single-line Pillbox Stream Rows with animated expand/collapse
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            streams.forEach { stream ->
                val factor = getStreamFactor(stream.layerType).finiteOrZero().coerceIn(0f, 1f)
                AnimatedVisibility(
                    visible = factor > 0.05f,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    val pct = (safeFraction(stream.amount * factor, totalDca) * 100f).roundToInt()
                    DcaPillBox(
                        label = stream.label,
                        amount = stream.amount,
                        pct = pct,
                        color = stream.color,
                        subtitle = stream.subtitle
                    )
                }
            }
        }
    }
}

@Composable
private fun DcaPillBox(
    label: String,
    amount: Double,
    pct: Int,
    color: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${fmtExactCZK(amount)} ($pct%)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = color
                ),
                maxLines = 1
            )
        }
    }
}

