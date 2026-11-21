package com.example.ui.tabs

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.FullCalculationState
import com.example.ui.components.BankAccountsReconciliationCard
import com.example.ui.components.ColorPill
import com.example.ui.components.LearnedMerchantRulesCard
import com.example.ui.components.canAuthenticateWithDevice
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.ui.theme.brandSolidContentColor
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import com.example.util.Formatters.fmtRelativeTime
import kotlinx.coroutines.CoroutineScope

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
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    biometricLockEnabled: Boolean = false,
    flagSecureEnabled: Boolean = false,
    onToggleBiometricLock: (Boolean) -> Unit = {},
    onToggleFlagSecure: (Boolean) -> Unit = {},
    onShowExportBackupDialog: () -> Unit = {},
    onLaunchImportBackupFile: () -> Unit = {}
) {
    val s = state.settings
    @Suppress("UNUSED_VARIABLE")
    val unusedRefs = Triple(actionStates.size, ledgerEntries.size, importedTransactions.size)
    @Suppress("UNUSED_VARIABLE")
    val unusedScope = exportScope

    // 0. Appearance & Theme Settings (item index 1)
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

            val stackThemeModeIcons = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 385 ||
                androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
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
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (stackThemeModeIcons) 56.dp else 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onChangeThemeMode?.invoke(mode)
                                }
                            )
                            .testTag("theme_mode_$mode")
                    ) {
                        if (stackThemeModeIcons) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
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
    }

    // 1. Privacy & Security (item index 2)
    item {
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        SettingsGroupCard(
            title = "Privacy & Security",
            subtitle = when {
                biometricLockEnabled && flagSecureEnabled -> "App lock & screenshot protection active"
                biometricLockEnabled -> "Biometric / PIN app lock enabled"
                flagSecureEnabled -> "Screen privacy (FLAG_SECURE) enabled"
                else -> "On-device lock & screen privacy controls"
            },
            initiallyExpanded = false,
            badgeText = if (biometricLockEnabled || flagSecureEnabled) "ACTIVE" else "ON-DEVICE",
            badgeColor = if (biometricLockEnabled || flagSecureEnabled) GoodGreen else BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "privacy",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            fun handleBiometricToggle(enable: Boolean) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (enable && !canAuthenticateWithDevice(context)) {
                    Toast.makeText(
                        context,
                        "Configure a device PIN, pattern, password, or biometric in Android Settings first.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                onToggleBiometricLock(enable)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { handleBiometricToggle(!biometricLockEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Biometric / Device PIN Lock",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Require unlock when opening the app or returning from background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricLockEnabled,
                    onCheckedChange = { handleBiometricToggle(it) },
                    modifier = Modifier.testTag("switch_biometric_lock")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFlagSecure(!flagSecureEnabled)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Hide Screen Content in Recents",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Blocks screenshots, screen recording, and task-switcher previews",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = flagSecureEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFlagSecure(it)
                    },
                    modifier = Modifier.testTag("switch_screen_privacy")
                )
            }
        }
    }

    // 2. ČNB FX Rates & Statutory Constants (item index 3)
    item {
        val haptic = LocalHapticFeedback.current
        val isFxLive = liveRegulatoryData?.isFxLive == true
        SettingsGroupCard(
            title = "ČNB FX Rates & Statutory Constants",
            subtitle = liveDataSubtitle(liveRegulatoryData),
            initiallyExpanded = false,
            badgeText = if (isFxLive) "FX LIVE" else "OFFLINE",
            badgeColor = if (isFxLive) GoodGreen else BrandGold,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "sync",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(
                text = "Fetches live daily FX rates (EUR, USD, GBP, PLN, CHF, HUF) from api.cnb.cz. Czech 2026 tax, DIP/DPS, and parental constants are verified bundled local defaults.",
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
                    color = if (isFxLive) GoodGreen else BrandGold,
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
                Text("Sync ČNB FX & Check 2026 Constants", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 3. Export & Import Backups via SAF (item index 4)
    item {
        val context = LocalContext.current
        SettingsGroupCard(
            title = "Backup & Portable Restore",
            subtitle = "AES-256-GCM encrypted files & summary snapshot",
            initiallyExpanded = false,
            badgeText = "SAF / GCM",
            badgeColor = BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "backup",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(
                text = "Save full or settings-only backups directly to a file with optional AES-256-GCM passphrase encryption, or restore from a saved backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShowExportBackupDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_backup_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Backup File", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onLaunchImportBackupFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("import_backup_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Backup File", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onShowImportJsonDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("paste_json_backup_button")
            ) {
                Text("Paste JSON Backup (Power Users)", fontSize = 12.sp)
            }
        }
    }

    // 4. Bank Accounts & Reconciliation (item index 5)
    item {
        BankAccountsReconciliationCard(
            knownFamilyAccountsJson = s.knownFamilyAccountsJson,
            knownParentalGiftAccountsJson = s.knownParentalGiftAccountsJson,
            onUpdateAccounts = { newFamilyJson, newGiftJson ->
                dispatchSettingsPatch(s, onUpdateSettings, onReduceSettings) {
                    it.copy(
                        knownFamilyAccountsJson = newFamilyJson,
                        knownParentalGiftAccountsJson = newGiftJson
                    )
                }
            },
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "bank_accounts",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        )
    }

    // 5. Learned Merchant Rules (item index 6)
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

    // 6. Danger Zone / Reset Defaults (item index 7)
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

fun liveDataSubtitle(data: CzechRegulatoryData?): String {
    if (data == null) {
        return "Offline — bundled 2026 statutory constants & fallback FX"
    }
    val prefix = when {
        data.isFxLive && data.rateDate.isNotBlank() -> "Live ČNB (${data.rateDate})"
        data.isFxLive -> "Live ČNB FX"
        data.rateDate.isNotBlank() -> "Cached (${data.rateDate})"
        else -> "Offline FX"
    }
    return "$prefix · EUR: ${String.format(java.util.Locale.ROOT, "%.2f", data.eurCzkRate)} · USD: ${String.format(java.util.Locale.ROOT, "%.2f", data.usdCzkRate)}"
}
