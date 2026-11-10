package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.YoYRetrospectiveCard
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtCZK
import com.example.util.BankTransactionType

@Composable
internal fun bankBadgeColor(bankName: String): Color = when {
    bankName.contains("MONETA", ignoreCase = true) -> BrandTeal
    bankName.contains("CSOB", ignoreCase = true) || bankName.contains("ČSOB", ignoreCase = true) -> BrandGold
    bankName.contains("MBANK", ignoreCase = true) -> Color(0xFFE11D48)
    bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> Color(0xFF2563EB)
    bankName.contains("KOMERCNI_BANKA", ignoreCase = true) -> Color(0xFFDC2626)
    bankName.contains("FIO", ignoreCase = true) -> Color(0xFF16A34A)
    bankName.contains("RAIFFEISENBANK", ignoreCase = true) -> Color(0xFFCA8A04)
    bankName.contains("AIR_BANK", ignoreCase = true) -> Color(0xFF65A30D)
    bankName.contains("UNICREDIT", ignoreCase = true) -> Color(0xFF9333EA)
    bankName.contains("CREDITAS", ignoreCase = true) -> Color(0xFF0D9488)
    bankName.contains("REVOLUT", ignoreCase = true) -> Color(0xFF0284C7)
    bankName.contains("WISE", ignoreCase = true) -> Color(0xFF15803D)
    else -> BrandTeal
}

internal fun friendlyBankName(bankName: String): String = when {
    bankName.contains("MONETA", ignoreCase = true) -> "Moneta"
    bankName.contains("CSOB", ignoreCase = true) || bankName.contains("ČSOB", ignoreCase = true) -> "ČSOB"
    bankName.contains("MBANK", ignoreCase = true) -> "mBank"
    bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> "Česká spořitelna"
    bankName.contains("KOMERCNI_BANKA", ignoreCase = true) -> "Komerční banka"
    bankName.contains("FIO", ignoreCase = true) -> "Fio banka"
    bankName.contains("RAIFFEISENBANK", ignoreCase = true) -> "Raiffeisenbank"
    bankName.contains("AIR_BANK", ignoreCase = true) -> "Air Bank"
    bankName.contains("UNICREDIT", ignoreCase = true) -> "UniCredit"
    bankName.contains("CREDITAS", ignoreCase = true) -> "Banka Creditas"
    bankName.contains("REVOLUT", ignoreCase = true) -> "Revolut"
    bankName.contains("WISE", ignoreCase = true) -> "Wise"
    else -> bankName
}

internal fun bankShortBadge(bankName: String): String = when {
    bankName.contains("MONETA", ignoreCase = true) -> "MON"
    bankName.contains("CSOB", ignoreCase = true) || bankName.contains("ČSOB", ignoreCase = true) -> "ČSOB"
    bankName.contains("MBANK", ignoreCase = true) -> "mB"
    bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> "ČS"
    bankName.contains("KOMERCNI_BANKA", ignoreCase = true) -> "KB"
    bankName.contains("FIO", ignoreCase = true) -> "Fio"
    bankName.contains("RAIFFEISENBANK", ignoreCase = true) -> "RB"
    bankName.contains("AIR_BANK", ignoreCase = true) -> "Air"
    bankName.contains("UNICREDIT", ignoreCase = true) -> "UC"
    bankName.contains("CREDITAS", ignoreCase = true) -> "CR"
    bankName.contains("REVOLUT", ignoreCase = true) -> "Rev"
    bankName.contains("WISE", ignoreCase = true) -> "Wise"
    else -> bankName.take(3).uppercase()
}

@Composable
internal fun LedgerSubTab(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity>,
    onAddClick: () -> Unit,
    onDuplicateEntry: (LedgerEntryEntity) -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onTriggerImportCsv: () -> Unit,
    onShowInfo: (MetricInfo) -> Unit = {},
    onShowAuditReport: ((String) -> Unit)? = null,
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    lastImportTimestamp: Long? = null,
    onDeleteImportedStatement: ((String, String?) -> Unit)? = null,
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onToggleImportedTransactionExcluded: ((Long) -> Unit)? = null
) {
    val sortedEntries = remember(entries) {
        entries.sortedByDescending { it.yearMonth }
    }

    val daysSinceLastImport = remember(lastImportTimestamp, entries) {
        val ts = lastImportTimestamp ?: run {
            val latest = entries.maxOfOrNull { it.yearMonth }
            if (latest != null) {
                try {
                    val ym = java.time.YearMonth.parse(latest)
                    val endOfMonth = ym.atEndOfMonth().atTime(23, 59)
                    val zone = java.time.ZoneId.systemDefault()
                    endOfMonth.atZone(zone).toInstant().toEpochMilli()
                } catch (e: Exception) { null }
            } else null
        }
        if (ts != null && ts > 0L) {
            val diffMs = System.currentTimeMillis() - ts
            (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else null
    }

    // Interactive Month Carousel Selection
    var selectedYm by rememberSaveable {
        mutableStateOf("")
    }
    var showImportGuidanceDialog by remember { mutableStateOf(false) }

    val effectiveSelectedYm = remember(selectedYm, sortedEntries) {
        if (selectedYm.isNotEmpty() && sortedEntries.any { it.yearMonth == selectedYm }) {
            selectedYm
        } else {
            sortedEntries.firstOrNull()?.yearMonth ?: ""
        }
    }

    val activeEntry = remember(effectiveSelectedYm, sortedEntries) {
        sortedEntries.find { it.yearMonth == effectiveSelectedYm } ?: sortedEntries.firstOrNull()
    }

    val baselineExp = state.totalLivingCostMonthly
    val baselineInc = remember(state.settings) {
        FinancialEngine.householdIncome(state.settings.baseYear, state.settings).totalMonthly
    }
    val baselineSurplus = baselineInc - baselineExp

    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar: Header + Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "Monthly Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sortedEntries.isNotEmpty()) {
                    ColorPill(
                        text = "${sortedEntries.size} MO",
                        color = BrandTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showImportGuidanceDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandTeal
                    ),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("btn_import_statement")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Import Statement",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Import",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onAddClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandTeal.copy(alpha = 0.12f),
                        contentColor = BrandTeal
                    ),
                    modifier = Modifier.testTag("toolbar_add_entry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Entry",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "New",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (daysSinceLastImport != null && daysSinceLastImport >= 25) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BrandGold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onTriggerImportCsv() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(BrandGold, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Last statement import: $daysSinceLastImport days ago",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Import fresh",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandGold
                        )
                    )
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            if (sortedEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandTeal.copy(alpha = 0.1f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No monthly records logged yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Track your actual salary and living spending vs budget month-by-month to observe real FIRE velocity.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons Side-by-Side
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onAddClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            modifier = Modifier.testTag("log_first_month_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = { showImportGuidanceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, BrandTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTeal),
                            modifier = Modifier.testTag("empty_state_import_csv_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Statement", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Supported Bank Statement Formats Guide Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Direct Bank Statement Import",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            Text(
                                text = "Import official monthly PDF or CSV statements from any Czech bank — Moneta, ČSOB, mBank, Česká spořitelna, Komerční banka, Fio, Air Bank, Raiffeisenbank, UniCredit, Creditas, Revolut and more:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandTeal.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Moneta", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandTeal)
                                        Text("Václav", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandGold.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ČSOB", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandGold)
                                        Text("Eleonora", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("mBank", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Shared", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Text(
                                text = "Internal transfers between your own accounts (any bank) are automatically matched and netted out to prevent double counting.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                // Horizontal Month Pill Selector Strip
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sortedEntries, key = { it.id }) { entry ->
                        val isSelected = entry.yearMonth == (activeEntry?.yearMonth ?: "")
                        val net = (entry.incVaclav + entry.incEleonora + entry.incUnforeseen) - (entry.expRent + entry.expGroceries + entry.expOther)
                        val netColor = if (net >= 0) GoodGreen else BadRed

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedYm = entry.yearMonth
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("month_chip_${entry.yearMonth}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = entry.yearMonth,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = (if (net >= 0) "+" else "") + fmtCompact(net),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else netColor
                                    )
                                )
                                val chipBanks = importedBankSourcesByMonth[entry.yearMonth] ?: emptySet()
                                if (chipBanks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        chipBanks.sorted().forEach { bank ->
                                            ColorPill(
                                                text = bankShortBadge(bank),
                                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else bankBadgeColor(bank),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                horizontalPadding = 3.dp,
                                                verticalPadding = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Month Featured Showcase Card
                if (activeEntry != null) {
                    val monthTransactions = remember(allImportedTransactions, activeEntry.yearMonth) {
                        allImportedTransactions.filter { it.yearMonth == activeEntry.yearMonth }
                    }
                    ActiveMonthOverviewCard(
                        state = state,
                        entry = activeEntry,
                        baselineInc = baselineInc,
                        baselineExp = baselineExp,
                        baselineSurplus = baselineSurplus,
                        onEdit = { onEditEntry(activeEntry) },
                        onDuplicate = { onDuplicateEntry(activeEntry) },
                        onDelete = { onDelete(activeEntry.id) },
                        onShowInfo = onShowInfo,
                        onShowAudit = { onShowAuditReport?.invoke(activeEntry.yearMonth) },
                        importedBankSourcesByMonth = importedBankSourcesByMonth,
                        onDeleteImportedStatement = onDeleteImportedStatement,
                        monthTransactions = monthTransactions,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    if (monthTransactions.isNotEmpty()) {
                        com.example.ui.components.MonthlyTransactionsCard(
                            yearMonth = activeEntry.yearMonth,
                            transactions = monthTransactions,
                            onUpdateCategory = onUpdateImportedTransactionCategory,
                            onToggleExcluded = onToggleImportedTransactionExcluded,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // 6 Months Inflows vs Outflows Visualizer
                LedgerChart(
                    entries = sortedEntries,
                    selectedYm = activeEntry?.yearMonth ?: "",
                    onSelectMonth = { ym -> selectedYm = ym },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Year-Over-Year (YoY) Retrospective Card
                YoYRetrospectiveCard(
                    ledgerEntries = sortedEntries,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(110.dp)) // padding for FAB
            }
        }

        if (showImportGuidanceDialog) {
            BankStatementImportGuidanceDialog(
                onDismiss = { showImportGuidanceDialog = false },
                onSelectFile = {
                    showImportGuidanceDialog = false
                    onTriggerImportCsv()
                }
            )
        }
    }
}

@Composable
private fun BankStatementImportGuidanceDialog(
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandTeal.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Import Statement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CSV / TSV Tabular (Recommended)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoodGreen
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Direct export from online banking (Moneta, ČSOB, mBank, Fio, Air Bank, Raiffeisen, etc.). Guarantees 100% deterministic precision without layout wrapping errors.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PDF E-Statement (Alternative)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Parses PDF statements via text extraction heuristics. Recommended when digital CSV exports are unavailable.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSelectFile,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_guidance_select_file")
            ) {
                Text("Select Statement File", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
