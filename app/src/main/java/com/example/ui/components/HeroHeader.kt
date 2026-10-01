package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandGoldDarkTheme
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtLeverageReduction
import com.example.util.Formatters.fmtPct

@Composable
fun HeroHeader(
    state: FullCalculationState,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onOpenExportReportDialog: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNavigateToNetWorth: () -> Unit = {},
    onNavigateToEmergencyReserve: () -> Unit = {},
    onNavigateToSavingsRate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showMultiplierDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_header_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Unspecified
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Financial Dashboard",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onOpenExportReportDialog,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .testTag("export_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Export Summary Report",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onToggleDarkTheme,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStatChip(
                        label = "Net Worth",
                        value = fmtCompact(state.netWorthTotal),
                        accentColor = BrandGoldDarkTheme,
                        onClick = onNavigateToNetWorth,
                        modifier = Modifier.weight(1f).testTag("hero_stat_net_worth")
                    )
                    MiniStatChip(
                        label = "Emergency Reserve",
                        value = fmtCompact(state.settings.emergencyReserveCurrent),
                        onClick = onNavigateToEmergencyReserve,
                        modifier = Modifier.weight(1f).testTag("hero_stat_emergency_reserve")
                    )
                    MiniStatChip(
                        label = "Savings Rate",
                        value = fmtPct(state.savingsRatePct, 1),
                        onClick = onNavigateToSavingsRate,
                        modifier = Modifier.weight(1f).testTag("hero_stat_savings_rate")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                PerpetualFireMultiplierPill(
                    multiplier = state.perpetualFireMultiplier,
                    reductionFor100 = state.fireReductionPer100CzkMonthly,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMultiplierDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("hero_perpetual_multiplier_pill")
                )
            }
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
fun MiniStatChip(
    label: String,
    value: String,
    accentColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = if (onClick != null) {
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        } else modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                softWrap = true
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun PerpetualFireMultiplierPill(
    multiplier: Double,
    reductionFor100: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PERPETUAL FIRE LEVERAGE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrandTeal.copy(alpha = 0.22f),
                            contentColor = BrandTeal
                        ) {
                            Text(
                                text = "${String.format(java.util.Locale.ROOT, "%.0fx", multiplier)} LEVERAGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "100 CZK/mo saved cuts FIRE target by ${fmtLeverageReduction(reductionFor100)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Leverage details",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun PerpetualFireMultiplierDialog(
    state: FullCalculationState,
    onDismiss: () -> Unit
) {
    val s = state.settings
    val mult = state.perpetualFireMultiplier
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
                Text("Perpetual FIRE Multiplier", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Every crown saved in permanent monthly living expenses reduces your required FIRE portfolio by a massive multiple due to the Safe Withdrawal Rate rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Current Live Formula:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                        )
                        Text(
                            text = "(12 months / ${fmtPct(swr)}) * (1 + ${fmtPct(buffer)} buffer) = ${String.format(java.util.Locale.ROOT, "%.1fx", mult)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Text(
                    text = "Permanent Capital Reduction:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                val examples = listOf(100.0, 500.0, 1000.0, 2500.0, 5000.0)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    examples.forEach { amount ->
                        val savedCapital = amount * mult
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${fmtCZK(amount)} / month",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "-${fmtCZK(savedCapital)} target",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = GoodGreen
                                )
                            )
                        }
                    }
                }

                Text(
                    text = "Tip: Trimming a recurring 1,000 CZK subscription cuts ${fmtLeverageReduction(1000.0 * mult)} from your required FIRE target.",
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
