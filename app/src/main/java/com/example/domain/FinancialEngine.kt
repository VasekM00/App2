package com.example.domain

import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
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
    val amount: Double,
    val dayOfMonth: Int? = null
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
    val ytdTaxShieldStatus: YtdTaxShieldStatus? = null,
    val decumulationComparison: DecumulationComparisonResult? = null,
    val yearEndTaxChecklist: YearEndTaxChecklistReport? = null,
    val capitalGainsSummary: CapitalGainsPortfolioSummary? = null
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
        val safeMonth = currentMonth.coerceIn(1, 12)
        val thresholdMonthly = settings.dpsDeductionThresholdMonthly.finiteNonNegative(
            RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026
        )
        val yearPrefix = "$currentYear"
        val yearTxs = importedTransactions.filter {
            !it.isNetted && (it.yearMonth.startsWith(yearPrefix) || it.date.startsWith(yearPrefix))
        }

        val (dipYtd, dpsYtd) = if (yearTxs.isNotEmpty()) {
            val dipTxs = yearTxs.filter { it.category == "INVESTMENT_DIP" }
            val dpsTxs = yearTxs.filter { it.category == "INVESTMENT_DPS" }
            val dipActual = dipTxs.sumOf { abs(it.amount).finiteNonNegative() }
            val dpsByMonth = dpsTxs.groupBy { if (it.yearMonth.isNotBlank()) it.yearMonth else it.date.take(7) }
            val dpsActual = dpsByMonth.values.sumOf { txs ->
                val monthTotal = txs.sumOf { abs(it.amount).finiteNonNegative() }
                max(0.0, monthTotal - thresholdMonthly)
            }
            dipActual to dpsActual
        } else {
            val dipPlanned = settings.dipContributionMonthly.finiteNonNegative() * safeMonth
            val dpsPlanned = max(0.0, settings.dpsOwnContributionMonthly.finiteNonNegative() - thresholdMonthly) * safeMonth
            dipPlanned to dpsPlanned
        }

        val totalYtd = (dipYtd + dpsYtd).finiteNonNegative()
        val ceiling = settings.taxDeductionCeilingAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026
        )
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
        val vGrossAnnual = CzechTaxEngine.netToGrossAnnual(
            netMonthly = vaclavSalaryMonthly(currentYear, settings),
            taxpayerCreditAnnual = basicCredit,
            highBracketThresholdAnnual = taxThreshold,
            baseTaxRatePct = baseRatePct,
            highTaxRatePct = highRatePct
        )
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
        val monthsRemaining = max(1, 12 - safeMonth + 1)
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
            isCeilingReached = totalYtd >= ceiling
        )
    }

    fun perpetualFireLeverageMultiplier(settings: SettingsEntity): Double {
        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val buffer = (1.0 + settings.safetyBufferPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.0)
        return ((12.0 / swr) * buffer).finiteNonNegative()
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

    fun partnerBirthYear(settings: SettingsEntity): Int = settings.baseYear - settings.primaryAge

    fun netRealReturnRate(settings: SettingsEntity): Double {
        val netNominalFactor = 1.0 + netTaxableNominalReturnPct(settings) / 100.0
        val inflationFactor = (1.0 + settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(0.01)
        return ((netNominalFactor / inflationFactor) - 1.0).finiteOr(0.0)
    }

    fun annualPartnerTaxCredit(
        settings: SettingsEntity,
        year: Int,
        vGrossOverride: Double? = null
    ): Double {
        if (settings.isSingleHousehold || !settings.includeSpouseCredit) return 0.0
        if (!CzechTaxEngine.hasChildUnder3(settings, year)) return 0.0
        val spouseInc = spouseOwnIncomeAnnual(year, settings).finiteNonNegative()
        val spouseLimit = settings.spouseIncomeLimitAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026
        )
        if (spouseInc > spouseLimit) return 0.0

        val credit = settings.taxpayerCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026
        )
        val highThreshold = settings.taxSecondBracketThresholdAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        val baseRatePct = settings.taxRatePct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT)
        val highRatePct = settings.taxRateSecondPct.finiteIn(0.0, 100.0, RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT)

        val vGross = (vGrossOverride ?: CzechTaxEngine.netToGrossAnnual(
            netMonthly = vaclavSalaryMonthly(year, settings),
            taxpayerCreditAnnual = credit,
            highBracketThresholdAnnual = highThreshold,
            baseTaxRatePct = baseRatePct,
            highTaxRatePct = highRatePct
        )).finiteNonNegative()
        val vGrossTaxBeforeDeduction = run {
            val highIncome = max(0.0, vGross - highThreshold)
            val baseIncome = vGross - highIncome
            highIncome * (highRatePct / 100.0) + baseIncome * (baseRatePct / 100.0)
        }
        val vRemainingTaxBeforeDeduction = max(0.0, vGrossTaxBeforeDeduction - credit)
        val spouseCredit = settings.spouseTaxCreditAnnual.finiteNonNegative(
            RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026
        )
        return min(spouseCredit, vRemainingTaxBeforeDeduction).finiteNonNegative()
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

    fun remainingParentalPotAtYearStart(targetYear: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || targetYear < settings.baseYear) return 0.0
        val baseYear = settings.baseYear
        val monthlyDraw = settings.eParentalAllowanceMonthly.finiteNonNegative()
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
                val desired = monthlyDraw * months
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

        val desiredAnnual = settings.eParentalAllowanceMonthly.finiteNonNegative() * monthsInThisYear
        val actualAnnual = min(desiredAnnual, remainingPot)
        return actualAnnual / 12.0
    }

    fun eleonoraBenefitForMonth(year: Int, month: Int, settings: SettingsEntity): Double {
        if (settings.isSingleHousehold || year < settings.baseYear || year > settings.eReturnYear) return 0.0
        val m = month.coerceIn(1, 12)
        if (year == settings.eReturnYear && m >= settings.eReturnMonth) return 0.0
        val remainingPotAtStartOfYear = remainingParentalPotAtYearStart(year, settings)
        if (remainingPotAtStartOfYear <= 0.0) return 0.0

        val monthlyDraw = settings.eParentalAllowanceMonthly.finiteNonNegative()
        // In the current base year, honour the user's configured active monthly draw directly
        // (capped only by the available pot) so raising the monthly allowance late in the calendar year
        // never collapses current-month income to 0 by retroactively subtracting prior months.
        if (year == settings.baseYear) {
            return min(monthlyDraw, remainingPotAtStartOfYear)
        }
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
            isSingle -> 0.0
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
                    if (month >= settings.eReturnMonth) 0.0
                    else if (ledgerMatchesYear && activeLedgerEntry!!.incEleonora > 0.0) activeLedgerEntry.incEleonora
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

    private class LivingCostSpec(
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

    private data class PreFireAnnualCashflow(
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

    private fun buildPreFirePlan(
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
            val dpsInflowAnnual = (vDpsEffective + eDpsOwn + vEmpEffectiveNow + eEmpEffective) * 12.0 + subV + subE
            val dipInflowAnnual = (vDipEffective + eDipOwn) * 12.0

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
        val yieldPct = settings.dividendYieldPct.finiteNonNegative()
        val ratePct = settings.dividendTaxRatePct.finiteIn(0.0, 100.0)
        return (yieldPct * ratePct / 100.0).finiteNonNegative()
    }

    fun netTaxableNominalReturnPct(settings: SettingsEntity): Double {
        val drag = dividendTaxDragPct(settings)
        return max(0.0, settings.portfolioNominalReturnPct.finiteOr(0.0) - drag)
    }

    private class BoundedLruCache<K, V>(private val maxEntries: Int = 16) {
        private val map = object : java.util.LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxEntries
            }
        }

        @Synchronized
        fun get(key: K): V? = map[key]

        @Synchronized
        fun put(key: K, value: V) {
            map[key] = value
        }
    }

    /** Strips UI/metadata-only fields so renaming partners or learning merchant rules never busts MC caches. */
    private fun mcCacheNormalizedSettings(settings: SettingsEntity): SettingsEntity = settings.copy(
        primaryName = "",
        spouseName = "",
        child1Name = "",
        child2Name = "",
        merchantRulesJson = ""
    )

    private data class MonteCarloKey(
        val settings: SettingsEntity,
        val horizonYears: Int,
        val initialCrashPct: Double = 0.0
    )

    private val mcCache = BoundedLruCache<MonteCarloKey, MonteCarloResult>(maxEntries = 16)

    fun runMonteCarlo(settings: SettingsEntity, horizonYears: Int = 35, initialCrashPct: Double = 0.0): MonteCarloResult {
        val safeHorizon = horizonYears.coerceIn(1, 100)
        val safeCrashPct = initialCrashPct.finiteNonNegative()
        val currentKey = MonteCarloKey(
            settings = mcCacheNormalizedSettings(settings),
            horizonYears = safeHorizon,
            initialCrashPct = safeCrashPct
        )

        mcCache.get(currentKey)?.let { return it }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct.finiteNonNegative() / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)
        val includeSpouse = !settings.isSingleHousehold
        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val preFirePlan = buildPreFirePlan(settings, includeSpouse, yearsCount = safeHorizon)
        val targets = DoubleArray(safeHorizon) { y ->
            fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
        }

        val yearlyBalances = Array(safeHorizon + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        val crashMult = max(0.0, 1.0 - safeCrashPct)
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        val initialLiquid = (settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid) * crashMult
        val initialDip = (settings.dipBalanceCurrent.finiteNonNegative() + eDip) * crashMult
        val initialDps = (settings.dpsBalanceCurrent.finiteNonNegative() + eDps) * crashMult
        val initialTotal = initialLiquid + initialDip + initialDps

        for (i in 0 until sims) {
            var liquidBal = initialLiquid
            var dipBal = initialDip
            var dpsBal = initialDps
            yearlyBalances[0][i] = initialTotal
            var hitAge: Int? = if (initialTotal >= initialTarget) baseAge else null

            for (y in 0 until safeHorizon) {
                val plan = preFirePlan[y]
                val age = baseAge + y + 1
                val ret = exp(muLog + sigmaLog * nextGaussian(random)) - 1.0
                val eqFactor = max(0.0, 1.0 + ret)
                val eqHalf = 1.0 + ret * 0.5
                liquidBal = max(0.0, liquidBal * eqFactor + plan.liquidNetInflow * eqHalf)
                dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalfRet)
                val totalBal = liquidBal + dipBal + dpsBal
                yearlyBalances[y + 1][i] = totalBal

                if (hitAge == null && totalBal >= targets[y]) {
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
        for (y in 0..safeHorizon) {
            val arr = yearlyBalances[y].copyOf().apply { sort() }
            val year = baseYear + y
            val age = baseAge + y
            val target = if (y == 0) initialTarget else targets[y - 1]

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

        // Full-distribution FIRE age percentiles (failed paths recorded as Int.MAX_VALUE to eliminate survivorship bias)
        val allPathAges = IntArray(sims) { idx -> if (idx < hitAges.size) hitAges[idx] else Int.MAX_VALUE }
        val medianFireAge = allPathAges[sims / 2].takeIf { it != Int.MAX_VALUE }
        val bestCaseAge = allPathAges[(sims * 0.05).toInt().coerceIn(0, sims - 1)].takeIf { it != Int.MAX_VALUE }
        val worstCaseAge = allPathAges[(sims * 0.95).toInt().coerceIn(0, sims - 1)].takeIf { it != Int.MAX_VALUE }

        val result = MonteCarloResult(
            successRatePct = successRatePct,
            medianFireAge = medianFireAge,
            bestCaseAge = bestCaseAge,
            worstCaseAge = worstCaseAge,
            fanPoints = fanPoints,
            probabilityTable = probTable
        )

        mcCache.put(currentKey, result)
        return result
    }

    private data class HistoricalMonteCarloKey(
        val settings: SettingsEntity,
        val horizonYears: Int,
        val initialCrashPct: Double = 0.0,
        val blockSize: Int = 3
    )

    private val histMcCache = BoundedLruCache<HistoricalMonteCarloKey, MonteCarloResult>(maxEntries = 16)

    /**
     * Empirical Block-Bootstrap Monte Carlo simulation resampling from 1970-2025 equity history.
     * Preserves fat tails and multi-year sequence-of-returns drawdowns, while accounting for dividend tax drag.
     */
    fun runHistoricalMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = 35,
        initialCrashPct: Double = 0.0,
        blockSize: Int = 3
    ): MonteCarloResult {
        val safeHorizon = horizonYears.coerceIn(1, 100)
        val safeCrashPct = initialCrashPct.finiteNonNegative()
        val effectiveBlockSize = blockSize.coerceIn(1, 5)
        val currentKey = HistoricalMonteCarloKey(
            settings = mcCacheNormalizedSettings(settings),
            horizonYears = safeHorizon,
            initialCrashPct = safeCrashPct,
            blockSize = effectiveBlockSize
        )

        histMcCache.get(currentKey)?.let { return it }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val taxDrag = dividendTaxDragPct(settings)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        // Calibrate arithmetic mean to match parametric MC expected return (eliminates +41% geometric bias)
        val histArithFactor = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { max(0.01, 1.0 + (it - taxDrag) / 100.0) }.average()
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histArithFactor > 0.0 && histArithFactor.isFinite()) targetFactor / histArithFactor else 1.0
        val random = Random(settings.monteCarloSeed + 101L)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = fireTargetYear(baseYear, settings, baseAge)
        val includeSpouse = !settings.isSingleHousehold
        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val preFirePlan = buildPreFirePlan(settings, includeSpouse, yearsCount = safeHorizon)
        val targets = DoubleArray(safeHorizon) { y ->
            fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
        }

        val yearlyBalances = Array(safeHorizon + 1) { DoubleArray(sims) }
        val hitAges = mutableListOf<Int>()

        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val crashMult = max(0.0, 1.0 - safeCrashPct)
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        val initialLiquid = (settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid) * crashMult
        val initialDip = (settings.dipBalanceCurrent.finiteNonNegative() + eDip) * crashMult
        val initialDps = (settings.dpsBalanceCurrent.finiteNonNegative() + eDps) * crashMult
        val initialTotal = initialLiquid + initialDip + initialDps

        for (i in 0 until sims) {
            var liquidBal = initialLiquid
            var dipBal = initialDip
            var dpsBal = initialDps
            yearlyBalances[0][i] = initialTotal
            var hitAge: Int? = if (initialTotal >= initialTarget) baseAge else null

            val pathReturns = DoubleArray(safeHorizon)
            var yOffset = 0
            while (yOffset < safeHorizon) {
                val startIdx = random.nextInt(nHistory)
                for (b in 0 until effectiveBlockSize) {
                    if (yOffset + b < safeHorizon) {
                        val histIdx = (startIdx + b) % nHistory
                        val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                        val rawFactor = max(0.01, 1.0 + (rawReturn - taxDrag) / 100.0)
                        val netReturn = max(-0.99, rawFactor * scaleFactor - 1.0)
                        pathReturns[yOffset + b] = netReturn
                    }
                }
                yOffset += effectiveBlockSize
            }

            for (y in 0 until safeHorizon) {
                val plan = preFirePlan[y]
                val age = baseAge + y + 1
                val ret = pathReturns[y]
                val eqFactor = max(0.0, 1.0 + ret)
                val eqHalf = 1.0 + ret * 0.5
                liquidBal = max(0.0, liquidBal * eqFactor + plan.liquidNetInflow * eqHalf)
                dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalfRet)
                val totalBal = liquidBal + dipBal + dpsBal
                yearlyBalances[y + 1][i] = totalBal

                if (hitAge == null && totalBal >= targets[y]) {
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
        for (y in 0..safeHorizon) {
            val arr = yearlyBalances[y].copyOf().apply { sort() }
            val year = baseYear + y
            val age = baseAge + y
            val target = if (y == 0) initialTarget else targets[y - 1]

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

        // Full-distribution FIRE age percentiles (failed paths recorded as Int.MAX_VALUE to eliminate survivorship bias)
        val allPathAges = IntArray(sims) { idx -> if (idx < hitAges.size) hitAges[idx] else Int.MAX_VALUE }
        val medianFireAge = allPathAges[sims / 2].takeIf { it != Int.MAX_VALUE }
        val bestCaseAge = allPathAges[(sims * 0.05).toInt().coerceIn(0, sims - 1)].takeIf { it != Int.MAX_VALUE }
        val worstCaseAge = allPathAges[(sims * 0.95).toInt().coerceIn(0, sims - 1)].takeIf { it != Int.MAX_VALUE }

        val result = MonteCarloResult(
            successRatePct = successRatePct,
            medianFireAge = medianFireAge,
            bestCaseAge = bestCaseAge,
            worstCaseAge = worstCaseAge,
            fanPoints = fanPoints,
            probabilityTable = probTable
        )

        histMcCache.put(currentKey, result)
        return result
    }

    private data class SurvivalCacheKey(
        val settings: SettingsEntity,
        val useHistoricalBootstrap: Boolean
    )

    private val parametricSurvivalCache = BoundedLruCache<SurvivalCacheKey, RetirementSurvivalResult>(maxEntries = 16)
    private val histSurvivalCache = BoundedLruCache<SurvivalCacheKey, RetirementSurvivalResult>(maxEntries = 16)

    /**
     * Simulates accumulation to FIRE and then a full withdrawal phase to the configured
     * retirement horizon. Success = the portfolio never depletes among paths that reach FIRE.
     * Incorporates dividend tax drag, Guyton-Klinger dynamic spending guardrails, separate DPS/DIP
     * compounding, Barista transition cashflows, and supports both parametric log-normal and
     * historical empirical block-bootstrap resampling.
     */
    fun runRetirementSurvival(
        settings: SettingsEntity,
        useHistoricalBootstrap: Boolean = false
    ): RetirementSurvivalResult {
        val currentKey = SurvivalCacheKey(mcCacheNormalizedSettings(settings), useHistoricalBootstrap)
        if (useHistoricalBootstrap) {
            histSurvivalCache.get(currentKey)?.let { return it }
        } else {
            parametricSurvivalCache.get(currentKey)?.let { return it }
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct.finiteNonNegative() / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed + (if (useHistoricalBootstrap) 202L else 1L))

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val partnerBirth = partnerBirthYear(settings)
        val cpi = (settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(-0.99)
        val maxAccumYears = 35
        val horizon = settings.retirementHorizonYears.coerceIn(10, 60)
        val totalMaxOffset = maxAccumYears + horizon + 5
        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val taxDrag = dividendTaxDragPct(settings)
        val histArithFactor = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { max(0.01, 1.0 + (it - taxDrag) / 100.0) }.average()
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histArithFactor > 0.0 && histArithFactor.isFinite()) targetFactor / histArithFactor else 1.0
        val blockSize = 3
        val includeSpouse = !settings.isSingleHousehold

        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val lifestyleToday = baselineLivingCostAtFireMonthly(settings)
        val vPensionToday = settings.vStatePensionMonthly.finiteNonNegative()
        val ePensionToday = if (includeSpouse) settings.eStatePensionMonthly.finiteNonNegative() else 0.0
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        val initialLiquid = settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid
        val initialDip = settings.dipBalanceCurrent.finiteNonNegative() + eDip
        val initialDps = settings.dpsBalanceCurrent.finiteNonNegative() + eDps

        val preFirePlan = buildPreFirePlan(settings, includeSpouse, yearsCount = maxAccumYears)
        val indexationTable = DoubleArray(totalMaxOffset + 1) { offset ->
            (1.0 + cpi).coerceAtLeast(0.0).pow(offset).finiteOr(1.0)
        }
        val targets = DoubleArray(maxAccumYears) { y ->
            fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
        }
        val plannedTargetsByOffset = DoubleArray(totalMaxOffset + 1) { offset ->
            fireTargetYear(baseYear + offset, settings, baseAge + offset).coerceAtLeast(1.0)
        }

        val livingSpec = LivingCostSpec.from(settings)
        val hasLifestyleOverride = settings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0
        val rentShareInLiving = if (!hasLifestyleOverride) {
            val totalBase = livingSpec.baseRentMonthly + livingSpec.nonRentBaseMonthly
            if (totalBase > 0.0) (livingSpec.baseRentMonthly / totalBase).coerceIn(0.0, 1.0) else 0.0
        } else 0.0
        val rentGrowthRate = (livingSpec.rentGrowthPct / 100.0).coerceAtLeast(-0.99)
        val livingIndexationTable = DoubleArray(totalMaxOffset + 1) { offset ->
            val cpiF = indexationTable[offset]
            if (rentShareInLiving > 0.0) {
                val rentF = (1.0 + rentGrowthRate).coerceAtLeast(0.0).pow(offset).finiteOr(1.0)
                rentShareInLiving * rentF + (1.0 - rentShareInLiving) * cpiF
            } else {
                cpiF
            }
        }

        val initialTarget0 = plannedTargetsByOffset[0]
        val canRetireAtYear0 = (initialLiquid + initialDip + initialDps) >= initialTarget0

        val reserveTargetAnnual = effectiveEmergencyReserveTarget(settings).finiteNonNegative()
        val initialCashCushion = settings.emergencyReserveCurrent.finiteNonNegative()
        val upperThreshold = 1.0 + (settings.guardrailsUpperThresholdPct.finiteNonNegative() / 100.0)
        val lowerThreshold = (1.0 - (settings.guardrailsLowerThresholdPct.finiteNonNegative() / 100.0)).coerceAtLeast(0.10)
        val cutMultiplier = (1.0 - (settings.guardrailsCutPct.finiteNonNegative() / 100.0)).coerceIn(0.50, 0.99)
        val boostMultiplier = (1.0 + (settings.guardrailsBoostPct.finiteNonNegative() / 100.0)).coerceIn(1.01, 1.50)
        val floorMultiplier = (settings.guardrailsFloorPct.finiteNonNegative() / 100.0).coerceIn(0.30, 0.95)

        var fireReached = 0
        var survived = 0
        var pathsWithGuardrailsTriggered = 0
        val endBalancesToday = ArrayList<Double>(sims)
        val depletionAges = ArrayList<Int>()

        for (i in 0 until sims) {
            val pathReturns = DoubleArray(totalMaxOffset)
            if (useHistoricalBootstrap) {
                var yOffset = 0
                while (yOffset < totalMaxOffset) {
                    val startIdx = random.nextInt(nHistory)
                    for (b in 0 until blockSize) {
                        if (yOffset + b < totalMaxOffset) {
                            val histIdx = (startIdx + b) % nHistory
                            val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                            val rawFactor = max(0.01, 1.0 + (rawReturn - taxDrag) / 100.0)
                            val netReturn = max(-0.99, rawFactor * scaleFactor - 1.0)
                            pathReturns[yOffset + b] = netReturn
                        }
                    }
                    yOffset += blockSize
                }
            } else {
                for (t in 0 until totalMaxOffset) {
                    pathReturns[t] = exp(muLog + sigmaLog * nextGaussian(random)) - 1.0
                }
            }

            var liquidBal = initialLiquid
            var dipBal = initialDip
            var dpsBal = initialDps
            var fireYear = if (canRetireAtYear0) baseYear else -1

            if (fireYear < 0) {
                for (y in 0 until maxAccumYears) {
                    val plan = preFirePlan[y]
                    val ret = pathReturns[y]
                    val eqFactor = max(0.0, 1.0 + ret)
                    val eqHalf = 1.0 + ret * 0.5
                    liquidBal = max(0.0, liquidBal * eqFactor + plan.liquidNetInflow * eqHalf)
                    dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                    dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalfRet)
                    if ((liquidBal + dipBal + dpsBal) >= targets[y]) {
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
            val fireOffset = (fireYear - baseYear).coerceIn(0, totalMaxOffset)
            var cashCushion = initialCashCushion * indexationTable[fireOffset]
            var bal = liquidBal + dipBal + dpsBal + cashCushion
            for (k in 0 until horizon) {
                val yearOffset = (fireYear - baseYear) + k
                val age = baseAge + yearOffset
                val eAge = (baseYear + yearOffset) - partnerBirth

                // Statutory Czech lock-in guard (Act No. 586/1992 Coll. § 15a & Act No. 427/2011 Coll. § 94):
                // Pension assets (DIP/DPS) unlock into the liquid spending pool at age 60.
                if (age >= 60 && (dipBal > 0.0 || dpsBal > 0.0)) {
                    liquidBal += dipBal + dpsBal
                    dipBal = 0.0
                    dpsBal = 0.0
                }

                val safeOffset = yearOffset.coerceIn(0, totalMaxOffset)
                val indexation = indexationTable[safeOffset]
                val livingIndexation = livingIndexationTable[safeOffset]
                val lifestyleAnnual = lifestyleToday * 12.0 * livingIndexation * spendingScale
                var pensionAnnual = 0.0
                if (age >= settings.vStatePensionAge) pensionAnnual += vPensionToday * 12.0 * indexation
                if (includeSpouse && eAge >= settings.eStatePensionAge) {
                    pensionAnnual += ePensionToday * 12.0 * indexation
                }
                var need = max(0.0, lifestyleAnnual - pensionAnnual)

                val currentTotalBal = liquidBal + dipBal + dpsBal + cashCushion
                val baseNeedAtAge = max(0.0, lifestyleToday * 12.0 * livingIndexation - pensionAnnual)
                if (settings.guardrailsEnabled && currentTotalBal > 0.0 && baseNeedAtAge > 0.0) {
                    val plannedTargetAtAge = plannedTargetsByOffset[safeOffset]
                    val referenceSwr = (baseNeedAtAge / plannedTargetAtAge).coerceAtLeast(0.001)
                    val currentWithdrawalRate = need / currentTotalBal

                    val isSunsetPhase = horizon > 15 && k >= horizon - 15
                    if (!isSunsetPhase && currentWithdrawalRate > referenceSwr * upperThreshold) {
                        // Upper Guardrail (Capital Preservation Rule):
                        // Reduce discretionary spending, protected by customizable essential floor,
                        // subject to Guyton-Klinger 15-year sunset rule (only when horizon > 15y)
                        spendingScale = (spendingScale * cutMultiplier).coerceAtLeast(floorMultiplier)
                        val adjustedLifestyle = lifestyleToday * 12.0 * livingIndexation * spendingScale
                        need = max(0.0, adjustedLifestyle - pensionAnnual)
                        guardrailTriggeredInPath = true
                    } else if (currentWithdrawalRate < referenceSwr * lowerThreshold) {
                        // Lower Guardrail (Prosperity Rule): boost spending
                        spendingScale = min(1.30, spendingScale * boostMultiplier)
                        val adjustedLifestyle = lifestyleToday * 12.0 * livingIndexation * spendingScale
                        need = max(0.0, adjustedLifestyle - pensionAnnual)
                    }
                }

                val ret = pathReturns.getOrNull(yearOffset) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)

                // 1. Compound balances first for the investment year
                val eqFactor = max(0.0, 1.0 + ret)
                liquidBal *= eqFactor
                dipBal *= eqFactor
                dpsBal *= dpsFactor
                cashCushion *= (1.0 + cpi).coerceAtLeast(0.0)

                // 2. Dynamic cash cushion sequence-of-returns defense:
                // During market drawdowns (ret < 0), draw living expenses from emergency cash reserve
                // first rather than liquidating equity at market troughs.
                val indexedReserveTarget = reserveTargetAnnual * indexation
                if (ret < 0.0 && cashCushion > 0.0) {
                    val drawFromCash = min(cashCushion, need)
                    cashCushion -= drawFromCash
                    need -= drawFromCash
                } else if (ret > 0.05 && cashCushion < indexedReserveTarget) {
                    val replenishBase = liquidBal + (if (age >= 60) dipBal + dpsBal else 0.0)
                    val replenish = min(indexedReserveTarget - cashCushion, replenishBase * 0.02)
                    if (age < 60 && liquidBal > replenish) {
                        liquidBal -= replenish
                        cashCushion += replenish
                    } else if (age >= 60 && replenishBase > replenish) {
                        if (liquidBal >= replenish) {
                            liquidBal -= replenish
                        } else {
                            var rem = replenish - liquidBal
                            liquidBal = 0.0
                            val takeDip = min(dipBal, rem)
                            dipBal -= takeDip
                            rem -= takeDip
                            dpsBal = max(0.0, dpsBal - rem)
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
                    bal = liquidBal + dipBal + dpsBal + cashCushion
                } else {
                    var available = liquidBal + dipBal + dpsBal
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
                    dipBal = 0.0
                    dpsBal = 0.0
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
            val deflator = indexationTable[totalYearsFromBase.coerceIn(0, totalMaxOffset)]
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
            histSurvivalCache.put(currentKey, result)
        } else {
            parametricSurvivalCache.put(currentKey, result)
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
        val ledgerMatchesBaseYear = activeLedgerEntry != null &&
            (activeLedgerEntry.yearMonth.isBlank() || activeLedgerEntry.yearMonth.startsWith("${settings.baseYear}-"))
        val currentYear = if (ledgerMatchesBaseYear) {
            activeLedgerEntry!!.yearMonth.substringBefore('-').toIntOrNull() ?: settings.baseYear
        } else {
            settings.baseYear
        }
        val monthForTaxShield = activeMonth ?: 1
        val trailing3m = computeTrailingBurnRateMonthly(ledgerEntries, 3)
        val trailing12m = computeTrailingBurnRateMonthly(ledgerEntries, 12)

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0

        val hasLiquidOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.portfolioBalanceAtMonthEnd.finiteNonNegative() > 0.0
        val hasPensionOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.pensionBalanceAtMonthEnd.finiteNonNegative() > 0.0
        val hasReserveOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.emergencyReserveAtMonthEnd.finiteNonNegative() > 0.0

        // Per-field fallback: the ledger dialog stores blank balance fields as 0.0, so a 0.0 value
        // means "not entered" and must fall back to settings independently for each bucket.
        val snapLiquid = if (hasLiquidOverride) {
            activeLedgerEntry!!.portfolioBalanceAtMonthEnd.finiteNonNegative()
        } else {
            settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid
        }

        val snapPension = if (hasPensionOverride) {
            activeLedgerEntry!!.pensionBalanceAtMonthEnd.finiteNonNegative()
        } else {
            settings.dpsBalanceCurrent.finiteNonNegative() + eDps + settings.dipBalanceCurrent.finiteNonNegative() + eDip
        }

        val snapReserve = if (hasReserveOverride) {
            activeLedgerEntry!!.emergencyReserveAtMonthEnd.finiteNonNegative()
        } else {
            settings.emergencyReserveCurrent.finiteNonNegative()
        }

        val projectionSettings = run {
            var updated = settings
            if (hasLiquidOverride) {
                updated = if (settings.isSingleHousehold || snapLiquid >= eLiquid) {
                    updated.copy(liquidPortfolioCurrent = (snapLiquid - eLiquid).coerceAtLeast(0.0))
                } else {
                    updated.copy(liquidPortfolioCurrent = 0.0, eLiquidPortfolioCurrent = snapLiquid)
                }
            }
            if (hasPensionOverride) {
                val ePenSum = eDps + eDip
                if (settings.isSingleHousehold || snapPension >= ePenSum) {
                    val primaryPen = (snapPension - ePenSum).coerceAtLeast(0.0)
                    val curPrimary = settings.dpsBalanceCurrent.finiteNonNegative() + settings.dipBalanceCurrent.finiteNonNegative()
                    val dpsShare = if (curPrimary > 0.0) settings.dpsBalanceCurrent.finiteNonNegative() / curPrimary else 1.0
                    val newDps = primaryPen * dpsShare
                    updated = updated.copy(dpsBalanceCurrent = newDps, dipBalanceCurrent = primaryPen - newDps)
                } else {
                    val eDpsShare = if (ePenSum > 0.0) eDps / ePenSum else 1.0
                    val newEDps = snapPension * eDpsShare
                    updated = updated.copy(
                        dpsBalanceCurrent = 0.0,
                        dipBalanceCurrent = 0.0,
                        eDpsBalanceCurrent = newEDps,
                        eDipBalanceCurrent = snapPension - newEDps
                    )
                }
            }
            if (hasReserveOverride) {
                updated = updated.copy(emergencyReserveCurrent = snapReserve)
            }
            if (ledgerMatchesBaseYear && activeLedgerEntry!!.incVaclav > 0.0) {
                updated = updated.copy(vSalary = activeLedgerEntry.incVaclav)
            }
            updated
        }

        val ytdTaxShield = computeYtdTaxShieldStatus(
            settings = projectionSettings,
            currentYear = currentYear,
            currentMonth = monthForTaxShield,
            importedTransactions = importedTransactions
        )
        val fireBase = fireTargetBase(projectionSettings)
        val dual = buildLiquidPortfolio(projectionSettings, true)
        val single = buildLiquidPortfolio(projectionSettings, false)

        val fireDualPoint = dual.firstOrNull { it.totalPortfolio >= it.target }
        val fireSinglePoint = single.firstOrNull { it.totalPortfolio >= it.target }
        val activeTrajectory = if (projectionSettings.isSingleHousehold) single else dual
        val fireLiquidBridgePoint = activeTrajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }

        val netWorth = (snapLiquid + snapReserve + snapPension).finiteNonNegative()

        val rReal = netRealReturnRate(settings)
        val annualLiving = if (settings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0) {
            settings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0
        } else {
            totalLivingCostMonthly(settings) * 12.0
        }
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

        val dps = buildDpsProjection(projectionSettings)
        val dip = buildDipProjection(projectionSettings)

        val spouseInc = spouseOwnIncomeAnnual(projectionSettings.baseYear, projectionSettings)
        val hasChildUnder3 = CzechTaxEngine.hasChildUnder3(projectionSettings, projectionSettings.baseYear)

        val spouseEligible = !projectionSettings.isSingleHousehold &&
                projectionSettings.includeSpouseCredit &&
                hasChildUnder3 &&
                (spouseInc <= projectionSettings.spouseIncomeLimitAnnual)

        val spouseCreditVal = if (spouseEligible) annualPartnerTaxCredit(projectionSettings, projectionSettings.baseYear) else 0.0
        val childBonusVal = CzechTaxEngine.annualChildTaxRelief(
            settings = projectionSettings,
            year = projectionSettings.baseYear,
            vSpouseCreditAnnual = spouseCreditVal
        )
        val retirementTaxSaved = annualRetirementTaxSaved(projectionSettings, projectionSettings.baseYear)
        val incrementalValue = spouseCreditVal + childBonusVal + retirementTaxSaved

        val taxHelper = TaxReturnHelperData(
            year = projectionSettings.baseYear,
            taxpayerCredit = projectionSettings.taxpayerCreditAnnual,
            spouseCredit = spouseCreditVal,
            childBonus = childBonusVal,
            retirementDeductionBase = annualRetirementDeduction(projectionSettings),
            dipSaving = retirementTaxSaved,
            totalIncrementalValue = incrementalValue,
            spouseOwnIncome = spouseInc,
            spouseEligible = spouseEligible
        )

        val monteCarlo = if (runMonteCarlo) runMonteCarlo(projectionSettings) else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val historicalMonteCarlo = if (runMonteCarlo) runHistoricalMonteCarlo(projectionSettings) else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val retirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(projectionSettings, useHistoricalBootstrap = false)
        } else {
            RetirementSurvivalResult(
                successRatePct = 0.0,
                fireProbabilityPct = 0.0,
                horizonYears = projectionSettings.retirementHorizonYears.coerceIn(10, 60),
                medianEndBalanceToday = 0.0,
                p5EndBalanceToday = 0.0,
                medianDepletionAge = null,
                sampleSize = 0,
                guardrailsActive = projectionSettings.guardrailsEnabled,
                guardrailTriggeredPct = 0.0
            )
        }
        val historicalRetirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(projectionSettings, useHistoricalBootstrap = true)
        } else retirementSurvival
        val stressScenarios = calculateStressScenarios(projectionSettings, runMonteCarlo = runMonteCarlo)

        val savingsRate = if (currentIncome.totalMonthly > 0) {
            ((investMonthly / currentIncome.totalMonthly) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        val actionsImpacts = mapOf(
            // B1 fix: ac1 respects eIncludeLecturing toggle
            "ac1" to (if (!settings.isSingleHousehold && settings.eIncludeLecturing) settings.eLecturingMonthly.finiteNonNegative() * 12.0 else 0.0),
            "ac2" to taxHelper.totalIncrementalValue.finiteNonNegative(),
            "ac3" to (snapLiquid.finiteNonNegative() * 0.01),
            "ac4" to (snapReserve.finiteNonNegative() * 0.04),
            "ac5" to dip.taxSavedYear.finiteNonNegative(),
            "ac6" to (if (!settings.isSingleHousehold) {
                settings.eStartingSalary.finiteNonNegative() * 12.0 * (settings.eReinvestedPct.finiteIn(0.0, 100.0) / 100.0)
            } else 0.0),
            "ac8" to (settings.subscriptionsMonthly.finiteNonNegative() * 12.0),
            "ac9" to max(0.0, snapReserve.finiteNonNegative() - effectiveReserveTarget),
            "ac10" to (snapPension.finiteNonNegative() * 0.005)
        )

        val investableNetWorth = snapLiquid + snapPension

        val swr = (settings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val realReturnRate = rReal
        val yearsToRetire = max(0, settings.vStatePensionAge - settings.primaryAge)
        val cpiCompounding = (1.0 + settings.cpiInflationPct / 100.0).coerceAtLeast(0.0)
        val milestoneSettings = settings.copy(fireTargetOverride = 0.0)
        val effectiveBaseLiving = baselineLivingCostAtFireMonthly(milestoneSettings)

        // 1. Coast FIRE
        // The portfolio needs to grow on its own without additional contributions to reach the required
        // capital at statutory state pension age (settings.vStatePensionAge), where bridge years are 0.
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

        // 1b. Barista FIRE (50% baseline living expenses)
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

        // 2. Lean FIRE (75% baseline living expenses)
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

        // 3. Standard FIRE (100% baseline living expenses)
        val standardRawTarget = fireBase
        val standardTarget = if (standardRawTarget.isFinite()) max(0.0, kotlin.math.round(standardRawTarget / 10_000.0) * 10_000.0) else 0.0
        val standardProgress = if (standardTarget > 0.0 && standardTarget.isFinite()) ((investableNetWorth / standardTarget) * 100.0).coerceIn(0.0, 100.0) else 100.0
        val standardAchieved = investableNetWorth >= standardTarget && bridgeFundedToday
        val standardPoint = fireLiquidBridgePoint ?: (if (settings.isSingleHousehold) fireSinglePoint else fireDualPoint)
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

        val fireMilestones = FireMilestonesSummary(
            coastFire = coastMilestone,
            baristaFire = baristaMilestone,
            leanFire = leanMilestone,
            standardFire = standardMilestone,
            fatFire = fatMilestone
        )

        val perpetualMultiplier = perpetualFireLeverageMultiplier(settings)
        val fireReductionFor100 = 100.0 * perpetualMultiplier

        val decumulationBase = maxOf(
            (snapLiquid + snapPension).finiteNonNegative(),
            fireBase.finiteNonNegative(),
            1_000_000.0
        )
        val decumulationSwr = settings.safeWithdrawalRatePct.finiteIn(0.5, 20.0, 4.0)
        val decumulationComparison = if (runMonteCarlo) {
            DecumulationStrategies.compareStrategies(
                DecumulationConfig(
                    initialPortfolio = decumulationBase,
                    initialAnnualWithdrawal = decumulationBase * (decumulationSwr / 100.0),
                    horizonYears = settings.retirementHorizonYears.coerceIn(5, 60),
                    annualInflationPct = settings.cpiInflationPct.finiteAtLeast(-99.0, 2.5),
                    expectedAnnualReturnPct = netTaxableNominalReturnPct(settings),
                    returnVolatilityPct = settings.monteCarloVolatilityPct.finiteNonNegative(15.0),
                    simulationsCount = settings.monteCarloN.coerceIn(50, 2000),
                    randomSeed = settings.monteCarloSeed,
                    gkUpperThresholdPct = settings.guardrailsUpperThresholdPct.finiteIn(5.0, 100.0, 20.0),
                    gkLowerThresholdPct = settings.guardrailsLowerThresholdPct.finiteIn(5.0, 100.0, 20.0),
                    gkCutPct = settings.guardrailsCutPct.finiteIn(1.0, 50.0, 10.0),
                    gkBoostPct = settings.guardrailsBoostPct.finiteIn(1.0, 50.0, 10.0),
                    gkFloorPctOfInitial = settings.guardrailsFloorPct.finiteIn(10.0, 100.0, 70.0),
                    gkInitialWithdrawalMultiplier = 1.15,
                    vpdRatePct = (decumulationSwr * 1.075).coerceIn(0.5, 20.0),
                    vpdFloorPctOfInitial = settings.guardrailsFloorPct.finiteIn(10.0, 100.0, 70.0)
                )
            )
        } else null

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
            realReturnPct = realReturnRate * 100.0,
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
            ytdTaxShieldStatus = ytdTaxShield,
            decumulationComparison = decumulationComparison,
            yearEndTaxChecklist = CzechYearEndTaxChecklist.generateChecklist(
                settings = projectionSettings,
                taxYear = projectionSettings.baseYear,
                spouseOwnIncomeAnnual = spouseInc,
                hasChildUnder3 = hasChildUnder3
            )
        )
    }
}
