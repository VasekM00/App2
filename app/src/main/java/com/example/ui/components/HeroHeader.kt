package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtLeverageReduction
import com.example.util.Formatters.fmtPct

@Composable
fun HeroHeader(
    state: FullCalculationState,
    onNavigateToNetWorth: () -> Unit = {},
    onNavigateToEmergencyReserve: () -> Unit = {},
    onNavigateToSavingsRate: () -> Unit = {},
    onOpenQuickGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showMultiplierDialog by remember { mutableStateOf(false) }

    val snap = state.snapshot
    val sEff = state.effectiveSettings
    val pensionTotal = snap.pensionTotal
    val liquidTotal = snap.investedLiquidTotal
    val fireYearLabel = snap.activeFirePoint?.year?.toString() ?: "${sEff.baseYear + 35}+"
    @Suppress("UNUSED_VARIABLE")
    val unusedQuickGuide = onOpenQuickGuide

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("hero_header_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = "TOTAL NET WORTH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToNetWorth()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("hero_stat_net_worth")
            ) {
                Text(
                    text = fmtCZK(snap.netWorthTotal),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = DisplayFontFamily,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        fontFeatureSettings = "tnum",
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Invested ${fmtCompact(liquidTotal)}  ·  Pension ${fmtCompact(pensionTotal)}  ·  Cash ${fmtCompact(snap.emergencyCashTotal)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontFeatureSettings = "tnum",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroStripMetric(
                        label = "Target FIRE Year",
                        value = fireYearLabel,
                        valueColor = BrandBlue,
                        onClick = onNavigateToNetWorth,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("hero_stat_fire_year")
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    HeroStripMetric(
                        label = "Freedom Goal",
                        value = fmtCompact(snap.activeFireTargetToday),
                        valueColor = BrandGold,
                        onClick = onNavigateToNetWorth,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("hero_stat_freedom_goal")
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    HeroStripMetric(
                        label = "Savings Rate",
                        value = fmtPct(snap.savingsRatePct, 1),
                        valueColor = GoodGreen,
                        onClick = onNavigateToSavingsRate,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("hero_stat_savings_rate")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SavingsImpactRow(
                reductionFor100 = snap.fireReductionPer100CzkMonthly,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMultiplierDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_perpetual_multiplier_pill")
            )
        }
    }

    if (showMultiplierDialog) {
        PerpetualFireMultiplierDialog(
            state = state,
            onDismiss = { showMultiplierDialog = false }
        )
    }
}

@Composable
private fun HeroStripMetric(
    label: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                fontFeatureSettings = "tnum",
                color = valueColor
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true
        )
    }
}

@Composable
private fun SavingsImpactRow(
    reductionFor100: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandTeal.copy(alpha = 0.08f),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BrandTeal.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Savings Impact",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "Every 100\u00A0Kč/month saved trims your goal by ${fmtLeverageReduction(reductionFor100)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        softWrap = true
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Savings multiplier details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PerpetualFireMultiplierDialog(
    state: FullCalculationState,
    onDismiss: () -> Unit
) {
    val s = state.effectiveSettings
    val mult = state.snapshot.perpetualFireMultiplier
    val swr = s.safeWithdrawalRatePct
    val buffer = s.safetyBufferPct

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = BrandTeal)
                Text("Savings Multiplier", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reducing recurring monthly expenses shrinks the investment pot you need for financial independence by a large multiple.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Current Live Formula:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                        )
                        Text(
                            text = "(12 months / ${fmtPct(swr)}) * (1 + ${fmtPct(buffer)} buffer) = ${String.format(java.util.Locale.ROOT, "%.1fx", mult)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }

                Text(
                    text = "Goal Reduction Examples:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                val examples = listOf(100.0, 500.0, 1000.0, 2500.0, 5000.0)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    examples.forEach { amount ->
                        val savedCapital = amount * mult
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${fmtCZK(amount)} / month",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                            )
                            Text(
                                text = "-${fmtCZK(savedCapital)} goal",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                    color = GoodGreen
                                )
                            )
                        }
                    }
                }

                Text(
                    text = "Tip: Canceling a 1\u00A0000\u00A0Kč/month subscription cuts your required retirement goal by ${fmtLeverageReduction(1000.0 * mult)}.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}
