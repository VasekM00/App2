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
     * Standard: 20% max 340 Kč (deposits 500..1 700 Kč/mo).
     * Per Act No. 462/2023 Coll. (eff. July 1, 2024), old-age pension recipients (age >= statePensionAge)
     * receive 0 Kč state subsidy.
     */
    fun dpsSubsidy(
        monthlyDeposit: Double,
        age: Int,
        settings: SettingsEntity? = null,
        year: Int = settings?.baseYear ?: 2026,
        statePensionAge: Int = settings?.vStatePensionAge ?: Int.MAX_VALUE
    ): Double {
        if (age >= statePensionAge) return 0.0
        val minDeposit = (settings?.dpsMinDepositForSubsidy
            ?: RegulatoryConstants.LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY)
            .finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY)
        if (!monthlyDeposit.isFinite() || monthlyDeposit <= 0.0 || monthlyDeposit < minDeposit) return 0.0
        val youthAge = settings?.dpsYouthAgeLimit ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_AGE_LIMIT
        val stdRate = ((settings?.dpsSubsidyRateStandardPct
            ?: RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_SUBSIDY_RATE_PCT)
            .finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_SUBSIDY_RATE_PCT)) / 100.0
        val youthRate = ((settings?.dpsSubsidyRateYouthPct
            ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT)
            .finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT)) / 100.0
        val isYouthEligible = (age < youthAge) && (year >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR)
        val rate = if (isYouthEligible) youthRate else stdRate
        val youthCap = (settings?.dpsYouthSubsidyMaxMonthly
            ?: RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY)
            .finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY)
        val standardCap = (settings?.dpsStandardSubsidyMaxMonthly
            ?: RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY)
            .finiteNonNegative(RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY)
        val maxSub = if (isYouthEligible) youthCap else standardCap
        return max(0.0, min(monthlyDeposit * rate, maxSub)).finiteNonNegative()
    }

    /**
     * Computes annual combined tax deduction for DIP and DPS under Act No. 586/1992 Coll. § 15a/15b.
     * Capped at statutory ceiling per earner (48,000 Kč).
     * Pensioners (age >= statePensionAge) receive no DPS subsidy so their DPS deduction starts from 1 Kč.
     */
    fun annualRetirementDeduction(settings: SettingsEntity, year: Int = settings.baseYear): Double {
        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)
        val baseThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val vAge = settings.primaryAge + (year - settings.baseYear)
        val vThreshold = if (vAge >= settings.vStatePensionAge) 0.0 else baseThreshold
        val vDipAnnual = settings.dipContributionMonthly.finiteNonNegative() * 12.0
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - vThreshold) * 12.0
        val vDeduction = min(vDipAnnual + vDpsAboveThreshold, ceiling)

        val eAge = year - FinancialEngine.partnerBirthYear(settings)
        val eThreshold = if (eAge >= settings.eStatePensionAge) 0.0 else baseThreshold
        val eDipAnnual = settings.eDipContributionMonthly.finiteNonNegative() * 12.0
        val eDpsAboveThreshold = max(0.0, settings.eDpsOwnContributionMonthly.finiteNonNegative() - eThreshold) * 12.0
        val eDeduction = min(eDipAnnual + eDpsAboveThreshold, ceiling)

        val eTotal = if (!settings.isSingleHousehold) eDeduction else 0.0
        return vDeduction + eTotal
    }

    fun singleEarnerRetirementTaxSaved(
        taxableGrossAnnual: Double,
        deductionAnnual: Double,
        thresholdHighBracket: Double,
        baseRate: Double,
        highRate: Double,
        basicTaxpayerCredit: Double,
        additionalCredits: Double = 0.0
    ): Double {
        if (!taxableGrossAnnual.isFinite() || taxableGrossAnnual <= 0.0 ||
            !deductionAnnual.isFinite() || deductionAnnual <= 0.0
        ) return 0.0

        val safeThreshold = thresholdHighBracket
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026)
        val safeBaseRate = baseRate.finiteIn(0.0, 1.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT / 100.0)
        val safeHighRate = highRate.finiteIn(safeBaseRate, 1.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT / 100.0)
        val safeCredit = basicTaxpayerCredit
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026) +
            additionalCredits.finiteNonNegative()

        val highIncomeBefore = max(0.0, taxableGrossAnnual - safeThreshold)
        val baseIncomeBefore = taxableGrossAnnual - highIncomeBefore
        val grossTaxBefore = highIncomeBefore * safeHighRate + baseIncomeBefore * safeBaseRate
        val netTaxBefore = max(0.0, grossTaxBefore - safeCredit)

        val effectiveDeduction = min(deductionAnnual, taxableGrossAnnual)
        val taxableGrossAfter = taxableGrossAnnual - effectiveDeduction
        val highIncomeAfter = max(0.0, taxableGrossAfter - safeThreshold)
        val baseIncomeAfter = taxableGrossAfter - highIncomeAfter
        val grossTaxAfter = highIncomeAfter * safeHighRate + baseIncomeAfter * safeBaseRate
        val netTaxAfter = max(0.0, grossTaxAfter - safeCredit)

        return max(0.0, netTaxBefore - netTaxAfter).finiteNonNegative()
    }

    fun hasChildUnder3(settings: SettingsEntity, year: Int = settings.baseYear): Boolean {
        if (!settings.hasChildUnder3) return false
        val c1Age = year - settings.child1BirthYear
        val c2Age = year - settings.child2BirthYear
        return (settings.child1Enabled && c1Age in 0..2) ||
            (settings.child2Enabled && c2Age in 0..2)
    }

    fun eligibleSpouseCreditAnnual(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        hasChildUnder3Override: Boolean? = null,
        spouseOwnIncomeOverride: Double? = null
    ): Double {
        if (settings.isSingleHousehold || !settings.includeSpouseCredit) return 0.0
        val childUnder3 = hasChildUnder3Override ?: hasChildUnder3(settings, year)
        if (!childUnder3) return 0.0
        val spouseIncome = (spouseOwnIncomeOverride ?: spouseOwnIncomeAnnual(year, settings)).finiteNonNegative()
        val spouseLimit = settings.spouseIncomeLimitAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026)
        return if (spouseIncome <= spouseLimit) {
            settings.spouseTaxCreditAnnual.finiteNonNegative(RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026)
        } else 0.0
    }

    /**
     * Calculates tax refund/savings from statutory retirement deductions (DIP + qualifying DPS).
     */
    fun annualRetirementTaxSaved(settings: SettingsEntity, year: Int = settings.baseYear): Double =
        annualRetirementTaxSavedForHousehold(settings, year, includeSpouse = !settings.isSingleHousehold)

    internal fun annualRetirementTaxSavedForHousehold(
        settings: SettingsEntity,
        year: Int,
        includeSpouse: Boolean,
        vGrossOverride: Double? = null
    ): Double {
        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)
        val baseThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val taxThreshold = settings.taxSecondBracketThresholdAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026)
        val baseRate = (settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)) / 100.0
        val highRate = (settings.taxRateSecondPct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)) / 100.0
        val credit = settings.taxpayerCreditAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026)

        val vAge = settings.primaryAge + (year - settings.baseYear)
        val vThreshold = if (vAge >= settings.vStatePensionAge) 0.0 else baseThreshold
        val vDeduction = min(
            settings.dipContributionMonthly.finiteNonNegative() * 12.0 +
                max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - vThreshold) * 12.0,
            ceiling
        )
        val vGross = (vGrossOverride ?: netToGrossAnnual(
            netMonthly = FinancialEngine.vaclavSalaryMonthly(year, settings),
            taxpayerCreditAnnual = credit,
            highBracketThresholdAnnual = taxThreshold,
            baseTaxRatePct = baseRate * 100.0,
            highTaxRatePct = highRate * 100.0
        )).finiteNonNegative()
        // Account for active non-refundable spouse credit so DIP/DPS deduction isn't overstated
        // when basic + spouse credits already absorb primary earner's income tax.
        val activeSpouseCredit = if (includeSpouse) {
            eligibleSpouseCreditAnnual(settings, year)
        } else 0.0

        val vSaved = singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGross,
            deductionAnnual = vDeduction,
            thresholdHighBracket = taxThreshold,
            baseRate = baseRate,
            highRate = highRate,
            basicTaxpayerCredit = credit,
            additionalCredits = activeSpouseCredit
        )

        val eSaved = if (includeSpouse) {
            val eAge = year - FinancialEngine.partnerBirthYear(settings)
            val eThreshold = if (eAge >= settings.eStatePensionAge) 0.0 else baseThreshold
            val eDeduction = min(
                settings.eDipContributionMonthly.finiteNonNegative() * 12.0 +
                    max(0.0, settings.eDpsOwnContributionMonthly.finiteNonNegative() - eThreshold) * 12.0,
                ceiling
            )
            val eGross = spouseEarnedGrossAnnual(year, settings)
            singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = eGross,
                deductionAnnual = eDeduction,
                thresholdHighBracket = taxThreshold,
                baseRate = baseRate,
                highRate = highRate,
                basicTaxpayerCredit = credit
            )
        } else 0.0

        return (vSaved + eSaved).finiteNonNegative()
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
        if (!netMonthly.isFinite() || netMonthly <= 0.0) return 0.0
        val creditMonthly = taxpayerCreditAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026) / 12.0
        val thresholdMonthly = highBracketThresholdAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026) / 12.0
        val baseRate = (baseTaxRatePct.finiteOr(15.0) / 100.0).coerceIn(0.01, 0.50)
        val highRate = (highTaxRatePct.finiteOr(23.0) / 100.0).coerceIn(baseRate, 0.60)
        val socialHealthRate = 0.116 // 7.1% social + 4.5% health
        val healthOnlyRate = 0.045

        val socialCapMonthly = thresholdMonthly * (48.0 / 36.0)
        val factorBase = 1.0 - socialHealthRate - baseRate
        val factorHigh = 1.0 - socialHealthRate - highRate
        val factorAboveCap = 1.0 - healthOnlyRate - highRate // social capped at 48x average wage

        val taxAtThreshold = thresholdMonthly * baseRate
        val grossMonthly = if (creditMonthly <= taxAtThreshold) {
            val netZeroTax = (creditMonthly / baseRate) * (1.0 - socialHealthRate)
            val netAtThreshold = thresholdMonthly * factorBase + creditMonthly
            val netAtSocialCap = netAtThreshold + (socialCapMonthly - thresholdMonthly) * factorHigh

            when {
                netMonthly <= netZeroTax -> netMonthly / (1.0 - socialHealthRate)
                netMonthly <= netAtThreshold -> (netMonthly - creditMonthly) / factorBase
                netMonthly <= netAtSocialCap -> thresholdMonthly + (netMonthly - netAtThreshold) / factorHigh
                else -> socialCapMonthly + (netMonthly - netAtSocialCap) / factorAboveCap
            }
        } else {
            // Custom low bracket threshold / high credit where tax remains zero beyond the high-bracket cutoff.
            val gTax0 = thresholdMonthly + (creditMonthly - taxAtThreshold) / highRate
            if (gTax0 <= socialCapMonthly) {
                val netAtTax0 = gTax0 * (1.0 - socialHealthRate)
                val netAtSocialCap = netAtTax0 + (socialCapMonthly - gTax0) * factorHigh
                when {
                    netMonthly <= netAtTax0 -> netMonthly / (1.0 - socialHealthRate)
                    netMonthly <= netAtSocialCap -> gTax0 + (netMonthly - netAtTax0) / factorHigh
                    else -> socialCapMonthly + (netMonthly - netAtSocialCap) / factorAboveCap
                }
            } else {
                val netAtSocialCap = socialCapMonthly * (1.0 - socialHealthRate)
                val netAtTax0 = netAtSocialCap + (gTax0 - socialCapMonthly) * (1.0 - healthOnlyRate)
                when {
                    netMonthly <= netAtSocialCap -> netMonthly / (1.0 - socialHealthRate)
                    netMonthly <= netAtTax0 -> socialCapMonthly + (netMonthly - netAtSocialCap) / (1.0 - healthOnlyRate)
                    else -> gTax0 + (netMonthly - netAtTax0) / factorAboveCap
                }
            }
        }
        return (grossMonthly * 12.0).finiteNonNegative()
    }

    /**
     * Computes tax savings specifically for DIP contributions in the base year.
     */
    fun dipTaxSavingYear(settings: SettingsEntity): Double {
        return annualRetirementTaxSaved(settings, settings.baseYear)
    }

    /**
     * Computes spouse earned gross income month-by-month in [year], separating pre-return
     * DPP lecturing months (0% SP/ZP strictly below 12,000 Kč/mo per § 7a Act No. 187/2006 Coll.)
     * from regular employment months.
     */
    fun spouseEarnedGrossAnnual(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold) return 0.0
        val dppMonthlyLimit = RegulatoryConstants.STATUTORY_DPP_INSURANCE_THRESHOLD_MONTHLY_2026
        val lecMonthly = settings.eLecturingMonthly.finiteNonNegative()
        val returnMonth = settings.eReturnMonth.coerceIn(1, 12)
        val fullEmploymentSalaryMonthly = when {
            year > settings.eReturnYear -> FinancialEngine.eleonoraSalaryMonthly(year, settings).finiteNonNegative()
            year == settings.eReturnYear -> settings.eStartingSalary.finiteNonNegative()
            else -> 0.0
        }

        var totalGross = 0.0
        for (month in 1..12) {
            val isEmployedMonth = year > settings.eReturnYear ||
                (year == settings.eReturnYear && month >= returnMonth)
            if (isEmployedMonth) {
                if (fullEmploymentSalaryMonthly > 0.0) {
                    totalGross += netToGrossAnnual(
                        netMonthly = fullEmploymentSalaryMonthly,
                        taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                        highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                        baseTaxRatePct = settings.taxRatePct,
                        highTaxRatePct = settings.taxRateSecondPct
                    ) / 12.0
                }
            } else if (year <= settings.eReturnYear && settings.eIncludeLecturing && lecMonthly > 0.0) {
                totalGross += if (lecMonthly < dppMonthlyLimit) {
                    // DPP strictly below 12,000 Kč/mo threshold: 0% SP/ZP, covered by basic credit
                    lecMonthly
                } else {
                    netToGrossAnnual(
                        netMonthly = lecMonthly,
                        taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                        highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                        baseTaxRatePct = settings.taxRatePct,
                        highTaxRatePct = settings.taxRateSecondPct
                    ) / 12.0
                }
            }
        }
        return totalGross.finiteNonNegative()
    }

    /**
     * Calculates spouse own gross income for the spouse tax credit qualification threshold (68,000 Kč)
     * under Act No. 586/1992 Coll. § 35ba(1)(b). Excludes state parental allowance and family gifts.
     */
    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold) return 0.0
        val grossEarnedAnnual = spouseEarnedGrossAnnual(year, settings)
        val oth = settings.eOtherInflowsMonthly.finiteNonNegative()
        return (grossEarnedAnnual + (oth * 12.0)).finiteNonNegative()
    }

    /**
     * Computes annual child tax relief under ZDP § 35c(1)-(4):
     * - Statutory entitlement by child count: 1st child, + 2nd child, + (count - 2) * 3rd+ child.
     * - ZDP § 35c(4) 6x minimum wage threshold gates ONLY the negative-tax cash bonus;
     *   positive income tax liability after basic & spouse credits is still reduced by the § 35c(1) child credit.
     */
    fun annualChildTaxRelief(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        vGrossAnnualOverride: Double? = null,
        eGrossAnnualOverride: Double? = null,
        vDeductionAnnualOverride: Double? = null,
        eDeductionAnnualOverride: Double? = null,
        vSpouseCreditAnnual: Double? = null,
        eligibleChildrenCountOverride: Int? = null
    ): Double {
        val c1Age = year - settings.child1BirthYear
        val c2Age = year - settings.child2BirthYear
        val computedCount = (if (settings.child1Enabled && c1Age in 0 until settings.childSupportUntilAge) 1 else 0) +
            (if (settings.child2Enabled && c2Age in 0 until settings.childSupportUntilAge) 1 else 0)
        val eligibleCount = (eligibleChildrenCountOverride ?: computedCount).coerceAtLeast(0)
        if (eligibleCount == 0) return 0.0

        var entitlement = 0.0
        if (eligibleCount >= 1) entitlement += settings.child1TaxBonusAnnual.finiteNonNegative()
        if (eligibleCount >= 2) entitlement += settings.child2TaxBonusAnnual.finiteNonNegative()
        if (eligibleCount >= 3) entitlement += (eligibleCount - 2) * settings.child3PlusTaxBonusAnnual.finiteNonNegative()
        if (entitlement <= 0.0) return 0.0

        val baseRate = (settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)) / 100.0
        val highRate = (settings.taxRateSecondPct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)) / 100.0
        val taxThreshold = settings.taxSecondBracketThresholdAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026)
        val basicCredit = settings.taxpayerCreditAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026)
        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)
        val baseThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val minEarnedIncome = settings.minWageMonthly.finiteNonNegative() *
            settings.childBonusMinWageMultiplier.finiteNonNegative()

        fun grossTax(taxableBase: Double): Double {
            if (taxableBase <= 0.0) return 0.0
            val highInc = max(0.0, taxableBase - taxThreshold)
            val baseInc = taxableBase - highInc
            return highInc * highRate + baseInc * baseRate
        }

        val vGross = (vGrossAnnualOverride ?: netToGrossAnnual(
            netMonthly = FinancialEngine.vaclavSalaryMonthly(year, settings),
            taxpayerCreditAnnual = basicCredit,
            highBracketThresholdAnnual = taxThreshold,
            baseTaxRatePct = baseRate * 100.0,
            highTaxRatePct = highRate * 100.0
        )).finiteNonNegative()
        val vAge = settings.primaryAge + (year - settings.baseYear)
        val vDpsThresh = if (vAge >= settings.vStatePensionAge) 0.0 else baseThreshold
        val vDed = (vDeductionAnnualOverride ?: min(
            settings.dipContributionMonthly.finiteNonNegative() * 12.0 +
                max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - vDpsThresh) * 12.0,
            ceiling
        )).finiteNonNegative()
        val vSpouseCredit = (vSpouseCreditAnnual ?: FinancialEngine.annualPartnerTaxCredit(settings, year, vGrossOverride = vGross)).finiteNonNegative()
        val vRemainingTax = max(0.0, grossTax(max(0.0, vGross - vDed)) - basicCredit - vSpouseCredit)
        val vRelief = if (vGross > 0.0) {
            if (vGross >= minEarnedIncome) entitlement else min(entitlement, vRemainingTax)
        } else 0.0

        val eRelief = if (!settings.isSingleHousehold) {
            val eGross = (eGrossAnnualOverride ?: spouseEarnedGrossAnnual(year, settings)).finiteNonNegative()
            val eAge = year - FinancialEngine.partnerBirthYear(settings)
            val eDpsThresh = if (eAge >= settings.eStatePensionAge) 0.0 else baseThreshold
            val eDed = (eDeductionAnnualOverride ?: min(
                settings.eDipContributionMonthly.finiteNonNegative() * 12.0 +
                    max(0.0, settings.eDpsOwnContributionMonthly.finiteNonNegative() - eDpsThresh) * 12.0,
                ceiling
            )).finiteNonNegative()
            val eRemainingTax = max(0.0, grossTax(max(0.0, eGross - eDed)) - basicCredit)
            if (eGross > 0.0) {
                if (eGross >= minEarnedIncome) entitlement else min(entitlement, eRemainingTax)
            } else 0.0
        } else 0.0

        return max(vRelief, eRelief).finiteNonNegative()
    }
}
