package com.example.domain

import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object HouseholdCashFlowEngine {

    fun computeTrailingBurnRateMonthly(ledger: List<LedgerEntryEntity>, months: Int): Double? {
        val nonZeroEntries = ledger.filter { (it.expRent + it.expGroceries + it.expOther) > 0.0 }
            .sortedByDescending { it.yearMonth }
            .take(months)
        if (nonZeroEntries.isEmpty()) return null
        val sum = nonZeroEntries.sumOf { it.expRent + it.expGroceries + it.expOther }
        return sum / nonZeroEntries.size
    }

    fun computeYtdTaxShieldStatus(
        settings: SettingsEntity,
        currentYear: Int = settings.baseYear,
        currentMonth: Int = 1,
        importedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
        partnerBirthYearOverride: Int? = null
    ): YtdTaxShieldStatus {
        val safeMonth = currentMonth.coerceIn(1, 12)
        val baseThresholdMonthly = settings.dpsDeductionThresholdMonthly.finiteNonNegative(
            RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026
        )
        val vAgeAtYear = settings.primaryAge + (currentYear - settings.baseYear)
        val thresholdMonthly = if (vAgeAtYear >= settings.vStatePensionAge) 0.0 else baseThresholdMonthly
        val eAgeAtYear = currentYear - partnerBirthYear(settings, partnerBirthYearOverride)
        val eThresholdMonthly = if (eAgeAtYear >= settings.eStatePensionAge) 0.0 else baseThresholdMonthly
        val yearPrefix = "$currentYear"
        val yearTxs = importedTransactions.filter {
            !it.isNetted && (it.yearMonth.startsWith(yearPrefix) || it.date.startsWith(yearPrefix))
        }
        val dipTxs = yearTxs.filter { it.category == "INVESTMENT_DIP" }
        val dpsTxs = yearTxs.filter { it.category == "INVESTMENT_DPS" }
        val hasImported = dipTxs.isNotEmpty() || dpsTxs.isNotEmpty()

        val (dipYtd, dpsYtd) = if (hasImported) {
            // Signed net outflows: deposits are negative, refunds/reversals are positive (subtract rather than inflate).
            val dipActual = max(0.0, dipTxs.sumOf { -it.amount.finiteOr(0.0) })
            val dpsByMonthAndAccount = dpsTxs.groupBy { tx ->
                val ym = if (tx.yearMonth.isNotBlank()) tx.yearMonth else tx.date.take(7)
                val acct = tx.accountKey.ifBlank { tx.bankName }
                ym to acct
            }
            val dpsActual = dpsByMonthAndAccount.values.sumOf { txs ->
                val monthNet = max(0.0, txs.sumOf { -it.amount.finiteOr(0.0) })
                max(0.0, monthNet - thresholdMonthly)
            }
            dipActual to dpsActual
        } else {
            val dipPlanned = settings.dipContributionMonthly.finiteNonNegative() * safeMonth
            val dpsPlanned = max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - thresholdMonthly) * safeMonth
            dipPlanned to dpsPlanned
        }

        val ceiling = settings.taxDeductionCeilingAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026
        )
        val partnerDipYtd = if (!settings.isSingleHousehold && !hasImported) {
            settings.eDipContributionMonthly.finiteNonNegative() * safeMonth
        } else 0.0
        val partnerDpsYtd = if (!settings.isSingleHousehold && !hasImported) {
            max(0.0, settings.eDpsOwnContributionMonthly.finiteNonNegative() - eThresholdMonthly) * safeMonth
        } else 0.0
        val partnerRemainingHeadroom = if (!settings.isSingleHousehold) {
            max(0.0, ceiling - (partnerDipYtd + partnerDpsYtd))
        } else 0.0
        val householdCeiling = if (settings.isSingleHousehold) ceiling else ceiling * 2.0

        val totalYtd = (dipYtd + dpsYtd).finiteNonNegative()
        val progressPct = if (ceiling > 0.0) ((totalYtd / ceiling) * 100.0).finiteIn(0.0, 100.0) else 100.0
        val effectiveDeduction = min(ceiling, totalYtd)
        val baseRatePct = settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highRatePct = settings.taxRateSecondPct.finiteIn(baseRatePct, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)
        val baseRate = baseRatePct / 100.0
        val highRate = highRatePct / 100.0
        val taxThreshold = settings.taxSecondBracketThresholdAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        val basicCredit = settings.taxpayerCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026
        )
        val vGrossAnnual = CzechTaxEngine.primaryGrossAnnual(settings, currentYear)
        val activeSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(settings, currentYear)
        val refund15 = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGrossAnnual,
            deductionAnnual = effectiveDeduction,
            thresholdHighBracket = taxThreshold,
            baseRate = baseRate,
            highRate = highRate,
            basicTaxpayerCredit = basicCredit,
            additionalCredits = activeSpouseCredit
        ).finiteNonNegative()
        val refund23 = if (vGrossAnnual > 0.0) (effectiveDeduction * highRate).finiteNonNegative() else 0.0
        val remainingHeadroom = max(0.0, ceiling - totalYtd)
        val monthsRemaining = max(1, 12 - safeMonth)
        val recommendedPace = if (remainingHeadroom > 0.0) remainingHeadroom / monthsRemaining else 0.0

        return YtdTaxShieldStatus(
            currentYear = currentYear,
            statutoryCeilingAnnual = ceiling,
            ytdDipContributed = dipYtd,
            ytdDpsContributed = dpsYtd,
            ytdTotalContributed = totalYtd,
            ytdProgressPct = progressPct,
            taxRefundSecured15Pct = refund15,
            taxRefundSecured23Pct = refund23,
            remainingHeadroomAnnual = remainingHeadroom,
            monthsRemainingInYear = monthsRemaining,
            recommendedMonthlyPace = recommendedPace,
            isCeilingReached = totalYtd >= ceiling,
            isFromImportedTransactions = hasImported,
            partnerYtdDipContributed = partnerDipYtd,
            partnerYtdDpsContributed = partnerDpsYtd,
            partnerRemainingHeadroomAnnual = partnerRemainingHeadroom,
            householdCeilingAnnual = householdCeiling
        )
    }

    fun partnerBirthYear(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): Int =
        partnerBirthYearOverride ?: (settings.baseYear - settings.primaryAge)

    fun annualPartnerTaxCredit(
        settings: SettingsEntity,
        year: Int,
        vGrossOverride: Double? = null,
        child1BirthMonth: Int = 1,
        child2BirthMonth: Int = 1,
        maternityBenefitPpmAnnual: Double = 0.0
    ): Double {
        val eligibleSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(
            settings = settings,
            year = year,
            child1BirthMonth = child1BirthMonth,
            child2BirthMonth = child2BirthMonth,
            maternityBenefitPpmAnnual = maternityBenefitPpmAnnual
        )
        if (eligibleSpouseCredit <= 0.0) return 0.0

        val credit = settings.taxpayerCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026
        )
        val highThreshold = settings.taxSecondBracketThresholdAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        val baseRatePct = settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highRatePct = settings.taxRateSecondPct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)

        val vGross = (vGrossOverride ?: CzechTaxEngine.primaryGrossAnnual(settings, year)).finiteNonNegative()
        val vGrossTaxBeforeDeduction = run {
            val highIncome = max(0.0, vGross - highThreshold)
            val baseIncome = vGross - highIncome
            highIncome * (highRatePct / 100.0) + baseIncome * (baseRatePct / 100.0)
        }
        val vRemainingTaxBeforeDeduction = max(0.0, vGrossTaxBeforeDeduction - credit)
        return min(eligibleSpouseCredit, vRemainingTaxBeforeDeduction).finiteNonNegative()
    }

    fun vaclavSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        val sy = settings.baseYear
        if (year < sy) return 0.0
        val baseSal = settings.vSalary.finiteNonNegative()
        val growthPct = settings.vSalaryGrowthPct.finiteOr(0.0)
        val growthFactor = if (growthPct != 0.0) {
            (1.0 + growthPct / 100.0).coerceAtLeast(0.0).pow(year - sy).finiteOr(1.0)
        } else 1.0
        return (baseSal * growthFactor).finiteNonNegative()
    }

    fun eleonoraSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.eReturnYear) return 0.0
        val month = settings.eReturnMonth.coerceIn(1, 12)
        val startSal = settings.eStartingSalary.finiteNonNegative()
        val growthPct = settings.eSalaryGrowthPct.finiteOr(0.0)
        val fullSalary = if (year == settings.eReturnYear) {
            startSal
        } else {
            val yearsActive = year - settings.eReturnYear
            val growthFactor = (1.0 + growthPct / 100.0).coerceAtLeast(0.0).pow(yearsActive)
            if (growthFactor.isFinite()) (startSal * growthFactor).finiteNonNegative() else 0.0
        }
        return if (year == settings.eReturnYear) {
            val workFraction = (13 - month) / 12.0
            (fullSalary * workFraction).finiteNonNegative()
        } else {
            fullSalary
        }
    }

    private fun parentalCutoffAgeYears(birthYear: Int): Int =
        if (birthYear >= 2024) 3 else 4

    fun remainingParentalPotAtYearStart(targetYear: Int, settings: SettingsEntity): Double {
        if (targetYear < settings.baseYear) return 0.0
        val baseYear = settings.baseYear
        val monthlyDraw = settings.eParentalAllowanceMonthly.finiteNonNegative()
        val c1On = settings.child1Enabled
        val y1 = settings.child1BirthYear
        val c2On = settings.child2Enabled
        val y2 = settings.child2BirthYear

        // Act No. 117/1995 Coll. § 30(1): younger child birth forfeits any unspent prior child pot.
        // Multiple birth (twins) multiplier is 2.0x (700k CZK) from 2026 (1.5x prior).
        // Act No. 407/2023 Coll.: entitlement ends at youngest active child age 3 (born >= 2024) or 4 (born < 2024).
        // Czech law imposes NO employment income limit on parental allowance — returning to work never forfeits it.
        var pot = 0.0
        var activeExpiryYear: Int? = null

        fun applyBirth(birthYear: Int) {
            when {
                c1On && c2On && birthYear == y1 && y1 == y2 -> {
                    val twinsMult = if (y1 >= 2026) {
                        RegulatoryConstants.PARENTAL_ALLOWANCE_TWINS_MULTIPLIER_2026
                    } else 1.5
                    pot = parentalAllowancePot(y1, settings) * twinsMult
                    activeExpiryYear = y1 + parentalCutoffAgeYears(y1)
                }
                c2On && birthYear == y2 && (!c1On || y2 >= y1) -> {
                    pot = parentalAllowancePot(y2, settings)
                    activeExpiryYear = y2 + parentalCutoffAgeYears(y2)
                }
                c1On && birthYear == y1 -> {
                    pot = parentalAllowancePot(y1, settings)
                    activeExpiryYear = y1 + parentalCutoffAgeYears(y1)
                }
            }
        }

        // Seed state at baseYear using the latest already-born child, deducting estimated draws prior to baseYear
        val bornAtBaseYears = listOfNotNull(
            y1.takeIf { c1On && baseYear >= y1 },
            y2.takeIf { c2On && baseYear >= y2 }
        ).distinct().sorted()
        var lastSeedBirthYear: Int? = null
        for (by in bornAtBaseYears) {
            applyBirth(by)
            lastSeedBirthYear = by
        }
        if (lastSeedBirthYear != null && lastSeedBirthYear < baseYear && pot > 0.0) {
            val ageYears = baseYear - lastSeedBirthYear
            val estPriorMonths = if (ageYears == 1) 6.0 else ((ageYears - 1) * 12.0)
            val effectiveDrawMonths = (parentalCutoffAgeYears(lastSeedBirthYear) * 12.0 - 6.0).coerceAtLeast(12.0)
            val priorMonthlyPace = min(monthlyDraw, pot / effectiveDrawMonths)
            pot = max(0.0, pot - estPriorMonths * priorMonthlyPace)
        }
        if (activeExpiryYear != null && baseYear >= activeExpiryYear!!) {
            pot = 0.0
        }

        for (y in baseYear until targetYear) {
            if (y > baseYear) {
                if (c1On && y == y1) applyBirth(y1)
                if (c2On && y == y2 && y2 != y1) applyBirth(y2)
                if (activeExpiryYear != null && y >= activeExpiryYear!!) {
                    pot = 0.0
                }
            }
            if (pot > 0.0) {
                val desired = monthlyDraw * 12.0
                val drawn = min(pot, max(0.0, desired))
                pot -= drawn
            }
        }
        if (targetYear > baseYear) {
            if (c1On && targetYear == y1) applyBirth(y1)
            if (c2On && targetYear == y2 && y2 != y1) applyBirth(y2)
            if (activeExpiryYear != null && targetYear >= activeExpiryYear!!) {
                pot = 0.0
            }
        }
        return max(0.0, pot)
    }

    fun eleonoraBenefitMonthly(year: Int, settings: SettingsEntity): Double {
        if (year < settings.baseYear) return 0.0
        val remainingPot = remainingParentalPotAtYearStart(year, settings)
        if (remainingPot <= 0.0) return 0.0

        val desiredAnnual = settings.eParentalAllowanceMonthly.finiteNonNegative() * 12.0
        val actualAnnual = min(desiredAnnual, remainingPot)
        return actualAnnual / 12.0
    }

    fun eleonoraBenefitForMonth(year: Int, month: Int, settings: SettingsEntity): Double {
        if (year < settings.baseYear) return 0.0
        val m = month.coerceIn(1, 12)
        val remainingPotAtStartOfYear = remainingParentalPotAtYearStart(year, settings)
        if (remainingPotAtStartOfYear <= 0.0) return 0.0

        val monthlyDraw = settings.eParentalAllowanceMonthly.finiteNonNegative()
        val drawnPriorInThisYear = (m - 1) * monthlyDraw
        val remainingForThisMonth = max(0.0, remainingPotAtStartOfYear - drawnPriorInThisYear)
        return min(monthlyDraw, max(0.0, remainingForThisMonth))
    }

    fun parentalAllowancePot(birthYear: Int, settings: SettingsEntity): Double {
        return if (birthYear >= settings.parentalAllowanceCutoffYear) {
            settings.parentalAllowanceTotalFromCutoff
        } else {
            settings.parentalAllowanceTotalBeforeCutoff
        }
    }

    fun eleonoraLecturingMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || !settings.eIncludeLecturing || year > settings.eReturnYear) return 0.0
        val baseLecturing = settings.eLecturingMonthly
        return if (year == settings.eReturnYear) {
            val month = settings.eReturnMonth.coerceIn(1, 12)
            val leaveFraction = (month - 1) / 12.0
            baseLecturing * leaveFraction
        } else {
            baseLecturing
        }
    }

    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double =
        CzechTaxEngine.spouseOwnIncomeAnnual(year, settings)

    fun householdIncome(
        year: Int,
        settings: SettingsEntity,
        activeLedgerEntry: LedgerEntryEntity? = null,
        month: Int? = null
    ): YearlyIncome {
        val baseYear = settings.baseYear
        val ledgerMatchesYear = activeLedgerEntry != null &&
            year == baseYear &&
            (activeLedgerEntry.yearMonth.isBlank() || activeLedgerEntry.yearMonth.startsWith("$year-"))
        val v = if (ledgerMatchesYear && activeLedgerEntry!!.incVaclav > 0.0) {
            activeLedgerEntry.incVaclav
        } else {
            vaclavSalaryMonthly(year, settings)
        }

        val isBeforeReturn = year < settings.eReturnYear
        val isReturnYear = year == settings.eReturnYear
        val isSingle = settings.isSingleHousehold

        val e = when {
            isSingle -> 0.0
            isBeforeReturn -> 0.0
            isReturnYear -> {
                if (month != null) {
                    if (month < settings.eReturnMonth) 0.0
                    else if (ledgerMatchesYear && activeLedgerEntry!!.incEleonora > 0.0) activeLedgerEntry.incEleonora
                    else eleonoraSalaryMonthly(year, settings.copy(eReturnMonth = 1))
                } else {
                    eleonoraSalaryMonthly(year, settings)
                }
            }
            ledgerMatchesYear && activeLedgerEntry!!.incEleonora > 0.0 -> {
                activeLedgerEntry.incEleonora
            }
            else -> eleonoraSalaryMonthly(year, settings)
        }

        val b = when {
            isSingle -> {
                if (month != null) eleonoraBenefitForMonth(year, month, settings)
                else eleonoraBenefitMonthly(year, settings)
            }
            isBeforeReturn -> {
                if (ledgerMatchesYear && activeLedgerEntry!!.incEleonora > 0.0) {
                    activeLedgerEntry.incEleonora
                } else if (month != null) {
                    eleonoraBenefitForMonth(year, month, settings)
                } else {
                    eleonoraBenefitMonthly(year, settings)
                }
            }
            isReturnYear -> {
                if (month != null) {
                    if (month < settings.eReturnMonth && ledgerMatchesYear && activeLedgerEntry!!.incEleonora > 0.0) {
                        activeLedgerEntry.incEleonora
                    } else {
                        eleonoraBenefitForMonth(year, month, settings)
                    }
                } else {
                    eleonoraBenefitMonthly(year, settings)
                }
            }
            else -> {
                if (month != null) eleonoraBenefitForMonth(year, month, settings)
                else eleonoraBenefitMonthly(year, settings)
            }
        }

        val lec = when {
            isSingle -> 0.0
            isReturnYear && month != null -> {
                if (month < settings.eReturnMonth && settings.eIncludeLecturing) settings.eLecturingMonthly else 0.0
            }
            else -> eleonoraLecturingMonthly(year, settings)
        }

        val vGift = settings.vGiftsMonthly.finiteNonNegative()
        val eGift = if (!settings.isSingleHousehold) settings.eGiftsMonthly.finiteNonNegative() else 0.0
        val totalGift = vGift + eGift

        val scheduledLumps = if (month != null) {
            lumpSumsForMonth(year, month, settings).sumOf { it.amount }
        } else {
            lumpSumForYear(year, settings) / 12.0
        }

        val budgetedOtherV = settings.vOtherInflowsMonthly.finiteNonNegative()
        val budgetedOtherE = if (!settings.isSingleHousehold) settings.eOtherInflowsMonthly.finiteNonNegative() else 0.0
        val (vOther, eOther, lumpTotal) = if (ledgerMatchesYear && activeLedgerEntry!!.incUnforeseen > 0.0) {
            val actualOther = activeLedgerEntry.incUnforeseen
            val budgetedTotal = budgetedOtherV + budgetedOtherE + scheduledLumps
            Triple(budgetedOtherV, budgetedOtherE, scheduledLumps + max(0.0, actualOther - budgetedTotal))
        } else {
            Triple(budgetedOtherV, budgetedOtherE, scheduledLumps)
        }

        val total = v + e + b + lec + settings.vMealVouchersMonthly + totalGift + vOther + eOther + lumpTotal

        return YearlyIncome(
            year = year,
            vaclavNet = v,
            eleonoraSalary = e,
            benefit = b,
            lecturing = lec,
            vouchers = settings.vMealVouchersMonthly,
            gift = totalGift,
            vaclavOther = vOther,
            eleonoraOther = eOther,
            vaclavGifts = vGift,
            eleonoraGifts = eGift,
            lumpSumMonthly = lumpTotal,
            totalMonthly = total
        )
    }

    fun dpsSubsidy(
        monthlyDeposit: Double,
        age: Int,
        settings: SettingsEntity? = null,
        year: Int = settings?.baseYear ?: 2026,
        statePensionAge: Int = settings?.vStatePensionAge ?: Int.MAX_VALUE
    ): Double = CzechTaxEngine.dpsSubsidy(monthlyDeposit, age, settings, year, statePensionAge)

    fun annualRetirementDeduction(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): Double =
        CzechTaxEngine.annualRetirementDeduction(settings, settings.baseYear, partnerBirthYearOverride)

    fun annualRetirementTaxSaved(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        partnerBirthYearOverride: Int? = null
    ): Double = CzechTaxEngine.annualRetirementTaxSaved(settings, year, partnerBirthYearOverride)

    fun netToGrossAnnual(
        netMonthly: Double,
        taxpayerCreditAnnual: Double,
        highBracketThresholdAnnual: Double = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
        baseTaxRatePct: Double = 15.0,
        highTaxRatePct: Double = 23.0
    ): Double = CzechTaxEngine.netToGrossAnnual(netMonthly, taxpayerCreditAnnual, highBracketThresholdAnnual, baseTaxRatePct, highTaxRatePct)

    fun dipTaxSavingYear(settings: SettingsEntity): Double =
        CzechTaxEngine.dipTaxSavingYear(settings)

    fun childMonthlyExpense(birthYear: Int, currentYear: Int, settings: SettingsEntity): Double {
        val age = currentYear - birthYear
        if (age < 0 || age >= settings.childSupportUntilAge) return 0.0
        return when (age) {
            in 0..2 -> settings.childToddlerMonthly
            in 3..5 -> settings.childPreschoolMonthly
            in 6..14 -> settings.childSchoolMonthly
            in 15..18 -> settings.childTeenMonthly
            else -> settings.childUniMonthly
        }
    }

    internal class LivingCostSpec(
        val nonRentBaseMonthly: Double,
        val baseRentMonthly: Double,
        val rentGrowthPct: Double
    ) {
        fun rentAtYear(settings: SettingsEntity, year: Int): Double {
            if (baseRentMonthly <= 0.0) return 0.0
            val rentYears = max(0, year - settings.baseYear)
            val rentFactor = if (rentYears > 0 && rentGrowthPct != 0.0) {
                (1.0 + rentGrowthPct / 100.0).coerceAtLeast(0.0).pow(rentYears).finiteOr(1.0)
            } else 1.0
            return (baseRentMonthly * rentFactor).finiteNonNegative()
        }

        fun childAdditionAtYear(settings: SettingsEntity, year: Int): Double {
            if (!settings.childExpensesEnabled) return 0.0
            fun computeChildAddition(enabled: Boolean, birthYear: Int): Double {
                if (!enabled) return 0.0
                return if (settings.currentChildCostsInBaseline) {
                    if (birthYear <= settings.baseYear) {
                        if (year <= settings.baseYear) {
                            0.0
                        } else {
                            childMonthlyExpense(birthYear, year, settings) -
                                childMonthlyExpense(birthYear, settings.baseYear, settings)
                        }
                    } else {
                        if (year < birthYear) 0.0 else childMonthlyExpense(birthYear, year, settings)
                    }
                } else {
                    childMonthlyExpense(birthYear, year, settings)
                }
            }
            return (computeChildAddition(settings.child1Enabled, settings.child1BirthYear) +
                computeChildAddition(settings.child2Enabled, settings.child2BirthYear)).finiteOr(0.0)
        }

        fun nonRentMonthlyAtYear(settings: SettingsEntity, year: Int): Double =
            (nonRentBaseMonthly + childAdditionAtYear(settings, year)).finiteNonNegative()

        fun totalMonthlyAtYear(settings: SettingsEntity, year: Int): Double =
            (nonRentMonthlyAtYear(settings, year) + rentAtYear(settings, year)).finiteNonNegative()

        fun annualLivingAtProjectedYear(
            settings: SettingsEntity,
            targetYear: Int,
            inflationFactor: Double,
            rentFactor: Double
        ): Double {
            return if (rentGrowthPct != 0.0 && baseRentMonthly > 0.0) {
                ((nonRentMonthlyAtYear(settings, targetYear) * inflationFactor + baseRentMonthly * rentFactor) * 12.0)
                    .finiteNonNegative()
            } else {
                (totalMonthlyAtYear(settings, targetYear) * 12.0 * inflationFactor).finiteNonNegative()
            }
        }

        companion object {
            fun from(settings: SettingsEntity): LivingCostSpec {
                val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
                var base = 0.0
                if (!deletedSet.contains("groceries")) base += settings.groceriesMonthly.finiteNonNegative()
                if (!deletedSet.contains("other_discretionary")) base += settings.otherDiscretionaryMonthly.finiteNonNegative()
                if (!deletedSet.contains("cafes")) base += settings.cafesMonthly.finiteNonNegative()
                if (!deletedSet.contains("therapy")) base += settings.therapyMonthly.finiteNonNegative()
                if (!deletedSet.contains("charity")) base += settings.charityMonthly.finiteNonNegative()
                if (!deletedSet.contains("entertainment")) base += settings.entertainmentMonthly.finiteNonNegative()
                if (!deletedSet.contains("transport")) base += settings.transportMonthly.finiteNonNegative()
                if (!deletedSet.contains("subscriptions")) base += settings.subscriptionsMonthly.finiteNonNegative()

                val customItems = parseCustomExpenses(settings.customExpensesJson)
                base += customItems.sumOf { it.amount.finiteNonNegative() }
                val baseRent = if (!deletedSet.contains("rent")) settings.rentMonthly.finiteNonNegative() else 0.0
                return LivingCostSpec(
                    nonRentBaseMonthly = base.finiteNonNegative(),
                    baseRentMonthly = baseRent,
                    rentGrowthPct = settings.rentGrowthPct.finiteOr(0.0)
                )
            }
        }
    }

    fun totalLivingCostMonthly(settings: SettingsEntity, year: Int = settings.baseYear): Double {
        return LivingCostSpec.from(settings).totalMonthlyAtYear(settings, year)
    }

    fun computeEmergencyReserveTargetForMode(
        settings: SettingsEntity,
        totalLivingMonthly: Double,
        mode: String
    ): Double {
        val living = totalLivingMonthly.finiteNonNegative()
        val customTarget = settings.emergencyReserveTarget.finiteNonNegative()
        val raw = when (mode) {
            "3M" -> living * 3.0
            "6M" -> living * 6.0
            "9M" -> living * 9.0
            "12M" -> living * 12.0
            else -> if (customTarget > 0.0) customTarget else living * 6.0
        }
        return (kotlin.math.round(raw / 1_000.0) * 1_000.0).coerceAtLeast(1_000.0)
    }

    fun effectiveEmergencyReserveTarget(settings: SettingsEntity): Double {
        return computeEmergencyReserveTargetForMode(
            settings,
            totalLivingCostMonthly(settings),
            settings.emergencyReserveMode
        )
    }

    /**
     * Auto-calculated monthly cash allocation toward building the emergency reserve buffer when current reserves
     * sit below the effective target (prorated across a 24-month funding runway).
     */
    fun autoEmergencyReserveContribution(settings: SettingsEntity): Double {
        val reserveCurrent = settings.emergencyReserveCurrent.finiteNonNegative()
        val reserveTarget = effectiveEmergencyReserveTarget(settings).finiteNonNegative()
        return if (reserveCurrent < reserveTarget) {
            ((reserveTarget - reserveCurrent) / 24.0).finiteNonNegative()
        } else {
            0.0
        }
    }

    /**
     * Effective monthly cash allocation toward the emergency reserve buffer:
     * returns the explicit manual monthly override when set (>= 0.0), or falls back to the 24-month auto catch-up.
     */
    fun monthlyEmergencyReserveContribution(settings: SettingsEntity): Double {
        val manual = settings.emergencyReserveMonthlyOverride
        if (manual.isFinite() && manual >= 0.0) {
            return manual
        }
        return autoEmergencyReserveContribution(settings)
    }

    fun monthlySavingsAndInvestmentTotal(settings: SettingsEntity): Double {
        return baseInvestMonthly(settings) + monthlyEmergencyReserveContribution(settings)
    }

    fun baseOwnInvestMonthly(settings: SettingsEntity): Double {
        val vaclavOwn = settings.portuDcaMonthly.finiteNonNegative() +
            settings.dpsOwnContributionMonthly.finiteNonNegative() +
            settings.dipContributionMonthly.finiteNonNegative()
        val eleonoraOwn = if (!settings.isSingleHousehold) {
            settings.ePortuDcaMonthly.finiteNonNegative() +
                settings.eDpsOwnContributionMonthly.finiteNonNegative() +
                settings.eDipContributionMonthly.finiteNonNegative()
        } else 0.0
        return (vaclavOwn + eleonoraOwn).finiteNonNegative()
    }

    fun baseEmployerMatchMonthly(settings: SettingsEntity): Double {
        val empCap = settings.employerRetirementExemptionAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL
        ) / 12.0
        val vEmp = min(settings.employerRetirementMonthly.finiteNonNegative(), empCap)
        val eEmp = if (!settings.isSingleHousehold) {
            min(settings.eEmployerRetirementMonthly.finiteNonNegative(), empCap)
        } else 0.0
        return (vEmp + eEmp).finiteNonNegative()
    }

    fun baseInvestMonthly(settings: SettingsEntity): Double {
        return (baseOwnInvestMonthly(settings) + baseEmployerMatchMonthly(settings)).finiteNonNegative()
    }


    fun computePensionOptimizationSnapshot(
        s: SettingsEntity,
        currentIncome: YearlyIncome = householdIncome(s.baseYear, s),
        partnerBirthYearOverride: Int? = null
    ): PensionOptimizationSnapshot {
        val vAge = s.primaryAge
        val eAge = s.baseYear - partnerBirthYear(s, partnerBirthYearOverride)
        val vSubsidy = dpsSubsidy(s.dpsOwnContributionMonthly, vAge, s, s.baseYear, s.vStatePensionAge)
        val eSubsidy = if (!s.isSingleHousehold) {
            dpsSubsidy(s.eDpsOwnContributionMonthly, eAge, s, s.baseYear, s.eStatePensionAge)
        } else 0.0

        val vSalaryMonthly = currentIncome.vaclavNet.finiteNonNegative()
        val eSalaryMonthly = if (!s.isSingleHousehold) currentIncome.eleonoraSalary.finiteNonNegative() else 0.0
        val eLecturingMonthly = if (!s.isSingleHousehold) currentIncome.lecturing.finiteNonNegative() else 0.0
        val baseThreshold = s.dpsDeductionThresholdMonthly.finiteNonNegative(
            RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026
        )
        val ceiling = s.taxDeductionCeilingAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026
        )
        val baseRatePct = s.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highRatePct = s.taxRateSecondPct.finiteIn(baseRatePct, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)
        val taxThreshold = s.taxSecondBracketThresholdAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        val taxpayerCredit = s.taxpayerCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026
        )

        val vGrossAnnual = CzechTaxEngine.netToGrossAnnual(
            netMonthly = vSalaryMonthly,
            taxpayerCreditAnnual = taxpayerCredit,
            highBracketThresholdAnnual = taxThreshold,
            baseTaxRatePct = baseRatePct,
            highTaxRatePct = highRatePct,
            embeddedMonthlyChildCredit = CzechTaxEngine.baseYearChildCreditAnnual(s) / 12.0
        )
        val eGrossAnnual = if (!s.isSingleHousehold) {
            CzechTaxEngine.spouseEarnedGrossAnnual(s.baseYear, s)
        } else 0.0
        val activeSpouseCredit = CzechTaxEngine.eligibleSpouseCreditAnnual(s, s.baseYear)
        val vHasIncome = vSalaryMonthly > 0.0
        val eHasIncome = !s.isSingleHousehold && (eSalaryMonthly + eLecturingMonthly) > 0.0

        val vDpsEffectiveThreshold = if (vAge >= s.vStatePensionAge) 0.0 else baseThreshold
        val vDeductionAnnual = min(
            ceiling,
            s.dipContributionMonthly.finiteNonNegative() * 12.0 +
                max(0.0, s.dpsOwnContributionMonthly.finiteNonNegative() - vDpsEffectiveThreshold) * 12.0
        )
        val vActualAnnualTaxSaved = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = vGrossAnnual,
            deductionAnnual = vDeductionAnnual,
            thresholdHighBracket = taxThreshold,
            baseRate = baseRatePct / 100.0,
            highRate = highRatePct / 100.0,
            basicTaxpayerCredit = taxpayerCredit,
            additionalCredits = activeSpouseCredit
        )
        val eDpsEffectiveThreshold = if (eAge >= s.eStatePensionAge) 0.0 else baseThreshold
        val eDeductionAnnual = if (!s.isSingleHousehold) {
            min(
                ceiling,
                s.eDipContributionMonthly.finiteNonNegative() * 12.0 +
                    max(0.0, s.eDpsOwnContributionMonthly.finiteNonNegative() - eDpsEffectiveThreshold) * 12.0
            )
        } else 0.0
        val eActualAnnualTaxSaved = if (!s.isSingleHousehold) {
            CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = eGrossAnnual,
                deductionAnnual = eDeductionAnnual,
                thresholdHighBracket = taxThreshold,
                baseRate = baseRatePct / 100.0,
                highRate = highRatePct / 100.0,
                basicTaxpayerCredit = taxpayerCredit
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

        val vOptimalDepositAnnual = (if (vAge < s.vStatePensionAge) baseThreshold * 12.0 else 0.0) + (if (vHasIncome) ceiling else 0.0)
        val eOptimalDepositAnnual = if (!s.isSingleHousehold) {
            (if (eAge < s.eStatePensionAge) baseThreshold * 12.0 else 0.0) + (if (eHasIncome) ceiling else 0.0)
        } else 0.0
        val totalOptimalDepositAnnual = vOptimalDepositAnnual + eOptimalDepositAnnual

        val maxCombinedBenefit = (vSubsidyMaxAnnual + eSubsidyMaxAnnual) +
            CzechTaxEngine.singleEarnerRetirementTaxSaved(
                taxableGrossAnnual = vGrossAnnual,
                deductionAnnual = ceiling,
                thresholdHighBracket = taxThreshold,
                baseRate = baseRatePct / 100.0,
                highRate = highRatePct / 100.0,
                basicTaxpayerCredit = taxpayerCredit,
                additionalCredits = activeSpouseCredit
            ) +
            (if (!s.isSingleHousehold) {
                CzechTaxEngine.singleEarnerRetirementTaxSaved(
                    taxableGrossAnnual = eGrossAnnual,
                    deductionAnnual = ceiling,
                    thresholdHighBracket = taxThreshold,
                    baseRate = baseRatePct / 100.0,
                    highRate = highRatePct / 100.0,
                    basicTaxpayerCredit = taxpayerCredit
                )
            } else 0.0)

        val totalOwnRetirementMonthly = s.dipContributionMonthly.finiteNonNegative() +
            s.dpsOwnContributionMonthly.finiteNonNegative() +
            if (!s.isSingleHousehold) {
                s.eDipContributionMonthly.finiteNonNegative() + s.eDpsOwnContributionMonthly.finiteNonNegative()
            } else 0.0
        val empMatchMonthly = baseEmployerMatchMonthly(s)
        val dpsNetReturnPct = DpsDipEngine.effectiveDpsNetReturnPct(s)

        return PensionOptimizationSnapshot(
            primaryAge = vAge,
            partnerAge = eAge,
            primaryStatePensionAge = s.vStatePensionAge,
            partnerStatePensionAge = s.eStatePensionAge,
            primarySubsidyMonthly = vSubsidy,
            partnerSubsidyMonthly = eSubsidy,
            totalSubsidyMonthly = vSubsidy + eSubsidy,
            primaryAnnualTaxSaved = vActualAnnualTaxSaved,
            partnerAnnualTaxSaved = eActualAnnualTaxSaved,
            totalAnnualTaxSaved = vActualAnnualTaxSaved + eActualAnnualTaxSaved,
            primarySubsidyMaxAnnual = vSubsidyMaxAnnual,
            partnerSubsidyMaxAnnual = eSubsidyMaxAnnual,
            primaryOptimalDepositAnnual = vOptimalDepositAnnual,
            partnerOptimalDepositAnnual = eOptimalDepositAnnual,
            totalOptimalDepositAnnual = totalOptimalDepositAnnual,
            maxCombinedBenefitAnnual = maxCombinedBenefit,
            totalMonthlyOwnRetirement = totalOwnRetirementMonthly,
            totalMonthlyRetirementWithMatch = totalOwnRetirementMonthly + empMatchMonthly,
            dpsNetReturnPct = dpsNetReturnPct,
            primaryDpsDeductionThresholdMonthly = vDpsEffectiveThreshold,
            partnerDpsDeductionThresholdMonthly = eDpsEffectiveThreshold,
            primaryHasIncome = vHasIncome,
            partnerHasIncome = eHasIncome,
            primaryGrossAnnual = vGrossAnnual,
            partnerGrossAnnual = eGrossAnnual
        )
    }

}
