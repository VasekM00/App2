package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private object WaterfallMetricInfos {
    val emergencyReserve = MetricInfo(
        title = "Step 1: Emergency Reserve Safety Buffer",
        category = "Financial Defense",
        formulaOrRule = "3–6 Months Essential Living Costs in Yield Account (>4% p.a.)",
        explanation = "Liquid capital held in instant-access savings accounts or short-term treasury repos. Must be fully funded before locking money in multi-year pension products or equity markets.",
        practicalImplication = "Protects against having to sell ETF shares at a loss during bear market drawdowns or sudden unexpected income pauses.",
        accentColor = Color(0xFF0F766E)
    )

    val employerMatch = MetricInfo(
        title = "Step 2: Employer Pension Exemption",
        category = "Statutory Benefit",
        formulaOrRule = "Up to 50,000 CZK/yr per employee · 100% tax and levy exempt",
        explanation = "Employer contributions to retirement products (DPS, DIP, životní pojištění). Fully exempt from income tax, health insurance (9%), and social security (24.8%) for the employer, and 100% net for the employee.",
        statutoryReference = "§ 6 odst. 9 písm. m) Act No. 586/1992 Coll. (ZDP)",
        practicalImplication = "Instant 100% immediate return on employer capital with zero tax drag.",
        accentColor = Color(0xFF16A34A)
    )

    val dpsSubsidy = MetricInfo(
        title = "Step 3: State Supplementary Pension (DPS)",
        category = "State Cash Subsidy",
        formulaOrRule = "20% standard match · 40% youth match (<30 yrs) up to 1,700 CZK/mo",
        explanation = "Deposits up to 1,700 CZK/mo receive direct monthly state cash payments (up to 340 CZK/mo standard, or 680 CZK/mo youth match). Above 1,700 CZK, subsidy is 0 CZK; tax shield is identical to DIP, but DIP has lower ETF fees.",
        statutoryReference = "Act No. 427/2011 Coll. (ZPS)",
        practicalImplication = "Max out 1,700 CZK/mo first for guaranteed 20-40% state match. Any additional retirement savings beyond 1,700 CZK should go to DIP rather than DPS to avoid pension fund fee drag.",
        accentColor = Color(0xFFD97706)
    )

    val dipTaxShield = MetricInfo(
        title = "Step 4: Long-Term Investment Product (DIP)",
        category = "Retirement Tax Shield",
        formulaOrRule = "Up to 48,000 CZK/yr personal income tax deduction per earner",
        explanation = "Pre-tax investment into broad-market index ETFs (VWCE, S&P 500) through regulated brokers. Saves 7,200 CZK/yr (15% tax bracket) or 11,040 CZK/yr (23% progressive bracket) per person.",
        statutoryReference = "§ 15a Act No. 586/1992 Coll. (ZDP)",
        practicalImplication = "Preferred vehicle over DPS for savings beyond 1,700 CZK/mo: captures the exact same tax shield (up to 48k CZK/yr) but in low-cost global ETFs (TER ~0.2%) vs DPS fund fees.",
        accentColor = Color(0xFF16A34A)
    )

    val liquidBrokerage = MetricInfo(
        title = "Step 5: Liquid Brokerage Index ETFs",
        category = "Wealth Compounding & Early Retirement Bridge",
        formulaOrRule = "Broad Equity Index Funds (VWCE, S&P 500) · 3-year holding test",
        explanation = "Unconstrained dollar-cost averaging into liquid brokerage accounts (Portu, XTB, Fio). 100% tax-free capital gains after holding for 3 years, with immediate liquidity at any age without penalty.",
        statutoryReference = "§ 4 odst. 1 písm. u) Act No. 586/1992 Coll. (ZDP)",
        practicalImplication = "Serves as the vital financial bridge between early retirement and statutory state pension age, free from age-60 withdrawal locks.",
        accentColor = Color(0xFF0F766E)
    )
}

/**
 * Visual 5-step funds allocator and waterfall priority card.
 * Directs capital into:
 * 1. Emergency Reserve (instant-access safety buffer)
 * 2. Employer Pension Match (free 100% immediate match)
 * 3. DPS State Subsidy (20% to 40% cash match up to 1,700 CZK/mo)
 * 4. DIP Tax Shield (48,000 CZK/yr deduction per person)
 * 5. Liquid Brokerage / Global ETFs (unconstrained compounding & 3-year time-test exemption)
 */
@Composable
fun FundsAllocatorCard(
    state: FullCalculationState,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val reserveCurrent = s.emergencyReserveCurrent
    val reserveTarget = s.emergencyReserveTarget
    val isReserveFilled = reserveCurrent >= reserveTarget && reserveTarget > 0

    // Employer match
    val vEmp = min(s.employerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)
    val eEmp = if (!s.isSingleHousehold) min(s.eEmployerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0) else 0.0
    val totalEmpMonthly = vEmp + eEmp

    // DPS deposits & subsidy status
    val vDps = s.dpsOwnContributionMonthly
    val eDps = if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0
    val dpsSubsidyOptimalTier = RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026
    val isDpsMaxed = vDps >= dpsSubsidyOptimalTier && (s.isSingleHousehold || eDps >= dpsSubsidyOptimalTier)

    // DIP deposits & tax shield status
    val vDip = s.dipContributionMonthly
    val eDip = if (!s.isSingleHousehold) s.eDipContributionMonthly else 0.0
    val vDipDpsAbove = max(0.0, vDps - s.dpsDeductionThresholdMonthly)
    val eDipDpsAbove = max(0.0, eDps - s.dpsDeductionThresholdMonthly)
    val vRetirementDeductionMonthly = vDip + vDipDpsAbove
    val eRetirementDeductionMonthly = eDip + eDipDpsAbove
    val statutoryCeilingMonthly = RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026 / 12.0 // 4,000 CZK/mo
    val isDipMaxed = vRetirementDeductionMonthly >= statutoryCeilingMonthly && (s.isSingleHousehold || eRetirementDeductionMonthly >= statutoryCeilingMonthly)

    // Liquid Brokerage ETF DCA
    val vPortu = s.portuDcaMonthly
    val ePortu = if (!s.isSingleHousehold) s.ePortuDcaMonthly else 0.0
    val totalBrokerageMonthly = vPortu + ePortu

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("funds_allocator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            CardHeaderPill(
                title = "Funds Allocator & Priority Waterfall",
                subtitle = "Optimal capital flow: safety, subsidies, tax shields & ETFs",
                badgeText = "WATERFALL",
                accentColor = BrandTeal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: Emergency Reserve
            WaterfallStepRow(
                stepNumber = "1",
                title = "Emergency Reserve Buffer",
                currentAlloc = "${fmtCompact(reserveCurrent)} / ${fmtCompact(reserveTarget)}",
                statusText = if (isReserveFilled) "FILLED (${String.format(java.util.Locale.getDefault(), "%.1f", state.emergencyCoverageMonths)} mo)" else "BUILDING (${String.format(java.util.Locale.getDefault(), "%.1f", state.emergencyCoverageMonths)} mo)",
                statusColor = if (isReserveFilled) GoodGreen else BrandGold,
                recommendation = "Target 3–6 months essential living burn in high-yield account. Fund this first before locking capital.",
                info = WaterfallMetricInfos.emergencyReserve,
                onShowInfo = onShowInfo
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Step 2: Employer Pension Contribution
            WaterfallStepRow(
                stepNumber = "2",
                title = "Employer Pension Exemption",
                currentAlloc = "${fmtCZK(totalEmpMonthly)} / month",
                statusText = if (totalEmpMonthly > 0) "ACTIVE MATCH" else "NOT CLAIMED",
                statusColor = if (totalEmpMonthly > 0) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                recommendation = "Capture 100% employer match up to 50k Kč/yr per person. Tax-free and social levy-free.",
                info = WaterfallMetricInfos.employerMatch,
                onShowInfo = onShowInfo
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Step 3: DPS State Cash Subsidy
            WaterfallStepRow(
                stepNumber = "3",
                title = "State Pension Subsidy (DPS)",
                currentAlloc = "${fmtCZK(vDps + eDps)} / month",
                statusText = if (isDpsMaxed) "MAX SUBSIDY" else "OPTIMIZE TO 1,700",
                statusColor = if (isDpsMaxed) GoodGreen else BrandGold,
                recommendation = if (state.dps.youthSubsidyActive) {
                    "Deposit 1,700 Kč/mo for the 40% youth match (680 Kč/mo cash). Above 1,700 Kč, route savings to DIP for lower ETF fees."
                } else {
                    "Deposit 1,700 Kč/mo for the max 20% state match (340 Kč/mo). Above 1,700 Kč, route savings to DIP for lower ETF fees."
                },
                info = WaterfallMetricInfos.dpsSubsidy,
                onShowInfo = onShowInfo
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Step 4: DIP Tax Shield Deduction
            WaterfallStepRow(
                stepNumber = "4",
                title = "Retirement Tax Shield (DIP)",
                currentAlloc = "${fmtCZK(vRetirementDeductionMonthly + eRetirementDeductionMonthly)} / month",
                statusText = if (isDipMaxed) "MAX SHIELD (48k)" else "REFUND: ${fmtCompact(state.taxReturnHelper.dipSaving)}/yr",
                statusColor = if (isDipMaxed) GoodGreen else BrandTeal,
                recommendation = "Deduct up to 48k Kč/yr per person into global ETFs. Captures +7.2k to 11k refund with lower fees than DPS above 1.7k.",
                info = WaterfallMetricInfos.dipTaxShield,
                onShowInfo = onShowInfo
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Step 5: Liquid Brokerage / Global ETFs
            WaterfallStepRow(
                stepNumber = "5",
                title = "Liquid Index ETFs (Portu / XTB)",
                currentAlloc = "${fmtCZK(totalBrokerageMonthly)} / month",
                statusText = "TIME-TEST EXEMPT",
                statusColor = BrandTeal,
                recommendation = "Route all remaining surplus to diversified index ETFs. 100% tax-free after 3 years; fully accessible before age 60 for early retirement.",
                info = WaterfallMetricInfos.liquidBrokerage,
                onShowInfo = onShowInfo
            )
        }
    }
}

@Composable
private fun WaterfallStepRow(
    stepNumber: String,
    title: String,
    currentAlloc: String,
    statusText: String,
    statusColor: Color,
    recommendation: String,
    info: MetricInfo,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (onShowInfo != null) {
        Modifier.infoTapHold(info, onShowInfo)
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            ColorPill(
                text = statusText,
                color = statusColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 6.dp,
                verticalPadding = 2.dp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = currentAlloc,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * Streamlined DIP Scenario Optimization Matrix Card.
 */
@Composable
fun DipOptimizationMatrixCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val dip = state.dip
    val vDipMonthly = s.dipContributionMonthly
    val eDipMonthly = s.eDipContributionMonthly

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dip_optimization_matrix_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            CardHeaderPill(
                title = "DIP Deposit Optimization",
                subtitle = "Monthly tax shield tiers in global ETFs (lower fees than DPS)",
                badgeText = "SCENARIOS",
                accentColor = BrandTeal
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dip.scenarios.forEach { sc ->
                    val isVaclav = abs(sc.monthly - vDipMonthly) < 1.0
                    val isEleonora = !s.isSingleHousehold && abs(sc.monthly - eDipMonthly) < 1.0
                    val isBoth = isVaclav && isEleonora
                    val isHighlighted = isVaclav || isEleonora

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighlighted) BrandTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (isHighlighted) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = fmtCZK(sc.monthly),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                if (s.isSingleHousehold) {
                                    if (isVaclav) {
                                        ColorPill(text = "CURRENT", color = BrandTeal, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    }
                                } else {
                                    if (isBoth) {
                                        ColorPill(text = "V & E CURRENT", color = BrandTeal, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    } else {
                                        if (isVaclav) ColorPill(text = "VÁCLAV", color = BrandTeal, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                        if (isEleonora) ColorPill(text = "ELEONORA", color = BrandGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val monthlySaved = sc.annualTaxSaved / 12.0
                                Text(
                                    text = "+${fmtCZK(monthlySaved)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GoodGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                                if (sc.riskLevel == "MAX SHIELD" || sc.riskLevel == "STATUTORY MAX") {
                                    ColorPill(
                                        text = "MAX SHIELD",
                                        color = BrandTeal,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        horizontalPadding = 5.dp,
                                        verticalPadding = 1.5.dp
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

/**
 * Streamlined DPS Scenario Optimization Matrix Card.
 */
@Composable
fun DpsOptimizationMatrixCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val dps = state.dps
    val vDpsMonthly = s.dpsOwnContributionMonthly
    val eDpsMonthly = s.eDpsOwnContributionMonthly

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dps_optimization_matrix_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            CardHeaderPill(
                title = "DPS Deposit Optimization",
                subtitle = "Monthly state match (caps at 1,700 Kč; use DIP above)",
                badgeText = "SCENARIOS",
                accentColor = BrandGold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dps.scenarios.forEach { sc ->
                    val isVaclav = abs(sc.monthly - vDpsMonthly) < 1.0
                    val isEleonora = !s.isSingleHousehold && abs(sc.monthly - eDpsMonthly) < 1.0
                    val isBoth = isVaclav && isEleonora
                    val isHighlighted = isVaclav || isEleonora

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighlighted) BrandGold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (isHighlighted) BrandGold else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = fmtCZK(sc.monthly),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                if (s.isSingleHousehold) {
                                    if (isVaclav) {
                                        ColorPill(text = "CURRENT", color = BrandGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    }
                                } else {
                                    if (isBoth) {
                                        ColorPill(text = "V & E CURRENT", color = BrandGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    } else {
                                        if (isVaclav) ColorPill(text = "VÁCLAV", color = BrandTeal, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                        if (isEleonora) ColorPill(text = "ELEONORA", color = BrandGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, horizontalPadding = 5.dp, verticalPadding = 1.5.dp)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val monthlyBenefit = sc.totalAnnualBenefit / 12.0
                                Text(
                                    text = "+${fmtCZK(monthlyBenefit)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GoodGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                                sc.badgeLabel?.let { badge ->
                                    ColorPill(
                                        text = badge,
                                        color = if (badge == "MAX SUBSIDY" || badge == "SUBSIDY MAX" || badge == "DPS + DIP MAX" || badge == "OPTIMAL MAX") BrandGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        horizontalPadding = 5.dp,
                                        verticalPadding = 1.5.dp
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
