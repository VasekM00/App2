package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.CashFlowProjectionChart
import kotlin.math.abs

fun nextYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size == 2) {
        val y = parts[0].toIntOrNull() ?: 2026
        val m = parts[1].toIntOrNull() ?: 8
        return if (m >= 12) {
            "${y + 1}-01"
        } else {
            String.format("%04d-%02d", y, m + 1)
        }
    }
    return "2026-09"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    val subTabs = listOf("Budget & Incomes", "Monthly Ledger")
    var showAddDialog by remember { mutableStateOf(false) }
    var duplicateFromEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("cashflow_tab")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(
                selectedTabIndex = selectedSubTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSubTab == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedSubTab = index
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("cashflow_subtab_$index")
                    )
                }
            }

            when (selectedSubTab) {
                0 -> BudgetAndIncomesSubTab(state = state, ledgerEntries = ledgerEntries)
                1 -> LedgerSubTab(
                    state = state,
                    entries = ledgerEntries,
                    onAddClick = {
                        duplicateFromEntry = null
                        showAddDialog = true
                    },
                    onDuplicateEntry = { entry ->
                        duplicateFromEntry = entry
                        showAddDialog = true
                    },
                    onEditEntry = { entry -> editingEntry = entry },
                    onDelete = onDeleteLedgerEntry,
                    onImportCsv = onImportCsv
                )
            }
        }

        if (selectedSubTab == 1) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    duplicateFromEntry = null
                    showAddDialog = true
                },
                containerColor = BrandTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .testTag("fab_add_ledger_entry")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Entry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showAddDialog) {
        AddLedgerEntryDialog(
            state = state,
            entries = ledgerEntries,
            duplicateFrom = duplicateFromEntry,
            onDismiss = {
                showAddDialog = false
                duplicateFromEntry = null
            },
            onSave = { ym, incV, incE, incU, expR, expL, notes ->
                onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes)
                showAddDialog = false
                duplicateFromEntry = null
            }
        )
    }

    editingEntry?.let { entry ->
        AddLedgerEntryDialog(
            state = state,
            entries = ledgerEntries,
            initialEntry = entry,
            onDismiss = { editingEntry = null },
            onSave = { ym, incV, incE, incU, expR, expL, notes ->
                onUpdateLedgerEntry(
                    entry.copy(
                        yearMonth = ym,
                        incVaclav = incV,
                        incEleonora = incE,
                        incUnforeseen = incU,
                        expRent = expR,
                        expGroceries = expL,
                        expOther = 0.0,
                        notes = notes
                    )
                )
                editingEntry = null
            }
        )
    }
}

@Composable
private fun IncomeSubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Income Breakdown",
                    subtitle = "Combined household inflows",
                    badgeText = "INFLOWS",
                    accentColor = GoodGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                IncomeRow(label = "Václav's Net Salary", value = fmtCZK(inc.vaclavNet))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Eleonora's Allowance / Salary", value = fmtCZK(if (inc.eleonoraSalary > 0) inc.eleonoraSalary else inc.benefit))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Eleonora's Lecturing", value = fmtCZK(inc.lecturing))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Meal Vouchers (Václav)", value = fmtCZK(inc.vouchers))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Family Support Gift", value = fmtCZK(inc.gift))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY INCOME",
                    value = fmtCZK(inc.totalMonthly),
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Cash Flow Projection Chart
        CashFlowProjectionChart(data = state.dualTrajectory)

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun IncomeRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun SpendingSubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()
    val s = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Living Costs",
                    subtitle = "Baseline budget & essential expenses",
                    badgeText = "EXPENSES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExpenseItem("Rent", s.rentMonthly)
                ExpenseItem("Groceries & Daily Living", s.groceriesMonthly + s.otherDiscretionaryMonthly)
                ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                ExpenseItem("Therapy / Physio", s.therapyMonthly)
                ExpenseItem("Charity", s.charityMonthly)
                ExpenseItem("Entertainment", s.entertainmentMonthly)
                ExpenseItem("Transport", s.transportMonthly)
                ExpenseItem("Subscriptions", s.subscriptionsMonthly)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                ExpenseItem("TOTAL LIVING COSTS", state.totalLivingCostMonthly, isBold = true)
                Spacer(modifier = Modifier.height(8.dp))
                ExpenseItem(
                    "MONTHLY INVESTABLE SURPLUS",
                    state.currentIncome.totalMonthly - state.totalLivingCostMonthly,
                    isBold = true,
                    highlightColor = BrandTeal
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ExpenseItem(label: String, value: Double, isBold: Boolean = false, highlightColor: androidx.compose.ui.graphics.Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = fmtCZK(value),
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun LedgerSubTab(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity>,
    onAddClick: () -> Unit,
    onDuplicateEntry: (LedgerEntryEntity) -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportCsv(it) }
    }

    val latestEntry = remember(entries) {
        entries.maxByOrNull { it.yearMonth }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monthly Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (entries.isNotEmpty()) {
                        ColorPill(
                            text = "${entries.size} MONTHS",
                            color = BrandTeal,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (latestEntry != null) {
                        AssistChip(
                            onClick = { onDuplicateEntry(latestEntry) },
                            label = { Text("Duplicate Latest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = BrandTeal.copy(alpha = 0.12f),
                                labelColor = BrandTeal
                            ),
                            modifier = Modifier.testTag("duplicate_latest_button")
                        )
                    }
                    TextButton(
                        onClick = { launcher.launch("text/comma-separated-values") },
                        modifier = Modifier.testTag("import_csv_button")
                    ) {
                        Text("Import CSV")
                    }
                }
            }

            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No ledger entries recorded yet.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap '+' below to log monthly cash flow or tap 'Import CSV' above.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            } else {
                LedgerChart(entries = entries, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LedgerCard(
                            state = state,
                            entry = entry,
                            onClick = { onEditEntry(entry) },
                            onDuplicate = { onDuplicateEntry(entry) },
                            onDelete = { onDelete(entry.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = BrandTeal,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_ledger_fab")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Entry")
        }
    }
}

@Composable
private fun LedgerCard(
    state: FullCalculationState,
    entry: LedgerEntryEntity,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val totalInc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    val totalExp = entry.expRent + entry.expGroceries + entry.expOther
    val netFlow = totalInc - totalExp

    val baselineExp = state.totalLivingCostMonthly
    val baselineInc = state.currentIncome.totalMonthly
    val baselineSurplus = baselineInc - baselineExp

    val expDiff = totalExp - baselineExp
    val incDiff = totalInc - baselineInc
    val surplusDiff = netFlow - baselineSurplus

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete the ledger record for ${entry.yearMonth}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ledger_card_${entry.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorPill(
                        text = entry.yearMonth,
                        color = BrandTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        horizontalPadding = 7.dp,
                        verticalPadding = 3.dp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorPill(
                        text = (if (netFlow >= 0) "+ " else "") + fmtCZK(netFlow),
                        color = if (netFlow >= 0) GoodGreen else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        horizontalPadding = 7.dp,
                        verticalPadding = 3.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("duplicate_ledger_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate This Month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_ledger_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Ledger Entry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Income: ${fmtCZK(totalInc)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Living Expenses: ${fmtCZK(totalExp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Monthly Difference / Variance Indicators Row
            Spacer(modifier = Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Expense Variance Badge
                val expBadgeColor = if (expDiff > 50) MaterialTheme.colorScheme.error else if (expDiff < -50) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
                val expBgColor = if (expDiff > 50) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else if (expDiff < -50) GoodGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = expBgColor
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (expDiff > 50) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = expBadgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (expDiff > 50) "Exp: +${fmtCompact(expDiff)} (Over)" else if (expDiff < -50) "Exp: -${fmtCompact(abs(expDiff))} (Saved)" else "Exp: On budget",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = expBadgeColor,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                // Surplus Variance Badge
                val surplusBadgeColor = if (surplusDiff >= 0) BrandTeal else MaterialTheme.colorScheme.error
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = surplusBadgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Net: ${if (surplusDiff >= 0) "+" else ""}${fmtCompact(surplusDiff)} vs plan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = surplusBadgeColor,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (entry.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notes: ${entry.notes}",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

@Composable
private fun AddLedgerEntryDialog(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity> = emptyList(),
    initialEntry: LedgerEntryEntity? = null,
    duplicateFrom: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, String) -> Unit
) {
    val defaultRent = state.settings.rentMonthly.toInt().toString()
    val defaultLiving = (state.settings.groceriesMonthly + state.settings.otherDiscretionaryMonthly).toInt().toString()
    val defaultVaclav = state.currentIncome.vaclavNet.toInt().toString()
    val defaultEleonora = state.currentIncome.eleonoraSalary.takeIf { it > 0 }?.toInt()?.toString()
        ?: state.currentIncome.benefit.toInt().toString()

    val sourceEntry = initialEntry ?: duplicateFrom
    val defaultYm = if (duplicateFrom != null) {
        nextYearMonth(duplicateFrom.yearMonth)
    } else if (initialEntry != null) {
        initialEntry.yearMonth
    } else {
        val latest = entries.maxByOrNull { it.yearMonth }
        if (latest != null) nextYearMonth(latest.yearMonth) else "${state.settings.baseYear}-08"
    }

    var ym by remember { mutableStateOf(defaultYm) }
    var incV by remember { mutableStateOf(sourceEntry?.incVaclav?.toInt()?.toString() ?: defaultVaclav) }
    var incE by remember { mutableStateOf(sourceEntry?.incEleonora?.toInt()?.toString() ?: defaultEleonora) }
    var incU by remember { mutableStateOf(sourceEntry?.incUnforeseen?.toInt()?.toString() ?: "0") }
    var expR by remember { mutableStateOf(sourceEntry?.expRent?.toInt()?.toString() ?: defaultRent) }
    var expL by remember { mutableStateOf(sourceEntry?.let { (it.expGroceries + it.expOther).toInt().toString() } ?: defaultLiving) }
    var notes by remember { mutableStateOf(sourceEntry?.notes ?: "") }

    val latestEntry = remember(entries) { entries.maxByOrNull { it.yearMonth } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialEntry != null) "Edit Monthly Ledger Record"
                else if (duplicateFrom != null) "Duplicate Record (${duplicateFrom.yearMonth})"
                else "Add Monthly Ledger Record",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Quick Prefill helper buttons
                if (initialEntry == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (latestEntry != null) {
                            AssistChip(
                                onClick = {
                                    ym = nextYearMonth(latestEntry.yearMonth)
                                    incV = latestEntry.incVaclav.toInt().toString()
                                    incE = latestEntry.incEleonora.toInt().toString()
                                    incU = latestEntry.incUnforeseen.toInt().toString()
                                    expR = latestEntry.expRent.toInt().toString()
                                    expL = (latestEntry.expGroceries + latestEntry.expOther).toInt().toString()
                                    notes = latestEntry.notes
                                },
                                label = { Text("Copy Latest (${latestEntry.yearMonth})", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = BrandTeal.copy(alpha = 0.12f),
                                    labelColor = BrandTeal
                                )
                            )
                        }

                        AssistChip(
                            onClick = {
                                incV = defaultVaclav
                                incE = defaultEleonora
                                incU = "0"
                                expR = defaultRent
                                expL = defaultLiving
                            },
                            label = { Text("Baseline Budget", fontSize = 11.sp) }
                        )

                        AssistChip(
                            onClick = {
                                incV = "0"
                                incE = "0"
                                incU = "0"
                                expR = "0"
                                expL = "0"
                                notes = ""
                            },
                            label = { Text("Clear", fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ym,
                        onValueChange = { ym = it },
                        label = { Text("Year-Month (YYYY-MM)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ledger_input_ym")
                    )
                    OutlinedButton(
                        onClick = { ym = nextYearMonth(ym) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("+1 Mo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = incV,
                    onValueChange = { incV = it },
                    label = { Text("Václav Net Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_v")
                )
                OutlinedTextField(
                    value = incE,
                    onValueChange = { incE = it },
                    label = { Text("Eleonora Net Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_e")
                )
                OutlinedTextField(
                    value = incU,
                    onValueChange = { incU = it },
                    label = { Text("Other Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expR,
                    onValueChange = { expR = it },
                    label = { Text("Rent Expense") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expL,
                    onValueChange = { expL = it },
                    label = { Text("Groceries & Daily Living Expenses") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ym,
                        incV.toDoubleOrNull() ?: 0.0,
                        incE.toDoubleOrNull() ?: 0.0,
                        incU.toDoubleOrNull() ?: 0.0,
                        expR.toDoubleOrNull() ?: 0.0,
                        expL.toDoubleOrNull() ?: 0.0,
                        notes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                modifier = Modifier.testTag("save_ledger_entry_button")
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LedgerChart(entries: List<LedgerEntryEntity>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val sorted = entries.sortedBy { it.yearMonth }.takeLast(6)
    val maxVal = sorted.maxOf { maxOf(it.incVaclav + it.incEleonora + it.incUnforeseen, it.expRent + it.expGroceries + it.expOther) }.coerceAtLeast(100.0)

    val incColor = BrandTeal
    val expColor = androidx.compose.ui.graphics.Color(0xFFE57373)

    Card(
        modifier = modifier.fillMaxWidth().height(195.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "Last 6 Months Trend",
                subtitle = "Income vs expense historical flow",
                badgeText = "HISTORY",
                accentColor = BrandTeal
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                sorted.forEach { entry ->
                    val inc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
                    val exp = entry.expRent + entry.expGroceries + entry.expOther
                    val incRatio = (inc / maxVal).toFloat()
                    val expRatio = (exp / maxVal).toFloat()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                            Box(modifier = Modifier.width(12.dp).fillMaxHeight(incRatio).background(incColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(modifier = Modifier.width(12.dp).fillMaxHeight(expRatio).background(expColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.yearMonth.takeLast(2), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetAndIncomesSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Summary & Allocations, 1 = Income Details, 2 = Expense Details
    val sections = listOf("Overview", "Incomes", "Expenses")
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEachIndexed { index, name ->
                val isSelected = selectedSection == index
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedSection = index
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("budget_section_$index")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> SummarySubTab(state = state, ledgerEntries = ledgerEntries)
            1 -> IncomeSubTab(state = state)
            2 -> SpendingSubTab(state = state)
        }
    }
}

@Composable
private fun SummarySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>
) {
    val scrollState = rememberScrollState()
    val availableMonths = remember(ledgerEntries) {
        listOf("Current Baseline") + ledgerEntries.sortedByDescending { it.yearMonth }.map { it.yearMonth }
    }
    var selectedMonth by remember { mutableStateOf("Current Baseline") }

    val entry = remember(selectedMonth, ledgerEntries) {
        if (selectedMonth == "Current Baseline") null
        else ledgerEntries.find { it.yearMonth == selectedMonth }
    }

    val totalInc = if (entry != null) {
        entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    } else {
        state.currentIncome.totalMonthly
    }

    val totalExp = if (entry != null) {
        entry.expRent + entry.expGroceries + entry.expOther
    } else {
        state.totalLivingCostMonthly
    }

    val netFlow = totalInc - totalExp
    val savingsRatePct = if (totalInc > 0) (netFlow / totalInc) * 100.0 else 0.0
    val investmentsMonthly = state.investMonthlyTotal
    val unallocatedSurplus = (netFlow - investmentsMonthly).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Month Selector Chips
        if (availableMonths.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Period:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableMonths.forEach { month ->
                        val isSelected = month == selectedMonth
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMonth = month }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = month,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }

        // 1. Key Metrics Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = if (selectedMonth == "Current Baseline") "Monthly Cash Flow Summary" else "Summary for $selectedMonth",
                    subtitle = "Income, living expenses & net monthly surplus",
                    badgeText = "SUMMARY",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricStatBox(
                        label = "Total Income",
                        value = fmtCZK(totalInc),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Living Expenses",
                        value = fmtCZK(totalExp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Surplus",
                        value = fmtCZK(netFlow),
                        valueColor = if (netFlow >= 0) BrandTeal else MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Visual Cash Allocation Bar
                CashAllocationBar(
                    totalIncome = totalInc,
                    expenses = totalExp,
                    investments = investmentsMonthly,
                    unallocated = unallocatedSurplus
                )
            }
        }

        // Monthly Difference / Variance vs Baseline Budget Indicator Card (when viewing a specific recorded month)
        if (entry != null) {
            val baselineInc = state.currentIncome.totalMonthly
            val baselineExp = state.totalLivingCostMonthly
            val baselineSurplus = baselineInc - baselineExp
            val incDiff = totalInc - baselineInc
            val expDiff = totalExp - baselineExp
            val surplusDiff = netFlow - baselineSurplus

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CardHeaderPill(
                        title = "Variance vs Baseline Budget",
                        subtitle = "Performance deviation against baseline target",
                        badgeText = if (surplusDiff >= 0) "+${fmtCompact(surplusDiff)} Net" else "${fmtCompact(surplusDiff)} Net",
                        accentColor = if (surplusDiff >= 0) GoodGreen else MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStatBox(
                            label = "Income Variance",
                            value = "${if (incDiff >= 0) "+" else ""}${fmtCZK(incDiff)}",
                            valueColor = if (incDiff >= 0) GoodGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricStatBox(
                            label = "Expense Variance",
                            value = "${if (expDiff > 0) "+" else ""}${fmtCZK(expDiff)}",
                            valueColor = if (expDiff <= 0) GoodGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MetricStatBox(
                            label = "Surplus Variance",
                            value = "${if (surplusDiff >= 0) "+" else ""}${fmtCZK(surplusDiff)}",
                            valueColor = if (surplusDiff >= 0) BrandTeal else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (surplusDiff >= 0) {
                            "In $selectedMonth, you saved ${fmtCZK(abs(expDiff))} ${if (expDiff <= 0) "more" else "less"} on expenses and had a net surplus ${fmtCZK(surplusDiff)} above plan."
                        } else {
                            "In $selectedMonth, net cash surplus was ${fmtCZK(abs(surplusDiff))} below your baseline target."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Income Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Income Breakdown",
                    subtitle = "Monthly take-home earnings & cash inflows",
                    badgeText = "INCOME",
                    accentColor = GoodGreen
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (entry != null) {
                    IncomeRow(label = "Václav Net Income", value = fmtCZK(entry.incVaclav))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora Net Income", value = fmtCZK(entry.incEleonora))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Other / Unforeseen Income", value = fmtCZK(entry.incUnforeseen))
                } else {
                    val inc = state.currentIncome
                    IncomeRow(label = "Václav's Net Salary", value = fmtCZK(inc.vaclavNet))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora's Allowance / Salary", value = fmtCZK(if (inc.eleonoraSalary > 0) inc.eleonoraSalary else inc.benefit))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora's Lecturing", value = fmtCZK(inc.lecturing))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Meal Vouchers (Václav)", value = fmtCZK(inc.vouchers))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Family Support Gift", value = fmtCZK(inc.gift))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL INCOME",
                    value = fmtCZK(totalInc),
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Living Expenses Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Living Expenses Breakdown",
                    subtitle = "Monthly necessities, lifestyle & child allocations",
                    badgeText = "EXPENSES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (entry != null) {
                    ExpenseItem("Rent", entry.expRent)
                    ExpenseItem("Groceries & Daily Living", entry.expGroceries + entry.expOther)
                } else {
                    val s = state.settings
                    val deletedSet = com.example.domain.parseDeletedCategories(s.deletedCategoriesJson)
                    if (!deletedSet.contains("rent")) ExpenseItem("Rent", s.rentMonthly)
                    if (!deletedSet.contains("groceries")) ExpenseItem("Groceries & Daily Living", s.groceriesMonthly + s.otherDiscretionaryMonthly)
                    if (!deletedSet.contains("cafes")) ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                    if (!deletedSet.contains("therapy")) ExpenseItem("Therapy / Physio", s.therapyMonthly)
                    if (!deletedSet.contains("charity")) ExpenseItem("Charity", s.charityMonthly)
                    if (!deletedSet.contains("entertainment")) ExpenseItem("Entertainment", s.entertainmentMonthly)
                    if (!deletedSet.contains("transport")) ExpenseItem("Transport", s.transportMonthly)
                    if (!deletedSet.contains("subscriptions")) ExpenseItem("Subscriptions", s.subscriptionsMonthly)

                    if (s.childExpensesEnabled) {
                        if (s.child1Enabled) {
                            ExpenseItem("Child 1 Expenses", com.example.domain.FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s))
                        }
                        if (s.child2Enabled) {
                            ExpenseItem("Child 2 Expenses", com.example.domain.FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s))
                        }
                    }

                    val customCategories = com.example.domain.parseCustomExpenses(s.customExpensesJson)
                    customCategories.forEach { customItem ->
                        ExpenseItem(customItem.name, customItem.amount)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                ExpenseItem("TOTAL LIVING EXPENSES", totalExp, isBold = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Monthly Savings & Investments Allocation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Savings & Investment Allocations",
                    subtitle = "Automated wealth accumulation flows",
                    badgeText = "DCA FLOW",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                val s = state.settings
                IncomeRow(label = "Portu / Stock ETFs (${s.primaryName})", value = fmtCZK(s.portuDcaMonthly))
                if (!s.isSingleHousehold && s.ePortuDcaMonthly > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Portu / Stock ETFs (Eleonora)", value = fmtCZK(s.ePortuDcaMonthly))
                }
                if (s.dpsOwnContributionMonthly > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Pension Plan (DPS)", value = fmtCZK(s.dpsOwnContributionMonthly))
                }
                if (s.dipContributionMonthly > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Long-Term Investment Product (DIP)", value = fmtCZK(s.dipContributionMonthly))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY ALLOCATIONS",
                    value = fmtCZK(investmentsMonthly),
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun MetricStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.5.sp,
                    color = valueColor
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CashAllocationBar(
    totalIncome: Double,
    expenses: Double,
    investments: Double,
    unallocated: Double
) {
    val expRatio = if (totalIncome > 0) (expenses / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f
    val invRatio = if (totalIncome > 0) (investments / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f
    val unallocRatio = if (totalIncome > 0) (unallocated.coerceAtLeast(0.0) / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f

    val expColor = androidx.compose.ui.graphics.Color(0xFFE57373)
    val invColor = BrandTeal
    val unallocColor = androidx.compose.ui.graphics.Color(0xFFFFB74D)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Monthly Income Allocation",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (expRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(expRatio)
                        .fillMaxHeight()
                        .background(expColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                )
            }
            if (invRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(invRatio)
                        .fillMaxHeight()
                        .background(invColor)
                )
            }
            if (unallocRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(unallocRatio)
                        .fillMaxHeight()
                        .background(unallocColor, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AllocationLegendItem("Living Expenses", expenses, expRatio, expColor)
            AllocationLegendItem("Investments (ETFs/DPS/DIP)", investments, invRatio, invColor)
            if (unallocated > 0) {
                AllocationLegendItem("Surplus Buffer", unallocated, unallocRatio, unallocColor)
            }
        }
    }
}

@Composable
private fun AllocationLegendItem(
    label: String,
    amount: Double,
    ratio: Float,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "${fmtCZK(amount)} (${(ratio * 100).toInt()}%)",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
