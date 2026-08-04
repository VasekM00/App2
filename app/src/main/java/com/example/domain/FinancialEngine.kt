package com.example.domain

import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// --- Data Classes for Calculations & Output UI ---

data class YearlyIncome(
    val year: Int,
    val vaclavNet: Double,
    val eleonoraSalary: Double,
    val benefit: Double,
    val lecturing: Double,
    val vouchers: Double,
    val gift: Double,
    val totalMonthly: Double
)

data class CustomExpenseItem(
    val id: String,
    val name: String,
    val amount: Double
)

fun parseCustomExpenses(jsonStr: String): List<CustomExpenseItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val list = mutableListOf<CustomExpenseItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                CustomExpenseItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Custom Expense"),
                    amount = obj.optDouble("amount", 0.0)
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializeCustomExpenses(items: List<CustomExpenseItem>): String {
    val array = org.json.JSONArray()
    items.forEach { item ->
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("name", item.name)
        obj.put("amount", item.amount)
        array.put(obj)
    }
    return array.toString()
}

data class PortfolioYearPoint(
    val year: Int,
    val age: Int,
    val portfolio: Double,
    val target: Double,
    val investedAnnual: Double,
    val reinvestAnnual: Double,
    val lumpSum: Double,
    val status: String
)

data class DpsProjection(
    val yearsTo60: Int,
    val ownTotal: Double,
    val subsidyTotal: Double,
    val employerTotal: Double,
    val dpsBalance: Double,
    val etfBalance: Double,
    val margin: Double,
    val balanceAt36: Double,
    val earlyWithdrawalLimitAt36: Double,
    val youthSubsidyActive: Boolean
)

data class DipScenario(
    val monthly: Double,
    val annual: Double,
    val annualTaxSaved: Double,
    val netCostMonthly: Double,
    val headroom: Double,
    val riskLevel: String
)

data class DipProjection(
    val taxSavedYear: Double,
    val netCostMonthly: Double,
    val scenarios: List<DipScenario>,
    val headroom: Double,
    val dipBalanceAt60: Double
)

data class TaxReturnHelperData(
    val year: Int,
    val taxpayerCredit: Double,
    val spouseCredit: Double,
    val childBonus: Double,
    val retirementDeductionBase: Double,
    val dipSaving: Double,
    val totalIncrementalValue: Double,
    val spouseOwnIncome: Double,
    val spouseEligible: Boolean
)

data class MonteCarloPoint(
    val year: Int,
    val age: Int,
    val p5: Double,
    val p50: Double,
    val p95: Double,
    val target: Double
)

data class MonteCarloAgeProbability(
    val age: Int,
    val probabilityPct: Double
)

data class MonteCarloResult(
    val successRatePct: Double,
    val medianFireAge: Int?,
    val bestCaseAge: Int?,
    val worstCaseAge: Int?,
    val fanPoints: List<MonteCarloPoint>,
    val probabilityTable: List<MonteCarloAgeProbability>
)

data class FullCalculationState(
    val settings: SettingsEntity,
    val fireBaseTargetToday: Double,
    val dualTrajectory: List<PortfolioYearPoint>,
    val singleTrajectory: List<PortfolioYearPoint>,
    val fireDualPoint: PortfolioYearPoint?,
    val fireSinglePoint: PortfolioYearPoint?,
    val currentIncome: YearlyIncome,
    val investMonthlyTotal: Double,
    val emergencyCoverageMonths: Double,
    val realReturnPct: Double,
    val dps: DpsProjection,
    val dip: DipProjection,
    val taxReturnHelper: TaxReturnHelperData,
    val monteCarlo: MonteCarloResult,
    val savingsRatePct: Double,
    val totalLivingCostMonthly: Double,
    val netWorthTotal: Double,
    val actionsImpacts: Map<String, Double>
)

object FinancialEngine {

    fun annuityFactor(rate: Double, years: Int): Double {
        if (years <= 0) return 0.0
        if (rate <= 0.0) return years.toDouble()
        return (1.0 - (1.0 + rate).pow(-years)) / rate
    }

    fun statePensionBridgeYears(age: Int, settings: SettingsEntity): Int {
        return max(0, settings.statePensionAge - age)
    }

    fun fireTargetBase(settings: SettingsEntity, age: Int = settings.primaryAge): Double {
        if (settings.fireTargetOverride > 0) return settings.fireTargetOverride
        val swr = settings.safeWithdrawalRatePct / 100.0
        if (swr <= 0.0) return Double.MAX_VALUE

        val annualLifestyle = settings.lifestyleCostAtFireMonthly * 12.0
        val annualStatePension = settings.statePensionMonthly * 12.0
        val bridgeYears = statePensionBridgeYears(age, settings)

        val bridgeCost = annualLifestyle * annuityFactor(swr, bridgeYears)
        val postPensionShortfall = max(0.0, annualLifestyle - annualStatePension)
        val targetCapitalPostPension = if (postPensionShortfall > 0) {
            (postPensionShortfall / swr) / (1.0 + swr).pow(bridgeYears)
        } else 0.0

        return (bridgeCost + targetCapitalPostPension) * (1.0 + settings.safetyBufferPct / 100.0)
    }

    fun fireTargetYear(year: Int, settings: SettingsEntity, age: Int = settings.primaryAge + (year - settings.baseYear)): Double {
        val baseTarget = fireTargetBase(settings, age)
        val yearsElapsed = year - settings.baseYear
        return baseTarget * (1.0 + settings.cpiInflationPct / 100.0).pow(yearsElapsed)
    }

    fun vaclavSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        val sy = settings.baseYear
        val nb = settings.vSalary
        val r = settings.vRaiseAnnual
        val bonusMonthly = settings.vBonusAnnual / 12.0

        if (year < sy) return 0.0
        val base = nb + (year - sy) * r
        return base + bonusMonthly
    }

    fun eleonoraSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        if (year < settings.eReturnYear) return 0.0
        val yearsActive = year - settings.eReturnYear
        return settings.eStartingSalary * (1.0 + settings.eSalaryGrowthPct / 100.0).pow(yearsActive) + (settings.eBonusAnnual / 12.0)
    }

    fun eleonoraBenefitMonthly(year: Int, settings: SettingsEntity): Double {
        return if (year < settings.eReturnYear) settings.eParentalAllowanceMonthly else 0.0
    }

    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double {
        val sal = eleonoraSalaryMonthly(year, settings)
        val lec = if (settings.eIncludeLecturing) settings.eLecturingMonthly else 0.0
        return (sal + lec) * 12.0
    }

    fun householdIncome(year: Int, settings: SettingsEntity): YearlyIncome {
        val v = vaclavSalaryMonthly(year, settings)
        val e = eleonoraSalaryMonthly(year, settings)
        val b = eleonoraBenefitMonthly(year, settings)
        val lec = if (settings.eIncludeLecturing) settings.eLecturingMonthly else 0.0
        val total = v + e + b + lec + settings.familyGiftMonthly + settings.vMealVouchersMonthly

        return YearlyIncome(
            year = year,
            vaclavNet = v,
            eleonoraSalary = e,
            benefit = b,
            lecturing = lec,
            vouchers = settings.vMealVouchersMonthly,
            gift = settings.familyGiftMonthly,
            totalMonthly = total
        )
    }

    // ★ LEPŠÍ PENZIJKO REFORM: Doubled State Subsidy (40%) for youth under 30 (max 680 CZK/mo)
    fun dpsSubsidy(monthlyDeposit: Double, age: Int): Double {
        if (monthlyDeposit < 500) return 0.0
        val rate = if (age < 30) 0.40 else 0.20
        val maxSub = if (age < 30) 680.0 else 340.0
        return min(monthlyDeposit * rate, maxSub)
    }

    fun annualRetirementDeduction(settings: SettingsEntity): Double {
        val vDipAnnual = settings.dipContributionMonthly * 12.0
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly - 1700.0) * 12.0
        val vDeduction = min(vDipAnnual + vDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        val eDipAnnual = settings.eDipContributionMonthly * 12.0
        val eDpsAboveThreshold = max(0.0, settings.eDpsOwnContributionMonthly - 1700.0) * 12.0
        val eDeduction = min(eDipAnnual + eDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        return vDeduction + eDeduction
    }

    fun dipTaxSavingYear(settings: SettingsEntity): Double {
        return annualRetirementDeduction(settings) * (settings.taxRatePct / 100.0)
    }

    fun totalLivingCostMonthly(settings: SettingsEntity): Double {
        var base = settings.rentMonthly + settings.groceriesMonthly + settings.cafesMonthly +
                settings.therapyMonthly + settings.charityMonthly + settings.entertainmentMonthly +
                settings.transportMonthly + settings.subscriptionsMonthly + settings.otherDiscretionaryMonthly
        
        val customItems = parseCustomExpenses(settings.customExpensesJson)
        base += customItems.sumOf { it.amount }

        if (settings.childExpensesEnabled) {
            val childAge = settings.baseYear - settings.childBirthYear
            val childCost = when (childAge) {
                in 0..2 -> settings.childToddlerMonthly
                in 3..5 -> settings.childPreschoolMonthly
                in 6..14 -> settings.childSchoolMonthly
                in 15..18 -> settings.childTeenMonthly
                in 19..25 -> settings.childUniMonthly
                else -> 0.0
            }
            base += childCost
        }
        return base
    }

    fun baseInvestMonthly(settings: SettingsEntity): Double {
        val vaclavInvest = settings.portuDcaMonthly + settings.dpsOwnContributionMonthly +
                settings.dipContributionMonthly + (settings.employerRetirementAnnual / 12.0)
        val eleonoraInvest = settings.ePortuDcaMonthly + settings.eDpsOwnContributionMonthly +
                settings.eDipContributionMonthly + (settings.eEmployerRetirementAnnual / 12.0)
        return vaclavInvest + eleonoraInvest
    }

    fun buildLiquidPortfolio(settings: SettingsEntity, dualIncome: Boolean): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = settings.portfolioNominalReturnPct / 100.0
        var bal = settings.liquidPortfolioCurrent + settings.eLiquidPortfolioCurrent
        val initialTarget = fireTargetYear(sy, settings, age0)

        list.add(
            PortfolioYearPoint(
                year = sy,
                age = age0,
                portfolio = bal,
                target = initialTarget,
                investedAnnual = 0.0,
                reinvestAnnual = 0.0,
                lumpSum = 0.0,
                status = if (bal >= initialTarget) "FIRE OK" else "Growing"
            )
        )

        for (year in sy until (sy + 35)) {
            val age = age0 + (year - sy) + 1
            val baseAnnual = (settings.portuDcaMonthly + settings.ePortuDcaMonthly) * 12.0
            val reinvestAnnual = if (dualIncome && year >= settings.eReturnYear) {
                eleonoraSalaryMonthly(year, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = if (settings.lumpSumInclude && year == settings.lumpSumYear) settings.lumpSumAmount else 0.0

            bal = (bal + baseAnnual + reinvestAnnual + lump) * (1.0 + ret)
            val t = fireTargetYear(year + 1, settings, age)
            val gap = t - bal

            val status = when {
                bal >= t -> "FIRE OK"
                gap < t * 0.1 -> "Close"
                else -> "Growing"
            }

            list.add(
                PortfolioYearPoint(
                    year = year + 1,
                    age = age,
                    portfolio = bal,
                    target = t,
                    investedAnnual = baseAnnual,
                    reinvestAnnual = reinvestAnnual,
                    lumpSum = lump,
                    status = status
                )
            )
        }
        return list
    }

    // ★ LEPŠÍ PENZIJKO REFORM PROJECTION: Capped 0.5% TER & 1/3 Partial Withdrawal at Age 36
    fun buildDpsProjection(settings: SettingsEntity): DpsProjection {
        val years = max(0, 60 - settings.primaryAge)
        val fee = min(settings.dpsAnnualFeePct, 0.5) // Statutory 0.5% cap
        val monthlyRateDPS = (settings.dpsGrossReturnPct - fee) / 100.0 / 12.0
        val monthlyRateETF = settings.portfolioNominalReturnPct / 100.0 / 12.0

        val own = settings.dpsOwnContributionMonthly + settings.eDpsOwnContributionMonthly
        val emp = (settings.employerRetirementAnnual + settings.eEmployerRetirementAnnual) / 12.0

        var dpsBal = settings.dpsBalanceCurrent + settings.eDpsBalanceCurrent
        var etfBal = settings.dpsBalanceCurrent + settings.eDpsBalanceCurrent
        var totalSubsidy = 0.0
        var totalOwn = 0.0
        var totalEmp = 0.0

        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            val currentAge = settings.primaryAge + (m / 12)
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, currentAge)
            val subE = dpsSubsidy(settings.eDpsOwnContributionMonthly, currentAge)
            val sub = subV + subE

            totalSubsidy += sub
            totalOwn += own
            totalEmp += emp

            dpsBal = (dpsBal + own + sub + emp) * (1.0 + monthlyRateDPS)
            etfBal = (etfBal + own + emp) * (1.0 + monthlyRateETF)
        }

        val monthsTo36 = max(0, (36 - settings.primaryAge) * 12)
        var balAt36 = settings.dpsBalanceCurrent + settings.eDpsBalanceCurrent
        if (monthsTo36 in 1..totalMonths) {
            for (m in 0 until monthsTo36) {
                val currentAge = settings.primaryAge + (m / 12)
                val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, currentAge)
                val subE = dpsSubsidy(settings.eDpsOwnContributionMonthly, currentAge)
                balAt36 = (balAt36 + own + subV + subE + emp) * (1.0 + monthlyRateDPS)
            }
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
            earlyWithdrawalLimitAt36 = balAt36 / 3.0,
            youthSubsidyActive = settings.primaryAge < 30
        )
    }

    fun buildDipProjection(settings: SettingsEntity): DipProjection {
        val years = max(0, 60 - settings.primaryAge)
        val tsYear = dipTaxSavingYear(settings)
        val levels = listOf(1000.0, 1700.0, 2300.0, 4000.0)
        val totalMonthlyDip = settings.dipContributionMonthly + settings.eDipContributionMonthly
        val scenarios = levels.map { monthly ->
            val mock = settings.copy(dipContributionMonthly = monthly)
            val asave = dipTaxSavingYear(mock)
            val risk = when {
                monthly >= 4000.0 -> "High lock-up"
                monthly >= 2300.0 -> "Medium"
                else -> "Lower"
            }
            DipScenario(
                monthly = monthly,
                annual = monthly * 12.0,
                annualTaxSaved = asave,
                netCostMonthly = monthly - asave / 12.0,
                headroom = max(0.0, (settings.taxDeductionCeilingAnnual * 2) - annualRetirementDeduction(mock)),
                riskLevel = risk
            )
        }

        val monthlyRate = settings.portfolioNominalReturnPct / 100.0 / 12.0
        var dipBal = settings.dipBalanceCurrent + settings.eDipBalanceCurrent
        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            dipBal = (dipBal + totalMonthlyDip) * (1.0 + monthlyRate)
        }

        return DipProjection(
            taxSavedYear = tsYear,
            netCostMonthly = totalMonthlyDip - tsYear / 12.0,
            scenarios = scenarios,
            headroom = max(0.0, (settings.taxDeductionCeilingAnnual * 2) - annualRetirementDeduction(settings)),
            dipBalanceAt60 = dipBal
        )
    }

    // --- Gaussian Box-Muller generator ---
    private fun nextGaussian(random: Random): Double {
        var u1: Double
        var u2: Double
        do {
            u1 = random.nextDouble()
        } while (u1 == 0.0)
        do {
            u2 = random.nextDouble()
        } while (u2 == 0.0)

        val mag = sqrt(-2.0 * log(u1, Math.E))
        val theta = 2.0 * Math.PI * u2

        return mag * cos(theta)
    }

    private fun sin(rad: Double) = kotlin.math.sin(rad)

    fun runMonteCarlo(settings: SettingsEntity, horizonYears: Int = 35): MonteCarloResult {
        val sims = settings.monteCarloN.coerceIn(500, 2000)
        val meanReturn = settings.portfolioNominalReturnPct / 100.0
        val sigma = 0.15
        val random = Random(42) // Fixed seed for stable UI state

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)

        // Store year-by-year cash flow additions
        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val baseDca = (settings.portuDcaMonthly + settings.ePortuDcaMonthly) * 12.0
            val eleonoraSal = if (sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = if (settings.lumpSumInclude && sy == settings.lumpSumYear) settings.lumpSumAmount else 0.0
            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            Triple(baseDca + eleonoraSal + lump, target, baseAge + y + 1)
        }

        val yearlyBalances = Array(horizonYears + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        for (i in 0 until sims) {
            var bal = settings.liquidPortfolioCurrent + settings.eLiquidPortfolioCurrent
            yearlyBalances[0][i] = bal
            var hitAge: Int? = null

            for (y in 0 until horizonYears) {
                val (add, target, age) = additions[y]
                val ret = max(-0.60, meanReturn + nextGaussian(random) * sigma)
                bal = (bal + add) * (1.0 + ret)
                yearlyBalances[y + 1][i] = bal

                if (hitAge == null && bal >= target) {
                    hitAge = age
                }
            }
            if (hitAge != null) {
                hitAges.add(hitAge)
            }
        }

        hitAges.sort()
        val successRatePct = (hitAges.size.toDouble() / sims) * 100.0

        val fanPoints = mutableListOf<MonteCarloPoint>()
        for (y in 0..horizonYears) {
            val arr = yearlyBalances[y].copyOf().apply { sort() }
            val year = baseYear + y
            val age = baseAge + y
            val target = if (y == 0) initialTarget else additions[y - 1].second

            val p5 = arr[(sims * 0.05).toInt().coerceIn(0, sims - 1)]
            val p50 = arr[(sims * 0.50).toInt().coerceIn(0, sims - 1)]
            val p95 = arr[(sims * 0.95).toInt().coerceIn(0, sims - 1)]

            fanPoints.add(MonteCarloPoint(year, age, p5, p50, p95, target))
        }

        val ageCheckpoints = listOf(baseAge + 10, baseAge + 12, baseAge + 15, baseAge + 18, baseAge + 20)
        val probTable = ageCheckpoints.map { targetAge ->
            val countHit = hitAges.count { it <= targetAge }
            MonteCarloAgeProbability(targetAge, (countHit.toDouble() / sims) * 100.0)
        }

        val medianFireAge = if (hitAges.isNotEmpty()) hitAges[hitAges.size / 2] else null
        val bestCaseAge = if (hitAges.isNotEmpty()) hitAges[(hitAges.size * 0.05).toInt().coerceIn(0, hitAges.size - 1)] else null
        val worstCaseAge = if (hitAges.isNotEmpty()) hitAges[(hitAges.size * 0.95).toInt().coerceIn(0, hitAges.size - 1)] else null

        return MonteCarloResult(
            successRatePct = successRatePct,
            medianFireAge = medianFireAge,
            bestCaseAge = bestCaseAge,
            worstCaseAge = worstCaseAge,
            fanPoints = fanPoints,
            probabilityTable = probTable
        )
    }

    fun calculate(settings: SettingsEntity, actionStates: Map<String, Boolean> = emptyMap()): FullCalculationState {
        val fireBase = fireTargetBase(settings)
        val dual = buildLiquidPortfolio(settings, true)
        val single = buildLiquidPortfolio(settings, false)

        val fireDualPoint = dual.firstOrNull { it.portfolio >= it.target }
        val fireSinglePoint = single.firstOrNull { it.portfolio >= it.target }

        val currentIncome = householdIncome(settings.baseYear, settings)
        val investMonthly = baseInvestMonthly(settings)
        val livingCostTotal = totalLivingCostMonthly(settings)
        val emergencyMonths = if (livingCostTotal > 0) settings.emergencyReserveCurrent / livingCostTotal else 0.0

        val dps = buildDpsProjection(settings)
        val dip = buildDipProjection(settings)

        val spouseInc = spouseOwnIncomeAnnual(settings.baseYear, settings)
        val spouseEligible = settings.includeSpouseCredit &&
                settings.hasChildUnder3 &&
                (spouseInc <= 68000.0)

        val childBonusOk = (vaclavSalaryMonthly(settings.baseYear, settings) * 12.0) >= (settings.minWageMonthly * 6.0)

        val spouseCreditVal = if (spouseEligible) settings.spouseTaxCreditAnnual else 0.0
        val childBonusVal = if (childBonusOk) settings.childTaxBonusAnnual else 0.0
        val incrementalValue = spouseCreditVal + childBonusVal + dip.taxSavedYear

        val taxHelper = TaxReturnHelperData(
            year = settings.baseYear,
            taxpayerCredit = 30840.0,
            spouseCredit = spouseCreditVal,
            childBonus = childBonusVal,
            retirementDeductionBase = annualRetirementDeduction(settings),
            dipSaving = dip.taxSavedYear,
            totalIncrementalValue = incrementalValue,
            spouseOwnIncome = spouseInc,
            spouseEligible = spouseEligible
        )

        val monteCarlo = runMonteCarlo(settings)
        val savingsRate = if (currentIncome.totalMonthly > 0) {
            ((investMonthly + settings.familySavingsMonthly) / currentIncome.totalMonthly) * 100.0
        } else 0.0

        val netWorth = settings.liquidPortfolioCurrent + settings.eLiquidPortfolioCurrent +
                settings.emergencyReserveCurrent + settings.dpsBalanceCurrent + settings.eDpsBalanceCurrent +
                settings.dipBalanceCurrent + settings.eDipBalanceCurrent

        val actionsImpacts = mapOf(
            "ac1" to (if (settings.eIncludeLecturing) settings.eLecturingMonthly * 12.0 else 0.0),
            "ac2" to taxHelper.totalIncrementalValue,
            "ac3" to ((settings.liquidPortfolioCurrent + settings.eLiquidPortfolioCurrent) * 0.01),
            "ac4" to (settings.emergencyReserveCurrent * 0.04),
            "ac5" to max(0.0, dip.taxSavedYear),
            "ac6" to (settings.eStartingSalary * 12.0 * (settings.eReinvestedPct / 100.0)),
            "ac7" to 12000.0,
            "ac8" to (settings.subscriptionsMonthly * 12.0),
            "ac9" to max(0.0, settings.emergencyReserveCurrent - settings.emergencyReserveTarget),
            "ac10" to ((settings.dpsBalanceCurrent + settings.eDpsBalanceCurrent) * 0.005),
            "ac11" to 0.0,
            "ac12" to 0.0
        )

        return FullCalculationState(
            settings = settings,
            fireBaseTargetToday = fireBase,
            dualTrajectory = dual,
            singleTrajectory = single,
            fireDualPoint = fireDualPoint,
            fireSinglePoint = fireSinglePoint,
            currentIncome = currentIncome,
            investMonthlyTotal = investMonthly,
            emergencyCoverageMonths = emergencyMonths,
            realReturnPct = settings.portfolioNominalReturnPct - settings.cpiInflationPct,
            dps = dps,
            dip = dip,
            taxReturnHelper = taxHelper,
            monteCarlo = monteCarlo,
            savingsRatePct = savingsRate,
            totalLivingCostMonthly = livingCostTotal,
            netWorthTotal = netWorth,
            actionsImpacts = actionsImpacts
        )
    }
}
