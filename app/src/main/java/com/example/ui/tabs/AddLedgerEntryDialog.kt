package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import java.util.Locale

fun nextYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size == 2) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (y != null && m != null && m in 1..12) {
            return if (m >= 12) {
                String.format(Locale.ROOT, "%04d-%02d", y + 1, 1)
            } else {
                String.format(Locale.ROOT, "%04d-%02d", y, m + 1)
            }
        }
    }
    val now = java.util.Calendar.getInstance()
    return String.format(
        Locale.ROOT,
        "%04d-%02d",
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH) + 1
    )
}

fun prevYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size == 2) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (y != null && m != null && m in 1..12) {
            return if (m <= 1) {
                String.format(Locale.ROOT, "%04d-%02d", y - 1, 12)
            } else {
                String.format(Locale.ROOT, "%04d-%02d", y, m - 1)
            }
        }
    }
    return ym
}

@Composable
internal fun AddLedgerEntryDialog(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity> = emptyList(),
    initialEntry: LedgerEntryEntity? = null,
    duplicateFrom: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onTriggerImportCsv: () -> Unit = {},
    onSave: (String, Double, Double, Double, Double, Double, String, Double, Double, Double) -> Unit
) {
    val defaultRent = state.settings.rentMonthly.toInt().toString()
    val baselineLiving = (state.settings.groceriesMonthly + state.settings.cafesMonthly + state.settings.entertainmentMonthly + state.settings.otherDiscretionaryMonthly).toInt().toString()
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
        if (latest != null) nextYearMonth(latest.yearMonth) else nextYearMonth("")
    }

    var ym by remember { mutableStateOf(defaultYm) }
    var incV by remember { mutableStateOf(sourceEntry?.incVaclav?.toInt()?.toString() ?: defaultVaclav) }
    var incE by remember { mutableStateOf(sourceEntry?.incEleonora?.toInt()?.toString() ?: defaultEleonora) }
    var incU by remember { mutableStateOf(sourceEntry?.incUnforeseen?.toInt()?.toString() ?: "0") }
    var expR by remember { mutableStateOf(sourceEntry?.expRent?.toInt()?.toString() ?: defaultRent) }
    var expL by remember { mutableStateOf(sourceEntry?.let { (it.expGroceries + it.expOther).toInt().toString() } ?: baselineLiving) }
    var notes by remember { mutableStateOf(sourceEntry?.notes ?: "") }
    var balPortu by remember { mutableStateOf(sourceEntry?.portfolioBalanceAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var balPension by remember { mutableStateOf(sourceEntry?.pensionBalanceAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var balReserve by remember { mutableStateOf(sourceEntry?.emergencyReserveAtMonthEnd?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }

    val latestEntry = remember(entries) { entries.maxByOrNull { it.yearMonth } }
    val primaryName = state.settings.primaryName.ifBlank { "Primary Earner" }
    val spouseName = state.settings.spouseName.ifBlank { "Spouse / Partner" }
    val isSingle = state.settings.isSingleHousehold

    // Live calculations
    val valIncV = incV.toDoubleOrNull() ?: 0.0
    val valIncE = if (!isSingle) (incE.toDoubleOrNull() ?: 0.0) else 0.0
    val valIncU = incU.toDoubleOrNull() ?: 0.0
    val totalInflows = valIncV + valIncE + valIncU

    val valExpR = expR.toDoubleOrNull() ?: 0.0
    val valExpL = expL.toDoubleOrNull() ?: 0.0
    val totalExpenses = valExpR + valExpL

    val netFlow = totalInflows - totalExpenses
    val baselineExp = state.totalLivingCostMonthly
    val baselineInc = remember(state.settings) {
        FinancialEngine.householdIncome(state.settings.baseYear, state.settings).totalMonthly
    }
    val baselineSurplus = baselineInc - baselineExp
    val surplusDiff = netFlow - baselineSurplus

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (initialEntry != null) "Edit Record"
                        else if (duplicateFrom != null) "Duplicate Record"
                        else "New Monthly Record",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Actual earnings & spending",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    )
                }
                ColorPill(
                    text = ym.ifBlank { "LEDGER" },
                    color = BrandTeal,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 8.dp,
                    verticalPadding = 3.dp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Period Selector & Quick Presets Strip (Spaced for clean mobile ergonomics)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Month Stepper Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { ym = prevYearMonth(ym) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedTextField(
                                value = ym,
                                onValueChange = { ym = it },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier
                                    .width(108.dp)
                                    .testTag("ledger_input_ym")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { ym = nextYearMonth(ym) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Presets Chips Row (Dedicated row so labels never crush or truncate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (latestEntry != null && initialEntry == null) {
                                AssistChip(
                                    onClick = {
                                        ym = nextYearMonth(latestEntry.yearMonth)
                                        incV = latestEntry.incVaclav.toInt().toString()
                                        incE = latestEntry.incEleonora.toInt().toString()
                                        incU = latestEntry.incUnforeseen.toInt().toString()
                                        expR = latestEntry.expRent.toInt().toString()
                                        expL = (latestEntry.expGroceries + latestEntry.expOther).toInt().toString()
                                        notes = latestEntry.notes
                                        balPortu = latestEntry.portfolioBalanceAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                        balPension = latestEntry.pensionBalanceAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                        balReserve = latestEntry.emergencyReserveAtMonthEnd.takeIf { it > 0 }?.toInt()?.toString() ?: ""
                                    },
                                    label = { Text("Copy Prev", fontSize = 10.5.sp, maxLines = 1) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = BrandTeal.copy(alpha = 0.12f),
                                        labelColor = BrandTeal
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            AssistChip(
                                onClick = {
                                    incV = defaultVaclav
                                    incE = defaultEleonora
                                    incU = "0"
                                    expR = defaultRent
                                    expL = baselineLiving
                                },
                                label = { Text("Fill Budget", fontSize = 10.5.sp, maxLines = 1) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AssistChip(
                                onClick = {
                                    onDismiss()
                                    onTriggerImportCsv()
                                },
                                label = { Text("Import", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = BrandGold.copy(alpha = 0.15f),
                                    labelColor = BrandGold
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }

                // 2. Incomes Group
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoodGreen.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Incomes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "+ " + fmtCZK(totalInflows),
                                color = GoodGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        if (!isSingle) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = incV,
                                    onValueChange = { incV = it },
                                    label = { Text(primaryName, fontSize = 11.sp) },
                                    suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ledger_input_inc_v")
                                )
                                OutlinedTextField(
                                    value = incE,
                                    onValueChange = { incE = it },
                                    label = { Text(spouseName, fontSize = 11.sp) },
                                    suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ledger_input_inc_e")
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = incV,
                                onValueChange = { incV = it },
                                label = { Text("$primaryName Net", fontSize = 11.sp) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_input_inc_v")
                            )
                        }

                        OutlinedTextField(
                            value = incU,
                            onValueChange = { incU = it },
                            label = { Text("Other Inflows / Bonuses (optional)", fontSize = 11.sp) },
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. Living Expenses Group
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BadRed.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Expenses",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "- " + fmtCZK(totalExpenses),
                                color = BadRed,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = expR,
                                onValueChange = { expR = it },
                                label = { Text("Rent / Housing", fontSize = 11.sp) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_exp_rent")
                            )
                            OutlinedTextField(
                                value = expL,
                                onValueChange = { expL = it },
                                label = { Text("Variable Living", fontSize = 11.sp) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_exp_living")
                            )
                        }
                    }
                }

                // 4. Live Net Cash Flow & Budget Variance Strip
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                val varianceColor = if (surplusDiff >= 0) GoodGreen else BadRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (netFlow >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = netColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Net Cash Flow",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Text(
                                text = (if (netFlow >= 0) "+ " else "") + fmtCZK(netFlow),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = netColor
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "vs Baseline Budget",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            ColorPill(
                                text = "${if (surplusDiff >= 0) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                                color = varianceColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                    }
                }

                // 5. Month-End Wealth Snapshot
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val vPortu = balPortu.toDoubleOrNull() ?: 0.0
                        val vPension = balPension.toDoubleOrNull() ?: 0.0
                        val vReserve = balReserve.toDoubleOrNull() ?: 0.0
                        val vTotalNetWorth = vPortu + vPension + vReserve

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Month-End Wealth Snapshot",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                            if (vTotalNetWorth > 0) {
                                ColorPill(
                                    text = fmtCZK(vTotalNetWorth),
                                    color = BrandGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            } else {
                                Text(
                                    text = "Optional",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = balPortu,
                                onValueChange = { balPortu = it },
                                label = { Text("Portu", fontSize = 11.sp) },
                                placeholder = { Text("Broker", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_bal_portu")
                            )
                            OutlinedTextField(
                                value = balReserve,
                                onValueChange = { balReserve = it },
                                label = { Text("Reserve", fontSize = 11.sp) },
                                placeholder = { Text("Cash fund", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ledger_input_bal_reserve")
                            )
                        }

                        OutlinedTextField(
                            value = balPension,
                            onValueChange = { balPension = it },
                            label = { Text("Pension (DIP + DPS)", fontSize = 11.sp) },
                            placeholder = { Text("Combined total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            suffix = { Text("Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ledger_input_bal_pension")
                        )
                    }
                }

                // 6. Notes / Comments Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Memos (optional)", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Travel, bonus, car service...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
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
                        notes,
                        balPortu.toDoubleOrNull() ?: 0.0,
                        balPension.toDoubleOrNull() ?: 0.0,
                        balReserve.toDoubleOrNull() ?: 0.0
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_ledger_entry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (initialEntry != null) "Update Record" else "Save Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 13.sp)
            }
        }
    )
}
