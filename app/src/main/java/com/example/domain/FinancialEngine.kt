package com.example.domain

import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

// Václav and Eleonora birth years are fixed to 2000 (com.example.data.VACLAV_BIRTH_YEAR / ELEONORA_BIRTH_YEAR)
// Primary age is computed directly as baseYear - 2000.

// --- Data Classes for Calculations & Output UI ---

data class YearlyIncome(
    val year: Int,
    val vaclavNet: Double,
    val eleonoraSalary: Double,
    val benefit: Double,
    val lecturing: Double,
    val vouchers: Double,
    val gift: Double,
    val vaclavOther: Double = 0.0,
    val eleonoraOther: Double = 0.0,
    val vaclavGifts: Double = 0.0,
    val eleonoraGifts: Double = 0.0,
    val lumpSumMonthly: Double = 0.0,
    val totalMonthly: Double
)

data class CustomExpenseItem(
    val id: String,
    val name: String,
    val amount: Double
)

data class CustomLifeGoalItem(
    val id: String,
    val name: String,
    val iconName: String = "flag",
    val targetYear: Int,
    val targetAmountCzk: Double,
    val currentSavedCzk: Double
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

fun parseDeletedCategories(jsonStr: String): Set<String> {
    if (jsonStr.isBlank()) return emptySet()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val set = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            set.add(array.getString(i))
        }
        set
    } catch (e: Exception) {
        emptySet()
    }
}

fun serializeDeletedCategories(set: Set<String>): String {
    val array = org.json.JSONArray()
    set.forEach { array.put(it) }
    return array.toString()
}

val DEFAULT_CUSTOM_LIFE_GOALS = listOf(
    CustomLifeGoalItem("1", "Real Estate Down Payment", "home", 2028, 1_500_000.0, 450_000.0),
    CustomLifeGoalItem("2", "Children Education & Family Fund", "school", 2032, 600_000.0, 120_000.0),
    CustomLifeGoalItem("3", "Sabbatical / Career Break", "star", 2030, 300_000.0, 80_000.0)
)

fun parseCustomLifeGoals(jsonStr: String): List<CustomLifeGoalItem> {
    if (jsonStr.isBlank()) return DEFAULT_CUSTOM_LIFE_GOALS
    if (jsonStr.trim() == "[]") return emptyList()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val list = mutableListOf<CustomLifeGoalItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                CustomLifeGoalItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Life Goal"),
                    iconName = obj.optString("iconName", "flag"),
                    targetYear = obj.optInt("targetYear", 2030),
                    targetAmountCzk = obj.optDouble("targetAmountCzk", 500_000.0),
                    currentSavedCzk = obj.optDouble("currentSavedCzk", 0.0)
                )
            )
        }
        list
    } catch (e: Exception) {
        DEFAULT_CUSTOM_LIFE_GOALS
    }
}

fun serializeCustomLifeGoals(items: List<CustomLifeGoalItem>): String {
    val array = org.json.JSONArray()
    items.forEach { item ->
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("name", item.name)
        obj.put("iconName", item.iconName)
        obj.put("targetYear", item.targetYear)
        obj.put("targetAmountCzk", item.targetAmountCzk)
        obj.put("currentSavedCzk", item.currentSavedCzk)
        array.put(obj)
    }
    return array.toString()
}

data class CustomLumpSumItem(
    val id: String,
    val name: String,
    val year: Int,
    val month: Int? = null,
    val amount: Double,
    val enabled: Boolean = true
)

fun parseCustomLumpSums(jsonStr: String): List<CustomLumpSumItem> {
    if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val list = mutableListOf<CustomLumpSumItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val monthVal = if (obj.has("month") && !obj.isNull("month")) {
                val m = obj.optInt("month", 0)
                if (m in 1..12) m else null
            } else null
            list.add(
                CustomLumpSumItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Lump Sum"),
                    year = obj.optInt("year", 2030),
                    month = monthVal,
                    amount = obj.optDouble("amount", 0.0),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

fun serializeCustomLumpSums(items: List<CustomLumpSumItem>): String {
    val array = org.json.JSONArray()
    items.forEach { item ->
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("name", item.name)
        obj.put("year", item.year)
        if (item.month != null) {
            obj.put("month", item.month)
        }
        obj.put("amount", item.amount)
        obj.put("enabled", item.enabled)
        array.put(obj)
    }
    return array.toString()
}

fun lumpSumForYear(year: Int, settings: SettingsEntity): Double {
    var total = 0.0
    if (settings.lumpSumInclude && year == settings.lumpSumYear) {
        total += settings.lumpSumAmount
    }
    val additional = parseCustomLumpSums(settings.customLumpSumsJson)
    for (item in additional) {
        if (item.enabled && item.year == year) {
            total += item.amount
        }
    }
    return total
}

fun lumpSumsForMonth(year: Int, month: Int, settings: SettingsEntity): List<CustomLumpSumItem> {
    val additional = parseCustomLumpSums(settings.customLumpSumsJson)
    return additional.filter { it.enabled && it.year == year && it.month == month }
}

data class PortfolioYearPoint(
    val year: Int,
    val age: Int,
    val portfolio: Double,
    val target: Double,
    val investedAnnual: Double,
    val reinvestAnnual: Double,
    val lumpSum: Double,
    val status: String,
    val pensionPortfolio: Double = 0.0,
    val totalPortfolio: Double = portfolio + pensionPortfolio,
    val liquidBridgeTo60Required: Double = 0.0,
    val isLiquidBridgeFunded: Boolean = true
)

data class DpsScenario(
    val monthly: Double,
    val annual: Double,
    val monthlySubsidy: Double,
    val annualSubsidy: Double,
    val annualTaxSaved: Double,
    val totalAnnualBenefit: Double,
    val badgeLabel: String?
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
    val youthSubsidyActive: Boolean,
    val scenarios: List<DpsScenario> = emptyList()
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

/**
 * Answers the question the accumulation Monte Carlo cannot: once FIRE is reached,
 * does the portfolio actually survive withdrawals for the planned retirement horizon?
 * Withdrawals are inflation-adjusted lifestyle costs minus indexed state pensions,
 * simulated against the same stochastic market model.
 */
data class RetirementSurvivalResult(
    val successRatePct: Double,
    val fireProbabilityPct: Double,
    val horizonYears: Int,
    val medianEndBalanceToday: Double,
    val p5EndBalanceToday: Double,
    val medianDepletionAge: Int?,
    val sampleSize: Int,
    val guardrailsActive: Boolean = false,
    val guardrailTriggeredPct: Double = 0.0
)

data class StressScenarioResult(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val description: String,
    val nominalReturnPct: Double,
    val cpiInflationPct: Double,
    val swrPct: Double,
    val rentGrowthPct: Double,
    val fireTargetToday: Double,
    val fireAge: Int?,
    val fireYear: Int?,
    val successRatePct: Double,
    val emergencySurvivalMonths: Double,
    val netWorthAt60: Double,
    val trajectory: List<PortfolioYearPoint>
)

data class FireMilestone(
    val id: String,
    val name: String,
    val badgeLabel: String,
    val description: String,
    val targetAmountToday: Double,
    val monthlyPassiveIncome: Double,
    val progressPct: Double,
    val isAchieved: Boolean,
    val estimatedAge: Int?,
    val estimatedYear: Int?
)

data class FireMilestonesSummary(
    val coastFire: FireMilestone,
    val baristaFire: FireMilestone? = null,
    val leanFire: FireMilestone,
    val standardFire: FireMilestone,
    val fatFire: FireMilestone
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
    val historicalMonteCarlo: MonteCarloResult,
    val retirementSurvival: RetirementSurvivalResult,
    val historicalRetirementSurvival: RetirementSurvivalResult = retirementSurvival,
    val stressScenarios: List<StressScenarioResult>,
    val fireMilestones: FireMilestonesSummary,
    val savingsRatePct: Double,
    val totalLivingCostMonthly: Double,
    val netWorthTotal: Double,
    val actionsImpacts: Map<String, Double>,
    val perpetualFireMultiplier: Double = 330.0,
    val fireReductionPer100CzkMonthly: Double = 33000.0,
    val fireLiquidBridgePoint: PortfolioYearPoint? = null,
    val liquidBridgeTo60RequiredToday: Double = 0.0,
    val isLiquidBridgeFundedToday: Boolean = true,
    val liquidBridgeDeficitToday: Double = 0.0,
    val currentLiquidPortfolio: Double = 0.0,
    val currentPensionPortfolio: Double = 0.0
)

object FinancialEngine {

    fun perpetualFireLeverageMultiplier(settings: SettingsEntity): Double {
        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.001)
        val buffer = (1.0 + settings.safetyBufferPct / 100.0).coerceAtLeast(0.0)
        return (12.0 / swr) * buffer
    }

    fun lumpSumsForMonth(year: Int, month: Int, settings: SettingsEntity): List<CustomLumpSumItem> {
        return com.example.domain.lumpSumsForMonth(year, month, settings)
    }

    fun annuityFactor(rate: Double, years: Int): Double {
        if (years <= 0) return 0.0
        if (abs(rate) < 1e-9) return years.toDouble()
        if (rate <= -1.0) return 0.0
        val term = (1.0 + rate).pow(-years)
        if (term.isNaN() || term.isInfinite()) return years.toDouble()
        return (1.0 - term) / rate
    }

    fun statePensionBridgeYears(age: Int, settings: SettingsEntity): Int {
        val vBridge = max(0, settings.vStatePensionAge - age)
        val eBridge = if (!settings.isSingleHousehold) max(0, settings.eStatePensionAge - age) else vBridge
        return max(vBridge, eBridge)
    }

    fun fireTargetBase(settings: SettingsEntity, age: Int = settings.primaryAge): Double {
        if (settings.fireTargetOverride > 0) return settings.fireTargetOverride
        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.001)

        val lifestyleMonthly = if (settings.lifestyleCostAtFireMonthly > 0.0) {
            settings.lifestyleCostAtFireMonthly
        } else {
            val effSettings = if (settings.childExpensesEnabled && settings.prolongChildSupportInFire) settings else settings.copy(childExpensesEnabled = false)
            var cost = totalLivingCostMonthly(effSettings, settings.baseYear)
            if (!settings.prolongChildSupportInFire && settings.currentChildCostsInBaseline) {
                if (settings.child1Enabled && settings.child1BirthYear <= settings.baseYear) {
                    cost = max(0.0, cost - childMonthlyExpense(settings.child1BirthYear, settings.baseYear, settings))
                }
                if (settings.child2Enabled && settings.child2BirthYear <= settings.baseYear) {
                    cost = max(0.0, cost - childMonthlyExpense(settings.child2BirthYear, settings.baseYear, settings))
                }
            } else if (settings.childExpensesEnabled && settings.prolongChildSupportInFire) {
                if (settings.child2Enabled && settings.child2BirthYear > settings.baseYear) {
                    cost += settings.childUniMonthly
                }
            }
            cost
        }
        val annualLifestyle = max(0.0, lifestyleMonthly * 12.0)
        
        val vAnnualPension = max(0.0, settings.vStatePensionMonthly * 12.0)
        val eAnnualPension = if (!settings.isSingleHousehold) max(0.0, settings.eStatePensionMonthly * 12.0) else 0.0

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

        val safetyBuffer = (1.0 + settings.safetyBufferPct / 100.0).coerceAtLeast(0.0)
        val total = (costPhase1 + costPhase2 + targetCapitalPhase3) * safetyBuffer
        return if (total.isNaN() || total.isInfinite()) 0.0 else max(0.0, total)
    }

    fun fireTargetYear(year: Int, settings: SettingsEntity, age: Int = settings.primaryAge + (year - settings.baseYear)): Double {
        val baseTarget = fireTargetBase(settings, age)
        val yearsElapsed = max(0, year - settings.baseYear)
        val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)
        val result = baseTarget * inflationFactor.pow(yearsElapsed)
        return if (result.isNaN() || result.isInfinite()) baseTarget else max(0.0, result)
    }

    fun vaclavSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        val sy = settings.baseYear
        if (year < sy) return 0.0
        val growthFactor = if (settings.vSalaryGrowthPct > 0.0) {
            (1.0 + settings.vSalaryGrowthPct / 100.0).pow(year - sy)
        } else 1.0
        return settings.vSalary * growthFactor
    }

    fun eleonoraSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.eReturnYear) return 0.0
        val month = settings.eReturnMonth.coerceIn(1, 12)
        val fullSalary = if (year == settings.eReturnYear) {
            settings.eStartingSalary
        } else {
            val yearsActive = year - settings.eReturnYear
            settings.eStartingSalary * (1.0 + settings.eSalaryGrowthPct / 100.0).pow(yearsActive)
        }
        return if (year == settings.eReturnYear) {
            val workFraction = (13 - month) / 12.0
            fullSalary * workFraction
        } else {
            fullSalary
        }
    }

    fun eleonoraBenefitMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.baseYear || year > settings.eReturnYear) return 0.0
        val hasChildren = settings.child1Enabled || settings.child2Enabled
        if (!hasChildren) return 0.0

        val totalPot = (if (settings.child1Enabled) parentalAllowancePot(settings.child1BirthYear, settings) else 0.0) +
                (if (settings.child2Enabled) parentalAllowancePot(settings.child2BirthYear, settings) else 0.0)

        // Calculate cumulative benefits drawn in prior years
        var cumulativeDrawn = 0.0
        for (y in settings.baseYear until year) {
            val annualForY = if (y == settings.eReturnYear) {
                val month = settings.eReturnMonth.coerceIn(1, 12)
                settings.eParentalAllowanceMonthly * (month - 1)
            } else {
                settings.eParentalAllowanceMonthly * 12.0
            }
            cumulativeDrawn += annualForY
        }

        val remainingPot = max(0.0, totalPot - cumulativeDrawn)
        if (remainingPot <= 0.0) return 0.0

        val monthsInThisYear = if (year == settings.eReturnYear) {
            val month = settings.eReturnMonth.coerceIn(1, 12)
            (month - 1).toDouble()
        } else {
            12.0
        }

        if (monthsInThisYear <= 0.0) return 0.0

        val desiredAnnual = settings.eParentalAllowanceMonthly * monthsInThisYear
        val actualAnnual = min(desiredAnnual, remainingPot)
        return actualAnnual / 12.0
    }

    fun eleonoraBenefitForMonth(year: Int, month: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.baseYear || year > settings.eReturnYear) return 0.0
        if (year == settings.eReturnYear && month >= settings.eReturnMonth) return 0.0
        val hasChildren = settings.child1Enabled || settings.child2Enabled
        if (!hasChildren) return 0.0

        val totalPot = (if (settings.child1Enabled) parentalAllowancePot(settings.child1BirthYear, settings) else 0.0) +
                (if (settings.child2Enabled) parentalAllowancePot(settings.child2BirthYear, settings) else 0.0)

        var cumulativeDrawnBeforeThisYear = 0.0
        for (y in settings.baseYear until year) {
            val annualForY = if (y == settings.eReturnYear) {
                val m = settings.eReturnMonth.coerceIn(1, 12)
                settings.eParentalAllowanceMonthly * (m - 1)
            } else {
                settings.eParentalAllowanceMonthly * 12.0
            }
            cumulativeDrawnBeforeThisYear += annualForY
        }

        val remainingPotAtStartOfYear = max(0.0, totalPot - cumulativeDrawnBeforeThisYear)
        if (remainingPotAtStartOfYear <= 0.0) return 0.0

        val drawnPriorInThisYear = (month - 1) * settings.eParentalAllowanceMonthly
        val remainingForThisMonth = max(0.0, remainingPotAtStartOfYear - drawnPriorInThisYear)
        return min(settings.eParentalAllowanceMonthly, remainingForThisMonth)
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
        val v = if (activeLedgerEntry != null && activeLedgerEntry.incVaclav > 0.0 && year == baseYear) {
            activeLedgerEntry.incVaclav
        } else {
            vaclavSalaryMonthly(year, settings)
        }

        val isBeforeReturn = year < settings.eReturnYear
        val isReturnYear = year == settings.eReturnYear
        val e = when {
            isBeforeReturn -> 0.0
            isReturnYear && activeLedgerEntry != null && year == baseYear -> {
                if (month != null && month < settings.eReturnMonth) 0.0
                else if (activeLedgerEntry.incEleonora > 0.0) activeLedgerEntry.incEleonora
                else eleonoraSalaryMonthly(year, settings)
            }
            activeLedgerEntry != null && activeLedgerEntry.incEleonora > 0.0 && year == baseYear -> {
                activeLedgerEntry.incEleonora
            }
            else -> eleonoraSalaryMonthly(year, settings)
        }

        val b = when {
            isBeforeReturn -> {
                if (activeLedgerEntry != null && activeLedgerEntry.incEleonora > 0.0 && year == baseYear) {
                    activeLedgerEntry.incEleonora
                } else if (month != null) {
                    eleonoraBenefitForMonth(year, month, settings)
                } else {
                    eleonoraBenefitMonthly(year, settings)
                }
            }
            isReturnYear -> {
                if (activeLedgerEntry != null && year == baseYear) {
                    if (month != null && month < settings.eReturnMonth && activeLedgerEntry.incEleonora > 0.0) {
                        activeLedgerEntry.incEleonora
                    } else if (month != null && month >= settings.eReturnMonth) {
                        0.0
                    } else if (month != null) {
                        eleonoraBenefitForMonth(year, month, settings)
                    } else {
                        eleonoraBenefitMonthly(year, settings)
                    }
                } else if (month != null) {
                    eleonoraBenefitForMonth(year, month, settings)
                } else {
                    eleonoraBenefitMonthly(year, settings)
                }
            }
            else -> 0.0
        }

        val lec = eleonoraLecturingMonthly(year, settings)

        val vGift = if (settings.familyGiftMonthly != 16000.0 && settings.vGiftsMonthly == 16000.0 && settings.eGiftsMonthly == 0.0) {
            settings.familyGiftMonthly
        } else {
            settings.vGiftsMonthly
        }
        val eGift = if (!settings.isSingleHousehold) settings.eGiftsMonthly else 0.0
        val totalGift = vGift + eGift

        val scheduledLumps = if (month != null) {
            lumpSumsForMonth(year, month, settings).sumOf { it.amount }
        } else 0.0

        val (vOther, eOther, lumpTotal) = if (activeLedgerEntry != null && activeLedgerEntry.incUnforeseen > 0.0 && year == baseYear) {
            val actualOther = activeLedgerEntry.incUnforeseen
            val budgetedOtherV = settings.vOtherInflowsMonthly
            val budgetedOtherE = if (!settings.isSingleHousehold) settings.eOtherInflowsMonthly else 0.0
            val budgetedTotal = budgetedOtherV + budgetedOtherE + scheduledLumps

            if (actualOther >= budgetedTotal) {
                Triple(budgetedOtherV, budgetedOtherE, scheduledLumps + (actualOther - budgetedTotal))
            } else {
                val ratio = if (budgetedTotal > 0.0) actualOther / budgetedTotal else 0.0
                Triple(budgetedOtherV * ratio, budgetedOtherE * ratio, scheduledLumps * ratio)
            }
        } else {
            val vO = settings.vOtherInflowsMonthly
            val eO = if (!settings.isSingleHousehold) settings.eOtherInflowsMonthly else 0.0
            Triple(vO, eO, scheduledLumps)
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

    fun dpsSubsidy(monthlyDeposit: Double, age: Int, settings: SettingsEntity? = null, year: Int = settings?.baseYear ?: 2026): Double =
        CzechTaxEngine.dpsSubsidy(monthlyDeposit, age, settings, year)

    fun annualRetirementDeduction(settings: SettingsEntity): Double =
        CzechTaxEngine.annualRetirementDeduction(settings)

    fun annualRetirementTaxSaved(settings: SettingsEntity, year: Int = settings.baseYear): Double =
        CzechTaxEngine.annualRetirementTaxSaved(settings, year)

    fun netToGrossAnnual(
        netMonthly: Double,
        taxpayerCreditAnnual: Double,
        highBracketThresholdAnnual: Double = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
    ): Double = CzechTaxEngine.netToGrossAnnual(netMonthly, taxpayerCreditAnnual, highBracketThresholdAnnual)

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

    fun totalLivingCostMonthly(settings: SettingsEntity, year: Int = settings.baseYear): Double {
        val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
        var base = 0.0
        if (!deletedSet.contains("rent")) base += settings.rentMonthly
        if (!deletedSet.contains("groceries")) base += settings.groceriesMonthly
        if (!deletedSet.contains("other_discretionary")) base += settings.otherDiscretionaryMonthly
        if (!deletedSet.contains("cafes")) base += settings.cafesMonthly
        if (!deletedSet.contains("therapy")) base += settings.therapyMonthly
        if (!deletedSet.contains("charity")) base += settings.charityMonthly
        if (!deletedSet.contains("entertainment")) base += settings.entertainmentMonthly
        if (!deletedSet.contains("transport")) base += settings.transportMonthly
        if (!deletedSet.contains("subscriptions")) base += settings.subscriptionsMonthly

        val customItems = parseCustomExpenses(settings.customExpensesJson)
        base += customItems.sumOf { it.amount }

        if (settings.childExpensesEnabled) {
            fun computeChildAddition(enabled: Boolean, birthYear: Int): Double {
                if (!enabled) return 0.0
                return if (settings.currentChildCostsInBaseline) {
                    if (birthYear <= settings.baseYear) {
                        // Existing child already alive in baseYear: current stage expense is already embedded
                        // in manual living costs (groceries, rent, baby items).
                        // In base year, addition is 0.0. In future years, only the incremental delta is added.
                        if (year <= settings.baseYear) {
                            0.0
                        } else {
                            val baseYearStageCost = childMonthlyExpense(birthYear, settings.baseYear, settings)
                            val currentYearStageCost = childMonthlyExpense(birthYear, year, settings)
                            currentYearStageCost - baseYearStageCost
                        }
                    } else {
                        // Future child born after baseYear: not present in base-year budget, full stage cost added.
                        if (year < birthYear) 0.0 else childMonthlyExpense(birthYear, year, settings)
                    }
                } else {
                    childMonthlyExpense(birthYear, year, settings)
                }
            }

            base += computeChildAddition(settings.child1Enabled, settings.child1BirthYear)
            base += computeChildAddition(settings.child2Enabled, settings.child2BirthYear)
        }
        return max(0.0, base)
    }

    /**
     * Calculates the number of days of permanent financial freedom purchased
     * by a given monthly savings amount, based on the household's current daily living burn.
     */
    fun freedomDaysBoughtMonthly(monthlySavings: Double, monthlyLivingCost: Double): Double {
        if (monthlyLivingCost <= 0.0) return 0.0
        val dailyBurn = monthlyLivingCost / 30.42
        return if (dailyBurn > 0.0) monthlySavings / dailyBurn else 0.0
    }

    /**
     * Calculates the total number of days of financial freedom stored in a given portfolio balance.
     */
    fun freedomDaysCoveredByPortfolio(portfolioBalance: Double, monthlyLivingCost: Double): Double {
        if (portfolioBalance <= 0.0 || monthlyLivingCost <= 0.0) return 0.0
        val dailyBurn = monthlyLivingCost / 30.42
        return if (dailyBurn > 0.0) portfolioBalance / dailyBurn else 0.0
    }

    fun baseInvestMonthly(settings: SettingsEntity): Double {
        val empCap = settings.employerRetirementExemptionAnnual / 12.0
        val vaclavInvest = settings.portuDcaMonthly + settings.dpsOwnContributionMonthly +
                settings.dipContributionMonthly + min(settings.employerRetirementMonthly, empCap)
        val eleonoraInvest = if (!settings.isSingleHousehold) {
            settings.ePortuDcaMonthly + settings.eDpsOwnContributionMonthly +
                    settings.eDipContributionMonthly + min(settings.eEmployerRetirementMonthly, empCap)
        } else 0.0
        return vaclavInvest + eleonoraInvest
    }

    fun buildLiquidPortfolio(settings: SettingsEntity, dualIncome: Boolean): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = netTaxableNominalReturnPct(settings) / 100.0
        val includeSpouse = dualIncome && !settings.isSingleHousehold
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent else 0.0
        var bal = settings.liquidPortfolioCurrent + eLiquid
        val initialTarget = fireTargetYear(sy, settings, age0)

        val eDpsBal = if (includeSpouse) settings.eDpsBalanceCurrent else 0.0
        val eDipBal = if (includeSpouse) settings.eDipBalanceCurrent else 0.0
        var dpsBal = settings.dpsBalanceCurrent + eDpsBal
        var dipBal = settings.dipBalanceCurrent + eDipBal
        var pensionBal = dpsBal + dipBal

        val rReal = ((settings.portfolioNominalReturnPct - settings.cpiInflationPct) / 100.0)
        val annualLiving0 = totalLivingCostMonthly(settings) * 12.0
        val yearsTo60_0 = max(0, 60 - age0)
        val bridgeReq0 = if (yearsTo60_0 == 0) 0.0 else if (rReal > 0.001) {
            annualLiving0 * ((1.0 - (1.0 + rReal).pow(-yearsTo60_0)) / rReal)
        } else {
            annualLiving0 * yearsTo60_0
        }
        val bridgeFunded0 = if (yearsTo60_0 == 0) true else bal >= bridgeReq0

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
                isLiquidBridgeFunded = bridgeFunded0
            )
        )

        val dpsFee = min(settings.dpsAnnualFeePct, settings.dpsStatutoryFeeCapPct)
        val dpsRet = max(-0.99, (settings.dpsGrossReturnPct - dpsFee) / 100.0)

        for (year in sy until (sy + 35)) {
            val age = age0 + (year - sy) + 1
            val ePortu = if (includeSpouse) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(year - sy) else 1.0
            val baseAnnual = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val reinvestAnnual = if (includeSpouse && year >= settings.eReturnYear) {
                eleonoraSalaryMonthly(year, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val taxRefundAnnual = annualRetirementTaxSaved(settings, year)
            val lump = lumpSumForYear(year, settings)

            val annualInflows = baseAnnual + reinvestAnnual + taxRefundAnnual + lump
            bal = max(0.0, bal * max(0.0, 1.0 + ret) + annualInflows * (1.0 + ret * 0.5))

            // Pension tier: DPS (own + employer + state match) and DIP (own)
            val eDpsOwn = if (includeSpouse) settings.eDpsOwnContributionMonthly else 0.0
            val eDipOwn = if (includeSpouse) settings.eDipContributionMonthly else 0.0
            val eEmp = if (includeSpouse) settings.eEmployerRetirementMonthly else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age - 1, settings, year) * 12.0
            val subE = if (includeSpouse) dpsSubsidy(settings.eDpsOwnContributionMonthly, age - 1, settings, year) * 12.0 else 0.0
            val dpsInflows = (settings.dpsOwnContributionMonthly + eDpsOwn + settings.employerRetirementMonthly + eEmp) * 12.0 + subV + subE
            val dipInflows = (settings.dipContributionMonthly + eDipOwn) * 12.0

            dpsBal = max(0.0, dpsBal * max(0.0, 1.0 + dpsRet) + dpsInflows * (1.0 + dpsRet * 0.5))
            dipBal = max(0.0, dipBal * max(0.0, 1.0 + ret) + dipInflows * (1.0 + ret * 0.5))
            pensionBal = dpsBal + dipBal

            val t = fireTargetYear(year + 1, settings, age)
            val totalBal = bal + pensionBal
            val gap = t - totalBal

            val yearsTo60 = max(0, 60 - age)
            val annualLiving = totalLivingCostMonthly(settings) * 12.0
            val bridgeReq = if (yearsTo60 == 0) 0.0 else if (rReal > 0.001) {
                annualLiving * ((1.0 - (1.0 + rReal).pow(-yearsTo60)) / rReal)
            } else {
                annualLiving * yearsTo60
            }
            val bridgeFunded = if (yearsTo60 == 0) true else bal >= bridgeReq

            val status = when {
                totalBal >= t -> {
                    if (age >= 60 || bridgeFunded) "FIRE OK" else "Bridge Constrained"
                }
                gap < t * 0.1 -> "Close"
                else -> "Growing"
            }

            list.add(
                PortfolioYearPoint(
                    year = year + 1,
                    age = age,
                    portfolio = bal,
                    target = t,
                    investedAnnual = baseAnnual + taxRefundAnnual,
                    reinvestAnnual = reinvestAnnual,
                    lumpSum = lump,
                    status = status,
                    pensionPortfolio = pensionBal,
                    totalPortfolio = totalBal,
                    liquidBridgeTo60Required = bridgeReq,
                    isLiquidBridgeFunded = bridgeFunded
                )
            )
        }
        return list
    }

    fun buildDpsProjection(settings: SettingsEntity): DpsProjection =
        DpsDipEngine.buildDpsProjection(settings)

    fun buildDipProjection(settings: SettingsEntity): DipProjection =
        DpsDipEngine.buildDipProjection(settings)

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

    /**
     * Historical annual total returns of global equities (S&P 500 / MSCI World total return
     * with dividends reinvested) from 1970 to 2025 (56 years).
     * Used for empirical block-bootstrap resampling to preserve real-world fat tails,
     * sequence-of-returns drawdowns (e.g. 1973-74, 2000-02, 2008), and multi-year autocorrelation.
     */
    val HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT = doubleArrayOf(
         3.9,  // 1970
        14.3,  // 1971
        19.0,  // 1972
       -14.7,  // 1973 (Stagflation shock)
       -26.5,  // 1974 (Stagflation shock)
        37.2,  // 1975 (Rebound)
        23.8,  // 1976
        -7.2,  // 1977
         6.6,  // 1978
        18.4,  // 1979
        32.4,  // 1980
        -4.9,  // 1981
        21.6,  // 1982
        22.6,  // 1983
         6.3,  // 1984
        31.7,  // 1985
        18.7,  // 1986
         5.3,  // 1987 (Black Monday)
        16.6,  // 1988
        31.7,  // 1989
        -3.1,  // 1990
        30.5,  // 1991
         7.6,  // 1992
        10.1,  // 1993
         1.3,  // 1994
        37.6,  // 1995
        23.0,  // 1996
        33.4,  // 1997
        28.6,  // 1998
        21.0,  // 1999 (Dot-com peak)
        -9.1,  // 2000 (Dot-com bust)
       -11.9,  // 2001 (Dot-com bust)
       -22.1,  // 2002 (Dot-com bust)
        28.7,  // 2003
        10.9,  // 2004
         4.9,  // 2005
        15.8,  // 2006
         5.5,  // 2007
       -37.0,  // 2008 (Global Financial Crisis)
        26.5,  // 2009 (Recovery)
        15.1,  // 2010
         2.1,  // 2011
        16.0,  // 2012
        32.4,  // 2013
        13.7,  // 2014
         1.4,  // 2015
        12.0,  // 2016
        21.8,  // 2017
        -4.4,  // 2018
        31.5,  // 2019
        18.4,  // 2020 (COVID shock)
        28.7,  // 2021
       -18.1,  // 2022 (Inflation / rate hikes)
        26.3,  // 2023
        25.0,  // 2024
        12.5   // 2025
    )

    fun dividendTaxDragPct(settings: SettingsEntity): Double {
        return (settings.dividendYieldPct * settings.dividendTaxRatePct / 100.0).coerceAtLeast(0.0)
    }

    fun netTaxableNominalReturnPct(settings: SettingsEntity): Double {
        val drag = dividendTaxDragPct(settings)
        return max(0.0, settings.portfolioNominalReturnPct - drag)
    }

    private data class MonteCarloKey(
        val settings: SettingsEntity,
        val horizonYears: Int,
        val initialCrashPct: Double = 0.0
    )

    @Volatile
    private var cachedMcKey: MonteCarloKey? = null
    @Volatile
    private var cachedMcResult: MonteCarloResult? = null

    fun runMonteCarlo(settings: SettingsEntity, horizonYears: Int = 35, initialCrashPct: Double = 0.0): MonteCarloResult {
        val currentKey = MonteCarloKey(
            settings = settings,
            horizonYears = horizonYears,
            initialCrashPct = initialCrashPct
        )

        cachedMcResult?.let { result ->
            if (currentKey == cachedMcKey) {
                return result
            }
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct / 100.0
        val sigmaLog = sqrt(ln(1.0 + (sigma / (1.0 + meanReturn)).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(1.0 + meanReturn) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)

        // Store year-by-year cash flow additions
        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(y) else 1.0
            val baseDca = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val eDipOwn = if (!settings.isSingleHousehold) settings.eDipContributionMonthly else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0
            val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, age, settings, sy) * 12.0 else 0.0
            val pensionInflows = (settings.dipContributionMonthly + eDipOwn + settings.dpsOwnContributionMonthly + eDpsOwn + settings.employerRetirementMonthly + eEmp) * 12.0 + subV + subE

            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            Triple(baseDca + eleonoraSal + lump + pensionInflows, target, baseAge + y + 1)
        }

        val yearlyBalances = Array(horizonYears + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0
        val initialPortfolio = settings.liquidPortfolioCurrent + eLiquid + settings.dpsBalanceCurrent + eDps + settings.dipBalanceCurrent + eDip

        for (i in 0 until sims) {
            var bal = max(0.0, initialPortfolio * max(0.0, 1.0 - initialCrashPct))
            yearlyBalances[0][i] = bal
            var hitAge: Int? = null

            for (y in 0 until horizonYears) {
                val (add, target, age) = additions[y]
                val ret = exp(muLog + sigmaLog * nextGaussian(random)) - 1.0
                bal = max(0.0, bal * max(0.0, 1.0 + ret) + add * (1.0 + ret * 0.5))
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

        val result = MonteCarloResult(
            successRatePct = successRatePct,
            medianFireAge = medianFireAge,
            bestCaseAge = bestCaseAge,
            worstCaseAge = worstCaseAge,
            fanPoints = fanPoints,
            probabilityTable = probTable
        )

        cachedMcKey = currentKey
        cachedMcResult = result
        return result
    }

    private data class HistoricalMonteCarloKey(
        val settings: SettingsEntity,
        val horizonYears: Int,
        val initialCrashPct: Double = 0.0,
        val blockSize: Int = 3
    )

    @Volatile
    private var cachedHistMcKey: HistoricalMonteCarloKey? = null
    @Volatile
    private var cachedHistMcResult: MonteCarloResult? = null

    /**
     * Empirical Block-Bootstrap Monte Carlo simulation resampling from 1970–2025 equity history.
     * Preserves fat tails and multi-year sequence-of-returns drawdowns, while accounting for dividend tax drag.
     */
    fun runHistoricalMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = 35,
        initialCrashPct: Double = 0.0,
        blockSize: Int = 3
    ): MonteCarloResult {
        val currentKey = HistoricalMonteCarloKey(
            settings = settings,
            horizonYears = horizonYears,
            initialCrashPct = initialCrashPct,
            blockSize = blockSize
        )

        cachedHistMcResult?.let { result ->
            if (currentKey == cachedHistMcKey) {
                return result
            }
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val taxDrag = dividendTaxDragPct(settings)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val meanHistoricalRaw = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.average()
        val returnShift = meanReturn - (meanHistoricalRaw - taxDrag) / 100.0
        val random = Random(settings.monteCarloSeed + 101L)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)

        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(y) else 1.0
            val baseDca = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val eDipOwn = if (!settings.isSingleHousehold) settings.eDipContributionMonthly else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0
            val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, age, settings, sy) * 12.0 else 0.0
            val pensionInflows = (settings.dipContributionMonthly + eDipOwn + settings.dpsOwnContributionMonthly + eDpsOwn + settings.employerRetirementMonthly + eEmp) * 12.0 + subV + subE

            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            Triple(baseDca + eleonoraSal + lump + pensionInflows, target, baseAge + y + 1)
        }

        val yearlyBalances = Array(horizonYears + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val effectiveBlockSize = blockSize.coerceIn(1, 5)

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0
        val initialPortfolio = settings.liquidPortfolioCurrent + eLiquid + settings.dpsBalanceCurrent + eDps + settings.dipBalanceCurrent + eDip

        for (i in 0 until sims) {
            var bal = max(0.0, initialPortfolio * max(0.0, 1.0 - initialCrashPct))
            yearlyBalances[0][i] = bal
            var hitAge: Int? = null

            val pathReturns = DoubleArray(horizonYears)
            var yOffset = 0
            while (yOffset < horizonYears) {
                val startIdx = random.nextInt(nHistory)
                for (b in 0 until effectiveBlockSize) {
                    if (yOffset + b < horizonYears) {
                        val histIdx = (startIdx + b) % nHistory
                        val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                        val netReturn = max(-0.99, (rawReturn - taxDrag) / 100.0 + returnShift)
                        pathReturns[yOffset + b] = netReturn
                    }
                }
                yOffset += effectiveBlockSize
            }

            for (y in 0 until horizonYears) {
                val (add, target, age) = additions[y]
                val ret = pathReturns[y]
                bal = max(0.0, bal * max(0.0, 1.0 + ret) + add * (1.0 + ret * 0.5))
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

        val result = MonteCarloResult(
            successRatePct = successRatePct,
            medianFireAge = medianFireAge,
            bestCaseAge = bestCaseAge,
            worstCaseAge = worstCaseAge,
            fanPoints = fanPoints,
            probabilityTable = probTable
        )

        cachedHistMcKey = currentKey
        cachedHistMcResult = result
        return result
    }

    private data class SurvivalCacheKey(
        val settings: SettingsEntity,
        val useHistoricalBootstrap: Boolean
    )

    @Volatile
    private var cachedSurvivalKey: SurvivalCacheKey? = null
    @Volatile
    private var cachedSurvivalResult: RetirementSurvivalResult? = null

    /**
     * Simulates accumulation to FIRE and then a full withdrawal phase to the configured
     * retirement horizon. Success = the portfolio never depletes among paths that reach FIRE.
     * Incorporates dividend tax drag, Guyton-Klinger dynamic spending guardrails, and
     * supports both parametric log-normal and historical empirical block-bootstrap resampling.
     */
    fun runRetirementSurvival(
        settings: SettingsEntity,
        useHistoricalBootstrap: Boolean = false
    ): RetirementSurvivalResult {
        val currentKey = SurvivalCacheKey(settings, useHistoricalBootstrap)
        cachedSurvivalResult?.let { result ->
            if (cachedSurvivalKey == currentKey) return result
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct / 100.0
        val sigmaLog = sqrt(ln(1.0 + (sigma / (1.0 + meanReturn)).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(1.0 + meanReturn) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed + (if (useHistoricalBootstrap) 202L else 1L))

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val cpi = settings.cpiInflationPct / 100.0
        val maxAccumYears = 35
        val horizon = settings.retirementHorizonYears.coerceIn(10, 60)
        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val taxDrag = dividendTaxDragPct(settings)
        val meanHistoricalRaw = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.average()
        val returnShift = meanReturn - (meanHistoricalRaw - taxDrag) / 100.0
        val blockSize = 3

        val lifestyleToday = max(
            0.0,
            if (settings.lifestyleCostAtFireMonthly > 0.0) {
                settings.lifestyleCostAtFireMonthly
            } else {
                totalLivingCostMonthly(settings.copy(childExpensesEnabled = false), baseYear)
            }
        )
        val vPensionToday = max(0.0, settings.vStatePensionMonthly)
        val ePensionToday = if (!settings.isSingleHousehold) max(0.0, settings.eStatePensionMonthly) else 0.0
        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0
        val initial = max(
            0.0,
            settings.liquidPortfolioCurrent + eLiquid +
                settings.dpsBalanceCurrent + eDps +
                settings.dipBalanceCurrent + eDip
        )

        val additions = DoubleArray(maxAccumYears) { y ->
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(y) else 1.0
            val baseDca = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val eDipOwn = if (!settings.isSingleHousehold) settings.eDipContributionMonthly else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0
            val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, age, settings, sy) * 12.0 else 0.0
            val pensionInflows = (settings.dipContributionMonthly + eDipOwn + settings.dpsOwnContributionMonthly + eDpsOwn + settings.employerRetirementMonthly + eEmp) * 12.0 + subV + subE
            baseDca + eleonoraSal + lump + pensionInflows
        }

        val targets = DoubleArray(maxAccumYears) { y ->
            fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
        }

        var fireReached = 0
        var survived = 0
        var pathsWithGuardrailsTriggered = 0
        val endBalancesToday = ArrayList<Double>(sims)
        val depletionAges = ArrayList<Int>()

        for (i in 0 until sims) {
            val pathReturns = if (useHistoricalBootstrap) {
                val totalYearsNeeded = maxAccumYears + horizon + 5
                val returns = DoubleArray(totalYearsNeeded)
                var yOffset = 0
                while (yOffset < totalYearsNeeded) {
                    val startIdx = random.nextInt(nHistory)
                    for (b in 0 until blockSize) {
                        if (yOffset + b < totalYearsNeeded) {
                            val histIdx = (startIdx + b) % nHistory
                            val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                            val netReturn = max(-0.99, (rawReturn - taxDrag) / 100.0 + returnShift)
                            returns[yOffset + b] = netReturn
                        }
                    }
                    yOffset += blockSize
                }
                returns
            } else null

            var bal = initial
            var fireYear = -1
            for (y in 0 until maxAccumYears) {
                val ret = pathReturns?.get(y) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)
                bal = max(0.0, bal * max(0.0, 1.0 + ret) + additions[y] * (1.0 + ret * 0.5))
                if (bal >= targets[y]) {
                    fireYear = baseYear + y + 1
                    break
                }
            }
            if (fireYear < 0) continue
            fireReached++

            var depletedAge: Int? = null
            var guardrailTriggeredInPath = false
            for (k in 0 until horizon) {
                val year = fireYear + k
                val age = baseAge + (year - baseYear)
                val indexation = (1.0 + cpi).pow((year - baseYear).coerceAtLeast(0))
                val lifestyleAnnual = lifestyleToday * 12.0 * indexation
                var pensionAnnual = 0.0
                if (age >= settings.vStatePensionAge) pensionAnnual += vPensionToday * 12.0 * indexation
                if (!settings.isSingleHousehold && age >= settings.eStatePensionAge) {
                    pensionAnnual += ePensionToday * 12.0 * indexation
                }
                var need = max(0.0, lifestyleAnnual - pensionAnnual)

                if (settings.guardrailsEnabled && bal > 0.0) {
                    val currentWithdrawalRate = need / bal
                    val targetSwr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.001)
                    if (currentWithdrawalRate > targetSwr * 1.20) {
                        // Upper Guardrail (Capital Preservation Rule):
                        // Reduce discretionary spending by 10%, protected by a 70% essential floor
                        val essentialFloor = lifestyleAnnual * 0.70 - pensionAnnual
                        need = max(max(0.0, essentialFloor), need * 0.90)
                        guardrailTriggeredInPath = true
                    } else if (currentWithdrawalRate < targetSwr * 0.80) {
                        // Lower Guardrail (Prosperity Rule): boost spending by 10%
                        need = need * 1.10
                    }
                }

                bal -= need
                if (bal <= 0.0) {
                    bal = 0.0
                    depletedAge = age
                    break
                }
                val returnIdx = (fireYear - baseYear) + k
                val ret = pathReturns?.getOrNull(returnIdx) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)
                bal *= max(0.0, 1.0 + ret)
            }

            if (depletedAge == null) {
                survived++
            } else {
                depletionAges.add(depletedAge)
            }
            if (guardrailTriggeredInPath) {
                pathsWithGuardrailsTriggered++
            }
            val totalYearsFromBase = (fireYear - baseYear) + horizon
            val deflator = (1.0 + cpi).pow(totalYearsFromBase)
            endBalancesToday.add(if (deflator.isFinite() && deflator > 0.0) bal / deflator else bal)
        }

        val successRate = if (fireReached > 0) survived.toDouble() / fireReached * 100.0 else 0.0
        val fireProbability = (fireReached.toDouble() / sims) * 100.0
        val guardrailTriggeredPct = if (fireReached > 0 && settings.guardrailsEnabled) {
            (pathsWithGuardrailsTriggered.toDouble() / fireReached) * 100.0
        } else 0.0

        endBalancesToday.sort()
        val p5 = if (endBalancesToday.isNotEmpty()) {
            endBalancesToday[(endBalancesToday.size * 0.05).toInt().coerceIn(0, endBalancesToday.size - 1)]
        } else 0.0
        val p50 = if (endBalancesToday.isNotEmpty()) endBalancesToday[endBalancesToday.size / 2] else 0.0

        depletionAges.sort()
        val medianDepletionAge = if (depletionAges.isNotEmpty()) depletionAges[depletionAges.size / 2] else null

        val result = RetirementSurvivalResult(
            successRatePct = if (successRate.isFinite()) successRate.coerceIn(0.0, 100.0) else 0.0,
            fireProbabilityPct = if (fireProbability.isFinite()) fireProbability.coerceIn(0.0, 100.0) else 0.0,
            horizonYears = horizon,
            medianEndBalanceToday = if (p50.isFinite()) max(0.0, p50) else 0.0,
            p5EndBalanceToday = if (p5.isFinite()) max(0.0, p5) else 0.0,
            medianDepletionAge = medianDepletionAge,
            sampleSize = sims,
            guardrailsActive = settings.guardrailsEnabled,
            guardrailTriggeredPct = guardrailTriggeredPct
        )
        cachedSurvivalKey = currentKey
        cachedSurvivalResult = result
        return result
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
                else -> cpiPct
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
                buildLiquidPortfolio(mockSettings, true)
            }

            val firePoint = trajectory.firstOrNull { it.portfolio >= it.target }
            val successRate = if (runMonteCarlo) {
                runMonteCarlo(mockSettings, initialCrashPct = if (id == "crash") 0.25 else 0.0).successRatePct
            } else 0.0

            val effectiveLivingCost = if (cpiPct > settings.cpiInflationPct) {
                baseLivingCost * (1.0 + (cpiPct - settings.cpiInflationPct) / 100.0)
            } else baseLivingCost

            val emergencySurvival = if (effectiveLivingCost > 0) max(0.0, settings.emergencyReserveCurrent / effectiveLivingCost) else 0.0
            val pointAt60 = trajectory.firstOrNull { it.age >= 60 } ?: trajectory.lastOrNull()
            val nwAt60 = pointAt60?.portfolio ?: 0.0

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

    private fun buildLiquidPortfolioWithInitialCrash(settings: SettingsEntity, crashPct: Double): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = netTaxableNominalReturnPct(settings) / 100.0
        // B3 fix: respect isSingleHousehold for opening balance
        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        var bal = (settings.liquidPortfolioCurrent + eLiquid) * (1.0 - crashPct)
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
            // B3 fix: respect isSingleHousehold for DCA and apply dcaAnnualGrowthPct
            val ePortu = if (!settings.isSingleHousehold) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0)
                (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(year - sy) else 1.0
            val baseAnnual = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val reinvestAnnual = if (!settings.isSingleHousehold && year >= settings.eReturnYear) {
                eleonoraSalaryMonthly(year, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(year, settings)

            bal = max(0.0, (bal + baseAnnual + reinvestAnnual + lump) * max(0.0, 1.0 + ret))
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

    fun calculate(
        settings: SettingsEntity,
        actionStates: Map<String, Boolean> = emptyMap(),
        runMonteCarlo: Boolean = true,
        activeLedgerEntry: LedgerEntryEntity? = null,
        activeMonth: Int? = null
    ): FullCalculationState {
        val fireBase = fireTargetBase(settings)
        val dual = buildLiquidPortfolio(settings, true)
        val single = buildLiquidPortfolio(settings, false)

        val fireDualPoint = dual.firstOrNull { it.totalPortfolio >= it.target }
        val fireSinglePoint = single.firstOrNull { it.totalPortfolio >= it.target }
        val activeTrajectory = if (settings.isSingleHousehold) single else dual
        val fireLiquidBridgePoint = activeTrajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0

        val snapLiquid = if (activeLedgerEntry != null && activeLedgerEntry.portfolioBalanceAtMonthEnd > 0.0) {
            activeLedgerEntry.portfolioBalanceAtMonthEnd
        } else {
            settings.liquidPortfolioCurrent + eLiquid
        }

        val snapPension = if (activeLedgerEntry != null && activeLedgerEntry.pensionBalanceAtMonthEnd > 0.0) {
            activeLedgerEntry.pensionBalanceAtMonthEnd
        } else {
            settings.dpsBalanceCurrent + eDps + settings.dipBalanceCurrent + eDip
        }

        val snapReserve = if (activeLedgerEntry != null && activeLedgerEntry.emergencyReserveAtMonthEnd > 0.0) {
            activeLedgerEntry.emergencyReserveAtMonthEnd
        } else {
            settings.emergencyReserveCurrent
        }

        val netWorth = snapLiquid + snapReserve + snapPension

        val rReal = ((settings.portfolioNominalReturnPct - settings.cpiInflationPct) / 100.0)
        val annualLiving = totalLivingCostMonthly(settings) * 12.0
        val yearsTo60Today = max(0, 60 - settings.primaryAge)
        val bridgeReqToday = if (yearsTo60Today == 0) 0.0 else if (rReal > 0.001) {
            annualLiving * ((1.0 - (1.0 + rReal).pow(-yearsTo60Today)) / rReal)
        } else {
            annualLiving * yearsTo60Today
        }
        val currentLiquidBal = snapLiquid
        val bridgeFundedToday = if (yearsTo60Today == 0) true else currentLiquidBal >= bridgeReqToday
        val bridgeDeficitToday = max(0.0, bridgeReqToday - currentLiquidBal)

        val currentIncome = householdIncome(settings.baseYear, settings, activeLedgerEntry, activeMonth)
        val investMonthly = baseInvestMonthly(settings)
        val livingCostTotal = totalLivingCostMonthly(settings)
        val emergencyMonths = if (livingCostTotal > 0) max(0.0, settings.emergencyReserveCurrent / livingCostTotal) else 0.0

        val dps = buildDpsProjection(settings)
        val dip = buildDipProjection(settings)

        val spouseInc = spouseOwnIncomeAnnual(settings.baseYear, settings)
        // Child under 3 check: child must be born and under 3 years old
        val child1AgeAtBase = settings.baseYear - settings.child1BirthYear
        val child2AgeAtBase = settings.baseYear - settings.child2BirthYear
        val hasChildUnder3 = settings.hasChildUnder3

        val spouseEligible = !settings.isSingleHousehold &&
                settings.includeSpouseCredit &&
                hasChildUnder3 &&
                (spouseInc <= settings.spouseIncomeLimitAnnual)

        // Section 35c ZDP: either earner meeting statutory min wage multiplier qualifies the household
        val minEarnedIncome = settings.minWageMonthly * settings.childBonusMinWageMultiplier
        val vChildBonusOk = (vaclavSalaryMonthly(settings.baseYear, settings) * 12.0) >= minEarnedIncome
        val eChildBonusOk = !settings.isSingleHousehold && (eleonoraSalaryMonthly(settings.baseYear, settings) * 12.0) >= minEarnedIncome
        val childBonusOk = vChildBonusOk || eChildBonusOk

        val spouseCreditVal = if (spouseEligible) settings.spouseTaxCreditAnnual else 0.0
        val childBonusVal = if (childBonusOk) {
            var eligibleCount = 0
            if (settings.child1Enabled && child1AgeAtBase in 0..26) eligibleCount++
            if (settings.child2Enabled && child2AgeAtBase in 0..26) eligibleCount++
            var bonus = 0.0
            if (eligibleCount >= 1) bonus += settings.child1TaxBonusAnnual
            if (eligibleCount >= 2) bonus += settings.child2TaxBonusAnnual
            if (eligibleCount >= 3) bonus += settings.child3PlusTaxBonusAnnual
            bonus
        } else 0.0
        val incrementalValue = spouseCreditVal + childBonusVal + dip.taxSavedYear

        val taxHelper = TaxReturnHelperData(
            year = settings.baseYear,
            taxpayerCredit = settings.taxpayerCreditAnnual,
            spouseCredit = spouseCreditVal,
            childBonus = childBonusVal,
            retirementDeductionBase = annualRetirementDeduction(settings),
            dipSaving = dip.taxSavedYear,
            totalIncrementalValue = incrementalValue,
            spouseOwnIncome = spouseInc,
            spouseEligible = spouseEligible
        )

        val monteCarlo = if (runMonteCarlo) runMonteCarlo(settings) else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val historicalMonteCarlo = if (runMonteCarlo) runHistoricalMonteCarlo(settings) else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val retirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(settings, useHistoricalBootstrap = false)
        } else {
            RetirementSurvivalResult(
                successRatePct = 0.0,
                fireProbabilityPct = 0.0,
                horizonYears = settings.retirementHorizonYears.coerceIn(10, 60),
                medianEndBalanceToday = 0.0,
                p5EndBalanceToday = 0.0,
                medianDepletionAge = null,
                sampleSize = 0,
                guardrailsActive = settings.guardrailsEnabled,
                guardrailTriggeredPct = 0.0
            )
        }
        val historicalRetirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(settings, useHistoricalBootstrap = true)
        } else retirementSurvival
        val stressScenarios = calculateStressScenarios(settings, runMonteCarlo = runMonteCarlo)

        val savingsRate = if (currentIncome.totalMonthly > 0) {
            (investMonthly / currentIncome.totalMonthly) * 100.0
        } else 0.0

        val actionsImpacts = mapOf(
            // B1 fix: ac1 respects eIncludeLecturing toggle
            "ac1" to (if (!settings.isSingleHousehold && settings.eIncludeLecturing) settings.eLecturingMonthly * 12.0 else 0.0),
            "ac2" to taxHelper.totalIncrementalValue,
            "ac3" to (snapLiquid * 0.01),
            "ac4" to (snapReserve * 0.04),
            "ac5" to max(0.0, dip.taxSavedYear),
            "ac6" to (if (!settings.isSingleHousehold) settings.eStartingSalary * 12.0 * (settings.eReinvestedPct / 100.0) else 0.0),
            "ac8" to (settings.subscriptionsMonthly * 12.0),
            "ac9" to max(0.0, snapReserve - settings.emergencyReserveTarget),
            "ac10" to (snapPension * 0.005)
        )

        val investableNetWorth = snapLiquid + snapPension

        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.01)
        val nominalFactor = 1.0 + settings.portfolioNominalReturnPct / 100.0
        val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.01)
        val realReturnFactor = nominalFactor / inflationFactor
        val realReturnRate = realReturnFactor - 1.0
        val yearsToRetire = max(1, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)

        // 1. Coast FIRE
        // If real return <= 0 (stagflation or zero return), portfolio loses or preserves purchasing power.
        // It cannot grow on its own to cover the FIRE target without ongoing contributions.
        val coastRawTarget = if (realReturnRate > 0.0) {
            fireBase / (1.0 + realReturnRate).pow(yearsToRetire)
        } else {
            fireBase / realReturnFactor.coerceAtLeast(0.01).pow(yearsToRetire)
        }
        val coastTarget = kotlin.math.round(coastRawTarget / 10_000.0) * 10_000.0
        val coastProgress = if (coastTarget > 0) ((investableNetWorth / coastTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val coastAchieved = realReturnRate > 0.0 && investableNetWorth >= coastTarget
        val coastPoint = if (coastAchieved) dual.firstOrNull() else if (realReturnRate > 0.0) dual.firstOrNull { point ->
            val yDiff = point.year - settings.baseYear
            val futureTarget = coastTarget * cpiCompounding.pow(yDiff)
            point.portfolio >= futureTarget
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
            monthlyPassiveIncome = kotlin.math.round(((fireBase * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = coastProgress,
            isAchieved = coastAchieved,
            estimatedAge = if (coastAchieved) settings.primaryAge else coastPoint?.age,
            estimatedYear = if (coastAchieved) settings.baseYear else coastPoint?.year
        )

        // 1b. Barista FIRE (50% baseline living expenses)
        val baristaRawTarget = fireBase * 0.50
        val baristaTarget = kotlin.math.round(baristaRawTarget / 10_000.0) * 10_000.0
        val baristaProgress = if (baristaTarget > 0) ((investableNetWorth / baristaTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val baristaAchieved = investableNetWorth >= baristaTarget
        val baristaPoint = if (baristaAchieved) dual.firstOrNull() else dual.firstOrNull { point ->
            val yDiff = point.year - settings.baseYear
            val futureTarget = baristaTarget * cpiCompounding.pow(yDiff)
            point.portfolio >= futureTarget
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

        // 2. Lean FIRE (75% baseline living expenses)
        val leanRawTarget = fireBase * 0.75
        val leanTarget = kotlin.math.round(leanRawTarget / 10_000.0) * 10_000.0
        val leanProgress = if (leanTarget > 0) ((investableNetWorth / leanTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val leanAchieved = investableNetWorth >= leanTarget
        val leanPoint = if (leanAchieved) dual.firstOrNull() else dual.firstOrNull { point ->
            val yDiff = point.year - settings.baseYear
            val futureTarget = leanTarget * cpiCompounding.pow(yDiff)
            point.portfolio >= futureTarget
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

        // 3. Standard FIRE (100% baseline living expenses)
        val standardRawTarget = fireBase
        val standardTarget = kotlin.math.round(standardRawTarget / 10_000.0) * 10_000.0
        val standardProgress = if (standardTarget > 0) ((investableNetWorth / standardTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val standardAchieved = investableNetWorth >= standardTarget
        val standardPoint = fireDualPoint
        val standardMilestone = FireMilestone(
            id = "standard",
            name = "Standard FIRE",
            badgeLabel = "Full Independence",
            description = "Covers 100% of current comfortable household lifestyle without needing employment income.",
            targetAmountToday = standardTarget,
            monthlyPassiveIncome = kotlin.math.round(((standardTarget * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = standardProgress,
            isAchieved = standardAchieved,
            estimatedAge = if (standardAchieved) settings.primaryAge else standardPoint?.age,
            estimatedYear = if (standardAchieved) settings.baseYear else standardPoint?.year
        )

        // 4. Fat FIRE (130% baseline living expenses)
        val fatRawTarget = fireBase * 1.30
        val fatTarget = kotlin.math.round(fatRawTarget / 10_000.0) * 10_000.0
        val fatProgress = if (fatTarget > 0) ((investableNetWorth / fatTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val fatAchieved = investableNetWorth >= fatTarget
        val fatPoint = if (fatAchieved) dual.firstOrNull() else dual.firstOrNull { point ->
            val yDiff = point.year - settings.baseYear
            val futureTarget = fatTarget * cpiCompounding.pow(yDiff)
            point.portfolio >= futureTarget
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

        val fireMilestones = FireMilestonesSummary(
            coastFire = coastMilestone,
            baristaFire = baristaMilestone,
            leanFire = leanMilestone,
            standardFire = standardMilestone,
            fatFire = fatMilestone
        )

        val perpetualMultiplier = perpetualFireLeverageMultiplier(settings)
        val fireReductionFor100 = 100.0 * perpetualMultiplier

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
            realReturnPct = (realReturnFactor - 1.0) * 100.0,
            dps = dps,
            dip = dip,
            taxReturnHelper = taxHelper,
            monteCarlo = monteCarlo,
            historicalMonteCarlo = historicalMonteCarlo,
            retirementSurvival = retirementSurvival,
            historicalRetirementSurvival = historicalRetirementSurvival,
            stressScenarios = stressScenarios,
            fireMilestones = fireMilestones,
            savingsRatePct = savingsRate,
            totalLivingCostMonthly = livingCostTotal,
            netWorthTotal = netWorth,
            actionsImpacts = actionsImpacts,
            perpetualFireMultiplier = perpetualMultiplier,
            fireReductionPer100CzkMonthly = fireReductionFor100,
            fireLiquidBridgePoint = fireLiquidBridgePoint,
            liquidBridgeTo60RequiredToday = bridgeReqToday,
            isLiquidBridgeFundedToday = bridgeFundedToday,
            liquidBridgeDeficitToday = bridgeDeficitToday,
            currentLiquidPortfolio = snapLiquid,
            currentPensionPortfolio = snapPension
        )
    }
}
