package com.example.ui.tabs

import com.example.util.parseUserAmountOrNull
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtEditableAmount
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
    return java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).toString()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddLedgerEntryDialog(
    state: FullCalculationState,
    entries: List<LedgerEntryEntity> = emptyList(),
    initialEntry: LedgerEntryEntity? = null,
    duplicateFrom: LedgerEntryEntity? = null,
    onDismiss: () -> Unit,
    onTriggerImportCsv: () -> Unit = {},
    onSave: (String, Double, Double, Double, Double, Double, String, Double?, Double?, Double?) -> Unit
) {
    val defaultRent = fmtEditableAmount(state.settings.rentMonthly)
    val baselineLiving = fmtEditableAmount((state.totalLivingCostMonthly - state.settings.rentMonthly).coerceAtLeast(0.0))
    val isSingle = state.settings.isSingleHousehold
    val budgetedIncV = fmtEditableAmount(state.currentIncome.vaclavNet)
    val budgetedIncE = if (isSingle) "0" else {
        val base = state.currentIncome.eleonoraSalary.takeIf { it > 0 } ?: state.currentIncome.benefit
        fmtEditableAmount(base)
    }

    val sourceEntry = initialEntry ?: duplicateFrom
    val defaultYm = if (duplicateFrom != null) {
        nextYearMonth(duplicateFrom.yearMonth)
    } else if (initialEntry != null) {
        initialEntry.yearMonth
    } else {
        val latest = entries.maxByOrNull { it.yearMonth }
        if (latest != null) nextYearMonth(latest.yearMonth) else nextYearMonth("")
    }

    fun fmtVal(v: Double?): String {
        if (v == null || !v.isFinite() || v < 0.0) return "0"
        return fmtEditableAmount(v)
    }

    var ym by rememberSaveable(sourceEntry?.id, defaultYm) { mutableStateOf(defaultYm) }
    var ymErrorText by rememberSaveable(sourceEntry?.id) { mutableStateOf<String?>(null) }
    var amountErrorText by rememberSaveable(sourceEntry?.id) { mutableStateOf<String?>(null) }
    var incV by rememberSaveable(sourceEntry?.id, budgetedIncV) { mutableStateOf(sourceEntry?.incVaclav?.let { fmtVal(it) } ?: budgetedIncV) }
    var incE by rememberSaveable(sourceEntry?.id, isSingle, budgetedIncE) { mutableStateOf(if (isSingle) "0" else (sourceEntry?.incEleonora?.let { fmtVal(it) } ?: budgetedIncE)) }
    var incU by rememberSaveable(sourceEntry?.id) { mutableStateOf(sourceEntry?.incUnforeseen?.let { fmtVal(it) } ?: "0") }
    var expR by rememberSaveable(sourceEntry?.id, defaultRent) { mutableStateOf(sourceEntry?.expRent?.let { fmtVal(it) } ?: defaultRent) }
    var expL by rememberSaveable(sourceEntry?.id, baselineLiving) { mutableStateOf(sourceEntry?.let { fmtVal(it.expGroceries + it.expOther) } ?: baselineLiving) }
    var notes by rememberSaveable(sourceEntry?.id) { mutableStateOf(sourceEntry?.notes ?: "") }
    var balPortu by rememberSaveable(sourceEntry?.id) { mutableStateOf(sourceEntry?.portfolioBalanceAtMonthEnd?.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: "") }
    var balPension by rememberSaveable(sourceEntry?.id) { mutableStateOf(sourceEntry?.pensionBalanceAtMonthEnd?.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: "") }
    var balReserve by rememberSaveable(sourceEntry?.id) { mutableStateOf(sourceEntry?.emergencyReserveAtMonthEnd?.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: "") }

    val latestEntry = remember(entries) { entries.maxByOrNull { it.yearMonth } }
    val primaryName = state.settings.primaryName.ifBlank { "Primary Earner" }
    val spouseName = state.settings.spouseName.ifBlank { "Partner" }

    // Live calculations
    val valIncV = incV.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val valIncE = if (!isSingle) (incE.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0) else 0.0
    val valIncU = incU.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val totalInflows = valIncV + valIncE + valIncU

    val valExpR = expR.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val valExpL = expL.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val totalExpenses = valExpR + valExpL

    val netFlow = totalInflows - totalExpenses
    val baselineExp = state.snapshot.monthlyLivingCosts
    val baselineInc = state.snapshot.monthlyTakeHomeInflows
    val baselineSurplus = state.snapshot.monthlyLeftoverBeforeInvesting
    val surplusDiff = netFlow - baselineSurplus

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
                    fontFamily = BodyFontFamily,
                    horizontalPadding = 8.dp,
                    verticalPadding = 3.dp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .imePadding()
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
                                onClick = {
                                    ym = prevYearMonth(ym)
                                    ymErrorText = null
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedTextField(
                                value = ym,
                                onValueChange = {
                                    ym = it
                                    ymErrorText = null
                                },
                                singleLine = true,
                                isError = ymErrorText != null,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BodyFontFamily,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier
                                    .width(108.dp)
                                    .testTag("ledger_input_ym")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    ym = nextYearMonth(ym)
                                    ymErrorText = null
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (ymErrorText != null) {
                            Text(
                                text = ymErrorText ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        // Presets Chips Row (Wraps cleanly on narrow widths)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (latestEntry != null && initialEntry == null) {
                                AssistChip(
                                    onClick = {
                                        ym = nextYearMonth(latestEntry.yearMonth)
                                        ymErrorText = null
                                        amountErrorText = null
                                        incV = fmtVal(latestEntry.incVaclav)
                                        incE = if (isSingle) "0" else fmtVal(latestEntry.incEleonora)
                                        incU = fmtVal(latestEntry.incUnforeseen)
                                        expR = fmtVal(latestEntry.expRent)
                                        expL = fmtVal(latestEntry.expGroceries + latestEntry.expOther)
                                        notes = if (latestEntry.notes.startsWith("Imported from", ignoreCase = true)) "" else latestEntry.notes
                                        balPortu = latestEntry.portfolioBalanceAtMonthEnd.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: ""
                                        balPension = latestEntry.pensionBalanceAtMonthEnd.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: ""
                                        balReserve = latestEntry.emergencyReserveAtMonthEnd.takeIf { it.isFinite() && it > 0 }?.let { fmtVal(it) } ?: ""
                                    },
                                    label = { Text("Copy Previous", fontSize = 11.sp, maxLines = 2, softWrap = true) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = BrandTeal.copy(alpha = 0.12f),
                                        labelColor = BrandTeal
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            AssistChip(
                                onClick = {
                                    amountErrorText = null
                                    incV = budgetedIncV
                                    incE = budgetedIncE
                                    incU = "0"
                                    expR = defaultRent
                                    expL = baselineLiving
                                },
                                label = { Text("Fill Budget", fontSize = 11.sp, maxLines = 1) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            AssistChip(
                                onClick = {
                                    onDismiss()
                                    onTriggerImportCsv()
                                },
                                label = { Text("Import", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = BrandGold.copy(alpha = 0.15f),
                                    labelColor = BrandGold
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
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
                                fontFamily = BodyFontFamily,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        if (!isSingle) {
                            ResponsiveFieldPair(
                                first = { mod ->
                                    LedgerAmountField(
                                        value = incV,
                                        onValueChange = { incV = it; amountErrorText = null },
                                        label = primaryName,
                                        modifier = mod.testTag("ledger_input_inc_v")
                                    )
                                },
                                second = { mod ->
                                    LedgerAmountField(
                                        value = incE,
                                        onValueChange = { incE = it; amountErrorText = null },
                                        label = spouseName,
                                        modifier = mod.testTag("ledger_input_inc_e")
                                    )
                                }
                            )
                        } else {
                            LedgerAmountField(
                                value = incV,
                                onValueChange = { incV = it; amountErrorText = null },
                                label = "$primaryName Net",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_input_inc_v")
                            )
                        }

                        LedgerAmountField(
                            value = incU,
                            onValueChange = { incU = it; amountErrorText = null },
                            label = "Other Inflows / Bonuses (optional)",
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
                                fontFamily = BodyFontFamily,
                                horizontalPadding = 5.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        ResponsiveFieldPair(
                            first = { mod ->
                                LedgerAmountField(
                                    value = expR,
                                    onValueChange = { expR = it; amountErrorText = null },
                                    label = "Rent / Housing",
                                    modifier = mod.testTag("ledger_input_exp_rent")
                                )
                            },
                            second = { mod ->
                                LedgerAmountField(
                                    value = expL,
                                    onValueChange = { expL = it; amountErrorText = null },
                                    label = "Variable Living",
                                    modifier = mod.testTag("ledger_input_exp_living")
                                )
                            }
                        )
                    }
                }

                // 4. Live Net Cash Flow & Budget Variance Strip
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                val varianceColor = if (surplusDiff >= 0) GoodGreen else BadRed
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                                    fontFamily = BodyFontFamily,
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
                                fontFamily = BodyFontFamily,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                    }
                }

                // 5. Month-End Wealth Snapshot
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandGold.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val vPortu = balPortu.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val vPension = balPension.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val vReserve = balReserve.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
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
                                    fontFamily = BodyFontFamily,
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

                        ResponsiveFieldPair(
                            first = { mod ->
                                LedgerAmountField(
                                    value = balPortu,
                                    onValueChange = { balPortu = it; amountErrorText = null },
                                    label = "Portu",
                                    placeholder = "Broker",
                                    modifier = mod.testTag("ledger_input_bal_portu")
                                )
                            },
                            second = { mod ->
                                LedgerAmountField(
                                    value = balReserve,
                                    onValueChange = { balReserve = it; amountErrorText = null },
                                    label = "Reserve",
                                    placeholder = "Cash fund",
                                    modifier = mod.testTag("ledger_input_bal_reserve")
                                )
                            }
                        )

                        LedgerAmountField(
                            value = balPension,
                            onValueChange = { balPension = it; amountErrorText = null },
                            label = "Pension (DIP + DPS)",
                            placeholder = "Combined total",
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
                    placeholder = { Text("e.g. Travel, bonus, car service") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                }
                if (amountErrorText != null) {
                    Text(
                        text = amountErrorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("ledger_amount_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedYm = ym.trim()
                    if (!trimmedYm.matches(Regex("^\\d{4}-(0[1-9]|1[0-2])$"))) {
                        ymErrorText = "Use YYYY-MM format (e.g. 2026-04)."
                        return@Button
                    }
                    fun parseRequired(raw: String, fieldLabel: String): Double? {
                        if (raw.isBlank()) return 0.0
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < 0.0) {
                            amountErrorText = "$fieldLabel must be a valid non-negative number."
                            return null
                        }
                        return p
                    }
                    fun parseOptional(raw: String, fieldLabel: String): Pair<Boolean, Double?> {
                        if (raw.isBlank()) return true to null
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < 0.0) {
                            amountErrorText = "$fieldLabel must be a valid non-negative number."
                            return false to null
                        }
                        return true to p
                    }
                    val parsedIncV = parseRequired(incV, primaryName) ?: return@Button
                    val parsedIncE = if (isSingle) 0.0 else (parseRequired(incE, spouseName) ?: return@Button)
                    val parsedIncU = parseRequired(incU, "Other inflows") ?: return@Button
                    val parsedExpR = parseRequired(expR, "Rent / Housing") ?: return@Button
                    val parsedExpL = parseRequired(expL, "Variable living") ?: return@Button
                    val (okPortu, parsedBalPortu) = parseOptional(balPortu, "Portu balance")
                    if (!okPortu) return@Button
                    val (okPension, parsedBalPension) = parseOptional(balPension, "Pension balance")
                    if (!okPension) return@Button
                    val (okReserve, parsedBalReserve) = parseOptional(balReserve, "Reserve balance")
                    if (!okReserve) return@Button

                    amountErrorText = null
                    onSave(
                        trimmedYm,
                        parsedIncV,
                        parsedIncE,
                        parsedIncU,
                        parsedExpR,
                        parsedExpL,
                        notes,
                        parsedBalPortu,
                        parsedBalPension,
                        parsedBalReserve
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_ledger_entry_button")
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", fontSize = 13.sp)
            }
        }
    )
}
