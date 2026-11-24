package com.example.ui.components.statement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtExactCZK
import com.example.util.StatementParseSummary

@Composable
fun StatementSummaryBar(
    summary: StatementParseSummary,
    categoryTotals: Map<BankTransactionType, Double>,
    onShowNettingAudit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    primaryName: String = "Primary",
    partnerName: String = "Partner"
) {
    val safeInflows = summary.totalInflows.finiteOrZero()
    val safeExpenses = summary.totalExpenses.finiteOrZero()
    val netFlow = safeInflows - safeExpenses
    val netColor = if (netFlow >= 0) GoodGreen else BadRed
    val safeEndBalance = summary.monthEndBalance?.takeIf { it.isFinite() }
    val safeNettedAmount = summary.totalNettedAmount.finiteOrZero()
    val safePrimaryInc = summary.incVaclav.finiteOrZero()
    val safePartnerInc = summary.incEleonora.finiteOrZero()
    val safeGroceries = summary.expGroceries.finiteOrZero()
    val safePortu = summary.invPortu.finiteOrZero()
    val safeDps = summary.invDps.finiteOrZero()
    val primaryLabel = primaryName.ifBlank { "Primary" }
    val partnerLabel = partnerName.ifBlank { "Partner" }

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
                    shape = RoundedCornerShape(12.dp),
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
                            text = fmtExactCZK(safeInflows),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
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
                    shape = RoundedCornerShape(12.dp),
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
                            text = fmtExactCZK(safeExpenses),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
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
                    shape = RoundedCornerShape(12.dp),
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
                            text = (if (netFlow >= 0) "+" else "") + fmtExactCZK(netFlow),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
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
                    shape = RoundedCornerShape(12.dp),
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
                            text = safeEndBalance?.let { fmtExactCZK(it) } ?: "--",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    if (summary.internalTransfersCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoodGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clickable(enabled = onShowNettingAudit != null) {
                                    onShowNettingAudit?.invoke()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                                    text = "${summary.internalTransfersCount} netted (${fmtCompact(safeNettedAmount)})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = GoodGreen,
                                        fontSize = 10.sp,
                                        fontFamily = BodyFontFamily,
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
                    if (safePrimaryInc > 0) {
                        ColorPill(
                            text = "$primaryLabel: " + fmtCompact(safePrimaryInc),
                            color = GoodGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (safePartnerInc > 0) {
                        ColorPill(
                            text = "$partnerLabel: " + fmtCompact(safePartnerInc),
                            color = GoodGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (safeGroceries > 0) {
                        ColorPill(
                            text = "Groceries: " + fmtCompact(safeGroceries),
                            color = BadRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val diningTotal = (categoryTotals[BankTransactionType.DINING_RESTAURANT] ?: 0.0).finiteOrZero()
                    if (diningTotal > 0) {
                        ColorPill(
                            text = "Dining: " + fmtCompact(diningTotal),
                            color = Color(0xFFD97706),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val shoppingTotal = (categoryTotals[BankTransactionType.SHOPPING_GOODS] ?: 0.0).finiteOrZero()
                    if (shoppingTotal > 0) {
                        ColorPill(
                            text = "Shopping: " + fmtCompact(shoppingTotal),
                            color = Color(0xFF6366F1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val transportTotal = (categoryTotals[BankTransactionType.TRANSPORTATION] ?: 0.0).finiteOrZero()
                    if (transportTotal > 0) {
                        ColorPill(
                            text = "Transport: " + fmtCompact(transportTotal),
                            color = Color(0xFF0284C7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val subsTotal = (categoryTotals[BankTransactionType.SUBSCRIPTIONS_MEDIA] ?: 0.0).finiteOrZero()
                    if (subsTotal > 0) {
                        ColorPill(
                            text = "Subs: " + fmtCompact(subsTotal),
                            color = Color(0xFF0D9488),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val healthTotal = (categoryTotals[BankTransactionType.HEALTH_DRUGSTORE] ?: 0.0).finiteOrZero()
                    if (healthTotal > 0) {
                        ColorPill(
                            text = "Health: " + fmtCompact(healthTotal),
                            color = Color(0xFFE11D48),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val utilTotal = (categoryTotals[BankTransactionType.SERVICES_UTILITIES] ?: 0.0).finiteOrZero()
                    if (utilTotal > 0) {
                        ColorPill(
                            text = "Utilities: " + fmtCompact(utilTotal),
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val atmTotal = (categoryTotals[BankTransactionType.ATM_CASH] ?: 0.0).finiteOrZero()
                    if (atmTotal > 0) {
                        ColorPill(
                            text = "ATM: " + fmtCompact(atmTotal),
                            color = Color(0xFF78716C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    val charityTotal = (categoryTotals[BankTransactionType.CHARITY_DONATION] ?: 0.0).finiteOrZero()
                    if (charityTotal > 0) {
                        ColorPill(
                            text = "Charity: " + fmtCompact(charityTotal),
                            color = Color(0xFFEC4899),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (safePortu > 0) {
                        ColorPill(
                            text = "Portu: " + fmtCompact(safePortu),
                            color = BrandGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    if (safeDps > 0) {
                        ColorPill(
                            text = "DPS: " + fmtCompact(safeDps),
                            color = BrandTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodyFontFamily,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                }
            }
        }
    }
}
