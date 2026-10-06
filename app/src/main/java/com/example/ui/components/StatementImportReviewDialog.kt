package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.VerifiedUser

@Composable
fun StatementImportReviewDialog(
    summary: StatementParseSummary,
    onConfirm: (StatementParseSummary) -> Unit,
    onDismiss: () -> Unit,
    onShowNettingAudit: (() -> Unit)? = null,
    onUpdateTransactionCategory: ((Int, BankTransactionType, Boolean) -> Unit)? = null
) {
    var editingTxIndex by remember { mutableStateOf<Int?>(null) }

    val bankColor = when (summary.detectedBank) {
        BankType.MONETA -> BrandTeal
        BankType.CSOB -> BrandGold
        BankType.MBANK -> Color(0xFFE11D48)
        BankType.CESKA_SPORITELNA -> Color(0xFF00539B)
        BankType.KOMERCNI_BANKA -> Color(0xFFB0231E)
        BankType.FIO -> Color(0xFF1E5AA8)
        BankType.RAIFFEISENBANK -> Color(0xFFFFC400)
        BankType.AIR_BANK -> Color(0xFF00A0E3)
        BankType.UNICREDIT -> Color(0xFFE4002B)
        BankType.CREDITAS -> Color(0xFF00695C)
        BankType.MAX_BANKA -> Color(0xFF6A1B9A)
        BankType.PARTNERS -> Color(0xFF00838F)
        BankType.PPF -> Color(0xFF37474F)
        BankType.JT -> Color(0xFF8D6E63)
        BankType.EQUA -> Color(0xFFEF6C00)
        BankType.ING -> Color(0xFFFF6200)
        BankType.OBERBANK -> Color(0xFF00695C)
        BankType.REVOLUT -> Color(0xFF191C1F)
        BankType.WISE -> Color(0xFF7BB661)
        BankType.GENERIC -> MaterialTheme.colorScheme.primary
    }

    val distinctMonths = remember(summary.transactions) {
        summary.transactions.map { it.date.take(7) }.filter { it.matches(Regex("""\d{4}-\d{2}""")) }.distinct().sorted()
    }
    var selectedMonthFilter by remember { mutableStateOf<String?>(null) }

    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filterLabels = listOf("All (${summary.transactions.size})", "Inflows", "Expenses", "Investments", "Transfers")
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = remember(selectedFilterIndex, selectedMonthFilter, searchQuery, summary.transactions) {
        val baseList = if (selectedMonthFilter != null) {
            summary.transactions.filter { it.date.startsWith(selectedMonthFilter!!) }
        } else {
            summary.transactions
        }
        val categoryFiltered = when (selectedFilterIndex) {
            1 -> baseList.filter { it.amount > 0 }
            2 -> baseList.filter {
                it.amount < 0 && it.category != BankTransactionType.INVESTMENT_PORTU &&
                        it.category != BankTransactionType.INVESTMENT_DIP &&
                        it.category != BankTransactionType.INVESTMENT_DPS &&
                        it.category != BankTransactionType.INTERNAL_TRANSFER
            }
            3 -> baseList.filter {
                it.category == BankTransactionType.INVESTMENT_PORTU ||
                        it.category == BankTransactionType.INVESTMENT_DIP ||
                        it.category == BankTransactionType.INVESTMENT_DPS
            }
            4 -> baseList.filter { it.category == BankTransactionType.INTERNAL_TRANSFER }
            else -> baseList
        }
        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val q = searchQuery.trim().lowercase(java.util.Locale.ROOT)
            categoryFiltered.filter {
                it.counterpartyName.lowercase(java.util.Locale.ROOT).contains(q) ||
                    it.message.lowercase(java.util.Locale.ROOT).contains(q) ||
                    it.category.name.lowercase(java.util.Locale.ROOT).contains(q) ||
                    it.date.contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, bankColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("statement_import_review_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = bankColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = bankColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = summary.detectedBank.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bankColor
                                    )
                                )
                                if (distinctMonths.size > 1) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        distinctMonths.forEach { ym ->
                                            ColorPill(
                                                text = ym,
                                                color = bankColor,
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                horizontalPadding = 5.dp,
                                                verticalPadding = 2.dp
                                            )
                                        }
                                    }
                                } else {
                                    ColorPill(
                                        text = summary.yearMonth,
                                        color = bankColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        horizontalPadding = 6.dp,
                                        verticalPadding = 2.dp
                                    )
                                }
                                ColorPill(
                                    text = if (summary.isPdfSource) "PDF EXTRACTED" else "CSV TABULAR",
                                    color = if (summary.isPdfSource) Color(0xFFD97706) else GoodGreen,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 2.dp
                                )
                            }
                            Text(
                                text = if (distinctMonths.size > 1) {
                                    "Split into ${distinctMonths.size} ledgers (${distinctMonths.joinToString(" & ")}) · ${summary.transactions.size} txs"
                                } else {
                                    "Statement Review · ${summary.transactions.size} transactions parsed"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dismiss_statement_review")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (summary.isPdfSource) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PDF statements use heuristic layout parsing. For deterministic precision, download direct CSV/GPC/ABO export from online banking.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4-Card Summary Grid (2x2): Row 1: Inflows & Expenses | Row 2: Net Result & End Balance
                val netFlow = summary.totalInflows - summary.totalExpenses
                val netColor = if (netFlow >= 0) GoodGreen else BadRed

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
                            val diningTotal = summary.transactions.filter { it.category == BankTransactionType.DINING_RESTAURANT }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val shoppingTotal = summary.transactions.filter { it.category == BankTransactionType.SHOPPING_GOODS }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val transportTotal = summary.transactions.filter { it.category == BankTransactionType.TRANSPORTATION }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val subsTotal = summary.transactions.filter { it.category == BankTransactionType.SUBSCRIPTIONS_MEDIA }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val healthTotal = summary.transactions.filter { it.category == BankTransactionType.HEALTH_DRUGSTORE }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val utilTotal = summary.transactions.filter { it.category == BankTransactionType.SERVICES_UTILITIES }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val atmTotal = summary.transactions.filter { it.category == BankTransactionType.ATM_CASH }.sumOf { -it.amount }.coerceAtLeast(0.0)
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
                            val charityTotal = summary.transactions.filter { it.category == BankTransactionType.CHARITY_DONATION }.sumOf { -it.amount }.coerceAtLeast(0.0)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Transaction Filter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (distinctMonths.size > 1) {
                        FilterChip(
                            selected = selectedMonthFilter == null,
                            onClick = { selectedMonthFilter = null },
                            label = { Text("All (${summary.transactions.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bankColor.copy(alpha = 0.18f),
                                selectedLabelColor = bankColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        distinctMonths.forEach { ym ->
                            val mCount = summary.transactions.count { it.date.startsWith(ym) }
                            FilterChip(
                                selected = selectedMonthFilter == ym,
                                onClick = { selectedMonthFilter = ym },
                                label = { Text("$ym ($mCount)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bankColor.copy(alpha = 0.18f),
                                    selectedLabelColor = bankColor
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }

                    filterLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedFilterIndex == index,
                            onClick = { selectedFilterIndex = index },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bankColor.copy(alpha = 0.18f),
                                selectedLabelColor = bankColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter by merchant, note, or date...",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = bankColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("statement_review_search_field")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable List of Parsed Transactions
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredTransactions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions match the selected filter",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("statement_transaction_list"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredTransactions) { tx ->
                                val origIndex = summary.transactions.indexOf(tx)
                                TransactionRowItem(
                                    tx = tx,
                                    onClickCategory = {
                                        if (origIndex != -1) {
                                            editingTxIndex = origIndex
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_statement_import")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = { onConfirm(summary) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        modifier = Modifier
                            .weight(1.6f)
                            .testTag("confirm_statement_import")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (distinctMonths.size > 1) "Split & Save (${distinctMonths.size} Months)" else "Save to Ledger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    editingTxIndex?.let { idx ->
        if (idx in summary.transactions.indices) {
            val tx = summary.transactions[idx]
            CategoryPickerModal(
                tx = tx,
                onSelectCategory = { newCategory, rememberForMerchant ->
                    onUpdateTransactionCategory?.invoke(idx, newCategory, rememberForMerchant)
                    editingTxIndex = null
                },
                onDismiss = { editingTxIndex = null }
            )
        }
    }
}

@Composable
private fun TransactionRowItem(
    tx: ParsedBankTransaction,
    onClickCategory: (() -> Unit)? = null
) {
    val isPositive = tx.amount > 0
    val amountColor = if (isPositive) GoodGreen else BadRed

    val categoryColor = getCategoryColorValue(tx.category)
    val categoryLabel = getCategoryDisplayName(tx.category)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClickCategory != null) Modifier.clickable { onClickCategory() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.date,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f)),
                        modifier = Modifier.then(if (onClickCategory != null) Modifier.clickable { onClickCategory() } else Modifier)
                    ) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }

                val rawTitle = if (tx.counterpartyName.isNotBlank()) tx.counterpartyName else tx.message.ifBlank { "Transaction" }
                val cleanTitle = BankStatementImporter.cleanPaymentDescription(rawTitle)

                val cleanMessage = BankStatementImporter.cleanPaymentDescription(tx.message)
                val distinctMessage = if (cleanMessage.isNotBlank() && !cleanMessage.equals(cleanTitle, ignoreCase = true)) cleanMessage else ""
                val subtitle = when {
                    tx.counterpartyAccount.isNotBlank() && distinctMessage.isNotBlank() -> "${tx.counterpartyAccount} · $distinctMessage"
                    tx.counterpartyAccount.isNotBlank() -> tx.counterpartyAccount
                    distinctMessage.isNotBlank() -> distinctMessage
                    else -> ""
                }

                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tx.category == BankTransactionType.INTERNAL_TRANSFER) {
                    Text(
                        text = "NETTED -> Excluded from income & spending",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoodGreen
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = (if (isPositive) "+" else "") + fmtCZK(tx.amount),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    color = amountColor
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
