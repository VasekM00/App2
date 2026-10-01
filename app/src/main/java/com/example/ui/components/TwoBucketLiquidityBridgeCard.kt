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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import kotlin.math.max

@Composable
fun TwoBucketLiquidityBridgeCard(
    state: FullCalculationState,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val isSingleHh = s.isSingleHousehold
    val liquidBal = if (state.currentLiquidPortfolio > 0.0) state.currentLiquidPortfolio else (s.liquidPortfolioCurrent + if (!isSingleHh) s.eLiquidPortfolioCurrent else 0.0)
    val pensionBal = if (state.currentPensionPortfolio > 0.0) state.currentPensionPortfolio else {
        val dpsBal = s.dpsBalanceCurrent + if (!isSingleHh) s.eDpsBalanceCurrent else 0.0
        val dipBal = s.dipBalanceCurrent + if (!isSingleHh) s.eDipBalanceCurrent else 0.0
        dpsBal + dipBal
    }
    val totalInvestable = liquidBal + pensionBal

    val yearsTo60 = max(0, 60 - s.primaryAge)
    val bridgeFunded = state.isLiquidBridgeFundedToday
    val bridgeRequired = state.liquidBridgeTo60RequiredToday
    val bridgeDeficit = state.liquidBridgeDeficitToday

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("two_bucket_liquidity_bridge_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "Two-Bucket Liquidity & Age 60 Bridge",
                subtitle = "Actuarial separation of taxable Portu from locked DIP/DPS",
                badgeText = if (bridgeFunded) "BRIDGE FUNDED" else "BRIDGE GAP",
                badgeColor = if (bridgeFunded) GoodGreen else BrandGold,
                icon = Icons.Default.Shield,
                accentColor = BrandTeal
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two Buckets Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bucket 1: Liquid Brokerage (Portu)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GoodGreen.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bucket_1_liquid_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bucket 1: Liquid",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoodGreen,
                                    fontSize = 11.5.sp
                                )
                            )
                            ColorPill(
                                text = "PORTU",
                                color = GoodGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 4.dp,
                                verticalPadding = 1.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = fmtCZK(liquidBal),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GoodGreen,
                                fontSize = 15.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Unrestricted early retirement bridge to age 60. Tax-exempt under 3-year time test.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        )
                    }
                }

                // Bucket 2: Statutory Pension (DIP & DPS)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BrandGold.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bucket_2_pension_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bucket 2: Pension",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BrandGold,
                                    fontSize = 11.5.sp
                                )
                            )
                            ColorPill(
                                text = "RULE 60+10",
                                color = BrandGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 4.dp,
                                verticalPadding = 1.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = fmtCZK(pensionBal),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BrandGold,
                                fontSize = 15.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Statutory capital locked to age 60. Early exit triggers 15% tax clawback of deductions.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actuarial Bridge Solvency Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (bridgeFunded) GoodGreen.copy(alpha = 0.08f) else BrandGold.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, if (bridgeFunded) GoodGreen.copy(alpha = 0.35f) else BrandGold.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (bridgeFunded) GoodGreen.copy(alpha = 0.2f) else BrandGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (bridgeFunded) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (bridgeFunded) GoodGreen else BrandGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (yearsTo60 == 0) {
                                "Statutory Age 60 Achieved"
                            } else if (bridgeFunded) {
                                "Age 60 Bridge Solvency: Fully Funded"
                            } else {
                                "Age 60 Bridge Gap: ${fmtCZK(bridgeDeficit)}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (bridgeFunded) GoodGreen else BrandGold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (yearsTo60 == 0) {
                                "Primary earner has reached age 60. Both Bucket 1 (Portu) and Bucket 2 (DIP & DPS) are fully unlocked and liquid."
                            } else if (bridgeFunded) {
                                "Current liquid Portu capital (${fmtCZK(liquidBal)}) exceeds the ${fmtCZK(bridgeRequired)} required to fund all household living burn across the next $yearsTo60 years to age 60 at real return."
                            } else {
                                "Bridging the next $yearsTo60 years of living burn to age 60 requires ${fmtCZK(bridgeRequired)} in liquid capital. Total net worth satisfies overall FIRE targets, but early retirement requires accumulating an additional ${fmtCZK(bridgeDeficit)} in liquid Portu to prevent premature DIP/DPS liquidation."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }
            }

            // Milestone projection note if available
            val bridgePoint = state.fireLiquidBridgePoint
            val targetPoint = if (state.settings.isSingleHousehold) state.fireSinglePoint else state.fireDualPoint
            if (bridgePoint != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Actuarially Viable Full FIRE:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "Year ${bridgePoint.year} (Age ${bridgePoint.age})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BrandTeal,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        if (targetPoint != null && targetPoint.year < bridgePoint.year) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Gross capital milestone reached in Year ${targetPoint.year}, but full retirement requires waiting until Year ${bridgePoint.year} (+${bridgePoint.year - targetPoint.year} yrs) for the liquid bridge to fully fund burn to age 60.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
