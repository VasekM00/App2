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
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
        val sigma = settings.monteCarloVolatilityPct.finiteNonNegative() / 100.0
        val safeFactor = max(0.01, 1.0 + meanReturn)
        val sigmaLog = sqrt(ln(1.0 + (sigma / safeFactor).pow(2)).coerceAtLeast(0.0))
        val muLog = ln(safeFactor) - 0.5 * sigmaLog.pow(2)
        val random = Random(settings.monteCarloSeed)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = FireTargetAndTrajectoryEngine.fireTargetYear(baseYear, settings, baseAge)
        val includeSpouse = !settings.isSingleHousehold
        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(settings, includeSpouse, yearsCount = safeHorizon)
        val targets = DoubleArray(safeHorizon) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
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
        val taxDrag = FireTargetAndTrajectoryEngine.dividendTaxDragPct(settings)
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
        // Calibrate arithmetic mean to match parametric MC expected return (eliminates +41% geometric bias)
        val histArithFactor = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { max(0.01, 1.0 + (it - taxDrag) / 100.0) }.average()
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histArithFactor > 0.0 && histArithFactor.isFinite()) targetFactor / histArithFactor else 1.0
        val random = Random(settings.monteCarloSeed + 101L)

        val baseYear = settings.baseYear
        val baseAge = settings.primaryAge
        val initialTarget = FireTargetAndTrajectoryEngine.fireTargetYear(baseYear, settings, baseAge)
        val includeSpouse = !settings.isSingleHousehold
        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(settings, includeSpouse, yearsCount = safeHorizon)
        val targets = DoubleArray(safeHorizon) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
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
        val meanReturn = FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings) / 100.0
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
        val taxDrag = FireTargetAndTrajectoryEngine.dividendTaxDragPct(settings)
        val histArithFactor = HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT.map { max(0.01, 1.0 + (it - taxDrag) / 100.0) }.average()
        val targetFactor = 1.0 + meanReturn
        val scaleFactor = if (histArithFactor > 0.0 && histArithFactor.isFinite()) targetFactor / histArithFactor else 1.0
        val blockSize = 3
        val includeSpouse = !settings.isSingleHousehold

        val dpsFee = min(settings.dpsAnnualFeePct.finiteNonNegative(), settings.dpsStatutoryFeeCapPct.finiteNonNegative())
        val dpsRet = ((settings.dpsGrossReturnPct.finiteOr(0.0) - dpsFee) / 100.0).coerceAtLeast(-0.99)
        val dpsFactor = max(0.0, 1.0 + dpsRet)
        val dpsHalfRet = 1.0 + dpsRet * 0.5

        val lifestyleToday = FireTargetAndTrajectoryEngine.baselineLivingCostAtFireMonthly(settings)
        val vPensionToday = settings.vStatePensionMonthly.finiteNonNegative()
        val ePensionToday = if (includeSpouse) settings.eStatePensionMonthly.finiteNonNegative() else 0.0
        val eLiquid = if (includeSpouse) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (includeSpouse) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (includeSpouse) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0
        val initialLiquid = settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid
        val initialDip = settings.dipBalanceCurrent.finiteNonNegative() + eDip
        val initialDps = settings.dpsBalanceCurrent.finiteNonNegative() + eDps

        val preFirePlan = FireTargetAndTrajectoryEngine.buildPreFirePlan(settings, includeSpouse, yearsCount = maxAccumYears)
        val indexationTable = DoubleArray(totalMaxOffset + 1) { offset ->
            (1.0 + cpi).coerceAtLeast(0.0).pow(offset).finiteOr(1.0)
        }
        val targets = DoubleArray(maxAccumYears) { y ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + y + 1, settings, baseAge + y + 1)
        }
        val plannedTargetsByOffset = DoubleArray(totalMaxOffset + 1) { offset ->
            FireTargetAndTrajectoryEngine.fireTargetYear(baseYear + offset, settings, baseAge + offset).coerceAtLeast(1.0)
        }

        val livingSpec = HouseholdCashFlowEngine.LivingCostSpec.from(settings)
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

}
