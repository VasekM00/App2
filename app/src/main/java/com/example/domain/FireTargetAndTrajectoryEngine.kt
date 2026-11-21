package com.example.domain

import com.example.data.SettingsEntity
import com.example.domain.HouseholdCashFlowEngine.LivingCostSpec
import com.example.domain.HouseholdCashFlowEngine.annualPartnerTaxCredit
import com.example.domain.HouseholdCashFlowEngine.childMonthlyExpense
import com.example.domain.HouseholdCashFlowEngine.effectiveEmergencyReserveTarget
import com.example.domain.HouseholdCashFlowEngine.eleonoraSalaryMonthly
import com.example.domain.HouseholdCashFlowEngine.monthlyEmergencyReserveContribution
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

    fun fireTargetBase(settings: SettingsEntity, age: Int = settings.primaryAge): Double {
        val overrideTarget = settings.fireTargetOverride.finiteOr(0.0)
        if (overrideTarget > 0.0) return overrideTarget
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)

        val lifestyleMonthly = baselineLivingCostAtFireMonthly(settings)
        val annualLifestyle = max(0.0, lifestyleMonthly * 12.0)

        val vAnnualPension = max(0.0, settings.vStatePensionMonthly.finiteNonNegative() * 12.0)
        val eAnnualPension = if (!settings.isSingleHousehold) max(0.0, settings.eStatePensionMonthly.finiteNonNegative() * 12.0) else 0.0

        val vBridgeYears = max(0, settings.vStatePensionAge - age)
        val eBridgeYears = if (!settings.isSingleHousehold) max(0, settings.eStatePensionAge - age) else vBridgeYears

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
        // When prolongChildSupportInFire is enabled, support is modeled as a finite annuity bridge
        // ending when the children reach childSupportUntilAge (age 26),
        // rather than an immortal perpetuity into age 70+.
        val childBridgeCapital = if (age < settings.vStatePensionAge && settings.childExpensesEnabled && settings.prolongChildSupportInFire) {
            val retireCalYear = settings.baseYear + (age - settings.primaryAge)
            val c1CostAnnual = settings.childUniMonthly * 12.0
            val c2CostAnnual = settings.childUniMonthly * 12.0
            val c1Cap = if (settings.child1Enabled) {
                val remYears = max(0, min(settings.childSupportUntilAge, (settings.child1BirthYear + settings.childSupportUntilAge) - retireCalYear))
                val ann = c1CostAnnual * annuityFactor(swr, remYears)
                val waitYears = max(0, settings.child1BirthYear - retireCalYear)
                val disc = (1.0 + swr).pow(waitYears)
                if (waitYears > 0 && disc.isFinite() && disc > 0.0) ann / disc else ann
            } else 0.0
            val c2Cap = if (settings.child2Enabled) {
                val remYears = max(0, min(settings.childSupportUntilAge, (settings.child2BirthYear + settings.childSupportUntilAge) - retireCalYear))
                val ann = c2CostAnnual * annuityFactor(swr, remYears)
                val waitYears = max(0, settings.child2BirthYear - retireCalYear)
                val disc = (1.0 + swr).pow(waitYears)
                if (waitYears > 0 && disc.isFinite() && disc > 0.0) ann / disc else ann
            } else 0.0
            c1Cap + c2Cap
        } else 0.0

        val safetyBuffer = (1.0 + settings.safetyBufferPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0)
        val total = (costPhase1 + costPhase2 + targetCapitalPhase3 + childBridgeCapital) * safetyBuffer
        return total.finiteNonNegative()
    }

    fun fireTargetYear(year: Int, settings: SettingsEntity, age: Int = settings.primaryAge + (year - settings.baseYear)): Double {
        val baseTarget = fireTargetBase(settings, age)
        val yearsElapsed = max(0, year - settings.baseYear)
        val cpiFactor = (1.0 + settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0).pow(yearsElapsed)
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
        val annualLivingAtTargetYear: Double
    )

    internal fun buildPreFirePlan(
        settings: SettingsEntity,
        includeSpouse: Boolean,
        yearsCount: Int
    ): Array<PreFireAnnualCashflow> {
        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val partnerBirth = partnerBirthYear(settings)
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

        return Array(yearsCount) { y ->
            val sy = baseYear + y
            val targetYear = sy + 1
            val ageAtStart = baseAge + y
            val yearsElapsed = y + 1
            val inflationFactor = (1.0 + cpiRate).coerceAtLeast(0.0).pow(yearsElapsed).finiteOr(1.0)
            val rentFactor = if (livingSpec.rentGrowthPct != 0.0) {
                (1.0 + livingSpec.rentGrowthPct / 100.0).coerceAtLeast(0.0).pow(yearsElapsed).finiteOr(1.0)
            } else 1.0
            val annualLivingAtTargetYear = livingSpec.annualLivingAtProjectedYear(
                settings = settings,
                targetYear = targetYear,
                inflationFactor = inflationFactor,
                rentFactor = rentFactor
            )

            val isBaristaActive = settings.baristaTransitionEnabled && targetYear >= settings.baristaStartYear
            val dcaFactor = if (dcaGrowthPct != 0.0) {
                (1.0 + dcaGrowthPct / 100.0).coerceAtLeast(0.0).pow(y).finiteOr(1.0)
            } else 1.0

            val baseAnnual = if (isBaristaActive) {
                settings.baristaSavingsMonthly.finiteNonNegative() * 12.0 * inflationFactor
            } else {
                (settings.portuDcaMonthly.finiteNonNegative() + ePortu) * 12.0 * dcaFactor
            }
            val reinvestAnnual = if (!isBaristaActive && includeSpouse && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * eReinvestFraction * 12.0
            } else 0.0

            val taxRefundAnnual = if (isBaristaActive) {
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
                        vGrossOverride = baristaVGross
                    )
                } else 0.0
                val childRefund = CzechTaxEngine.annualChildTaxRelief(
                    baristaSettings,
                    sy,
                    vGrossAnnualOverride = baristaVGross,
                    vSpouseCreditAnnual = partnerRefund
                )
                retirementSaved + partnerRefund + childRefund
            } else {
                val partnerRefund = if (includeSpouse) annualPartnerTaxCredit(settings, sy) else 0.0
                val retirementSaved = CzechTaxEngine.annualRetirementTaxSavedForHousehold(settings, sy, includeSpouse)
                val childRefund = CzechTaxEngine.annualChildTaxRelief(
                    settings,
                    sy,
                    vSpouseCreditAnnual = partnerRefund
                )
                retirementSaved + partnerRefund + childRefund
            }

            val baristaShortfallAnnual = if (isBaristaActive) {
                val baristaIncomeAnnual = settings.baristaMonthlyIncome.finiteNonNegative() * 12.0 * inflationFactor
                max(0.0, annualLivingAtTargetYear - baristaIncomeAnnual)
            } else 0.0

            val lumpAnnual = lumpSumForYear(sy, settings, parsedLumpSums)
            val goalsOutlayAnnual = goalsOutlayForYear(sy, parsedGoals, hasGoals)
            val liquidNetInflow = baseAnnual + reinvestAnnual + taxRefundAnnual + lumpAnnual - goalsOutlayAnnual - baristaShortfallAnnual

            val subV = if (isBaristaActive) 0.0 else CzechTaxEngine.dpsSubsidy(
                settings.dpsOwnContributionMonthly,
                ageAtStart,
                settings,
                sy,
                settings.vStatePensionAge
            ) * 12.0
            val subE = if (includeSpouse) {
                CzechTaxEngine.dpsSubsidy(
                    settings.eDpsOwnContributionMonthly,
                    sy - partnerBirth,
                    settings,
                    sy,
                    settings.eStatePensionAge
                ) * 12.0
            } else 0.0
            val vDpsEffective = if (isBaristaActive) 0.0 else settings.dpsOwnContributionMonthly.finiteNonNegative()
            val vDipEffective = if (isBaristaActive) 0.0 else settings.dipContributionMonthly.finiteNonNegative()
            val vEmpEffectiveNow = if (isBaristaActive) 0.0 else vEmpEffective
            val vEmpToDip = if (vDpsEffective <= 0.0 && vDipEffective > 0.0) vEmpEffectiveNow else 0.0
            val vEmpToDps = vEmpEffectiveNow - vEmpToDip
            val eEmpToDip = if (eDpsOwn <= 0.0 && eDipOwn > 0.0) eEmpEffective else 0.0
            val eEmpToDps = eEmpEffective - eEmpToDip
            val dpsInflowAnnual = (vDpsEffective + eDpsOwn + vEmpToDps + eEmpToDps) * 12.0 + subV + subE
            val dipInflowAnnual = (vDipEffective + eDipOwn + vEmpToDip + eEmpToDip) * 12.0

            PreFireAnnualCashflow(
                isBaristaActive = isBaristaActive,
                baseAnnual = baseAnnual,
                reinvestAnnual = reinvestAnnual,
                taxRefundAnnual = taxRefundAnnual,
                lumpAnnual = lumpAnnual,
                goalsOutlayAnnual = goalsOutlayAnnual,
                baristaShortfallAnnual = baristaShortfallAnnual,
                liquidNetInflow = liquidNetInflow,
                dipInflowAnnual = dipInflowAnnual,
                dpsInflowAnnual = dpsInflowAnnual,
                inflationFactor = inflationFactor,
                annualLivingAtTargetYear = annualLivingAtTargetYear
            )
        }
    }

    fun buildLiquidPortfolio(
        settings: SettingsEntity,
        dualIncome: Boolean,
        initialCrashPct: Double = 0.0
    ): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = (netTaxableNominalReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val includeSpouse = dualIncome && !settings.isSingleHousehold
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val crashMult = max(0.0, 1.0 - initialCrashPct.finiteNonNegative())
        var bal = (settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid) * crashMult
        val initialTarget = fireTargetYear(sy, settings, age0)

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
        val bridgeReq0 = if (yearsTo60_0 == 0) 0.0 else annualLiving0 * annuityFactor(rReal, yearsTo60_0)
        val bridgeFunded0 = if (yearsTo60_0 == 0) true else bal >= bridgeReq0
        val reserveCurrent = settings.emergencyReserveCurrent.finiteNonNegative()
        val reserveTargetToday = effectiveEmergencyReserveTarget(settings).finiteNonNegative()
        val reserveMonthlyContrib = monthlyEmergencyReserveContribution(settings).finiteNonNegative()
        val isManualReserveContrib = settings.emergencyReserveMonthlyOverride.let { it.isFinite() && it >= 0.0 }
        var projectedReserve = reserveCurrent
        val cpiRate = (settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(-0.99)

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

        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val preFirePlan = buildPreFirePlan(settings, includeSpouse, yearsCount = 35)

        for (y in 0 until 35) {
            val plan = preFirePlan[y]
            val targetYear = sy + y + 1
            val age = age0 + y + 1

            bal = max(0.0, bal * max(0.0, 1.0 + ret) + plan.liquidNetInflow * (1.0 + ret * 0.5))
            dpsBal = max(0.0, dpsBal * max(0.0, 1.0 + dpsRet) + plan.dpsInflowAnnual * (1.0 + dpsRet * 0.5))
            dipBal = max(0.0, dipBal * max(0.0, 1.0 + ret) + plan.dipInflowAnnual * (1.0 + ret * 0.5))
            pensionBal = dpsBal + dipBal

            val t = fireTargetYear(targetYear, settings, age)
            val totalBal = bal + pensionBal
            val gap = t - totalBal

            val yearsTo60 = max(0, 60 - age)
            val annualLivingForBridge = if (hasLifestyleOverride) {
                settings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0 * plan.inflationFactor
            } else {
                plan.annualLivingAtTargetYear
            }
            val bridgeReq = if (yearsTo60 == 0) 0.0 else annualLivingForBridge * annuityFactor(rReal, yearsTo60)
            val bridgeFunded = if (yearsTo60 == 0) true else bal >= bridgeReq

            // Grow emergency cash reserve with inflation + monthly reserve contributions
            val indexedReserveTarget = reserveTargetToday * plan.inflationFactor
            projectedReserve = projectedReserve * (1.0 + cpiRate).coerceAtLeast(0.0)
            if (isManualReserveContrib) {
                projectedReserve += reserveMonthlyContrib * 12.0
            } else if (projectedReserve < indexedReserveTarget && reserveMonthlyContrib > 0.0) {
                projectedReserve = min(indexedReserveTarget, projectedReserve + reserveMonthlyContrib * 12.0)
            }

            val status = when {
                totalBal >= t -> {
                    if (age >= 60 || bridgeFunded) "FIRE OK" else "Bridge Constrained"
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
                    investedAnnual = plan.baseAnnual + plan.taxRefundAnnual,
                    reinvestAnnual = plan.reinvestAnnual,
                    lumpSum = plan.lumpAnnual,
                    status = status,
                    pensionPortfolio = pensionBal,
                    totalPortfolio = totalBal,
                    liquidBridgeTo60Required = bridgeReq,
                    isLiquidBridgeFunded = bridgeFunded,
                    pensionInflowAnnual = plan.dpsInflowAnnual + plan.dipInflowAnnual,
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

    fun calculateStressScenarios(settings: SettingsEntity, runMonteCarlo: Boolean = true): List<StressScenarioResult> {
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

            val fireTarget = fireTargetBase(mockSettings)
            val trajectory = if (id == "crash") {
                buildLiquidPortfolioWithInitialCrash(mockSettings, crashPct = 0.25)
            } else {
                buildLiquidPortfolio(mockSettings, !mockSettings.isSingleHousehold)
            }

            val firePoint = trajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }
                ?: trajectory.firstOrNull { it.totalPortfolio >= it.target }
            val successRate = if (runMonteCarlo) {
                MonteCarloEngine.runMonteCarlo(mockSettings, initialCrashPct = if (id == "crash") 0.25 else 0.0).successRatePct
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

    fun buildLiquidPortfolioWithInitialCrash(settings: SettingsEntity, crashPct: Double): List<PortfolioYearPoint> =
        buildLiquidPortfolio(settings, !settings.isSingleHousehold, initialCrashPct = crashPct)


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
        yearsTo60Today: Int
    ): FireMilestonesSummary {
        val investableNetWorth = snapLiquid + snapPension
        val currentLiquidBal = snapLiquid
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val realReturnRate = netRealReturnRate(settings)
        val yearsToRetire = max(0, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)
        val milestoneSettings = settings.copy(fireTargetOverride = 0.0)
        val effectiveBaseLiving = baselineLivingCostAtFireMonthly(milestoneSettings)

        val targetAtPensionAge = fireTargetBase(milestoneSettings, age = settings.vStatePensionAge)
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

        val baristaRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50))
        val baristaTarget = if (baristaRawTarget.isFinite()) max(0.0, kotlin.math.round(baristaRawTarget / 10_000.0) * 10_000.0) else 0.0
        val baristaProgress = if (baristaTarget > 0.0 && baristaTarget.isFinite()) ((investableNetWorth / baristaTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val baristaBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 0.50
        val baristaAchieved = investableNetWorth >= baristaTarget && baristaBridgeFundedToday
        val baristaPoint = if (baristaAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50), point.age)
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

        val leanRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75))
        val leanTarget = if (leanRawTarget.isFinite()) max(0.0, kotlin.math.round(leanRawTarget / 10_000.0) * 10_000.0) else 0.0
        val leanProgress = if (leanTarget > 0.0 && leanTarget.isFinite()) ((investableNetWorth / leanTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val leanBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 0.75
        val leanAchieved = investableNetWorth >= leanTarget && leanBridgeFundedToday
        val leanPoint = if (leanAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75), point.age)
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

        val fatRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30))
        val fatTarget = if (fatRawTarget.isFinite()) max(0.0, kotlin.math.round(fatRawTarget / 10_000.0) * 10_000.0) else 0.0
        val fatProgress = if (fatTarget > 0.0 && fatTarget.isFinite()) ((investableNetWorth / fatTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val fatBridgeFundedToday = yearsTo60Today == 0 || currentLiquidBal >= bridgeReqToday * 1.30
        val fatAchieved = investableNetWorth >= fatTarget && fatBridgeFundedToday
        val fatPoint = if (fatAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30), point.age)
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
