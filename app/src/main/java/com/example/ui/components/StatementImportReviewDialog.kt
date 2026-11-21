package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.statement.StatementFilterBar
import com.example.ui.components.statement.StatementImportHeader
import com.example.ui.components.statement.StatementSummaryBar
import com.example.ui.components.statement.StatementTransactionRowItem
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.StatementParseSummary

private val YEAR_MONTH_REGEX = Regex("""\d{4}-\d{2}""")

@Composable
fun StatementImportReviewDialog(
    summary: StatementParseSummary,
    onConfirm: (StatementParseSummary) -> Unit,
    onDismiss: () -> Unit,
    onShowNettingAudit: (() -> Unit)? = null,
    onUpdateTransactionCategory: ((Int, BankTransactionType, Boolean) -> Unit)? = null,
    onUpdateTransactionCategoryBulk: ((Int, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onToggleTransactionExcluded: ((Int) -> Unit)? = null,
    onBulkExcludeMerchant: ((String) -> Unit)? = null
) {
    var editingTxIndex by remember { mutableStateOf<Int?>(null) }

    val bankColor = when (summary.detectedBank) {
        BankType.MONETA -> BrandTeal
        BankType.CSOB -> BrandGold
        BankType.MBANK -> Color(0xFFE11D48)
        BankType.CESKA_SPORITELNA -> Color(0xFF2563EB)
        BankType.KOMERCNI_BANKA -> Color(0xFFDC2626)
        BankType.FIO -> Color(0xFF3B82F6)
        BankType.RAIFFEISENBANK -> Color(0xFFD97706)
        BankType.AIR_BANK -> Color(0xFF00A0E3)
        BankType.UNICREDIT -> Color(0xFFE4002B)
        BankType.CREDITAS -> Color(0xFF0D9488)
        BankType.MAX_BANKA -> Color(0xFF9333EA)
        BankType.PARTNERS -> Color(0xFF00838F)
        BankType.PPF -> Color(0xFF0284C7)
        BankType.JT -> Color(0xFFA1887F)
        BankType.EQUA -> Color(0xFFEF6C00)
        BankType.ING -> Color(0xFFFF6200)
        BankType.OBERBANK -> Color(0xFF0D9488)
        BankType.REVOLUT -> Color(0xFF3B82F6)
        BankType.WISE -> Color(0xFF7BB661)
        BankType.GENERIC -> MaterialTheme.colorScheme.primary
    }

    val distinctMonths = remember(summary.transactions) {
        summary.transactions.map { it.date.take(7) }.filter { it.matches(YEAR_MONTH_REGEX) }.distinct().sorted()
    }
    var selectedMonthFilter by remember { mutableStateOf<String?>(null) }

    val activeTransactionsCount = summary.transactions.count { !it.isExcluded }
    val excludedTransactionsCount = summary.transactions.count { it.isExcluded }

    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filterLabels = remember(summary.transactions, selectedMonthFilter) {
        val monthBase = if (selectedMonthFilter != null) {
            summary.transactions.filter { it.date.startsWith(selectedMonthFilter!!) }
        } else {
            summary.transactions
        }
        val activeCount = monthBase.count { !it.isExcluded }
        val inflowsCount = monthBase.count { !it.isExcluded && it.amount > 0 }
        val expensesCount = monthBase.count {
            !it.isExcluded && it.amount < 0 &&
                it.category != BankTransactionType.INVESTMENT_PORTU &&
                it.category != BankTransactionType.INVESTMENT_DIP &&
                it.category != BankTransactionType.INVESTMENT_DPS &&
                it.category != BankTransactionType.INTERNAL_TRANSFER
        }
        val investmentsCount = monthBase.count {
            !it.isExcluded && (it.category == BankTransactionType.INVESTMENT_PORTU ||
                it.category == BankTransactionType.INVESTMENT_DIP ||
                it.category == BankTransactionType.INVESTMENT_DPS)
        }
        val transfersCount = monthBase.count { !it.isExcluded && it.category == BankTransactionType.INTERNAL_TRANSFER }
        val excludedCount = monthBase.count { it.isExcluded }
        val uncategorizedCount = monthBase.count { !it.isExcluded && it.category == BankTransactionType.UNCATEGORIZED }
        buildList {
            add("Active ($activeCount)")
            add("Inflows ($inflowsCount)")
            add("Expenses ($expensesCount)")
            add("Investments ($investmentsCount)")
            add("Transfers ($transfersCount)")
            add("Excluded ($excludedCount)")
            if (uncategorizedCount > 0) {
                add("Uncategorized ($uncategorizedCount)")
            }
        }
    }
    val activeFilterIndex = if (selectedFilterIndex in filterLabels.indices) selectedFilterIndex else 0
    var searchQuery by remember { mutableStateOf("") }

    val indexedTransactions = remember(summary.transactions) {
        summary.transactions.mapIndexed { idx, tx -> idx to tx }
    }

    val categoryTotals = remember(summary.transactions) {
        val active = summary.transactions.filter { !it.isExcluded }
        mapOf(
            BankTransactionType.DINING_RESTAURANT to active.filter { it.category == BankTransactionType.DINING_RESTAURANT }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.SHOPPING_GOODS to active.filter { it.category == BankTransactionType.SHOPPING_GOODS }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.TRANSPORTATION to active.filter { it.category == BankTransactionType.TRANSPORTATION }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.SUBSCRIPTIONS_MEDIA to active.filter { it.category == BankTransactionType.SUBSCRIPTIONS_MEDIA }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.HEALTH_DRUGSTORE to active.filter { it.category == BankTransactionType.HEALTH_DRUGSTORE }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.SERVICES_UTILITIES to active.filter { it.category == BankTransactionType.SERVICES_UTILITIES }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.ATM_CASH to active.filter { it.category == BankTransactionType.ATM_CASH }.sumOf { -it.amount }.coerceAtLeast(0.0),
            BankTransactionType.CHARITY_DONATION to active.filter { it.category == BankTransactionType.CHARITY_DONATION }.sumOf { -it.amount }.coerceAtLeast(0.0)
        )
    }

    val filteredTransactions = remember(activeFilterIndex, selectedMonthFilter, searchQuery, indexedTransactions) {
        val baseList = if (selectedMonthFilter != null) {
            indexedTransactions.filter { (_, tx) -> tx.date.startsWith(selectedMonthFilter!!) }
        } else {
            indexedTransactions
        }
        val categoryFiltered = when (activeFilterIndex) {
            0 -> baseList.filter { (_, tx) -> !tx.isExcluded }
            1 -> baseList.filter { (_, tx) -> !tx.isExcluded && tx.amount > 0 }
            2 -> baseList.filter { (_, tx) ->
                !tx.isExcluded && tx.amount < 0 && tx.category != BankTransactionType.INVESTMENT_PORTU &&
                        tx.category != BankTransactionType.INVESTMENT_DIP &&
                        tx.category != BankTransactionType.INVESTMENT_DPS &&
                        tx.category != BankTransactionType.INTERNAL_TRANSFER
            }
            3 -> baseList.filter { (_, tx) ->
                !tx.isExcluded && (tx.category == BankTransactionType.INVESTMENT_PORTU ||
                        tx.category == BankTransactionType.INVESTMENT_DIP ||
                        tx.category == BankTransactionType.INVESTMENT_DPS)
            }
            4 -> baseList.filter { (_, tx) -> !tx.isExcluded && tx.category == BankTransactionType.INTERNAL_TRANSFER }
            5 -> baseList.filter { (_, tx) -> tx.isExcluded }
            6 -> baseList.filter { (_, tx) -> !tx.isExcluded && tx.category == BankTransactionType.UNCATEGORIZED }
            else -> baseList
        }
        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val q = searchQuery.trim().lowercase(java.util.Locale.ROOT)
            categoryFiltered.filter { (_, tx) ->
                tx.counterpartyName.lowercase(java.util.Locale.ROOT).contains(q) ||
                    tx.message.lowercase(java.util.Locale.ROOT).contains(q) ||
                    tx.category.name.lowercase(java.util.Locale.ROOT).contains(q) ||
                    tx.date.contains(q)
            }
        }
    }

    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp

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
                .heightIn(min = 320.dp, max = maxDialogHeight)
                .fillMaxHeight(0.90f)
                .imePadding()
                .testTag("statement_import_review_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header Bar
                StatementImportHeader(
                    summary = summary,
                    distinctMonths = distinctMonths,
                    bankColor = bankColor,
                    activeTransactionsCount = activeTransactionsCount,
                    excludedTransactionsCount = excludedTransactionsCount,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Summary KPIs & Breakdown Strip
                StatementSummaryBar(
                    summary = summary,
                    categoryTotals = categoryTotals,
                    onShowNettingAudit = onShowNettingAudit
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filters & Search
                StatementFilterBar(
                    distinctMonths = distinctMonths,
                    selectedMonthFilter = selectedMonthFilter,
                    onSelectMonthFilter = { selectedMonthFilter = it },
                    filterLabels = filterLabels,
                    activeFilterIndex = activeFilterIndex,
                    onSelectFilterIndex = { selectedFilterIndex = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    bankColor = bankColor,
                    allTransactions = summary.transactions
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable List of Parsed Transactions
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredTransactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
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
                            items(
                                items = filteredTransactions,
                                key = { (origIdx, _) -> origIdx }
                            ) { (origIndex, tx) ->
                                StatementTransactionRowItem(
                                    tx = tx,
                                    onClickCategory = {
                                        editingTxIndex = origIndex
                                    },
                                    onToggleExclude = {
                                        onToggleTransactionExcluded?.invoke(origIndex)
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
                            .heightIn(min = 48.dp)
                            .testTag("cancel_statement_import")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { onConfirm(summary) },
                        enabled = activeTransactionsCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            contentColor = OnBrandTeal
                        ),
                        modifier = Modifier
                            .weight(1.6f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_statement_import")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (distinctMonths.size > 1) "Split & Save ($activeTransactionsCount transactions)" else "Save $activeTransactionsCount to Ledger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    editingTxIndex?.let { idx ->
        if (idx in summary.transactions.indices) {
            val tx = summary.transactions[idx]
            val (merchantQuery, matchingCount) = remember(idx, tx, summary.transactions) {
                val rawTitle = if (tx.counterpartyName.isNotBlank()) tx.counterpartyName else tx.message.ifBlank { "Transaction" }
                val cleanTitle = BankStatementImporter.cleanPaymentDescription(rawTitle)
                val query = com.example.util.CzechMerchantCatalog.suggestMerchantSearchQuery(cleanTitle)
                val normQuery = com.example.util.CzechMerchantCatalog.normalize(query)
                val count = if (normQuery.isNotBlank()) {
                    summary.transactions.count { other ->
                        val otherText = "${other.counterpartyName} ${other.message}"
                        com.example.util.CzechMerchantCatalog.normalize(otherText).contains(normQuery)
                    }
                } else 1
                query to count
            }

            CategoryPickerModal(
                tx = tx,
                onSelectCategory = { newCategory, rememberForMerchant, applyToAllMatching ->
                    if (onUpdateTransactionCategoryBulk != null) {
                        onUpdateTransactionCategoryBulk(idx, newCategory, rememberForMerchant, applyToAllMatching)
                    } else {
                        onUpdateTransactionCategory?.invoke(idx, newCategory, rememberForMerchant)
                    }
                    editingTxIndex = null
                },
                onDismiss = { editingTxIndex = null },
                matchingMerchantCount = matchingCount,
                onToggleExclude = {
                    onToggleTransactionExcluded?.invoke(idx)
                },
                onBulkExcludeMerchant = if (matchingCount > 1 && merchantQuery.isNotBlank()) {
                    { onBulkExcludeMerchant?.invoke(merchantQuery) }
                } else null
            )
        }
    }
}
