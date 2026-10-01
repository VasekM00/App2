package com.example.domain

import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.data.SettingsEntity
import kotlin.math.abs
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
     * Lepší penzijko Reform Projection: Capped 0.5% TER & One-Third Partial Withdrawal at Age 36.
     */
    fun buildDpsProjection(settings: SettingsEntity): DpsProjection {
        val years = max(0, 60 - settings.primaryAge)
        val fee = min(settings.dpsAnnualFeePct, settings.dpsStatutoryFeeCapPct) // Statutory fee cap
        val annualRateDPS = max(-0.99, (settings.dpsGrossReturnPct - fee) / 100.0)
        val monthlyRateDPS = (1.0 + annualRateDPS).pow(1.0 / 12.0) - 1.0

        val annualRateETF = max(-0.99, settings.portfolioNominalReturnPct / 100.0)
        val monthlyRateETF = (1.0 + annualRateETF).pow(1.0 / 12.0) - 1.0

        val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0
        val eDpsBal = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly else 0.0

        val own = settings.dpsOwnContributionMonthly + eDpsOwn
        val emp = settings.employerRetirementMonthly + eEmp

        var dpsBal = settings.dpsBalanceCurrent + eDpsBal
        var etfBal = settings.dpsBalanceCurrent + eDpsBal
        var totalSubsidy = 0.0
        var totalOwn = 0.0
        var totalEmp = 0.0

        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            val currentYear = settings.baseYear + (m / 12)
            val currentAge = settings.primaryAge + (m / 12)
            val subV = CzechTaxEngine.dpsSubsidy(settings.dpsOwnContributionMonthly, currentAge, settings, currentYear)
            val subE = if (!settings.isSingleHousehold) CzechTaxEngine.dpsSubsidy(settings.eDpsOwnContributionMonthly, currentAge, settings, currentYear) else 0.0
            val sub = subV + subE

            totalSubsidy += sub
            totalOwn += own
            totalEmp += emp

            dpsBal = max(0.0, (dpsBal + own + sub + emp) * max(0.0, 1.0 + monthlyRateDPS))
            etfBal = max(0.0, (etfBal + own + emp) * max(0.0, 1.0 + monthlyRateETF))
        }

        // Statutory one-third early withdrawal under Lepší penzijko:
        // Evaluated per individual contract based on individual age and 10-year saving requirement
        val vAge0 = settings.primaryAge
        val eAge0 = settings.baseYear - ELEONORA_BIRTH_YEAR
        val vMonthsTo36 = max(0, (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - vAge0) * 12)
        val eMonthsTo36 = max(0, (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - eAge0) * 12)

        var balAt36 = settings.dpsBalanceCurrent + eDpsBal
        var vOwnValueTo36 = 0.0
        var eOwnValueTo36 = 0.0

        val maxMonthsTo36 = max(vMonthsTo36, if (!settings.isSingleHousehold) eMonthsTo36 else 0)
        if (maxMonthsTo36 in 1..totalMonths) {
            for (m in 0 until maxMonthsTo36) {
                val currentYear = settings.baseYear + (m / 12)
                val vAge = vAge0 + (m / 12)
                val eAge = eAge0 + (m / 12)
                val subV = CzechTaxEngine.dpsSubsidy(settings.dpsOwnContributionMonthly, vAge, settings, currentYear)
                val subE = if (!settings.isSingleHousehold) CzechTaxEngine.dpsSubsidy(settings.eDpsOwnContributionMonthly, eAge, settings, currentYear) else 0.0

                if (m < vMonthsTo36) {
                    vOwnValueTo36 = max(0.0, (vOwnValueTo36 + settings.dpsOwnContributionMonthly) * max(0.0, 1.0 + monthlyRateDPS))
                }
                if (!settings.isSingleHousehold && m < eMonthsTo36) {
                    eOwnValueTo36 = max(0.0, (eOwnValueTo36 + settings.eDpsOwnContributionMonthly) * max(0.0, 1.0 + monthlyRateDPS))
                }
                if (m < vMonthsTo36) {
                    balAt36 = max(0.0, (balAt36 + own + subV + subE + emp) * max(0.0, 1.0 + monthlyRateDPS))
                }
            }
        }

        val vTenYearMet = vAge0 <= RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - 10
        val eTenYearMet = !settings.isSingleHousehold && (eAge0 <= RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - 10)
        val vEarlyLimit = if (vTenYearMet) vOwnValueTo36 * (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT / 100.0) else 0.0
        val eEarlyLimit = if (eTenYearMet) eOwnValueTo36 * (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT / 100.0) else 0.0
        val earlyWithdrawalLimitAt36 = vEarlyLimit + eEarlyLimit

        val baseDpsLevels = listOf(0.0, 500.0, 1000.0, 1500.0, 1700.0, 5700.0)
        val candidateDps = listOf(settings.dpsOwnContributionMonthly, if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0)
        val dpsLevels = (baseDpsLevels + candidateDps)
            .filter { it >= 0.0 }
            .distinct()
            .sorted()

        val dipUtilizedAnnual = min(settings.dipContributionMonthly * 12.0, settings.taxDeductionCeilingAnnual)
        val remainingTaxHeadroom = max(0.0, settings.taxDeductionCeilingAnnual - dipUtilizedAnnual)

        val scenarios = dpsLevels.map { monthly: Double ->
            val subMonthly = CzechTaxEngine.dpsSubsidy(monthly, settings.primaryAge, settings, settings.baseYear)
            val subAnnual = subMonthly * 12.0
            val dpsAboveThreshold = max(0.0, monthly - settings.dpsDeductionThresholdMonthly)
            val dpsDeductionBase = dpsAboveThreshold * 12.0
            val effectiveDpsDeduction = if (abs(monthly - 5700.0) < 1.0) {
                // Combined maximum tier: 1 700 DPS max subsidy + 4 000 DIP max tax shield
                settings.taxDeductionCeilingAnnual
            } else {
                min(dpsDeductionBase, remainingTaxHeadroom)
            }
            val vTaxableGross = CzechTaxEngine.netToGrossAnnual(
                FinancialEngine.vaclavSalaryMonthly(settings.baseYear, settings),
                settings.taxpayerCreditAnnual,
                settings.taxSecondBracketThresholdAnnual
            )
            val taxSavedAnnual = CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = vTaxableGross,
                deductionAnnual = effectiveDpsDeduction,
                thresholdHighBracket = settings.taxSecondBracketThresholdAnnual,
                baseRate = settings.taxRatePct / 100.0,
                highRate = settings.taxRateSecondPct / 100.0,
                basicTaxpayerCredit = settings.taxpayerCreditAnnual
            )
            val totalBenefit = subAnnual + taxSavedAnnual
            val badge = when {
                abs(monthly - 5700.0) < 1.0 -> "DPS + DIP MAX"
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
            dpsBalance = dpsBal,
            etfBalance = etfBal,
            margin = dpsBal - etfBal,
            balanceAt36 = balAt36,
            earlyWithdrawalLimitAt36 = earlyWithdrawalLimitAt36,
            youthSubsidyActive = (settings.primaryAge < settings.dpsYouthAgeLimit) && (settings.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR),
            scenarios = scenarios
        )
    }

    /**
     * Projections and optimization matrix for DIP.
     */
    fun buildDipProjection(settings: SettingsEntity): DipProjection {
        val years = max(0, 60 - settings.primaryAge)
        val tsYear = CzechTaxEngine.dipTaxSavingYear(settings)
        val vDipMonthly = settings.dipContributionMonthly
        val eDipMonthly = if (!settings.isSingleHousehold) settings.eDipContributionMonthly else 0.0
        val totalMonthlyDip = vDipMonthly + eDipMonthly
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0

        val baseDipLevels = listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0)
        val candidateDipLevels = if (!settings.isSingleHousehold) {
            listOf(settings.dipContributionMonthly, settings.eDipContributionMonthly)
        } else {
            listOf(settings.dipContributionMonthly)
        }
        val dipLevels = (baseDipLevels + candidateDipLevels)
            .filter { it >= 0.0 }
            .distinct()
            .sorted()

        val scenarios = dipLevels.map { monthly: Double ->
            val scenarioSettings = settings.copy(
                dipContributionMonthly = monthly,
                dpsOwnContributionMonthly = 0.0,
                eDipContributionMonthly = 0.0,
                eDpsOwnContributionMonthly = 0.0
            )
            val asave = CzechTaxEngine.dipTaxSavingYear(scenarioSettings)
            val dipAnnual = monthly * 12.0

            val risk = when {
                monthly >= 4000.0 -> "MAX SHIELD"
                else -> ""
            }
            DipScenario(
                monthly = monthly,
                annual = monthly * 12.0,
                annualTaxSaved = asave,
                netCostMonthly = monthly - asave / 12.0,
                headroom = max(0.0, settings.taxDeductionCeilingAnnual - (dipAnnual + vDpsAboveThreshold)),
                riskLevel = risk
            )
        }

        val annualRateDIP = max(-0.99, settings.portfolioNominalReturnPct / 100.0)
        val monthlyRate = (1.0 + annualRateDIP).pow(1.0 / 12.0) - 1.0
        val eDipBal = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0
        var dipBal = settings.dipBalanceCurrent + eDipBal
        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            dipBal = max(0.0, (dipBal + totalMonthlyDip) * max(0.0, 1.0 + monthlyRate))
        }

        val totalCeiling = if (settings.isSingleHousehold) settings.taxDeductionCeilingAnnual else settings.taxDeductionCeilingAnnual * 2.0
        val totalUtilized = CzechTaxEngine.annualRetirementDeduction(settings)

        return DipProjection(
            taxSavedYear = tsYear,
            netCostMonthly = totalMonthlyDip - tsYear / 12.0,
            scenarios = scenarios,
            headroom = max(0.0, totalCeiling - totalUtilized),
            dipBalanceAt60 = dipBal
        )
    }
}
