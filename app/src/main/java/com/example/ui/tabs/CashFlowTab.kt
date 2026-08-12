package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCZK
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import com.example.ui.components.CashFlowProjectionChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Summary", "Income", "Spending", "Ledger")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("cashflow_tab")
    ) {
        SecondaryTabRow(
            selectedTabIndex = selectedSubTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("cashflow_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> SummarySubTab(state = state, ledgerEntries = ledgerEntries)
            1 -> IncomeSubTab(state = state)
            2 -> SpendingSubTab(state = state)
            3 -> LedgerSubTab(
                entries = ledgerEntries,
                onAddClick = { showAddDialog = true },
                onEditEntry = { entry -> editingEntry = entry },
                onDelete = onDeleteLedgerEntry,
                onImportCsv = onImportCsv
            )
        }
    }

    if (showAddDialog) {
        AddLedgerEntryDialog(
            state = state,
            onDismiss = { showAddDialog = false },
            onSave = { ym, incV, incE, incU, expR, expL, notes ->
                onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes)
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        AddLedgerEntryDialog(
            state = state,
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
                Text(
                    text = "Household Monthly Income Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                IncomeRow(label = "Vaclav Net Salary", value = fmtCZK(inc.vaclavNet))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Eleonora Salary / Allowance", value = fmtCZK(inc.eleonoraSalary.takeIf { it > 0 } ?: inc.benefit))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Lecturing & Meal Vouchers", value = fmtCZK(inc.lecturing + inc.vouchers))
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
    }
}

@Composable
private fun IncomeRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
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
                Text(
                    text = "Current Monthly Living Costs",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
    }
}

@Composable
private fun ExpenseItem(label: String, value: Double, isBold: Boolean = false, highlightColor: androidx.compose.ui.graphics.Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodySmall
        )
        Text(
            text = fmtCZK(value),
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun LedgerSubTab(
    entries: List<LedgerEntryEntity>,
    onAddClick: () -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImportCsv(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { launcher.launch("text/comma-separated-values") },
                    modifier = Modifier.testTag("import_csv_button")
                ) {
                    Text("Import CSV")
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
                LedgerChart(entries = entries, modifier = Modifier.padding(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LedgerCard(
                            entry = entry,
                            onClick = { onEditEntry(entry) },
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
    entry: LedgerEntryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val totalInc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    val totalExp = entry.expRent + entry.expGroceries + entry.expOther
    val netFlow = totalInc - totalExp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ledger_card_${entry.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.yearMonth,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Surplus: ${com.example.util.Formatters.fmtCZK(netFlow)}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (netFlow >= 0) com.example.ui.theme.BrandTeal else MaterialTheme.colorScheme.error
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Income: ${com.example.util.Formatters.fmtCZK(totalInc)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Living Expenses: ${com.example.util.Formatters.fmtCZK(totalExp)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (entry.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notes: ${entry.notes}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_ledger_entry_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Ledger Entry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddLedgerEntryDialog(
    state: FullCalculationState,
    initialEntry: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, String) -> Unit
) {
    val defaultRent = state.settings.rentMonthly.toInt().toString()
    val defaultLiving = (state.settings.groceriesMonthly + state.settings.otherDiscretionaryMonthly).toInt().toString()

    var ym by remember { mutableStateOf(initialEntry?.yearMonth ?: "2026-08") }
    var incV by remember { mutableStateOf(initialEntry?.incVaclav?.toInt()?.toString() ?: "35000") }
    var incE by remember { mutableStateOf(initialEntry?.incEleonora?.toInt()?.toString() ?: "0") }
    var incU by remember { mutableStateOf(initialEntry?.incUnforeseen?.toInt()?.toString() ?: "0") }
    var expR by remember { mutableStateOf(initialEntry?.expRent?.toInt()?.toString() ?: defaultRent) }
    var expL by remember { mutableStateOf(initialEntry?.let { (it.expGroceries + it.expOther).toInt().toString() } ?: defaultLiving) }
    var notes by remember { mutableStateOf(initialEntry?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEntry == null) "Add Monthly Ledger Record" else "Edit Monthly Ledger Record", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ym,
                    onValueChange = { ym = it },
                    label = { Text("Year-Month (YYYY-MM)") },
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_ym")
                )
                OutlinedTextField(
                    value = incV,
                    onValueChange = { incV = it },
                    label = { Text("Vaclav Net Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_v")
                )
                OutlinedTextField(
                    value = incE,
                    onValueChange = { incE = it },
                    label = { Text("Eleonora Net Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_e")
                )
                OutlinedTextField(
                    value = incU,
                    onValueChange = { incU = it },
                    label = { Text("Other Income") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expR,
                    onValueChange = { expR = it },
                    label = { Text("Rent Expense") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expL,
                    onValueChange = { expL = it },
                    label = { Text("Groceries & Daily Living Expenses") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        modifier = modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last 6 Months", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
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
    val familySavingsMonthly = state.settings.familySavingsMonthly
    val unallocatedSurplus = (netFlow - investmentsMonthly - familySavingsMonthly).coerceAtLeast(0.0)

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (selectedMonth == "Current Baseline") "Monthly Cash Flow Summary" else "Summary for $selectedMonth",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                    familySavings = familySavingsMonthly,
                    unallocated = unallocatedSurplus
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Income Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Income Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (entry != null) {
                    IncomeRow(label = "Vaclav Net Income", value = fmtCZK(entry.incVaclav))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora Net Income", value = fmtCZK(entry.incEleonora))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Other Income", value = fmtCZK(entry.incUnforeseen))
                } else {
                    val inc = state.currentIncome
                    IncomeRow(label = "Vaclav Net Salary", value = fmtCZK(inc.vaclavNet))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Eleonora Salary / Allowance", value = fmtCZK(inc.eleonoraSalary.takeIf { it > 0 } ?: inc.benefit))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    IncomeRow(label = "Lecturing & Vouchers", value = fmtCZK(inc.lecturing + inc.vouchers))
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Living Expenses Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (entry != null) {
                    ExpenseItem("Rent", entry.expRent)
                    ExpenseItem("Groceries & Daily Living", entry.expGroceries + entry.expOther)
                } else {
                    val s = state.settings
                    ExpenseItem("Rent", s.rentMonthly)
                    ExpenseItem("Groceries & Daily Living", s.groceriesMonthly + s.otherDiscretionaryMonthly)
                    ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                    ExpenseItem("Therapy / Physio", s.therapyMonthly)
                    ExpenseItem("Charity", s.charityMonthly)
                    ExpenseItem("Entertainment", s.entertainmentMonthly)
                    ExpenseItem("Transport", s.transportMonthly)
                    ExpenseItem("Subscriptions", s.subscriptionsMonthly)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Savings & Investment Allocations",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val s = state.settings
                IncomeRow(label = "Portu / Stock ETFs (Vaclav)", value = fmtCZK(s.portuDcaMonthly))
                if (s.ePortuDcaMonthly > 0) {
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                IncomeRow(label = "Family Cash Savings Account", value = fmtCZK(s.familySavingsMonthly))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "TOTAL MONTHLY ALLOCATIONS",
                    value = fmtCZK(investmentsMonthly + familySavingsMonthly),
                    isBold = true
                )
            }
        }
    }
}

@Composable
private fun MetricStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = valueColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CashAllocationBar(
    totalIncome: Double,
    expenses: Double,
    investments: Double,
    familySavings: Double,
    unallocated: Double
) {
    val expRatio = if (totalIncome > 0) (expenses / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f
    val invRatio = if (totalIncome > 0) (investments / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f
    val savRatio = if (totalIncome > 0) (familySavings / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f
    val unallocRatio = if (totalIncome > 0) (unallocated.coerceAtLeast(0.0) / totalIncome).coerceIn(0.0, 1.0).toFloat() else 0f

    val expColor = androidx.compose.ui.graphics.Color(0xFFE57373)
    val invColor = BrandTeal
    val savColor = androidx.compose.ui.graphics.Color(0xFF81C784)
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
            if (savRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(savRatio)
                        .fillMaxHeight()
                        .background(savColor)
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
            AllocationLegendItem("Family Cash Savings", familySavings, savRatio, savColor)
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
