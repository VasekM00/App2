package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.util.RecurringSubscriptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtExactCZK
import com.example.util.Formatters.fmtPct
import com.example.util.SubscriptionAuditor
import java.util.Locale

@Composable
fun SubscriptionAuditorCard(
    transactions: List<ImportedBankTransactionEntity>,
    swrPct: Double,
    safetyBufferPct: Double = 0.0,
    dismissedMerchantKeys: Set<String> = emptySet(),
    onDismissMerchant: ((String) -> Unit)? = null,
    onRestoreMerchant: ((String) -> Unit)? = null,
    onClearAllDismissed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var showDismissedSection by rememberSaveable { mutableStateOf(false) }
    var selectedMerchantKeyForDetail by rememberSaveable { mutableStateOf<String?>(null) }

    val audit by produceState(
        initialValue = remember(transactions, swrPct, safetyBufferPct, dismissedMerchantKeys) {
            if (transactions.size <= 50) {
                SubscriptionAuditor.auditSubscriptions(transactions, swrPct, dismissedMerchantKeys, safetyBufferPct)
            } else {
                com.example.util.SubscriptionAuditSummary(emptyList(), 0.0, 0.0, 0.0, 0)
            }
        },
        transactions, swrPct, safetyBufferPct, dismissedMerchantKeys
    ) {
        if (transactions.size > 50) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                SubscriptionAuditor.auditSubscriptions(transactions, swrPct, dismissedMerchantKeys, safetyBufferPct)
            }
        }
    }
    val selectedItemForDetail = selectedMerchantKeyForDetail?.let { key ->
        audit.items.firstOrNull { it.merchantKey == key }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subscription_auditor_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Recurring Subscriptions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (audit.items.isEmpty()) {
                            "Auto-detected recurring bills"
                        } else {
                            "${audit.items.size} recurring · ${fmtExactCZK(audit.totalMonthlyBurn)} monthly"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (audit.priceCreepCount > 0) {
                        ColorPill(
                            text = "${audit.priceCreepCount} PRICE CREEP",
                            color = BadRed,
                            fontFamily = BodyFontFamily
                        )
                    } else if (audit.items.isNotEmpty()) {
                        ColorPill(
                            text = "${audit.items.size} ACTIVE",
                            color = BrandTeal,
                            fontFamily = BodyFontFamily
                        )
                    }

                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                if (audit.items.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No recurring monthly debits detected. Import multi-month bank statements to automatically identify recurring media, telecom, utility, and insurance debits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (dismissedMerchantKeys.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${dismissedMerchantKeys.size} item${if (dismissedMerchantKeys.size > 1) "s" else ""} currently dismissed.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandGold
                                )
                                if (onClearAllDismissed != null) {
                                    TextButton(
                                        onClick = onClearAllDismissed,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Restore all dismissed items", color = BrandTeal, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Summary KPIs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Monthly Burn",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = fmtExactCZK(audit.totalMonthlyBurn.finiteOrZero()),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = BodyFontFamily
                                    ),
                                    color = BrandGold
                                )
                                Text(
                                    text = "${fmtExactCZK(audit.annualizedBurn.finiteOrZero())} annual",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = BodyFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "FIRE Capital Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = fmtCZK(audit.fireCapitalRequired.finiteOrZero()),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = BodyFontFamily
                                    ),
                                    color = BrandTeal
                                )
                                val safeBuffer = safetyBufferPct.finiteOrZero().coerceAtLeast(0.0)
                                val bufferNote = if (safeBuffer > 0.0) " (+${fmtPct(safeBuffer, 0)} buffer)" else ""
                                Text(
                                    text = "to fund forever at ${fmtPct(swrPct)} SWR$bufferNote",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = BodyFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Audited Subscriptions & Fixed Overhead",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val safeBufferMult = 1.0 + safetyBufferPct.finiteOrZero().coerceAtLeast(0.0) / 100.0
                        audit.items.forEach { item ->
                            val itemAnnualBurn = item.normalizedMonthlyAmount.finiteOrZero() * 12.0
                            val itemFireCapital = ((itemAnnualBurn / (swrPct.finiteOrZero() / 100.0).coerceAtLeast(0.001)) * safeBufferMult).finiteOrZero()

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    1.dp,
                                    if (item.priceCreepDelta > 0.0) BadRed.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMerchantKeyForDetail = item.merchantKey }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = formatCategoryLabel(item.category),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BrandTeal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                when (item.billingCadence) {
                                                    "ANNUAL" -> ColorPill(
                                                        text = "ANNUAL",
                                                        color = BrandGold,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        horizontalPadding = 4.dp,
                                                        verticalPadding = 1.dp
                                                    )
                                                    "QUARTERLY" -> ColorPill(
                                                        text = "QUARTERLY",
                                                        color = BrandTeal,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        horizontalPadding = 4.dp,
                                                        verticalPadding = 1.dp
                                                    )
                                                    "SEMI_ANNUAL" -> ColorPill(
                                                        text = "SEMI-ANNUAL",
                                                        color = BrandTeal,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        horizontalPadding = 4.dp,
                                                        verticalPadding = 1.dp
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                val isNonMonthly = item.billingCadence != "MONTHLY"
                                                Text(
                                                    text = if (isNonMonthly) {
                                                        fmtExactCZK(item.normalizedMonthlyAmount)
                                                    } else {
                                                        fmtExactCZK(item.latestMonthlyAmount)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = BodyFontFamily
                                                    ),
                                                    maxLines = 2,
                                                    softWrap = true
                                                )
                                                val cadenceSubtitle = when (item.billingCadence) {
                                                    "ANNUAL" -> "${fmtExactCZK(item.latestMonthlyAmount)}/year · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    "QUARTERLY" -> "${fmtExactCZK(item.latestMonthlyAmount)}/quarter · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    "SEMI_ANNUAL" -> "${fmtExactCZK(item.latestMonthlyAmount)}/6 months · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    else -> "FIRE: ${fmtCZK(itemFireCapital)}"
                                                }
                                                Text(
                                                    text = cadenceSubtitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = BodyFontFamily,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    softWrap = true
                                                )
                                            }

                                            if (onDismissMerchant != null) {
                                                IconButton(
                                                    onClick = { onDismissMerchant(item.merchantKey) },
                                                    modifier = Modifier.size(48.dp).testTag("dismiss_subscription_${item.merchantKey}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss Subscription",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (item.priceCreepDelta > 0.0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = BadRed.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                        contentDescription = null,
                                                        tint = BadRed,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Price Increase: +${fmtExactCZK(item.priceCreepDelta)} (+${fmtPct(item.priceCreepPct, 1)})",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = BodyFontFamily
                                                        ),
                                                        color = BadRed,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (item.previousMonthlyAmount != null) {
                                                    Text(
                                                        text = "was ${fmtExactCZK(item.previousMonthlyAmount)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = BodyFontFamily
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (dismissedMerchantKeys.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${dismissedMerchantKeys.size} dismissed item${if (dismissedMerchantKeys.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { showDismissedSection = !showDismissedSection },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (showDismissedSection) "Hide Dismissed" else "View Dismissed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandTeal
                                    )
                                }
                                if (onClearAllDismissed != null) {
                                    TextButton(
                                        onClick = onClearAllDismissed,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Restore All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandGold
                                        )
                                    }
                                }
                            }
                        }

                        if (showDismissedSection) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                dismissedMerchantKeys.sorted().forEach { key ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (onRestoreMerchant != null) {
                                                TextButton(
                                                    onClick = { onRestoreMerchant(key) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Text(
                                                        text = "Restore",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoodGreen
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedItemForDetail?.let { item ->
        SubscriptionDetailDialog(
            item = item,
            swrPct = swrPct,
            safetyBufferPct = safetyBufferPct,
            onDismissItem = {
                onDismissMerchant?.invoke(item.merchantKey)
                selectedMerchantKeyForDetail = null
            },
            onClose = { selectedMerchantKeyForDetail = null }
        )
    }
}
