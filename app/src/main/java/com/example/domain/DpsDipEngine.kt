package com.example.domain

import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * DpsDipEngine handles multi-decade projections and scenario optimization matrices
 * for DPS (Doplňkové penzijní spoření under Act No. 427/2011 Coll. and Lepší penzijko reform)
 * and DIP (Dlouhodobý investiční produkt under Act No. 586/1992 Coll. § 15a/15b).
 */
object DpsDipEngine {

    /**
     * Effective net annual DPS return after both management fee (capped at statutory 1.0%)
     * and dynamic/balanced DPS performance fee (10%-15% of positive annual returns under Act No. 427/2011 Coll. § 60(4)).
     */
    fun effectiveDpsNetReturnPct(settings: SettingsEntity): Double {
        val feeCap = settings.dpsStatutoryFeeCapPct.finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_STATUTORY_FEE_CAP_PCT)
        val fee = min(settings.dpsAnnualFeePct.finiteNonNegative(), feeCap)
        val gross = settings.dpsGrossReturnPct.finiteOr(0.0)
        val afterMgmt = gross - fee
        val perfFeePct = when {
            gross >= 5.5 -> RegulatoryConstants.STATUTORY_DPS_DYNAMIC_PERFORMANCE_FEE_PCT
            gross >= 4.0 -> RegulatoryConstants.STATUTORY_DPS_BALANCED_PERFORMANCE_FEE_PCT
            else -> 0.0
        }
        return if (afterMgmt > 0.0) {
            (afterMgmt * (1.0 - perfFeePct / 100.0)).finiteOr(0.0)
        } else {
            afterMgmt
        }
    }

    /**
     * Statutory penalty-free unlock age under ZDP § 15b (minimum age 60 AND 120 months / 10 years duration).
     * Contracts opened after age 50 remain locked past age 60 until 10 years have elapsed.
     */
    fun pensionUnlockAge(age0: Int, currentBalance: Double, annualInflow: Double): Int {
        val pastYears = if (currentBalance > 0.0) {
            min(
                RegulatoryConstants.STATUTORY_PENSION_MIN_LOCK_IN_YEARS,
                floor(currentBalance / max(6000.0, annualInflow)).toInt()
            )
        } else 0
        val remainingLockIn = max(0, RegulatoryConstants.STATUTORY_PENSION_MIN_LOCK_IN_YEARS - pastYears)
        return max(60, age0 + remainingLockIn)
    }

    /**
     * Lepší penzijko / DPS Projection: Management & performance fees, 120-month lock-in rule,
     * 15% lump-sum exit tax on gains + employer contributions, and age-36 1/3 withdrawal gating.
     */
    fun buildDpsProjection(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): DpsProjection {
        val annualRateDPS = max(-0.99, effectiveDpsNetReturnPct(settings).finiteOr(0.0) / 100.0)
        val monthlyRateDPS = (1.0 + annualRateDPS).pow(1.0 / 12.0) - 1.0

        val annualRateETF = max(-0.99, FinancialEngine.netTaxableNominalReturnPct(settings).finiteOr(0.0) / 100.0)
        val monthlyRateETF = (1.0 + annualRateETF).pow(1.0 / 12.0) - 1.0

        val vDpsOwn = settings.dpsOwnContributionMonthly.finiteNonNegative()
        val vDpsBal0 = settings.dpsBalanceCurrent.finiteNonNegative()
        val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly.finiteNonNegative() else 0.0
        val eDpsBal = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly.finiteNonNegative() else 0.0

        val own = vDpsOwn + eDpsOwn
        val empCap = settings.employerRetirementExemptionAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL) / 12.0
        val vHasDps = vDpsOwn > 0.0 || settings.dipContributionMonthly.finiteNonNegative() <= 0.0
        val eHasDps = eDpsOwn > 0.0 || settings.eDipContributionMonthly.finiteNonNegative() <= 0.0
        val vEmp = if (vHasDps) min(settings.employerRetirementMonthly.finiteNonNegative(), empCap) else 0.0
        val eEmpEffective = if (!settings.isSingleHousehold && eHasDps) min(eEmp, empCap) else 0.0
        val emp = vEmp + eEmpEffective

        val unlockAge = pensionUnlockAge(settings.primaryAge, vDpsBal0, (vDpsOwn + vEmp) * 12.0)
        val years = max(0, unlockAge - settings.primaryAge)

        var dpsBal = vDpsBal0 + eDpsBal
        var etfBal = vDpsBal0 + eDpsBal
        var totalSubsidy = 0.0
        var totalOwn = 0.0
        var totalEmp = 0.0

        val totalMonths = years * 12
        val baseRateFrac = (settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)) / 100.0
        // Employer pension match (ZDP § 6(9)(m)) is tax & payroll-levy exempt ONLY in DPS/DIP/ŽP.
        // If paid as regular gross salary to invest in a taxable ETF, employee nets (1 - 11.6% SP/ZP - baseTaxRate).
        val empNetEtfEquivalent = emp * (1.0 - 0.116 - baseRateFrac).coerceIn(0.0, 1.0)
        for (m in 0 until totalMonths) {
            val currentYear = settings.baseYear + (m / 12)
            val currentAge = settings.primaryAge + (m / 12)
            val eAgeCurrent = (currentYear - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride))
            val subV = CzechTaxEngine.dpsSubsidy(vDpsOwn, currentAge, settings, currentYear, settings.vStatePensionAge)
            val subE = if (!settings.isSingleHousehold) CzechTaxEngine.dpsSubsidy(eDpsOwn, eAgeCurrent, settings, currentYear, settings.eStatePensionAge) else 0.0
            val sub = subV + subE

            totalSubsidy += sub
            totalOwn += own
            totalEmp += emp

            dpsBal = max(0.0, (dpsBal + own + sub + emp) * max(0.0, 1.0 + monthlyRateDPS))
            etfBal = max(0.0, (etfBal + own + empNetEtfEquivalent) * max(0.0, 1.0 + monthlyRateETF))
        }
        val dpsGrossBal = dpsBal.finiteNonNegative()
        // ZDP § 8(1)(e)/(f): lump-sum DPS exit triggers 15% withholding tax on investment gains + employer contributions
        val taxableLumpBase = max(0.0, dpsGrossBal - (vDpsBal0 + eDpsBal) - totalOwn - totalSubsidy)
        val lumpSumExitTax = (taxableLumpBase * baseRateFrac).finiteNonNegative()
        val dpsNetBal = max(0.0, dpsGrossBal - lumpSumExitTax).finiteNonNegative()

        // Statutory one-third early withdrawal under Lepší penzijko (effective 2027+ only, strictly before age 36):
        val earlyAge = RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE
        val vAge0 = settings.primaryAge
        val eAge0 = settings.baseYear - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride)
        val vMonthsTo36 = max(0, (earlyAge - vAge0) * 12)
        val eMonthsTo36 = max(0, (earlyAge - eAge0) * 12)

        var vBalAt36 = vDpsBal0
        var eBalAt36 = eDpsBal
        var vOwnValueTo36 = vDpsBal0
        var eOwnValueTo36 = eDpsBal

        val maxMonthsTo36 = max(vMonthsTo36, if (!settings.isSingleHousehold) eMonthsTo36 else 0)
        if (maxMonthsTo36 in 1..totalMonths) {
            for (m in 0 until maxMonthsTo36) {
                val currentYear = settings.baseYear + (m / 12)
                val vAge = vAge0 + (m / 12)
                val eAge = eAge0 + (m / 12)
                val subV = CzechTaxEngine.dpsSubsidy(vDpsOwn, vAge, settings, currentYear, settings.vStatePensionAge)
                val subE = if (!settings.isSingleHousehold) CzechTaxEngine.dpsSubsidy(eDpsOwn, eAge, settings, currentYear, settings.eStatePensionAge) else 0.0

                if (m < vMonthsTo36) {
                    vOwnValueTo36 = max(0.0, (vOwnValueTo36 + vDpsOwn) * max(0.0, 1.0 + monthlyRateDPS))
                    vBalAt36 = max(0.0, (vBalAt36 + vDpsOwn + subV + vEmp) * max(0.0, 1.0 + monthlyRateDPS))
                }
                if (!settings.isSingleHousehold && m < eMonthsTo36) {
                    eOwnValueTo36 = max(0.0, (eOwnValueTo36 + eDpsOwn) * max(0.0, 1.0 + monthlyRateDPS))
                    eBalAt36 = max(0.0, (eBalAt36 + eDpsOwn + subE + eEmpEffective) * max(0.0, 1.0 + monthlyRateDPS))
                }
            }
        }
        val balAt36 = vBalAt36 + eBalAt36

        val reformActive = settings.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR
        val vPastYears = if (vDpsBal0 > 0.0) {
            min(10.0, vDpsBal0 / max(6000.0, (vDpsOwn + vEmp) * 12.0))
        } else 0.0
        val vTenYearMet = reformActive && vAge0 < earlyAge && (
            (vAge0 <= earlyAge - 10) || (vPastYears + (earlyAge - vAge0) >= 10.0)
        )

        val ePastYears = if (!settings.isSingleHousehold && eDpsBal > 0.0) {
            min(10.0, eDpsBal / max(6000.0, (eDpsOwn + eEmpEffective) * 12.0))
        } else 0.0
        val eTenYearMet = reformActive && !settings.isSingleHousehold && eAge0 < earlyAge && (
            (eAge0 <= earlyAge - 10) || (ePastYears + (earlyAge - eAge0) >= 10.0)
        )
        val vEarlyLimit = if (vTenYearMet) vOwnValueTo36 * (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT / 100.0) else 0.0
        val eEarlyLimit = if (eTenYearMet) eOwnValueTo36 * (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT / 100.0) else 0.0
        val earlyWithdrawalLimitAt36 = (vEarlyLimit + eEarlyLimit).finiteNonNegative()

        val baseDpsLevels = listOf(0.0, 500.0, 1000.0, 1500.0, 1700.0, 5700.0)
        val candidateDps = listOf(vDpsOwn, if (!settings.isSingleHousehold) eDpsOwn else 0.0)
        val dpsLevels = (baseDpsLevels + candidateDps)
            .filter { it.isFinite() && it >= 0.0 }
            .distinct()
            .sorted()

        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)
        val dpsThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val dipUtilizedAnnual = min(settings.dipContributionMonthly.finiteNonNegative() * 12.0, ceiling)
        val remainingTaxHeadroom = max(0.0, ceiling - dipUtilizedAnnual)
        val baseTaxRatePct = settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highTaxRatePct = settings.taxRateSecondPct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)
        val taxThreshold = settings.taxSecondBracketThresholdAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026)
        val taxpayerCredit = settings.taxpayerCreditAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026)

        val vTaxableGross = CzechTaxEngine.primaryGrossAnnual(settings, settings.baseYear)
        val activeSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(settings, settings.baseYear)
        val baselineDipTaxSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vTaxableGross,
            deductionAnnual = dipUtilizedAnnual,
            thresholdHighBracket = taxThreshold,
            baseRate = baseTaxRatePct / 100.0,
            highRate = highTaxRatePct / 100.0,
            basicTaxpayerCredit = taxpayerCredit,
            additionalCredits = activeSpouseCredit
        )

        val vAgeScenario = settings.primaryAge
        val effectiveScenarioThreshold = if (vAgeScenario >= settings.vStatePensionAge) 0.0 else dpsThreshold
        val scenarios = dpsLevels.map { monthly: Double ->
            val subMonthly = CzechTaxEngine.dpsSubsidy(monthly, vAgeScenario, settings, settings.baseYear, settings.vStatePensionAge)
            val subAnnual = subMonthly * 12.0
            val dpsAboveThreshold = max(0.0, monthly - effectiveScenarioThreshold)
            val dpsDeductionBase = dpsAboveThreshold * 12.0
            val effectiveDpsDeduction = min(dpsDeductionBase, remainingTaxHeadroom)
            val combinedSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = vTaxableGross,
                deductionAnnual = min(ceiling, dipUtilizedAnnual + effectiveDpsDeduction),
                thresholdHighBracket = taxThreshold,
                baseRate = baseTaxRatePct / 100.0,
                highRate = highTaxRatePct / 100.0,
                basicTaxpayerCredit = taxpayerCredit,
                additionalCredits = activeSpouseCredit
            )
            val taxSavedAnnual = max(0.0, combinedSaved - baselineDipTaxSaved)
            val totalBenefit = subAnnual + taxSavedAnnual
            val badge = when {
                abs(monthly - 5700.0) < 1.0 -> if (remainingTaxHeadroom > 0.0) "DPS + DIP MAX" else "ABOVE SUBSIDY CAP"
                abs(monthly - 1700.0) < 1.0 -> "MAX SUBSIDY"
                abs(monthly - 500.0) < 1.0 -> "MIN SUBSIDY"
                monthly > 1700.0 -> "ABOVE SUBSIDY CAP"
                else -> null
            }
            DpsScenario(
                monthly = monthly,
                annual = monthly * 12.0,
                monthlySubsidy = subMonthly,
                annualSubsidy = subAnnual,
                annualTaxSaved = taxSavedAnnual,
                totalAnnualBenefit = totalBenefit,
                badgeLabel = badge
            )
        }

        return DpsProjection(
            yearsTo60 = years,
            ownTotal = totalOwn,
            subsidyTotal = totalSubsidy,
            employerTotal = totalEmp,
            dpsBalance = dpsNetBal,
            etfBalance = etfBal.finiteNonNegative(),
            margin = (dpsNetBal - etfBal).finiteOr(0.0),
            balanceAt36 = balAt36.finiteNonNegative(),
            earlyWithdrawalLimitAt36 = earlyWithdrawalLimitAt36,
            youthSubsidyActive = (settings.primaryAge < settings.dpsYouthAgeLimit) && (settings.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR),
            scenarios = scenarios,
            dpsGrossBalance = dpsGrossBal,
            lumpSumExitTax = lumpSumExitTax,
            unlockAge = unlockAge
        )
    }

    /**
     * Projections and optimization matrix for DIP.
     */
    fun buildDipProjection(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): DipProjection {
        val tsCombined = CzechTaxEngine.dipTaxSavingYear(settings, partnerBirthYearOverride)
        val tsDpsOnly = CzechTaxEngine.dipTaxSavingYear(
            settings.copy(dipContributionMonthly = 0.0, eDipContributionMonthly = 0.0),
            partnerBirthYearOverride
        )
        val tsYear = max(0.0, tsCombined - tsDpsOnly)
        val vDipMonthly = settings.dipContributionMonthly.finiteNonNegative()
        val eDipMonthly = if (!settings.isSingleHousehold) settings.eDipContributionMonthly.finiteNonNegative() else 0.0
        val totalMonthlyDip = vDipMonthly + eDipMonthly
        val dpsThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val effectiveDpsThreshold = if (settings.primaryAge >= settings.vStatePensionAge) 0.0 else dpsThreshold
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - effectiveDpsThreshold) * 12.0
        val empCap = settings.employerRetirementExemptionAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL) / 12.0
        val vEmpToDip = if (settings.dpsOwnContributionMonthly.finiteNonNegative() <= 0.0 && vDipMonthly > 0.0) {
            min(settings.employerRetirementMonthly.finiteNonNegative(), empCap)
        } else 0.0
        val eEmpToDip = if (!settings.isSingleHousehold && settings.eDpsOwnContributionMonthly.finiteNonNegative() <= 0.0 && eDipMonthly > 0.0) {
            min(settings.eEmployerRetirementMonthly.finiteNonNegative(), empCap)
        } else 0.0
        val totalDipContributionWithMatchMonthly = totalMonthlyDip + vEmpToDip + eEmpToDip
        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)

        val baseDipLevels = listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0)
        val candidateDipLevels = if (!settings.isSingleHousehold) {
            listOf(vDipMonthly, eDipMonthly)
        } else {
            listOf(vDipMonthly)
        }
        val dipLevels = (baseDipLevels + candidateDipLevels)
            .filter { it.isFinite() && it >= 0.0 }
            .distinct()
            .sorted()

        val vDpsOnlyBaseline = CzechTaxEngine.dipTaxSavingYear(
            settings.copy(
                dipContributionMonthly = 0.0,
                eDipContributionMonthly = 0.0,
                eDpsOwnContributionMonthly = 0.0
            ),
            partnerBirthYearOverride
        )

        val scenarios = dipLevels.map { monthly: Double ->
            val scenarioSettings = settings.copy(
                dipContributionMonthly = monthly,
                eDipContributionMonthly = 0.0,
                eDpsOwnContributionMonthly = 0.0
            )
            val asave = max(0.0, CzechTaxEngine.dipTaxSavingYear(scenarioSettings, partnerBirthYearOverride) - vDpsOnlyBaseline)
            val dipAnnual = monthly * 12.0

            val risk = when {
                monthly >= 4000.0 -> "MAX SHIELD"
                else -> ""
            }
            DipScenario(
                monthly = monthly,
                annual = dipAnnual,
                annualTaxSaved = asave,
                netCostMonthly = max(0.0, monthly - asave / 12.0),
                headroom = max(0.0, ceiling - (dipAnnual + vDpsAboveThreshold)),
                riskLevel = risk
            )
        }

        val annualRateDIP = max(-0.99, FinancialEngine.netTaxableNominalReturnPct(settings).finiteOr(0.0) / 100.0)
        val monthlyRate = (1.0 + annualRateDIP).pow(1.0 / 12.0) - 1.0
        val vDipBal0 = settings.dipBalanceCurrent.finiteNonNegative()
        val eDipBal = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        var dipBal = vDipBal0 + eDipBal
        val unlockAge = pensionUnlockAge(settings.primaryAge, vDipBal0, (vDipMonthly + vEmpToDip) * 12.0)
        val years = max(0, unlockAge - settings.primaryAge)
        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            dipBal = max(0.0, (dipBal + totalDipContributionWithMatchMonthly) * max(0.0, 1.0 + monthlyRate))
        }

        val totalCeiling = if (settings.isSingleHousehold) ceiling else ceiling * 2.0
        val totalUtilized = CzechTaxEngine.annualRetirementDeduction(settings, settings.baseYear, partnerBirthYearOverride)

        return DipProjection(
            taxSavedYear = tsYear,
            netCostMonthly = max(0.0, totalMonthlyDip - tsYear / 12.0),
            scenarios = scenarios,
            headroom = max(0.0, totalCeiling - totalUtilized),
            dipBalanceAt60 = dipBal.finiteNonNegative(),
            unlockAge = unlockAge
        )
    }
}
