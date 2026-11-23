package com.example.domain

import com.example.data.SettingsEntity
import com.example.domain.HouseholdCashFlowEngine.LivingCostSpec
import com.example.domain.HouseholdCashFlowEngine.annualPartnerTaxCredit
import com.example.domain.HouseholdCashFlowEngine.childMonthlyExpense
import com.example.domain.HouseholdCashFlowEngine.eleonoraSalaryMonthly
import com.example.domain.HouseholdCashFlowEngine.partnerBirthYear
import com.example.domain.HouseholdCashFlowEngine.totalLivingCostMonthly
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object FireTargetAndTrajectoryEngine {

    fun perpetualFireLeverageMultiplier(settings: SettingsEntity): Double {
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val buffer = (1.0 + settings.safetyBufferPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0)
        return ((12.0 / swr) * buffer).finiteNonNegative()
    }

    fun annuityFactor(rate: Double, years: Int): Double {
        if (years <= 0) return 0.0
        if (abs(rate) < 1e-9) return years.toDouble()
        if (rate <= -1.0) return 0.0
        val term = (1.0 + rate).pow(-years)
        if (term.isNaN() || term.isInfinite()) return years.toDouble()
        return (1.0 - term) / rate
    }

    fun netRealReturnRate(settings: SettingsEntity): Double {
        val netNominalFactor = 1.0 + netTaxableNominalReturnPct(settings) / 100.0
        val inflationFactor = (1.0 + settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.01)
        return ((netNominalFactor / inflationFactor) - 1.0).finiteOr(0.0)
    }

    internal fun goalsOutlayForYear(
        year: Int,
        parsedGoals: List<CustomLifeGoalItem>,
        hasGoals: Boolean
    ): Double {
        if (!hasGoals) return 0.0
        return parsedGoals
            .filter { it.targetYear == year }
            .sumOf { max(0.0, it.targetAmountCzk.finiteNonNegative() - it.currentSavedCzk.finiteNonNegative()) }
            .finiteNonNegative()
    }

    fun goalsOutlayForYear(year: Int, settings: SettingsEntity): Double {
        if (settings.customGoalsJson.isBlank() || settings.customGoalsJson.trim() == "[]") return 0.0
        return goalsOutlayForYear(year, parseCustomLifeGoals(settings.customGoalsJson), hasGoals = true)
    }

    fun projectionHorizonYears(settings: SettingsEntity): Int {
        val targetMilestoneAge = max(60, settings.vStatePensionAge)
        return (max(0, targetMilestoneAge - settings.primaryAge) + 3).coerceIn(15, 60)
    }

    fun computeBridgeTo60Required(
        annualLiving: Double,
        yearsTo60: Int,
        rReal: Double,
        totalFireTarget: Double
    ): Double {
        if (yearsTo60 <= 0) return 0.0
        val rawBridge = annualLiving.finiteNonNegative() * annuityFactor(rReal.finiteOr(0.0), yearsTo60)
        return if (totalFireTarget > 0.0 && totalFireTarget.isFinite()) {
            min(rawBridge, totalFireTarget).finiteNonNegative()
        } else {
            rawBridge.finiteNonNegative()
        }
    }

    fun baselineLivingCostAtFireMonthly(settings: SettingsEntity): Double {
        val overrideCost = settings.lifestyleCostAtFireMonthly.finiteOr(0.0)
        if (overrideCost > 0.0) {
            return overrideCost
        }
        val spec = LivingCostSpec.from(settings)
        var cost = spec.nonRentBaseMonthly + spec.baseRentMonthly
        if (settings.childExpensesEnabled && settings.currentChildCostsInBaseline) {
            if (settings.child1Enabled && settings.child1BirthYear <= settings.baseYear) {
                cost = max(0.0, cost - childMonthlyExpense(settings.child1BirthYear, settings.baseYear, settings))
            }
            if (settings.child2Enabled && settings.child2BirthYear <= settings.baseYear) {
                cost = max(0.0, cost - childMonthlyExpense(settings.child2BirthYear, settings.baseYear, settings))
            }
        }
        return cost.finiteNonNegative()
    }

    fun fireTargetBase(
        settings: SettingsEntity,
        age: Int = settings.primaryAge,
        partnerBirthYearOverride: Int? = null
    ): Double {
        val overrideTarget = settings.fireTargetOverride.finiteOr(0.0)
        if (overrideTarget > 0.0) return overrideTarget
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)

        val lifestyleMonthly = baselineLivingCostAtFireMonthly(settings)
        val annualLifestyle = max(0.0, lifestyleMonthly * 12.0)

        val vAnnualPension = max(0.0, settings.vStatePensionMonthly.finiteNonNegative() * 12.0)
        val eAnnualPension = if (!settings.isSingleHousehold) max(0.0, settings.eStatePensionMonthly.finiteNonNegative() * 12.0) else 0.0

        val retireCalYear = settings.baseYear + (age - settings.primaryAge)
        val eAgeAtRetire = retireCalYear - partnerBirthYear(settings, partnerBirthYearOverride)
        val vBridgeYears = max(0, settings.vStatePensionAge - age)
        val eBridgeYears = if (!settings.isSingleHousehold) max(0, settings.eStatePensionAge - eAgeAtRetire) else vBridgeYears

        val (b1, b2, p1Active) = if (vBridgeYears <= eBridgeYears) {
            Triple(vBridgeYears, eBridgeYears, vAnnualPension)
        } else {
            Triple(eBridgeYears, vBridgeYears, eAnnualPension)
        }

        // Phase 1: From age until first pension starts (b1 years)
        val costPhase1 = annualLifestyle * annuityFactor(swr, b1)

        // Phase 2: From b1 until second pension starts (b2 years)
        val shortfallPhase2 = max(0.0, annualLifestyle - p1Active)
        val costPhase2 = if (b2 > b1 && shortfallPhase2 > 0) {
            val annuity2 = shortfallPhase2 * annuityFactor(swr, b2 - b1)
            val discountFactor1 = (1.0 + swr).pow(b1)
            if (discountFactor1 > 0.0 && !discountFactor1.isNaN() && !discountFactor1.isInfinite()) {
                annuity2 / discountFactor1
            } else 0.0
        } else 0.0

        // Phase 3: Perpetuity after second pension starts (b2 years)
        val postAllPensionsShortfall = max(0.0, annualLifestyle - vAnnualPension - eAnnualPension)
        val targetCapitalPhase3 = if (postAllPensionsShortfall > 0) {
            val discountFactor2 = (1.0 + swr).pow(b2)
            if (discountFactor2 > 0.0 && !discountFactor2.isNaN() && !discountFactor2.isInfinite()) {
                (postAllPensionsShortfall / swr) / discountFactor2
            } else 0.0
        } else 0.0

        // Child support bridge during early retirement:
        // Uses age-tiered monthly costs from retirement year up to childSupportUntilAge.
        val childBridgeCapital = if (age < settings.vStatePensionAge && settings.childExpensesEnabled && settings.prolongChildSupportInFire) {
            fun ageTieredChildPv(enabled: Boolean, birthYear: Int): Double {
                if (!enabled) return 0.0
                val endCalYear = birthYear + settings.childSupportUntilAge
                if (endCalYear <= retireCalYear) return 0.0
                val spanYears = endCalYear - retireCalYear
                var pv = 0.0
                for (offset in 0 until spanYears) {
                    val calYear = retireCalYear + offset
                    val monthlyCost = childMonthlyExpense(birthYear, calYear, settings)
                    if (monthlyCost > 0.0) {
                        val disc = (1.0 + swr).pow(offset + 1)
                        if (disc.isFinite() && disc > 0.0) {
                            pv += (monthlyCost * 12.0) / disc
                        }
                    }
                }
                return pv
            }
            ageTieredChildPv(settings.child1Enabled, settings.child1BirthYear) +
                ageTieredChildPv(settings.child2Enabled, settings.child2BirthYear)
        } else 0.0

        val safetyBuffer = (1.0 + settings.safetyBufferPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0)
        val total = (costPhase1 + costPhase2 + targetCapitalPhase3 + childBridgeCapital) * safetyBuffer
        return total.finiteNonNegative()
    }

    fun fireTargetYear(
        year: Int,
        settings: SettingsEntity,
        age: Int = settings.primaryAge + (year - settings.baseYear),
        partnerBirthYearOverride: Int? = null
    ): Double {
        val yearsElapsed = max(0, year - settings.baseYear)
        val cpiFactor = (1.0 + settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0).pow(yearsElapsed)
        val hasTargetOverride = settings.fireTargetOverride.finiteOr(0.0) > 0.0
        val hasLifestyleOverride = settings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0
        val baseTarget = if (!hasTargetOverride && !hasLifestyleOverride && yearsElapsed > 0 && cpiFactor.isFinite() && cpiFactor > 0.0) {
            val spec = LivingCostSpec.from(settings)
            val rentGrowthRate = settings.rentGrowthPct.finiteOr(0.0) / 100.0
            if (spec.baseRentMonthly > 0.0 && abs(settings.rentGrowthPct.finiteOr(0.0) - settings.cpiInflationPct.finiteOr(0.0)) > 1e-9) {
                val rentFactor = (1.0 + rentGrowthRate).coerceAtLeast(0.0).pow(yearsElapsed)
                val nonRentBase = max(0.0, baselineLivingCostAtFireMonthly(settings) - spec.baseRentMonthly)
                val realRentAtYear = spec.baseRentMonthly * (rentFactor / cpiFactor)
                fireTargetBase(
                    settings = settings.copy(lifestyleCostAtFireMonthly = nonRentBase + realRentAtYear),
                    age = age,
                    partnerBirthYearOverride = partnerBirthYearOverride
                )
            } else {
                fireTargetBase(settings, age, partnerBirthYearOverride)
            }
        } else {
            fireTargetBase(settings, age, partnerBirthYearOverride)
        }
        val result = baseTarget * cpiFactor
        return if (result.isFinite()) max(0.0, result) else baseTarget
    }

    internal data class PreFireAnnualCashflow(
        val isBaristaActive: Boolean,
        val baseAnnual: Double,
        val reinvestAnnual: Double,
        val taxRefundAnnual: Double,
        val lumpAnnual: Double,
        val goalsOutlayAnnual: Double,
        val baristaShortfallAnnual: Double,
        val liquidNetInflow: Double,
        val dipInflowAnnual: Double,
        val dpsInflowAnnual: Double,
        val inflationFactor: Double,
        val rentFactor: Double,
        val annualLivingAtTargetYear: Double,
        val unallocatedCashSurplusAnnual: Double = 0.0,
        val cashflowDeficitAnnual: Double = 0.0,
        val vRetiredByAge: Boolean = false
    )

    internal fun buildPreFirePlan(
        settings: SettingsEntity,
        includeSpouse: Boolean,
        yearsCount: Int,
        partnerBirthYearOverride: Int? = null
    ): Array<PreFireAnnualCashflow> {
        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val partnerBirth = partnerBirthYear(settings, partnerBirthYearOverride)
        val cpiRate = (settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(-0.99)
        val dcaGrowthPct = settings.dcaAnnualGrowthPct.finiteOr(0.0)
        val empCap = settings.employerRetirementExemptionAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL
        ) / 12.0
        val vEmpEffective = min(settings.employerRetirementMonthly.finiteNonNegative(), empCap)
        val eEmpEffective = if (includeSpouse) min(settings.eEmployerRetirementMonthly.finiteNonNegative(), empCap) else 0.0
        val ePortu = if (includeSpouse) settings.ePortuDcaMonthly.finiteNonNegative() else 0.0
        val eDpsOwn = if (includeSpouse) settings.eDpsOwnContributionMonthly.finiteNonNegative() else 0.0
        val eDipOwn = if (includeSpouse) settings.eDipContributionMonthly.finiteNonNegative() else 0.0
        val eReinvestFraction = (settings.eReinvestedPct.finiteIn(0.0, 100.0) / 100.0)
        val livingSpec = LivingCostSpec.from(settings)
        val parsedLumpSums = parseCustomLumpSums(settings.customLumpSumsJson)
        val hasGoals = settings.customGoalsJson.isNotBlank() && settings.customGoalsJson.trim() != "[]"
        val parsedGoals = if (hasGoals) parseCustomLifeGoals(settings.customGoalsJson) else emptyList()
        val baseEmbeddedChildCredit = CzechTaxEngine.baseYearChildCreditAnnual(settings)
        val dpsSubThresholdMonthly = settings.dpsDeductionThresholdMonthly.finiteNonNegative(
            RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026
        )

        return Array(yearsCount) { y ->
            val sy = baseYear + y
            val targetYear = sy + 1
            val ageAtStart = baseAge + y
            val eAgeAtStart = sy - partnerBirth
            val yearsElapsed = y + 1
            val inflationFactor = (1.0 + cpiRate).coerceAtLeast(0.0).pow(yearsElapsed).finiteOr(1.0)
            val inflationAtStartFactor = (1.0 + cpiRate).coerceAtLeast(0.0).pow(y).finiteOr(1.0)
            val rentFactor = if (livingSpec.rentGrowthPct != 0.0) {
                (1.0 + livingSpec.rentGrowthPct / 100.0).coerceAtLeast(0.0).pow(yearsElapsed).finiteOr(1.0)
            } else 1.0
            val annualLivingAtTargetYear = livingSpec.annualLivingAtProjectedYear(
                settings = settings,
                targetYear = targetYear,
                inflationFactor = inflationFactor,
                rentFactor = rentFactor
            )

            val vRetiredByAge = ageAtStart >= settings.vStatePensionAge
            val eRetiredByAge = !includeSpouse || eAgeAtStart >= settings.eStatePensionAge
            // Barista activates during the configured calendar year `sy >= baristaStartYear` (never 1 year early)
            val isBaristaActive = !vRetiredByAge && settings.baristaTransitionEnabled && sy >= settings.baristaStartYear
            val dcaFactor = if (dcaGrowthPct != 0.0) {
                (1.0 + dcaGrowthPct / 100.0).coerceAtLeast(0.0).pow(y).finiteOr(1.0)
            } else 1.0

            val plannedBaseAnnual = when {
                vRetiredByAge -> 0.0
                isBaristaActive -> settings.baristaSavingsMonthly.finiteNonNegative() * 12.0 * inflationFactor
                else -> (settings.portuDcaMonthly.finiteNonNegative() + ePortu) * 12.0 * dcaFactor
            }
            val plannedReinvestAnnual = if (!vRetiredByAge && !isBaristaActive && includeSpouse && !eRetiredByAge && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * eReinvestFraction * 12.0
            } else 0.0

            val taxRefundAnnual = when {
                vRetiredByAge -> 0.0
                isBaristaActive -> {
                    val baristaSettings = settings.copy(dipContributionMonthly = 0.0, dpsOwnContributionMonthly = 0.0)
                    val baristaVGross = CzechTaxEngine.netToGrossAnnual(
                        netMonthly = settings.baristaMonthlyIncome.finiteNonNegative() * inflationFactor,
                        taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                        highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                        baseTaxRatePct = settings.taxRatePct,
                        highTaxRatePct = settings.taxRateSecondPct
                    )
                    val partnerRefund = if (includeSpouse) annualPartnerTaxCredit(baristaSettings, sy, vGrossOverride = baristaVGross) else 0.0
                    val retirementSaved = if (includeSpouse) {
                        CzechTaxEngine.annualRetirementTaxSavedForHousehold(
                            baristaSettings,
                            sy,
                            includeSpouse = true,
                            vGrossOverride = baristaVGross,
                            partnerBirthYearOverride = partnerBirthYearOverride
                        )
                    } else 0.0
                    val childRefund = CzechTaxEngine.annualChildTaxRelief(
                        baristaSettings,
                        sy,
                        vGrossAnnualOverride = baristaVGross,
                        vSpouseCreditAnnual = partnerRefund,
                        partnerBirthYearOverride = partnerBirthYearOverride
                    )
                    retirementSaved + partnerRefund + childRefund
                }
                else -> {
                    val partnerRefund = if (includeSpouse) annualPartnerTaxCredit(settings, sy) else 0.0
                    val retirementSaved = CzechTaxEngine.annualRetirementTaxSavedForHousehold(
                        settings, sy, includeSpouse, partnerBirthYearOverride = partnerBirthYearOverride
                    )
                    val yearChildCredit = CzechTaxEngine.annualChildTaxRelief(
                        settings,
                        sy,
                        vSpouseCreditAnnual = partnerRefund,
                        partnerBirthYearOverride = partnerBirthYearOverride
                    )
                    // Base-year child tax credit is already received inside `vSalary` monthly; only new incremental relief adds cash.
                    val childCreditDelta = max(0.0, yearChildCredit - baseEmbeddedChildCredit)
                    retirementSaved + partnerRefund + childCreditDelta
                }
            }

            // Full household net take-home cash inflow in year `sy` (for solvency & Barista shortfall)
            val vNetAnnual = when {
                vRetiredByAge -> settings.vStatePensionMonthly.finiteNonNegative() * 12.0 * inflationAtStartFactor
                isBaristaActive -> settings.baristaMonthlyIncome.finiteNonNegative() * 12.0 * inflationFactor
                else -> HouseholdCashFlowEngine.vaclavSalaryMonthly(sy, settings) * 12.0
            }
            val vVouchersAnnual = if (isBaristaActive || vRetiredByAge) 0.0 else settings.vMealVouchersMonthly.finiteNonNegative() * 12.0
            val eNetAnnual = if (includeSpouse) {
                if (eRetiredByAge) {
                    settings.eStatePensionMonthly.finiteNonNegative() * 12.0 * inflationAtStartFactor
                } else {
                    eleonoraSalaryMonthly(sy, settings) * 12.0 + HouseholdCashFlowEngine.eleonoraLecturingMonthly(sy, settings) * 12.0
                }
            } else 0.0
            val eBenefitAnnual = HouseholdCashFlowEngine.eleonoraBenefitMonthly(sy, settings) * 12.0
            val giftsAndOtherAnnual = (
                settings.vGiftsMonthly.finiteNonNegative() +
                    settings.vOtherInflowsMonthly.finiteNonNegative() +
                    if (includeSpouse) settings.eGiftsMonthly.finiteNonNegative() + settings.eOtherInflowsMonthly.finiteNonNegative() else 0.0
                ) * 12.0
            val totalHouseholdNetIncomeAnnual = vNetAnnual + vVouchersAnnual + eNetAnnual + eBenefitAnnual + giftsAndOtherAnnual

            val baristaShortfallAnnual = if (isBaristaActive) {
                max(0.0, annualLivingAtTargetYear - totalHouseholdNetIncomeAnnual - taxRefundAnnual)
            } else 0.0

            val plannedVDpsOwn = if (isBaristaActive || vRetiredByAge) 0.0 else settings.dpsOwnContributionMonthly.finiteNonNegative() * 12.0
            val plannedVDipOwn = if (isBaristaActive || vRetiredByAge) 0.0 else settings.dipContributionMonthly.finiteNonNegative() * 12.0
            val plannedEDpsOwn = if (!vRetiredByAge && !eRetiredByAge && includeSpouse) eDpsOwn * 12.0 else 0.0
            val plannedEDipOwn = if (!vRetiredByAge && !eRetiredByAge && includeSpouse) eDipOwn * 12.0 else 0.0

            val availableForOwnInvesting = totalHouseholdNetIncomeAnnual + taxRefundAnnual - annualLivingAtTargetYear
            val plannedLiquidOwn = plannedBaseAnnual + plannedReinvestAnnual + taxRefundAnnual
            val plannedDpsOwnTotal = plannedVDpsOwn + plannedEDpsOwn
            val plannedDipOwnTotal = plannedVDipOwn + plannedEDipOwn
            val plannedOwnTotal = plannedLiquidOwn + plannedDpsOwnTotal + plannedDipOwnTotal

            val actualBaseAnnual: Double
            val actualReinvestAnnual: Double
            val actualTaxRefundInvested: Double
            val actualVDpsOwn: Double
            val actualEDpsOwn: Double
            val actualVDipOwn: Double
            val actualEDipOwn: Double
            val unallocatedCashSurplusAnnual: Double
            val cashflowDeficitAnnual: Double

            when {
                availableForOwnInvesting >= plannedOwnTotal -> {
                    actualBaseAnnual = plannedBaseAnnual
                    actualReinvestAnnual = plannedReinvestAnnual
                    actualTaxRefundInvested = taxRefundAnnual
                    actualVDpsOwn = plannedVDpsOwn
                    actualEDpsOwn = plannedEDpsOwn
                    actualVDipOwn = plannedVDipOwn
                    actualEDipOwn = plannedEDipOwn
                    unallocatedCashSurplusAnnual = availableForOwnInvesting - plannedOwnTotal
                    cashflowDeficitAnnual = 0.0
                }
                availableForOwnInvesting > 0.0 -> {
                    var rem = availableForOwnInvesting
                    val vDpsSubsidyTier = min(plannedVDpsOwn, dpsSubThresholdMonthly * 12.0)
                    actualVDpsOwn = min(rem, vDpsSubsidyTier); rem -= actualVDpsOwn
                    val eDpsSubsidyTier = min(plannedEDpsOwn, dpsSubThresholdMonthly * 12.0)
                    actualEDpsOwn = min(rem, eDpsSubsidyTier); rem -= actualEDpsOwn
                    actualVDipOwn = min(rem, plannedVDipOwn); rem -= actualVDipOwn
                    actualEDipOwn = min(rem, plannedEDipOwn); rem -= actualEDipOwn
                    val liquidScale = if (plannedLiquidOwn > 0.0) min(1.0, rem / plannedLiquidOwn) else 0.0
                    actualBaseAnnual = plannedBaseAnnual * liquidScale
                    actualReinvestAnnual = plannedReinvestAnnual * liquidScale
                    actualTaxRefundInvested = taxRefundAnnual * liquidScale
                    unallocatedCashSurplusAnnual = 0.0
                    cashflowDeficitAnnual = 0.0
                }
                else -> {
                    actualBaseAnnual = 0.0
                    actualReinvestAnnual = 0.0
                    actualTaxRefundInvested = 0.0
                    actualVDpsOwn = 0.0
                    actualEDpsOwn = 0.0
                    actualVDipOwn = 0.0
                    actualEDipOwn = 0.0
                    unallocatedCashSurplusAnnual = 0.0
                    cashflowDeficitAnnual = -availableForOwnInvesting
                }
            }

            val lumpAnnual = lumpSumForYear(sy, settings, parsedLumpSums)
            val goalsOutlayAnnual = goalsOutlayForYear(sy, parsedGoals, hasGoals)
            val liquidNetInflow = actualBaseAnnual + actualReinvestAnnual + actualTaxRefundInvested + lumpAnnual - goalsOutlayAnnual

            val subV = CzechTaxEngine.dpsSubsidy(
                actualVDpsOwn / 12.0,
                ageAtStart,
                settings,
                sy,
                settings.vStatePensionAge
            ) * 12.0
            val subE = if (includeSpouse) {
                CzechTaxEngine.dpsSubsidy(
                    actualEDpsOwn / 12.0,
                    eAgeAtStart,
                    settings,
                    sy,
                    settings.eStatePensionAge
                ) * 12.0
            } else 0.0
            val vEmpEffectiveNow = if (isBaristaActive || vRetiredByAge) 0.0 else vEmpEffective
            val eEmpEffectiveNow = if (vRetiredByAge || eRetiredByAge || !includeSpouse) 0.0 else eEmpEffective
            val vEmpToDip = if (plannedVDpsOwn <= 0.0 && plannedVDipOwn > 0.0) vEmpEffectiveNow else 0.0
            val vEmpToDps = vEmpEffectiveNow - vEmpToDip
            val eEmpToDip = if (plannedEDpsOwn <= 0.0 && plannedEDipOwn > 0.0) eEmpEffectiveNow else 0.0
            val eEmpToDps = eEmpEffectiveNow - eEmpToDip
            val dpsInflowAnnual = actualVDpsOwn + actualEDpsOwn + (vEmpToDps + eEmpToDps) * 12.0 + subV + subE
            val dipInflowAnnual = actualVDipOwn + actualEDipOwn + (vEmpToDip + eEmpToDip) * 12.0

            PreFireAnnualCashflow(
                isBaristaActive = isBaristaActive,
                baseAnnual = actualBaseAnnual,
                reinvestAnnual = actualReinvestAnnual,
                taxRefundAnnual = actualTaxRefundInvested,
                lumpAnnual = lumpAnnual,
                goalsOutlayAnnual = goalsOutlayAnnual,
                baristaShortfallAnnual = baristaShortfallAnnual,
                liquidNetInflow = liquidNetInflow,
                dipInflowAnnual = dipInflowAnnual,
                dpsInflowAnnual = dpsInflowAnnual,
                inflationFactor = inflationFactor,
                rentFactor = rentFactor,
                annualLivingAtTargetYear = annualLivingAtTargetYear,
                unallocatedCashSurplusAnnual = unallocatedCashSurplusAnnual,
                cashflowDeficitAnnual = cashflowDeficitAnnual,
                vRetiredByAge = vRetiredByAge
            )
        }
    }

    fun buildLiquidPortfolio(
        settings: SettingsEntity,
        dualIncome: Boolean,
        initialCrashPct: Double = 0.0,
        partnerBirthYearOverride: Int? = null
    ): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val partnerBirth = partnerBirthYear(settings, partnerBirthYearOverride)
        val horizon = projectionHorizonYears(settings)
        val ret = (netTaxableNominalReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val includeSpouse = dualIncome && !settings.isSingleHousehold
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val crashMult = max(0.0, 1.0 - initialCrashPct.finiteNonNegative())
        var bal = (settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid) * crashMult
        val initialTarget = fireTargetYear(sy, settings, age0, partnerBirthYearOverride)

        val eDpsBal = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDipBal = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        var dpsBal = (settings.dpsBalanceCurrent.finiteNonNegative() + eDpsBal) * crashMult
        var dipBal = (settings.dipBalanceCurrent.finiteNonNegative() + eDipBal) * crashMult
        var pensionBal = dpsBal + dipBal

        val rReal = netRealReturnRate(settings)
        val hasLifestyleOverride = settings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0
        val annualLiving0 = if (hasLifestyleOverride) {
            settings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0
        } else {
            totalLivingCostMonthly(settings) * 12.0
        }
        val yearsTo60_0 = max(0, 60 - age0)
        val bridgeReq0 = computeBridgeTo60Required(annualLiving0, yearsTo60_0, rReal, initialTarget)
        val bridgeFunded0 = if (yearsTo60_0 == 0) true else bal >= bridgeReq0
        val reserveCurrent = settings.emergencyReserveCurrent.finiteNonNegative()
        var projectedReserve = reserveCurrent
        val cpiRate = (settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(-0.99)
        val livingSpec = LivingCostSpec.from(settings)

        val initStatus = when {
            (bal + pensionBal) >= initialTarget -> {
                if (age0 >= 60 || bridgeFunded0) "FIRE OK" else "Bridge Constrained"
            }
            else -> "Growing"
        }

        list.add(
            PortfolioYearPoint(
                year = sy,
                age = age0,
                portfolio = bal,
                target = initialTarget,
                investedAnnual = 0.0,
                reinvestAnnual = 0.0,
                lumpSum = 0.0,
                status = initStatus,
                pensionPortfolio = pensionBal,
                totalPortfolio = bal + pensionBal,
                liquidBridgeTo60Required = bridgeReq0,
                isLiquidBridgeFunded = bridgeFunded0,
                dipPortfolio = dipBal,
                dpsPortfolio = dpsBal,
                emergencyCash = reserveCurrent
            )
        )

        val dpsRet = (DpsDipEngine.effectiveDpsNetReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val preFirePlan = buildPreFirePlan(settings, includeSpouse, yearsCount = horizon, partnerBirthYearOverride = partnerBirthYearOverride)
        var isRetired = initStatus == "FIRE OK" || age0 >= settings.vStatePensionAge

        for (y in 0 until horizon) {
            val plan = preFirePlan[y]
            val targetYear = sy + y + 1
            val age = age0 + y + 1
            val ageAtStart = age0 + y
            val eAgeAtStart = (sy + y) - partnerBirth

            val investedDisplay: Double
            val reinvestDisplay: Double
            val pensionInflowDisplay: Double

            if (isRetired || plan.vRetiredByAge) {
                // Post-FIRE / post-pension decumulation: stop active salary DCA and compound remaining assets minus withdrawals
                if (ageAtStart >= 60 && (dipBal > 0.0 || dpsBal > 0.0)) {
                    bal += dipBal + dpsBal
                    dipBal = 0.0
                    dpsBal = 0.0
                }
                val retireLivingAnnual = if (hasLifestyleOverride) {
                    val childExtra = if (settings.childExpensesEnabled && settings.prolongChildSupportInFire) {
                        val c1 = if (settings.child1Enabled) childMonthlyExpense(settings.child1BirthYear, targetYear, settings) else 0.0
                        val c2 = if (settings.child2Enabled) childMonthlyExpense(settings.child2BirthYear, targetYear, settings) else 0.0
                        (c1 + c2) * 12.0 * plan.inflationFactor
                    } else 0.0
                    settings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0 * plan.inflationFactor + childExtra
                } else if (settings.prolongChildSupportInFire) {
                    plan.annualLivingAtTargetYear
                } else {
                    val nonRentBase = max(0.0, baselineLivingCostAtFireMonthly(settings) - livingSpec.baseRentMonthly)
                    (nonRentBase * plan.inflationFactor + livingSpec.baseRentMonthly * plan.rentFactor) * 12.0
                }
                val vPen = if (ageAtStart >= settings.vStatePensionAge) {
                    settings.vStatePensionMonthly.finiteNonNegative() * 12.0 * plan.inflationFactor
                } else 0.0
                val ePen = if (includeSpouse && eAgeAtStart >= settings.eStatePensionAge) {
                    settings.eStatePensionMonthly.finiteNonNegative() * 12.0 * plan.inflationFactor
                } else 0.0
                val netDraw = max(0.0, retireLivingAnnual - vPen - ePen + plan.goalsOutlayAnnual - plan.lumpAnnual)
                if (bal >= netDraw) {
                    bal -= netDraw
                } else {
                    val shortfall = netDraw - bal
                    bal = 0.0
                    projectedReserve = max(0.0, projectedReserve - shortfall)
                }
                bal = max(0.0, bal * max(0.0, 1.0 + ret))
                dipBal = max(0.0, dipBal * max(0.0, 1.0 + ret))
                dpsBal = max(0.0, dpsBal * max(0.0, 1.0 + dpsRet))
                projectedReserve = max(0.0, projectedReserve * (1.0 + cpiRate).coerceAtLeast(0.0))
                pensionBal = dpsBal + dipBal

                investedDisplay = 0.0
                reinvestDisplay = 0.0
                pensionInflowDisplay = 0.0
            } else {
                val deficitFromReserve = min(projectedReserve, plan.cashflowDeficitAnnual)
                projectedReserve -= deficitFromReserve
                val residualDeficit = plan.cashflowDeficitAnnual - deficitFromReserve
                bal = max(0.0, bal - residualDeficit)
                val eqFactor = max(0.0, 1.0 + ret)
                val liquidFlowEffect = if (plan.liquidNetInflow >= 0.0) {
                    plan.liquidNetInflow * (1.0 + ret * 0.5)
                } else {
                    plan.liquidNetInflow
                }
                bal = max(0.0, bal * eqFactor + liquidFlowEffect)
                dpsBal = max(0.0, dpsBal * max(0.0, 1.0 + dpsRet) + plan.dpsInflowAnnual * (1.0 + dpsRet * 0.5))
                dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * (1.0 + ret * 0.5))
                pensionBal = dpsBal + dipBal
                projectedReserve = max(
                    0.0,
                    projectedReserve * (1.0 + cpiRate).coerceAtLeast(0.0) + plan.unallocatedCashSurplusAnnual
                )

                investedDisplay = plan.baseAnnual + plan.taxRefundAnnual
                reinvestDisplay = plan.reinvestAnnual
                pensionInflowDisplay = plan.dpsInflowAnnual + plan.dipInflowAnnual
            }

            val t = fireTargetYear(targetYear, settings, age, partnerBirthYearOverride)
            val totalBal = bal + pensionBal
            val gap = t - totalBal

            val yearsTo60 = max(0, 60 - age)
            val annualLivingForBridge = if (hasLifestyleOverride) {
                settings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0 * plan.inflationFactor
            } else {
                plan.annualLivingAtTargetYear
            }
            val bridgeReq = computeBridgeTo60Required(annualLivingForBridge, yearsTo60, rReal, t)
            val bridgeFunded = if (yearsTo60 == 0) true else bal >= bridgeReq

            val status = when {
                isRetired -> if (totalBal > 0.0) "FIRE OK" else "Depleted"
                totalBal >= t -> {
                    if (age >= 60 || bridgeFunded) {
                        isRetired = true
                        "FIRE OK"
                    } else {
                        "Bridge Constrained"
                    }
                }
                plan.isBaristaActive -> "Barista Active"
                gap < t * 0.1 -> "Close"
                else -> "Growing"
            }

            list.add(
                PortfolioYearPoint(
                    year = targetYear,
                    age = age,
                    portfolio = bal,
                    target = t,
                    investedAnnual = investedDisplay,
                    reinvestAnnual = reinvestDisplay,
                    lumpSum = plan.lumpAnnual,
                    status = status,
                    pensionPortfolio = pensionBal,
                    totalPortfolio = totalBal,
                    liquidBridgeTo60Required = bridgeReq,
                    isLiquidBridgeFunded = bridgeFunded,
                    pensionInflowAnnual = pensionInflowDisplay,
                    dipPortfolio = dipBal,
                    dpsPortfolio = dpsBal,
                    emergencyCash = projectedReserve.finiteNonNegative()
                )
            )
        }
        return list
    }

    fun dividendTaxDragPct(settings: SettingsEntity): Double {
        val yieldPct = settings.dividendYieldPct.finiteNonNegative()
        val ratePct = settings.dividendTaxRatePct.finiteIn(0.0, 100.0)
        return (yieldPct * ratePct / 100.0).finiteNonNegative()
    }

    fun netTaxableNominalReturnPct(settings: SettingsEntity): Double {
        val drag = dividendTaxDragPct(settings)
        return max(0.0, settings.portfolioNominalReturnPct.finiteOr(0.0) - drag)
    }

    fun calculateStressScenarios(
        settings: SettingsEntity,
        runMonteCarlo: Boolean = true,
        partnerBirthYearOverride: Int? = null
    ): List<StressScenarioResult> {
        val baseLivingCost = totalLivingCostMonthly(settings)

        val configs = listOf(
            Triple("baseline", "Baseline Plan", "" to "Current baseline parameters (7% return, 3% CPI)"),
            Triple("bull", "Bull Expansion", "" to "High market growth (9% return, 2% CPI)"),
            Triple("stagflation", "Stagflation Bear", "" to "Low growth & high inflation (4.5% return, 5% CPI, 6% rent growth)"),
            Triple("crash", "Year-1 Crash (-25%)", "" to "Immediate 25% market drawdown in Year 1, then standard growth"),
            Triple("inflation_shock", "High Inflation Spike", "" to "Persistent high inflation (6.5% return, 6% CPI, 7% rent growth)")
        )

        return configs.map { (id, name, pair) ->
            val (icon, desc) = pair
            val retPct = when (id) {
                "bull" -> 9.0
                "stagflation" -> 4.5
                "inflation_shock" -> 6.5
                else -> settings.portfolioNominalReturnPct
            }
            val cpiPct = when (id) {
                "bull" -> 2.0
                "stagflation" -> 5.0
                "inflation_shock" -> 6.0
                else -> settings.cpiInflationPct
            }
            val rentGrowth = when (id) {
                "stagflation" -> 6.0
                "inflation_shock" -> 7.0
                else -> settings.rentGrowthPct
            }
            val swr = when (id) {
                "stagflation", "crash" -> 3.5
                else -> settings.safeWithdrawalRatePct
            }

            val mockSettings = settings.copy(
                portfolioNominalReturnPct = retPct,
                cpiInflationPct = cpiPct,
                safeWithdrawalRatePct = swr,
                rentGrowthPct = rentGrowth
            )

            val fireTarget = fireTargetBase(mockSettings, partnerBirthYearOverride = partnerBirthYearOverride)
            val trajectory = if (id == "crash") {
                buildLiquidPortfolioWithInitialCrash(mockSettings, crashPct = 0.25, partnerBirthYearOverride = partnerBirthYearOverride)
            } else {
                buildLiquidPortfolio(mockSettings, !mockSettings.isSingleHousehold, partnerBirthYearOverride = partnerBirthYearOverride)
            }

            val firePoint = trajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }
                ?: trajectory.firstOrNull { it.totalPortfolio >= it.target }
            val successRate = if (runMonteCarlo) {
                MonteCarloEngine.runMonteCarlo(
                    mockSettings,
                    initialCrashPct = if (id == "crash") 0.25 else 0.0,
                    partnerBirthYearOverride = partnerBirthYearOverride
                ).successRatePct
            } else 0.0

            val effectiveLivingCost = if (cpiPct > settings.cpiInflationPct) {
                baseLivingCost * (1.0 + (cpiPct - settings.cpiInflationPct) / 100.0)
            } else baseLivingCost

            val emergencySurvival = if (effectiveLivingCost > 0) max(0.0, settings.emergencyReserveCurrent / effectiveLivingCost) else 0.0
            val pointAt60 = trajectory.firstOrNull { it.age >= 60 } ?: trajectory.lastOrNull()
            val nwAt60 = pointAt60?.totalPortfolio ?: 0.0

            StressScenarioResult(
                id = id,
                name = name,
                iconEmoji = icon,
                description = desc,
                nominalReturnPct = retPct,
                cpiInflationPct = cpiPct,
                swrPct = swr,
                rentGrowthPct = rentGrowth,
                fireTargetToday = fireTarget,
                fireAge = firePoint?.age,
                fireYear = firePoint?.year,
                successRatePct = successRate,
                emergencySurvivalMonths = emergencySurvival,
                netWorthAt60 = nwAt60,
                trajectory = trajectory
            )
        }
    }

    fun buildLiquidPortfolioWithInitialCrash(
        settings: SettingsEntity,
        crashPct: Double,
        partnerBirthYearOverride: Int? = null
    ): List<PortfolioYearPoint> =
        buildLiquidPortfolio(settings, !settings.isSingleHousehold, initialCrashPct = crashPct, partnerBirthYearOverride = partnerBirthYearOverride)


    internal fun buildFireMilestones(
        settings: SettingsEntity,
        fireBase: Double,
        activeTrajectory: List<PortfolioYearPoint>,
        activeFireYear: Int?,
        activeFireAge: Int?,
        isFireAchievedToday: Boolean,
        snapLiquid: Double,
        snapPension: Double,
        bridgeReqToday: Double,
        yearsTo60Today: Int,
        partnerBirthYearOverride: Int? = null
    ): FireMilestonesSummary {
        val investableNetWorth = snapLiquid + snapPension
        val currentLiquidBal = snapLiquid
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val realReturnRate = netRealReturnRate(settings)
        val yearsToRetire = max(0, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)
        val milestoneSettings = settings.copy(fireTargetOverride = 0.0)
        val effectiveBaseLiving = baselineLivingCostAtFireMonthly(milestoneSettings)

        val targetAtPensionAge = fireTargetBase(milestoneSettings, age = settings.vStatePensionAge, partnerBirthYearOverride = partnerBirthYearOverride)
        val coastRawTarget = when {
            yearsToRetire <= 0 -> targetAtPensionAge
            realReturnRate > 0.0 -> targetAtPensionAge / (1.0 + realReturnRate).pow(yearsToRetire)
            else -> targetAtPensionAge
        }
        val coastTarget = if (coastRawTarget.isFinite()) max(0.0, kotlin.math.round(coastRawTarget / 10_000.0) * 10_000.0) else 0.0
        val coastProgress = if (coastTarget > 0.0 && coastTarget.isFinite()) ((investableNetWorth / coastTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val coastAchieved = realReturnRate > 0.0 && investableNetWorth >= coastTarget
        val coastPoint = if (coastAchieved) activeTrajectory.firstOrNull() else if (realReturnRate > 0.0) activeTrajectory.firstOrNull { point ->
            val remainingYears = max(0, settings.vStatePensionAge - point.age)
            val requiredCapitalAtPoint = targetAtPensionAge / (1.0 + realReturnRate).pow(remainingYears)
            val yDiff = point.year - settings.baseYear
            val futureTarget = requiredCapitalAtPoint * cpiCompounding.pow(yDiff)
            point.totalPortfolio >= futureTarget
        } else null
        val coastMilestone = FireMilestone(
            id = "coast",
            name = "Coast FIRE",
            badgeLabel = "Compound Only",
            description = if (realReturnRate <= 0.0) {
                "Negative real return; portfolio cannot coast to FIRE target without ongoing contributions."
            } else {
                "Existing investments grow to full FIRE target by age ${settings.vStatePensionAge} with 0 additional contributions."
            },
            targetAmountToday = coastTarget,
            monthlyPassiveIncome = kotlin.math.round(((targetAtPensionAge * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = coastProgress,
            isAchieved = coastAchieved,
            estimatedAge = if (coastAchieved) settings.primaryAge else coastPoint?.age,
            estimatedYear = if (coastAchieved) settings.baseYear else coastPoint?.year
        )

        val baristaRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50), partnerBirthYearOverride = partnerBirthYearOverride)
        val baristaTarget = if (baristaRawTarget.isFinite()) max(0.0, kotlin.math.round(baristaRawTarget / 10_000.0) * 10_000.0) else 0.0
        val baristaProgress = if (baristaTarget > 0.0 && baristaTarget.isFinite()) ((investableNetWorth / baristaTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val baristaBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 0.50
        val baristaAchieved = investableNetWorth >= baristaTarget && baristaBridgeFundedToday
        val baristaPoint = if (baristaAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50), point.age, partnerBirthYearOverride)
            val pointBridgeOk = point.age >= 60 || point.portfolio >= point.liquidBridgeTo60Required * 0.50
            point.totalPortfolio >= futureTarget && pointBridgeOk
        }
        val baristaMilestone = FireMilestone(
            id = "barista",
            name = "Barista FIRE",
            badgeLabel = "Semi-Retired",
            description = "Covers 50% of current living expenses from portfolio; remainder covered by part-time, seasonal, or freelance work.",
            targetAmountToday = baristaTarget,
            monthlyPassiveIncome = kotlin.math.round(((baristaTarget * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = baristaProgress,
            isAchieved = baristaAchieved,
            estimatedAge = if (baristaAchieved) settings.primaryAge else baristaPoint?.age,
            estimatedYear = if (baristaAchieved) settings.baseYear else baristaPoint?.year
        )

        val leanRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75), partnerBirthYearOverride = partnerBirthYearOverride)
        val leanTarget = if (leanRawTarget.isFinite()) max(0.0, kotlin.math.round(leanRawTarget / 10_000.0) * 10_000.0) else 0.0
        val leanProgress = if (leanTarget > 0.0 && leanTarget.isFinite()) ((investableNetWorth / leanTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val leanBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 0.75
        val leanAchieved = investableNetWorth >= leanTarget && leanBridgeFundedToday
        val leanPoint = if (leanAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75), point.age, partnerBirthYearOverride)
            val pointBridgeOk = point.age >= 60 || point.portfolio >= point.liquidBridgeTo60Required * 0.75
            point.totalPortfolio >= futureTarget && pointBridgeOk
        }
        val leanMilestone = FireMilestone(
            id = "lean",
            name = "Lean FIRE",
            badgeLabel = "Essential Baseline",
            description = "Covers essential living costs (75% budget), basic housing, and groceries indefinitely.",
            targetAmountToday = leanTarget,
            monthlyPassiveIncome = kotlin.math.round(((leanTarget * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = leanProgress,
            isAchieved = leanAchieved,
            estimatedAge = if (leanAchieved) settings.primaryAge else leanPoint?.age,
            estimatedYear = if (leanAchieved) settings.baseYear else leanPoint?.year
        )

        val standardProgress = if (fireBase > 0.0 && fireBase.isFinite()) ((investableNetWorth / fireBase) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val standardMilestone = FireMilestone(
            id = "standard",
            name = "Standard FIRE",
            badgeLabel = "Full Independence",
            description = "Covers 100% of current comfortable household lifestyle without needing employment income.",
            targetAmountToday = fireBase,
            monthlyPassiveIncome = (fireBase * swr) / 12.0,
            progressPct = standardProgress,
            isAchieved = isFireAchievedToday,
            estimatedAge = activeFireAge,
            estimatedYear = activeFireYear
        )

        val fatRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30), partnerBirthYearOverride = partnerBirthYearOverride)
        val fatTarget = if (fatRawTarget.isFinite()) max(0.0, kotlin.math.round(fatRawTarget / 10_000.0) * 10_000.0) else 0.0
        val fatProgress = if (fatTarget > 0.0 && fatTarget.isFinite()) ((investableNetWorth / fatTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val fatBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 1.30
        val fatAchieved = investableNetWorth >= fatTarget && fatBridgeFundedToday
        val fatPoint = if (fatAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30), point.age, partnerBirthYearOverride)
            val pointBridgeOk = point.age >= 60 || point.portfolio >= point.liquidBridgeTo60Required * 1.30
            point.totalPortfolio >= futureTarget && pointBridgeOk
        }
        val fatMilestone = FireMilestone(
            id = "fat",
            name = "Fat FIRE",
            badgeLabel = "Luxury & Abundance",
            description = "Covers an upgraded lifestyle (+30% spending buffer) with abundant travel and private amenities.",
            targetAmountToday = fatTarget,
            monthlyPassiveIncome = kotlin.math.round(((fatTarget * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = fatProgress,
            isAchieved = fatAchieved,
            estimatedAge = if (fatAchieved) settings.primaryAge else fatPoint?.age,
            estimatedYear = if (fatAchieved) settings.baseYear else fatPoint?.year
        )

        return FireMilestonesSummary(
            coastFire = coastMilestone,
            baristaFire = baristaMilestone,
            leanFire = leanMilestone,
            standardFire = standardMilestone,
            fatFire = fatMilestone
        )
    }

}
