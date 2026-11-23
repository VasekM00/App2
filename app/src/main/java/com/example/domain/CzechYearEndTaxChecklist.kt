package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.SettingsEntity
import com.example.util.Formatters.fmtExactCZK
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * CzechYearEndTaxChecklist generates a smart, actionable year-end tax optimization checklist
 * before December 31st for Czech individuals and households under Act No. 586/1992 Coll. (ZDP).
 */
object CzechYearEndTaxChecklist {

    const val DIP_DPS_CEILING_ANNUAL = RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026 // 48 000 CZK
    const val EMPLOYER_MATCH_CEILING_ANNUAL = RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL // 50 000 CZK
    const val SPOUSE_INCOME_LIMIT_ANNUAL = RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026 // 68 000 CZK
    const val SPOUSE_TAX_CREDIT_ANNUAL = RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026 // 24 840 CZK
    const val MORTGAGE_INTEREST_CEILING_ANNUAL = RegulatoryConstants.STATUTORY_MORTGAGE_INTEREST_DEDUCTION_ANNUAL_CAP // 150 000 CZK

    /**
     * Generates a comprehensive checklist report tailored to the current settings and user data.
     */
    fun generateChecklist(
        settings: SettingsEntity,
        taxYear: Int = settings.baseYear,
        mortgageInterestPaidAnnual: Double? = null,
        spouseOwnIncomeAnnual: Double? = null,
        hasChildUnder3: Boolean? = null,
        actualDipContributedAnnual: Double? = null,
        actualDpsContributedAnnual: Double? = null,
        actualDpsQualifyingAnnual: Double? = null,
        partnerBirthYearOverride: Int? = null
    ): YearEndTaxChecklistReport {
        val items = mutableListOf<ChecklistItem>()
        var totalPotentialSavings = 0.0

        val retirementCeiling = settings.taxDeductionCeilingAnnual
            .finiteNonNegative(DIP_DPS_CEILING_ANNUAL)
        val dpsThreshold = settings.dpsDeductionThresholdMonthly
            .finiteNonNegative(RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026)
        val baseRatePct = settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highRatePct = settings.taxRateSecondPct.finiteIn(baseRatePct, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)
        val baseRate = baseRatePct / 100.0
        val highRate = highRatePct / 100.0
        val employerCeiling = settings.employerRetirementExemptionAnnual
            .finiteNonNegative(EMPLOYER_MATCH_CEILING_ANNUAL)
        val spouseIncomeLimit = settings.spouseIncomeLimitAnnual
            .finiteNonNegative(SPOUSE_INCOME_LIMIT_ANNUAL)
        val spouseCreditAmount = settings.spouseTaxCreditAnnual
            .finiteNonNegative(SPOUSE_TAX_CREDIT_ANNUAL)

        // 1. DIP & DPS Deduction Optimization (respects 0-tax floor and 23% progressive bracket)
        val vAge = settings.primaryAge + (taxYear - settings.baseYear)
        val effectiveDpsThreshold = if (vAge >= settings.vStatePensionAge) 0.0 else dpsThreshold
        val dipAnnual = actualDipContributedAnnual?.finiteNonNegative()
            ?: (settings.dipContributionMonthly.finiteNonNegative() * 12.0)
        val dpsAboveThresholdAnnual = actualDpsQualifyingAnnual?.finiteNonNegative()
            ?: actualDpsContributedAnnual?.let {
                max(0.0, it.finiteNonNegative() - effectiveDpsThreshold * 12.0)
            }
            ?: (max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - effectiveDpsThreshold) * 12.0)

        val taxThreshold = settings.taxSecondBracketThresholdAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        val basicCredit = settings.taxpayerCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026
        )
        val vGrossAnnual = CzechTaxEngine.primaryGrossAnnual(settings, taxYear)
        val resolvedChildUnder3 = hasChildUnder3 ?: CzechTaxEngine.hasChildUnder3(settings, taxYear)
        val activeSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(
            settings = settings,
            year = taxYear,
            hasChildUnder3Override = resolvedChildUnder3,
            spouseOwnIncomeOverride = spouseOwnIncomeAnnual
        )
        val vQualifyingTotal = dipAnnual + dpsAboveThresholdAnnual
        val vHeadroom = max(0.0, retirementCeiling - vQualifyingTotal)
        val vTaxSavedCurrent = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGrossAnnual,
            deductionAnnual = min(vQualifyingTotal, retirementCeiling),
            thresholdHighBracket = taxThreshold,
            baseRate = baseRate,
            highRate = highRate,
            basicTaxpayerCredit = basicCredit,
            additionalCredits = activeSpouseCredit
        )
        val vTaxSavedMax = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGrossAnnual,
            deductionAnnual = retirementCeiling,
            thresholdHighBracket = taxThreshold,
            baseRate = baseRate,
            highRate = highRate,
            basicTaxpayerCredit = basicCredit,
            additionalCredits = activeSpouseCredit
        )
        val vSavingEffective = max(0.0, vTaxSavedMax - vTaxSavedCurrent)
        val vSaving23 = vHeadroom * highRate

        val dipDpsStatus = when {
            vHeadroom <= 0.0 -> ChecklistStatus.FULLY_OPTIMIZED
            vTaxSavedMax <= 0.0 -> ChecklistStatus.NOT_APPLICABLE
            else -> ChecklistStatus.ACTION_REQUIRED
        }
        val maxSavingBase = retirementCeiling * baseRate
        val maxSavingHigh = retirementCeiling * highRate
        val dipDpsAdvice = when {
            vHeadroom <= 0.0 ->
                "The statutory ${fmtExactCZK(retirementCeiling)} retirement deduction cap for $taxYear is fully utilized. " +
                    "Annual tax saving of ${fmtExactCZK(vTaxSavedCurrent)} (max ${fmtExactCZK(maxSavingBase)} at ${baseRatePct.toInt()}% / ${fmtExactCZK(maxSavingHigh)} at ${highRatePct.toInt()}%) is secured."
            vTaxSavedMax <= 0.0 ->
                "Your taxable income in $taxYear is fully covered by the basic taxpayer credit, so personal DIP/DPS deductions yield 0\u00A0Kč additional tax refund."
            else ->
                "Before December 31, $taxYear, you can contribute up to ${fmtExactCZK(vHeadroom)} extra to DIP or DPS. " +
                    "Based on your taxable income, this yields ${fmtExactCZK(vSavingEffective)} in tax relief (up to ${fmtExactCZK(vSaving23)} in the ${highRatePct.toInt()}% bracket)."
        }

        items.add(
            ChecklistItem(
                id = "ret_dip_dps_deduction",
                title = "DIP & DPS Tax Deduction (${fmtExactCZK(retirementCeiling)} cap)",
                category = ChecklistCategory.RETIREMENT_DIP_DPS,
                status = dipDpsStatus,
                statutoryReference = "ZDP § 15a & § 15b",
                deadline = "December 31, $taxYear",
                currentValueCzk = min(vQualifyingTotal, retirementCeiling),
                ceilingOrThresholdCzk = retirementCeiling,
                remainingHeadroomCzk = if (vTaxSavedMax > 0.0) vHeadroom else 0.0,
                potentialTaxSavingCzk = vSavingEffective,
                actionDescription = when {
                    vHeadroom <= 0.0 -> "Annual cap fully utilized"
                    vTaxSavedMax <= 0.0 -> "Tax shield paused (income covered by taxpayer credit)"
                    else -> "Top up DIP/DPS deduction"
                },
                detailedAdvice = dipDpsAdvice
            )
        )
        totalPotentialSavings += vSavingEffective

        // Secondary Earner DIP/DPS in dual household
        if (!settings.isSingleHousehold) {
            val eAge = taxYear - FinancialEngine.partnerBirthYear(settings, partnerBirthYearOverride)
            val eEffectiveDpsThreshold = if (eAge >= settings.eStatePensionAge) 0.0 else dpsThreshold
            val eDipAnnual = settings.eDipContributionMonthly.finiteNonNegative() * 12.0
            val eDpsAboveThreshold = max(0.0, settings.eDpsOwnContributionMonthly.finiteNonNegative() - eEffectiveDpsThreshold) * 12.0
            val eQualifying = eDipAnnual + eDpsAboveThreshold
            val eHeadroom = max(0.0, retirementCeiling - eQualifying)
            val eGrossFromSettings = CzechTaxEngine.spouseEarnedGrossAnnual(taxYear, settings)
            val effectiveSpouseGross = max(eGrossFromSettings, spouseOwnIncomeAnnual?.finiteNonNegative() ?: 0.0)
            val currentSpouseTaxSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = effectiveSpouseGross,
                deductionAnnual = min(eQualifying, retirementCeiling),
                thresholdHighBracket = taxThreshold,
                baseRate = baseRate,
                highRate = highRate,
                basicTaxpayerCredit = basicCredit
            )
            val maxSpouseTaxSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = effectiveSpouseGross,
                deductionAnnual = retirementCeiling,
                thresholdHighBracket = taxThreshold,
                baseRate = baseRate,
                highRate = highRate,
                basicTaxpayerCredit = basicCredit
            )
            val eSaving15 = max(0.0, maxSpouseTaxSaved - currentSpouseTaxSaved)
            val hasTaxableSpouseLiability = maxSpouseTaxSaved > 0.0

            if (eHeadroom > 0.0) {
                items.add(
                    ChecklistItem(
                        id = "ret_dip_dps_partner",
                        title = "Spouse DIP & DPS Deduction (${settings.spouseName.ifBlank { "Partner" }})",
                        category = ChecklistCategory.RETIREMENT_DIP_DPS,
                        status = if (hasTaxableSpouseLiability) ChecklistStatus.ACTION_REQUIRED else ChecklistStatus.NOT_APPLICABLE,
                        statutoryReference = "ZDP § 15a & § 15b",
                        deadline = "December 31, $taxYear",
                        currentValueCzk = min(eQualifying, retirementCeiling),
                        ceilingOrThresholdCzk = retirementCeiling,
                        remainingHeadroomCzk = if (hasTaxableSpouseLiability) eHeadroom else 0.0,
                        potentialTaxSavingCzk = eSaving15,
                        actionDescription = if (hasTaxableSpouseLiability) {
                            "Top up partner DIP/DPS contribution"
                        } else {
                            "Tax shield paused (income covered by taxpayer credit)"
                        },
                        detailedAdvice = if (hasTaxableSpouseLiability) {
                            "Your partner can also deduct up to ${fmtExactCZK(retirementCeiling)} per year. " +
                                "Remaining deduction headroom is ${fmtExactCZK(eHeadroom)}, saving ${fmtExactCZK(eSaving15)} in tax."
                        } else {
                            "${settings.spouseName.ifBlank { "Partner" }}'s taxable income in $taxYear is fully covered by the basic taxpayer credit, so personal DIP/DPS tax deductions yield 0\u00A0Kč until taxable income rises (e.g. post-parental leave in ${settings.eReturnYear})."
                        }
                    )
                )
                totalPotentialSavings += eSaving15
            }
        }

        // 2. Employer Match Utilization
        val currentEmployerAnnual = settings.employerRetirementMonthly.finiteNonNegative() * 12.0
        val employerHeadroom = max(0.0, employerCeiling - currentEmployerAnnual)
        val empStatus = if (employerHeadroom <= 0.0) ChecklistStatus.FULLY_OPTIMIZED else ChecklistStatus.ATTENTION_NEEDED

        val primaryLabel = if (!settings.isSingleHousehold) " · ${settings.primaryName}" else ""
        items.add(
            ChecklistItem(
                id = "employer_contribution_exemption",
                title = "Tax-Exempt Employer Pension Benefit$primaryLabel (${fmtExactCZK(employerCeiling)}/year cap)",
                category = ChecklistCategory.EMPLOYER_BENEFIT,
                status = empStatus,
                statutoryReference = "ZDP § 6(9)(m)",
                deadline = "Year-end / compensation review",
                currentValueCzk = min(currentEmployerAnnual, employerCeiling),
                ceilingOrThresholdCzk = employerCeiling,
                remainingHeadroomCzk = employerHeadroom,
                potentialTaxSavingCzk = employerHeadroom * (baseRate + 0.116), // income tax + 11.6% employee social/health
                actionDescription = if (employerHeadroom > 0.0) "Request higher employer DPS/DIP match" else "Exempt limit fully utilized",
                detailedAdvice = if (employerHeadroom > 0.0) {
                    "Your employer can contribute up to ${fmtExactCZK(employerCeiling)} per year to DIP/DPS free of income tax and payroll contributions. " +
                        "Unused room is ${fmtExactCZK(employerHeadroom)}/year."
                } else {
                    "Employer retirement contribution is already optimized at the full ${fmtExactCZK(employerCeiling)} annual tax-exempt limit."
                }
            )
        )

        if (!settings.isSingleHousehold) {
            val isSpouseEmployed = settings.eEmployerRetirementMonthly.finiteNonNegative() > 0.0 ||
                taxYear >= settings.eReturnYear
            val currentSpouseEmployerAnnual = settings.eEmployerRetirementMonthly.finiteNonNegative() * 12.0
            val spouseEmployerHeadroom = if (isSpouseEmployed) max(0.0, employerCeiling - currentSpouseEmployerAnnual) else 0.0
            val spouseEmpStatus = when {
                !isSpouseEmployed -> ChecklistStatus.NOT_APPLICABLE
                spouseEmployerHeadroom <= 0.0 -> ChecklistStatus.FULLY_OPTIMIZED
                else -> ChecklistStatus.ATTENTION_NEEDED
            }
            items.add(
                ChecklistItem(
                    id = "employer_contribution_exemption_spouse",
                    title = "Tax-Exempt Employer Pension Benefit · ${settings.spouseName} (${fmtExactCZK(employerCeiling)}/year cap)",
                    category = ChecklistCategory.EMPLOYER_BENEFIT,
                    status = spouseEmpStatus,
                    statutoryReference = "ZDP § 6(9)(m)",
                    deadline = "Year-end / compensation review",
                    currentValueCzk = min(currentSpouseEmployerAnnual, employerCeiling),
                    ceilingOrThresholdCzk = employerCeiling,
                    remainingHeadroomCzk = spouseEmployerHeadroom,
                    potentialTaxSavingCzk = spouseEmployerHeadroom * (baseRate + 0.116),
                    actionDescription = when {
                        !isSpouseEmployed -> "No active employer payroll in $taxYear"
                        spouseEmployerHeadroom > 0.0 -> "Request higher employer DPS/DIP match for ${settings.spouseName}"
                        else -> "Exempt limit fully utilized"
                    },
                    detailedAdvice = when {
                        !isSpouseEmployed ->
                            "${settings.spouseName} has no active employer salary in $taxYear (e.g. parental leave before ${settings.eReturnYear}); employer contribution exemption resumes upon return to work."
                        spouseEmployerHeadroom > 0.0 ->
                            "${settings.spouseName}'s employer can contribute up to ${fmtExactCZK(employerCeiling)} per year to DIP/DPS free of income tax and payroll contributions. " +
                                "Unused room is ${fmtExactCZK(spouseEmployerHeadroom)}/year."
                        else ->
                            "${settings.spouseName}'s employer retirement contribution is fully optimized at ${fmtExactCZK(employerCeiling)} annual tax-exempt limit."
                    }
                )
            )
        }

        // 3. Spouse Tax Credit
        if (!settings.isSingleHousehold) {
            val childUnder3 = resolvedChildUnder3
            val spouseIncome = (spouseOwnIncomeAnnual ?: CzechTaxEngine.spouseOwnIncomeAnnual(taxYear, settings))
                .finiteNonNegative()

            val qualifies = settings.includeSpouseCredit && childUnder3 && spouseIncome <= spouseIncomeLimit
            val spouseStatus = when {
                !childUnder3 || !settings.includeSpouseCredit -> ChecklistStatus.NOT_APPLICABLE
                qualifies -> ChecklistStatus.FULLY_OPTIMIZED
                else -> ChecklistStatus.ATTENTION_NEEDED
            }

            val vGrossTaxBeforeCredits = run {
                val highInc = max(0.0, vGrossAnnual - taxThreshold)
                val baseInc = vGrossAnnual - highInc
                highInc * highRate + baseInc * baseRate
            }
            val vRemainingTaxForSpouse = max(0.0, vGrossTaxBeforeCredits - basicCredit)
            val spouseCreditSaved = if (qualifies) {
                min(spouseCreditAmount, vRemainingTaxForSpouse)
            } else 0.0
            val spouseAdvice = when {
                !settings.includeSpouseCredit ->
                    "Spouse tax credit is disabled in your household settings."
                !childUnder3 ->
                    "From 2024 onward, claiming the ${fmtExactCZK(spouseCreditAmount)} spouse tax credit requires caring for a dependent child under age 3. The child-age criterion is not met."
                qualifies && spouseCreditSaved < spouseCreditAmount ->
                    "You qualify for the ZDP § 35ba(1)(b) spouse tax credit, but because it is non-refundable, actual saving is capped by your remaining tax liability (${fmtExactCZK(spouseCreditSaved)} of ${fmtExactCZK(spouseCreditAmount)}). Family gifts from parents/relatives and state parental allowance are 100% tax-exempt and excluded from this income cap."
                qualifies ->
                    "You qualify for the ${fmtExactCZK(spouseCreditAmount)} annual spouse tax credit: own income is within the ${fmtExactCZK(spouseIncomeLimit)} limit while caring for a child under 3. Note: Family gifts from parents/relatives and state parental allowance are 100% tax-exempt and excluded from this income cap, whereas maternity benefit (PPM) and wages count."
                else ->
                    "Spouse own annual income (${fmtExactCZK(spouseIncome)}) exceeds the ${fmtExactCZK(spouseIncomeLimit)} limit. The ${fmtExactCZK(spouseCreditAmount)} credit cannot be claimed."
            }

            items.add(
                ChecklistItem(
                    id = "spouse_tax_credit",
                    title = "Spouse Tax Credit (${fmtExactCZK(spouseCreditAmount)})",
                    category = ChecklistCategory.FAMILY_TAX_CREDIT,
                    status = spouseStatus,
                    statutoryReference = "ZDP § 35ba(1)(b)",
                    deadline = "February 15 (payroll) / tax return",
                    currentValueCzk = spouseIncome,
                    ceilingOrThresholdCzk = spouseIncomeLimit,
                    remainingHeadroomCzk = max(0.0, spouseIncomeLimit - spouseIncome),
                    potentialTaxSavingCzk = spouseCreditSaved,
                    actionDescription = when {
                        !settings.includeSpouseCredit -> "Disabled in household settings"
                        !childUnder3 -> "Requires dependent child under age 3"
                        qualifies && spouseCreditSaved <= 0.0 -> "No remaining income tax liability to offset"
                        qualifies -> "Claim spouse tax credit"
                        else -> "Spouse income (${fmtExactCZK(spouseIncome)}) exceeds ${fmtExactCZK(spouseIncomeLimit)} limit"
                    },
                    detailedAdvice = spouseAdvice
                )
            )
            if (qualifies) {
                totalPotentialSavings += spouseCreditSaved
            }
        }

        // 4. Mortgage Interest Deduction Certificate
        val interestPaid = mortgageInterestPaidAnnual?.finiteNonNegative() ?: 0.0
        val isMortgageActive = interestPaid > 0.0 || mortgageInterestPaidAnnual != null
        if (isMortgageActive) {
            val deduction = min(interestPaid, MORTGAGE_INTEREST_CEILING_ANNUAL)
            val mortgageTaxSaving = deduction * baseRate

            items.add(
                ChecklistItem(
                    id = "mortgage_interest_certificate",
                    title = "Mortgage Interest Deduction Certificate (up to ${fmtExactCZK(MORTGAGE_INTEREST_CEILING_ANNUAL)})",
                    category = ChecklistCategory.MORTGAGE_DEDUCTION,
                    status = if (interestPaid > 0.0) ChecklistStatus.ACTION_REQUIRED else ChecklistStatus.ATTENTION_NEEDED,
                    statutoryReference = "ZDP § 15(3)–(4)",
                    deadline = "February 15 (payroll) / tax return",
                    currentValueCzk = interestPaid,
                    ceilingOrThresholdCzk = MORTGAGE_INTEREST_CEILING_ANNUAL,
                    remainingHeadroomCzk = max(0.0, MORTGAGE_INTEREST_CEILING_ANNUAL - interestPaid),
                    potentialTaxSavingCzk = mortgageTaxSaving,
                    actionDescription = "Download bank interest certificate for $taxYear",
                    detailedAdvice = "In January ${taxYear + 1}, your bank issues the annual mortgage interest certificate. " +
                        "This deduction lowers your tax base by ${fmtExactCZK(deduction)}, saving ${fmtExactCZK(mortgageTaxSaving)}. " +
                        "Deliver it to payroll no later than February 15 (15. února), ${taxYear + 1}."
                )
            )
            totalPotentialSavings += mortgageTaxSaving
        }

        // 5. Charitable Donations Deduction (ZDP § 15(1))
        val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
        val charityAnnual = if ("charity" !in deletedSet) settings.charityMonthly.finiteNonNegative() * 12.0 else 0.0
        if (charityAnnual >= RegulatoryConstants.STATUTORY_CHARITY_MIN_ANNUAL_CZK) {
            val maxBasePct = if (taxYear <= 2026) {
                RegulatoryConstants.STATUTORY_CHARITY_MAX_BASE_PCT_TEMP_2026
            } else {
                RegulatoryConstants.STATUTORY_CHARITY_MAX_BASE_PCT_STANDARD
            }
            val charityCap = (vGrossAnnual * (maxBasePct / 100.0)).finiteNonNegative()
            val deductibleCharity = min(charityAnnual, charityCap)
            val vTaxSavedWithCharity = CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = vGrossAnnual,
                deductionAnnual = min(vQualifyingTotal, retirementCeiling) + deductibleCharity,
                thresholdHighBracket = taxThreshold,
                baseRate = baseRate,
                highRate = highRate,
                basicTaxpayerCredit = basicCredit,
                additionalCredits = activeSpouseCredit
            )
            val charityTaxSaving = max(0.0, vTaxSavedWithCharity - vTaxSavedCurrent)
            val charityStatus = if (charityTaxSaving > 0.0) ChecklistStatus.ACTION_REQUIRED else ChecklistStatus.NOT_APPLICABLE
            items.add(
                ChecklistItem(
                    id = "charity_donations_deduction",
                    title = "Charitable Donations Tax Deduction (${fmtExactCZK(charityAnnual)}/yr)",
                    category = ChecklistCategory.CHARITY_DONATIONS,
                    status = charityStatus,
                    statutoryReference = "ZDP § 15(1)",
                    deadline = "February 15 (payroll) / tax return",
                    currentValueCzk = deductibleCharity,
                    ceilingOrThresholdCzk = charityCap,
                    remainingHeadroomCzk = max(0.0, charityCap - deductibleCharity),
                    potentialTaxSavingCzk = charityTaxSaving,
                    actionDescription = if (charityTaxSaving > 0.0) {
                        "Submit charity gift confirmations for $taxYear"
                    } else {
                        "No remaining income tax liability to offset"
                    },
                    detailedAdvice = if (charityTaxSaving > 0.0) {
                        "Qualifying charitable donations of ${fmtExactCZK(deductibleCharity)} (min ${fmtExactCZK(RegulatoryConstants.STATUTORY_CHARITY_MIN_ANNUAL_CZK)}/yr, capped at ${maxBasePct.toInt()}% of tax base) lower your $taxYear tax base and save ${fmtExactCZK(charityTaxSaving)}."
                    } else {
                        "Donations meet the ${fmtExactCZK(RegulatoryConstants.STATUTORY_CHARITY_MIN_ANNUAL_CZK)} annual minimum, but your taxable income is already absorbed by credits and retirement deductions."
                    }
                )
            )
            totalPotentialSavings += charityTaxSaving
        }

        val requiredCount = items.count { it.status == ChecklistStatus.ACTION_REQUIRED }
        val optimizedCount = items.count { it.status == ChecklistStatus.FULLY_OPTIMIZED }

        val summary = "As of December 31, $taxYear, you have $requiredCount open optimization actions and $optimizedCount maxed statutory limits. " +
            "Total potential tax savings and refunds: ${fmtExactCZK(totalPotentialSavings)}."

        return YearEndTaxChecklistReport(
            taxYear = taxYear,
            generatedDate = LocalDate.now(ZoneId.of("Europe/Prague")).toString(),
            items = items,
            totalPotentialTaxSavingsCzk = totalPotentialSavings,
            actionsRequiredCount = requiredCount,
            fullyOptimizedCount = optimizedCount,
            summaryMessage = summary
        )
    }
}

/**
 * Category of tax checklist item.
 */
enum class ChecklistCategory {
    RETIREMENT_DIP_DPS,
    EMPLOYER_BENEFIT,
    FAMILY_TAX_CREDIT,
    MORTGAGE_DEDUCTION,
    CHARITY_DONATIONS
}

/**
 * Optimization status of a checklist item.
 */
enum class ChecklistStatus {
    FULLY_OPTIMIZED,     // Maximum statutory benefit already utilized
    ACTION_REQUIRED,     // Direct action before Dec 31 yields guaranteed savings
    ATTENTION_NEEDED,    // Thresholds or conditions require tracking
    NOT_APPLICABLE       // Statutory criteria not met (e.g. child > 3 for spouse credit)
}

/**
 * Detailed checklist item for year-end tax optimization.
 */
@Immutable
data class ChecklistItem(
    val id: String,
    val title: String,
    val category: ChecklistCategory,
    val status: ChecklistStatus,
    val statutoryReference: String,
    val deadline: String,
    val currentValueCzk: Double,
    val ceilingOrThresholdCzk: Double,
    val remainingHeadroomCzk: Double,
    val potentialTaxSavingCzk: Double,
    val actionDescription: String,
    val detailedAdvice: String
)

/**
 * Aggregate report summarizing all year-end tax checklist recommendations.
 */
@Immutable
data class YearEndTaxChecklistReport(
    val taxYear: Int,
    val generatedDate: String,
    val items: List<ChecklistItem>,
    val totalPotentialTaxSavingsCzk: Double,
    val actionsRequiredCount: Int,
    val fullyOptimizedCount: Int,
    val summaryMessage: String
)
