package com.example.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CrossAccountNettingAuditDialog
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.StatementImportReviewDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandTeal
import com.example.util.BankTransactionType
import com.example.util.CrossStatementAuditReport
import com.example.util.StatementParseSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    onAddLedgerEntry: (String, Double, Double, Double, Double, Double, String, Double, Double, Double) -> Unit,
    onUpdateLedgerEntry: (LedgerEntryEntity) -> Unit = {},
    onDeleteLedgerEntry: (Long) -> Unit,
    onImportCsv: (Uri) -> Unit = {},
    pendingStatementImport: StatementParseSummary? = null,
    onConfirmStatementImport: (StatementParseSummary) -> Unit = {},
    onDismissStatementImport: () -> Unit = {},
    onUpdateTransactionCategory: ((Int, BankTransactionType, Boolean) -> Unit)? = null,
    activeAuditReport: CrossStatementAuditReport? = null,
    onShowAuditReport: ((String) -> Unit)? = null,
    onDismissAuditReport: () -> Unit = {},
    importedBankSourcesByMonth: Map<String, Set<String>> = emptyMap(),
    lastImportTimestamp: Long? = null,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onDeleteImportedStatement: ((String, String?) -> Unit)? = null,
    initialSubTab: Int = 0,
    onUpdateSettings: (SettingsEntity) -> Unit = {},
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
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportCsv(it) }
    }

    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    val subTabs = listOf("Budget & Incomes", "Monthly Ledger")
    var showAddDialog by remember { mutableStateOf(false) }
    var duplicateFromEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    val infoState = rememberMetricInfoState()

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
                                maxLines = 2,
                                softWrap = true
                            )
                        },
                        modifier = Modifier.testTag("cashflow_subtab_$index")
                    )
                }
            }

            when (selectedSubTab) {
                0 -> BudgetAndIncomesSubTab(
                    state = state,
                    ledgerEntries = ledgerEntries,
                    allImportedTransactions = allImportedTransactions,
                    dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
                    onDismissSubscription = onDismissSubscription,
                    onRestoreSubscription = onRestoreSubscription,
                    onClearAllDismissedSubscriptions = onClearAllDismissedSubscriptions,
                    onShowInfo = { infoState.show(it) },
                    onUpdateSettings = onUpdateSettings
                )
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
                    onTriggerImportCsv = { csvLauncher.launch(supportedMimeTypes) },
                    onShowInfo = { infoState.show(it) },
                    onShowAuditReport = onShowAuditReport,
                    importedBankSourcesByMonth = importedBankSourcesByMonth,
                    lastImportTimestamp = lastImportTimestamp,
                    onDeleteImportedStatement = onDeleteImportedStatement,
                    allImportedTransactions = allImportedTransactions
                )
            }
        }

        if (selectedSubTab == 1 && ledgerEntries.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    duplicateFromEntry = null
                    showAddDialog = true
                },
                containerColor = BrandTeal,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 22.dp)
                    .size(52.dp)
                    .testTag("fab_add_ledger_entry")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Entry",
                    modifier = Modifier.size(24.dp)
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
                duplicateFromEntry = null
            },
            onTriggerImportCsv = { csvLauncher.launch(supportedMimeTypes) },
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                onAddLedgerEntry(ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve)
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
            onTriggerImportCsv = { csvLauncher.launch(supportedMimeTypes) },
            onSave = { ym, incV, incE, incU, expR, expL, notes, balPortu, balPension, balReserve ->
                val prevTotalLiving = entry.expGroceries + entry.expOther
                val (newGroceries, newOther) = if (entry.expOther > 0.0 && prevTotalLiving > 0.0) {
                    val otherRatio = (entry.expOther / prevTotalLiving).coerceIn(0.0, 1.0)
                    val calculatedOther = kotlin.math.round(expL * otherRatio)
                    (expL - calculatedOther) to calculatedOther
                } else {
                    expL to 0.0
                }
                onUpdateLedgerEntry(
                    entry.copy(
                        yearMonth = ym,
                        incVaclav = incV,
                        incEleonora = incE,
                        incUnforeseen = incU,
                        expRent = expR,
                        expGroceries = newGroceries,
                        expOther = newOther,
                        notes = notes,
                        portfolioBalanceAtMonthEnd = balPortu,
                        pensionBalanceAtMonthEnd = balPension,
                        emergencyReserveAtMonthEnd = balReserve
                    )
                )
                editingEntry = null
            }
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )

    pendingStatementImport?.let { summary ->
        StatementImportReviewDialog(
            summary = summary,
            onConfirm = onConfirmStatementImport,
            onDismiss = onDismissStatementImport,
            onShowNettingAudit = {
                onShowAuditReport?.invoke(summary.yearMonth)
            },
            onUpdateTransactionCategory = onUpdateTransactionCategory
        )
    }

    activeAuditReport?.let { report ->
        CrossAccountNettingAuditDialog(
            auditReport = report,
            onDismiss = onDismissAuditReport
        )
    }
}
