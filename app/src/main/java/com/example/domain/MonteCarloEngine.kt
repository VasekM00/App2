package com.example.domain

import com.example.data.SettingsEntity
import com.example.domain.HouseholdCashFlowEngine.effectiveEmergencyReserveTarget
import com.example.domain.HouseholdCashFlowEngine.partnerBirthYear
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

object MonteCarloEngine {

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

    /**
     * Matched annual CPI inflation rates (%) from 1970 to 2025 (56 years), paired 1:1 with
     * [HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT] to preserve real stagflation / deflation correlations
     * (e.g. 1973-74 oil shock, 1979-81 inflation spike, 2009 deflation, 2022 inflation/rate shock).
     */
    val HISTORICAL_ANNUAL_INFLATION_PCT = doubleArrayOf(
         5.7,  // 1970
         4.4,  // 1971
         3.2,  // 1972
         6.2,  // 1973
        11.0,  // 1974
         9.1,  // 1975
         5.8,  // 1976
         6.5,  // 1977
         7.6,  // 1978
        11.3,  // 1979
        13.5,  // 1980
        10.3,  // 1981
         6.2,  // 1982
         3.2,  // 1983
         4.3,  // 1984
         3.6,  // 1985
         1.9,  // 1986
         3.6,  // 1987
         4.1,  // 1988
         4.8,  // 1989
         5.4,  // 1990
         4.2,  // 1991
         3.0,  // 1992
         3.0,  // 1993
         2.6,  // 1994
         2.8,  // 1995
         3.0,  // 1996
         2.3,  // 1997
         1.6,  // 1998
         2.2,  // 1999
         3.4,  // 2000
         2.8,  // 2001
         1.6,  // 2002
         2.3,  // 2003
         2.7,  // 2004
         3.4,  // 2005
         3.2,  // 2006
         2.8,  // 2007
         3.8,  // 2008
        -0.4,  // 2009
         1.6,  // 2010
         3.2,  // 2011
         2.1,  // 2012
         1.5,  // 2013
         1.6,  // 2014
         0.1,  // 2015
         1.3,  // 2016
         2.1,  // 2017
         2.4,  // 2018
         1.8,  // 2019
         1.2,  // 2020
         4.7,  // 2021
         8.0,  // 2022
         4.1,  // 2023
         2.9,  // 2024
         2.7   // 2025
    )

    private val HIST_MEAN_INFLATION_PCT: Double = HISTORICAL_ANNUAL_INFLATION_PCT.average()


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
    internal fun mcCacheNormalizedSettings(settings: SettingsEntity): SettingsEntity = settings.copy(
        primaryName = "",
        spouseName = "",
        child1Name = "",
        child2Name = "",
        merchantRulesJson = ""
    )

    private data class MonteCarloKey(
        val settings: SettingsEntity,
        val horizonYears: Int,
        val initialCrashPct: Double = 0.0,
        val partnerBirthYearOverride: Int? = null
    )

    private val mcCache = BoundedLruCache<MonteCarloKey, MonteCarloResult>(maxEntries = 16)

    fun runMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = FireTargetAndTrajectoryEngine.projectionHorizonYears(settings),
        initialCrashPct: Double = 0.0,
        partnerBirthYearOverride: Int? = null
    ): MonteCarloResult {
        val safeHorizon = horizonYears.coerceIn(1, 100)
        val safeCrashPct = initialCrashPct.finiteNonNegative()
        val currentKey = MonteCarloKey(
            settings = mcCacheNormalizedSettings(settings),
            horizonYears = safeHorizon,
            initialCrashPct = safeCrashPct,
            partnerBirthYearOverride = partnerBirthYearOverride
        )

        mcCache.get(currentKey)?.let { return it }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct.finiteNonNegative() / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = FireTargetAndTrajectoryEngine.fireTargetYear(baseYear, settings, baseAge, partnerBirthYearOverride)
        val includeSpouse = !settings.isSingleHousehold
        val dpsBaseNetRet = (DpsDipEngine.effectiveDpsNetReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val dpsBeta = ((settings.dpsGrossReturnPct.finiteOr(6.0) - 2.0) / 5.0).coerceIn(0.15, 0.90)

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(
            settings,
            includeSpouse,
            yearsCount = safeHorizon,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val targets = DoubleArray(safeHorizon) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1, partnerBirthYearOverride)
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
                val dpsYearRet = (dpsBaseNetRet + dpsBeta * (ret - meanReturn)).coerceAtLeast(-0.99)
                val dpsFactor = max(0.0, 1.0 + dpsYearRet)
                val dpsHalf = 1.0 + dpsYearRet * 0.5
                val liquidFlow = if (plan.liquidNetInflow >= 0.0) plan.liquidNetInflow * eqHalf else plan.liquidNetInflow
                liquidBal = max(0.0, liquidBal * eqFactor + liquidFlow)
                dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalf)
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
        val blockSize: Int = 3,
        val partnerBirthYearOverride: Int? = null
    )

    private val histMcCache = BoundedLruCache<HistoricalMonteCarloKey, MonteCarloResult>(maxEntries = 16)

    /**
     * Empirical Block-Bootstrap Monte Carlo simulation resampling from 1970-2025 equity history.
     * Preserves fat tails, crash magnitudes via additive mean shift, and coupled inflation regimes.
     */
    fun runHistoricalMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = FireTargetAndTrajectoryEngine.projectionHorizonYears(settings),
        initialCrashPct: Double = 0.0,
        blockSize: Int = 3,
        partnerBirthYearOverride: Int? = null
    ): MonteCarloResult {
        val safeHorizon = horizonYears.coerceIn(1, 100)
        val safeCrashPct = initialCrashPct.finiteNonNegative()
        val effectiveBlockSize = blockSize.coerceIn(1, 5)
        val currentKey = HistoricalMonteCarloKey(
            settings = mcCacheNormalizedSettings(settings),
            horizonYears = safeHorizon,
            initialCrashPct = safeCrashPct,
            blockSize = effectiveBlockSize,
            partnerBirthYearOverride = partnerBirthYearOverride
        )

        histMcCache.get(currentKey)?.let { return it }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val taxDrag = FireTargetAndTrajectoryEngine.dividendTaxDragPct(settings)
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
        // Additive mean shift preserves exact historical drawdown amplitudes (e.g. -37% in 2008)
        val histMeanNetReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { (it - taxDrag) / 100.0 }.average()
        val meanShift = meanReturn - histMeanNetReturn
        val random = Random(settings.monteCarloSeed + 101L)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = FireTargetAndTrajectoryEngine.fireTargetYear(baseYear, settings, baseAge, partnerBirthYearOverride)
        val includeSpouse = !settings.isSingleHousehold
        val dpsBaseNetRet = (DpsDipEngine.effectiveDpsNetReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val dpsBeta = ((settings.dpsGrossReturnPct.finiteOr(6.0) - 2.0) / 5.0).coerceIn(0.15, 0.90)

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(
            settings,
            includeSpouse,
            yearsCount = safeHorizon,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val targets = DoubleArray(safeHorizon) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1, partnerBirthYearOverride)
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
                        val netReturn = max(-0.99, (rawReturn - taxDrag) / 100.0 + meanShift)
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
                val dpsYearRet = (dpsBaseNetRet + dpsBeta * (ret - meanReturn)).coerceAtLeast(-0.99)
                val dpsFactor = max(0.0, 1.0 + dpsYearRet)
                val dpsHalf = 1.0 + dpsYearRet * 0.5
                val liquidFlow = if (plan.liquidNetInflow >= 0.0) plan.liquidNetInflow * eqHalf else plan.liquidNetInflow
                liquidBal = max(0.0, liquidBal * eqFactor + liquidFlow)
                dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalf)
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
        val useHistoricalBootstrap: Boolean,
        val partnerBirthYearOverride: Int? = null
    )

    private val parametricSurvivalCache = BoundedLruCache<SurvivalCacheKey, RetirementSurvivalResult>(maxEntries = 16)
    private val histSurvivalCache = BoundedLruCache<SurvivalCacheKey, RetirementSurvivalResult>(maxEntries = 16)

    /**
     * Simulates accumulation to FIRE (enforcing the pre-60 liquid bridge) and then a full
     * beginning-of-year withdrawal phase to the configured retirement horizon.
     * Incorporates dividend tax drag, stochastic DPS volatility, coupled historical inflation,
     * prolonged age-tiered child costs, Guyton-Klinger guardrails, and causal cash-cushion defense.
     */
    fun runRetirementSurvival(
        settings: SettingsEntity,
        useHistoricalBootstrap: Boolean = false,
        partnerBirthYearOverride: Int? = null
    ): RetirementSurvivalResult {
        val currentKey = SurvivalCacheKey(mcCacheNormalizedSettings(settings), useHistoricalBootstrap, partnerBirthYearOverride)
        if (useHistoricalBootstrap) {
            histSurvivalCache.get(currentKey)?.let { return it }
        } else {
            parametricSurvivalCache.get(currentKey)?.let { return it }
        }

        val sims = settings.monteCarloN.coerceIn(100, 2000)
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct.finiteNonNegative() / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed + (if (useHistoricalBootstrap) 202L else 1L))

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val partnerBirth = partnerBirthYear(settings, partnerBirthYearOverride)
        val cpi = (settings.cpiInflationPct.finiteOr(0.0) / 100.0).coerceAtLeast(-0.99)
        val maxAccumYears = FireTargetAndTrajectoryEngine.projectionHorizonYears(settings)
        val horizon = settings.retirementHorizonYears.coerceIn(10, 60)
        val totalMaxOffset = maxAccumYears + horizon + 5
        val nHistory = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.size
        val taxDrag = FireTargetAndTrajectoryEngine.dividendTaxDragPct(settings)
        val histMeanNetReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { (it - taxDrag) / 100.0 }.average()
        val meanShift = meanReturn - histMeanNetReturn
        val blockSize = 3
        val includeSpouse = !settings.isSingleHousehold

        val dpsBaseNetRet = (DpsDipEngine.effectiveDpsNetReturnPct(settings) / 100.0).coerceAtLeast(-0.99)
        val dpsBeta = ((settings.dpsGrossReturnPct.finiteOr(6.0) - 2.0) / 5.0).coerceIn(0.15, 0.90)

        val lifestyleToday = FireTargetAndTrajectoryEngine.baselineLivingCostAtFireMonthly(settings)
        val vPensionToday = settings.vStatePensionMonthly.finiteNonNegative()
        val ePensionToday = if (includeSpouse) settings.eStatePensionMonthly.finiteNonNegative() else 0.0
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        val initialLiquid = settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid
        val initialDip = settings.dipBalanceCurrent.finiteNonNegative() + eDip
        val initialDps = settings.dpsBalanceCurrent.finiteNonNegative() + eDps

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(
            settings,
            includeSpouse,
            yearsCount = maxAccumYears,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val indexationTable = DoubleArray(totalMaxOffset + 1) { offset ->
            (1.0 + cpi).coerceAtLeast(0.0).pow(offset).finiteOr(1.0)
        }
        val targets = DoubleArray(maxAccumYears) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1, partnerBirthYearOverride)
        }
        val plannedTargetsByOffset = DoubleArray(totalMaxOffset + 1) { offset ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + offset, settings, baseAge + offset, partnerBirthYearOverride).coerceAtLeast(1.0)
        }

        val rReal = FireTargetAndTrajectoryEngine.netRealReturnRate(settings)
        val livingSpec = HouseholdCashFlowEngine.LivingCostSpec.from(settings)
        val hasLifestyleOverride = settings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0
        val rentShareInLiving = if (!hasLifestyleOverride && lifestyleToday > 0.0) {
            (livingSpec.baseRentMonthly / lifestyleToday).coerceIn(0.0, 1.0)
        } else 0.0
        val rentGrowthRate = (livingSpec.rentGrowthPct / 100.0).coerceAtLeast(-0.99)
        val rentSpread = rentGrowthRate - cpi
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
        val bridgeReq0 = FireTargetAndTrajectoryEngine.computeBridgeTo60Required(
            annualLiving = lifestyleToday * 12.0,
            yearsTo60 = max(0, 60 - baseAge),
            rReal = rReal,
            totalFireTarget = initialTarget0
        )
        val bridgeRequiredByYear = DoubleArray(maxAccumYears) { y ->
            val ageAtY = baseAge + y + 1
            val livingForBridge = if (hasLifestyleOverride) {
                lifestyleToday * 12.0 * preFirePlan[y].inflationFactor
            } else {
                preFirePlan[y].annualLivingAtTargetYear
            }
            FireTargetAndTrajectoryEngine.computeBridgeTo60Required(
                annualLiving = livingForBridge,
                yearsTo60 = max(0, 60 - ageAtY),
                rReal = rReal,
                totalFireTarget = targets[y]
            )
        }
        val reserveTargetAnnual = effectiveEmergencyReserveTarget(settings).finiteNonNegative()
        val initialCashCushion = settings.emergencyReserveCurrent.finiteNonNegative()
        val parsedLumpSums = parseCustomLumpSums(settings.customLumpSumsJson)
        val upperThreshold = 1.0 + (settings.guardrailsUpperThresholdPct.finiteNonNegative() / 100.0)
        val lowerThreshold = (1.0 - (settings.guardrailsLowerThresholdPct.finiteNonNegative() / 100.0)).coerceAtLeast(0.10)
        val cutMultiplier = (1.0 - (settings.guardrailsCutPct.finiteNonNegative() / 100.0)).coerceIn(0.50, 0.99)
        val boostMultiplier = (1.0 + (settings.guardrailsBoostPct.finiteNonNegative() / 100.0)).coerceIn(1.01, 1.50)
        val floorMultiplier = (settings.guardrailsFloorPct.finiteNonNegative() / 100.0).coerceIn(0.30, 0.95)
        val targetRetAge = settings.targetRetirementAge
        val canRetireAtYear0 = (targetRetAge <= 0 || baseAge >= targetRetAge || baseAge >= settings.vStatePensionAge) &&
            (initialLiquid + initialDip + initialDps) >= initialTarget0 &&
            (baseAge >= 60 || initialLiquid >= bridgeReq0)

        var fireReached = 0
        var survived = 0
        var pathsWithGuardrailsTriggered = 0
        val endBalancesToday = ArrayList<Double>(sims)
        val depletionAges = ArrayList<Int>()

        for (i in 0 until sims) {
            val pathReturns = DoubleArray(totalMaxOffset)
            val pathCpis = DoubleArray(totalMaxOffset) { cpi }
            if (useHistoricalBootstrap) {
                var yOffset = 0
                while (yOffset < totalMaxOffset) {
                    val startIdx = random.nextInt(nHistory)
                    for (b in 0 until blockSize) {
                        if (yOffset + b < totalMaxOffset) {
                            val histIdx = (startIdx + b) % nHistory
                            val rawReturn = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT[histIdx]
                            val netReturn = max(-0.99, (rawReturn - taxDrag) / 100.0 + meanShift)
                            pathReturns[yOffset + b] = netReturn
                            val cpiOffset = (HISTORICAL_ANNUAL_INFLATION_PCT[histIdx] - HIST_MEAN_INFLATION_PCT) / 100.0
                            pathCpis[yOffset + b] = (cpi + cpiOffset).coerceAtLeast(-0.05)
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
            var prevYearRet = 0.0

            if (fireYear < 0) {
                for (y in 0 until maxAccumYears) {
                    val plan = preFirePlan[y]
                    val ageAtEnd = baseAge + y + 1
                    val ret = pathReturns[y]
                    prevYearRet = ret
                    val eqFactor = max(0.0, 1.0 + ret)
                    val eqHalf = 1.0 + ret * 0.5
                    val dpsYearRet = (dpsBaseNetRet + dpsBeta * (ret - meanReturn)).coerceAtLeast(-0.99)
                    val dpsFactor = max(0.0, 1.0 + dpsYearRet)
                    val dpsHalf = 1.0 + dpsYearRet * 0.5
                    val liquidFlow = if (plan.liquidNetInflow >= 0.0) plan.liquidNetInflow * eqHalf else plan.liquidNetInflow
                    liquidBal = max(0.0, liquidBal * eqFactor + liquidFlow)
                    dipBal = max(0.0, dipBal * eqFactor + plan.dipInflowAnnual * eqHalf)
                    dpsBal = max(0.0, dpsBal * dpsFactor + plan.dpsInflowAnnual * dpsHalf)
                    val totalNow = liquidBal + dipBal + dpsBal
                    val bridgeOk = ageAtEnd >= 60 || liquidBal >= bridgeRequiredByYear[y]
                    val targetAgeOk = targetRetAge <= 0 || ageAtEnd >= targetRetAge || ageAtEnd >= settings.vStatePensionAge
                    if (totalNow >= targets[y] && bridgeOk && targetAgeOk) {
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
            var pathCpiFactor = indexationTable[fireOffset]
            var pathRentFactor = if (rentShareInLiving > 0.0) {
                (1.0 + rentGrowthRate).coerceAtLeast(0.0).pow(fireOffset).finiteOr(1.0)
            } else pathCpiFactor
            var cashCushion = initialCashCushion * pathCpiFactor
            var bal = liquidBal + dipBal + dpsBal + cashCushion

            for (k in 0 until horizon) {
                val yearOffset = (fireYear - baseYear) + k
                val calYear = baseYear + yearOffset
                val age = baseAge + yearOffset
                val eAge = calYear - partnerBirth

                // Statutory Czech lock-in guard (Act No. 586/1992 Coll. § 15a & Act No. 427/2011 Coll. § 94):
                // Pension assets (DIP/DPS) unlock into the liquid spending pool at age 60.
                if (age >= 60 && (dipBal > 0.0 || dpsBal > 0.0)) {
                    liquidBal += dipBal + dpsBal
                    dipBal = 0.0
                    dpsBal = 0.0
                }

                val safeOffset = yearOffset.coerceIn(0, totalMaxOffset)
                val stepCpi = if (useHistoricalBootstrap) pathCpis.getOrElse(yearOffset) { cpi } else cpi
                val stepRentRate = if (useHistoricalBootstrap && rentShareInLiving > 0.0) {
                    (stepCpi + rentSpread).coerceAtLeast(-0.05)
                } else rentGrowthRate
                val indexation = if (useHistoricalBootstrap) pathCpiFactor else indexationTable[safeOffset]
                val livingIndexation = if (useHistoricalBootstrap) {
                    if (rentShareInLiving > 0.0) {
                        rentShareInLiving * pathRentFactor + (1.0 - rentShareInLiving) * pathCpiFactor
                    } else pathCpiFactor
                } else livingIndexationTable[safeOffset]

                val childAnnualToday = if (settings.childExpensesEnabled && settings.prolongChildSupportInFire) {
                    val c1 = if (settings.child1Enabled) {
                        HouseholdCashFlowEngine.childMonthlyExpense(settings.child1BirthYear, calYear, settings)
                    } else 0.0
                    val c2 = if (settings.child2Enabled) {
                        HouseholdCashFlowEngine.childMonthlyExpense(settings.child2BirthYear, calYear, settings)
                    } else 0.0
                    (c1 + c2) * 12.0
                } else 0.0
                val childAnnualNominal = childAnnualToday * indexation

                var effectiveLifestyle = lifestyleToday * 12.0 * livingIndexation * spendingScale + childAnnualNominal
                var pensionAnnual = 0.0
                if (age >= settings.vStatePensionAge) pensionAnnual += vPensionToday * 12.0 * indexation
                if (includeSpouse && eAge >= settings.eStatePensionAge) {
                    pensionAnnual += ePensionToday * 12.0 * indexation
                }
                val lumpAnnual = lumpSumForYear(calYear, settings, parsedLumpSums)
                val baristaAnnual = FireTargetAndTrajectoryEngine.postFireBaristaNetAnnualForYear(
                    calYear = calYear,
                    settings = settings,
                    inflationFactor = indexation
                )
                val recurringIncome = pensionAnnual + baristaAnnual
                // Guardrail withdrawal rate excludes one-off scheduled lump sums so a single windfall
                // cannot trigger a permanent Prosperity spending ratchet.
                var guardrailNeed = max(0.0, effectiveLifestyle - recurringIncome)

                val currentTotalBal = liquidBal + dipBal + dpsBal + cashCushion
                val baseNeedAtAge = max(0.0, lifestyleToday * 12.0 * livingIndexation + childAnnualNominal - recurringIncome)
                if (settings.guardrailsEnabled && currentTotalBal > 0.0 && baseNeedAtAge > 0.0) {
                    val plannedTargetAtAge = plannedTargetsByOffset[safeOffset]
                    val referenceSwr = (baseNeedAtAge / plannedTargetAtAge).coerceAtLeast(0.001)
                    val currentWithdrawalRate = guardrailNeed / currentTotalBal

                    val isSunsetPhase = horizon > 15 && k >= horizon - 15
                    if (!isSunsetPhase && currentWithdrawalRate > referenceSwr * upperThreshold) {
                        spendingScale = (spendingScale * cutMultiplier).coerceAtLeast(floorMultiplier)
                        effectiveLifestyle = lifestyleToday * 12.0 * livingIndexation * spendingScale + childAnnualNominal
                        guardrailNeed = max(0.0, effectiveLifestyle - recurringIncome)
                        guardrailTriggeredInPath = true
                    } else if (currentWithdrawalRate < referenceSwr * lowerThreshold) {
                        spendingScale = min(1.30, spendingScale * boostMultiplier)
                        effectiveLifestyle = lifestyleToday * 12.0 * livingIndexation * spendingScale + childAnnualNominal
                        guardrailNeed = max(0.0, effectiveLifestyle - recurringIncome)
                    }
                }

                val signedNetFlow = lumpAnnual + recurringIncome - effectiveLifestyle
                var need = max(0.0, -signedNetFlow)
                val postFireSurplus = max(0.0, signedNetFlow)

                // 1. Causal beginning-of-year cash-cushion defense based strictly on PRIOR year's realized return
                val indexedReserveTarget = reserveTargetAnnual * indexation
                if (k > 0 && prevYearRet < 0.0 && cashCushion > 0.0 && need > 0.0) {
                    val drawFromCash = min(cashCushion, need)
                    cashCushion -= drawFromCash
                    need -= drawFromCash
                } else if (k > 0 && prevYearRet > 0.05 && cashCushion < indexedReserveTarget) {
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

                // 2. Beginning-of-year living withdrawal before equity compounding
                if (need > 0.0) {
                    if (age < 60) {
                        if (liquidBal < need) {
                            val deficit = need - liquidBal
                            if (cashCushion + 0.001 >= deficit) {
                                cashCushion = max(0.0, cashCushion - deficit)
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
                    } else {
                        var available = liquidBal + dipBal + dpsBal
                        if (available < need) {
                            val deficit = need - available
                            if (cashCushion + 0.001 >= deficit) {
                                cashCushion = max(0.0, cashCushion - deficit)
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
                    }
                }

                // 3. Compound remaining balances over year `k` and invest any post-FIRE lump-sum / Barista surplus
                val ret = pathReturns.getOrNull(yearOffset) ?: (exp(muLog + sigmaLog * nextGaussian(random)) - 1.0)
                val eqFactor = max(0.0, 1.0 + ret)
                val dpsYearRet = (dpsBaseNetRet + dpsBeta * (ret - meanReturn)).coerceAtLeast(-0.99)
                val dpsFactor = max(0.0, 1.0 + dpsYearRet)
                liquidBal = max(0.0, liquidBal * eqFactor + postFireSurplus * (1.0 + ret * 0.5))
                dipBal *= eqFactor
                dpsBal *= dpsFactor
                cashCushion *= (1.0 + stepCpi).coerceAtLeast(0.0)
                bal = liquidBal + dipBal + dpsBal + cashCushion

                prevYearRet = ret
                pathCpiFactor *= (1.0 + stepCpi).coerceAtLeast(0.0)
                pathRentFactor *= (1.0 + stepRentRate).coerceAtLeast(0.0)
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
            val deflator = if (useHistoricalBootstrap) pathCpiFactor else indexationTable[totalYearsFromBase.coerceIn(0, totalMaxOffset)]
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
            guardrailTriggeredPct = guardrailTriggeredPct,
            fireReachedCount = fireReached
        )
        if (useHistoricalBootstrap) {
            histSurvivalCache.put(currentKey, result)
        } else {
            parametricSurvivalCache.put(currentKey, result)
        }
        return result
    }

}
