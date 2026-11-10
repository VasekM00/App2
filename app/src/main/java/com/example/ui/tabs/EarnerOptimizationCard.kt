package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import kotlin.math.max
import kotlin.math.min

@Composable
fun EarnerOptimizationCard(
    name: String,
    roleSubtitle: String,
    dpsMonthly: Double,
    dipMonthly: Double,
    subsidyMonthly: Double,
    subsidyCapMonthly: Double = 1700.0,
    taxShieldCapAnnual: Double = 48000.0,
    taxRatePct: Double = 15.0,
    hasTaxableIncome: Boolean = true,
    maxSubsidyAnnual: Double = 4080.0,
    accentColor: Color = BrandTeal,
    modifier: Modifier = Modifier
) {
    val effectiveTaxShieldCapAnnual = if (hasTaxableIncome) taxShieldCapAnnual else 0.0
    val taxShieldCapMonthly = effectiveTaxShieldCapAnnual / 12.0
    val totalTargetMonthly = subsidyCapMonthly + taxShieldCapMonthly

    val subsidyDeposit = min(dpsMonthly, subsidyCapMonthly)
    val subsidyRatio = (subsidyDeposit / subsidyCapMonthly).toFloat().coerceIn(0f, 1f)

    val dpsAbove = max(0.0, dpsMonthly - subsidyCapMonthly)
    val taxShieldMonthly = dipMonthly + dpsAbove
    val taxShieldDeposit = if (hasTaxableIncome) min(taxShieldMonthly, taxShieldCapMonthly) else 0.0
    val taxShieldRatio = if (hasTaxableIncome && taxShieldCapMonthly > 0.0) {
        (taxShieldDeposit / taxShieldCapMonthly).toFloat().coerceIn(0f, 1f)
    } else 0f

    val totalOptimizedMonthly = subsidyDeposit + taxShieldDeposit
    val isMaxed = totalOptimizedMonthly >= (totalTargetMonthly - 0.5)
    val isActive = dpsMonthly > 0.0 || (hasTaxableIncome && dipMonthly > 0.0)

    val annualSubsidy = subsidyMonthly * 12.0
    val annualTaxSaved = if (hasTaxableIncome) taxShieldDeposit * 12.0 * (taxRatePct / 100.0) else 0.0
    val totalAnnualBenefit = annualSubsidy + annualTaxSaved
    val headroomMonthly = max(0.0, totalTargetMonthly - totalOptimizedMonthly)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Monogram Avatar + Name + Role and Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        )
                    }

                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = roleSubtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                if (isActive) {
                    ColorPill(
                        text = if (isMaxed) {
                            if (!hasTaxableIncome) "DPS OPTIMIZED" else "100% OPTIMIZED"
                        } else {
                            "${fmtCZK(totalOptimizedMonthly)} / ${fmtCZK(totalTargetMonthly)}"
                        },
                        color = if (isMaxed) GoodGreen else BrandTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp
                    )
                } else {
                    ColorPill(
                        text = if (!hasTaxableIncome) "0 / 1 700 Kč · PARENTAL" else "0 / ${fmtCZK(totalTargetMonthly)} · UNUSED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two Symmetrical Stage Cards: DPS Subsidy & DIP Tax Shield
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stage 1: DPS State Subsidy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, BrandGold.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STAGE 1: DPS MATCH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BrandGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${String.format(java.util.Locale.getDefault(), "%.0f", subsidyRatio * 100)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BrandGold,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = fmtCZK(subsidyDeposit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "/ ${fmtCZK(subsidyCapMonthly)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BrandGold.copy(alpha = 0.2f))
                        ) {
                            if (subsidyRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(subsidyRatio)
                                        .fillMaxHeight()
                                        .background(BrandGold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "State match",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.5.sp
                                )
                            )
                            Text(
                                text = "+${fmtCZK(subsidyMonthly)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BrandGold,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Stage 2: DIP Tax Shield
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasTaxableIncome) GoodGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(0.5.dp, if (hasTaxableIncome) GoodGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STAGE 2: TAX SHIELD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (hasTaxableIncome) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (hasTaxableIncome) {
                                Text(
                                    text = "${String.format(java.util.Locale.getDefault(), "%.0f", taxShieldRatio * 100)}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = GoodGreen,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (hasTaxableIncome) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = fmtCZK(taxShieldDeposit),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "/ ${fmtCZK(taxShieldCapMonthly)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = "0 Kč (Paused)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (hasTaxableIncome) GoodGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            if (hasTaxableIncome && taxShieldRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(taxShieldRatio)
                                        .fillMaxHeight()
                                        .background(GoodGreen)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasTaxableIncome) "Tax relief" else "Parental leave",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.5.sp
                                )
                            )
                            Text(
                                text = if (hasTaxableIncome) "+${fmtCZK(annualTaxSaved / 12.0)}" else "No tax base",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (hasTaxableIncome) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (hasTaxableIncome) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Earner Footer Row: Annual Benefit & Headroom in dedicated mini surface
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Annual Benefit:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            )
                        )
                        Text(
                            text = "+${fmtCZK(totalAnnualBenefit)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (totalAnnualBenefit > 0.0) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    Text(
                        text = if (headroomMonthly <= 0.5) "Fully optimized" else "Headroom: ${fmtCZK(headroomMonthly)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (headroomMonthly <= 0.5) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (headroomMonthly <= 0.5) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
        }
    }
}
