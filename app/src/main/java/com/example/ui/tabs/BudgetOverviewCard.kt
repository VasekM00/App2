package com.example.ui.tabs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.DcaAllocationBreakdownBar
import com.example.ui.components.MetricInfo
import com.example.ui.components.SankeyCashFlowCard
import com.example.ui.components.SubscriptionAuditorCard
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
internal fun BudgetOverviewCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity>,
    allImportedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
    dismissedSubscriptionMerchants: Set<String> = emptySet(),
    onDismissSubscription: ((String) -> Unit)? = null,
    onRestoreSubscription: ((String) -> Unit)? = null,
    onClearAllDismissedSubscriptions: (() -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()

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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly Cash Flow Summary",
                    subtitle = "Income, living expenses & net monthly surplus · Tap for info",
                    badgeText = "SUMMARY",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricStatBox(
                        label = "Total Income",
                        value = fmtCZK(totalInc),
                        info = CashFlowMetricInfos.totalIncome,
                        onShowInfo = onShowInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Living Expenses",
                        value = fmtCZK(totalExp),
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MetricStatBox(
                        label = "Surplus",
                        value = fmtCZK(netFlow),
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
            swrPct = state.settings.safeWithdrawalRatePct,
            dismissedMerchantKeys = dismissedSubscriptionMerchants,
            onDismissMerchant = onDismissSubscription,
            onRestoreMerchant = onRestoreSubscription,
            onClearAllDismissed = onClearAllDismissedSubscriptions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Monthly Savings & Investments Allocation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Savings & Investment Allocations",
                    subtitle = "Automated wealth accumulation flows",
                    badgeText = "DCA FLOW",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                DcaAllocationBreakdownBar(settings = state.settings)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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

    Surface(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.3.sp,
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = valueColor
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
