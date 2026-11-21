package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.SyncDifferenceItem
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.CzechEconomicSyncService
import com.example.util.Formatters.fmtRelativeTime

@Composable
fun LiveSyncDialog(
    currentSettings: SettingsEntity,
    liveData: CzechRegulatoryData?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onApplySettings: (SettingsEntity) -> Unit,
    onRetry: (() -> Unit)? = null,
    lastSyncTimestamp: Long? = null
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .testTag("live_sync_dialog"),
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ČNB FX & 2026 Statutory Constants",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    val subtitleText = when {
                        liveData?.isFxLive == true && lastSyncTimestamp != null ->
                            "Live ČNB FX + bundled 2026 constants · Synced ${fmtRelativeTime(lastSyncTimestamp)}"
                        liveData?.isFxLive == true ->
                            "Live ČNB FX + bundled 2026 constants"
                        else ->
                            "Offline · Bundled 2026 ZDP/DPS statutory defaults"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Fetching live daily FX rates from ČNB (api.cnb.cz)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (liveData != null) {
                val differences = remember(currentSettings, liveData) {
                    CzechEconomicSyncService.computeDifferences(currentSettings, liveData)
                }
                val diffCount = remember(differences) { differences.count { it.isDifferent } }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Section 1: Live ČNB Daily FX Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val datePrefix = if (liveData.rateDate.isNotBlank()) " (${liveData.rateDate})" else ""
                                Text(
                                    text = "Live ČNB Daily FX$datePrefix",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                ColorPill(
                                    text = if (liveData.isFxLive) "FX LIVE" else "OFFLINE",
                                    color = if (liveData.isFxLive) GoodGreen else BrandGold,
                                    fontSize = 9.sp,
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.dp
                                )
                            }

                            val locale = java.util.Locale.getDefault()
                            val eurRate = liveData.eurCzkRate.finiteOrZero()
                            val usdRate = liveData.usdCzkRate.finiteOrZero()
                            val gbpRate = liveData.gbpCzkRate.finiteOrZero()
                            val plnRate = liveData.plnCzkRate.finiteOrZero()
                            val chfRate = liveData.chfCzkRate.finiteOrZero()
                            val hufRate = liveData.hufCzkRate.finiteOrZero()
                            Text(
                                text = "EUR ${String.format(locale, "%.2f", eurRate)} · USD ${String.format(locale, "%.2f", usdRate)} · GBP ${String.format(locale, "%.2f", gbpRate)}\n" +
                                    "PLN ${String.format(locale, "%.2f", plnRate)} · CHF ${String.format(locale, "%.2f", chfRate)} · 100 HUF ${String.format(locale, "%.2f", hufRate * 100.0)} Kč",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = BodyFontFamily,
                                    fontSize = 11.sp
                                )
                            )

                            if (!liveData.isFxLive) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Offline: showing bundled ČNB fallback FX",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (onRetry != null) {
                                        TextButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onRetry()
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Retry ČNB FX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Section 2 Header: Bundled 2026 Czech Statutory Constants
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Bundled 2026 Czech Statutory Constants",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Local ZDP & pension reference values (not fetched from a live tax API)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ColorPill(
                            text = if (diffCount > 0) "$diffCount DIFFERENCES" else "MATCHES 2026",
                            color = if (diffCount > 0) BrandGold else GoodGreen,
                            fontSize = 9.sp,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.36f).dp

                    // Comparison List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = differences,
                            key = { idx, diff -> "${idx}_${diff.label}" }
                        ) { _, diff ->
                            SyncDifferenceCard(diff)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Unable to reach the ČNB exchange rate feed right now.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Check your internet connection and retry, or continue using your bundled 2026 Czech statutory defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading && liveData != null) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val updated = CzechEconomicSyncService.applyRegulatoryUpdates(
                            current = currentSettings,
                            live = liveData,
                            applyMacroInflation = true,
                            applyTaxAndPension = true
                        )
                        onApplySettings(updated)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("apply_all_sync_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply 2026 Statutory Defaults", maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
                }
            } else if (!isLoading && onRetry != null) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRetry()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry Sync", maxLines = 2, softWrap = true)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("close_sync_dialog_button")
            ) {
                Text("Keep Custom")
            }
        }
    )
}

@Composable
private fun SyncDifferenceCard(diff: SyncDifferenceItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (diff.isDifferent) BrandGold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (diff.isDifferent) BrandGold.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diff.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (diff.isDifferent) {
                    ColorPill(
                        text = "NEW VALUE",
                        color = BrandGold,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 4.dp,
                        verticalPadding = 1.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current: ${diff.currentValueFormatted}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = BodyFontFamily,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = "Official: ${diff.liveValueFormatted}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (diff.isDifferent) BrandTeal else GoodGreen,
                        fontFamily = BodyFontFamily,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = diff.impactHint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
        }
    }
}
