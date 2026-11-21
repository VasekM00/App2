package com.example.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.LocalExternalPickerGuard
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankTransactionType

@Composable
fun CashFlowTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String, Double, Double, Double) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    onImportStatements: (List<Uri>) -> Unit = { uris -> uris.firstOrNull()?.let(onImportCsv) },
    onShowAuditReport: ((String) -> Unit)? = null,
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    lastImportTimestamp: Long? = null,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onToggleImportedTransactionExcluded: ((Long) -> Unit)? = null,
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onDeleteImportedStatement: ((String, String?) -> Unit)? = null,
    initialSubTab: Int = 0,
    onSubTabSelected: ((Int) -> Unit)? = null,
    onUpdateSettings: (SettingsEntity) -> Unit = {},
    onAddLedgerEntryWithOptionalBalances: ((String, Double, Double, Double, Double, Double, String, Double?, Double?, Double?) -> Unit)? = null,
    onUpdateLedgerEntryWithSync: ((LedgerEntryEntity, Double?, Double?, Double?) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val supportedMimeTypes = arrayOf(
        "application/pdf",
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "application/octet-stream",
        "*/*"
    )
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportStatements(uris)
        }
    }

    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 3)) }
    val subTabs = listOf("Overview", "Income", "Expenses", "Ledger")
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var duplicateFromId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val duplicateFromEntry = duplicateFromId?.let { id -> ledgerEntries.firstOrNull { it.id == id } }
    val editingEntry = editingEntryId?.let { id -> ledgerEntries.firstOrNull { it.id == id } }
    val infoState = rememberMetricInfoState()

    val haptic = LocalHapticFeedback.current
    val onExternalPickerLaunching = LocalExternalPickerGuard.current
    val launchStatementPicker = {
        onExternalPickerLaunching()
        csvLauncher.launch(supportedMimeTypes)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("cashflow_tab")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Single clean Neobank segmented control (eliminates stacked 3-tier navigation)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subTabs.forEachIndexed { index, title ->
                        val isSelected = selectedSubTab == index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedSubTab = index
                                        onSubTabSelected?.invoke(index)
                                    }
                                )
                                .testTag("cashflow_subtab_$index")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            when (selectedSubTab) {
                0 -> BudgetOverviewCard(
                    state = state,
                    ledgerEntries = ledgerEntries,
                    allImportedTransactions = allImportedTransactions,
                    dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
                    onDismissSubscription = onDismissSubscription,
                    onRestoreSubscription = onRestoreSubscription,
                    onClearAllDismissedSubscriptions = onClearAllDismissedSubscriptions,
                    onUpdateImportedTransactionCategory = onUpdateImportedTransactionCategory,
                    onShowInfo = { infoState.show(it) },
                    onUpdateSettings = onUpdateSettings,
                    onReduceSettings = onReduceSettings
                )
                1 -> IncomeSourcesCard(
                    state = state,
                    ledgerEntries = ledgerEntries,
                    onShowInfo = { infoState.show(it) },
                    onUpdateSettings = onUpdateSettings,
                    onReduceSettings = onReduceSettings,
                    allImportedTransactions = allImportedTransactions
                )
                2 -> ExpenseCategoriesCard(
                    state = state,
                    ledgerEntries = ledgerEntries,
                    onShowInfo = { infoState.show(it) },
                    onUpdateSettings = onUpdateSettings,
                    onReduceSettings = onReduceSettings,
                    allImportedTransactions = allImportedTransactions
                )
                else -> LedgerSubTab(
                    state = state,
                    entries = ledgerEntries,
                    onAddClick = {
                        duplicateFromId = null
                        showAddDialog = true
                    },
                    onDuplicateEntry = { entry ->
                        duplicateFromId = entry.id
                        showAddDialog = true
                    },
                    onEditEntry = { entry -> editingEntryId = entry.id },
                    onDelete = onDeleteLedgerEntry,
                    onTriggerImportCsv = launchStatementPicker,
                    onShowInfo = { infoState.show(it) },
                    onShowAuditReport = onShowAuditReport,
                    importedBankSourcesByMonth = importedBankSourcesByMonth,
                    lastImportTimestamp = lastImportTimestamp,
                    onDeleteImportedStatement = onDeleteImportedStatement,
                    allImportedTransactions = allImportedTransactions,
                    onUpdateImportedTransactionCategory = onUpdateImportedTransactionCategory,
                    onToggleImportedTransactionExcluded = onToggleImportedTransactionExcluded
                )
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
                duplicateFromId = null
            },
            onTriggerImportCsv = launchStatementPicker,
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                if (onAddLedgerEntryWithOptionalBalances != null) {
                    onAddLedgerEntryWithOptionalBalances(ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve)
                } else {
                    onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes, balPortu ?: 0.0, balPension ?: 0.0, balReserve ?: 0.0)
                }
                showAddDialog = false
                duplicateFromId = null
            }
        )
    }

    editingEntry?.let { entry ->
        AddLedgerEntryDialog(
            state = state,
            entries = ledgerEntries,
            initialEntry = entry,
            onDismiss = { editingEntryId = null },
            onTriggerImportCsv = launchStatementPicker,
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                val prevTotalLiving = entry.expGroceries + entry.expOther
                val (newGroceries, newOther) = if (entry.expOther > 0.0 && prevTotalLiving > 0.0) {
                    val otherRatio = (entry.expOther / prevTotalLiving).coerceIn(0.0, 1.0)
                    val calculatedOther = kotlin.math.round(expL * otherRatio)
                    (expL - calculatedOther) to calculatedOther
                } else {
                    expL to 0.0
                }
                val updatedEntry = entry.copy(
                    yearMonth = ym,
                    incVaclav = incV,
                    incEleonora = incE,
                    incUnforeseen = incU,
                    expRent = expR,
                    expGroceries = newGroceries,
                    expOther = newOther,
                    notes = notes,
                    portfolioBalanceAtMonthEnd = balPortu ?: 0.0,
                    pensionBalanceAtMonthEnd = balPension ?: 0.0,
                    emergencyReserveAtMonthEnd = balReserve ?: 0.0
                )
                if (onUpdateLedgerEntryWithSync != null) {
                    onUpdateLedgerEntryWithSync(updatedEntry, balPortu, balPension, balReserve)
                } else {
                    onUpdateLedgerEntry(updatedEntry)
                }
                editingEntryId = null
            }
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}
