package com.example.ui.tabs

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionStateEntity
import com.example.data.AppPreferencesRepository
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomLumpSums
import com.example.ui.MainViewModel
import com.example.ui.components.LiveSyncDialog
import com.example.ui.components.LocalExternalPickerGuard
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandTeal
import com.example.util.BackupCrypto
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import com.example.util.InvestmentLotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAllData: () -> Unit,
    liveRegulatoryData: com.example.domain.CzechRegulatoryData? = null,
    isSyncing: Boolean = false,
    onSyncLiveCzechData: () -> Unit = {},
    lastSyncTimestamp: Long? = null,
    initialSubTab: Int = 0,
    onSubTabSelected: ((Int) -> Unit)? = null,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0,
    onConsumeFocusedSection: (() -> Unit)? = null,
    ledgerEntries: List<com.example.data.LedgerEntryEntity> = emptyList(),
    actionStates: Map<String, Boolean> = emptyMap(),
    importedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
    onRestoreFullBackup: ((BackupManager.FullBackupPayload) -> Unit)? = null,
    themeMode: String = "SYSTEM",
    onChangeThemeMode: ((String) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    biometricLockEnabled: Boolean = false,
    flagSecureEnabled: Boolean = false,
    onToggleBiometricLock: (Boolean) -> Unit = {},
    onToggleFlagSecure: (Boolean) -> Unit = {},
    onPrepareExportRequest: ((Boolean, Boolean, String) -> Unit)? = null,
    onConsumeExportRequest: (() -> MainViewModel.PendingExportState?)? = null,
    partnerBirthYear: Int? = null,
    onUpdatePartnerBirthYear: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val exportScope = rememberCoroutineScope()
    val s = state.settings
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val onExternalPickerLaunching = LocalExternalPickerGuard.current

    var showSyncDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDataDialog by rememberSaveable { mutableStateOf(false) }
    var showImportJsonDialog by rememberSaveable { mutableStateOf(false) }
    var showExportBackupDialog by rememberSaveable { mutableStateOf(false) }
    var fallbackExportPending by rememberSaveable { mutableStateOf(false) }
    var fallbackExportFull by rememberSaveable { mutableStateOf(false) }
    var fallbackExportEncrypt by rememberSaveable { mutableStateOf(false) }
    var fallbackExportPhrase by rememberSaveable { mutableStateOf("") }
    var pendingEncryptedImportEnvelope by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportRawJson by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingImportPreview = androidx.compose.runtime.remember(pendingImportRawJson, s) {
        pendingImportRawJson?.let { parseBackupJsonForPreview(it, s) }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val req = onConsumeExportRequest?.invoke() ?: if (fallbackExportPending) {
            MainViewModel.PendingExportState(fallbackExportFull, fallbackExportEncrypt, fallbackExportPhrase)
        } else {
            null
        }
        fallbackExportPending = false
        fallbackExportPhrase = ""
        if (uri == null) return@rememberLauncherForActivityResult
        if (req == null) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            Toast.makeText(context, "Export state was interrupted. Please try exporting again.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val passChars = req.passphrase.toCharArray()
        exportScope.launch {
            try {
                val outputJson = withContext(Dispatchers.Default) {
                    val plainJson = if (req.fullBackupScope) {
                        val actionEntities = actionStates.map { (k, done) ->
                            ActionStateEntity(
                                actionKey = k,
                                year = k.substringBefore('_').toIntOrNull() ?: s.baseYear,
                                actionId = k.substringAfter('_', k),
                                isDone = done
                            )
                        }
                        val prefsRepo = AppPreferencesRepository(context)
                        prefsRepo.hydrate()
                        val exportedPrefs = prefsRepo.exportPreferencesForBackup()
                        val lots = InvestmentLotManager.getLots(context, prefsRepo.storeName)
                        BackupManager.serializeFullBackupToJson(
                            BackupManager.FullBackupPayload(
                                settings = s,
                                ledgerEntries = ledgerEntries,
                                actionStates = actionEntities,
                                importedTransactions = importedTransactions,
                                preferences = exportedPrefs,
                                investmentLots = lots
                            )
                        )
                    } else {
                        BackupManager.serializeSettingsToJson(s)
                    }
                    if (req.encryptEnabled) {
                        BackupCrypto.encryptJson(plainJson, passChars)
                    } else {
                        plainJson
                    }
                }
                val ok = writeUriTextUtf8(context, uri, outputJson)
                Toast.makeText(
                    context,
                    if (ok) "Backup file saved" else "Failed to write backup file",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to export backup file", Toast.LENGTH_SHORT).show()
            } finally {
                passChars.fill('\u0000')
            }
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportScope.launch {
            when (val readResult = readUriTextBounded(context, uri)) {
                is ReadBackupFileResult.Success -> {
                    val raw = readResult.text
                    if (withContext(Dispatchers.Default) { BackupCrypto.isEncryptedBackup(raw) }) {
                        pendingEncryptedImportEnvelope = raw
                    } else {
                        val preview = withContext(Dispatchers.Default) {
                            parseBackupJsonForPreview(raw, s)
                        }
                        if (preview != null) {
                            pendingImportRawJson = raw
                        } else {
                            Toast.makeText(
                                context,
                                "Selected file is not a valid Martinu Financials backup.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                ReadBackupFileResult.TooLarge -> {
                    Toast.makeText(context, "Backup file exceeds 10 MB limit.", Toast.LENGTH_LONG).show()
                }
                ReadBackupFileResult.ReadFailed -> {
                    Toast.makeText(context, "Unable to read selected backup file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showAddLumpSumDialog by rememberSaveable { mutableStateOf(false) }
    var editingLumpSumId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingLumpSumItem = if (editingLumpSumId != null) {
        parseCustomLumpSums(s.customLumpSumsJson).find { it.id == editingLumpSumId }
    } else null
    val infoState = rememberMetricInfoState()

    // Consolidated into 3 high-level cohesive hubs
    val clampedInitialTab = initialSubTab.coerceIn(0, 2)
    var selectedTab by rememberSaveable(initialSubTab) { mutableIntStateOf(clampedInitialTab) }
    var expandTrigger by rememberSaveable(selectedTab) { mutableIntStateOf(0) }
    var expandTargetState by rememberSaveable(selectedTab) { mutableStateOf<Boolean?>(null) }

    val hubTitles = listOf("Household", "Portfolio & Tax", "App & Data")
    @Suppress("UNUSED_VARIABLE")
    val unusedIconCheck = liveRegulatoryData?.isFxLive

    var lastHandledFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab, focusedSectionTrigger, focusedSectionKey) {
        val targetIndex = when (selectedTab) {
            0 -> when (focusedSectionKey) {
                "income" -> 1
                "expenses" -> 2
                "family" -> 3
                else -> null
            }
            1 -> when (focusedSectionKey) {
                "balances" -> 1
                "dca" -> 2
                "assumptions" -> 3
                "tax_shield" -> 4
                else -> null
            }
            else -> when (focusedSectionKey) {
                "appearance" -> 1
                "privacy" -> 2
                "sync" -> 3
                "backup" -> 4
                "bank_accounts" -> 5
                "merchant_rules" -> 6
                "danger" -> 7
                else -> null
            }
        }
        if (focusedSectionTrigger > lastHandledFocusTrigger && targetIndex != null) {
            lastHandledFocusTrigger = focusedSectionTrigger
            listState.animateScrollToItem(targetIndex)
            kotlinx.coroutines.delay(50)
            onConsumeFocusedSection?.invoke()
        } else {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isCompactHub = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380 ||
                    androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
                hubTitles.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTab = index
                                    onSubTabSelected?.invoke(index)
                                }
                            )
                            .padding(horizontal = if (isCompactHub) 4.dp else 6.dp, vertical = 8.dp)
                            .testTag("settings_hub_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = if (isCompactHub) 10.5.sp else 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2,
                            softWrap = true,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val snap = state.snapshot
                val sEff = state.effectiveSettings
                val (hubSummaryTitle, hubSummaryDetail) = when (selectedTab) {
                    0 -> "Personalize your household setup" to "Income ${fmtCompact(snap.monthlyTakeHomeInflows)} · Living ${fmtCompact(snap.monthlyLivingCosts)} · Save ${fmtPct(snap.savingsRatePct, 1)}"
                    1 -> "Portfolio & Freedom parameters" to "Net ${fmtCompact(snap.netWorthTotal)} · Investing ${fmtCompact(snap.monthlyTotalInvesting)} · ${fmtPct(sEff.portfolioNominalReturnPct)} return"
                    else -> "Preferences, Sync & Backups" to "Base ${sEff.baseYear} · ${if (sEff.isSingleHousehold) "Single" else "Dual"} household · JSON & ČNB Sync"
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = hubSummaryTitle,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = hubSummaryDetail,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        val nextExpand = !(expandTargetState ?: false)
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandTargetState = nextExpand
                                expandTrigger += 1
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (nextExpand) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (nextExpand) "Expand All" else "Collapse",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandTeal
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> settingsHouseholdSubTab(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    expandTrigger = expandTrigger,
                    expandTargetState = expandTargetState,
                    focusedSectionKey = focusedSectionKey,
                    focusedSectionTrigger = focusedSectionTrigger,
                    onShowAddCategory = { showAddCategoryDialog = true },
                    onShowAddLumpSum = { item ->
                        editingLumpSumId = item?.id
                        showAddLumpSumDialog = true
                    },
                    onShowInfo = { infoState.show(it) },
                    onReduceSettings = onReduceSettings,
                    partnerBirthYear = partnerBirthYear,
                    onUpdatePartnerBirthYear = onUpdatePartnerBirthYear
                )
                1 -> settingsFireSubTab(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    expandTrigger = expandTrigger,
                    expandTargetState = expandTargetState,
                    focusedSectionKey = focusedSectionKey,
                    focusedSectionTrigger = focusedSectionTrigger,
                    onShowInfo = { infoState.show(it) },
                    onReduceSettings = onReduceSettings
                )
                else -> settingsDataSubTab(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    expandTrigger = expandTrigger,
                    expandTargetState = expandTargetState,
                    focusedSectionKey = focusedSectionKey,
                    focusedSectionTrigger = focusedSectionTrigger,
                    liveRegulatoryData = liveRegulatoryData,
                    onSyncLiveCzechData = onSyncLiveCzechData,
                    lastSyncTimestamp = lastSyncTimestamp,
                    themeMode = themeMode,
                    onChangeThemeMode = onChangeThemeMode,
                    onShowSyncDialog = { showSyncDialog = true },
                    onShowImportJsonDialog = { showImportJsonDialog = true },
                    onShowResetDialog = { showResetDialog = true },
                    onShowClearDataDialog = { showClearDataDialog = true },
                    actionStates = actionStates,
                    ledgerEntries = ledgerEntries,
                    importedTransactions = importedTransactions,
                    exportScope = exportScope,
                    onReduceSettings = onReduceSettings,
                    biometricLockEnabled = biometricLockEnabled,
                    flagSecureEnabled = flagSecureEnabled,
                    onToggleBiometricLock = onToggleBiometricLock,
                    onToggleFlagSecure = onToggleFlagSecure,
                    onShowExportBackupDialog = { showExportBackupDialog = true },
                    onLaunchImportBackupFile = {
                        onExternalPickerLaunching()
                        importDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSyncDialog) {
        LiveSyncDialog(
            currentSettings = s,
            liveData = liveRegulatoryData,
            isLoading = isSyncing,
            onDismiss = { showSyncDialog = false },
            onApplySettings = { updated ->
                onUpdateSettings(updated)
                showSyncDialog = false
                Toast.makeText(context, "2026 Czech statutory defaults applied", Toast.LENGTH_SHORT).show()
            },
            onRetry = onSyncLiveCzechData,
            lastSyncTimestamp = lastSyncTimestamp
        )
    }

    if (showExportBackupDialog) {
        SettingsExportBackupDialog(
            onDismiss = { showExportBackupDialog = false },
            onConfirmExport = { fullScope, encryptEnabled, passphrase ->
                showExportBackupDialog = false
                if (onPrepareExportRequest != null) {
                    onPrepareExportRequest(fullScope, encryptEnabled, passphrase)
                } else {
                    fallbackExportFull = fullScope
                    fallbackExportEncrypt = encryptEnabled
                    fallbackExportPhrase = passphrase
                    fallbackExportPending = true
                }
                val dateSuffix = LocalDate.now().toString()
                val fileName = when {
                    fullScope && encryptEnabled -> "martinu_financials_full_encrypted_$dateSuffix.json"
                    fullScope -> "martinu_financials_full_backup_$dateSuffix.json"
                    encryptEnabled -> "martinu_financials_settings_encrypted_$dateSuffix.json"
                    else -> "martinu_financials_settings_backup_$dateSuffix.json"
                }
                onExternalPickerLaunching()
                exportDocumentLauncher.launch(fileName)
            }
        )
    }

    val encryptedEnvelope = pendingEncryptedImportEnvelope
    if (encryptedEnvelope != null) {
        SettingsPassphrasePromptDialog(
            envelopeJson = encryptedEnvelope,
            fallbackSettings = s,
            onDismiss = { pendingEncryptedImportEnvelope = null },
            onDecryptedPreview = { preview ->
                pendingEncryptedImportEnvelope = null
                pendingImportRawJson = when (preview) {
                    is ParsedBackupPreview.Full -> BackupManager.serializeFullBackupToJson(preview.payload)
                    is ParsedBackupPreview.SettingsOnly -> BackupManager.serializeSettingsToJson(preview.restoredSettings)
                }
            }
        )
    }

    val importPreview = pendingImportPreview
    if (importPreview != null) {
        SettingsBackupPreviewDialog(
            preview = importPreview,
            onDismiss = { pendingImportRawJson = null },
            onApplyPreview = { preview ->
                pendingImportRawJson = null
                when (preview) {
                    is ParsedBackupPreview.Full -> {
                        if (onRestoreFullBackup != null) {
                            onRestoreFullBackup(preview.payload)
                        } else {
                            onUpdateSettings(preview.payload.settings)
                        }
                    }
                    is ParsedBackupPreview.SettingsOnly -> {
                        onUpdateSettings(preview.restoredSettings)
                        Toast.makeText(context, "Settings successfully restored", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showImportJsonDialog) {
        SettingsImportJsonDialog(
            settings = s,
            onDismiss = { showImportJsonDialog = false },
            onUpdateSettings = onUpdateSettings,
            onRestoreFullBackup = onRestoreFullBackup
        )
    }

    if (showResetDialog) {
        SettingsResetDialog(
            onDismiss = { showResetDialog = false },
            onConfirmReset = onResetDefaults
        )
    }

    if (showClearDataDialog) {
        SettingsClearDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirmClear = onClearAllData
        )
    }

    if (showAddCategoryDialog) {
        SettingsAddCategoryDialog(
            settings = s,
            onDismiss = { showAddCategoryDialog = false },
            onUpdateSettings = onUpdateSettings,
            onReduceSettings = onReduceSettings
        )
    }

    if (showAddLumpSumDialog) {
        SettingsAddLumpSumDialog(
            settings = s,
            initialItem = editingLumpSumItem,
            onDismiss = {
                showAddLumpSumDialog = false
                editingLumpSumId = null
            },
            onUpdateSettings = onUpdateSettings,
            onReduceSettings = onReduceSettings
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}
