package com.example.ui.components.statement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtCZK
import com.example.util.StatementParseSummary

@Composable
fun StatementSummaryBar(
    summary: StatementParseSummary,
    categoryTotals: Map<BankTransactionType, Double>,
    onShowNettingAudit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val netFlow = summary.totalInflows - summary.totalExpenses
    val netColor = if (netFlow >= 0) GoodGreen else BadRed

    Column(modifier = modifier.fillMaxWidth()) {
        // 4-Card Summary Grid (2x2): Row 1: Inflows & Expenses | Row 2: Net Result & End Balance
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Inflows & Expenses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Inflows Tile
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Inflows",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmtCZK(summary.totalInflows),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GoodGreen,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expenses Tile
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmtCZK(summary.totalExpenses),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BadRed,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Row 2: Net Result & End Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Net Flow Tile
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Net Result",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = (if (netFlow >= 0) "+" else "") + fmtCZK(netFlow),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = netColor,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Closing Balance Tile
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "End Balance",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary.monthEndBalance?.let { fmtCZK(it) } ?: "--",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BrandGold,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Breakdown Highlight Strip (Salary, Groceries, Portu, Netting)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Categorization Breakdown",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (summary.internalTransfersCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoodGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable(enabled = onShowNettingAudit != null) {
                                onShowNettingAudit?.invoke()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = GoodGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${summary.internalTransfersCount} netted (${fmtCompact(summary.totalNettedAmount)})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = GoodGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (summary.incVaclav > 0) {
                        ColorPill(
                            text = "Václav: " + fmtCompact(summary.incVaclav),
                            color = GoodGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (summary.incEleonora > 0) {
                        ColorPill(
                            text = "Eleonora: " + fmtCompact(summary.incEleonora),
                            color = GoodGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (summary.expGroceries > 0) {
                        ColorPill(
                            text = "Groceries: " + fmtCompact(summary.expGroceries),
                            color = BadRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val diningTotal = categoryTotals[BankTransactionType.DINING_RESTAURANT] ?: 0.0
                    if (diningTotal > 0) {
                        ColorPill(
                            text = "Dining: " + fmtCompact(diningTotal),
                            color = Color(0xFFD97706),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val shoppingTotal = categoryTotals[BankTransactionType.SHOPPING_GOODS] ?: 0.0
                    if (shoppingTotal > 0) {
                        ColorPill(
                            text = "Shopping: " + fmtCompact(shoppingTotal),
                            color = Color(0xFF6366F1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val transportTotal = categoryTotals[BankTransactionType.TRANSPORTATION] ?: 0.0
                    if (transportTotal > 0) {
                        ColorPill(
                            text = "Transport: " + fmtCompact(transportTotal),
                            color = Color(0xFF0284C7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val subsTotal = categoryTotals[BankTransactionType.SUBSCRIPTIONS_MEDIA] ?: 0.0
                    if (subsTotal > 0) {
                        ColorPill(
                            text = "Subs: " + fmtCompact(subsTotal),
                            color = Color(0xFF0D9488),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val healthTotal = categoryTotals[BankTransactionType.HEALTH_DRUGSTORE] ?: 0.0
                    if (healthTotal > 0) {
                        ColorPill(
                            text = "Health: " + fmtCompact(healthTotal),
                            color = Color(0xFFE11D48),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val utilTotal = categoryTotals[BankTransactionType.SERVICES_UTILITIES] ?: 0.0
                    if (utilTotal > 0) {
                        ColorPill(
                            text = "Utilities: " + fmtCompact(utilTotal),
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val atmTotal = categoryTotals[BankTransactionType.ATM_CASH] ?: 0.0
                    if (atmTotal > 0) {
                        ColorPill(
                            text = "ATM: " + fmtCompact(atmTotal),
                            color = Color(0xFF78716C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val charityTotal = categoryTotals[BankTransactionType.CHARITY_DONATION] ?: 0.0
                    if (charityTotal > 0) {
                        ColorPill(
                            text = "Charity: " + fmtCompact(charityTotal),
                            color = Color(0xFFEC4899),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (summary.invPortu > 0) {
                        ColorPill(
                            text = "Portu: " + fmtCompact(summary.invPortu),
                            color = BrandGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (summary.invDps > 0) {
                        ColorPill(
                            text = "DPS: " + fmtCompact(summary.invDps),
                            color = BrandTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                }
            }
        }
    }
}
