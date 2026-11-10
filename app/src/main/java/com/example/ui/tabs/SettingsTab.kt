@file:Suppress("InlinedApi")

package com.example.ui.tabs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomLumpSumItem
import com.example.domain.FullCalculationState
import com.example.ui.components.LiveSyncDialog
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandTeal
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

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
    ledgerEntries: List<com.example.data.LedgerEntryEntity> = emptyList(),
    actionStates: Map<String, Boolean> = emptyMap(),
    importedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
    onRestoreFullBackup: ((BackupManager.FullBackupPayload) -> Unit)? = null,
    themeMode: String = "SYSTEM",
    onChangeThemeMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val s = state.settings
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showSyncDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddLumpSumDialog by remember { mutableStateOf(false) }
    var editingLumpSumItem by remember { mutableStateOf<CustomLumpSumItem?>(null) }
    val infoState = rememberMetricInfoState()

    // Consolidated into 3 high-level cohesive hubs
    val clampedInitialTab = initialSubTab.coerceIn(0, 2)
    var selectedTab by rememberSaveable(initialSubTab) { mutableIntStateOf(clampedInitialTab) }
    var expandTrigger by rememberSaveable(selectedTab) { mutableIntStateOf(0) }
    var expandTargetState by rememberSaveable(selectedTab) { mutableStateOf<Boolean?>(null) }

    val hubDefinitions = listOf(
        Triple(
            "Cashflow & Family",
            "${fmtCompact(state.currentIncome.totalMonthly)}/mo net",
            Icons.Default.AccountBalanceWallet
        ),
        Triple(
            "FIRE & Assets",
            "${fmtCompact(state.netWorthTotal)} · ${fmtPct(s.safeWithdrawalRatePct)}",
            Icons.AutoMirrored.Filled.TrendingUp
        ),
        Triple(
            "Data & System",
            if (liveRegulatoryData != null) "ČNB Live Synced" else "Sync & Backup",
            Icons.Default.CloudDownload
        )
    )

    LaunchedEffect(selectedTab) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hubDefinitions.forEachIndexed { index, (title, subLabel, icon) ->
                val isSelected = selectedTab == index
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = index
                        onSubTabSelected?.invoke(index)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("settings_hub_$index")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) BrandTeal.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val (hubSummaryTitle, hubSummaryDetail) = when (selectedTab) {
                    0 -> "Household Budget & Demographics" to "Inflows ${fmtCZK(state.currentIncome.totalMonthly)} · Burn ${fmtCZK(state.totalLivingCostMonthly)} · Savings ${fmtPct(state.savingsRatePct, 1)}"
                    1 -> "Portfolio, DCA & Tax Parameters" to "Net Worth ${fmtCZK(state.netWorthTotal)} · DCA ${fmtCZK(state.investMonthlyTotal)} · Return ${fmtPct(s.portfolioNominalReturnPct)}"
                    else -> "Regulatory Data, Backups & Categorization" to "Base Year ${s.baseYear} · ${if (s.isSingleHousehold) "Single Earner" else "Dual Earner"} · Portable JSON & ČNB Sync"
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = hubSummaryTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hubSummaryDetail,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        val nextExpand = !(expandTargetState ?: false)
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandTargetState = nextExpand
                                expandTrigger += 1
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (nextExpand) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (nextExpand) "Expand All" else "Collapse",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
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
                    onShowAddCategory = { showAddCategoryDialog = true },
                    onShowAddLumpSum = { item ->
                        editingLumpSumItem = item
                        showAddLumpSumDialog = true
                    },
                    onShowInfo = { infoState.show(it) }
                )
                1 -> settingsFireSubTab(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    expandTrigger = expandTrigger,
                    expandTargetState = expandTargetState,
                    onShowInfo = { infoState.show(it) }
                )
                else -> settingsDataSubTab(
                    state = state,
                    onUpdateSettings = onUpdateSettings,
                    expandTrigger = expandTrigger,
                    expandTargetState = expandTargetState,
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
                    importedTransactions = importedTransactions
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
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
                Toast.makeText(context, "Official Czech benchmarks applied", Toast.LENGTH_SHORT).show()
            },
            lastSyncTimestamp = lastSyncTimestamp
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
            onUpdateSettings = onUpdateSettings
        )
    }

    if (showAddLumpSumDialog) {
        SettingsAddLumpSumDialog(
            settings = s,
            initialItem = editingLumpSumItem,
            onDismiss = {
                showAddLumpSumDialog = false
                editingLumpSumItem = null
            },
            onUpdateSettings = onUpdateSettings
        )
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}
