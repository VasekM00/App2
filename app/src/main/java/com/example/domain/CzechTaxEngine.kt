package com.example.domain

import com.example.data.SettingsEntity
import kotlin.math.max
import kotlin.math.min

/**
 * CzechTaxEngine handles Czech Republic tax, deduction, and subsidy calculations
 * under Act No. 586/1992 Coll. (ZDP) and Act No. 462/2023 Coll. (Konsolidační balíček).
 */
object CzechTaxEngine {

    /**
     * State Subsidy calculation with customizable threshold, rates, youth cutoff and caps.
     * Standard: 20% max 340 CZK; Youth < 30 y/o: capped at statutory maximum (340 CZK/mo).
     */
    fun dpsSubsidy(
        monthlyDeposit: Double,
        age: Int,
        settings: SettingsEntity? = null,
        year: Int = settings?.baseYear ?: 2026
    ): Double {
        val minDeposit = settings?.dpsMinDepositForSubsidy ?: RegulatoryConstants.LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY
        if (monthlyDeposit < minDeposit) return 0.0
        val youthAge = settings?.dpsYouthAgeLimit ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_AGE_LIMIT
        val stdRate = (settings?.dpsSubsidyRateStandardPct ?: RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_SUBSIDY_RATE_PCT) / 100.0
        val youthRate = (settings?.dpsSubsidyRateYouthPct ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT) / 100.0
        val isYouthEligible = (age < youthAge) && (year >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR)
        val rate = if (isYouthEligible) youthRate else stdRate
        val youthCap = settings?.dpsYouthSubsidyMaxMonthly ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY
        val standardCap = settings?.dpsStandardSubsidyMaxMonthly ?: RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY
        val maxSub = if (isYouthEligible) youthCap else standardCap
        return min(monthlyDeposit * rate, maxSub)
    }

    /**
     * Computes annual combined tax deduction for DIP and DPS under Act No. 586/1992 Coll. § 15a/15b.
     * Capped at statutory ceiling per earner (48,000 CZK).
     */
    fun annualRetirementDeduction(settings: SettingsEntity): Double {
        val vDipAnnual = settings.dipContributionMonthly * 12.0
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val vDeduction = min(vDipAnnual + vDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        val eDipAnnual = settings.eDipContributionMonthly * 12.0
        val eDpsAboveThreshold = max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val eDeduction = min(eDipAnnual + eDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        val eTotal = if (!settings.isSingleHousehold) eDeduction else 0.0
        return vDeduction + eTotal
    }

    fun singleEarnerRetirementTaxSaved(
        taxableGrossAnnual: Double,
        deductionAnnual: Double,
        thresholdHighBracket: Double,
        baseRate: Double,
        highRate: Double,
        basicTaxpayerCredit: Double
    ): Double {
        if (taxableGrossAnnual <= 0.0 || deductionAnnual <= 0.0) return 0.0

        val highIncomeBefore = max(0.0, taxableGrossAnnual - thresholdHighBracket)
        val baseIncomeBefore = taxableGrossAnnual - highIncomeBefore
        val grossTaxBefore = highIncomeBefore * highRate + baseIncomeBefore * baseRate
        val netTaxBefore = max(0.0, grossTaxBefore - basicTaxpayerCredit)

        val effectiveDeduction = min(deductionAnnual, taxableGrossAnnual)
        val taxableGrossAfter = taxableGrossAnnual - effectiveDeduction
        val highIncomeAfter = max(0.0, taxableGrossAfter - thresholdHighBracket)
        val baseIncomeAfter = taxableGrossAfter - highIncomeAfter
        val grossTaxAfter = highIncomeAfter * highRate + baseIncomeAfter * baseRate
        val netTaxAfter = max(0.0, grossTaxAfter - basicTaxpayerCredit)

        return max(0.0, netTaxBefore - netTaxAfter)
    }

    /**
     * Calculates tax refund/savings from statutory retirement deductions (DIP + qualifying DPS).
     */
    fun annualRetirementTaxSaved(settings: SettingsEntity, year: Int = settings.baseYear): Double {
        val vDeduction = min(
            settings.dipContributionMonthly * 12.0 + max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0,
            settings.taxDeductionCeilingAnnual
        )
        val vGross = netToGrossAnnual(
            netMonthly = FinancialEngine.vaclavSalaryMonthly(year, settings),
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
            baseTaxRatePct = settings.taxRatePct,
            highTaxRatePct = settings.taxRateSecondPct
        )
        val vSaved = singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGross,
            deductionAnnual = vDeduction,
            thresholdHighBracket = settings.taxSecondBracketThresholdAnnual,
            baseRate = settings.taxRatePct / 100.0,
            highRate = settings.taxRateSecondPct / 100.0,
            basicTaxpayerCredit = settings.taxpayerCreditAnnual
        )

        val eSaved = if (!settings.isSingleHousehold) {
            val eDeduction = min(
                settings.eDipContributionMonthly * 12.0 + max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0,
                settings.taxDeductionCeilingAnnual
            )
            val eSalary = FinancialEngine.eleonoraSalaryMonthly(year, settings)
            val eLec = FinancialEngine.eleonoraLecturingMonthly(year, settings)
            val eGross = netToGrossAnnual(
                netMonthly = eSalary + eLec,
                taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                baseTaxRatePct = settings.taxRatePct,
                highTaxRatePct = settings.taxRateSecondPct
            )
            singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = eGross,
                deductionAnnual = eDeduction,
                thresholdHighBracket = settings.taxSecondBracketThresholdAnnual,
                baseRate = settings.taxRatePct / 100.0,
                highRate = settings.taxRateSecondPct / 100.0,
                basicTaxpayerCredit = settings.taxpayerCreditAnnual
            )
        } else 0.0

        return vSaved + eSaved
    }

    /**
     * Translates net employment wage to statutory gross wage taking into account mandatory social (7.1%),
     * health (4.5%), progressive income tax brackets (15% / 23%), basic taxpayer credit, and statutory social cap.
     */
    fun netToGrossAnnual(
        netMonthly: Double,
        taxpayerCreditAnnual: Double,
        highBracketThresholdAnnual: Double = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
        baseTaxRatePct: Double = 15.0,
        highTaxRatePct: Double = 23.0
    ): Double {
        if (netMonthly <= 0.0) return 0.0
        val creditMonthly = taxpayerCreditAnnual / 12.0
        val thresholdMonthly = highBracketThresholdAnnual / 12.0
        val baseRate = (baseTaxRatePct / 100.0).coerceIn(0.01, 0.50)
        val highRate = (highTaxRatePct / 100.0).coerceIn(baseRate, 0.60)
        val socialHealthRate = 0.116 // 7.1% social + 4.5% health

        val netZeroTax = (creditMonthly / baseRate) * (1.0 - socialHealthRate)
        val factorBase = 1.0 - socialHealthRate - baseRate
        val netAtThreshold = thresholdMonthly * factorBase + creditMonthly

        val socialCapMonthly = thresholdMonthly * (48.0 / 36.0)
        val factorHigh = 1.0 - socialHealthRate - highRate
        val netAtSocialCap = netAtThreshold + (socialCapMonthly - thresholdMonthly) * factorHigh
        val factorAboveCap = 1.0 - 0.045 - highRate // social capped at 48x average wage

        val grossMonthly = when {
            netMonthly <= netZeroTax -> netMonthly / (1.0 - socialHealthRate)
            netMonthly <= netAtThreshold -> (netMonthly - creditMonthly) / factorBase
            netMonthly <= netAtSocialCap -> thresholdMonthly + (netMonthly - netAtThreshold) / factorHigh
            else -> socialCapMonthly + (netMonthly - netAtSocialCap) / factorAboveCap
        }
        return grossMonthly * 12.0
    }

    /**
     * Computes tax savings specifically for DIP contributions in the base year.
     */
    fun dipTaxSavingYear(settings: SettingsEntity): Double {
        val threshold = settings.taxSecondBracketThresholdAnnual
        val baseRate = settings.taxRatePct / 100.0
        val highRate = settings.taxRateSecondPct / 100.0
        val credit = settings.taxpayerCreditAnnual

        // Václav saving (DIP + DPS qualifying combined)
        val vDpsAbove = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val vDip = settings.dipContributionMonthly * 12.0
        val vDeduction = min(vDip + vDpsAbove, settings.taxDeductionCeilingAnnual)
        val vTaxableBase = netToGrossAnnual(FinancialEngine.vaclavSalaryMonthly(settings.baseYear, settings), credit, threshold, settings.taxRatePct, settings.taxRateSecondPct)
        val vSaving = singleEarnerRetirementTaxSaved(vTaxableBase, vDeduction, threshold, baseRate, highRate, credit)

        // Eleonora saving (including lecturing if enabled, only when not single household)
        val eSaving = if (!settings.isSingleHousehold) {
            val eDpsAbove = max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
            val eDip = settings.eDipContributionMonthly * 12.0
            val eDeduction = min(eDip + eDpsAbove, settings.taxDeductionCeilingAnnual)
            val eEarnedMonthly = FinancialEngine.eleonoraSalaryMonthly(settings.baseYear, settings) +
                FinancialEngine.eleonoraLecturingMonthly(settings.baseYear, settings)
            val eTaxableBase = netToGrossAnnual(eEarnedMonthly, credit, threshold, settings.taxRatePct, settings.taxRateSecondPct)
            singleEarnerRetirementTaxSaved(eTaxableBase, eDeduction, threshold, baseRate, highRate, credit)
        } else 0.0

        return vSaving + eSaving
    }

    /**
     * Calculates spouse own gross income for the spouse tax credit qualification threshold (68,000 CZK)
     * under Act No. 586/1992 Coll. § 35ba(1)(b). Excludes state parental allowance.
     */
    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold) return 0.0
        val sal = FinancialEngine.eleonoraSalaryMonthly(year, settings)
        val lec = FinancialEngine.eleonoraLecturingMonthly(year, settings)
        val oth = settings.eOtherInflowsMonthly
        val earnedMonthly = sal + lec
        val grossEarnedAnnual = if (earnedMonthly > 0.0) {
            netToGrossAnnual(
                netMonthly = earnedMonthly,
                taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                baseTaxRatePct = settings.taxRatePct,
                highTaxRatePct = settings.taxRateSecondPct
            )
        } else 0.0
        return grossEarnedAnnual + (oth * 12.0)
    }
}
