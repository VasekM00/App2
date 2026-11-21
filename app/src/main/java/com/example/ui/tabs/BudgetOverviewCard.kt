package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.charts.DcaAllocationBreakdownBar
import com.example.ui.components.EditInvestmentAllocationsDialog
import com.example.ui.components.MerchantLeaderboardCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.SankeyCashFlowCard
import com.example.ui.components.SubscriptionAuditorCard
import com.example.ui.components.UpcomingBillsCalendarCard
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.GoodGreen
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact

@Composable
internal fun BudgetOverviewCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var showEditInvestmentsDialog by rememberSaveable { mutableStateOf(false) }

    val totalInc = state.currentIncome.totalMonthly
    val totalExp = state.totalLivingCostMonthly
    val netFlow = totalInc - totalExp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 1. Key Metrics Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Budget Summary",
                    subtitle = "Take-home income vs. baseline living costs",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(14.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useCompact = maxWidth < 310.dp
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricStatBox(
                                label = "Income",
                                value = if (useCompact) fmtCompact(totalInc) else fmtCZK(totalInc),
                                info = CashFlowMetricInfos.totalIncome,
                                onShowInfo = onShowInfo,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            MetricStatBox(
                                label = "Living Costs",
                                value = if (useCompact) fmtCompact(totalExp) else fmtCZK(totalExp),
                                info = CashFlowMetricInfos.livingExpenses,
                                onShowInfo = onShowInfo,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            MetricStatBox(
                                label = "Leftover",
                                value = if (useCompact) fmtCompact(netFlow) else fmtCZK(netFlow),
                                valueColor = if (netFlow >= 0) GoodGreen else BadRed,
                                info = CashFlowMetricInfos.surplus,
                                onShowInfo = onShowInfo,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Interactive Sankey Cash Flow Waterfall
        SankeyCashFlowCard(
            state = state,
            onShowInfo = onShowInfo
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Subscription & Fixed Debit Auditor Card
        SubscriptionAuditorCard(
            transactions = allImportedTransactions,
            swrPct = state.effectiveSettings.safeWithdrawalRatePct,
            safetyBufferPct = state.effectiveSettings.safetyBufferPct,
            dismissedMerchantKeys = dismissedSubscriptionMerchants,
            onDismissMerchant = onDismissSubscription,
            onRestoreMerchant = onRestoreSubscription,
            onClearAllDismissed = onClearAllDismissedSubscriptions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Upcoming Bills Calendar Card (Projected 30 Days)
        UpcomingBillsCalendarCard(
            transactions = allImportedTransactions,
            settings = state.settings,
            dismissedSubscriptionMerchants = dismissedSubscriptionMerchants,
            initiallyExpanded = false,
            onShowInfo = onShowInfo,
            onUpdateSettings = onUpdateSettings,
            onDismissSubscription = onDismissSubscription,
            onReduceSettings = onReduceSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Merchant Leaderboard Card (Spend Drift Analysis)
        MerchantLeaderboardCard(
            transactions = allImportedTransactions,
            settings = state.settings,
            initiallyExpanded = false,
            onShowInfo = onShowInfo,
            onUpdateSettings = onUpdateSettings,
            onUpdateImportedTransactionCategory = onUpdateImportedTransactionCategory,
            onReduceSettings = onReduceSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Monthly Savings & Investments Allocation Card (includes emergency reserve build contribution)
        val snap = state.snapshot
        val sEff = state.effectiveSettings
        val emergencyReserveContrib = snap.monthlyReserveContribution
        val totalMonthlySavingsAndDca = snap.monthlySavingsAndInvestingTotal
        val isManualReserve = sEff.emergencyReserveMonthlyOverride.isFinite() &&
            sEff.emergencyReserveMonthlyOverride >= 0.0
        val allocationsInfo = remember(sEff) {
            CashFlowMetricInfos.savingsAndInvestmentAllocations(sEff)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("savings_investment_allocations_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackHeader = maxWidth < 330.dp
                    val subtitleText = when {
                        isManualReserve -> "DCA + manual reserve (${fmtCZK(emergencyReserveContrib)}/month) · Tap for info"
                        emergencyReserveContrib > 0.0 -> "DCA + 24-mo reserve catch-up · Tap for formula"
                        else -> "Automated wealth accumulation (DCA) · Tap for info"
                    }
                    val totalAndEditCluster: @Composable () -> Unit = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ColorPill(
                                text = "${fmtCZK(totalMonthlySavingsAndDca)} monthly",
                                color = BrandTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 8.dp,
                                verticalPadding = 4.dp,
                                cornerRadius = 8.dp
                            )
                            if (onUpdateSettings != null || onReduceSettings != null) {
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button) { showEditInvestmentsDialog = true }
                                        .testTag("btn_edit_investments"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BrandTeal.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.28f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit investments",
                                                tint = BrandTeal,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Edit",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandTeal,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 0.3.sp
                                                ),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (stackHeader) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CardHeaderPill(
                                title = "Savings & Investment Allocations",
                                subtitle = subtitleText,
                                accentColor = BrandTeal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .infoTapHold(allocationsInfo, onShowInfo)
                            )
                            totalAndEditCluster()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .infoTapHold(allocationsInfo, onShowInfo)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Savings & Investment Allocations",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            totalAndEditCluster()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                DcaAllocationBreakdownBar(settings = state.settings)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showEditInvestmentsDialog && (onUpdateSettings != null || onReduceSettings != null)) {
        EditInvestmentAllocationsDialog(
            settings = state.settings,
            onDismiss = { showEditInvestmentsDialog = false },
            onSave = { updated ->
                onUpdateSettings?.invoke(updated)
                showEditInvestmentsDialog = false
            },
            onSaveTransform = onReduceSettings
        )
    }
}

@Composable
internal fun MetricStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier.infoTapHold(info, onShowInfo)
    } else Modifier

    Box(
        modifier = modifier
            .then(clickModifier)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    fontSize = 13.sp,
                    color = valueColor
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
