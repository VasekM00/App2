package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.components.CapitalGainsTimeTestCard
import com.example.ui.components.ColorPill
import com.example.ui.components.DipOptimizationMatrixCard
import com.example.ui.components.DpsOptimizationMatrixCard
import com.example.ui.components.FundsAllocatorCard
import com.example.ui.components.KpiCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.YtdTaxShieldTrackerCard
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.roundToInt

@Composable
fun PensionSubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val s = state.effectiveSettings
    val dps = state.dps
    val dip = state.dip
    val opt = state.pensionOptimization

    val vDipMonthly = s.dipContributionMonthly
    val eDipMonthly = s.eDipContributionMonthly
    val vAge = opt.primaryAge
    val eAge = opt.partnerAge
    val vSubsidy = opt.primarySubsidyMonthly
    val eSubsidy = opt.partnerSubsidyMonthly
    val vHasIncome = opt.primaryHasIncome
    val eHasIncome = opt.partnerHasIncome
    val vActualAnnualTaxSaved = opt.primaryAnnualTaxSaved
    val eActualAnnualTaxSaved = opt.partnerAnnualTaxSaved
    val vSubsidyMaxAnnual = opt.primarySubsidyMaxAnnual
    val eSubsidyMaxAnnual = opt.partnerSubsidyMaxAnnual
    val totalOptimalDepositAnnual = opt.totalOptimalDepositAnnual
    val maxCombinedBenefit = opt.maxCombinedBenefitAnnual
    val yearlyTaxSaved = state.taxReturnHelper.totalIncrementalValue
    val totalMonthlyRetirement = opt.totalMonthlyOwnRetirement
    val totalSubsidyMonthly = opt.totalSubsidyMonthly
    val dpsNetReturnPct = opt.dpsNetReturnPct

    var optimizationExpanded by rememberSaveable { mutableStateOf(false) }
    var waterfallExpanded by rememberSaveable { mutableStateOf(false) }
    var tiersExpanded by rememberSaveable { mutableStateOf(false) }
    var timeTestExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("pension_subtab"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. KPI Highlights Hero Grid (2x2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val hasFamilyCredits = !s.isSingleHousehold && state.taxReturnHelper.spouseCredit > 0.0
                KpiCard(
                    title = "Annual Tax Refund",
                    value = "+${fmtCZK(yearlyTaxSaved)}",
                    hint = if (hasFamilyCredits) "DIP/DPS + family tax credits" else "${String.format(java.util.Locale.ROOT, "%.0f", s.taxRatePct)}% relief via tax return",
                    accentColor = GoodGreen,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dipDeduction,
                    onShowInfo = onShowInfo
                )
                val totalDipMonthly = vDipMonthly + if (s.isSingleHousehold) 0.0 else eDipMonthly
                val dipSharePct = if (totalMonthlyRetirement > 0.0) {
                    ((totalDipMonthly / totalMonthlyRetirement) * 100.0).roundToInt().coerceIn(0, 100)
                } else 0
                KpiCard(
                    title = "Monthly Deposit",
                    value = fmtCZK(totalMonthlyRetirement),
                    hint = if (totalMonthlyRetirement > 0.0) {
                        "DIP $dipSharePct% · DPS ${100 - dipSharePct}% split"
                    } else {
                        "No active contributions"
                    },
                    accentColor = BrandTeal,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dipDeduction,
                    onShowInfo = onShowInfo
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "State Cash Match",
                    value = "+${fmtCZK(totalSubsidyMonthly * 12.0)}",
                    hint = "${fmtCZK(totalSubsidyMonthly)}/month · ${if (dps.youthSubsidyActive) "${s.dpsSubsidyRateYouthPct.toInt()}% youth" else "${s.dpsSubsidyRateStandardPct.toInt()}% match"}",
                    accentColor = BrandGold,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dpsLepsiPenzijko,
                    onShowInfo = onShowInfo
                )
                KpiCard(
                    title = "DIP + DPS at Age 60",
                    value = fmtCompact(dip.dipBalanceAt60 + dps.dpsBalance),
                    hint = "DIP ${com.example.util.Formatters.fmtPct(s.portfolioNominalReturnPct, 1)} · DPS ${com.example.util.Formatters.fmtPct(dpsNetReturnPct, 1)} net",
                    accentColor = BrandBlue,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dpsAge36,
                    onShowInfo = onShowInfo
                )
            }
        }

        // 2. Real YTD Tax Shield Tracker (48k annual ceiling) & Year-End Checklist
        YtdTaxShieldTrackerCard(
            status = state.ytdTaxShieldStatus,
            checklist = state.yearEndTaxChecklist
        )

        // 3. Accordion: Statutory Optimization Strategy
        PensionAccordionCard(
            title = "Retirement Optimization",
            subtitle = "DIP & DPS earner match, tax deduction & blueprint",
            expanded = optimizationExpanded,
            onToggle = { optimizationExpanded = !optimizationExpanded },
            tag = "dip_summary_card"
        ) {
            EarnerOptimizationCard(
                name = if (s.primaryName.isNotBlank()) s.primaryName else "Primary Earner",
                roleSubtitle = "Primary Earner",
                dpsMonthly = s.dpsOwnContributionMonthly,
                dipMonthly = vDipMonthly,
                subsidyMonthly = vSubsidy,
                subsidyCapMonthly = if (vAge >= s.vStatePensionAge) 0.0 else s.dpsDeductionThresholdMonthly,
                taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                taxRatePct = s.taxRatePct,
                hasTaxableIncome = vHasIncome,
                maxSubsidyAnnual = vSubsidyMaxAnnual,
                annualTaxSavedOverride = vActualAnnualTaxSaved,
                accentColor = BrandTeal
            )

            if (!s.isSingleHousehold) {
                Spacer(modifier = Modifier.height(12.dp))
                EarnerOptimizationCard(
                    name = if (s.spouseName.isNotBlank()) s.spouseName else "Partner",
                    roleSubtitle = "Partner",
                    dpsMonthly = s.eDpsOwnContributionMonthly,
                    dipMonthly = eDipMonthly,
                    subsidyMonthly = eSubsidy,
                    subsidyCapMonthly = if (eAge >= s.eStatePensionAge) 0.0 else s.dpsDeductionThresholdMonthly,
                    taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                    taxRatePct = s.taxRatePct,
                    hasTaxableIncome = eHasIncome,
                    maxSubsidyAnnual = eSubsidyMaxAnnual,
                    annualTaxSavedOverride = eActualAnnualTaxSaved,
                    accentColor = BrandGold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoodGreen.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GoodGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GoodGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Maximum Optimization Strategy",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoodGreen,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ColorPill(
                            text = "MAX RETURN",
                            color = GoodGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val roiPct = if (totalOptimalDepositAnnual > 0.0) (maxCombinedBenefit / totalOptimalDepositAnnual) * 100.0 else 0.0
                    Text(
                        text = "Optimal deposit ${fmtCompact(totalOptimalDepositAnnual / 12.0)}/mo · Annual statutory benefit +${fmtCompact(maxCombinedBenefit)} (+${String.format(java.util.Locale.ROOT, "%.1f", roiPct)}% direct ROI).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!s.isSingleHousehold && !eHasIncome) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: ${if (s.spouseName.isNotBlank()) s.spouseName else "Partner"}'s DIP/DPS tax deduction requires taxable income; DPS state subsidy remains active.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Accordion: Funds Allocation Waterfall
        PensionAccordionCard(
            title = "Contribution Waterfall",
            subtitle = "Priority allocation order for monthly inflows",
            expanded = waterfallExpanded,
            onToggle = { waterfallExpanded = !waterfallExpanded }
        ) {
            FundsAllocatorCard(state = state, onShowInfo = onShowInfo)
        }

        // 5. Accordion: DIP & DPS Deposit Tier Matrices
        PensionAccordionCard(
            title = "DIP & DPS Deposit Tiers",
            subtitle = "Statutory deduction & state subsidy tables",
            expanded = tiersExpanded,
            onToggle = { tiersExpanded = !tiersExpanded }
        ) {
            DipOptimizationMatrixCard(state = state)
            Spacer(modifier = Modifier.height(12.dp))
            DpsOptimizationMatrixCard(state = state)
        }

        // 6. Accordion: ETF 3-Year Capital Gains Time Test (moved from Home)
        PensionAccordionCard(
            title = "ETF 3-Year Capital Gains Test",
            subtitle = "Track ZDP § 4(1)(w) tax-exempt holding lots",
            expanded = timeTestExpanded,
            onToggle = { timeTestExpanded = !timeTestExpanded },
            tag = "accordion_capital_gains_time_test"
        ) {
            CapitalGainsTimeTestCard(
                state = state,
                onShowInfo = onShowInfo
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PensionAccordionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}
