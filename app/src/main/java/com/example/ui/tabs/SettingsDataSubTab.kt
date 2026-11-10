package com.example.ui.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BackupManager
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import com.example.util.Formatters.fmtRelativeTime

fun LazyListScope.settingsDataSubTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    expandTrigger: Int,
    expandTargetState: Boolean?,
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
    importedTransactions: List<ImportedBankTransactionEntity>
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
            initiallyExpanded = true
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
                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("theme_mode_$mode")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
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
                                )
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
            title = "Live Czech Economic & Regulatory Sync",
            subtitle = liveDataSubtitle(liveRegulatoryData),
            initiallyExpanded = false,
            badgeText = if (liveRegulatoryData != null) "SYNCED" else "OFFLINE",
            badgeColor = if (liveRegulatoryData != null) GoodGreen else BrandGold,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            Text(
                text = "Fetch live daily exchange rates directly from the official Česká národní banka (ČNB) API and benchmark settings against verified Czech statutory tax and pension regulations.",
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
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
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
        SettingsGroupCard(
            title = "Backup & Share Settings",
            subtitle = "Portable JSON export/import and clipboard snapshot",
            initiallyExpanded = false,
            badgeText = "JSON",
            badgeColor = BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
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
                            exportedPrefs["dismissed_merchants"] = dismissedSet.joinToString("|")
                        }
                        val impPrefs = context.getSharedPreferences("statement_import_prefs", Context.MODE_PRIVATE)
                        impPrefs.all.forEach { (k, v) ->
                            if (k.startsWith("bank_bal_") && v != null) {
                                exportedPrefs[k] = v.toString()
                            }
                        }
                        val json = BackupManager.serializeFullBackupToJson(
                            BackupManager.FullBackupPayload(
                                settings = s,
                                ledgerEntries = ledgerEntries,
                                actionStates = actionEntities,
                                importedTransactions = importedTransactions,
                                preferences = exportedPrefs
                            )
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("FIRE Full Backup JSON", json)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Full backup JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShowImportJsonDialog,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val summary = buildString {
                        appendLine("Financial Summary (${s.baseYear})")
                        appendLine("• Net Worth: ${fmtCZK(state.netWorthTotal)}")
                        appendLine("• Monthly Expenses: ${fmtCZK(state.totalLivingCostMonthly)}")
                        appendLine("• Monthly Investments (DCA): ${fmtCZK(state.investMonthlyTotal)}")
                        appendLine("• FIRE Target: ${fmtCZK(state.fireBaseTargetToday)} (${fmtPct(s.safeWithdrawalRatePct)} SWR)")
                        appendLine("• Dual FIRE ETA: ${state.fireDualPoint?.let { "${it.year}" } ?: "Beyond 35y"}")
                        appendLine("• Emergency Reserve: ${fmtCZK(s.emergencyReserveCurrent)} (${String.format(java.util.Locale.ROOT, "%.1f", state.emergencyCoverageMonths)} months)")
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Financial Summary", summary)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Financial Snapshot Summary", fontSize = 12.sp)
            }
        }
    }

    // 3. Learned Merchant Rules
    item {
        LearnedMerchantRulesCard(
            rulesJson = s.merchantRulesJson,
            onUpdateRulesJson = { newJson ->
                onUpdateSettings(s.copy(merchantRulesJson = newJson))
            }
        )
    }

    // 4. Danger Zone / Reset Defaults
    item {
        SettingsGroupCard(
            title = "Reset & Danger Zone",
            subtitle = "Restore default parameters or wipe local database",
            initiallyExpanded = false,
            badgeText = "RESET",
            badgeColor = BadRed,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            Button(
                onClick = onShowResetDialog,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_defaults_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Reset All Settings to Defaults")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onShowClearDataDialog,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_all_data_button")
            ) {
                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Clear All Data & Cache")
            }
        }
    }
}
