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
     * Annual child tax credit (ZDP § 35c) already embedded in the primary earner's
     * base-year net monthly paycheck (`vSalary`) for enabled children born on or before `baseYear`.
     */
    fun baseYearChildCreditAnnual(settings: SettingsEntity): Double {
        if (settings.vSalary.finiteNonNegative() <= 0.0) return 0.0
        val baseYear = settings.baseYear
        val c1Age = baseYear - settings.child1BirthYear
        val c2Age = baseYear - settings.child2BirthYear
        val count = (if (settings.child1Enabled && c1Age in 0 until settings.childSupportUntilAge) 1 else 0) +
            (if (settings.child2Enabled && c2Age in 0 until settings.childSupportUntilAge) 1 else 0)
        var credit = 0.0
        if (count >= 1) credit += settings.child1TaxBonusAnnual.finiteNonNegative()
        if (count >= 2) credit += settings.child2TaxBonusAnnual.finiteNonNegative()
        return credit.finiteNonNegative()
    }

    /**
     * Infers the primary earner's annual gross taxable wage in [year], subtracting the
     * monthly child tax credit already embedded inside `vSalary` so gross income and
     * retirement deduction headroom are never inflated.
     */
    fun primaryGrossAnnual(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        netMonthlyOverride: Double? = null
    ): Double {
        val netMonthly = (netMonthlyOverride ?: FinancialEngine.vaclavSalaryMonthly(year, settings)).finiteNonNegative()
        if (netMonthly <= 0.0) return 0.0
        val embeddedMonthly = if (netMonthlyOverride == null) {
            baseYearChildCreditAnnual(settings) / 12.0
        } else 0.0
        return netToGrossAnnual(
            netMonthly = netMonthly,
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
            baseTaxRatePct = settings.taxRatePct,
            highTaxRatePct = settings.taxRateSecondPct,
            embeddedMonthlyChildCredit = embeddedMonthly
        )
    }

    /**
     * Computes annual combined tax deduction for DIP and DPS under Act No. 586/1992 Coll. § 15a/15b.
     * Capped at statutory ceiling per earner (48,000 Kč).
     * Pensioners (age >= statePensionAge) receive no DPS subsidy so their DPS deduction starts from 1 Kč.
     */
    fun annualRetirementDeduction(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        partnerBirthYearOverride: Int? = null
    ): Double {
        val ceiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026)
        val baseThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val vAge = settings.primaryAge + (year - settings.baseYear)
        val vThreshold = if (vAge >= settings.vStatePensionAge) 0.0 else baseThreshold
        val vDipAnnual = settings.dipContributionMonthly.finiteNonNegative() * 12.0
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - vThreshold) * 12.0
        val vDeduction = min(vDipAnnual + vDpsAboveThreshold, ceiling)

        val eAge = year - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride)
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

    /**
     * Counts qualifying calendar months (0..12) in [year] during which the household cares for at least
     * one dependent child under age 3 (ZDP § 35ba(1)(b), prorated at 1/12 = 2,070 Kč/month per § 35ba(3)).
     * Note: `settings.hasChildUnder3 == false` applies only to children already born at `baseYear` and
     * never blocks future-born children (`birthYear > baseYear`).
     */
    fun spouseCreditEligibleMonths(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        child1BirthMonth: Int = 1,
        child2BirthMonth: Int = 1
    ): Int {
        val eligibleMonths = BooleanArray(13) // 1..12

        fun markChildMonths(enabled: Boolean, birthYear: Int, birthMonth: Int) {
            if (!enabled) return
            if (birthYear <= settings.baseYear && !settings.hasChildUnder3) return
            val m = birthMonth.coerceIn(1, 12)
            val age = year - birthYear
            when (age) {
                0 -> for (mo in m..12) eligibleMonths[mo] = true
                1, 2 -> for (mo in 1..12) eligibleMonths[mo] = true
                3 -> for (mo in 1 until m) eligibleMonths[mo] = true
            }
        }

        markChildMonths(settings.child1Enabled, settings.child1BirthYear, child1BirthMonth)
        markChildMonths(settings.child2Enabled, settings.child2BirthYear, child2BirthMonth)
        return (1..12).count { eligibleMonths[it] }
    }

    fun hasChildUnder3(settings: SettingsEntity, year: Int = settings.baseYear): Boolean =
        spouseCreditEligibleMonths(settings, year) > 0

    fun eligibleSpouseCreditAnnual(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        hasChildUnder3Override: Boolean? = null,
        spouseOwnIncomeOverride: Double? = null,
        child1BirthMonth: Int = 1,
        child2BirthMonth: Int = 1,
        maternityBenefitPpmAnnual: Double = 0.0
    ): Double {
        if (settings.isSingleHousehold || !settings.includeSpouseCredit) return 0.0
        val months = when (hasChildUnder3Override) {
            false -> 0
            true -> {
                val computed = spouseCreditEligibleMonths(settings, year, child1BirthMonth, child2BirthMonth)
                if (computed > 0) computed else 12
            }
            null -> spouseCreditEligibleMonths(settings, year, child1BirthMonth, child2BirthMonth)
        }
        if (months <= 0) return 0.0
        val spouseIncome = (spouseOwnIncomeOverride
            ?: spouseOwnIncomeAnnual(year, settings, maternityBenefitPpmAnnual = maternityBenefitPpmAnnual))
            .finiteNonNegative()
        val spouseLimit = settings.spouseIncomeLimitAnnual
            .finiteNonNegative(RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026)
        return if (spouseIncome <= spouseLimit) {
            val fullAnnual = settings.spouseTaxCreditAnnual
                .finiteNonNegative(RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026)
            (fullAnnual * (months / 12.0)).finiteNonNegative()
        } else 0.0
    }

    /**
     * Calculates tax refund/savings from statutory retirement deductions (DIP + qualifying DPS).
     */
    fun annualRetirementTaxSaved(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        partnerBirthYearOverride: Int? = null
    ): Double = annualRetirementTaxSavedForHousehold(
        settings = settings,
        year = year,
        includeSpouse = !settings.isSingleHousehold,
        partnerBirthYearOverride = partnerBirthYearOverride
    )

    internal fun annualRetirementTaxSavedForHousehold(
        settings: SettingsEntity,
        year: Int,
        includeSpouse: Boolean,
        vGrossOverride: Double? = null,
        partnerBirthYearOverride: Int? = null
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
        val vGross = (vGrossOverride ?: primaryGrossAnnual(settings, year)).finiteNonNegative()
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
            val eAge = year - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride)
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
     * Optional [embeddedMonthlyChildCredit] strips monthly ZDP § 35c child bonus already paid in the net paycheck.
     */
    fun netToGrossAnnual(
        netMonthly: Double,
        taxpayerCreditAnnual: Double,
        highBracketThresholdAnnual: Double = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
        baseTaxRatePct: Double = 15.0,
        highTaxRatePct: Double = 23.0,
        embeddedMonthlyChildCredit: Double = 0.0
    ): Double {
        if (!netMonthly.isFinite() || netMonthly <= 0.0) return 0.0
        val netWithoutChildCredit = netMonthly - embeddedMonthlyChildCredit.finiteNonNegative()
        if (!netWithoutChildCredit.isFinite() || netWithoutChildCredit <= 0.0) return 0.0
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
                netWithoutChildCredit <= netZeroTax -> netWithoutChildCredit / (1.0 - socialHealthRate)
                netWithoutChildCredit <= netAtThreshold -> (netWithoutChildCredit - creditMonthly) / factorBase
                netWithoutChildCredit <= netAtSocialCap -> thresholdMonthly + (netWithoutChildCredit - netAtThreshold) / factorHigh
                else -> socialCapMonthly + (netWithoutChildCredit - netAtSocialCap) / factorAboveCap
            }
        } else {
            // Custom low bracket threshold / high credit where tax remains zero beyond the high-bracket cutoff.
            val gTax0 = thresholdMonthly + (creditMonthly - taxAtThreshold) / highRate
            if (gTax0 <= socialCapMonthly) {
                val netAtTax0 = gTax0 * (1.0 - socialHealthRate)
                val netAtSocialCap = netAtTax0 + (socialCapMonthly - gTax0) * factorHigh
                when {
                    netWithoutChildCredit <= netAtTax0 -> netWithoutChildCredit / (1.0 - socialHealthRate)
                    netWithoutChildCredit <= netAtSocialCap -> gTax0 + (netWithoutChildCredit - netAtTax0) / factorHigh
                    else -> socialCapMonthly + (netWithoutChildCredit - netAtSocialCap) / factorAboveCap
                }
            } else {
                val netAtSocialCap = socialCapMonthly * (1.0 - socialHealthRate)
                val netAtTax0 = netAtSocialCap + (gTax0 - socialCapMonthly) * (1.0 - healthOnlyRate)
                when {
                    netWithoutChildCredit <= netAtSocialCap -> netWithoutChildCredit / (1.0 - socialHealthRate)
                    netWithoutChildCredit <= netAtTax0 -> socialCapMonthly + (netWithoutChildCredit - netAtSocialCap) / (1.0 - healthOnlyRate)
                    else -> gTax0 + (netWithoutChildCredit - netAtTax0) / factorAboveCap
                }
            }
        }
        return (grossMonthly * 12.0).finiteNonNegative()
    }

    /**
     * Computes tax savings specifically for DIP contributions in the base year.
     */
    fun dipTaxSavingYear(
        settings: SettingsEntity,
        partnerBirthYearOverride: Int? = null
    ): Double {
        return annualRetirementTaxSaved(settings, settings.baseYear, partnerBirthYearOverride)
    }

    /**
     * Computes spouse earned gross income month-by-month in [year], separating pre-return
     * DPP lecturing months (grossed up for 15% withholding tax below 12,000 Kč/mo per ZDP § 36(2)(m)
     * when no taxpayer declaration is signed, or SP/ZP above threshold per § 7a Act No. 187/2006 Coll.)
     * from regular employment months.
     */
    fun spouseEarnedGrossAnnual(
        year: Int,
        settings: SettingsEntity,
        dppSignedTaxDeclaration: Boolean = false
    ): Double {
        if (settings.isSingleHousehold) return 0.0
        val dppMonthlyLimit = RegulatoryConstants.STATUTORY_DPP_INSURANCE_THRESHOLD_MONTHLY_2026
        val lecMonthly = settings.eLecturingMonthly.finiteNonNegative()
        val returnMonth = settings.eReturnMonth.coerceIn(1, 12)
        val baseRate = (settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)) / 100.0
        val srazkovaFactor = (1.0 - baseRate).coerceAtLeast(0.1)
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
                val grossedSrazkova = if (dppSignedTaxDeclaration) lecMonthly else (lecMonthly / srazkovaFactor)
                totalGross += if (grossedSrazkova < dppMonthlyLimit) {
                    grossedSrazkova
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
     * under Act No. 586/1992 Coll. § 35ba(1)(b). Includes maternity benefit (PPM) and earned/other income;
     * excludes state parental allowance (rodičovský příspěvek) and family gifts.
     */
    fun spouseOwnIncomeAnnual(
        year: Int,
        settings: SettingsEntity,
        maternityBenefitPpmAnnual: Double = 0.0,
        dppSignedTaxDeclaration: Boolean = false
    ): Double {
        if (settings.isSingleHousehold) return 0.0
        val grossEarnedAnnual = spouseEarnedGrossAnnual(year, settings, dppSignedTaxDeclaration)
        val oth = settings.eOtherInflowsMonthly.finiteNonNegative()
        val implicitPpm = if (maternityBenefitPpmAnnual <= 0.0 && year > settings.eReturnYear &&
            ((settings.child1Enabled && settings.child1BirthYear == year) ||
                (settings.child2Enabled && settings.child2BirthYear == year))
        ) {
            val priorGross = spouseEarnedGrossAnnual(year - 1, settings, dppSignedTaxDeclaration)
            (28.0 / 52.0) * 0.70 * priorGross
        } else 0.0
        val ppm = max(maternityBenefitPpmAnnual.finiteNonNegative(), implicitPpm)
        return (grossEarnedAnnual + (oth * 12.0) + ppm).finiteNonNegative()
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
        eligibleChildrenCountOverride: Int? = null,
        partnerBirthYearOverride: Int? = null
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

        val vGross = (vGrossAnnualOverride ?: primaryGrossAnnual(settings, year)).finiteNonNegative()
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
            val eAge = year - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride)
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
