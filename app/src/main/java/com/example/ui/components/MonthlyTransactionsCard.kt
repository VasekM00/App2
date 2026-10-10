package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import java.util.Locale

@Composable
fun MonthlyTransactionsCard(
    yearMonth: String,
    transactions: List<ImportedBankTransactionEntity>,
    modifier: Modifier = Modifier
) {
    if (transactions.isEmpty()) return

    var isExpanded by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }

    val filterLabels = remember(transactions) {
        val inflowsCount = transactions.count { it.amount > 0 && !it.isNetted }
        val expensesCount = transactions.count { it.amount < 0 && !it.isNetted && !it.category.startsWith("INVESTMENT") }
        val investmentsCount = transactions.count { it.category.startsWith("INVESTMENT") }
        val nettedCount = transactions.count { it.isNetted }
        listOf(
            "All (${transactions.size})",
            "Inflows ($inflowsCount)",
            "Expenses ($expensesCount)",
            "Investments ($investmentsCount)",
            "Netted ($nettedCount)"
        )
    }

    val filteredTransactions = remember(transactions, selectedFilterIndex, searchQuery) {
        val base = when (selectedFilterIndex) {
            1 -> transactions.filter { it.amount > 0 && !it.isNetted }
            2 -> transactions.filter { it.amount < 0 && !it.isNetted && !it.category.startsWith("INVESTMENT") }
            3 -> transactions.filter { it.category.startsWith("INVESTMENT") }
            4 -> transactions.filter { it.isNetted }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Monthly Transactions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "${transactions.size} txs",
                                color = BrandTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 5.dp,
                                verticalPadding = 1.dp
                            )
                        }
                        Text(
                            text = "Itemized statement records for $yearMonth",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
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
                            text = "Search merchant, note, or category...",
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
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(14.dp)
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
                        .height(48.dp)
                        .testTag("transactions_search_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedFilterIndex == index,
                            onClick = { selectedFilterIndex = index },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
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
                        // Display up to 30 transactions with scrollable or capped view
                        filteredTransactions.take(30).forEach { tx ->
                            TransactionRowItem(tx = tx)
                        }
                        if (filteredTransactions.size > 30) {
                            Text(
                                text = "+ ${filteredTransactions.size - 30} more transactions (use search to filter)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(vertical = 4.dp)
                            )
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                text = (if (totalNetFiltered >= 0) "+" else "") + fmtCZK(totalNetFiltered),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = netColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowItem(tx: ImportedBankTransactionEntity) {
    val isCredit = tx.amount > 0
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
        when {
            tx.bankName.contains("MONETA", ignoreCase = true) -> "MON"
            tx.bankName.contains("CSOB", ignoreCase = true) -> "ČSOB"
            tx.bankName.contains("MBANK", ignoreCase = true) -> "mB"
            tx.bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> "ČS"
            tx.bankName.contains("KOMERCNI_BANKA", ignoreCase = true) -> "KB"
            tx.bankName.contains("FIO", ignoreCase = true) -> "Fio"
            tx.bankName.contains("AIR_BANK", ignoreCase = true) -> "Air"
            tx.bankName.contains("RAIFFEISENBANK", ignoreCase = true) -> "RB"
            tx.bankName.contains("REVOLUT", ignoreCase = true) -> "Rev"
            else -> tx.bankName.take(3).uppercase()
        }
    }

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cGreen = GoodGreen
    val cOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val cPrimary = MaterialTheme.colorScheme.primary

    val bankColor = when {
        tx.bankName.contains("MONETA", ignoreCase = true) -> cTeal
        tx.bankName.contains("CSOB", ignoreCase = true) -> cGold
        tx.bankName.contains("MBANK", ignoreCase = true) -> Color(0xFFE11D48)
        tx.bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> Color(0xFF2563EB)
        tx.bankName.contains("FIO", ignoreCase = true) -> Color(0xFF16A34A)
        else -> cTeal
    }

    val categoryColor = when {
        tx.category == "HOUSING_RENT" -> Color(0xFFD97706)
        tx.category == "GROCERIES" -> Color(0xFF16A34A)
        tx.category == "DINING_RESTAURANT" -> Color(0xFFEA580C)
        tx.category == "TRANSPORTATION" -> Color(0xFF0284C7)
        tx.category == "SHOPPING_GOODS" -> Color(0xFF6366F1)
        tx.category == "HEALTH_DRUGSTORE" -> Color(0xFFE11D48)
        tx.category == "SUBSCRIPTIONS_MEDIA" -> Color(0xFF0D9488)
        tx.category.startsWith("INVESTMENT") -> cGold
        tx.category.startsWith("SALARY") -> cGreen
        tx.category == "PARENTAL_BENEFIT" -> cGreen
        tx.category == "INTERNAL_TRANSFER" -> cOnSurfaceVariant
        else -> cPrimary
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (tx.isNetted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (tx.isNetted) 0.2f else 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )

                ColorPill(
                    text = bankBadgeText,
                    color = bankColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 3.5.dp,
                    verticalPadding = 0.5.dp
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
                            fontSize = 11.5.sp,
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
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            val catLabel = tx.category.replace("INVESTMENT_", "").replace("SALARY_", "SALARY ").replace("_", " ")
                            ColorPill(
                                text = catLabel,
                                color = categoryColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                horizontalPadding = 4.dp,
                                verticalPadding = 0.5.dp
                            )
                            if (tx.message.isNotBlank() && tx.counterpartyName.isNotBlank()) {
                                Text(
                                    text = tx.message,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right block: Amount
            Text(
                text = (if (isCredit) "+" else "") + fmtCZK(tx.amount),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = amountColor,
                    textDecoration = if (tx.isNetted) TextDecoration.LineThrough else null
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
