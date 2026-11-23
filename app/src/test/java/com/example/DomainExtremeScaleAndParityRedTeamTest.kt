package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechCapitalGainsEngine
import com.example.domain.CzechTaxEngine
import com.example.domain.DpsDipEngine
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.InvestmentLot
import com.example.domain.LotSaleOrder
import com.example.domain.RegulatoryConstants
import com.example.domain.TimeTestStatus
import com.example.util.BankStatementImporter
import com.example.util.FxRateService
import com.example.util.SubscriptionAuditor
import com.example.util.UserInvestmentLot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainExtremeScaleAndParityRedTeamTest {

    @After
    fun tearDown() {
        FxRateService.resetRates()
    }

    private fun assertAllFinite(state: FullCalculationState) {
        val s = state.snapshot
        assertTrue(s.netWorthTotal.isFinite())
        assertTrue(s.investedLiquidTotal.isFinite())
        assertTrue(s.pensionTotal.isFinite())
        assertTrue(s.emergencyCashTotal.isFinite())
        assertTrue(s.monthlyTakeHomeInflows.isFinite())
        assertTrue(s.monthlyEmployerMatch.isFinite())
        assertTrue(s.monthlyTotalInflowsWithMatch.isFinite())
        assertTrue(s.monthlyLivingCosts.isFinite())
        assertTrue(s.monthlyOwnInvesting.isFinite())
        assertTrue(s.monthlyTotalInvesting.isFinite())
        assertTrue(s.monthlyUnallocatedCash.isFinite())
        assertTrue(s.savingsRatePct.isFinite())
        assertTrue(s.emergencyRunwayMonths.isFinite())
        assertTrue(s.activeFireTargetToday.isFinite())

        state.dualTrajectory.forEach { pt ->
            assertTrue(pt.portfolio.isFinite())
            assertTrue(pt.pensionPortfolio.isFinite())
            assertTrue(pt.totalPortfolio.isFinite())
            assertTrue(pt.target.isFinite())
        }
        state.singleTrajectory.forEach { pt ->
            assertTrue(pt.totalPortfolio.isFinite())
            assertTrue(pt.target.isFinite())
        }
        state.monteCarlo.fanPoints.forEach { mc ->
            assertTrue(mc.p5.isFinite())
            assertTrue(mc.p50.isFinite())
            assertTrue(mc.p95.isFinite())
        }
    }

    @Test
    fun fiveThousandMixedCurrencyTransactions_endToEndPipelineUnder3000ms() {
        FxRateService.resetRates()
        FxRateService.updateRates(
            mapOf(
                "EUR" to 25.25,
                "USD" to 23.80,
                "GBP" to 30.10,
                "CHF" to 27.05,
                "PLN" to 5.95
            )
        )

        val currencies = listOf("CZK", "EUR", "USD", "GBP", "CHF", "PLN")
        val banks = listOf("CSOB", "MONETA", "REVOLUT", "FIO", "AIRBANK")
        val categories = listOf(
            "GROCERIES",
            "DINING_RESTAURANT",
            "SUBSCRIPTIONS_MEDIA",
            "HOUSING_RENT",
            "TRANSPORTATION"
        )

        val txs = ArrayList<ImportedBankTransactionEntity>(5000)
        for (i in 0 until 5000) {
            val month = (i % 12) + 1
            val year = if (i % 24 < 12) 2025 else 2026
            val ym = "%04d-%02d".format(year, month)
            val day = (i % 28) + 1
            val date = "%s-%02d".format(ym, day)
            val cur = currencies[i % currencies.size]
            val rawAmt = when {
                i % 50 == 0 -> 1500.0 // inflow credit
                i % 10 == 0 -> -19.99 // recurring subscription pattern
                else -> -(5.0 + (i % 180))
            }
            val czkAmt = FxRateService.convertToCzk(rawAmt, cur)
            val isSub = i % 10 == 0
            txs.add(
                ImportedBankTransactionEntity(
                    id = i.toLong() + 1L,
                    yearMonth = ym,
                    bankName = banks[i % banks.size],
                    date = date,
                    amount = czkAmt,
                    counterpartyName = if (isSub) "Streaming Plus $cur" else "Merchant ${(i % 40)}",
                    message = if (isSub) "Monthly recurring subscription" else "Card payment $cur",
                    category = if (isSub) "SUBSCRIPTIONS_MEDIA" else categories[i % categories.size],
                    isNetted = (i % 97 == 0)
                )
            )
        }

        // Also include a realistic mixed-currency CSV batch parse inside the timed run
        val csvRows = buildString {
            appendLine("Datum;Protiucet;Nazev protiuctu;Castka;Mena;Zprava")
            for (r in 0 until 250) {
                val cur = currencies[r % currencies.size]
                appendLine("15.10.2026;111222/0800;Revolut Merchant $r;-12.50;$cur;Recurring SaaS")
            }
        }
        val settings = SettingsEntity(baseYear = 2026, monteCarloN = 100)
        val ledger = (1..12).map { m ->
            LedgerEntryEntity(
                yearMonth = "2026-%02d".format(m),
                incVaclav = 45_000.0,
                expRent = 21_770.0,
                expGroceries = 4_800.0
            )
        }

        // Warm JIT once before timing the 5,000-tx workload
        FinancialEngine.calculate(settings, runMonteCarlo = false)

        lateinit var calculatedState: FullCalculationState
        var auditedCount = 0
        var parsedCsvCount = 0
        val elapsedMs = measureTimeMillis {
            parsedCsvCount = BankStatementImporter.parseStatement(
                csvRows.toByteArray(Charsets.UTF_8)
            ).transactions.size
            val audit = SubscriptionAuditor.auditSubscriptions(txs, swrPct = settings.safeWithdrawalRatePct)
            auditedCount = audit.items.size
            calculatedState = FinancialEngine.calculate(
                settings = settings,
                runMonteCarlo = true,
                ledgerEntries = ledger,
                importedTransactions = txs
            )
        }

        assertTrue("Expected timed 5,000-tx end-to-end pass under 3000ms, took ${elapsedMs}ms", elapsedMs < 3000L)
        assertTrue(parsedCsvCount >= 200)
        assertTrue(auditedCount >= 0)
        assertAllFinite(calculatedState)
    }

    @Test
    fun zdp16_zeroAndHundredMillionIncome_progressiveBrackets36xAvgWage() {
        assertEquals(0.0, CzechTaxEngine.netToGrossAnnual(0.0, 30_840.0), 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026, 36.0 * 48_967.0, 0.5)

        val savedHighBracket = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = 100_000_000.0,
            deductionAnnual = 48_000.0,
            thresholdHighBracket = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
            baseRate = 0.15,
            highRate = 0.23,
            basicTaxpayerCredit = 30_840.0
        )
        assertEquals(48_000.0 * 0.23, savedHighBracket, 0.01)

        val savedBaseBracket = CzechTaxEngine.singleEarnerRetirementTaxSaved(
            taxableGrossAnnual = 1_000_000.0,
            deductionAnnual = 48_000.0,
            thresholdHighBracket = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
            baseRate = 0.15,
            highRate = 0.23,
            basicTaxpayerCredit = 30_840.0
        )
        assertEquals(48_000.0 * 0.15, savedBaseBracket, 0.01)
    }

    @Test
    fun retirement48kCap_50kEmployerExemption_and_dpsSubsidyPensionAgeCutoff() {
        // 48 000 Kč combined DIP/DPS cap per earner (and 96 000 Kč across dual household)
        val overCappedSingle = SettingsEntity(
            isSingleHousehold = true,
            dipContributionMonthly = 10_000.0,
            dpsOwnContributionMonthly = 10_000.0
        )
        assertEquals(48_000.0, CzechTaxEngine.annualRetirementDeduction(overCappedSingle), 0.01)

        val overCappedDual = overCappedSingle.copy(
            isSingleHousehold = false,
            eDipContributionMonthly = 10_000.0,
            eDpsOwnContributionMonthly = 10_000.0
        )
        assertEquals(96_000.0, CzechTaxEngine.annualRetirementDeduction(overCappedDual), 0.01)

        // Employer § 6(9)(m) 50 000 Kč/year exemption cap (4 166.67 Kč/mo) over remaining lock-in years
        val existingContractAt59 = SettingsEntity(
            isSingleHousehold = true,
            primaryAge = 59,
            vStatePensionAge = 67,
            dpsBalanceCurrent = 800_000.0, // Existing >= 10y contract unlocks at age 60 (1 year left)
            dpsOwnContributionMonthly = 1_700.0,
            employerRetirementMonthly = 10_000.0
        )
        val dpsProj = DpsDipEngine.buildDpsProjection(existingContractAt59)
        // 1 year to age 60 -> capped annual employer match <= 50,000 Kč + tiny extra rounding
        assertTrue(dpsProj.employerTotal <= 50_000.5)
        assertTrue(dpsProj.employerTotal >= 49_999.5)

        // New contract opened at 59 with 0 balance enforces ZDP § 15b 120-month lock-in (unlockAge = 69)
        val newContractAt59 = existingContractAt59.copy(dpsBalanceCurrent = 0.0)
        val newDpsProj = DpsDipEngine.buildDpsProjection(newContractAt59)
        assertEquals(69, newDpsProj.unlockAge)
        assertTrue(newDpsProj.employerTotal <= 500_005.0)

        // DPS 340 Kč/mo state match & old-age pensioner cutoff
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(499.99, age = 35, statePensionAge = 67), 0.01)
        assertEquals(100.0, CzechTaxEngine.dpsSubsidy(500.0, age = 35, statePensionAge = 67), 0.01)
        assertEquals(340.0, CzechTaxEngine.dpsSubsidy(1_700.0, age = 35, statePensionAge = 67), 0.01)
        assertEquals(340.0, CzechTaxEngine.dpsSubsidy(5_000.0, age = 66, statePensionAge = 67), 0.01)
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(5_000.0, age = 67, statePensionAge = 67), 0.01)
    }

    @Test
    fun etfThreeYearTimeTest_exact1095And1096Days_and_100kProceedsBoundary() {
        // Non-leap 3-year span (2025-03-01 .. 2028-03-01 is strictly 3*365 = 1095 days? wait 2028 is leap -> Feb 29, 2028!)
        // Use 2021-05-01 -> 2024-05-01 (3y) vs 2024-05-02 (3y+1d), plus explicit 1095 vs 1096 day span 2025-03-01 -> 2028-02-29/2028-03-01/2028-03-02:
        val buy = LocalDate.of(2025, 3, 1)
        val exact3y = buy.plusYears(3) // 2028-03-01
        val after3y = buy.plusYears(3).plusDays(1) // 2028-03-02

        assertFalse("Exact 3-year date must NOT satisfy strictly > 3 years", CzechCapitalGainsEngine.determineTimeTestStatus(buy, exact3y) == TimeTestStatus.EXEMPT_TIME_TEST)
        assertEquals(TimeTestStatus.EXEMPT_TIME_TEST, CzechCapitalGainsEngine.determineTimeTestStatus(buy, after3y))

        val userLot = UserInvestmentLot(
            id = "u1",
            batchName = "VWCE",
            instrument = "VWCE",
            purchaseDateStr = "2025-03-01",
            acquisitionCostCzk = 50_000.0,
            currentValueCzk = 80_000.0
        )
        assertFalse(userLot.isExempt(exact3y))
        assertTrue(userLot.isExempt(after3y))

        // Securities 100 000 Kč gross proceeds threshold under ZDP § 4(1)(w)
        val taxableLot = InvestmentLot(
            id = "tax_1",
            ticker = "VWCE",
            purchaseDate = LocalDate.of(2026, 1, 1),
            units = 200_000.0,
            acquisitionPricePerUnitCzk = 0.5,
            currentPricePerUnitCzk = 1.0
        )
        val exemptLot = InvestmentLot(
            id = "ex_1",
            ticker = "IWDA",
            purchaseDate = LocalDate.of(2020, 1, 1),
            units = 5_000.0,
            acquisitionPricePerUnitCzk = 500.0,
            currentPricePerUnitCzk = 1_000.0 // 5M gross proceeds exempt under 3y time test
        )
        val asOf = LocalDate.of(2026, 11, 1)

        // Exactly 100 000 Kč gross -> exempt (0 tax)
        val res100k = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(taxableLot),
            saleOrders = listOf(LotSaleOrder("tax_1", 100_000.0)),
            asOfDate = asOf
        )
        assertTrue(res100k.qualifiesFor100kExemption)
        assertEquals(0.0, res100k.taxableGainCzk, 0.01)
        assertEquals(0.0, res100k.estimatedTaxLiability15Pct, 0.01)

        // 100 001 Kč gross -> NOT exempt under 100k cap -> taxable gain > 0
        val res100k1 = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(taxableLot),
            saleOrders = listOf(LotSaleOrder("tax_1", 100_001.0)),
            asOfDate = asOf
        )
        assertFalse(res100k1.qualifiesFor100kExemption)
        assertTrue(res100k1.taxableGainCzk > 0.0)
        assertTrue(res100k1.estimatedTaxLiability15Pct > 0.0)

        // 5 000 000 Kč time-test-exempt sale + 99 000 Kč non-exempt sale -> still qualifies for 100k exemption!
        val resCombined = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(exemptLot, taxableLot),
            saleOrders = listOf(
                LotSaleOrder("ex_1", 5_000.0),
                LotSaleOrder("tax_1", 99_000.0)
            ),
            asOfDate = asOf
        )
        assertTrue(resCombined.qualifiesFor100kExemption)
        assertEquals(0.0, resCombined.estimatedTaxLiability15Pct, 0.01)
    }

    @Test
    fun under3SpouseCredit_68kThreshold_and_parentalGifts100PctExempt() {
        val baseYear = 2026
        val withChildAge2 = SettingsEntity(
            baseYear = baseYear,
            isSingleHousehold = false,
            includeSpouseCredit = true,
            hasChildUnder3 = true,
            child1Enabled = true,
            child1BirthYear = baseYear - 2,
            eStartingSalary = 0.0,
            eLecturingMonthly = 0.0,
            eOtherInflowsMonthly = 0.0,
            vGiftsMonthly = 100_000.0,
            eGiftsMonthly = 100_000.0
        )

        // Direct-line parental/family gifts NEVER count toward 68 000 Kč spouse income limit
        assertEquals(0.0, CzechTaxEngine.spouseOwnIncomeAnnual(baseYear, withChildAge2), 0.01)
        assertEquals(24_840.0, CzechTaxEngine.eligibleSpouseCreditAnnual(withChildAge2, year = baseYear), 0.01)

        // Child age 3 loses under-3 spouse credit
        val withChildAge3 = withChildAge2.copy(child1BirthYear = baseYear - 3)
        assertEquals(0.0, CzechTaxEngine.eligibleSpouseCreditAnnual(withChildAge3, year = baseYear), 0.01)

        // 68 000 vs 68 001 Kč spouse own taxable income cutoff
        assertEquals(
            24_840.0,
            CzechTaxEngine.eligibleSpouseCreditAnnual(withChildAge2, year = baseYear, spouseOwnIncomeOverride = 68_000.0),
            0.01
        )
        assertEquals(
            0.0,
            CzechTaxEngine.eligibleSpouseCreditAnnual(withChildAge2, year = baseYear, spouseOwnIncomeOverride = 68_001.0),
            0.01
        )

        // Gifts flow 100% tax-free into household monthly take-home inflows
        val giftState = FinancialEngine.calculate(withChildAge2, runMonteCarlo = false)
        assertTrue(giftState.snapshot.monthlyTakeHomeInflows >= 200_000.0)
        assertAllFinite(giftState)
    }

    @Test
    fun extremeFireSettings_and_canonicalParityInvariantsHoldWithoutDrift() {
        val scenarios = listOf(
            // Negative savings rate, high rent
            SettingsEntity(vSalary = 25_000.0, rentMonthly = 120_000.0, cpiInflationPct = 0.0),
            // 15% inflation shock, retirement at current age
            SettingsEntity(primaryAge = 60, vStatePensionAge = 60, cpiInflationPct = 15.0, portfolioNominalReturnPct = 12.0),
            // Huge portfolio + 0 spending edge
            SettingsEntity(liquidPortfolioCurrent = 150_000_000.0, rentMonthly = 0.0, groceriesMonthly = 0.0, safeWithdrawalRatePct = 4.0)
        )

        for (s in scenarios) {
            val state = FinancialEngine.calculate(s, runMonteCarlo = false)
            assertAllFinite(state)
            val snap = state.snapshot
            assertEquals(
                snap.investedLiquidTotal + snap.pensionTotal + snap.emergencyCashTotal,
                snap.netWorthTotal,
                0.01
            )
            assertEquals(
                snap.monthlyTakeHomeInflows + snap.monthlyEmployerMatch,
                snap.monthlyTotalInflowsWithMatch,
                0.01
            )
            assertTrue(
                abs((snap.monthlyOwnInvesting + snap.monthlyEmployerMatch) - snap.monthlyTotalInvesting) < 0.01
            )
        }
    }
}
