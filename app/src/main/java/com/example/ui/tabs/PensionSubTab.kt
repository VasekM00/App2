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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.CzechTaxEngine
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
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PensionSubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit = {}
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val sections = listOf("Optimization", "Waterfall", "Deposit Tiers")
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pension_subtab")
    ) {
        // Section Navigation Segmented Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sections.forEachIndexed { index, name ->
                    val isSelected = selectedSection == index
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedSection = index
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pension_section_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

    val vDipMonthly = s.dipContributionMonthly
    val eDipMonthly = s.eDipContributionMonthly

    val vAge = s.primaryAge
    val vSubsidy = CzechTaxEngine.dpsSubsidy(s.dpsOwnContributionMonthly, vAge, s, s.baseYear, statePensionAge = s.vStatePensionAge)
    val eAge = s.baseYear - FinancialEngine.partnerBirthYear(s)
    val eSubsidy = if (!s.isSingleHousehold) CzechTaxEngine.dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear, statePensionAge = s.eStatePensionAge) else 0.0

    val vSalaryMonthly = FinancialEngine.vaclavSalaryMonthly(s.baseYear, s)
    val eSalaryMonthly = if (!s.isSingleHousehold) FinancialEngine.eleonoraSalaryMonthly(s.baseYear, s) else 0.0
    val eLecturingMonthly = if (!s.isSingleHousehold) FinancialEngine.eleonoraLecturingMonthly(s.baseYear, s) else 0.0
    val vGrossAnnual = CzechTaxEngine.netToGrossAnnual(
        netMonthly = vSalaryMonthly,
        taxpayerCreditAnnual = s.taxpayerCreditAnnual,
        highBracketThresholdAnnual = s.taxSecondBracketThresholdAnnual,
        baseTaxRatePct = s.taxRatePct,
        highTaxRatePct = s.taxRateSecondPct
    )
    val eGrossAnnual = if (!s.isSingleHousehold) {
        CzechTaxEngine.spouseEarnedGrossAnnual(s.baseYear, s)
    } else 0.0
    val activeSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(s, s.baseYear)
    val vHasIncome = vSalaryMonthly > 0.0
    val eHasIncome = !s.isSingleHousehold && (eSalaryMonthly + eLecturingMonthly) > 0.0

    val vDpsEffectiveThreshold = if (vAge >= s.vStatePensionAge) 0.0 else s.dpsDeductionThresholdMonthly
    val vDeductionAnnual = min(
        s.taxDeductionCeilingAnnual,
        s.dipContributionMonthly * 12.0 + max(0.0, s.dpsOwnContributionMonthly - vDpsEffectiveThreshold) * 12.0
    )
    val vActualAnnualTaxSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
        taxableGrossAnnual = vGrossAnnual,
        deductionAnnual = vDeductionAnnual,
        thresholdHighBracket = s.taxSecondBracketThresholdAnnual,
        baseRate = s.taxRatePct / 100.0,
        highRate = s.taxRateSecondPct / 100.0,
        basicTaxpayerCredit = s.taxpayerCreditAnnual,
        additionalCredits = activeSpouseCredit
    )
    val eDpsEffectiveThreshold = if (eAge >= s.eStatePensionAge) 0.0 else s.dpsDeductionThresholdMonthly
    val eDeductionAnnual = if (!s.isSingleHousehold) {
        min(
            s.taxDeductionCeilingAnnual,
            s.eDipContributionMonthly * 12.0 + max(0.0, s.eDpsOwnContributionMonthly - eDpsEffectiveThreshold) * 12.0
        )
    } else 0.0
    val eActualAnnualTaxSaved = if (!s.isSingleHousehold) {
        CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = eGrossAnnual,
            deductionAnnual = eDeductionAnnual,
            thresholdHighBracket = s.taxSecondBracketThresholdAnnual,
            baseRate = s.taxRatePct / 100.0,
            highRate = s.taxRateSecondPct / 100.0,
            basicTaxpayerCredit = s.taxpayerCreditAnnual
        )
    } else 0.0

    val vSubsidyMaxAnnual = if (vAge >= s.vStatePensionAge) 0.0
        else if (vAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) s.dpsYouthSubsidyMaxMonthly * 12.0
        else s.dpsStandardSubsidyMaxMonthly * 12.0
    val eSubsidyMaxAnnual = if (!s.isSingleHousehold && eAge < s.eStatePensionAge) {
        if (eAge < s.dpsYouthAgeLimit && s.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR) {
            s.dpsYouthSubsidyMaxMonthly * 12.0
        } else {
            s.dpsStandardSubsidyMaxMonthly * 12.0
        }
    } else 0.0

    val vOptimalDepositAnnual = (if (vAge < s.vStatePensionAge) s.dpsDeductionThresholdMonthly * 12.0 else 0.0) + (if (vHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    val eOptimalDepositAnnual = if (!s.isSingleHousehold) {
        (if (eAge < s.eStatePensionAge) s.dpsDeductionThresholdMonthly * 12.0 else 0.0) + (if (eHasIncome) s.taxDeductionCeilingAnnual else 0.0)
    } else 0.0
    val totalOptimalDepositAnnual = vOptimalDepositAnnual + eOptimalDepositAnnual

    val maxCombinedBenefit = (vSubsidyMaxAnnual + eSubsidyMaxAnnual) +
        CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGrossAnnual,
            deductionAnnual = s.taxDeductionCeilingAnnual,
            thresholdHighBracket = s.taxSecondBracketThresholdAnnual,
            baseRate = s.taxRatePct / 100.0,
            highRate = s.taxRateSecondPct / 100.0,
            basicTaxpayerCredit = s.taxpayerCreditAnnual,
            additionalCredits = activeSpouseCredit
        ) +
        (if (!s.isSingleHousehold) CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = eGrossAnnual,
            deductionAnnual = s.taxDeductionCeilingAnnual,
            thresholdHighBracket = s.taxSecondBracketThresholdAnnual,
            baseRate = s.taxRatePct / 100.0,
            highRate = s.taxRateSecondPct / 100.0,
            basicTaxpayerCredit = s.taxpayerCreditAnnual
        ) else 0.0)

    val yearlyTaxSaved = state.taxReturnHelper.totalIncrementalValue
    val totalMonthlyRetirement = vDipMonthly + (if (!s.isSingleHousehold) eDipMonthly else 0.0) + s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
    val totalSubsidyMonthly = vSubsidy + eSubsidy
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
                val hasFamilyCredits = !s.isSingleHousehold && (state.taxReturnHelper.childBonus > 0.0 || state.taxReturnHelper.spouseCredit > 0.0)
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
                            modifier = Modifier.size(48.dp)
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

                // Partner Optimization Card — visible in dual-income mode
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

                // Optimal Statutory Strategy Banner (Structured Blueprint)
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

                        // 3 Key Metric Tiles: Optimal Deposit / Combined Return / Statutory ROI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "OPTIMAL / MO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = fmtCompact(totalOptimalDepositAnnual / 12.0),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = BodyFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${fmtCompact(totalOptimalDepositAnnual)}/year",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = BodyFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "ANNUAL GAIN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = GoodGreen,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${fmtCompact(maxCombinedBenefit)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = BodyFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = GoodGreen,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Cash & tax relief",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val roiPct = if (totalOptimalDepositAnnual > 0.0) (maxCombinedBenefit / totalOptimalDepositAnnual) * 100.0 else 0.0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, GoodGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "DIRECT RETURN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = BrandTeal,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${String.format(java.util.Locale.ROOT, "%.1f", roiPct)}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = BodyFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandTeal,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Guaranteed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                    text = "Step 1 (DPS): Deposit ${fmtCZK(s.dpsDeductionThresholdMonthly)}/month ${if (s.isSingleHousehold) "" else "per partner "}to lock full ${fmtCZK(if (s.isSingleHousehold) vSubsidyMaxAnnual else (vSubsidyMaxAnnual + eSubsidyMaxAnnual))} annual state cash match.",
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
                                val workingText = if (s.isSingleHousehold) "" else if (!eHasIncome) "for ${if (s.primaryName.isNotBlank()) s.primaryName else "Primary Earner"} " else "per earner "
                                Text(
                                    text = "Step 2 (DIP): Deposit ${fmtCZK(s.taxDeductionCeilingAnnual / 12.0)}/month ${workingText}to maximize the ${fmtCZK(s.taxDeductionCeilingAnnual)} personal tax deduction.",
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
                                text = "Note: ${if (s.spouseName.isNotBlank()) s.spouseName else "Partner"}'s tax shield is paused during parental leave because personal tax deductions require taxable income and cannot be transferred. DPS state subsidies remain 100% active and claimable.",
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
