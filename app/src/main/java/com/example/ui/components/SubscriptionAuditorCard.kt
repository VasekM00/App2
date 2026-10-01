package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import com.example.util.SubscriptionAuditor
import java.util.Locale

@Composable
fun SubscriptionAuditorCard(
    transactions: List<ImportedBankTransactionEntity>,
    swrPct: Double,
    dismissedMerchantKeys: Set<String> = emptySet(),
    onDismissMerchant: ((String) -> Unit)? = null,
    onRestoreMerchant: ((String) -> Unit)? = null,
    onClearAllDismissed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var showDismissedSection by remember { mutableStateOf(false) }

    val audit = remember(transactions, swrPct, dismissedMerchantKeys) {
        SubscriptionAuditor.auditSubscriptions(transactions, swrPct, dismissedMerchantKeys)
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
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Subscription & Fixed Debit Auditor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (audit.items.isEmpty()) {
                            "Recurring debits & perpetual FIRE capital requirement"
                        } else {
                            "${audit.items.size} detected recurring subscriptions (${fmtCZK(audit.totalMonthlyBurn)}/mo)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (audit.priceCreepCount > 0) {
                        ColorPill(
                            text = "${audit.priceCreepCount} PRICE CREEP",
                            color = BadRed
                        )
                    } else if (audit.items.isNotEmpty()) {
                        ColorPill(
                            text = "${audit.items.size} ACTIVE",
                            color = BrandTeal
                        )
                    } else {
                        ColorPill(
                            text = "0 DETECTED",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }) {
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
                        shape = RoundedCornerShape(10.dp),
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
                                        contentPadding = PaddingValues(0.dp)
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Monthly Burn",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = fmtCZK(audit.totalMonthlyBurn),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandGold
                                )
                                Text(
                                    text = "${fmtCZK(audit.annualizedBurn)}/yr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "FIRE Capital Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = fmtCZK(audit.fireCapitalRequired),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandTeal
                                )
                                Text(
                                    text = "to fund forever at ${fmtPct(swrPct)} SWR",
                                    style = MaterialTheme.typography.labelSmall,
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
                        audit.items.forEach { item ->
                            val itemAnnualBurn = item.normalizedMonthlyAmount * 12.0
                            val itemFireCapital = itemAnnualBurn / (swrPct / 100.0).coerceAtLeast(0.001)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    1.dp,
                                    if (item.priceCreepDelta > 0.0) BadRed.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                                                    color = BrandTeal
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
                                                        "${fmtCZK(item.normalizedMonthlyAmount)}/mo"
                                                    } else {
                                                        "${fmtCZK(item.latestMonthlyAmount)}/mo"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                )
                                                val cadenceSubtitle = when (item.billingCadence) {
                                                    "ANNUAL" -> "${fmtCZK(item.latestMonthlyAmount)}/yr · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    "QUARTERLY" -> "${fmtCZK(item.latestMonthlyAmount)}/qtr · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    "SEMI_ANNUAL" -> "${fmtCZK(item.latestMonthlyAmount)}/half-yr · FIRE: ${fmtCZK(itemFireCapital)}"
                                                    else -> "FIRE: ${fmtCZK(itemFireCapital)}"
                                                }
                                                Text(
                                                    text = cadenceSubtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (onDismissMerchant != null) {
                                                IconButton(
                                                    onClick = { onDismissMerchant(item.merchantKey) },
                                                    modifier = Modifier.size(28.dp).testTag("dismiss_subscription_${item.merchantKey}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss Subscription",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
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
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                        contentDescription = null,
                                                        tint = BadRed,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Price Increase: +${fmtCZK(item.priceCreepDelta)} (+${String.format(Locale.ROOT, "%.1f", item.priceCreepPct)}%)",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = BadRed
                                                    )
                                                }
                                                if (item.previousMonthlyAmount != null) {
                                                    Text(
                                                        text = "was ${fmtCZK(item.previousMonthlyAmount)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
}
