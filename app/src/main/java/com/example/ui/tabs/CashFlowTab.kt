package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    val subTabs = listOf("Income", "Spending", "Ledger")
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
            0 -> IncomeSubTab(state)
            1 -> SpendingSubTab(state)
            2 -> LedgerSubTab(
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
            onDismiss = { showAddDialog = false },
            onSave = { ym, incV, incE, expR, expG, expO, notes ->
                onAddLedgerEntry(ym, incV, incE, expR, expG, expO, notes)
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        AddLedgerEntryDialog(
            initialEntry = entry,
            onDismiss = { editingEntry = null },
            onSave = { ym, incV, incE, expR, expG, expO, notes ->
                onUpdateLedgerEntry(
                    entry.copy(
                        yearMonth = ym,
                        incVaclav = incV,
                        incEleonora = incE,
                        expRent = expR,
                        expGroceries = expG,
                        expOther = expO,
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
                ExpenseItem("Groceries", s.groceriesMonthly)
                ExpenseItem("Cafes & Restaurants", s.cafesMonthly)
                ExpenseItem("Therapy / Physio", s.therapyMonthly)
                ExpenseItem("Charity", s.charityMonthly)
                ExpenseItem("Entertainment", s.entertainmentMonthly)
                ExpenseItem("Transport", s.transportMonthly)
                ExpenseItem("Subscriptions", s.subscriptionsMonthly)
                ExpenseItem("Other Discretionary", s.otherDiscretionaryMonthly)

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
    val totalInc = entry.incVaclav + entry.incEleonora
    val totalExp = entry.expRent + entry.expGroceries + entry.expOther
    val netFlow = totalInc - totalExp
    val savingsRate = if (totalInc > 0) (netFlow / totalInc) * 100 else 0.0

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
                        text = "Net: ${com.example.util.Formatters.fmtCZK(netFlow)}",
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
                        text = "Expenses: ${com.example.util.Formatters.fmtCZK(totalExp)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "Savings Rate: ${com.example.util.Formatters.fmtPct(savingsRate)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
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
    initialEntry: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, String) -> Unit
) {
    var ym by remember { mutableStateOf(initialEntry?.yearMonth ?: "2026-04") }
    var incV by remember { mutableStateOf(initialEntry?.incVaclav?.toInt()?.toString() ?: "35000") }
    var incE by remember { mutableStateOf(initialEntry?.incEleonora?.toInt()?.toString() ?: "0") }
    var expR by remember { mutableStateOf(initialEntry?.expRent?.toInt()?.toString() ?: "21770") }
    var expG by remember { mutableStateOf(initialEntry?.expGroceries?.toInt()?.toString() ?: "4800") }
    var expO by remember { mutableStateOf(initialEntry?.expOther?.toInt()?.toString() ?: "5000") }
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
                    label = { Text("Vaclav Net Income (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_v")
                )
                OutlinedTextField(
                    value = incE,
                    onValueChange = { incE = it },
                    label = { Text("Eleonora Net Income (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("ledger_input_inc_e")
                )
                OutlinedTextField(
                    value = expR,
                    onValueChange = { expR = it },
                    label = { Text("Rent Expense (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expG,
                    onValueChange = { expG = it },
                    label = { Text("Groceries Expense (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expO,
                    onValueChange = { expO = it },
                    label = { Text("Other Expenses (CZK)") },
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
                        expR.toDoubleOrNull() ?: 0.0,
                        expG.toDoubleOrNull() ?: 0.0,
                        expO.toDoubleOrNull() ?: 0.0,
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
    val maxVal = sorted.maxOf { maxOf(it.incVaclav + it.incEleonora, it.expRent + it.expGroceries + it.expOther) }.coerceAtLeast(100.0)

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
                    val inc = entry.incVaclav + entry.incEleonora
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
