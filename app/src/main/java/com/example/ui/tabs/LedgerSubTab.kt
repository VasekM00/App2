package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCompact
import com.example.util.BankTransactionType

@OptIn(ExperimentalLayoutApi::class)
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
    var showImportGuidanceDialog by rememberSaveable { mutableStateOf(false) }

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
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterVertically)
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
                        text = "${sortedEntries.size} ${if (sortedEntries.size == 1) "MONTH" else "MONTHS"}",
                        color = BrandTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp
                    )
                }
            }

            if (sortedEntries.isNotEmpty()) {
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandTeal
                        ),
                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_import_statement")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Import",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    FilledTonalButton(
                        onClick = onAddClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandTeal.copy(alpha = 0.12f),
                            contentColor = BrandTeal
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toolbar_add_entry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "New",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
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
                    .heightIn(min = 48.dp)
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
                        text = "Track your actual monthly income and spending against your budget to see your real savings progress.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons (Wrap cleanly on narrow screens)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandTeal,
                                contentColor = OnBrandTeal
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("log_first_month_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, softWrap = true)
                        }

                        OutlinedButton(
                            onClick = { showImportGuidanceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, BrandTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTeal),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("empty_state_import_csv_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Statement", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, softWrap = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Supported Bank Statement Formats Guide Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandTeal.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PDF & CSV", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandTeal, maxLines = 1)
                                        Text("Official exports", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandGold.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Multi-Bank", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandGold, maxLines = 1)
                                        Text("Czech & Revolut", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Auto-Netted", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        Text("Own transfers", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("month_chip_${entry.yearMonth}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = entry.yearMonth,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = BodyFontFamily,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = (if (net >= 0) "+" else "") + fmtCompact(net),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = BodyFontFamily,
                                        color = if (isSelected) OnBrandTeal.copy(alpha = 0.92f) else netColor
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
                                                color = if (isSelected) OnBrandTeal else bankBadgeColor(bank),
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
