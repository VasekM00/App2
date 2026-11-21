package com.example.ui.tabs

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionStateEntity
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.components.LearnedMerchantRulesCard
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.ui.theme.brandSolidContentColor
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import com.example.util.Formatters.fmtRelativeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

fun LazyListScope.settingsDataSubTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    expandTrigger: Int,
    expandTargetState: Boolean?,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0,
    liveRegulatoryData: CzechRegulatoryData?,
    onSyncLiveCzechData: () -> Unit,
    lastSyncTimestamp: Long?,
    themeMode: String,
    onChangeThemeMode: ((String) -> Unit)?,
    onShowSyncDialog: () -> Unit,
    onShowImportJsonDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
    onShowClearDataDialog: () -> Unit,
    actionStates: Map<String, Boolean>,
    ledgerEntries: List<LedgerEntryEntity>,
    importedTransactions: List<ImportedBankTransactionEntity>,
    exportScope: CoroutineScope? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val s = state.settings

    // 0. Appearance & Theme Settings
    item {
        val haptic = LocalHapticFeedback.current
        SettingsGroupCard(
            title = "Appearance & Theme",
            subtitle = when (themeMode) {
                "LIGHT" -> "Light Mode"
                "DARK" -> "Dark Mode"
                else -> "Follow System (Auto)"
            },
            initiallyExpanded = true,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "appearance",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(
                text = "Choose whether the app follows your device's system theme or locks to light or dark mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    Triple("SYSTEM", "System", Icons.Default.Refresh),
                    Triple("LIGHT", "Light", Icons.Default.LightMode),
                    Triple("DARK", "Dark", Icons.Default.DarkMode)
                )
                modes.forEach { (mode, label, icon) ->
                    val isSelected = themeMode == mode
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChangeThemeMode?.invoke(mode)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("theme_mode_$mode")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Live Czech Benchmarks Sync
    item {
        val haptic = LocalHapticFeedback.current
        SettingsGroupCard(
            title = "Live Czech Rates & Sync",
            subtitle = liveDataSubtitle(liveRegulatoryData),
            initiallyExpanded = false,
            badgeText = if (liveRegulatoryData != null) "SYNCED" else "OFFLINE",
            badgeColor = if (liveRegulatoryData != null) GoodGreen else BrandGold,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "sync",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(
                text = "Fetch live daily exchange rates directly from the official Czech National Bank (ČNB) API and benchmark settings against verified Czech statutory tax and pension regulations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = liveDataSubtitle(liveRegulatoryData),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ColorPill(
                    text = fmtRelativeTime(lastSyncTimestamp),
                    color = if (lastSyncTimestamp != null) GoodGreen else BrandGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSyncLiveCzechData()
                    onShowSyncDialog()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("sync_czech_benchmarks_button")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check & Sync Live Benchmarks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 2. Export & Import Settings
    item {
        val context = LocalContext.current
        val fallbackScope = rememberCoroutineScope()
        val scope = exportScope ?: fallbackScope
        var isExporting by remember { mutableStateOf(false) }
        SettingsGroupCard(
            title = "Backup & Share Settings",
            subtitle = "JSON export/import and clipboard snapshot",
            initiallyExpanded = false,
            badgeText = "JSON",
            badgeColor = BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "backup",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(
                text = "Safely export your parameters as a portable JSON file, restore previous backups, or copy a plain-text summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isExporting) return@Button
                        isExporting = true
                        scope.launch {
                            try {
                                val json = withContext(Dispatchers.IO) {
                                    val actionEntities = actionStates.map { (k, done) ->
                                        ActionStateEntity(
                                            actionKey = k,
                                            year = k.substringBefore('_').toIntOrNull() ?: s.baseYear,
                                            actionId = k.substringAfter('_', k),
                                            isDone = done
                                        )
                                    }
                                    val exportedPrefs = mutableMapOf<String, String>()
                                    val subPrefs = context.getSharedPreferences("subscription_auditor_prefs", Context.MODE_PRIVATE)
                                    val dismissedSet = subPrefs.getStringSet("dismissed_merchants", emptySet()) ?: emptySet()
                                    if (dismissedSet.isNotEmpty()) {
                                        exportedPrefs["dismissed_merchants_json"] = JSONArray(dismissedSet.toList()).toString()
                                        exportedPrefs["dismissed_merchants"] = dismissedSet.joinToString("|")
                                    }
                                    val impPrefs = context.getSharedPreferences("statement_import_prefs", Context.MODE_PRIVATE)
                                    impPrefs.all.forEach { (k, v) ->
                                        if (k.startsWith("bank_bal_") && v != null) {
                                            exportedPrefs[k] = v.toString()
                                        }
                                    }
                                    val lots = com.example.util.InvestmentLotManager.getLots(context)
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
                                }
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("FIRE Full Backup JSON", json)
                                    clip.description.extras = PersistableBundle().apply {
                                        putBoolean(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                ClipDescription.EXTRA_IS_SENSITIVE
                                            } else {
                                                "android.content.extra.IS_SENSITIVE"
                                            },
                                            true
                                        )
                                    }
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Full backup JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Backup too large for Android clipboard. Use Export Report to share.", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onShowImportJsonDialog,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val snap = state.snapshot
                    val sEff = state.effectiveSettings
                    val summary = buildString {
                        appendLine("Financial Summary (${sEff.baseYear})")
                        appendLine("- Net Worth: ${fmtCZK(snap.netWorthTotal)}")
                        appendLine("- Monthly Expenses: ${fmtCZK(snap.monthlyLivingCosts)}")
                        appendLine("- Monthly Investments (DCA): ${fmtCZK(snap.monthlyTotalInvesting)}")
                        appendLine("- FIRE Target: ${fmtCZK(snap.activeFireTargetToday)} (${fmtPct(sEff.safeWithdrawalRatePct)} SWR)")
                        val fireLabel = if (sEff.isSingleHousehold) "Projected FIRE ETA" else "Projected Dual FIRE ETA"
                        appendLine("- $fireLabel: ${snap.activeFireYear?.let { "$it" } ?: "Beyond 35 years"}")
                        appendLine("- Emergency Reserve: ${fmtCZK(snap.emergencyCashTotal)} (${String.format(java.util.Locale.ROOT, "%.1f", snap.emergencyRunwayMonths)} months)")
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Financial Summary", summary)
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ClipDescription.EXTRA_IS_SENSITIVE
                            } else {
                                "android.content.extra.IS_SENSITIVE"
                            },
                            true
                        )
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Financial Snapshot Summary", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    // 3. Learned Merchant Rules
    item {
        LearnedMerchantRulesCard(
            rulesJson = s.merchantRulesJson,
            onUpdateRulesJson = { newJson ->
                dispatchSettingsPatch(s, onUpdateSettings, onReduceSettings) {
                    it.copy(merchantRulesJson = newJson)
                }
            },
            sectionKey = "merchant_rules",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        )
    }

    // 4. Danger Zone / Reset Defaults
    item {
        SettingsGroupCard(
            title = "Reset & Danger Zone",
            subtitle = "Restore defaults or clear local database",
            initiallyExpanded = false,
            badgeText = "RESET",
            badgeColor = BadRed,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "danger",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Button(
                onClick = onShowResetDialog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGold,
                    contentColor = brandSolidContentColor(BrandGold)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reset_defaults_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Reset All Settings to Defaults")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onShowClearDataDialog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clear_all_data_button")
            ) {
                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Clear All Data & Cache")
            }
        }
    }
}

fun liveDataSubtitle(data: com.example.domain.CzechRegulatoryData?): String {
    return if (data != null) {
        "ČSÚ CPI: ${data.csuCpiInflationPct}% · EUR: ${String.format(java.util.Locale.ROOT, "%.2f", data.eurCzkRate)} · USD: ${String.format(java.util.Locale.ROOT, "%.2f", data.usdCzkRate)}"
    } else {
        "Check live ČSÚ inflation, ČNB rates & tax credits"
    }
}
