package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveMonthOverviewCard(
    state: FullCalculationState,
    entry: LedgerEntryEntity,
    baselineInc: Double,
    baselineExp: Double,
    baselineSurplus: Double,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShowInfo: (MetricInfo) -> Unit = {},
    onShowAudit: (() -> Unit)? = null,
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    onDeleteImportedStatement: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val totalInc = entry.incVaclav + entry.incEleonora + entry.incUnforeseen
    val totalExp = entry.expRent + entry.expGroceries + entry.expOther
    val netFlow = totalInc - totalExp
    val savingsRate = if (totalInc > 0) (netFlow.coerceAtLeast(0.0) / totalInc) * 100.0 else 0.0

    val expDiff = totalExp - baselineExp
    val incDiff = totalInc - baselineInc
    val surplusDiff = netFlow - baselineSurplus

    var showDeleteDialog by remember { mutableStateOf(false) }
    var bankToDelete by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record") },
            text = { Text("Are you sure you want to delete the record and all associated bank transactions for ${entry.yearMonth}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (bankToDelete != null) {
        val targetBank = bankToDelete!!
        val friendlyName = when {
            targetBank.contains("MONETA", ignoreCase = true) -> "Moneta"
            targetBank.contains("CSOB", ignoreCase = true) || targetBank.contains("ČSOB", ignoreCase = true) -> "ČSOB"
            targetBank.contains("MBANK", ignoreCase = true) -> "mBank"
            targetBank.contains("CESKA_SPORITELNA", ignoreCase = true) -> "Česká spořitelna"
            targetBank.contains("KOMERCNI_BANKA", ignoreCase = true) -> "Komerční banka"
            targetBank.contains("FIO", ignoreCase = true) -> "Fio banka"
            targetBank.contains("RAIFFEISENBANK", ignoreCase = true) -> "Raiffeisenbank"
            targetBank.contains("AIR_BANK", ignoreCase = true) -> "Air Bank"
            else -> targetBank
        }
        AlertDialog(
            onDismissRequest = { bankToDelete = null },
            title = { Text("Delete Imported Statement") },
            text = { Text("Delete imported transactions from $friendlyName for ${entry.yearMonth}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteImportedStatement?.invoke(entry.yearMonth, targetBank)
                        bankToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bankToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_ledger_card_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row with YearMonth, Savings Pill & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    ColorPill(
                        text = entry.yearMonth,
                        color = BrandTeal,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp
                    )
                    ColorPill(
                        text = "${savingsRate.toInt()}% SAVED",
                        color = if (savingsRate >= 40) GoodGreen else BrandGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("edit_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Month",
                            tint = BrandTeal,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(32.dp).testTag("duplicate_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp).testTag("delete_active_ledger_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bank Badges FlowRow (Safely wraps on narrow screens without squishing headers)
            val activeBanks = importedBankSourcesByMonth[entry.yearMonth] ?: emptySet()
            if (activeBanks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeBanks.sorted().forEach { bank ->
                        val displayName = when {
                            bank.contains("MONETA", ignoreCase = true) -> "Moneta"
                            bank.contains("CSOB", ignoreCase = true) || bank.contains("ČSOB", ignoreCase = true) -> "ČSOB"
                            bank.contains("MBANK", ignoreCase = true) -> "mBank"
                            bank.contains("CESKA_SPORITELNA", ignoreCase = true) -> "Česká spořitelna"
                            bank.contains("KOMERCNI_BANKA", ignoreCase = true) -> "Komerční banka"
                            bank.contains("FIO", ignoreCase = true) -> "Fio banka"
                            bank.contains("RAIFFEISENBANK", ignoreCase = true) -> "Raiffeisenbank"
                            bank.contains("AIR_BANK", ignoreCase = true) -> "Air Bank"
                            bank.contains("UNICREDIT", ignoreCase = true) -> "UniCredit"
                            bank.contains("CREDITAS", ignoreCase = true) -> "Banka Creditas"
                            bank.contains("REVOLUT", ignoreCase = true) -> "Revolut"
                            bank.contains("WISE", ignoreCase = true) -> "Wise"
                            else -> bank
                        }
                        ColorPill(
                            text = "$displayName ×",
                            color = bankBadgeColor(bank),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 5.dp,
                            verticalPadding = 1.5.dp,
                            modifier = Modifier.clickable { bankToDelete = bank }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Metric Inflow / Outflow / Net Grid (Tap-Enabled with Info Modals)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incomes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerIncomes(entry, baselineInc), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inflows",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(totalInc),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GoodGreen,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Expenses
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerExpenses(entry, baselineExp), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expenses",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(totalExp),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BadRed,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Net Cash Flow
                val netColor = if (netFlow >= 0) BrandTeal else BadRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = netColor.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, netColor.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerNetFlow(entry, totalInc, totalExp, netFlow, savingsRate), onShowInfo)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Flow",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = (if (netFlow >= 0) "+ " else "") + fmtCompact(netFlow),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = netColor,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Net Cash Flow vs Budget Performance Strip
            val isSurplusPositive = surplusDiff >= 0
            val varianceColor = if (isSurplusPositive) GoodGreen else BadRed
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = varianceColor.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, varianceColor.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .infoTapHold(CashFlowMetricInfos.ledgerBudgetVariance(surplusDiff, baselineSurplus, netFlow), onShowInfo)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                imageVector = if (isSurplusPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = varianceColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "vs Baseline Budget",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Budget Info",
                                tint = varianceColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        ColorPill(
                            text = "${if (isSurplusPositive) "+" else ""}${fmtCompact(surplusDiff)} vs budget",
                            color = varianceColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }

                    Text(
                        text = "Rent ${fmtCompact(entry.expRent)} · Living ${fmtCompact(entry.expGroceries + entry.expOther)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // DCA Investment Flow tracking row
            if (entry.notes.contains("Invested:", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                val invFull = entry.notes.substringAfter("Invested: ").substringBefore(" |")
                val invTotalStr = invFull.substringBefore(" (").trim()
                val invBreakdown = invFull.substringAfter("(", "").substringBefore(")")

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
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
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Tracked Investments (DCA)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Text(
                                text = invTotalStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandBlue,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        if (invBreakdown.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                invBreakdown.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { part ->
                                    ColorPill(
                                        text = part,
                                        color = BrandBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        horizontalPadding = 5.dp,
                                        verticalPadding = 1.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (entry.totalNetWorthAtMonthEnd > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .infoTapHold(CashFlowMetricInfos.ledgerMonthEndWealth(entry), onShowInfo)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = BrandGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Month-End Wealth Snapshot",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Wealth Info",
                                    tint = BrandGold.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = fmtCZK(entry.totalNetWorthAtMonthEnd),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandGold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (entry.portfolioBalanceAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Portu: " + fmtCompact(entry.portfolioBalanceAtMonthEnd),
                                    color = BrandGold,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                            if (entry.pensionBalanceAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Pension: " + fmtCompact(entry.pensionBalanceAtMonthEnd),
                                    color = BrandGold,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                            if (entry.emergencyReserveAtMonthEnd > 0) {
                                ColorPill(
                                    text = "Reserve: " + fmtCompact(entry.emergencyReserveAtMonthEnd),
                                    color = BrandTeal,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp
                                )
                            }
                        }
                    }
                }
            }

            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val isBankImport = entry.notes.contains("Imported from", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isBankImport) BrandTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isBankImport) BrandTeal.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isBankImport && onShowAudit != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onShowAudit() }
                            } else if (isBankImport) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .infoTapHold(CashFlowMetricInfos.ledgerBankImport(entry), onShowInfo)
                            } else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isBankImport) Icons.Default.AccountBalance else Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                                tint = if (isBankImport) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isBankImport) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBankImport) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isBankImport) {
                            if (onShowAudit != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandTeal.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = "Audit Proof",
                                            tint = BrandTeal,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Audit Proof",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.5.sp,
                                                color = BrandTeal,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Info",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        color = BrandTeal.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
