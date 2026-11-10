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
    val isLiquidBridgeFunded: Boolean = true,
    val pensionInflowAnnual: Double = 0.0
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
    val isCeilingReached: Boolean
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
    val currentPensionPortfolio: Double = 0.0,
    val currentEmergencyReserve: Double = 0.0,
    val effectiveEmergencyReserveTargetToday: Double = 0.0,
    val isEmergencyReserveFundedToday: Boolean = true,
    val emergencyReserveSurplusOrDeficitToday: Double = 0.0,
    val trailingBurnRate3mMonthly: Double? = null,
    val trailingBurnRate12mMonthly: Double? = null,
    val ytdTaxShieldStatus: YtdTaxShieldStatus? = null
)

object FinancialEngine {

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
        importedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList()
    ): YtdTaxShieldStatus {
        val yearPrefix = "$currentYear"
        val yearTxs = importedTransactions.filter {
            !it.isNetted && (it.yearMonth.startsWith(yearPrefix) || it.date.startsWith(yearPrefix))
        }

        val dipTxs = yearTxs.filter { it.category == "INVESTMENT_DIP" }
        val dpsTxs = yearTxs.filter { it.category == "INVESTMENT_DPS" }

        val dipYtd = if (dipTxs.isNotEmpty()) {
            dipTxs.sumOf { abs(it.amount) }
        } else {
            val monthlyDip = settings.dipContributionMonthly + if (!settings.isSingleHousehold) settings.eDipContributionMonthly else 0.0
            monthlyDip * currentMonth.coerceIn(1, 12)
        }

        val dpsYtd = if (dpsTxs.isNotEmpty()) {
            val dpsByMonth = dpsTxs.groupBy { if (it.yearMonth.isNotBlank()) it.yearMonth else it.date.take(7) }
            dpsByMonth.values.sumOf { txs ->
                val monthTotal = txs.sumOf { abs(it.amount) }
                max(0.0, monthTotal - settings.dpsDeductionThresholdMonthly)
            }
        } else {
            val vDpsQualifying = max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly)
            val eDpsQualifying = if (!settings.isSingleHousehold) {
                max(0.0, settings.eDpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly)
            } else 0.0
            (vDpsQualifying + eDpsQualifying) * currentMonth.coerceIn(1, 12)
        }

        val totalYtd = dipYtd + dpsYtd
        val ceiling = settings.taxDeductionCeilingAnnual
        val progressPct = if (ceiling > 0) ((totalYtd / ceiling) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val effectiveDeduction = min(ceiling, totalYtd)
        val refund15 = effectiveDeduction * (settings.taxRatePct / 100.0)
        val refund23 = effectiveDeduction * (settings.taxRateSecondPct / 100.0)
        val remainingHeadroom = max(0.0, ceiling - totalYtd)
        val monthsRemaining = max(1, 12 - currentMonth + 1)
        val recommendedPace = if (remainingHeadroom > 0) remainingHeadroom / monthsRemaining else 0.0

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
            isCeilingReached = totalYtd >= ceiling
        )
    }

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

    fun partnerBirthYear(settings: SettingsEntity): Int = settings.baseYear - settings.primaryAge

    fun netRealReturnRate(settings: SettingsEntity): Double {
        val netNominalFactor = 1.0 + netTaxableNominalReturnPct(settings) / 100.0
        val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.01)
        return (netNominalFactor / inflationFactor) - 1.0
    }

    fun annualPartnerTaxCredit(settings: SettingsEntity, year: Int): Double {
        if (settings.isSingleHousehold || !settings.includeSpouseCredit || !settings.hasChildUnder3) return 0.0
        val c1Age = year - settings.child1BirthYear
        val c2Age = year - settings.child2BirthYear
        val hasUnder3 = (settings.child1Enabled && c1Age in 0..2) || (settings.child2Enabled && c2Age in 0..2)
        if (!hasUnder3) return 0.0
        val spouseInc = spouseOwnIncomeAnnual(year, settings)
        if (spouseInc > settings.spouseIncomeLimitAnnual) return 0.0

        val vGross = CzechTaxEngine.netToGrossAnnual(
            netMonthly = vaclavSalaryMonthly(year, settings),
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
            baseTaxRatePct = settings.taxRatePct,
            highTaxRatePct = settings.taxRateSecondPct
        )
        val ceiling = max(0.0, settings.taxDeductionCeilingAnnual)
        val vDeduction = min(
            max(0.0, settings.dipContributionMonthly) * 12.0 + max(0.0, settings.dpsOwnContributionMonthly - settings.dpsDeductionThresholdMonthly) * 12.0,
            ceiling
        )
        val vGrossTaxAfterDeduction = run {
            val taxableBase = max(0.0, vGross - vDeduction)
            val highIncome = max(0.0, taxableBase - settings.taxSecondBracketThresholdAnnual)
            val baseIncome = taxableBase - highIncome
            highIncome * (settings.taxRateSecondPct / 100.0) + baseIncome * (settings.taxRatePct / 100.0)
        }
        val vRemainingTax = max(0.0, vGrossTaxAfterDeduction - settings.taxpayerCreditAnnual)
        return min(settings.spouseTaxCreditAnnual, vRemainingTax)
    }

    fun goalsOutlayForYear(year: Int, settings: SettingsEntity): Double {
        if (settings.customGoalsJson.isBlank() || settings.customGoalsJson.trim() == "[]") return 0.0
        return parseCustomLifeGoals(settings.customGoalsJson)
            .filter { it.targetYear == year }
            .sumOf { max(0.0, it.targetAmountCzk - it.currentSavedCzk) }
    }

    fun baselineLivingCostAtFireMonthly(settings: SettingsEntity): Double {
        if (settings.lifestyleCostAtFireMonthly > 0.0) {
            return settings.lifestyleCostAtFireMonthly
        }
        var cost = totalLivingCostMonthly(settings.copy(childExpensesEnabled = false), settings.baseYear)
        if (settings.childExpensesEnabled && settings.currentChildCostsInBaseline) {
            if (settings.child1Enabled && settings.child1BirthYear <= settings.baseYear) {
                cost = max(0.0, cost - childMonthlyExpense(settings.child1BirthYear, settings.baseYear, settings))
            }
            if (settings.child2Enabled && settings.child2BirthYear <= settings.baseYear) {
                cost = max(0.0, cost - childMonthlyExpense(settings.child2BirthYear, settings.baseYear, settings))
            }
        }
        return cost
    }

    fun fireTargetBase(settings: SettingsEntity, age: Int = settings.primaryAge): Double {
        if (settings.fireTargetOverride > 0) return settings.fireTargetOverride
        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.001)

        val lifestyleMonthly = baselineLivingCostAtFireMonthly(settings)
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

        val safetyBuffer = (1.0 + settings.safetyBufferPct / 100.0).coerceAtLeast(0.0)
        val total = (costPhase1 + costPhase2 + targetCapitalPhase3 + childBridgeCapital) * safetyBuffer
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
        val growthFactor = if (settings.vSalaryGrowthPct != 0.0) {
            (1.0 + settings.vSalaryGrowthPct / 100.0).coerceAtLeast(0.0).pow(year - sy)
        } else 1.0
        return settings.vSalary * growthFactor
    }

    fun eleonoraSalaryMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.eReturnYear) return 0.0
        val month = settings.eReturnMonth.coerceIn(1, 12)
        val fullSalary = if (year == settings.eReturnYear) {
            max(0.0, settings.eStartingSalary)
        } else {
            val yearsActive = year - settings.eReturnYear
            val growthFactor = (1.0 + settings.eSalaryGrowthPct / 100.0).coerceAtLeast(0.0).pow(yearsActive)
            if (growthFactor.isFinite()) max(0.0, settings.eStartingSalary * growthFactor) else 0.0
        }
        return if (year == settings.eReturnYear) {
            val workFraction = (13 - month) / 12.0
            fullSalary * workFraction
        } else {
            fullSalary
        }
    }

    fun remainingParentalPotAtYearStart(targetYear: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || targetYear < settings.baseYear) return 0.0
        val baseYear = settings.baseYear
        var pot = (if (settings.child1Enabled && baseYear >= settings.child1BirthYear) parentalAllowancePot(settings.child1BirthYear, settings) else 0.0) +
                (if (settings.child2Enabled && baseYear >= settings.child2BirthYear) parentalAllowancePot(settings.child2BirthYear, settings) else 0.0)

        for (y in baseYear until targetYear) {
            if (settings.child1Enabled && y == settings.child1BirthYear && y > baseYear) {
                pot += parentalAllowancePot(settings.child1BirthYear, settings)
            }
            if (settings.child2Enabled && y == settings.child2BirthYear && y > baseYear) {
                pot += parentalAllowancePot(settings.child2BirthYear, settings)
            }
            if (y <= settings.eReturnYear && pot > 0.0) {
                val months = if (y == settings.eReturnYear) {
                    (settings.eReturnMonth.coerceIn(1, 12) - 1).toDouble()
                } else 12.0
                val desired = settings.eParentalAllowanceMonthly * months
                val drawn = min(pot, max(0.0, desired))
                pot -= drawn
            }
        }
        if (settings.child1Enabled && targetYear == settings.child1BirthYear && targetYear > baseYear) {
            pot += parentalAllowancePot(settings.child1BirthYear, settings)
        }
        if (settings.child2Enabled && targetYear == settings.child2BirthYear && targetYear > baseYear) {
            pot += parentalAllowancePot(settings.child2BirthYear, settings)
        }
        return max(0.0, pot)
    }

    fun eleonoraBenefitMonthly(year: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.baseYear || year > settings.eReturnYear) return 0.0
        val remainingPot = remainingParentalPotAtYearStart(year, settings)
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
        val m = month.coerceIn(1, 12)
        if (year == settings.eReturnYear && m >= settings.eReturnMonth) return 0.0
        val remainingPotAtStartOfYear = remainingParentalPotAtYearStart(year, settings)
        if (remainingPotAtStartOfYear <= 0.0) return 0.0

        val drawnPriorInThisYear = (m - 1) * settings.eParentalAllowanceMonthly
        val remainingForThisMonth = max(0.0, remainingPotAtStartOfYear - drawnPriorInThisYear)
        return min(settings.eParentalAllowanceMonthly, max(0.0, remainingForThisMonth))
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
        val isSingle = settings.isSingleHousehold

        val e = when {
            isSingle -> 0.0
            isBeforeReturn -> 0.0
            isReturnYear -> {
                if (month != null) {
                    if (month < settings.eReturnMonth) 0.0
                    else if (activeLedgerEntry != null && activeLedgerEntry.incEleonora > 0.0 && year == baseYear) activeLedgerEntry.incEleonora
                    else eleonoraSalaryMonthly(year, settings.copy(eReturnMonth = 1))
                } else {
                    eleonoraSalaryMonthly(year, settings)
                }
            }
            activeLedgerEntry != null && activeLedgerEntry.incEleonora > 0.0 && year == baseYear -> {
                activeLedgerEntry.incEleonora
            }
            else -> eleonoraSalaryMonthly(year, settings)
        }

        val b = when {
            isSingle -> 0.0
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
                if (month != null) {
                    if (month >= settings.eReturnMonth) 0.0
                    else if (activeLedgerEntry != null && activeLedgerEntry.incEleonora > 0.0 && year == baseYear) activeLedgerEntry.incEleonora
                    else eleonoraBenefitForMonth(year, month, settings)
                } else {
                    eleonoraBenefitMonthly(year, settings)
                }
            }
            else -> 0.0
        }

        val lec = when {
            isSingle -> 0.0
            isReturnYear && month != null -> {
                if (month < settings.eReturnMonth && settings.eIncludeLecturing) settings.eLecturingMonthly else 0.0
            }
            else -> eleonoraLecturingMonthly(year, settings)
        }

        val vGift = if (settings.familyGiftMonthly != 16000.0 && settings.vGiftsMonthly == 16000.0 && settings.eGiftsMonthly == 0.0) {
            settings.familyGiftMonthly
        } else {
            settings.vGiftsMonthly
        }
        val eGift = if (!settings.isSingleHousehold) settings.eGiftsMonthly else 0.0
        val totalGift = vGift + eGift

        val scheduledLumps = if (month != null) {
            lumpSumsForMonth(year, month, settings).sumOf { it.amount }
        } else {
            lumpSumForYear(year, settings) / 12.0
        }

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

    fun totalLivingCostMonthly(settings: SettingsEntity, year: Int = settings.baseYear): Double {
        val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
        var base = 0.0
        if (!deletedSet.contains("rent")) {
            val rentYears = max(0, year - settings.baseYear)
            val rentFactor = if (rentYears > 0 && settings.rentGrowthPct != 0.0) {
                (1.0 + settings.rentGrowthPct / 100.0).coerceAtLeast(0.0).pow(rentYears)
            } else 1.0
            val safeRentFactor = if (rentFactor.isNaN() || rentFactor.isInfinite()) 1.0 else rentFactor
            base += max(0.0, settings.rentMonthly) * safeRentFactor
        }
        if (!deletedSet.contains("groceries")) base += max(0.0, settings.groceriesMonthly)
        if (!deletedSet.contains("other_discretionary")) base += max(0.0, settings.otherDiscretionaryMonthly)
        if (!deletedSet.contains("cafes")) base += max(0.0, settings.cafesMonthly)
        if (!deletedSet.contains("therapy")) base += max(0.0, settings.therapyMonthly)
        if (!deletedSet.contains("charity")) base += max(0.0, settings.charityMonthly)
        if (!deletedSet.contains("entertainment")) base += max(0.0, settings.entertainmentMonthly)
        if (!deletedSet.contains("transport")) base += max(0.0, settings.transportMonthly)
        if (!deletedSet.contains("subscriptions")) base += max(0.0, settings.subscriptionsMonthly)

        val customItems = parseCustomExpenses(settings.customExpensesJson)
        base += customItems.sumOf { max(0.0, it.amount) }

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

    const val DAYS_PER_MONTH = 30.42

    /**
     * Calculates the number of days of permanent financial freedom purchased
     * by a given monthly savings amount, based on the household's current daily living burn.
     */
    fun freedomDaysBoughtMonthly(monthlySavings: Double, monthlyLivingCost: Double): Double {
        if (monthlyLivingCost <= 0.0) return 0.0
        val dailyBurn = monthlyLivingCost / DAYS_PER_MONTH
        return if (dailyBurn > 0.0) monthlySavings / dailyBurn else 0.0
    }

    /**
     * Calculates the total number of days of financial freedom stored in a given portfolio balance.
     */
    fun freedomDaysCoveredByPortfolio(portfolioBalance: Double, monthlyLivingCost: Double): Double {
        if (portfolioBalance <= 0.0 || monthlyLivingCost <= 0.0) return 0.0
        val dailyBurn = monthlyLivingCost / DAYS_PER_MONTH
        return if (dailyBurn > 0.0) portfolioBalance / dailyBurn else 0.0
    }

    fun effectiveEmergencyReserveTarget(settings: SettingsEntity): Double {
        val livingCostTotal = totalLivingCostMonthly(settings)
        val raw = when (settings.emergencyReserveMode) {
            "3M" -> livingCostTotal * 3.0
            "6M" -> livingCostTotal * 6.0
            "9M" -> livingCostTotal * 9.0
            "12M" -> livingCostTotal * 12.0
            "Target" -> if (settings.emergencyReserveTarget > 0.0) settings.emergencyReserveTarget else livingCostTotal * 6.0
            else -> if (settings.emergencyReserveTarget > 0.0) settings.emergencyReserveTarget else livingCostTotal * 6.0
        }
        return (kotlin.math.round(raw / 1_000.0) * 1_000.0).coerceAtLeast(1_000.0)
    }

    fun baseInvestMonthly(settings: SettingsEntity): Double {
        val empCap = max(0.0, settings.employerRetirementExemptionAnnual) / 12.0
        val vaclavInvest = max(0.0, settings.portuDcaMonthly) + max(0.0, settings.dpsOwnContributionMonthly) +
                max(0.0, settings.dipContributionMonthly) + min(max(0.0, settings.employerRetirementMonthly), empCap)
        val eleonoraInvest = if (!settings.isSingleHousehold) {
            max(0.0, settings.ePortuDcaMonthly) + max(0.0, settings.eDpsOwnContributionMonthly) +
                    max(0.0, settings.eDipContributionMonthly) + min(max(0.0, settings.eEmployerRetirementMonthly), empCap)
        } else 0.0
        return vaclavInvest + eleonoraInvest
    }

    fun buildLiquidPortfolio(
        settings: SettingsEntity,
        dualIncome: Boolean,
        initialCrashPct: Double = 0.0
    ): List<PortfolioYearPoint> {
        val list = mutableListOf<PortfolioYearPoint>()
        val sy = settings.baseYear
        val age0 = settings.primaryAge
        val ret = netTaxableNominalReturnPct(settings) / 100.0
        val includeSpouse = dualIncome && !settings.isSingleHousehold
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent else 0.0
        var bal = (settings.liquidPortfolioCurrent + eLiquid) * max(0.0, 1.0 - initialCrashPct)
        val initialTarget = fireTargetYear(sy, settings, age0)

        val eDpsBal = if (includeSpouse) settings.eDpsBalanceCurrent else 0.0
        val eDipBal = if (includeSpouse) settings.eDipBalanceCurrent else 0.0
        var dpsBal = settings.dpsBalanceCurrent + eDpsBal
        var dipBal = settings.dipBalanceCurrent + eDipBal
        var pensionBal = dpsBal + dipBal

        val rReal = netRealReturnRate(settings)
        val annualLiving0 = totalLivingCostMonthly(settings) * 12.0
        val yearsTo60_0 = max(0, 60 - age0)
        val bridgeReq0 = if (yearsTo60_0 == 0) 0.0 else annualLiving0 * annuityFactor(rReal, yearsTo60_0)
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

        val dpsFee = min(max(0.0, settings.dpsAnnualFeePct), max(0.0, settings.dpsStatutoryFeeCapPct))
        val dpsRet = max(-0.99, (settings.dpsGrossReturnPct - dpsFee) / 100.0)
        val deletedSet = parseDeletedCategories(settings.deletedCategoriesJson)
        val empCap = max(0.0, settings.employerRetirementExemptionAnnual) / 12.0
        val vEmpEffective = min(max(0.0, settings.employerRetirementMonthly), empCap)
        val eEmpEffective = if (includeSpouse) min(max(0.0, settings.eEmployerRetirementMonthly), empCap) else 0.0

        for (year in sy until (sy + 35)) {
            val age = age0 + (year - sy) + 1
            val ePortu = if (includeSpouse) max(0.0, settings.ePortuDcaMonthly) else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct != 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).coerceAtLeast(0.0).pow(year - sy) else 1.0
            val safeDcaFactor = if (dcaFactor.isFinite()) max(0.0, dcaFactor) else 1.0

            val isBaristaActive = settings.baristaTransitionEnabled && (year + 1) >= settings.baristaStartYear
            val baseAnnual = if (isBaristaActive) {
                settings.baristaSavingsMonthly * 12.0
            } else {
                (max(0.0, settings.portuDcaMonthly) + ePortu) * 12.0 * safeDcaFactor
            }
            val reinvestAnnual = if (!isBaristaActive && includeSpouse && year >= settings.eReturnYear) {
                eleonoraSalaryMonthly(year, settings) * (settings.eReinvestedPct / 100.0).coerceIn(0.0, 1.0) * 12.0
            } else 0.0
            val partnerRefund = if (!isBaristaActive && includeSpouse) annualPartnerTaxCredit(settings, year) else 0.0
            val taxRefundAnnual = annualRetirementTaxSaved(if (!includeSpouse) settings.copy(isSingleHousehold = true) else settings, year) + partnerRefund
            val lump = lumpSumForYear(year, settings)

            val goalsOutlay = goalsOutlayForYear(year + 1, settings)

            val annualInflows = baseAnnual + reinvestAnnual + taxRefundAnnual + lump
            bal = max(0.0, bal * max(0.0, 1.0 + ret) + annualInflows * (1.0 + ret * 0.5) - goalsOutlay)

            // Pension tier: DPS (own + employer + state match) and DIP (own)
            val eDpsOwn = if (includeSpouse) max(0.0, settings.eDpsOwnContributionMonthly) else 0.0
            val eDipOwn = if (includeSpouse) max(0.0, settings.eDipContributionMonthly) else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age - 1, settings, year) * 12.0
            val subE = if (includeSpouse) dpsSubsidy(settings.eDpsOwnContributionMonthly, (year - partnerBirthYear(settings)), settings, year) * 12.0 else 0.0
            val dpsInflows = (max(0.0, settings.dpsOwnContributionMonthly) + eDpsOwn + vEmpEffective + eEmpEffective) * 12.0 + subV + subE
            val dipInflows = (max(0.0, settings.dipContributionMonthly) + eDipOwn) * 12.0

            dpsBal = max(0.0, dpsBal * max(0.0, 1.0 + dpsRet) + dpsInflows * (1.0 + dpsRet * 0.5))
            dipBal = max(0.0, dipBal * max(0.0, 1.0 + ret) + dipInflows * (1.0 + ret * 0.5))
            pensionBal = dpsBal + dipBal

            val t = fireTargetYear(year + 1, settings, age)
            val totalBal = bal + pensionBal
            val gap = t - totalBal

            val yearsTo60 = max(0, 60 - age)
            val yearsElapsed = (year + 1) - sy
            val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0).pow(yearsElapsed)
            val annualLivingAtYear = if (settings.rentGrowthPct != 0.0 && !deletedSet.contains("rent")) {
                val nonRentMonthly = totalLivingCostMonthly(settings.copy(rentMonthly = 0.0), year + 1)
                val rentFactor = (1.0 + settings.rentGrowthPct / 100.0).coerceAtLeast(0.0).pow(yearsElapsed)
                (nonRentMonthly * inflationFactor + settings.rentMonthly * rentFactor) * 12.0
            } else {
                totalLivingCostMonthly(settings, year + 1) * 12.0 * inflationFactor
            }
            val bridgeReq = if (yearsTo60 == 0) 0.0 else annualLivingAtYear * annuityFactor(rReal, yearsTo60)
            val bridgeFunded = if (yearsTo60 == 0) true else bal >= bridgeReq

            val status = when {
                totalBal >= t -> {
                    if (age >= 60 || bridgeFunded) "FIRE OK" else "Bridge Constrained"
                }
                isBaristaActive -> "Barista Active"
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
                    isLiquidBridgeFunded = bridgeFunded,
                    pensionInflowAnnual = dpsInflows + dipInflows
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

        val mag = sqrt(-2.0 * ln(u1))
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
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)
        val empCap = max(0.0, settings.employerRetirementExemptionAnnual) / 12.0
        val vEmpEffective = min(max(0.0, settings.employerRetirementMonthly), empCap)
        val eEmpEffective = if (!settings.isSingleHousehold) min(max(0.0, settings.eEmployerRetirementMonthly), empCap) else 0.0

        // Store year-by-year cash flow additions
        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) max(0.0, settings.ePortuDcaMonthly) else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct != 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).coerceAtLeast(0.0).pow(y) else 1.0
            val safeDcaFactor = if (dcaFactor.isFinite()) max(0.0, dcaFactor) else 1.0
            val baseDca = (max(0.0, settings.portuDcaMonthly) + ePortu) * 12.0 * safeDcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0).coerceIn(0.0, 1.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val taxRefundAnnual = annualRetirementTaxSaved(settings, sy) + annualPartnerTaxCredit(settings, sy)
            val eDipOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDipContributionMonthly) else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDpsOwnContributionMonthly) else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, (sy - partnerBirthYear(settings)), settings, sy) * 12.0 else 0.0
            val pensionInflows = (max(0.0, settings.dipContributionMonthly) + eDipOwn + max(0.0, settings.dpsOwnContributionMonthly) + eDpsOwn + vEmpEffective + eEmpEffective) * 12.0 + subV + subE

            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            val goalsOutlay = goalsOutlayForYear(sy + 1, settings)
            Triple(baseDca + eleonoraSal + lump + taxRefundAnnual + pensionInflows - goalsOutlay, target, baseAge + y + 1)
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
            var hitAge: Int? = if (bal >= initialTarget) baseAge else null

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
        val histGeomFactor = exp(HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { ln(max(0.01, 1.0 + (it - taxDrag) / 100.0)) }.average())
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histGeomFactor > 0.0 && histGeomFactor.isFinite()) targetFactor / histGeomFactor else 1.0
        val random = Random(settings.monteCarloSeed + 101L)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)
        val empCap = max(0.0, settings.employerRetirementExemptionAnnual) / 12.0
        val vEmpEffective = min(max(0.0, settings.employerRetirementMonthly), empCap)
        val eEmpEffective = if (!settings.isSingleHousehold) min(max(0.0, settings.eEmployerRetirementMonthly), empCap) else 0.0

        val additions = Array(horizonYears) { y ->
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) max(0.0, settings.ePortuDcaMonthly) else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct != 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).coerceAtLeast(0.0).pow(y) else 1.0
            val safeDcaFactor = if (dcaFactor.isFinite()) max(0.0, dcaFactor) else 1.0
            val baseDca = (max(0.0, settings.portuDcaMonthly) + ePortu) * 12.0 * safeDcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0).coerceIn(0.0, 1.0) * 12.0
            } else 0.0
            val lump = lumpSumForYear(sy, settings)
            val taxRefundAnnual = annualRetirementTaxSaved(settings, sy) + annualPartnerTaxCredit(settings, sy)
            val eDipOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDipContributionMonthly) else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDpsOwnContributionMonthly) else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, (sy - partnerBirthYear(settings)), settings, sy) * 12.0 else 0.0
            val pensionInflows = (max(0.0, settings.dipContributionMonthly) + eDipOwn + max(0.0, settings.dpsOwnContributionMonthly) + eDpsOwn + vEmpEffective + eEmpEffective) * 12.0 + subV + subE

            val target = fireTargetYear(sy + 1, settings, baseAge + y + 1)
            val goalsOutlay = goalsOutlayForYear(sy + 1, settings)
            Triple(baseDca + eleonoraSal + lump + taxRefundAnnual + pensionInflows - goalsOutlay, target, baseAge + y + 1)
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
            var hitAge: Int? = if (bal >= initialTarget) baseAge else null

            val pathReturns = DoubleArray(horizonYears)
            var yOffset = 0
            while (yOffset < horizonYears) {
                val startIdx = random.nextInt(nHistory)
                for (b in 0 until effectiveBlockSize) {
                    if (yOffset + b < horizonYears) {
                        val histIdx = (startIdx + b) % nHistory
                        val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                        val rawFactor = max(0.01, 1.0 + (rawReturn - taxDrag) / 100.0)
                        val netReturn = max(-0.99, rawFactor * scaleFactor - 1.0)
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
    private var cachedParametricSurvivalKey: SurvivalCacheKey? = null
    @Volatile
    private var cachedParametricSurvivalResult: RetirementSurvivalResult? = null

    @Volatile
    private var cachedHistSurvivalKey: SurvivalCacheKey? = null
    @Volatile
    private var cachedHistSurvivalResult: RetirementSurvivalResult? = null

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
        if (useHistoricalBootstrap) {
            cachedHistSurvivalResult?.let { if (cachedHistSurvivalKey == currentKey) return it }
        } else {
            cachedParametricSurvivalResult?.let { if (cachedParametricSurvivalKey == currentKey) return it }
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed + (if (useHistoricalBootstrap) 202L else 1L))

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val cpi = settings.cpiInflationPct / 100.0
        val maxAccumYears = 35
        val horizon = settings.retirementHorizonYears.coerceIn(10, 60)
        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val taxDrag = dividendTaxDragPct(settings)
        val histGeomFactor = exp(HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { ln(max(0.01, 1.0 + (it - taxDrag) / 100.0)) }.average())
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histGeomFactor > 0.0 && histGeomFactor.isFinite()) targetFactor / histGeomFactor else 1.0
        val blockSize = 3

        val lifestyleToday = max(0.0, baselineLivingCostAtFireMonthly(settings))
        val vPensionToday = max(0.0, settings.vStatePensionMonthly)
        val ePensionToday = if (!settings.isSingleHousehold) max(0.0, settings.eStatePensionMonthly) else 0.0
        val eLiquid = if (!settings.isSingleHousehold) max(0.0, settings.eLiquidPortfolioCurrent) else 0.0
        val eDps = if (!settings.isSingleHousehold) max(0.0, settings.eDpsBalanceCurrent) else 0.0
        val eDip = if (!settings.isSingleHousehold) max(0.0, settings.eDipBalanceCurrent) else 0.0
        val initialLiquid = max(0.0, settings.liquidPortfolioCurrent + eLiquid)
        val initialPension = max(0.0, settings.dpsBalanceCurrent + eDps + settings.dipBalanceCurrent + eDip)

        val empCap = max(0.0, settings.employerRetirementExemptionAnnual) / 12.0
        val vEmpEffective = min(max(0.0, settings.employerRetirementMonthly), empCap)
        val eEmpEffective = if (!settings.isSingleHousehold) min(max(0.0, settings.eEmployerRetirementMonthly), empCap) else 0.0

        val liquidAdditions = DoubleArray(maxAccumYears)
        val pensionAdditions = DoubleArray(maxAccumYears)
        for (y in 0 until maxAccumYears) {
            val sy = baseYear + y
            val age = baseAge + y
            val ePortu = if (!settings.isSingleHousehold) max(0.0, settings.ePortuDcaMonthly) else 0.0
            val dcaFactor = if (settings.dcaAnnualGrowthPct != 0.0) (1.0 + settings.dcaAnnualGrowthPct / 100.0).coerceAtLeast(0.0).pow(y) else 1.0
            val safeDcaFactor = if (dcaFactor.isFinite()) max(0.0, dcaFactor) else 1.0
            val baseDca = (max(0.0, settings.portuDcaMonthly) + ePortu) * 12.0 * safeDcaFactor
            val eleonoraSal = if (!settings.isSingleHousehold && sy >= settings.eReturnYear) {
                eleonoraSalaryMonthly(sy, settings) * (settings.eReinvestedPct / 100.0).coerceIn(0.0, 1.0) * 12.0
            } else 0.0
            val taxRefundAnnual = annualRetirementTaxSaved(settings, sy) + annualPartnerTaxCredit(settings, sy)
            val lump = lumpSumForYear(sy, settings)
            val eDipOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDipContributionMonthly) else 0.0
            val eDpsOwn = if (!settings.isSingleHousehold) max(0.0, settings.eDpsOwnContributionMonthly) else 0.0
            val subV = dpsSubsidy(settings.dpsOwnContributionMonthly, age, settings, sy) * 12.0
            val subE = if (!settings.isSingleHousehold) dpsSubsidy(settings.eDpsOwnContributionMonthly, (sy - partnerBirthYear(settings)), settings, sy) * 12.0 else 0.0
            val pensionInflows = (max(0.0, settings.dipContributionMonthly) + eDipOwn + max(0.0, settings.dpsOwnContributionMonthly) + eDpsOwn + vEmpEffective + eEmpEffective) * 12.0 + subV + subE

            liquidAdditions[y] = baseDca + eleonoraSal + lump + taxRefundAnnual - goalsOutlayForYear(sy + 1, settings)
            pensionAdditions[y] = pensionInflows
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
                            val rawFactor = max(0.01, 1.0 + (rawReturn - taxDrag) / 100.0)
                            val netReturn = max(-0.99, rawFactor * scaleFactor - 1.0)
                            returns[yOffset + b] = netReturn
                        }
                    }
                    yOffset += blockSize
                }
                returns
            } else null

            var liquidBal = initialLiquid
            var pensionBal = initialPension
            var fireYear = -1

            val yearsTo60_0 = max(0, 60 - baseAge)
            val rReal0 = netRealReturnRate(settings)
            val bridgeReq0 = if (yearsTo60_0 == 0) 0.0 else lifestyleToday * 12.0 * annuityFactor(rReal0, yearsTo60_0)
            val initialTarget0 = fireTargetYear(baseYear, settings, baseAge)
            if ((initialLiquid + initialPension) >= initialTarget0) {
                fireYear = baseYear
            }

            if (fireYear < 0) {
                for (y in 0 until maxAccumYears) {
                    val ret = pathReturns?.get(y) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)
                    liquidBal = max(0.0, liquidBal * max(0.0, 1.0 + ret) + liquidAdditions[y] * (1.0 + ret * 0.5))
                    pensionBal = max(0.0, pensionBal * max(0.0, 1.0 + ret) + pensionAdditions[y] * (1.0 + ret * 0.5))
                    val currentAge = baseAge + y + 1
                    val yearsTo60 = max(0, 60 - currentAge)
                    val targetYearIndexation = (1.0 + cpi).pow(y + 1)
                    val annualLivingAtRetirement = lifestyleToday * 12.0 * targetYearIndexation
                    val rReal = netRealReturnRate(settings)
                    val bridgeReq = if (yearsTo60 == 0) 0.0 else annualLivingAtRetirement * annuityFactor(rReal, yearsTo60)
                    if ((liquidBal + pensionBal) >= targets[y] && (currentAge >= 60 || liquidBal >= bridgeReq)) {
                        fireYear = baseYear + y + 1
                        break
                    }
                }
            }
            if (fireYear < 0) continue
            fireReached++

            var depletedAge: Int? = null
            var guardrailTriggeredInPath = false
            var spendingScale = 1.0
            val reserveTargetAnnual = effectiveEmergencyReserveTarget(settings)
            var cashCushion = min(settings.emergencyReserveCurrent, reserveTargetAnnual)
            var bal = liquidBal + pensionBal + cashCushion
            for (k in 0 until horizon) {
                val year = fireYear + k
                val age = baseAge + (year - baseYear)
                val eAge = year - partnerBirthYear(settings)

                // Statutory Czech lock-in guard (Act No. 586/1992 Coll. § 15a & Act No. 427/2011 Coll. § 94):
                // Pension assets (DIP/DPS) unlock into the liquid spending pool at age 60.
                if (age >= 60 && pensionBal > 0.0) {
                    liquidBal += pensionBal
                    pensionBal = 0.0
                }

                val indexation = (1.0 + cpi).pow((year - baseYear).coerceAtLeast(0))
                val lifestyleAnnual = lifestyleToday * 12.0 * indexation * spendingScale
                var pensionAnnual = 0.0
                if (age >= settings.vStatePensionAge) pensionAnnual += vPensionToday * 12.0 * indexation
                if (!settings.isSingleHousehold && eAge >= settings.eStatePensionAge) {
                    pensionAnnual += ePensionToday * 12.0 * indexation
                }
                var need = max(0.0, lifestyleAnnual - pensionAnnual)

                val currentTotalBal = liquidBal + pensionBal + cashCushion
                if (settings.guardrailsEnabled && currentTotalBal > 0.0) {
                    val plannedTargetAtAge = fireTargetYear(year, settings, age).coerceAtLeast(1.0)
                    val baseNeedAtAge = max(0.0, lifestyleToday * 12.0 * indexation - pensionAnnual)
                    val referenceSwr = (baseNeedAtAge / plannedTargetAtAge).coerceAtLeast(0.001)
                    val currentWithdrawalRate = need / currentTotalBal

                    val upperThreshold = 1.0 + (settings.guardrailsUpperThresholdPct / 100.0)
                    val lowerThreshold = (1.0 - (settings.guardrailsLowerThresholdPct / 100.0)).coerceAtLeast(0.10)
                    val cutMultiplier = (1.0 - (settings.guardrailsCutPct / 100.0)).coerceIn(0.50, 0.99)
                    val boostMultiplier = (1.0 + (settings.guardrailsBoostPct / 100.0)).coerceIn(1.01, 1.50)
                    val floorMultiplier = (settings.guardrailsFloorPct / 100.0).coerceIn(0.30, 0.95)

                    if (k < horizon - 15 && currentWithdrawalRate > referenceSwr * upperThreshold) {
                        // Upper Guardrail (Capital Preservation Rule):
                        // Reduce discretionary spending, protected by customizable essential floor,
                        // subject to Guyton-Klinger 15-year sunset rule
                        spendingScale = (spendingScale * cutMultiplier).coerceAtLeast(floorMultiplier)
                        val adjustedLifestyle = lifestyleToday * 12.0 * indexation * spendingScale
                        need = max(0.0, adjustedLifestyle - pensionAnnual)
                        guardrailTriggeredInPath = true
                    } else if (currentWithdrawalRate < referenceSwr * lowerThreshold) {
                        // Lower Guardrail (Prosperity Rule): boost spending
                        spendingScale = min(1.30, spendingScale * boostMultiplier)
                        val adjustedLifestyle = lifestyleToday * 12.0 * indexation * spendingScale
                        need = max(0.0, adjustedLifestyle - pensionAnnual)
                    }
                }

                val returnIdx = (fireYear - baseYear) + k
                val ret = pathReturns?.getOrNull(returnIdx) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)

                // 1. Compound balances first for the investment year
                liquidBal *= max(0.0, 1.0 + ret)
                pensionBal *= max(0.0, 1.0 + ret)
                cashCushion *= (1.0 + cpi)

                // 2. Dynamic cash cushion sequence-of-returns defense:
                // During market drawdowns (ret < 0), draw living expenses from emergency cash reserve
                // first rather than liquidating equity at market troughs.
                val indexedReserveTarget = reserveTargetAnnual * indexation
                if (ret < 0.0 && cashCushion > 0.0) {
                    val drawFromCash = min(cashCushion, need)
                    cashCushion -= drawFromCash
                    need -= drawFromCash
                } else if (ret > 0.05 && cashCushion < indexedReserveTarget) {
                    val replenish = min(indexedReserveTarget - cashCushion, (liquidBal + (if (age >= 60) pensionBal else 0.0)) * 0.02)
                    if (age < 60 && liquidBal > replenish) {
                        liquidBal -= replenish
                        cashCushion += replenish
                    } else if (age >= 60 && (liquidBal + pensionBal) > replenish) {
                        if (liquidBal >= replenish) {
                            liquidBal -= replenish
                        } else {
                            val rem = replenish - liquidBal
                            liquidBal = 0.0
                            pensionBal -= rem
                        }
                        cashCushion += replenish
                    }
                }

                if (age < 60) {
                    if (liquidBal < need) {
                        val deficit = need - liquidBal
                        if (cashCushion >= deficit) {
                            cashCushion -= deficit
                            liquidBal = 0.0
                            need = 0.0
                        } else {
                            bal = 0.0
                            depletedAge = age
                            break
                        }
                    } else {
                        liquidBal -= need
                        need = 0.0
                    }
                    bal = liquidBal + pensionBal + cashCushion
                } else {
                    var available = liquidBal + pensionBal
                    if (available < need) {
                        val deficit = need - available
                        if (cashCushion >= deficit) {
                            cashCushion -= deficit
                            available = 0.0
                            need = 0.0
                        } else {
                            bal = 0.0
                            depletedAge = age
                            break
                        }
                    } else {
                        available -= need
                        need = 0.0
                    }
                    liquidBal = available
                    pensionBal = 0.0
                    bal = liquidBal + cashCushion
                }
            }

            if (depletedAge == null) {
                survived++
            } else {
                depletionAges.add(depletedAge)
            }
            if (guardrailTriggeredInPath) {
                pathsWithGuardrailsTriggered++
            }
            val totalYearsFromBase = max(0, (fireYear - baseYear) + horizon)
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
        if (useHistoricalBootstrap) {
            cachedHistSurvivalKey = currentKey
            cachedHistSurvivalResult = result
        } else {
            cachedParametricSurvivalKey = currentKey
            cachedParametricSurvivalResult = result
        }
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

            val firePoint = trajectory.firstOrNull { it.totalPortfolio >= it.target }
            val successRate = if (runMonteCarlo) {
                runMonteCarlo(mockSettings, initialCrashPct = if (id == "crash") 0.25 else 0.0).successRatePct
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

    fun calculate(
        settings: SettingsEntity,
        actionStates: Map<String, Boolean> = emptyMap(),
        runMonteCarlo: Boolean = true,
        activeLedgerEntry: LedgerEntryEntity? = null,
        activeMonth: Int? = null,
        ledgerEntries: List<LedgerEntryEntity> = emptyList(),
        importedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList()
    ): FullCalculationState {
        val currentYear = activeLedgerEntry?.yearMonth?.substringBefore('-')?.toIntOrNull() ?: settings.baseYear
        val monthForTaxShield = activeMonth ?: 1
        val ytdTaxShield = computeYtdTaxShieldStatus(
            settings = settings,
            currentYear = currentYear,
            currentMonth = monthForTaxShield,
            importedTransactions = importedTransactions
        )
        val trailing3m = computeTrailingBurnRateMonthly(ledgerEntries, 3)
        val trailing12m = computeTrailingBurnRateMonthly(ledgerEntries, 12)
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

        // Per-field fallback: the ledger dialog stores blank balance fields as 0.0, so a 0.0 value
        // means "not entered" and must fall back to settings independently for each bucket.
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

        val rReal = netRealReturnRate(settings)
        val annualLiving = totalLivingCostMonthly(settings) * 12.0
        val yearsTo60Today = max(0, 60 - settings.primaryAge)
        val bridgeReqToday = if (yearsTo60Today == 0) 0.0 else annualLiving * annuityFactor(rReal, yearsTo60Today)
        val currentLiquidBal = snapLiquid
        val bridgeFundedToday = if (yearsTo60Today == 0) true else currentLiquidBal >= bridgeReqToday
        val bridgeDeficitToday = max(0.0, bridgeReqToday - currentLiquidBal)

        val currentIncome = householdIncome(settings.baseYear, settings, activeLedgerEntry, activeMonth)
        val investMonthly = baseInvestMonthly(settings)
        val livingCostTotal = totalLivingCostMonthly(settings)
        val emergencyMonths = if (livingCostTotal > 0) max(0.0, snapReserve / livingCostTotal) else 0.0
        val effectiveReserveTarget = effectiveEmergencyReserveTarget(settings)
        val isReserveFunded = snapReserve >= effectiveReserveTarget
        val reserveSurplusOrDeficit = snapReserve - effectiveReserveTarget

        val dps = buildDpsProjection(settings)
        val dip = buildDipProjection(settings)

        val spouseInc = spouseOwnIncomeAnnual(settings.baseYear, settings)
        // Child under 3 check: child must be born and under 3 years old
        val child1AgeAtBase = settings.baseYear - settings.child1BirthYear
        val child2AgeAtBase = settings.baseYear - settings.child2BirthYear
        val hasChildAgeUnder3 = (settings.child1Enabled && child1AgeAtBase in 0..2) ||
                (settings.child2Enabled && child2AgeAtBase in 0..2)
        val hasChildUnder3 = settings.hasChildUnder3 && hasChildAgeUnder3

        val spouseEligible = !settings.isSingleHousehold &&
                settings.includeSpouseCredit &&
                hasChildUnder3 &&
                (spouseInc <= settings.spouseIncomeLimitAnnual)

        // Section 35c ZDP: either earner meeting statutory min wage multiplier qualifies the household
        val minEarnedIncome = settings.minWageMonthly * settings.childBonusMinWageMultiplier
        val vGrossAnnual = CzechTaxEngine.netToGrossAnnual(
            netMonthly = vaclavSalaryMonthly(settings.baseYear, settings),
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
            baseTaxRatePct = settings.taxRatePct,
            highTaxRatePct = settings.taxRateSecondPct
        )
        val eGrossAnnual = if (!settings.isSingleHousehold) {
            CzechTaxEngine.netToGrossAnnual(
                netMonthly = eleonoraSalaryMonthly(settings.baseYear, settings) + eleonoraLecturingMonthly(settings.baseYear, settings),
                taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
                highBracketThresholdAnnual = settings.taxSecondBracketThresholdAnnual,
                baseTaxRatePct = settings.taxRatePct,
                highTaxRatePct = settings.taxRateSecondPct
            )
        } else 0.0
        val vChildBonusOk = vGrossAnnual >= minEarnedIncome
        val eChildBonusOk = eGrossAnnual >= minEarnedIncome
        val childBonusOk = vChildBonusOk || eChildBonusOk

        val spouseCreditVal = if (spouseEligible) settings.spouseTaxCreditAnnual else 0.0
        val childBonusVal = if (childBonusOk) {
            var eligibleCount = 0
            if (settings.child1Enabled && child1AgeAtBase in 0 until settings.childSupportUntilAge) eligibleCount++
            if (settings.child2Enabled && child2AgeAtBase in 0 until settings.childSupportUntilAge) eligibleCount++
            var bonus = 0.0
            if (eligibleCount >= 1) bonus += settings.child1TaxBonusAnnual
            if (eligibleCount >= 2) bonus += settings.child2TaxBonusAnnual
            if (eligibleCount >= 3) bonus += settings.child3PlusTaxBonusAnnual
            bonus
        } else 0.0
        val retirementTaxSaved = annualRetirementTaxSaved(settings, settings.baseYear)
        val incrementalValue = spouseCreditVal + childBonusVal + retirementTaxSaved

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
            ((investMonthly / currentIncome.totalMonthly) * 100.0).coerceIn(0.0, 100.0)
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
            "ac9" to max(0.0, snapReserve - effectiveReserveTarget),
            "ac10" to (snapPension * 0.005)
        )

        val investableNetWorth = snapLiquid + snapPension

        val swr = (settings.safeWithdrawalRatePct / 100.0).coerceAtLeast(0.01)
        val realReturnRate = rReal
        val realReturnFactor = 1.0 + realReturnRate
        val yearsToRetire = max(1, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)
        val milestoneSettings = settings.copy(fireTargetOverride = 0.0)
        val effectiveBaseLiving = baselineLivingCostAtFireMonthly(milestoneSettings)

        // 1. Coast FIRE
        // The portfolio needs to grow on its own without additional contributions to reach the required
        // capital at statutory state pension age (settings.vStatePensionAge), where bridge years are 0.
        val targetAtPensionAge = fireTargetBase(milestoneSettings, age = settings.vStatePensionAge)
        val coastRawTarget = if (realReturnRate > 0.0) {
            targetAtPensionAge / (1.0 + realReturnRate).pow(yearsToRetire)
        } else {
            targetAtPensionAge / realReturnFactor.coerceAtLeast(0.01).pow(yearsToRetire)
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
            monthlyPassiveIncome = kotlin.math.round(((fireBase * swr) / 12.0) / 1_000.0) * 1_000.0,
            progressPct = coastProgress,
            isAchieved = coastAchieved,
            estimatedAge = if (coastAchieved) settings.primaryAge else coastPoint?.age,
            estimatedYear = if (coastAchieved) settings.baseYear else coastPoint?.year
        )

        // 1b. Barista FIRE (50% baseline living expenses)
        val baristaRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50))
        val baristaTarget = if (baristaRawTarget.isFinite()) max(0.0, kotlin.math.round(baristaRawTarget / 10_000.0) * 10_000.0) else 0.0
        val baristaProgress = if (baristaTarget > 0.0 && baristaTarget.isFinite()) ((investableNetWorth / baristaTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val baristaAchieved = investableNetWorth >= baristaTarget
        val baristaPoint = if (baristaAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.50), point.age)
            point.totalPortfolio >= futureTarget
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
        val leanRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75))
        val leanTarget = if (leanRawTarget.isFinite()) max(0.0, kotlin.math.round(leanRawTarget / 10_000.0) * 10_000.0) else 0.0
        val leanProgress = if (leanTarget > 0.0 && leanTarget.isFinite()) ((investableNetWorth / leanTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val leanAchieved = investableNetWorth >= leanTarget
        val leanPoint = if (leanAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 0.75), point.age)
            point.totalPortfolio >= futureTarget
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
        val standardTarget = if (standardRawTarget.isFinite()) max(0.0, kotlin.math.round(standardRawTarget / 10_000.0) * 10_000.0) else 0.0
        val standardProgress = if (standardTarget > 0.0 && standardTarget.isFinite()) ((investableNetWorth / standardTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val standardAchieved = investableNetWorth >= standardTarget
        val standardPoint = if (settings.isSingleHousehold) fireSinglePoint else fireDualPoint
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
        val fatRawTarget = fireTargetBase(milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30))
        val fatTarget = if (fatRawTarget.isFinite()) max(0.0, kotlin.math.round(fatRawTarget / 10_000.0) * 10_000.0) else 0.0
        val fatProgress = if (fatTarget > 0.0 && fatTarget.isFinite()) ((investableNetWorth / fatTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val fatAchieved = investableNetWorth >= fatTarget
        val fatPoint = if (fatAchieved) activeTrajectory.firstOrNull() else activeTrajectory.firstOrNull { point ->
            val futureTarget = fireTargetYear(point.year, milestoneSettings.copy(lifestyleCostAtFireMonthly = effectiveBaseLiving * 1.30), point.age)
            point.totalPortfolio >= futureTarget
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
            currentPensionPortfolio = snapPension,
            currentEmergencyReserve = snapReserve,
            effectiveEmergencyReserveTargetToday = effectiveReserveTarget,
            isEmergencyReserveFundedToday = isReserveFunded,
            emergencyReserveSurplusOrDeficitToday = reserveSurplusOrDeficit,
            trailingBurnRate3mMonthly = trailing3m,
            trailingBurnRate12mMonthly = trailing12m,
            ytdTaxShieldStatus = ytdTaxShield
        )
    }
}
