package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.DipOptimizationMatrixCard
import com.example.ui.components.DpsOptimizationMatrixCard
import com.example.ui.components.FundsAllocatorCard
import com.example.ui.components.KpiCard
import com.example.ui.components.MetricInfo
import com.example.ui.components.YtdTaxShieldTrackerCard
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PensionSubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val sections = listOf("Optimization", "Priority Waterfall", "Deposit Tiers")
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pension_subtab")
    ) {
        // Section Navigation Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEachIndexed { index, name ->
                val isSelected = selectedSection == index
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedSection = index
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("pension_section_$index")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> PensionOptimizationSection(state, onShowInfo)
            1 -> PensionWaterfallSection(state, onShowInfo)
            2 -> PensionDepositTiersSection(state)
        }
    }
}

@Composable
private fun PensionOptimizationSection(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val dps = state.dps
    val dip = state.dip
    val currentSubsidy = FinancialEngine.dpsSubsidy(s.dpsOwnContributionMonthly, s.primaryAge, s, s.baseYear)

    val vDipMonthly = s.dipContributionMonthly
    val eDipMonthly = s.eDipContributionMonthly

    val vAge = s.primaryAge
    val vSubsidy = FinancialEngine.dpsSubsidy(s.dpsOwnContributionMonthly, vAge, s, s.baseYear)
    val eAge = s.baseYear - ELEONORA_BIRTH_YEAR
    val eSubsidy = if (!s.isSingleHousehold) FinancialEngine.dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear) else 0.0

    val vHasIncome = FinancialEngine.vaclavSalaryMonthly(s.baseYear, s) > 0.0
    val eHasIncome = !s.isSingleHousehold && FinancialEngine.eleonoraSalaryMonthly(s.baseYear, s) > 0.0

    val vSubsidyMaxAnnual = if (vAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) {
        s.dpsYouthSubsidyMaxMonthly * 12.0
    } else {
        s.dpsStandardSubsidyMaxMonthly * 12.0
    }
    val eSubsidyMaxAnnual = if (!s.isSingleHousehold) {
        if (eAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) {
            s.dpsYouthSubsidyMaxMonthly * 12.0
        } else {
            s.dpsStandardSubsidyMaxMonthly * 12.0
        }
    } else 0.0

    val vOptimalDepositAnnual = s.dpsDeductionThresholdMonthly * 12.0 + (if (vHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    val eOptimalDepositAnnual = if (!s.isSingleHousehold) {
        s.dpsDeductionThresholdMonthly * 12.0 + (if (eHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    } else 0.0
    val totalOptimalDepositAnnual = vOptimalDepositAnnual + eOptimalDepositAnnual

    val maxCombinedBenefit = (vSubsidyMaxAnnual + eSubsidyMaxAnnual) +
        ((if (vHasIncome) s.taxDeductionCeilingAnnual else 0.0) + (if (eHasIncome) s.taxDeductionCeilingAnnual else 0.0)) * (s.taxRatePct / 100.0)

    val yearlyTaxSaved = state.taxReturnHelper.totalIncrementalValue
    val totalMonthlyRetirement = vDipMonthly + (if (!s.isSingleHousehold) eDipMonthly else 0.0) + s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
    val vTotalRetirement = vDipMonthly + s.dpsOwnContributionMonthly
    val eTotalRetirement = if (!s.isSingleHousehold) eDipMonthly + s.eDpsOwnContributionMonthly else 0.0
    val totalSubsidyMonthly = currentSubsidy + (if (!s.isSingleHousehold) FinancialEngine.dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear) else 0.0)
    val dpsTotalDeposit = s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
    val dpsNetReturnPct = max(0.0, s.dpsGrossReturnPct - min(s.dpsAnnualFeePct, s.dpsStatutoryFeeCapPct))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                KpiCard(
                    title = "Annual Tax Refund",
                    value = "+${fmtCZK(yearlyTaxSaved)}",
                    hint = "${String.format(java.util.Locale.getDefault(), "%.0f", s.taxRatePct)}% relief via tax return",
                    accentColor = GoodGreen,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dipDeduction,
                    onShowInfo = onShowInfo
                )
                KpiCard(
                    title = "Monthly Deposit",
                    value = fmtCZK(totalMonthlyRetirement),
                    hint = if (s.isSingleHousehold) {
                        "DIP: ${fmtCompact(vDipMonthly)} · DPS: ${fmtCompact(s.dpsOwnContributionMonthly)}"
                    } else {
                        "${if (s.primaryName.isNotBlank()) s.primaryName else "Primary"}: ${fmtCompact(vTotalRetirement)} · ${if (s.spouseName.isNotBlank()) s.spouseName else "Partner"}: ${fmtCompact(eTotalRetirement)}"
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
                    hint = "${fmtCZK(totalSubsidyMonthly)} monthly · ${if (dps.youthSubsidyActive) "40% youth match" else "20% state match"}",
                    accentColor = BrandGold,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dpsLepsiPenzijko,
                    onShowInfo = onShowInfo
                )
                KpiCard(
                    title = "DIP + DPS at Age 60",
                    value = fmtCompact(dip.dipBalanceAt60 + dps.dpsBalance),
                    hint = "DIP ${String.format(java.util.Locale.getDefault(), "%.1f", s.portfolioNominalReturnPct)}% · DPS ${String.format(java.util.Locale.getDefault(), "%.1f", dpsNetReturnPct)}% net",
                    accentColor = BrandBlue,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    info = PlanMetricInfos.dpsAge36,
                    onShowInfo = onShowInfo
                )
            }
        }

        // 2. Real YTD Tax Shield Tracker (48k statutory ceiling ZDP § 15 & § 15a)
        YtdTaxShieldTrackerCard(status = state.ytdTaxShieldStatus)

        // 3. Main Hero Card: DIP & DPS Two-Stage Statutory Optimization (Subsidy + Tax Shield)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .infoTapHold(PlanMetricInfos.dipDeduction, onShowInfo)
                .testTag("dip_summary_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeaderPill(
                    title = "Retirement Optimization",
                    subtitle = "DIP & DPS state match and tax shield",
                    icon = Icons.Default.AccountBalance,
                    badgeText = null,
                    accentColor = GoodGreen,
                    trailingContent = {
                        IconButton(
                            onClick = { onShowInfo(PlanMetricInfos.dipDeduction) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "DIP Details",
                                tint = GoodGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Earner Optimization Card
                EarnerOptimizationCard(
                    name = if (s.primaryName.isNotBlank()) s.primaryName else "Václav",
                    roleSubtitle = "Primary Earner",
                    dpsMonthly = s.dpsOwnContributionMonthly,
                    dipMonthly = vDipMonthly,
                    subsidyMonthly = vSubsidy,
                    subsidyCapMonthly = s.dpsDeductionThresholdMonthly,
                    taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                    taxRatePct = s.taxRatePct,
                    hasTaxableIncome = vHasIncome,
                    maxSubsidyAnnual = vSubsidyMaxAnnual,
                    accentColor = BrandTeal
                )

                // Partner Optimization Card — visible in dual-income mode
                if (!s.isSingleHousehold) {
                    Spacer(modifier = Modifier.height(12.dp))
                    EarnerOptimizationCard(
                        name = if (s.spouseName.isNotBlank()) s.spouseName else "Eleonora",
                        roleSubtitle = "Partner",
                        dpsMonthly = s.eDpsOwnContributionMonthly,
                        dipMonthly = eDipMonthly,
                        subsidyMonthly = eSubsidy,
                        subsidyCapMonthly = s.dpsDeductionThresholdMonthly,
                        taxShieldCapAnnual = s.taxDeductionCeilingAnnual,
                        taxRatePct = s.taxRatePct,
                        hasTaxableIncome = eHasIncome,
                        maxSubsidyAnnual = eSubsidyMaxAnnual,
                        accentColor = BrandGold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optimal Statutory Strategy Banner (Structured Blueprint)
                Surface(
                    shape = RoundedCornerShape(14.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    )
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

                        // 3 Key Metric Tiles: Optimal Deposit / Combined Return / Statutory ROI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "OPTIMAL MONTHLY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = fmtCompact(totalOptimalDepositAnnual / 12.0),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Text(
                                        text = "${fmtCompact(totalOptimalDepositAnnual)} annual",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "ANNUAL BENEFIT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = GoodGreen,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${fmtCompact(maxCombinedBenefit)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GoodGreen,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Text(
                                        text = "Cash & Tax relief",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }

                            val roiPct = if (totalOptimalDepositAnnual > 0.0) (maxCombinedBenefit / totalOptimalDepositAnnual) * 100.0 else 0.0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "DIRECT ROI",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = BrandTeal,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${String.format(java.util.Locale.getDefault(), "%.1f", roiPct)}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = BrandTeal,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Text(
                                        text = "Instant guaranteed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2 Structured Step Tiles
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BrandGold)
                                )
                                Text(
                                    text = "Step 1 (DPS): Deposit 1 700 Kč ${if (s.isSingleHousehold) "" else "per partner "}to lock full ${fmtCZK(if (s.isSingleHousehold) vSubsidyMaxAnnual else (vSubsidyMaxAnnual + eSubsidyMaxAnnual))} annual state cash match.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(GoodGreen)
                                )
                                val workingText = if (s.isSingleHousehold) "" else if (!eHasIncome) "for ${if (s.primaryName.isNotBlank()) s.primaryName else "Václav"} " else "per earner "
                                Text(
                                    text = "Step 2 (DIP): Deposit 4 000 Kč ${workingText}to maximize the 48 000 Kč personal tax deduction.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        if (!s.isSingleHousehold && !eHasIncome) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = GoodGreen.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Note: ${if (s.spouseName.isNotBlank()) s.spouseName else "Eleonora"}'s tax shield is paused during parental leave because personal tax deductions require taxable income and cannot be transferred. DPS state subsidies remain 100% active and claimable.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PensionWaterfallSection(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FundsAllocatorCard(state = state, onShowInfo = onShowInfo)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PensionDepositTiersSection(
    state: FullCalculationState
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DipOptimizationMatrixCard(state = state)
        DpsOptimizationMatrixCard(state = state)

        Spacer(modifier = Modifier.height(80.dp))
    }
}
