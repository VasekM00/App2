package com.example.domain

import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.cos
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
            list.add(
                CustomLumpSumItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Lump Sum"),
                    year = obj.optInt("year", 2030),
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
    val stressScenarios: List<StressScenarioResult>,
    val fireMilestones: FireMilestonesSummary,
    val savingsRatePct: Double,
    val totalLivingCostMonthly: Double,
    val netWorthTotal: Double,
    val actionsImpacts: Map<String, Double>
)

object FinancialEngine {

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

        val lifestyleMonthly = if (settings.lifestyleCostAtFireMonthly > 0.0) settings.lifestyleCostAtFireMonthly else totalLivingCostMonthly(settings, settings.baseYear)
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
        return settings.vSalary + settings.vOtherInflowsMonthly
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
        val baseBenefit = max(0.0, settings.eParentalAllowanceMonthly)
        return if (year == settings.eReturnYear) {
            val month = settings.eReturnMonth.coerceIn(1, 12)
            val leaveFraction = (month - 1) / 12.0
            baseBenefit * leaveFraction
        } else {
            baseBenefit
        }
    }

    private fun parentalAllowancePot(birthYear: Int): Double {
        return if (birthYear >= RegulatoryConstants.PARENTAL_ALLOWANCE_HIGHER_TOTAL_CUTOFF_YEAR) {
            RegulatoryConstants.PARENTAL_ALLOWANCE_TOTAL_FROM_CUTOFF
        } else {
            RegulatoryConstants.PARENTAL_ALLOWANCE_TOTAL_BEFORE_CUTOFF
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

    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold) return 0.0
        val sal = eleonoraSalaryMonthly(year, settings)
        val lec = eleonoraLecturingMonthly(year, settings)
        val oth = settings.eOtherInflowsMonthly
        return (sal + lec + oth) * 12.0
    }

    fun householdIncome(year: Int, settings: SettingsEntity): YearlyIncome {
        val v = vaclavSalaryMonthly(year, settings)
        val e = eleonoraSalaryMonthly(year, settings)
        val b = eleonoraBenefitMonthly(year, settings)
        val lec = eleonoraLecturingMonthly(year, settings)
        val gift = settings.familyGiftMonthly
        val eOther = settings.eOtherInflowsMonthly
        val total = v + e + b + lec + gift + settings.vMealVouchersMonthly + eOther

        return YearlyIncome(
            year = year,
            vaclavNet = v,
            eleonoraSalary = e,
            benefit = b,
            lecturing = lec,
            vouchers = settings.vMealVouchersMonthly,
            gift = gift,
            totalMonthly = total
        )
    }

    // Lepší penzijko Reform: State Subsidy calculation with customizable threshold, rates, youth cutoff and caps
    // (Standard: 20% max 340 CZK; Youth < 30 y/o: 40% max 680 CZK effective starting from 2027)
    fun dpsSubsidy(monthlyDeposit: Double, age: Int, settings: SettingsEntity? = null, year: Int = settings?.baseYear ?: 2026): Double {
        val minDeposit = settings?.dpsMinDepositForSubsidy ?: 500.0
        if (monthlyDeposit < minDeposit) return 0.0
        val youthAge = settings?.dpsYouthAgeLimit ?: 30
        val stdRate = (settings?.dpsSubsidyRateStandardPct ?: 20.0) / 100.0
        val youthRate = (settings?.dpsSubsidyRateYouthPct ?: 40.0) / 100.0
        val isYouthEligible = (age < youthAge) && (year >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR)
        val rate = if (isYouthEligible) youthRate else stdRate
        val youthCap = settings?.dpsYouthSubsidyMaxMonthly ?: 680.0
        val standardCap = settings?.dpsStandardSubsidyMaxMonthly ?: 340.0
        val maxSub = if (isYouthEligible) youthCap else standardCap
        return min(monthlyDeposit * rate, maxSub)
    }

    fun annualRetirementDeduction(settings: SettingsEntity): Double {
        val vDipAnnual = settings.dipContributionMonthly * 12.0
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val vDeduction = min(vDipAnnual + vDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        val eDipAnnual = settings.eDipContributionMonthly * 12.0
        val eDpsAboveThreshold = max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val eDeduction = min(eDipAnnual + eDpsAboveThreshold, settings.taxDeductionCeilingAnnual)

        return vDeduction + eDeduction
    }

    private fun singleEarnerRetirementTaxSaved(
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

    fun dipTaxSavingYear(settings: SettingsEntity): Double {
        val threshold = settings.taxSecondBracketThresholdAnnual
        val baseRate = settings.taxRatePct / 100.0
        val highRate = settings.taxRateSecondPct / 100.0
        val credit = settings.taxpayerCreditAnnual

        // Václav saving (DIP + DPS qualifying combined)
        val vDpsAbove = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val vDip = settings.dipContributionMonthly * 12.0
        val vDeduction = min(vDip + vDpsAbove, settings.taxDeductionCeilingAnnual)
        // Approximate gross from net: net ≈ gross × 0.85 (employee social 7.1% + health 4.5% + effective tax wedge)
        val vTaxableBase = (vaclavSalaryMonthly(settings.baseYear, settings) * 12.0) / 0.85
        val vSaving = singleEarnerRetirementTaxSaved(vTaxableBase, vDeduction, threshold, baseRate, highRate, credit)

        // Eleonora saving
        val eDpsAbove = max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0
        val eDip = settings.eDipContributionMonthly * 12.0
        val eDeduction = min(eDip + eDpsAbove, settings.taxDeductionCeilingAnnual)
        val eTaxableBase = (eleonoraSalaryMonthly(settings.baseYear, settings) * 12.0) / 0.85
        val eSaving = singleEarnerRetirementTaxSaved(eTaxableBase, eDeduction, threshold, baseRate, highRate, credit)

        return vSaving + eSaving
    }

    fun childMonthlyExpense(birthYear: Int, currentYear: Int, settings: SettingsEntity): Double {
        val age = currentYear - birthYear
        return when (age) {
            in 0..2 -> settings.childToddlerMonthly
            in 3..5 -> settings.childPreschoolMonthly
            in 6..14 -> settings.childSchoolMonthly
            in 15..18 -> settings.childTeenMonthly
            in 19..25 -> settings.childUniMonthly
            else -> 0.0
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
            if (settings.child1Enabled) {
                base += childMonthlyExpense(settings.child1BirthYear, year, settings)
            }
            if (settings.child2Enabled) {
                base += childMonthlyExpense(settings.child2BirthYear, year, settings)
            }
        }
        return base
    }

    fun baseInvestMonthly(settings: SettingsEntity): Double {
        val vaclavInvest = settings.portuDcaMonthly + settings.dpsOwnContributionMonthly +
                settings.dipContributionMonthly + min(settings.employerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)
        val eleonoraInvest = if (!settings.isSingleHousehold) {
            settings.ePortuDcaMonthly + settings.eDpsOwnContributionMonthly +
                    settings.eDipContributionMonthly + min(settings.eEmployerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)
        } else 0.0
        return vaclavInvest + eleonoraInvest
    }

    fun buildLiquidPortfolio(settings: SettingsEntity, dualIncome: Boolean): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = settings.portfolioNominalReturnPct / 100.0
        val includeSpouse = dualIncome && !settings.isSingleHousehold
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent else 0.0
        var bal = settings.liquidPortfolioCurrent + eLiquid
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
            val ePortu = if (includeSpouse) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(year - sy) else 1.0
            val baseAnnual = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val reinvestAnnual = if (includeSpouse && year >= settings.eReturnYear) {
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

    // Lepší penzijko Reform Projection: Capped 0.5% TER & One-Third Partial Withdrawal at Age 36
    fun buildDpsProjection(settings: SettingsEntity): DpsProjection {
        val years = max(0, 60 - settings.primaryAge)
        val fee = min(settings.dpsAnnualFeePct, RegulatoryConstants.LEPSI_PENZIJKO_STATUTORY_FEE_CAP_PCT) // Statutory 0.5% cap
        val annualRateDPS = max(-0.99, (settings.dpsGrossReturnPct - fee) / 100.0)
        val monthlyRateDPS = (1.0 + annualRateDPS).pow(1.0 / 12.0) - 1.0

        val annualRateETF = max(-0.99, settings.portfolioNominalReturnPct / 100.0)
        val monthlyRateETF = (1.0 + annualRateETF).pow(1.0 / 12.0) - 1.0

        val eDpsOwn = if (!settings.isSingleHousehold) settings.eDpsOwnContributionMonthly else 0.0
        val eDpsBal = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eEmp = if (!settings.isSingleHousehold) settings.eEmployerRetirementMonthly else 0.0

        val own = settings.dpsOwnContributionMonthly + eDpsOwn
        val emp = min(settings.employerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0) +
                min(eEmp, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)

        var dpsBal = settings.dpsBalanceCurrent + eDpsBal
        var etfBal = settings.dpsBalanceCurrent + eDpsBal
        var totalSubsidy = 0.0
        var totalOwn = 0.0
        var totalEmp = 0.0

        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            val currentYear = settings.baseYear + (m / 12)
            val currentAge = settings.primaryAge + (m / 12)
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, currentAge, settings, currentYear)
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, currentAge, settings, currentYear) else 0.0
            val sub = subV + subE

            totalSubsidy += sub
            totalOwn += own
            totalEmp += emp

            dpsBal = max(0.0, (dpsBal + own + sub + emp) * max(0.0, 1.0 + monthlyRateDPS))
            etfBal = max(0.0, (etfBal + own + emp) * max(0.0, 1.0 + monthlyRateETF))
        }

        // B4 fix: balAt36 respects isSingleHousehold for Eleonora's balance
        val monthsTo36 = max(0, (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - settings.primaryAge) * 12)
        var balAt36 = settings.dpsBalanceCurrent + eDpsBal  // eDpsBal already 0.0 in single mode
        var ownValueTo36 = 0.0
        if (monthsTo36 in 1..totalMonths) {
            for (m in 0 until monthsTo36) {
                val currentYear = settings.baseYear + (m / 12)
                val currentAge = settings.primaryAge + (m / 12)
                val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, currentAge, settings, currentYear)
                val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, currentAge, settings, currentYear) else 0.0
                ownValueTo36 = max(0.0, (ownValueTo36 + own) * max(0.0, 1.0 + monthlyRateDPS))
                balAt36 = max(0.0, (balAt36 + own + subV + subE + emp) * max(0.0, 1.0 + monthlyRateDPS))
            }
        }

        // Statutory one-third withdrawal basis: OWN deposits AND their appreciation only
        // (state subsidies and employer contributions are excluded from the basis),
        // and requires at least 10 years of saving by age 36 (i.e. started at age 26 or younger).
        val tenYearRuleMet = settings.primaryAge <= RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE - 10
        val earlyWithdrawalLimitAt36 = if (tenYearRuleMet) {
            ownValueTo36 * (RegulatoryConstants.LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT / 100.0)
        } else 0.0

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
            youthSubsidyActive = (settings.primaryAge < settings.dpsYouthAgeLimit) && (settings.baseYear >= RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR)
        )
    }

    fun buildDipProjection(settings: SettingsEntity): DipProjection {
        val years = max(0, 60 - settings.primaryAge)
        val tsYear = dipTaxSavingYear(settings)
        val vDipMonthly = settings.dipContributionMonthly
        val eDipMonthly = settings.eDipContributionMonthly
        val totalMonthlyDip = vDipMonthly + eDipMonthly
        val vDpsAboveThreshold = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0

        val levels = listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0)
        val scenarios = levels.map { monthly: Double ->
            val scenarioSettings = settings.copy(dipContributionMonthly = monthly, eDipContributionMonthly = 0.0)
            val asave = dipTaxSavingYear(scenarioSettings)
            val dipAnnual = monthly * 12.0
            
            val risk = when {
                monthly >= 4000.0 -> "Statutory Max"
                monthly >= 2500.0 -> "Balanced"
                monthly > 0.0 -> "Light"
                else -> "None"
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
        var dipBal = settings.dipBalanceCurrent + settings.eDipBalanceCurrent
        val totalMonths = years * 12
        for (m in 0 until totalMonths) {
            dipBal = max(0.0, (dipBal + totalMonthlyDip) * max(0.0, 1.0 + monthlyRate))
        }

        val totalCeiling = settings.taxDeductionCeilingAnnual * 2.0
        val totalUtilized = annualRetirementDeduction(settings)

        return DipProjection(
            taxSavedYear = tsYear,
            netCostMonthly = totalMonthlyDip - tsYear / 12.0,
            scenarios = scenarios,
            headroom = max(0.0, totalCeiling - totalUtilized),
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

        val sims = settings.monteCarloN.coerceIn(100, 400)
        val meanReturn = settings.portfolioNominalReturnPct / 100.0
        val sigma = settings.monteCarloVolatilityPct / 100.0
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)

        // Store year-by-year cash flow additions
        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val ePortu = if (!settings.isSingleHousehold) settings.ePortuDcaMonthly else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct > 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).pow(y) else 1.0
            val baseDca = (settings.portuDcaMonthly + ePortu) * 12.0 * dcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            Triple(baseDca + eleonoraSal + lump, target, baseAge + y + 1)
        }

        val yearlyBalances = Array(horizonYears + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        for (i in 0 until sims) {
            val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
            var bal = max(0.0, (settings.liquidPortfolioCurrent + eLiquid) * max(0.0, 1.0 - initialCrashPct))
            yearlyBalances[0][i] = bal
            var hitAge: Int? = null

            for (y in 0 until horizonYears) {
                val (add, target, age) = additions[y]
                val ret = max(-0.99, meanReturn + nextGaussian(random) * sigma)
                bal = max(0.0, (bal + add) * max(0.0, 1.0 + ret))
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

            val emergencySurvival = if (effectiveLivingCost > 0) settings.emergencyReserveCurrent / effectiveLivingCost else 0.0
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
        val ret = settings.portfolioNominalReturnPct / 100.0
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
        runMonteCarlo: Boolean = true
    ): FullCalculationState {
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
        // Child under 3 check: child must be born and under 3 years old
        val child1AgeAtBase = settings.baseYear - settings.child1BirthYear
        val child2AgeAtBase = settings.baseYear - settings.child2BirthYear
        val childAgeValid = (settings.child1Enabled && child1AgeAtBase in 0..2) || (settings.child2Enabled && child2AgeAtBase in 0..2)
        val hasChildUnder3 = settings.hasChildUnder3 && childAgeValid

        val spouseEligible = settings.includeSpouseCredit &&
                hasChildUnder3 &&
                (spouseInc <= settings.spouseIncomeLimitAnnual)

        // D3 fix: use named constant for 6x min wage multiplier (ZDP § 35c)
        val childBonusOk = (vaclavSalaryMonthly(settings.baseYear, settings) * 12.0) >=
                (settings.minWageMonthly * RegulatoryConstants.STATUTORY_CHILD_BONUS_MIN_WAGE_MULTIPLIER)

        val spouseCreditVal = if (spouseEligible) settings.spouseTaxCreditAnnual else 0.0
        val childBonusVal = if (childBonusOk) {
            var bonus = 0.0
            if (settings.child1Enabled && child1AgeAtBase in 0..26) bonus += settings.child1TaxBonusAnnual
            if (settings.child2Enabled && child2AgeAtBase in 0..26) bonus += settings.child2TaxBonusAnnual
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
        val stressScenarios = calculateStressScenarios(settings, runMonteCarlo = runMonteCarlo)

        val savingsRate = if (currentIncome.totalMonthly > 0) {
            (investMonthly / currentIncome.totalMonthly) * 100.0
        } else 0.0

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent else 0.0

        val netWorth = settings.liquidPortfolioCurrent + eLiquid +
                settings.emergencyReserveCurrent + settings.dpsBalanceCurrent + eDps +
                settings.dipBalanceCurrent + eDip

        val actionsImpacts = mapOf(
            // B1 fix: ac1 respects eIncludeLecturing toggle
            "ac1" to (if (!settings.isSingleHousehold && settings.eIncludeLecturing) settings.eLecturingMonthly * 12.0 else 0.0),
            "ac2" to taxHelper.totalIncrementalValue,
            "ac3" to ((settings.liquidPortfolioCurrent + eLiquid) * 0.01),
            "ac4" to (settings.emergencyReserveCurrent * 0.04),
            "ac5" to max(0.0, dip.taxSavedYear),
            "ac6" to (if (!settings.isSingleHousehold) settings.eStartingSalary * 12.0 * (settings.eReinvestedPct / 100.0) else 0.0),
            "ac7" to 0.0,
            "ac8" to (settings.subscriptionsMonthly * 12.0),
            "ac9" to max(0.0, settings.emergencyReserveCurrent - settings.emergencyReserveTarget),
            "ac10" to ((settings.dpsBalanceCurrent + eDps) * 0.005),
            "ac11" to 0.0,
            "ac12" to 0.0
        )

        val investableNetWorth = settings.liquidPortfolioCurrent + eLiquid +
                settings.dpsBalanceCurrent + eDps +
                settings.dipBalanceCurrent + eDip

        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.01)
        val nominalFactor = 1.0 + settings.portfolioNominalReturnPct / 100.0
        val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.01)
        val realReturnFactor = nominalFactor / inflationFactor
        val realReturnRate = (realReturnFactor - 1.0).coerceAtLeast(0.001)
        val yearsToRetire = max(1, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)

        // 1. Coast FIRE
        val coastRawTarget = fireBase / (1.0 + realReturnRate).pow(yearsToRetire)
        val coastTarget = kotlin.math.round(coastRawTarget / 10_000.0) * 10_000.0
        val coastProgress = if (coastTarget > 0) ((investableNetWorth / coastTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val coastAchieved = investableNetWorth >= coastTarget
        val coastPoint = if (coastAchieved) dual.firstOrNull() else dual.firstOrNull { point ->
            val yDiff = point.year - settings.baseYear
            val futureTarget = coastTarget * cpiCompounding.pow(yDiff)
            point.portfolio >= futureTarget
        }
        val coastMilestone = FireMilestone(
            id = "coast",
            name = "Coast FIRE",
            badgeLabel = "Compound Only",
            description = "Existing investments grow to full FIRE target by age ${settings.vStatePensionAge} with 0 additional contributions.",
            targetAmountToday = coastTarget,
            monthlyPassiveIncome = kotlin.math.round(((fireBase * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = coastProgress,
            isAchieved = coastAchieved,
            estimatedAge = if (coastAchieved) settings.primaryAge else coastPoint?.age,
            estimatedYear = if (coastAchieved) settings.baseYear else coastPoint?.year
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
            leanFire = leanMilestone,
            standardFire = standardMilestone,
            fatFire = fatMilestone
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
            realReturnPct = (realReturnFactor - 1.0) * 100.0,
            dps = dps,
            dip = dip,
            taxReturnHelper = taxHelper,
            monteCarlo = monteCarlo,
            stressScenarios = stressScenarios,
            fireMilestones = fireMilestones,
            savingsRatePct = savingsRate,
            totalLivingCostMonthly = livingCostTotal,
            netWorthTotal = netWorth,
            actionsImpacts = actionsImpacts
        )
    }
}
