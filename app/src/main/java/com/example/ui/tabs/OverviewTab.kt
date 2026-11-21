package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.components.KpiCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.infoTapHold
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.GoodGreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onNavigateToIncome: (() -> Unit)? = null,
    onNavigateToProjections: (() -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onNavigateToNetWorth: (() -> Unit)? = null,
    onNavigateToEmergencyReserve: (() -> Unit)? = null,
    onNavigateToSavingsRate: (() -> Unit)? = null,
    onNavigateToSandbox: (() -> Unit)? = null,
    onOpenQuickGuide: (() -> Unit)? = null,
    scrollToEmergencyReserveTrigger: Int = 0,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val infoState = rememberMetricInfoState()
    @Suppress("UNUSED_VARIABLE")
    val unusedParams = Triple(ledgerEntries.size, onOpenSettings, onNavigateToSandbox)
    val emergencyReserveRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(scrollToEmergencyReserveTrigger) {
        if (scrollToEmergencyReserveTrigger > 0) {
            emergencyReserveRequester.bringIntoView()
        }
    }

    val incomeInfo = MetricInfo(
        title = "Household Net Inflows",
        category = "Cash Flow & Inflows",
        formulaOrRule = "Salaries + Benefits + Vouchers + Gifts + Inflows + Lump Sums",
        explanation = "Measures true monthly cash generation after all personal Czech taxes (15%/23%), social security (7.1%), and health insurance (4.5%). Includes meal vouchers, tax-exempt family gifts, parental allowance, and lecturing.",
        statutoryReference = "Act No. 586/1992 Coll. (Income Tax Act)",
        practicalImplication = "Accurately represents the total disposable cash generated by the household each month for living expenses and DCA investing.",
        accentColor = BrandTeal
    )

    val snap = state.snapshot
    val sEff = state.effectiveSettings
    val livingCostInfo = MetricInfo(
        title = "Monthly Living Burn & Leftover",
        category = "Cash Flow & Living Costs",
        formulaOrRule = "Monthly Leftover = Take-Home Inflows (${fmtCZK(snap.monthlyTakeHomeInflows)}) − Living Costs (${fmtCZK(snap.monthlyLivingCosts)})",
        explanation = "Total recurring housing, groceries, family, and discretionary living outlays compared against household take-home cash.",
        practicalImplication = "After covering monthly living costs, ${fmtCZK(snap.monthlyLeftoverBeforeInvesting)} remains available for investing and cash buffers.",
        accentColor = BrandGold
    )

    val unallocatedCashInfo = MetricInfo(
        title = "Monthly Unallocated Cash",
        category = "Residual Cash Flow",
        formulaOrRule = "Unallocated = Take-Home (${fmtCZK(snap.monthlyTakeHomeInflows)}) − Living (${fmtCZK(snap.monthlyLivingCosts)}) − Own Investing (${fmtCZK(snap.monthlyOwnInvesting)})",
        explanation = "Liquid surplus remaining each month after all living costs and automated ETF/DIP/DPS investments have been funded.",
        practicalImplication = "This buffer absorbs irregular expenses, builds your emergency fund, or can be swept into extra taxable ETF purchases.",
        accentColor = BrandBlue
    )

    val savingsRateInfo = MetricInfo(
        title = "Monthly Investing & Employer Match",
        category = "Cash Flow & DCA Firepower",
        formulaOrRule = "Total DCA = Own Contributions (${fmtCZK(snap.monthlyOwnInvesting)}) + Employer Match (${fmtCZK(snap.monthlyEmployerMatch)}) = ${fmtCZK(snap.monthlyTotalInvesting)}/mo",
        explanation = "Measures household own monthly automated investing plus tax-exempt employer retirement contributions (ZDP § 6(9)(m)). Combined total inflows including match yield a ${fmtPct(snap.savingsRatePct, 1)} savings rate (${fmtPct(snap.savingsRateOwnCashPct, 1)} from own cash).",
        statutoryReference = "DCA Wealth Building Strategy & ZDP § 6(9)(m) / § 15a Tax Shield",
        practicalImplication = "Directing surplus cash into tax-advantaged DIP/DPS and low-fee ETFs compounds wealth directly toward FIRE.",
        accentColor = GoodGreen
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("overview_tab")
    ) {
        HeroHeader(
            state = state,
            onNavigateToNetWorth = { onNavigateToNetWorth?.invoke() ?: onNavigateToProjections?.invoke() },
            onNavigateToEmergencyReserve = { onNavigateToEmergencyReserve?.invoke() },
            onNavigateToSavingsRate = { onNavigateToSavingsRate?.invoke() ?: onNavigateToIncome?.invoke() },
            onOpenQuickGuide = onOpenQuickGuide
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4 Interactive KPI Cards (Symmetric 2x2 Grid, zero Hero duplication)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Monthly Income",
                value = fmtCZK(snap.monthlyTakeHomeInflows),
                hint = if (sEff.isSingleHousehold) "Net monthly income" else "Combined take-home",
                accentColor = BrandTeal,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTagStr = "kpi_family_net",
                info = incomeInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToIncome
            )

            KpiCard(
                title = "Monthly Living",
                value = fmtCZK(snap.monthlyLivingCosts),
                hint = "Leftover ${fmtCompact(snap.monthlyLeftoverBeforeInvesting)}",
                accentColor = BrandGold,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTagStr = "kpi_monthly_living",
                info = livingCostInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToIncome
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Own Investing",
                value = fmtCZK(snap.monthlyOwnInvesting),
                hint = if (snap.monthlyEmployerMatch > 0.0) {
                    "+ ${fmtCompact(snap.monthlyEmployerMatch)} employer match"
                } else {
                    "Automated monthly DCA"
                },
                accentColor = GoodGreen,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTagStr = "kpi_savings_rate",
                info = savingsRateInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToIncome
            )

            KpiCard(
                title = "Unallocated Cash",
                value = fmtCZK(snap.monthlyUnallocatedCash),
                hint = "Free cash after DCA",
                accentColor = BrandBlue,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                testTagStr = "kpi_unallocated_cash",
                info = unallocatedCashInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToIncome
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Emergency Reserve & Runway Goal Tracker Widget
        EmergencyReserveWidget(
            state = state,
            modifier = Modifier.bringIntoViewRequester(emergencyReserveRequester),
            onShowInfo = { infoState.show(it) },
            onUpdateSettings = onUpdateSettings,
            onReduceSettings = onReduceSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Strategic Highlights (Executive 2x2 Strategic Readiness Matrix)
        val liquidBridgeInfo = MetricInfo(
            title = "Pre-60 Liquid Bridge",
            category = "Two-Bucket Liquidity Architecture",
            formulaOrRule = "Required Bridge = Annual Living Cost × Years from FIRE to Age 60",
            explanation = "In the Czech Republic, DIP and DPS tax-advantaged accounts cannot be accessed without penalty before age 60. Therefore, retiring early requires sufficient liquid taxable investments (Portu / brokerage) to bridge spending until age 60.",
            statutoryReference = "§ 15a Act No. 586/1992 Coll. (ZDP) & Act No. 427/2011 Coll.",
            practicalImplication = if (state.isLiquidBridgeFundedToday) "Your liquid Portu balance is already sufficient to bridge early retirement to age 60 without tapping locked pension accounts." else "Focus contributions on liquid taxable brokerage to ensure pre-60 cash needs are covered.",
            accentColor = if (state.isLiquidBridgeFundedToday) GoodGreen else BrandGold
        )

        val coastMilestone = state.fireMilestones.coastFire
        val coastTargetYear = state.settings.baseYear + (state.settings.vStatePensionAge - state.settings.primaryAge).coerceAtLeast(0)
        val coastFireInfo = MetricInfo(
            title = "Coast FIRE Milestone",
            category = "Financial Independence Milestones",
            formulaOrRule = "Coast Target = FIRE Target / (1 + Real CAGR)^(Years to $coastTargetYear)",
            explanation = "The portfolio size where compounding alone — with zero further contributions — will grow to your full target by $coastTargetYear.",
            practicalImplication = if (coastMilestone.isAchieved) "You have already secured traditional retirement in $coastTargetYear through past savings! Further contributions simply accelerate early retirement." else "Focus DCA contributions until Coast FIRE is reached to secure a lifelong financial safety net.",
            accentColor = if (coastMilestone.isAchieved) GoodGreen else BrandGold
        )

        val survivalRate = state.historicalRetirementSurvival.successRatePct
        val survivalInfo = MetricInfo(
            title = "Historical Survival Probability",
            category = "Actuarial Longevity & Sequence Risk",
            formulaOrRule = "Survival Rate = Non-depleted Paths / Total Simulated Historical Market Paths",
            explanation = "Tests your decumulation strategy through 56 years (1970–2025) of global equity market cycles and drawdown periods (including 1970s stagflation, 2000 dot-com bust, and 2008 global financial crisis) with sequence-of-returns risk.",
            statutoryReference = "Historical Block-Bootstrap Simulation (1970–2025)",
            practicalImplication = "A rate above 90% provides strong actuarial confidence that your portfolio will outlive you without depleting principal.",
            accentColor = if (survivalRate >= 95.0) GoodGreen else BrandTeal
        )

        val taxHelper = state.taxReturnHelper
        val taxRefundTotal = taxHelper.totalIncrementalValue
        val taxReliefParts = buildList {
            if (taxHelper.spouseCredit > 0.0) add("Partner Credit (${fmtCZK(taxHelper.spouseCredit)})")
            if (taxHelper.childBonus > 0.0) add("Child Tax Credit (${fmtCZK(taxHelper.childBonus)})")
            if (taxHelper.dipSaving > 0.0) add("DIP & DPS Tax Deduction (${fmtCZK(taxHelper.dipSaving)})")
        }
        val taxFormulaText = if (taxReliefParts.isNotEmpty()) {
            "Annual Relief = ${taxReliefParts.joinToString(" + ")} (Total: ${fmtCZK(taxRefundTotal)}/year)"
        } else {
            "Annual Relief = Partner Credit (${fmtCZK(taxHelper.spouseCredit)}) + Child Tax Credit (${fmtCZK(taxHelper.childBonus)}) + DIP & DPS Tax Deduction (${fmtCZK(taxHelper.dipSaving)}) (Total: ${fmtCZK(taxRefundTotal)}/year)"
        }
        val taxRefundInfo = MetricInfo(
            title = "Annual Tax Refund & Relief",
            category = "Czech Tax Optimization",
            formulaOrRule = taxFormulaText,
            explanation = "This is not direct money you save from monthly spending. Rather, it is an annual statutory tax refund or cash rebate from the state paid out in spring after your annual employer tax reconciliation or via your personal income tax return.",
            statutoryReference = "§ 15a, § 35ba, § 35c Act No. 586/1992 Coll. (ZDP)",
            practicalImplication = "Because this is an annual cash rebate returned to you by the tax authority, it represents unbudgeted lump-sum liquidity each spring that can be directly invested into your portfolio without reducing your monthly living budget.",
            accentColor = GoodGreen
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("strategic_highlights_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Plan Health & Tax Relief",
                    subtitle = "Tap any tile to see how it is calculated",
                    accentColor = BrandTeal,
                    icon = Icons.Default.AutoAwesome
                )

                Spacer(modifier = Modifier.height(14.dp))

                val useSingleColumnHealth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 385 &&
                    androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
                val bridgeValue = if (state.isLiquidBridgeFundedToday) "Ready (100%)" else "Gap ${fmtCompact(state.liquidBridgeDeficitToday)}"
                val bridgeSub = if (state.isLiquidBridgeFundedToday) "Covers pre-60 years" else "Needed before age 60"
                val bridgeColor = if (state.isLiquidBridgeFundedToday) GoodGreen else BrandGold
                val coastValue = if (coastMilestone.isAchieved) "Achieved" else "${fmtPct(coastMilestone.progressPct, 0)} funded"
                val coastSub = if (coastMilestone.isAchieved) "Grows by $coastTargetYear" else "Goal: ${fmtCompact(coastMilestone.targetAmountToday)}"
                val coastColor = if (coastMilestone.isAchieved) GoodGreen else BrandGold
                val survivalColor = if (survivalRate >= 95.0) GoodGreen else if (survivalRate >= 85.0) BrandTeal else BrandGold

                if (useSingleColumnHealth) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StrategicMetricTile(
                            label = "Early-Access Bridge",
                            value = bridgeValue,
                            subLabel = bridgeSub,
                            accentColor = bridgeColor,
                            icon = Icons.Default.Shield,
                            info = liquidBridgeInfo,
                            onShowInfo = { infoState.show(it) },
                            modifier = Modifier.fillMaxWidth().testTag("strategic_tile_bridge")
                        )
                        StrategicMetricTile(
                            label = "Coast FIRE",
                            value = coastValue,
                            subLabel = coastSub,
                            accentColor = coastColor,
                            icon = Icons.Default.Star,
                            info = coastFireInfo,
                            onShowInfo = { infoState.show(it) },
                            modifier = Modifier.fillMaxWidth().testTag("strategic_tile_milestone")
                        )
                        StrategicMetricTile(
                            label = "Annual Tax Refund",
                            value = "+${fmtCZK(taxRefundTotal)}",
                            subLabel = "Spring tax rebate",
                            accentColor = GoodGreen,
                            icon = Icons.Default.AccountBalance,
                            info = taxRefundInfo,
                            onShowInfo = { infoState.show(it) },
                            modifier = Modifier.fillMaxWidth().testTag("strategic_tile_tax_refund")
                        )
                        StrategicMetricTile(
                            label = "Market Stress Score",
                            value = "${survivalRate.roundToInt()}% Pass",
                            subLabel = "Historical crash test",
                            accentColor = survivalColor,
                            icon = Icons.Default.CheckCircle,
                            info = survivalInfo,
                            onShowInfo = { infoState.show(it) },
                            modifier = Modifier.fillMaxWidth().testTag("strategic_tile_survival")
                        )
                    }
                } else {
                    // Symmetric 2x2 Strategic Metric Grid
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: Early-Access Bridge + Coast FIRE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StrategicMetricTile(
                                label = "Early-Access Bridge",
                                value = bridgeValue,
                                subLabel = bridgeSub,
                                accentColor = bridgeColor,
                                icon = Icons.Default.Shield,
                                info = liquidBridgeInfo,
                                onShowInfo = { infoState.show(it) },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("strategic_tile_bridge")
                            )

                            StrategicMetricTile(
                                label = "Coast FIRE",
                                value = coastValue,
                                subLabel = coastSub,
                                accentColor = coastColor,
                                icon = Icons.Default.Star,
                                info = coastFireInfo,
                                onShowInfo = { infoState.show(it) },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("strategic_tile_milestone")
                            )
                        }

                        // Row 2: Annual Tax Relief + Stress Score
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StrategicMetricTile(
                                label = "Annual Tax Refund",
                                value = "+${fmtCZK(taxRefundTotal)}",
                                subLabel = "Spring tax rebate",
                                accentColor = GoodGreen,
                                icon = Icons.Default.AccountBalance,
                                info = taxRefundInfo,
                                onShowInfo = { infoState.show(it) },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("strategic_tile_tax_refund")
                            )

                            StrategicMetricTile(
                                label = "Market Stress Score",
                                value = "${survivalRate.roundToInt()}% Pass",
                                subLabel = "Historical crash test",
                                accentColor = survivalColor,
                                icon = Icons.Default.CheckCircle,
                                info = survivalInfo,
                                onShowInfo = { infoState.show(it) },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("strategic_tile_survival")
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Modal Metric Info Dialog
    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}

@Composable
private fun StrategicMetricTile(
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    icon: ImageVector,
    info: MetricInfo,
    onShowInfo: (MetricInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .infoTapHold(info, onShowInfo)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    color = accentColor,
                    fontSize = 16.sp
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                maxLines = 3,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
