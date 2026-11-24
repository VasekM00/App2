package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtExactCZK
import java.util.Locale

@Composable
fun MonthlyTransactionsCard(
    yearMonth: String,
    transactions: List<ImportedBankTransactionEntity>,
    onUpdateCategory: ((txId: Long, newCategory: BankTransactionType, rememberMerchant: Boolean, applyToAllMatching: Boolean) -> Unit)? = null,
    onToggleExcluded: ((txId: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (transactions.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAllTransactions by rememberSaveable(yearMonth) { mutableStateOf(false) }
    var editingTxId by rememberSaveable(yearMonth) { mutableStateOf<Long?>(null) }
    val editingTx = editingTxId?.let { id -> transactions.firstOrNull { it.id == id } }

    val filterLabels = remember(transactions) {
        val inflowsCount = transactions.count { it.amount > 0 && !it.isNetted }
        val expensesCount = transactions.count { it.amount < 0 && !it.isNetted && !it.category.startsWith("INVESTMENT") }
        val investmentsCount = transactions.count { it.category.startsWith("INVESTMENT") && !it.isNetted }
        val nettedCount = transactions.count { it.isNetted }
        val uncategorizedCount = transactions.count { it.category == BankTransactionType.UNCATEGORIZED.name && !it.isNetted }
        buildList {
            add("All (${transactions.size})")
            add("Inflows ($inflowsCount)")
            add("Expenses ($expensesCount)")
            add("Investments ($investmentsCount)")
            add("Netted ($nettedCount)")
            if (uncategorizedCount > 0) {
                add("Uncategorized ($uncategorizedCount)")
            }
        }
    }

    val activeFilterIndex = if (selectedFilterIndex in filterLabels.indices) selectedFilterIndex else 0

    val filteredTransactions = remember(transactions, activeFilterIndex, searchQuery) {
        val base = when (activeFilterIndex) {
            1 -> transactions.filter { it.amount > 0 && !it.isNetted }
            2 -> transactions.filter { it.amount < 0 && !it.isNetted && !it.category.startsWith("INVESTMENT") }
            3 -> transactions.filter { it.category.startsWith("INVESTMENT") && !it.isNetted }
            4 -> transactions.filter { it.isNetted }
            5 -> transactions.filter { it.category == BankTransactionType.UNCATEGORIZED.name && !it.isNetted }
            else -> transactions
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            base.filter {
                it.counterpartyName.lowercase(Locale.ROOT).contains(q) ||
                    it.message.lowercase(Locale.ROOT).contains(q) ||
                    it.category.lowercase(Locale.ROOT).contains(q) ||
                    it.bankName.lowercase(Locale.ROOT).contains(q) ||
                    it.date.contains(q)
            }
        }
    }

    val totalNetFiltered = remember(filteredTransactions) {
        filteredTransactions.filter { !it.isNetted }.sumOf { it.amount }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_transactions_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row (Clickable to toggle expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandTeal.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Monthly Transactions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ColorPill(
                                text = "${transactions.size} txs",
                                color = BrandTeal,
                                fontSize = 10.sp,
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 5.dp,
                                verticalPadding = 1.dp
                            )
                        }
                        Text(
                            text = "Itemized statement records for $yearMonth • Tap to edit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search merchant, note, or category",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("transactions_search_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Strip
                ChipWrapRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = activeFilterIndex == index,
                            onClick = { selectedFilterIndex = index },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                                selectedLabelColor = BrandTeal
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Itemized Transactions List
                if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions found matching criteria",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val visibleTransactions = if (showAllTransactions) filteredTransactions else filteredTransactions.take(30)
                        visibleTransactions.forEach { tx ->
                            TransactionRowItem(
                                tx = tx,
                                onClick = { editingTxId = tx.id }
                            )
                        }
                        if (filteredTransactions.size > 30) {
                            TextButton(
                                onClick = { showAllTransactions = !showAllTransactions },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("toggle_all_monthly_transactions_button")
                            ) {
                                Text(
                                    text = if (showAllTransactions) {
                                        "Show first 30 transactions"
                                    } else {
                                        "Show all ${filteredTransactions.size} transactions (+${filteredTransactions.size - 30} more)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BrandTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Summary Footer Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredTransactions.size} of ${transactions.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = BodyFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Non-Netted:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            val netColor = if (totalNetFiltered >= 0) GoodGreen else BadRed
                            Text(
                                text = (if (totalNetFiltered >= 0) "+" else "") + fmtExactCZK(totalNetFiltered),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BodyFontFamily,
                                    color = netColor
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    editingTx?.let { tx ->
        val matchingCount = remember(tx, transactions) {
            transactions.count { other ->
                com.example.util.StatementImportCoordinator.matchesMerchantInMonth(tx, other)
            }
        }
        EditImportedTransactionDialog(
            tx = tx,
            matchingCountInMonth = matchingCount,
            onConfirmCategoryChange = { newCat, rem, applyAll ->
                onUpdateCategory?.invoke(tx.id, newCat, rem, applyAll)
            },
            onToggleExcluded = {
                onToggleExcluded?.invoke(tx.id)
            },
            onDismiss = { editingTxId = null }
        )
    }
}

@Composable
private fun TransactionRowItem(
    tx: ImportedBankTransactionEntity,
    onClick: (() -> Unit)? = null
) {
    val safeAmount = tx.amount.finiteOrZero()
    val isCredit = safeAmount > 0
    val amountColor = when {
        tx.isNetted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        isCredit -> GoodGreen
        else -> MaterialTheme.colorScheme.onSurface
    }

    val displayDate = remember(tx.date) {
        if (tx.date.length >= 10) {
            val parts = tx.date.split("-")
            if (parts.size == 3) {
                val d = parts[2].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (d != null && m != null) "$d.$m." else tx.date
            } else tx.date
        } else tx.date
    }

    val bankBadgeText = remember(tx.bankName) {
        com.example.ui.tabs.bankShortBadge(tx.bankName)
    }

    val bankColor = com.example.ui.tabs.bankBadgeColor(tx.bankName)

    val categoryColor = runCatching {
        getCategoryColorValue(BankTransactionType.valueOf(tx.category))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (tx.isNetted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (tx.isNetted) 0.2f else 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left block: Date + Bank Badge + Description + Category Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = BodyFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )

                ColorPill(
                    text = bankBadgeText,
                    color = bankColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 4.dp,
                    verticalPadding = 1.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    val primaryTitle = when {
                        tx.counterpartyName.isNotBlank() -> tx.counterpartyName
                        tx.message.isNotBlank() -> tx.message
                        else -> tx.category.replace("_", " ")
                    }
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tx.isNetted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (tx.isNetted) TextDecoration.LineThrough else null
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (tx.isNetted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (tx.nettingReason.isNotBlank()) tx.nettingReason else "Netted internal transfer",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            val catLabel = remember(tx.category) {
                                runCatching { formatCategoryLabel(BankTransactionType.valueOf(tx.category)) }
                                    .getOrElse { tx.category.replace("INVESTMENT_", "").replace("SALARY_", "SALARY ").replace("_", " ") }
                            }
                            ColorPill(
                                text = catLabel,
                                color = categoryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                horizontalPadding = 4.dp,
                                verticalPadding = 1.dp
                            )
                            if (tx.message.isNotBlank() && tx.counterpartyName.isNotBlank()) {
                                Text(
                                    text = tx.message,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right block: Amount
            Text(
                text = (if (isCredit) "+" else "") + fmtExactCZK(safeAmount),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodyFontFamily,
                    fontSize = 12.sp,
                    color = amountColor,
                    textDecoration = if (tx.isNetted) TextDecoration.LineThrough else null
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
