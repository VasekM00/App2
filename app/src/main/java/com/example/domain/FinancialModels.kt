package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.SettingsEntity


@Immutable
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

@Immutable
data class CustomExpenseItem(
    val id: String,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int? = null
)

@Immutable
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
            val parsedDay = if (obj.has("dayOfMonth") && !obj.isNull("dayOfMonth")) {
                val d = obj.optInt("dayOfMonth", 25)
                if (d in 1..31) d else null
            } else {
                null
            }
            list.add(
                CustomExpenseItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Custom Expense"),
                    amount = obj.optDouble("amount", 0.0).finiteNonNegative(),
                    dayOfMonth = parsedDay
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
        if (item.dayOfMonth != null) {
            obj.put("dayOfMonth", item.dayOfMonth.coerceIn(1, 31))
        }
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

fun parseAccountListJson(jsonStr: String): List<String> {
    if (jsonStr.isBlank() || jsonStr.trim() == "[]") return emptyList()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val result = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optString(i, "").trim()
            if (item.isNotBlank()) result.add(item)
        }
        result.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

fun serializeAccountListJson(items: Collection<String>): String {
    val array = org.json.JSONArray()
    items.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .forEach { array.put(it) }
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
                    targetAmountCzk = obj.optDouble("targetAmountCzk", 500_000.0).finiteNonNegative(),
                    currentSavedCzk = obj.optDouble("currentSavedCzk", 0.0).finiteNonNegative()
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

@Immutable
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
                    amount = obj.optDouble("amount", 0.0).finiteOr(0.0),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        list.sortedWith(compareBy({ it.year }, { it.month ?: 0 }, { it.name }, { it.id }))
    } catch (_: Exception) {
        emptyList()
    }
}

fun serializeCustomLumpSums(items: List<CustomLumpSumItem>): String {
    val sortedItems = items.sortedWith(compareBy({ it.year }, { it.month ?: 0 }, { it.name }, { it.id }))
    val array = org.json.JSONArray()
    sortedItems.forEach { item ->
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

internal fun lumpSumForYear(
    year: Int,
    settings: SettingsEntity,
    parsedLumpSums: List<CustomLumpSumItem>
): Double {
    var total = 0.0
    if (settings.lumpSumInclude && year == settings.lumpSumYear) {
        total += settings.lumpSumAmount.finiteOr(0.0)
    }
    for (item in parsedLumpSums) {
        if (item.enabled && item.year == year) {
            total += item.amount.finiteOr(0.0)
        }
    }
    return total.finiteOr(0.0)
}

fun lumpSumForYear(year: Int, settings: SettingsEntity): Double =
    lumpSumForYear(year, settings, parseCustomLumpSums(settings.customLumpSumsJson))

fun lumpSumsForMonth(year: Int, month: Int, settings: SettingsEntity): List<CustomLumpSumItem> {
    val additional = parseCustomLumpSums(settings.customLumpSumsJson)
    return additional.filter { it.enabled && it.year == year && it.month == month }
}

@Immutable
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
    val isLiquidBridgeFunded: Boolean = true,
    val pensionInflowAnnual: Double = 0.0,
    val dipPortfolio: Double = 0.0,
    val dpsPortfolio: Double = 0.0,
    val emergencyCash: Double = 0.0
)

@Immutable
data class DpsScenario(
    val monthly: Double,
    val annual: Double,
    val monthlySubsidy: Double,
    val annualSubsidy: Double,
    val annualTaxSaved: Double,
    val totalAnnualBenefit: Double,
    val badgeLabel: String?
)

@Immutable
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
    val scenarios: List<DpsScenario> = emptyList(),
    val dpsGrossBalance: Double = dpsBalance,
    val lumpSumExitTax: Double = 0.0,
    val unlockAge: Int = 60
)

@Immutable
data class DipScenario(
    val monthly: Double,
    val annual: Double,
    val annualTaxSaved: Double,
    val netCostMonthly: Double,
    val headroom: Double,
    val riskLevel: String
)

@Immutable
data class DipProjection(
    val taxSavedYear: Double,
    val netCostMonthly: Double,
    val scenarios: List<DipScenario>,
    val headroom: Double,
    val dipBalanceAt60: Double,
    val unlockAge: Int = 60
)

@Immutable
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

@Immutable
data class MonteCarloPoint(
    val year: Int,
    val age: Int,
    val p5: Double,
    val p50: Double,
    val p95: Double,
    val target: Double
)

@Immutable
data class MonteCarloAgeProbability(
    val age: Int,
    val probabilityPct: Double
)

@Immutable
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
@Immutable
data class RetirementSurvivalResult(
    val successRatePct: Double,
    val fireProbabilityPct: Double,
    val horizonYears: Int,
    val medianEndBalanceToday: Double,
    val p5EndBalanceToday: Double,
    val medianDepletionAge: Int?,
    val sampleSize: Int,
    val guardrailsActive: Boolean = false,
    val guardrailTriggeredPct: Double = 0.0,
    val fireReachedCount: Int = sampleSize
) {
    val isLowConfidenceSample: Boolean
        get() = fireReachedCount in 1 until 30
}

@Immutable
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

@Immutable
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

@Immutable
data class FireMilestonesSummary(
    val coastFire: FireMilestone,
    val baristaFire: FireMilestone? = null,
    val leanFire: FireMilestone,
    val standardFire: FireMilestone,
    val fatFire: FireMilestone
)

@Immutable
data class YtdTaxShieldStatus(
    val currentYear: Int,
    val statutoryCeilingAnnual: Double = 48000.0,
    val ytdDipContributed: Double,
    val ytdDpsContributed: Double,
    val ytdTotalContributed: Double,
    val ytdProgressPct: Double,
    val taxRefundSecured15Pct: Double,
    val taxRefundSecured23Pct: Double,
    val remainingHeadroomAnnual: Double,
    val monthsRemainingInYear: Int,
    val recommendedMonthlyPace: Double,
    val isCeilingReached: Boolean,
    val isFromImportedTransactions: Boolean = false,
    val partnerYtdDipContributed: Double = 0.0,
    val partnerYtdDpsContributed: Double = 0.0,
    val partnerRemainingHeadroomAnnual: Double = 0.0,
    val householdCeilingAnnual: Double = statutoryCeilingAnnual
)

@Immutable
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
    val currentPensionPortfolio: Double = 0.0,
    val currentEmergencyReserve: Double = 0.0,
    val effectiveEmergencyReserveTargetToday: Double = 0.0,
    val isEmergencyReserveFundedToday: Boolean = true,
    val emergencyReserveSurplusOrDeficitToday: Double = 0.0,
    val trailingBurnRate3mMonthly: Double? = null,
    val trailingBurnRate12mMonthly: Double? = null,
    val ytdTaxShieldStatus: YtdTaxShieldStatus? = null,
    val decumulationComparison: DecumulationComparisonResult? = null,
    val yearEndTaxChecklist: YearEndTaxChecklistReport? = null,
    val capitalGainsSummary: CapitalGainsPortfolioSummary? = null,
    val effectiveSettings: SettingsEntity = settings,
    val snapshot: CanonicalHouseholdSnapshot = CanonicalHouseholdSnapshot.EMPTY,
    val pensionOptimization: PensionOptimizationSnapshot = PensionOptimizationSnapshot.EMPTY,
    val bridgeYearsFromFireTo60: Int = 0,
    val bridgeRequiredAtFire: Double = 0.0,
    val isBridgeFundedAtFire: Boolean = true,
    val bridgeDeficitAtFire: Double = 0.0
)

@Immutable
data class CanonicalHouseholdSnapshot(
    val isSingleHousehold: Boolean = false,
    val netWorthTotal: Double = 0.0,
    val investedLiquidTotal: Double = 0.0,
    val pensionTotal: Double = 0.0,
    val emergencyCashTotal: Double = 0.0,
    val monthlyTakeHomeInflows: Double = 0.0,
    val monthlyEmployerMatch: Double = 0.0,
    val monthlyTotalInflowsWithMatch: Double = 0.0,
    val monthlyLivingCosts: Double = 0.0,
    val monthlyOwnInvesting: Double = 0.0,
    val monthlyTotalInvesting: Double = 0.0,
    val monthlyReserveContribution: Double = 0.0,
    val monthlySavingsAndInvestingTotal: Double = 0.0,
    val monthlyOwnSavingsAndInvestingTotal: Double = 0.0,
    val monthlyLeftoverBeforeInvesting: Double = 0.0,
    val monthlyUnallocatedCash: Double = 0.0,
    val savingsRatePct: Double = 0.0,
    val savingsRateOwnCashPct: Double = 0.0,
    val emergencyRunwayMonths: Double = 0.0,
    val emergencyReserveTargetToday: Double = 0.0,
    val isEmergencyReserveFundedToday: Boolean = true,
    val emergencyReserveSurplusOrDeficitToday: Double = 0.0,
    val activeFireTargetToday: Double = 0.0,
    val activeFirePoint: PortfolioYearPoint? = null,
    val activeFireYear: Int? = null,
    val activeFireAge: Int? = null,
    val unconstrainedFirePoint: PortfolioYearPoint? = null,
    val unconstrainedFireYear: Int? = null,
    val unconstrainedFireAge: Int? = null,
    val isFireAchievedToday: Boolean = false,
    val fireProgressPct: Double = 0.0,
    val monthlyPassiveIncomeAtFire: Double = 0.0,
    val perpetualFireMultiplier: Double = 330.0,
    val fireReductionPer100CzkMonthly: Double = 33_000.0,
    val taxRefundAnnualTotal: Double = 0.0,
    val historicalFireSuccessPct: Double = 0.0,
    val bridgeRequiredToday: Double = 0.0,
    val isBridgeFundedToday: Boolean = true,
    val bridgeDeficitToday: Double = 0.0,
    val bridgeWaitYears: Int = 0,
    val bridgeYearsFromFireTo60: Int = 0,
    val bridgeRequiredAtFire: Double = 0.0,
    val isBridgeFundedAtFire: Boolean = true,
    val bridgeDeficitAtFire: Double = 0.0
) {
    companion object {
        val EMPTY = CanonicalHouseholdSnapshot()
    }
}

@Immutable
data class PensionOptimizationSnapshot(
    val primaryAge: Int = 26,
    val partnerAge: Int = 26,
    val primaryStatePensionAge: Int = 67,
    val partnerStatePensionAge: Int = 67,
    val primarySubsidyMonthly: Double = 0.0,
    val partnerSubsidyMonthly: Double = 0.0,
    val totalSubsidyMonthly: Double = 0.0,
    val primaryAnnualTaxSaved: Double = 0.0,
    val partnerAnnualTaxSaved: Double = 0.0,
    val totalAnnualTaxSaved: Double = 0.0,
    val primarySubsidyMaxAnnual: Double = 0.0,
    val partnerSubsidyMaxAnnual: Double = 0.0,
    val primaryOptimalDepositAnnual: Double = 0.0,
    val partnerOptimalDepositAnnual: Double = 0.0,
    val totalOptimalDepositAnnual: Double = 0.0,
    val maxCombinedBenefitAnnual: Double = 0.0,
    val totalMonthlyOwnRetirement: Double = 0.0,
    val totalMonthlyRetirementWithMatch: Double = 0.0,
    val dpsNetReturnPct: Double = 0.0,
    val primaryDpsDeductionThresholdMonthly: Double = 1_700.0,
    val partnerDpsDeductionThresholdMonthly: Double = 1_700.0,
    val primaryHasIncome: Boolean = true,
    val partnerHasIncome: Boolean = false,
    val primaryGrossAnnual: Double = 0.0,
    val partnerGrossAnnual: Double = 0.0
) {
    companion object {
        val EMPTY = PensionOptimizationSnapshot()
    }
}

