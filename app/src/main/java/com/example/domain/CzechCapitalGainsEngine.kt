package com.example.domain

import com.example.util.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * CzechCapitalGainsEngine models Czech taxation of investment securities and ETFs under:
 * - Act No. 586/1992 Coll. on Income Taxes (ZDP):
 *   - § 4(1)(u) - 3-year holding time-test (strictly > 3 years) for securities & ETFs
 *   - § 4(1)(w) - 100,000 Kč annual total gross proceeds exemption limit (includes all securities sales per GFŘ D-59)
 *   - § 10 and § 16 - Capital gains tax base and progressive tax rates (15% / 23%)
 */
object CzechCapitalGainsEngine {

    const val STATUTORY_TIME_TEST_YEARS: Long = RegulatoryConstants.STATUTORY_SECURITIES_TIME_TEST_YEARS.toLong()
    const val STATUTORY_ANNUAL_GROSS_EXEMPTION_LIMIT_CZK: Double = RegulatoryConstants.STATUTORY_SECURITIES_ANNUAL_EXEMPTION_LIMIT_CZK // ZDP § 4(1)(w)
    const val STATUTORY_BASE_TAX_RATE_PCT: Double = RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT
    const val STATUTORY_HIGH_TAX_RATE_PCT: Double = RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT

    /**
     * Determines the statutory time-test status of an investment lot as of a specific date:
     * - EXEMPT_TIME_TEST: holding period strictly > 3 years (ZDP § 4(1)(u) "přesáhne dobu 3 let")
     * - APPROACHING_EXEMPTION: holding period <= 3 years, within 6 months of the 3-year mark
     * - TAXABLE: holding period <= 3 years and more than 6 months away from exemption
     */
    fun determineTimeTestStatus(purchaseDate: LocalDate, asOfDate: LocalDate): TimeTestStatus {
        val exemptionDate = purchaseDate.plusYears(STATUTORY_TIME_TEST_YEARS).plusDays(1)
        if (!asOfDate.isBefore(exemptionDate)) {
            return TimeTestStatus.EXEMPT_TIME_TEST
        }
        val sixMonthsBefore = exemptionDate.minusMonths(6)
        if (!asOfDate.isBefore(sixMonthsBefore)) {
            return TimeTestStatus.APPROACHING_EXEMPTION
        }
        return TimeTestStatus.TAXABLE
    }

    /**
     * Analyzes an entire portfolio of lots and aggregates status, values, gains, and exemption headroom.
     */
    fun analyzePortfolio(
        lots: List<InvestmentLot>,
        asOfDate: LocalDate = LocalDate.now(),
        priorNonExemptGrossSalesAnnualCzk: Double = 0.0
    ): CapitalGainsPortfolioSummary {
        var totalVal = 0.0
        var totalCost = 0.0
        var totalGain = 0.0

        var exemptVal = 0.0
        var exemptGain = 0.0
        var exemptLots = 0

        var approachingVal = 0.0
        var approachingGain = 0.0
        var approachingLots = 0

        var taxableVal = 0.0
        var taxableGain = 0.0
        var taxableLots = 0

        for (lot in lots) {
            val units = lot.units.finiteNonNegative()
            val curPrice = lot.currentPricePerUnitCzk.finiteNonNegative()
            val acqPrice = lot.acquisitionPricePerUnitCzk.finiteNonNegative()
            val curVal = units * curPrice
            val acqCost = units * acqPrice
            val gain = curVal - acqCost
            val status = determineTimeTestStatus(lot.purchaseDate, asOfDate)

            totalVal += curVal
            totalCost += acqCost
            totalGain += gain

            when (status) {
                TimeTestStatus.EXEMPT_TIME_TEST -> {
                    exemptVal += curVal
                    exemptGain += gain
                    exemptLots++
                }
                TimeTestStatus.APPROACHING_EXEMPTION -> {
                    approachingVal += curVal
                    approachingGain += gain
                    approachingLots++
                }
                TimeTestStatus.TAXABLE -> {
                    taxableVal += curVal
                    taxableGain += gain
                    taxableLots++
                }
            }
        }

        val remainingGrossHeadroom = max(
            0.0,
            STATUTORY_ANNUAL_GROSS_EXEMPTION_LIMIT_CZK - priorNonExemptGrossSalesAnnualCzk.finiteNonNegative()
        )

        return CapitalGainsPortfolioSummary(
            asOfDate = asOfDate.toString(),
            totalPortfolioValueCzk = totalVal,
            totalAcquisitionCostCzk = totalCost,
            totalUnrealizedGainCzk = totalGain,
            exemptValueCzk = exemptVal,
            exemptUnrealizedGainCzk = exemptGain,
            approachingExemptionValueCzk = approachingVal,
            approachingExemptionUnrealizedGainCzk = approachingGain,
            taxableValueCzk = taxableVal,
            taxableUnrealizedGainCzk = taxableGain,
            lotsCount = lots.size,
            exemptLotsCount = exemptLots,
            approachingLotsCount = approachingLots,
            taxableLotsCount = taxableLots,
            annualExemptionLimitRemainingCzk = remainingGrossHeadroom
        )
    }

    /**
     * Simulates sale of specified lots, applying Czech 3-year time-test and 100 000 CZK gross exemption limit.
     */
    fun simulateSale(
        lots: List<InvestmentLot>,
        saleOrders: List<LotSaleOrder>,
        asOfDate: LocalDate = LocalDate.now(),
        priorNonExemptGrossSalesAnnualCzk: Double = 0.0,
        taxRatePct: Double = STATUTORY_BASE_TAX_RATE_PCT
    ): SaleTaxSimulationResult {
        val lotMap = lots.associateBy { it.id }
        val remainingUnitsByLot = lots.associate { it.id to it.units.finiteNonNegative() }.toMutableMap()

        var totalGross = 0.0
        var exemptGross = 0.0
        var nonExemptGross = 0.0
        var nonExemptCost = 0.0
        var exemptGain = 0.0
        var potentialWaitingGain = 0.0

        for (order in saleOrders) {
            val lot = lotMap[order.lotId] ?: continue
            val availableUnits = remainingUnitsByLot[lot.id] ?: 0.0
            val unitsToSell = min(order.unitsToSell.finiteNonNegative(), availableUnits)
            if (unitsToSell <= 0.0) continue
            remainingUnitsByLot[lot.id] = availableUnits - unitsToSell

            val salePricePerUnit = lot.currentPricePerUnitCzk.finiteNonNegative()
            val costPricePerUnit = lot.acquisitionPricePerUnitCzk.finiteNonNegative()
            val grossRevenue = unitsToSell * salePricePerUnit
            val costBasis = unitsToSell * costPricePerUnit
            val gain = grossRevenue - costBasis
            val status = determineTimeTestStatus(lot.purchaseDate, asOfDate)

            totalGross += grossRevenue

            when (status) {
                TimeTestStatus.EXEMPT_TIME_TEST -> {
                    exemptGross += grossRevenue
                    exemptGain += gain
                }
                TimeTestStatus.APPROACHING_EXEMPTION -> {
                    nonExemptGross += grossRevenue
                    nonExemptCost += costBasis
                    potentialWaitingGain += max(0.0, gain)
                }
                TimeTestStatus.TAXABLE -> {
                    nonExemptGross += grossRevenue
                    nonExemptCost += costBasis
                }
            }
        }

        // Apply ZDP § 4(1)(w) sentence 2:
        // Proceeds already exempt under the 3-year time test are excluded from the 100,000 Kč annual gross proceeds limit.
        val totalNonExemptGrossProceeds = nonExemptGross + priorNonExemptGrossSalesAnnualCzk.finiteNonNegative()
        val qualifiesFor100k = totalNonExemptGrossProceeds <= STATUTORY_ANNUAL_GROSS_EXEMPTION_LIMIT_CZK

        val netTaxableGain = if (qualifiesFor100k) {
            0.0
        } else {
            max(0.0, nonExemptGross - nonExemptCost)
        }

        val rateBase = taxRatePct.finiteIn(0.0, 50.0, STATUTORY_BASE_TAX_RATE_PCT) / 100.0
        val taxLiability15 = netTaxableGain * (STATUTORY_BASE_TAX_RATE_PCT / 100.0)
        val taxLiability23 = netTaxableGain * (STATUTORY_HIGH_TAX_RATE_PCT / 100.0)
        val taxSavedByTimeTest = max(0.0, exemptGain) * rateBase
        // Waiting only saves tax when there is an actual taxable gain on approaching lots
        val effectiveWaitingGain = if (netTaxableGain > 0.0) min(potentialWaitingGain, netTaxableGain) else 0.0
        val taxSavedByWaiting = effectiveWaitingGain * rateBase

        val recommendation = when {
            netTaxableGain <= 0.0 && qualifiesFor100k && nonExemptGross > 0.0 ->
                "Sale is fully tax-exempt under the 3-year time test and annual 100\u00A0000\u00A0Kč total gross proceeds limit."
            netTaxableGain <= 0.0 && nonExemptGross == 0.0 ->
                "All sold positions exceed the 3-year holding test. Capital gains are 100% tax-exempt."
            netTaxableGain <= 0.0 ->
                "Sale generates no net taxable capital gain (losses offset gains). Tax liability is 0\u00A0Kč."
            taxSavedByWaiting > 0.0 ->
                "Notice: Selling lots close to maturity incurs ${Formatters.fmtExactCZK(taxLiability15)} (15%) tax. Postponing sale past the 3-year mark saves ${Formatters.fmtExactCZK(taxSavedByWaiting)}."
            else ->
                "Sale is subject to capital gains tax on ${Formatters.fmtExactCZK(netTaxableGain)} net gain: ${Formatters.fmtExactCZK(taxLiability15)} (15%) or ${Formatters.fmtExactCZK(taxLiability23)} (23%)."
        }

        return SaleTaxSimulationResult(
            totalGrossProceedsCzk = totalGross,
            exemptGrossProceedsCzk = exemptGross,
            nonExemptGrossProceedsCzk = nonExemptGross,
            qualifiesFor100kExemption = qualifiesFor100k,
            taxableGainCzk = netTaxableGain,
            estimatedTaxLiability15Pct = taxLiability15,
            estimatedTaxLiability23Pct = taxLiability23,
            taxSavedBy3yTimeTestCzk = taxSavedByTimeTest,
            potentialTaxSavingByWaitingCzk = taxSavedByWaiting,
            recommendation = recommendation
        )
    }

    /**
     * Recommends tax-optimal lots to sell to achieve a desired cash liquidation target:
     * 1. Sells lots already exempt under the 3-year time-test (0% tax drag).
     * 2. Sells lots with capital losses to offset any taxable gains.
     * 3. Sells non-exempt lots within the 100 000 CZK gross exemption ceiling.
     * 4. Avoids selling lots approaching exemption (within 6 months of 3 years).
     */
    fun recommendOptimalHarvest(
        lots: List<InvestmentLot>,
        targetCashCzk: Double,
        asOfDate: LocalDate = LocalDate.now(),
        priorNonExemptGrossSalesAnnualCzk: Double = 0.0,
        taxRatePct: Double = STATUTORY_BASE_TAX_RATE_PCT
    ): TaxOptimalHarvestRecommendation {
        val safeTarget = targetCashCzk.finiteNonNegative()
        val safePriorGross = priorNonExemptGrossSalesAnnualCzk.finiteNonNegative()
        if (safeTarget <= 0.0 || lots.isEmpty()) {
            return TaxOptimalHarvestRecommendation(
                targetCashCzk = safeTarget,
                totalCashRaisedCzk = 0.0,
                saleOrders = emptyList(),
                taxLiabilityCzk = 0.0,
                taxSavedCzk = 0.0,
                explanation = "Requested amount is zero or the portfolio contains no positions."
            )
        }

        var remainingCashNeeded = safeTarget
        val soldUnitsByLot = linkedMapOf<String, Double>()
        val remainingUnitsByLot = lots.associate { it.id to it.units.finiteNonNegative() }.toMutableMap()
        val lotStatusById = lots.associate { it.id to determineTimeTestStatus(it.purchaseDate, asOfDate) }
        var remaining100kLimit = max(0.0, STATUTORY_ANNUAL_GROSS_EXEMPTION_LIMIT_CZK - safePriorGross)

        fun takeFromLot(lot: InvestmentLot, maxCashCap: Double = Double.POSITIVE_INFINITY): Double {
            if (remainingCashNeeded <= 0.001) return 0.0
            val availUnits = remainingUnitsByLot[lot.id] ?: 0.0
            val price = lot.currentPricePerUnitCzk.finiteNonNegative()
            if (availUnits <= 1e-9 || price <= 0.0) return 0.0
            val availValue = availUnits * price
            val capCash = min(remainingCashNeeded, min(availValue, maxCashCap))
            if (capCash <= 0.0) return 0.0
            val units = min(availUnits, capCash / price)
            if (units <= 0.0) return 0.0
            val cashTaken = units * price
            remainingUnitsByLot[lot.id] = max(0.0, availUnits - units)
            soldUnitsByLot[lot.id] = (soldUnitsByLot[lot.id] ?: 0.0) + units
            remainingCashNeeded = max(0.0, remainingCashNeeded - cashTaken)
            // Under ZDP § 4(1)(w), only non-time-test-exempt proceeds consume the 100k annual gross threshold.
            if (lotStatusById[lot.id] != TimeTestStatus.EXEMPT_TIME_TEST) {
                remaining100kLimit = max(0.0, remaining100kLimit - cashTaken)
            }
            return cashTaken
        }

        fun gainRatio(lot: InvestmentLot): Double {
            val price = lot.currentPricePerUnitCzk.finiteNonNegative()
            val cost = lot.acquisitionPricePerUnitCzk.finiteNonNegative()
            return if (price > 0.0) (price - cost) / price else Double.MAX_VALUE
        }

        // Tier 1: Exempt lots (> 3 years) - ordered by largest unrealized gain to lock in tax-free appreciation
        val exemptLots = lots.filter { lotStatusById[it.id] == TimeTestStatus.EXEMPT_TIME_TEST }
            .sortedByDescending { it.units.finiteNonNegative() * (it.currentPricePerUnitCzk.finiteNonNegative() - it.acquisitionPricePerUnitCzk.finiteNonNegative()) }
        for (lot in exemptLots) {
            if (remainingCashNeeded <= 0.001) break
            takeFromLot(lot)
        }

        // If the entire remaining cash target fits inside the remaining 100,000 Kč annual gross cap,
        // prioritize TAXABLE gain lots first (highest gain ratio) to step up cost basis tax-free
        // rather than wasting the 100k exemption on loss lots.
        if (remainingCashNeeded > 0.001 && remainingCashNeeded <= remaining100kLimit + 0.001) {
            val taxableGainLots = lots.filter {
                lotStatusById[it.id] == TimeTestStatus.TAXABLE &&
                    it.currentPricePerUnitCzk.finiteNonNegative() >= it.acquisitionPricePerUnitCzk.finiteNonNegative() &&
                    (remainingUnitsByLot[it.id] ?: 0.0) > 1e-9
            }.sortedByDescending { gainRatio(it) }
            for (lot in taxableGainLots) {
                if (remainingCashNeeded <= 0.001 || remaining100kLimit <= 0.0) break
                takeFromLot(lot, maxCashCap = remaining100kLimit)
            }
        }

        // Tier 2: Non-exempt lots in loss to realize offsetting losses, preferring TAXABLE before APPROACHING
        if (remainingCashNeeded > 0.001) {
            val lossLots = lots.filter {
                val st = lotStatusById[it.id]
                val price = it.currentPricePerUnitCzk.finiteNonNegative()
                val cost = it.acquisitionPricePerUnitCzk.finiteNonNegative()
                st != TimeTestStatus.EXEMPT_TIME_TEST && price < cost && (remainingUnitsByLot[it.id] ?: 0.0) > 1e-9
            }.sortedWith(
                compareBy<InvestmentLot> { if (lotStatusById[it.id] == TimeTestStatus.TAXABLE) 0 else 1 }
                    .thenBy { it.units.finiteNonNegative() * (it.currentPricePerUnitCzk.finiteNonNegative() - it.acquisitionPricePerUnitCzk.finiteNonNegative()) }
            )
            for (lot in lossLots) {
                if (remainingCashNeeded <= 0.001) break
                takeFromLot(lot)
            }
        }

        // Tier 3: Remaining TAXABLE lots, sorted by lowest gain ratio
        if (remainingCashNeeded > 0.001) {
            val taxableLots = lots.filter {
                lotStatusById[it.id] == TimeTestStatus.TAXABLE &&
                    (remainingUnitsByLot[it.id] ?: 0.0) > 1e-9
            }.sortedBy { gainRatio(it) }
            for (lot in taxableLots) {
                if (remainingCashNeeded <= 0.001) break
                takeFromLot(lot)
            }
        }

        // Tier 4: Last resort - APPROACHING_EXEMPTION lots, sorted by lowest gain ratio
        if (remainingCashNeeded > 0.001) {
            val approachingLots = lots.filter {
                lotStatusById[it.id] == TimeTestStatus.APPROACHING_EXEMPTION &&
                    (remainingUnitsByLot[it.id] ?: 0.0) > 1e-9
            }.sortedBy { gainRatio(it) }
            for (lot in approachingLots) {
                if (remainingCashNeeded <= 0.001) break
                takeFromLot(lot)
            }
        }

        val orders = soldUnitsByLot.entries
            .filter { it.value > 0.0 }
            .map { LotSaleOrder(it.key, it.value) }
        val sim = simulateSale(lots, orders, asOfDate, safePriorGross, taxRatePct)
        val effectiveRate = taxRatePct.finiteIn(0.0, 50.0, STATUTORY_BASE_TAX_RATE_PCT) / 100.0

        return TaxOptimalHarvestRecommendation(
            targetCashCzk = safeTarget,
            totalCashRaisedCzk = sim.totalGrossProceedsCzk,
            saleOrders = orders,
            taxLiabilityCzk = sim.taxableGainCzk * effectiveRate,
            taxSavedCzk = sim.taxSavedBy3yTimeTestCzk,
            explanation = "Tax-minimization strategy: Prioritized 3-year time-test exempt positions, 100\u00A0000\u00A0Kč total annual gross exemption headroom, and offsetting loss lots."
        )
    }
}

/**
 * Statutory 3-year time-test status.
 */
enum class TimeTestStatus {
    EXEMPT_TIME_TEST,        // Held > 3 years (ZDP § 4(1)(u)) - 100% tax-free
    APPROACHING_EXEMPTION,   // Held <= 3 years but within 6 months of exemption
    TAXABLE                  // Held <= 3 years and > 6 months to exemption
}

/**
 * An investment lot for tax tracking.
 */
data class InvestmentLot(
    val id: String,
    val ticker: String,
    val purchaseDate: LocalDate,
    val units: Double,
    val acquisitionPricePerUnitCzk: Double,
    val currentPricePerUnitCzk: Double,
    val notes: String = ""
) {
    val totalAcquisitionCostCzk: Double get() = units.finiteNonNegative() * acquisitionPricePerUnitCzk.finiteNonNegative()
    val currentValueCzk: Double get() = units.finiteNonNegative() * currentPricePerUnitCzk.finiteNonNegative()

    val exemptionDate: LocalDate get() = purchaseDate.plusYears(CzechCapitalGainsEngine.STATUTORY_TIME_TEST_YEARS).plusDays(1)

    fun daysUntilExemption(asOfDate: LocalDate = LocalDate.now()): Long {
        if (!asOfDate.isBefore(exemptionDate)) return 0L
        return ChronoUnit.DAYS.between(asOfDate, exemptionDate)
    }

    fun timeTestStatus(asOfDate: LocalDate = LocalDate.now()): TimeTestStatus {
        return CzechCapitalGainsEngine.determineTimeTestStatus(purchaseDate, asOfDate)
    }

    companion object {
        fun fromIsoDate(
            id: String,
            ticker: String,
            purchaseDateIso: String,
            units: Double,
            acquisitionPricePerUnitCzk: Double,
            currentPricePerUnitCzk: Double,
            notes: String = ""
        ): InvestmentLot {
            val date = runCatching {
                LocalDate.parse(purchaseDateIso.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrElse { LocalDate.now() }
            return InvestmentLot(
                id = id,
                ticker = ticker,
                purchaseDate = date,
                units = units,
                acquisitionPricePerUnitCzk = acquisitionPricePerUnitCzk,
                currentPricePerUnitCzk = currentPricePerUnitCzk,
                notes = notes
            )
        }
    }
}

/**
 * Order to sell a specified number of units of an investment lot.
 */
data class LotSaleOrder(
    val lotId: String,
    val unitsToSell: Double
)

/**
 * Summary of a portfolio's capital gains and time-test distribution.
 */
data class CapitalGainsPortfolioSummary(
    val asOfDate: String,
    val totalPortfolioValueCzk: Double,
    val totalAcquisitionCostCzk: Double,
    val totalUnrealizedGainCzk: Double,
    val exemptValueCzk: Double,
    val exemptUnrealizedGainCzk: Double,
    val approachingExemptionValueCzk: Double,
    val approachingExemptionUnrealizedGainCzk: Double,
    val taxableValueCzk: Double,
    val taxableUnrealizedGainCzk: Double,
    val lotsCount: Int,
    val exemptLotsCount: Int,
    val approachingLotsCount: Int,
    val taxableLotsCount: Int,
    val annualExemptionLimitRemainingCzk: Double
)

/**
 * Result of simulating a sale of lots with tax implications.
 */
data class SaleTaxSimulationResult(
    val totalGrossProceedsCzk: Double,
    val exemptGrossProceedsCzk: Double,
    val nonExemptGrossProceedsCzk: Double,
    val qualifiesFor100kExemption: Boolean,
    val taxableGainCzk: Double,
    val estimatedTaxLiability15Pct: Double,
    val estimatedTaxLiability23Pct: Double,
    val taxSavedBy3yTimeTestCzk: Double,
    val potentialTaxSavingByWaitingCzk: Double,
    val recommendation: String
)

/**
 * Recommendation for an optimal liquidation order.
 */
data class TaxOptimalHarvestRecommendation(
    val targetCashCzk: Double,
    val totalCashRaisedCzk: Double,
    val saleOrders: List<LotSaleOrder>,
    val taxLiabilityCzk: Double,
    val taxSavedCzk: Double,
    val explanation: String
)
