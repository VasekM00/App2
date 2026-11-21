package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.util.Formatters.fmtExactCZK
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * DecumulationStrategies provides comparative modeling of 3 major retirement withdrawal strategies:
 * 1. Constant Real SWR (Bengen 4% rule, strictly adjusted for Czech CPI).
 * 2. Guyton-Klinger Guardrails (Capital Preservation cut by 10% if withdrawal rate rises 20% above initial,
 *    Capital Prosperity boost by 10% if withdrawal rate falls 20% below initial).
 * 3. Variable Percentage Decumulation (Dynamic % of remaining portfolio with living floor and ceiling).
 */
object DecumulationStrategies {

    /**
     * Executes a paired comparative simulation of all 3 decumulation strategies across N Monte Carlo iterations.
     * Uses identical market return sequences per iteration for rigorous, unbiased comparison.
     */
    fun compareStrategies(config: DecumulationConfig): DecumulationComparisonResult {
        val nSims = config.simulationsCount.coerceIn(50, 5000)
        val horizon = config.horizonYears.coerceIn(5, 60)
        val initialBal = config.initialPortfolio.finiteNonNegative()
        val rawNeed = config.initialAnnualWithdrawal.finiteOr(initialBal * 0.04)
        val initialNeed = if (rawNeed > 0.0) rawNeed else initialBal * 0.04
        val initialSwrRate = if (initialBal > 0.0) initialNeed / initialBal else 0.04
        val cpi = config.annualInflationPct.finiteAtLeast(-99.0, 2.8) / 100.0

        val meanNominalReturn = config.expectedAnnualReturnPct.finiteAtLeast(-99.0, 7.0) / 100.0
        val sigma = config.returnVolatilityPct.finiteNonNegative(15.0) / 100.0
        val safeFactor = max(0.01, 1.0 + meanNominalReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)

        val random = Random(config.randomSeed)

        // Generate return sequences for all simulations: Array of DoubleArray(horizon)
        val returnPaths = Array(nSims) {
            DoubleArray(horizon) {
                val z = nextGaussian(random)
                exp(muLog + sigmaLog * z) - 1.0
            }
        }

        val inflationFactors = buildInflationFactors(horizon, cpi)

        // Run simulation for each strategy with identical paths
        val bengenMetrics = evaluateStrategy(
            type = DecumulationStrategyType.CONSTANT_REAL_SWR,
            config = config,
            returnPaths = returnPaths,
            inflationFactors = inflationFactors,
            horizon = horizon,
            initialBal = initialBal,
            initialNeed = initialNeed,
            initialSwrRate = initialSwrRate,
            cpi = cpi
        )

        val gkMult = config.gkInitialWithdrawalMultiplier.finiteIn(0.2, 3.0, 1.0)
        val gkInitialNeed = initialNeed * gkMult
        val gkInitialSwrRate = if (initialBal > 0.0) gkInitialNeed / initialBal else initialSwrRate * gkMult
        val gkMetrics = evaluateStrategy(
            type = DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS,
            config = config,
            returnPaths = returnPaths,
            inflationFactors = inflationFactors,
            horizon = horizon,
            initialBal = initialBal,
            initialNeed = gkInitialNeed,
            initialSwrRate = gkInitialSwrRate,
            cpi = cpi
        )

        val vpdMetrics = evaluateStrategy(
            type = DecumulationStrategyType.VARIABLE_PERCENTAGE,
            config = config,
            returnPaths = returnPaths,
            inflationFactors = inflationFactors,
            horizon = horizon,
            initialBal = initialBal,
            initialNeed = initialNeed,
            initialSwrRate = initialSwrRate,
            cpi = cpi
        )

        val strategiesMap = mapOf(
            DecumulationStrategyType.CONSTANT_REAL_SWR to bengenMetrics,
            DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS to gkMetrics,
            DecumulationStrategyType.VARIABLE_PERCENTAGE to vpdMetrics
        )

        val recommended = when {
            vpdMetrics.survivalRatePct > gkMetrics.survivalRatePct && vpdMetrics.survivalRatePct >= bengenMetrics.survivalRatePct ->
                DecumulationStrategyType.VARIABLE_PERCENTAGE
            gkMetrics.survivalRatePct >= bengenMetrics.survivalRatePct && gkMetrics.survivalRatePct >= vpdMetrics.survivalRatePct ->
                DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS
            bengenMetrics.survivalRatePct >= gkMetrics.survivalRatePct && bengenMetrics.survivalRatePct >= vpdMetrics.survivalRatePct ->
                DecumulationStrategyType.CONSTANT_REAL_SWR
            else -> DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS
        }

        val summary = buildSummary(bengenMetrics, gkMetrics, vpdMetrics, recommended)

        return DecumulationComparisonResult(
            config = config,
            strategies = strategiesMap,
            bengenMetrics = bengenMetrics,
            guytonKlingerMetrics = gkMetrics,
            variablePercentageMetrics = vpdMetrics,
            recommendedStrategy = recommended,
            executiveSummary = summary
        )
    }

    private data class WithdrawalStep(
        val withdrawalNominal: Double,
        val isCut: Boolean,
        val isBoost: Boolean
    )

    private fun buildInflationFactors(horizon: Int, cpi: Double): DoubleArray {
        val factors = DoubleArray(horizon + 1)
        factors[0] = 1.0
        val mult = 1.0 + cpi
        for (i in 1..horizon) {
            factors[i] = factors[i - 1] * mult
        }
        return factors
    }

    private fun computeWithdrawalStep(
        type: DecumulationStrategyType,
        year: Int,
        horizon: Int,
        balance: Double,
        initialNeed: Double,
        initialSwr: Double,
        previousWithdrawalNominal: Double,
        inflationFactor: Double,
        cpi: Double,
        gkUpperThresholdPct: Double,
        gkLowerThresholdPct: Double,
        gkCutPct: Double,
        gkBoostPct: Double,
        gkFloorPctOfInitial: Double,
        vpdRatePct: Double,
        vpdFloorPctOfInitial: Double,
        vpdCeilingPctOfInitial: Double
    ): WithdrawalStep {
        return when (type) {
            DecumulationStrategyType.CONSTANT_REAL_SWR -> {
                WithdrawalStep(initialNeed * inflationFactor, isCut = false, isBoost = false)
            }

            DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS -> {
                val tentative = if (year == 1) initialNeed else previousWithdrawalNominal * (1.0 + cpi)
                val currentRate = if (balance > 0.0) tentative / balance else 1.0
                val upperThreshold = initialSwr * (1.0 + gkUpperThresholdPct.finiteNonNegative(20.0) / 100.0)
                val lowerThreshold = initialSwr * (1.0 - gkLowerThresholdPct.finiteIn(0.0, 100.0, 20.0) / 100.0)
                val isSunsetPhase = horizon > 15 && year > horizon - 15
                val gkFloorNominal = (initialNeed * inflationFactor) * (gkFloorPctOfInitial.finiteIn(0.0, 100.0, 70.0) / 100.0)

                if (!isSunsetPhase && currentRate > upperThreshold) {
                    val cut = gkCutPct.finiteIn(0.0, 100.0, 10.0) / 100.0
                    WithdrawalStep(max(gkFloorNominal, tentative * (1.0 - cut)), isCut = true, isBoost = false)
                } else if (currentRate < lowerThreshold) {
                    val boost = gkBoostPct.finiteNonNegative(10.0) / 100.0
                    WithdrawalStep(tentative * (1.0 + boost), isCut = false, isBoost = true)
                } else {
                    WithdrawalStep(tentative, isCut = false, isBoost = false)
                }
            }

            DecumulationStrategyType.VARIABLE_PERCENTAGE -> {
                val rate = vpdRatePct.finiteIn(0.0, 100.0, 4.5) / 100.0
                val dynamicWithdrawal = balance * rate
                val rawFloor = vpdFloorPctOfInitial.finiteNonNegative(70.0)
                val rawCeiling = vpdCeilingPctOfInitial.finiteNonNegative(130.0)
                val floorPct = min(rawFloor, rawCeiling)
                val ceilingPct = max(rawFloor, rawCeiling)
                val baseNeed = initialNeed * inflationFactor
                val floorNominal = baseNeed * (floorPct / 100.0)
                val ceilingNominal = baseNeed * (ceilingPct / 100.0)
                WithdrawalStep(dynamicWithdrawal.coerceIn(floorNominal, ceilingNominal), isCut = false, isBoost = false)
            }
        }
    }

    /**
     * Simulates a single deterministic trajectory for one specific strategy over given returns.
     */
    fun simulateSinglePath(
        type: DecumulationStrategyType,
        initialPortfolio: Double,
        initialAnnualWithdrawal: Double,
        annualReturns: List<Double>,
        annualInflationPct: Double = 2.8,
        gkUpperThresholdPct: Double = 20.0,
        gkLowerThresholdPct: Double = 20.0,
        gkCutPct: Double = 10.0,
        gkBoostPct: Double = 10.0,
        gkFloorPctOfInitial: Double = 70.0,
        vpdRatePct: Double = 4.5,
        vpdFloorPctOfInitial: Double = 70.0,
        vpdCeilingPctOfInitial: Double = 130.0
    ): List<SinglePathYearResult> {
        val horizon = annualReturns.size
        if (horizon == 0) return emptyList()
        val cpi = annualInflationPct.finiteAtLeast(-99.0, 2.8) / 100.0
        val inflationFactors = buildInflationFactors(horizon, cpi)
        val results = ArrayList<SinglePathYearResult>(horizon)

        var balance = initialPortfolio.finiteNonNegative()
        val initialNeed = initialAnnualWithdrawal.finiteNonNegative()
        val initialSwr = if (balance > 0.0) initialNeed / balance else 0.04
        var previousWithdrawalNominal = initialNeed

        for (y in 0 until horizon) {
            val year = y + 1
            val ret = annualReturns[y].finiteAtLeast(-1.0, 0.0)
            val inflationFactor = inflationFactors[y]

            if (balance <= 0.0) {
                results.add(
                    SinglePathYearResult(
                        year = year,
                        startingPortfolio = 0.0,
                        withdrawalNominal = 0.0,
                        withdrawalReal = 0.0,
                        marketReturnPct = ret * 100.0,
                        endingPortfolio = 0.0,
                        isDepleted = true
                    )
                )
                continue
            }

            val step = computeWithdrawalStep(
                type = type,
                year = year,
                horizon = horizon,
                balance = balance,
                initialNeed = initialNeed,
                initialSwr = initialSwr,
                previousWithdrawalNominal = previousWithdrawalNominal,
                inflationFactor = inflationFactor,
                cpi = cpi,
                gkUpperThresholdPct = gkUpperThresholdPct,
                gkLowerThresholdPct = gkLowerThresholdPct,
                gkCutPct = gkCutPct,
                gkBoostPct = gkBoostPct,
                gkFloorPctOfInitial = gkFloorPctOfInitial,
                vpdRatePct = vpdRatePct,
                vpdFloorPctOfInitial = vpdFloorPctOfInitial,
                vpdCeilingPctOfInitial = vpdCeilingPctOfInitial
            )

            val actualWithdrawal = min(balance, step.withdrawalNominal)
            previousWithdrawalNominal = actualWithdrawal
            val postWithdrawalBal = balance - actualWithdrawal
            val endingBal = max(0.0, postWithdrawalBal * (1.0 + ret)).finiteNonNegative()

            results.add(
                SinglePathYearResult(
                    year = year,
                    startingPortfolio = balance,
                    withdrawalNominal = actualWithdrawal,
                    withdrawalReal = actualWithdrawal / inflationFactor,
                    marketReturnPct = ret * 100.0,
                    endingPortfolio = endingBal,
                    isGuardrailCut = step.isCut,
                    isGuardrailBoost = step.isBoost,
                    isDepleted = endingBal <= 0.0
                )
            )

            balance = endingBal
        }

        return results
    }

    private fun evaluateStrategy(
        type: DecumulationStrategyType,
        config: DecumulationConfig,
        returnPaths: Array<DoubleArray>,
        inflationFactors: DoubleArray,
        horizon: Int,
        initialBal: Double,
        initialNeed: Double,
        initialSwrRate: Double,
        cpi: Double
    ): StrategyMetrics {
        val nSims = returnPaths.size
        var survivedCount = 0
        val endingBalancesNominal = ArrayList<Double>(nSims)
        val endingBalancesReal = ArrayList<Double>(nSims)
        val depletionYears = ArrayList<Int>()

        val allRealWithdrawals = ArrayList<Double>(nSims * horizon)
        val maxYearlyDrops = ArrayList<Double>(nSims)

        val totalInflationFactor = inflationFactors[horizon]

        for (i in 0 until nSims) {
            var balance = initialBal
            var previousWithdrawalNominal = initialNeed
            var depletedYear: Int? = null
            var pathMaxDrop = 0.0
            var prevRealWithdrawal = initialNeed

            for (y in 0 until horizon) {
                val year = y + 1
                val ret = returnPaths[i][y]
                val inflationFactor = inflationFactors[y]

                if (balance <= 0.0) {
                    if (depletedYear == null) depletedYear = year
                    if (y > 0 && prevRealWithdrawal > 0.0) {
                        pathMaxDrop = 100.0
                    }
                    repeat(horizon - y) {
                        allRealWithdrawals.add(0.0)
                    }
                    break
                }

                val step = computeWithdrawalStep(
                    type = type,
                    year = year,
                    horizon = horizon,
                    balance = balance,
                    initialNeed = initialNeed,
                    initialSwr = initialSwrRate,
                    previousWithdrawalNominal = previousWithdrawalNominal,
                    inflationFactor = inflationFactor,
                    cpi = cpi,
                    gkUpperThresholdPct = config.gkUpperThresholdPct,
                    gkLowerThresholdPct = config.gkLowerThresholdPct,
                    gkCutPct = config.gkCutPct,
                    gkBoostPct = config.gkBoostPct,
                    gkFloorPctOfInitial = config.gkFloorPctOfInitial,
                    vpdRatePct = config.vpdRatePct,
                    vpdFloorPctOfInitial = config.vpdFloorPctOfInitial,
                    vpdCeilingPctOfInitial = config.vpdCeilingPctOfInitial
                )

                val actualWithdrawal = min(balance, step.withdrawalNominal)
                previousWithdrawalNominal = actualWithdrawal
                val realWithdrawal = actualWithdrawal / inflationFactor
                allRealWithdrawals.add(realWithdrawal)

                if (y > 0 && prevRealWithdrawal > 0.0) {
                    val dropPct = max(0.0, ((prevRealWithdrawal - realWithdrawal) / prevRealWithdrawal) * 100.0)
                    if (dropPct > pathMaxDrop) pathMaxDrop = dropPct
                }
                prevRealWithdrawal = realWithdrawal

                val postWithdrawalBal = balance - actualWithdrawal
                val endingBal = max(0.0, postWithdrawalBal * (1.0 + ret))
                balance = endingBal

                if (balance <= 0.001 && depletedYear == null) {
                    depletedYear = year
                }
            }

            if (balance > 0.001) {
                survivedCount++
            } else if (depletedYear != null) {
                depletionYears.add(depletedYear)
            }

            maxYearlyDrops.add(pathMaxDrop)
            endingBalancesNominal.add(balance)
            endingBalancesReal.add(balance / totalInflationFactor)
        }

        endingBalancesNominal.sort()
        endingBalancesReal.sort()

        val survivalRate = (survivedCount.toDouble() / nSims.toDouble()) * 100.0
        val medianEndNominal = percentile(endingBalancesNominal, 0.50)
        val medianEndReal = percentile(endingBalancesReal, 0.50)
        val p10EndReal = percentile(endingBalancesReal, 0.10)
        val p90EndReal = percentile(endingBalancesReal, 0.90)

        val avgRealIncome = if (allRealWithdrawals.isNotEmpty()) allRealWithdrawals.average() else 0.0
        val stdDevRealIncome = if (allRealWithdrawals.isNotEmpty()) {
            val mean = avgRealIncome
            val sumSq = allRealWithdrawals.sumOf { (it - mean).pow(2) }
            sqrt(sumSq / allRealWithdrawals.size)
        } else 0.0
        val cv = if (avgRealIncome > 0.0) stdDevRealIncome / avgRealIncome else 0.0
        val maxObservedDrop = if (maxYearlyDrops.isNotEmpty()) maxYearlyDrops.maxOrNull() ?: 0.0 else 0.0
        val avgDepletionYear = if (depletionYears.isNotEmpty()) depletionYears.average() else null

        val name = when (type) {
            DecumulationStrategyType.CONSTANT_REAL_SWR -> "Constant Real SWR (Bengen 4% + CPI)"
            DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS -> "Guyton-Klinger Guardrails (±20% Band / ±10% Adj.)"
            DecumulationStrategyType.VARIABLE_PERCENTAGE -> "Variable Percentage Decumulation (Floor/Ceiling Dynamic)"
        }

        return StrategyMetrics(
            strategyType = type,
            strategyName = name,
            survivalRatePct = survivalRate,
            medianEndingCapitalNominal = medianEndNominal,
            medianEndingCapitalReal = medianEndReal,
            p10EndingCapitalReal = p10EndReal,
            p90EndingCapitalReal = p90EndReal,
            averageAnnualRealIncome = avgRealIncome,
            incomeVolatilityCv = cv,
            maxAnnualIncomeDropPct = maxObservedDrop,
            averageDepletionYear = avgDepletionYear
        )
    }

    private fun percentile(sortedList: List<Double>, p: Double): Double {
        if (sortedList.isEmpty()) return 0.0
        val idx = ((sortedList.size - 1) * p).toInt().coerceIn(0, sortedList.size - 1)
        return sortedList[idx]
    }

    private fun nextGaussian(random: Random): Double {
        var u1 = random.nextDouble()
        while (u1 <= 1e-15) u1 = random.nextDouble()
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    private fun buildSummary(
        bengen: StrategyMetrics,
        gk: StrategyMetrics,
        vpd: StrategyMetrics,
        recommended: DecumulationStrategyType
    ): String {
        val recName = when (recommended) {
            DecumulationStrategyType.CONSTANT_REAL_SWR -> "Constant Real SWR (Bengen)"
            DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS -> "Guyton-Klinger Guardrails"
            DecumulationStrategyType.VARIABLE_PERCENTAGE -> "Variable Percentage Decumulation (VPD)"
        }
        return "Recommended strategy: $recName. " +
                "Guyton-Klinger achieves a ${String.format(java.util.Locale.US, "%.1f", gk.survivalRatePct)}% survival rate " +
                "compared to ${String.format(java.util.Locale.US, "%.1f", bengen.survivalRatePct)}% for Bengen and " +
                "${String.format(java.util.Locale.US, "%.1f", vpd.survivalRatePct)}% for VPD. " +
                "Median real ending balance under Guardrails is ${fmtExactCZK(gk.medianEndingCapitalReal)}."
    }
}

/**
 * Strategy types for retirement decumulation phase.
 */
enum class DecumulationStrategyType {
    CONSTANT_REAL_SWR,             // William Bengen classic 4% rule adjusted for Czech CPI
    GUYTON_KLINGER_GUARDRAILS,     // Dynamic capital preservation/prosperity rules
    VARIABLE_PERCENTAGE            // Dynamic % of portfolio with living floor and ceiling
}

/**
 * Configuration parameters for decumulation comparison.
 */
@Immutable
data class DecumulationConfig(
    val initialPortfolio: Double,
    val initialAnnualWithdrawal: Double = initialPortfolio * 0.04,
    val horizonYears: Int = 30,
    val annualInflationPct: Double = 2.8,
    val expectedAnnualReturnPct: Double = 7.0,
    val returnVolatilityPct: Double = 15.0,
    val simulationsCount: Int = 500,
    val randomSeed: Long = 42L,
    val gkUpperThresholdPct: Double = 20.0,
    val gkLowerThresholdPct: Double = 20.0,
    val gkCutPct: Double = 10.0,
    val gkBoostPct: Double = 10.0,
    val gkFloorPctOfInitial: Double = 70.0,
    val gkInitialWithdrawalMultiplier: Double = 1.0,
    val vpdRatePct: Double = 4.5,
    val vpdFloorPctOfInitial: Double = 70.0,
    val vpdCeilingPctOfInitial: Double = 130.0
)

/**
 * Year-by-year result along a single deterministic trajectory.
 */
data class SinglePathYearResult(
    val year: Int,
    val startingPortfolio: Double,
    val withdrawalNominal: Double,
    val withdrawalReal: Double,
    val marketReturnPct: Double,
    val endingPortfolio: Double,
    val isGuardrailCut: Boolean = false,
    val isGuardrailBoost: Boolean = false,
    val isDepleted: Boolean = false
)

/**
 * Aggregate metrics evaluating performance, survival, and income stability of a strategy.
 */
@Immutable
data class StrategyMetrics(
    val strategyType: DecumulationStrategyType,
    val strategyName: String,
    val survivalRatePct: Double,
    val medianEndingCapitalNominal: Double,
    val medianEndingCapitalReal: Double,
    val p10EndingCapitalReal: Double,
    val p90EndingCapitalReal: Double,
    val averageAnnualRealIncome: Double,
    val incomeVolatilityCv: Double,
    val maxAnnualIncomeDropPct: Double,
    val averageDepletionYear: Double?
)

/**
 * Comparison result across all 3 decumulation strategies.
 */
@Immutable
data class DecumulationComparisonResult(
    val config: DecumulationConfig,
    val strategies: Map<DecumulationStrategyType, StrategyMetrics>,
    val bengenMetrics: StrategyMetrics,
    val guytonKlingerMetrics: StrategyMetrics,
    val variablePercentageMetrics: StrategyMetrics,
    val recommendedStrategy: DecumulationStrategyType,
    val executiveSummary: String
)
