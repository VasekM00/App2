package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.parseCustomExpenses
import com.example.domain.parseDeletedCategories
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandSky
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OnBrandTeal
import com.example.ui.theme.WarnAmber
import com.example.ui.theme.WarnAmberDark
import com.example.ui.theme.WarnAmberLight
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtExactCZK
import com.example.util.MerchantAnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class MerchantSpendStat(
    val rank: Int,
    val merchantName: String,
    val categoryName: String,
    val icon: ImageVector,
    val totalSpend: Double,
    val transactionCount: Int?,
    val spendSharePct: Double,
    val driftPct: Double?, // positive = spent more, negative = saved, null = no prior period baseline
    val driftLabelOverride: String? = null,
    val accentColor: Color,
    val editTarget: MerchantEditTarget? = null
)

@Composable
fun MerchantLeaderboardCard(
    transactions: List<ImportedBankTransactionEntity> = emptyList(),
    settings: SettingsEntity? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var selectedTimeframeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = 30 Days, 1 = 90 Days, 2 = Year to Date
    val timeframes = listOf("30 Days", "90 Days", "YTD")
    var editingTarget by remember { mutableStateOf<MerchantEditTarget?>(null) }

    val activeTimeframe = when (selectedTimeframeIndex) {
        1 -> MerchantAnalyticsEngine.MerchantDriftTimeframe.DAYS_90
        2 -> MerchantAnalyticsEngine.MerchantDriftTimeframe.YTD
        else -> MerchantAnalyticsEngine.MerchantDriftTimeframe.DAYS_30
    }

    val ytdMonths = remember {
        LocalDate.now(ZoneId.of("Europe/Prague")).monthValue.coerceAtLeast(1)
    }
    val periodMonths = when (selectedTimeframeIndex) {
        1 -> 3.0
        2 -> ytdMonths.toDouble()
        else -> 1.0
    }

    val monthlyLivingCost = settings?.let { FinancialEngine.totalLivingCostMonthly(it) } ?: 36_754.0
    val periodLivingCost = (monthlyLivingCost * periodMonths).coerceAtLeast(1.0)

    val isDark = LocalIsDarkTheme.current
    val statementItems by produceState(
        initialValue = remember(transactions, selectedTimeframeIndex, periodLivingCost) {
            if (transactions.size <= 50) {
                MerchantAnalyticsEngine.computeMerchantLeaderboardWithDrift(
                    transactions = transactions,
                    timeframe = activeTimeframe,
                    limit = 5,
                    totalBudget = periodLivingCost
                )
            } else emptyList()
        },
        transactions, selectedTimeframeIndex, periodLivingCost
    ) {
        if (transactions.size > 50) {
            value = withContext(Dispatchers.Default) {
                MerchantAnalyticsEngine.computeMerchantLeaderboardWithDrift(
                    transactions = transactions,
                    timeframe = activeTimeframe,
                    limit = 5,
                    totalBudget = periodLivingCost
                )
            }
        }
    }
    val hasStatementData = statementItems.isNotEmpty()

    val topMerchants = remember(statementItems, settings, selectedTimeframeIndex, periodMonths, periodLivingCost, isDark) {
        if (statementItems.isNotEmpty()) {
            statementItems.map { item ->
                val catDisplay = formatCategoryLabel(item.category)
                val (icon, color) = resolveMerchantIconAndColor(item.category.name, isDark)
                val driftOverride = when {
                    item.driftPct != null -> null
                    item.hasPriorPeriodData -> "New"
                    else -> "Baseline"
                }
                MerchantSpendStat(
                    rank = item.rank,
                    merchantName = item.displayName,
                    categoryName = catDisplay,
                    icon = icon,
                    totalSpend = item.totalSpend,
                    transactionCount = item.transactionCount,
                    spendSharePct = item.spendSharePct,
                    driftPct = item.driftPct,
                    driftLabelOverride = driftOverride,
                    accentColor = color,
                    editTarget = MerchantEditTarget.StatementMerchant(
                        merchantKey = item.merchantKey,
                        displayName = item.displayName,
                        currentCategory = item.category,
                        representativeTxIdsByMonth = item.representativeTxIdsByMonth,
                        totalSpend = item.totalSpend
                    )
                )
            }
        } else {
            // Derived directly from authentic Budget Expenses settings (no hardcoded fake merchants or fake drift)
            val s = settings ?: SettingsEntity()
            val deleted = parseDeletedCategories(s.deletedCategoriesJson)
            data class BudgetEntrySpec(val key: String, val name: String, val monthly: Double, val cat: String)
            val rawCategories = buildList {
                if ("rent" !in deleted && s.rentMonthly > 0.0) add(BudgetEntrySpec("rent", "Housing & Rent", s.rentMonthly, "HOUSING_RENT"))
                if ("groceries" !in deleted && s.groceriesMonthly > 0.0) add(BudgetEntrySpec("groceries", "Groceries & Supermarket", s.groceriesMonthly, "GROCERIES"))
                if ("cafes" !in deleted && s.cafesMonthly > 0.0) add(BudgetEntrySpec("cafes", "Cafes & Restaurants", s.cafesMonthly, "DINING_RESTAURANT"))
                if ("therapy" !in deleted && s.therapyMonthly > 0.0) add(BudgetEntrySpec("therapy", "Therapy & Care", s.therapyMonthly, "HEALTH_DRUGSTORE"))
                if ("charity" !in deleted && s.charityMonthly > 0.0) add(BudgetEntrySpec("charity", "Charity & Giving", s.charityMonthly, "CHARITY_DONATION"))
                if ("entertainment" !in deleted && s.entertainmentMonthly > 0.0) add(BudgetEntrySpec("entertainment", "Entertainment", s.entertainmentMonthly, "LIFESTYLE_LIVING"))
                if ("transport" !in deleted && s.transportMonthly > 0.0) add(BudgetEntrySpec("transport", "Transport", s.transportMonthly, "TRANSPORTATION"))
                if ("subscriptions" !in deleted && s.subscriptionsMonthly > 0.0) add(BudgetEntrySpec("subscriptions", "Subscriptions", s.subscriptionsMonthly, "SUBSCRIPTIONS_MEDIA"))
                if ("other_discretionary" !in deleted && s.otherDiscretionaryMonthly > 0.0) add(BudgetEntrySpec("other_discretionary", "Other Discretionary", s.otherDiscretionaryMonthly, "SHOPPING_GOODS"))
                parseCustomExpenses(s.customExpensesJson).filter { it.amount > 0.0 }.forEach { custom ->
                    add(BudgetEntrySpec("custom:${custom.id}", custom.name, custom.amount, "GENERAL_EXPENSE"))
                }
            }.sortedByDescending { it.monthly }.take(5)

            rawCategories.mapIndexed { idx, spec ->
                val periodSpend = spec.monthly * periodMonths
                val (icon, color) = resolveMerchantIconAndColor(spec.cat, isDark)
                val periodSubtitle = when (selectedTimeframeIndex) {
                    1 -> "Budget baseline · 3 months"
                    2 -> "Budget baseline · $ytdMonths months YTD"
                    else -> "Budget baseline · Monthly"
                }
                MerchantSpendStat(
                    rank = idx + 1,
                    merchantName = spec.name,
                    categoryName = periodSubtitle,
                    icon = icon,
                    totalSpend = periodSpend,
                    transactionCount = null,
                    spendSharePct = (periodSpend / periodLivingCost) * 100.0,
                    driftPct = null,
                    driftLabelOverride = "Planned",
                    accentColor = color,
                    editTarget = MerchantEditTarget.BudgetCategory(
                        settingKey = spec.key,
                        displayName = spec.name,
                        monthlyAmount = spec.monthly
                    )
                )
            }
        }
    }

    val top5Total = topMerchants.sumOf { it.totalSpend }.finiteOrZero()
    val top5Share = if (periodLivingCost > 0.0) ((top5Total / periodLivingCost) * 100.0).finiteOrZero() else 0.0

    val leaderboardInfo = MetricInfo(
        title = "Merchant Leaderboard & Spend Drift",
        category = "Cash Flow Intelligence",
        formulaOrRule = "Spend Drift = (Current Period - Prior Equal Period) / Prior Equal Period",
        explanation = "When bank statements are imported (STATEMENTS badge), groups real debit transactions by normalized merchant over 30 Days, 90 Days, or YTD and compares against the prior period. Before statement import (BUDGET ESTIMATE), ranks your configured Budget Expenses categories. Tap any row to edit its budget or merchant category.",
        practicalImplication = "Spot discretionary category drift early or reclassify mislabeled merchants so Cash Flow and Savings metrics stay accurate.",
        accentColor = BrandTeal
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("merchant_leaderboard_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BrandTeal.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header (collapsible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = !expanded
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Merchant Spend Drift",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (expanded) {
                                if (hasStatementData) "Top 5 merchants · tap row to recategorize" else "Top 5 budget categories · tap row to edit"
                            } else {
                                "Top 5: ${fmtCompact(top5Total)} (${String.format(java.util.Locale.ROOT, "%.0f", top5Share)}% of expenses)"
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
                        ColorPill(
                            text = if (hasStatementData) "STATEMENTS" else "BUDGET ESTIMATE",
                            color = BrandTeal,
                            fontSize = 9.5.sp,
                            fontFamily = BodyFontFamily
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowInfo?.invoke(leaderboardInfo)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Leaderboard Details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (expanded) {
                // Timeframe Chips (min 48dp target, selectableGroup + Role.Tab)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timeframes.forEachIndexed { index, name ->
                        val isSelected = selectedTimeframeIndex == index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTimeframeIndex = index
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Summary concentration banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOP 5 CONCENTRATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.3.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "${fmtCompact(top5Total)} (${String.format(java.util.Locale.ROOT, "%.1f", top5Share)}% of expenses)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                fontSize = 10.5.sp,
                                color = BrandTeal
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Merchant List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topMerchants.forEach { merchant ->
                        val canEdit = when (merchant.editTarget) {
                            is MerchantEditTarget.StatementMerchant -> onUpdateImportedTransactionCategory != null
                            is MerchantEditTarget.BudgetCategory ->
                                (onUpdateSettings != null || onReduceSettings != null) && settings != null
                            null -> false
                        }
                        MerchantRowItem(
                            merchant = merchant,
                            canEdit = canEdit,
                            onClick = if (canEdit) { { editingTarget = merchant.editTarget } } else null
                        )
                    }
                }
                }
            }
        }
    }

    editingTarget?.let { target ->
        EditMerchantSpendTargetDialog(
            target = target,
            settings = settings,
            onDismiss = { editingTarget = null },
            onUpdateSettings = onUpdateSettings,
            onUpdateImportedTransactionCategory = onUpdateImportedTransactionCategory,
            onReduceSettings = onReduceSettings
        )
    }
}

@Composable
private fun MerchantRowItem(
    merchant: MerchantSpendStat,
    canEdit: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shareFraction = (merchant.spendSharePct.finiteOrZero() / 100.0).toFloat().coerceIn(0f, 1f)
    val animShare by animateFloatAsState(
        targetValue = shareFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "merchant_share_anim"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canEdit && onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .testTag("merchant_leaderboard_item")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Rank Circle
                Surface(
                    shape = CircleShape,
                    color = merchant.accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, merchant.accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${merchant.rank}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = BodyFontFamily,
                                fontSize = 11.5.sp,
                                color = merchant.accentColor
                            )
                        )
                    }
                }

                // Name & Category
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = merchant.merchantName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (merchant.transactionCount != null) {
                            "${merchant.categoryName} · ${merchant.transactionCount} payments"
                        } else {
                            merchant.categoryName
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Spend & Drift
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtExactCZK(merchant.totalSpend),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = BodyFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        softWrap = false
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val driftVal = merchant.driftPct
                        val isPositive = driftVal != null && driftVal > 0.5
                        val isNegative = driftVal != null && driftVal < -0.5
                        val driftColor = when {
                            isPositive -> WarnAmber
                            isNegative -> GoodGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val driftIcon = when {
                            isPositive -> Icons.AutoMirrored.Filled.TrendingUp
                            isNegative -> Icons.AutoMirrored.Filled.TrendingDown
                            else -> Icons.AutoMirrored.Filled.TrendingFlat
                        }

                        if (driftVal != null) {
                            Icon(
                                imageVector = driftIcon,
                                contentDescription = null,
                                tint = driftColor,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = when {
                                merchant.driftLabelOverride != null -> merchant.driftLabelOverride
                                isPositive -> "+${String.format(java.util.Locale.ROOT, "%.1f", driftVal)}%"
                                isNegative -> "${String.format(java.util.Locale.ROOT, "%.1f", driftVal)}%"
                                else -> "Stable"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                fontSize = 9.5.sp,
                                color = driftColor
                            )
                        )
                        if (canEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit ${merchant.merchantName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // Share Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animShare)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(merchant.accentColor, merchant.accentColor.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }
    }
}

private fun resolveMerchantIconAndColor(categoryStr: String, isDark: Boolean = false): Pair<ImageVector, Color> {
    return when {
        categoryStr.contains("RENT", ignoreCase = true) || categoryStr.contains("HOUSING", ignoreCase = true) ->
            Icons.Default.Home to BrandSky
        categoryStr.contains("GROCERIES", ignoreCase = true) ->
            Icons.Default.ShoppingCart to if (isDark) Color(0xFF34D399) else Color(0xFF047857)
        categoryStr.contains("DINING", ignoreCase = true) || categoryStr.contains("RESTAURANT", ignoreCase = true) ->
            Icons.Default.Coffee to if (isDark) WarnAmberDark else WarnAmberLight
        categoryStr.contains("HEALTH", ignoreCase = true) || categoryStr.contains("PHARMACY", ignoreCase = true) ->
            Icons.Default.LocalPharmacy to if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
        categoryStr.contains("TRANSPORT", ignoreCase = true) ->
            Icons.Default.DirectionsBus to if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
        else ->
            Icons.Default.Storefront to BrandCyan
    }
}
