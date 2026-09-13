package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.util.BankTransactionType
import com.example.util.BackupManager
import com.example.util.CzechMerchantCatalog
import com.example.util.MerchantCategoryManager
import com.example.util.SubscriptionAuditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChildCostAndStatementIntelligenceTest {

    @Test
    fun testChildCost_baselineAnchoring_avoidsDoubleCountingInBaseYear() {
        val baseSettings = SettingsEntity(
            baseYear = 2026,
            rentMonthly = 20000.0,
            groceriesMonthly = 15000.0,
            otherDiscretionaryMonthly = 5000.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            customExpensesJson = "[]",
            deletedCategoriesJson = "[]",
            childExpensesEnabled = true,
            currentChildCostsInBaseline = true,
            child1Enabled = true,
            child1BirthYear = 2024, // Age 2 in 2026 (toddler)
            childToddlerMonthly = 4800.0,
            childPreschoolMonthly = 6500.0,
            childSchoolMonthly = 8500.0,
            childTeenMonthly = 13000.0,
            childUniMonthly = 10000.0,
            child2Enabled = false
        )

        val manualBaseline = 20000.0 + 15000.0 + 5000.0 // 40,000 Kč

        // Base year (2026): Child 1 is toddler (embedded in groceries/baby expenses) -> addition is 0.0
        val costBaseYear = FinancialEngine.totalLivingCostMonthly(baseSettings, 2026)
        assertEquals(
            "Base year living cost must not double count embedded toddler expenses",
            manualBaseline,
            costBaseYear,
            0.001
        )

        // Year 2027: Child 1 turns 3 (Preschool). Delta = 6,500 - 4,800 = +1,700 Kč
        val cost2027 = FinancialEngine.totalLivingCostMonthly(baseSettings, 2027)
        assertEquals(
            "Preschool year must only add incremental delta (+1,700 Kč)",
            manualBaseline + 1700.0,
            cost2027,
            0.001
        )

        // Year 2030: Child 1 turns 6 (School). Delta = 8,500 - 4,800 = +3,700 Kč
        val cost2030 = FinancialEngine.totalLivingCostMonthly(baseSettings, 2030)
        assertEquals(
            "School year must only add incremental delta (+3,700 Kč)",
            manualBaseline + 3700.0,
            cost2030,
            0.001
        )

        // Year 2039: Child 1 turns 15 (Teen). Delta = 13,000 - 4,800 = +8,200 Kč
        val cost2039 = FinancialEngine.totalLivingCostMonthly(baseSettings, 2039)
        assertEquals(
            "Teen year must only add incremental delta (+8,200 Kč)",
            manualBaseline + 8200.0,
            cost2039,
            0.001
        )

        // Year 2050: Child 1 turns 26 (Adult). Delta = 0 - 4,800 = -4,800 Kč (child leaves home)
        val cost2050 = FinancialEngine.totalLivingCostMonthly(baseSettings, 2050)
        assertEquals(
            "Adulthood removes toddler baseline expense from budget (-4,800 Kč)",
            manualBaseline - 4800.0,
            cost2050,
            0.001
        )
    }

    @Test
    fun testChildCost_futureChild_addsFullCostFromBirthYear() {
        val settings = SettingsEntity(
            baseYear = 2026,
            rentMonthly = 20000.0,
            groceriesMonthly = 10000.0,
            otherDiscretionaryMonthly = 0.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            customExpensesJson = "[]",
            deletedCategoriesJson = "[]",
            childExpensesEnabled = true,
            currentChildCostsInBaseline = true,
            child1Enabled = false,
            child2Enabled = true,
            child2BirthYear = 2028, // Future child born 2 years later
            childToddlerMonthly = 4800.0,
            childPreschoolMonthly = 6500.0
        )

        val manualBaseline = 30000.0

        // In 2026 & 2027, child 2 is not born yet
        assertEquals(manualBaseline, FinancialEngine.totalLivingCostMonthly(settings, 2026), 0.001)
        assertEquals(manualBaseline, FinancialEngine.totalLivingCostMonthly(settings, 2027), 0.001)

        // In 2028, child 2 is born: full toddler cost (4,800 Kč) is added because not present in base year
        assertEquals(manualBaseline + 4800.0, FinancialEngine.totalLivingCostMonthly(settings, 2028), 0.001)

        // In 2031, child 2 turns 3 (Preschool): full preschool cost (6,500 Kč) is added
        assertEquals(manualBaseline + 6500.0, FinancialEngine.totalLivingCostMonthly(settings, 2031), 0.001)
    }

    @Test
    fun testChildCost_legacyToggleOff_addsFullCostImmediately() {
        val settings = SettingsEntity(
            baseYear = 2026,
            rentMonthly = 20000.0,
            groceriesMonthly = 10000.0,
            otherDiscretionaryMonthly = 0.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            customExpensesJson = "[]",
            deletedCategoriesJson = "[]",
            childExpensesEnabled = true,
            currentChildCostsInBaseline = false, // Disabled anchor
            child1Enabled = true,
            child1BirthYear = 2024,
            childToddlerMonthly = 4800.0
        )

        // When toggled off, legacy full cost is added in base year
        assertEquals(34800.0, FinancialEngine.totalLivingCostMonthly(settings, 2026), 0.001)
    }

    @Test
    fun testFreedomDays_calculationAndGuards() {
        val monthlyCost = 30420.0
        val monthlySavings = 30420.0

        // Daily burn = 30420 / 30.42 = 1000 Kč/day
        // Savings = 30420 Kč -> 30.42 days of freedom bought
        val daysBought = FinancialEngine.freedomDaysBoughtMonthly(monthlySavings, monthlyCost)
        assertEquals(30.42, daysBought, 0.01)

        // Portfolio = 365,040 Kč -> 365.04 days of runway
        val daysCovered = FinancialEngine.freedomDaysCoveredByPortfolio(365040.0, monthlyCost)
        assertEquals(365.04, daysCovered, 0.01)

        // Zero / negative guards
        assertEquals(0.0, FinancialEngine.freedomDaysBoughtMonthly(0.0, monthlyCost), 0.001)
        assertEquals(0.0, FinancialEngine.freedomDaysBoughtMonthly(monthlySavings, 0.0), 0.001)
        assertEquals(0.0, FinancialEngine.freedomDaysCoveredByPortfolio(0.0, monthlyCost), 0.001)
        assertEquals(0.0, FinancialEngine.freedomDaysCoveredByPortfolio(365040.0, 0.0), 0.001)
    }

    @Test
    fun testSubscriptionAuditor_detectsRecurringAndPriceCreep() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 1L,
                yearMonth = "2026-07",
                bankName = "MONETA",
                date = "2026-07-15",
                amount = -259.0,
                counterpartyName = "NETFLIX.COM",
                category = "SUBSCRIPTIONS_MEDIA"
            ),
            ImportedBankTransactionEntity(
                id = 2L,
                yearMonth = "2026-08",
                bankName = "MONETA",
                date = "2026-08-15",
                amount = -319.0, // Price creep +60 Kč
                counterpartyName = "NETFLIX.COM",
                category = "SUBSCRIPTIONS_MEDIA"
            ),
            ImportedBankTransactionEntity(
                id = 3L,
                yearMonth = "2026-07",
                bankName = "CSOB",
                date = "2026-07-01",
                amount = -500.0,
                counterpartyName = "VODAFONE",
                category = "SERVICES_UTILITIES"
            ),
            ImportedBankTransactionEntity(
                id = 4L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-01",
                amount = -500.0,
                counterpartyName = "VODAFONE",
                category = "SERVICES_UTILITIES"
            ),
            ImportedBankTransactionEntity(
                id = 5L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-10",
                amount = -1500.0,
                counterpartyName = "ALZA.CZ", // One-time shopping, not a subscription
                category = "SHOPPING_GOODS"
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)

        assertEquals(2, summary.items.size)
        val netflix = summary.items.find { it.merchantKey.contains("netflix") }
        assertNotNull(netflix)
        assertEquals(319.0, netflix!!.latestMonthlyAmount, 0.001)
        assertEquals(259.0, netflix.previousMonthlyAmount ?: 0.0, 0.001)
        assertEquals(60.0, netflix.priceCreepDelta, 0.001)
        assertTrue(netflix.priceCreepPct > 20.0)

        val vodafone = summary.items.find { it.merchantKey.contains("vodafone") }
        assertNotNull(vodafone)
        assertEquals(500.0, vodafone!!.latestMonthlyAmount, 0.001)
        assertEquals(0.0, vodafone.priceCreepDelta, 0.001)

        assertEquals(1, summary.priceCreepCount)
        assertEquals(819.0, summary.totalMonthlyBurn, 0.001) // 319 + 500
        assertEquals(819.0 * 12.0, summary.annualizedBurn, 0.001)

        // FIRE capital at 4% SWR: (819 * 12) / 0.04 = 245,700 Kč
        assertEquals((819.0 * 12.0) / 0.04, summary.fireCapitalRequired, 0.01)
    }

    @Test
    fun testMerchantCategoryManager_rulesSerializationRoundTrip() {
        val rules = mapOf(
            "lidl" to BankTransactionType.GROCERIES,
            "netflix" to BankTransactionType.SUBSCRIPTIONS_MEDIA,
            "alza" to BankTransactionType.SHOPPING_GOODS
        )

        val json = MerchantCategoryManager.serializeRulesToJson(rules)
        val restored = MerchantCategoryManager.parseRulesFromJson(json)

        assertEquals(3, restored.size)
        assertEquals(BankTransactionType.GROCERIES, restored["lidl"])
        assertEquals(BankTransactionType.SUBSCRIPTIONS_MEDIA, restored["netflix"])
        assertEquals(BankTransactionType.SHOPPING_GOODS, restored["alza"])

        // Corrupted JSON returns empty map safely
        val emptyResult = MerchantCategoryManager.parseRulesFromJson("invalid_json")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun testSubscriptionAuditor_groceriesAndDiningExcluded() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 101L,
                yearMonth = "2026-06",
                bankName = "CSOB",
                date = "2026-06-05",
                amount = -1500.0,
                counterpartyName = "LIDL CESKA REPUBLIKA",
                category = "GROCERIES"
            ),
            ImportedBankTransactionEntity(
                id = 102L,
                yearMonth = "2026-07",
                bankName = "CSOB",
                date = "2026-07-05",
                amount = -1500.0,
                counterpartyName = "LIDL CESKA REPUBLIKA",
                category = "GROCERIES"
            ),
            ImportedBankTransactionEntity(
                id = 103L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-05",
                amount = -1500.0,
                counterpartyName = "LIDL CESKA REPUBLIKA",
                category = "GROCERIES"
            ),
            ImportedBankTransactionEntity(
                id = 104L,
                yearMonth = "2026-07",
                bankName = "MONETA",
                date = "2026-07-10",
                amount = -650.0,
                counterpartyName = "RESTAURACE KOLYBA",
                category = "RESTAURANTS_DINING"
            ),
            ImportedBankTransactionEntity(
                id = 105L,
                yearMonth = "2026-08",
                bankName = "MONETA",
                date = "2026-08-10",
                amount = -650.0,
                counterpartyName = "RESTAURACE KOLYBA",
                category = "RESTAURANTS_DINING"
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)
        assertTrue("Groceries and dining must never be audited as recurring subscriptions", summary.items.isEmpty())
        assertEquals(0.0, summary.totalMonthlyBurn, 0.001)
    }

    @Test
    fun testCzechMerchantCatalog_twoLetterExactTokenMatching() {
        val overrides = mapOf("o2" to BankTransactionType.SERVICES_UTILITIES)

        // Whole word "o2" token matches
        val matchTelecom = CzechMerchantCatalog.matchCategory("PLATBA KANCL O2 CZ PRAHA", overrides)
        assertEquals(BankTransactionType.SERVICES_UTILITIES, matchTelecom)

        // Embedded substring in unrelated word does not match 2-letter override
        val matchPhoto = CzechMerchantCatalog.matchCategory("FOTO24 NAKUP", overrides)
        assertNull(matchPhoto)
    }

    @Test
    fun testFinancialEngine_dualEarnerSection35cChildTaxBonus() {
        // Václav has 0 earned income (e.g. sabbatical), but Eleonora earns 40 000 CZK/mo (> 6x min wage)
        val settingsDual = SettingsEntity(
            baseYear = 2026,
            isSingleHousehold = false,
            vSalary = 0.0,
            eStartingSalary = 40000.0,
            eReturnYear = 2026,
            eReturnMonth = 1,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2024,
            child1TaxBonusAnnual = 15204.0,
            minWageMonthly = 20800.0
        )

        val state = FinancialEngine.calculate(settingsDual, runMonteCarlo = false)
        assertTrue(
            "Dual-earner household qualifies for Section 35c child tax bonus if either parent satisfies 6x min wage",
            state.taxReturnHelper.childBonus > 0.0
        )
        assertEquals(15204.0, state.taxReturnHelper.childBonus, 0.001)

        // When both parents have 0 earned income, child bonus is 0
        val settingsNoIncome = settingsDual.copy(eStartingSalary = 0.0)
        val stateNoIncome = FinancialEngine.calculate(settingsNoIncome, runMonteCarlo = false)
        assertEquals(0.0, stateNoIncome.taxReturnHelper.childBonus, 0.001)
    }

    @Test
    fun testFinancialEngine_progressive23TaxGrossInversion() {
        val settings = SettingsEntity(
            baseYear = 2026,
            taxSecondBracketThresholdAnnual = 1762812.0,
            taxpayerCreditAnnual = 30840.0
        )

        val thresholdAnnual = settings.taxSecondBracketThresholdAnnual
        val thresholdMonthly = thresholdAnnual / 12.0
        val creditMonthly = settings.taxpayerCreditAnnual / 12.0
        val netAtThresholdMonthly = thresholdMonthly * 0.734 + creditMonthly

        val invertedAtThreshold = FinancialEngine.netToGrossAnnual(
            netMonthly = netAtThresholdMonthly,
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = thresholdAnnual
        )
        assertEquals(thresholdAnnual, invertedAtThreshold, 1.0)

        // Above threshold: higher gross
        val netHighMonthly = netAtThresholdMonthly + 50000.0
        val invertedHigh = FinancialEngine.netToGrossAnnual(
            netMonthly = netHighMonthly,
            taxpayerCreditAnnual = settings.taxpayerCreditAnnual,
            highBracketThresholdAnnual = thresholdAnnual
        )
        assertTrue(invertedHigh > thresholdAnnual)
    }

    @Test
    fun testFinancialEngine_baristaFireMilestone() {
        val settings = SettingsEntity(
            baseYear = 2026,
            vSalary = 65000.0,
            rentMonthly = 20000.0,
            groceriesMonthly = 15000.0,
            safeWithdrawalRatePct = 3.5
        )

        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val barista = state.fireMilestones.baristaFire
        assertNotNull("Barista FIRE milestone must be computed", barista)
        val expectedBaristaTarget = kotlin.math.round((state.fireBaseTargetToday * 0.50) / 10_000.0) * 10_000.0
        assertEquals(expectedBaristaTarget, barista!!.targetAmountToday, 0.001)
        assertEquals("Barista FIRE", barista.name)
        assertEquals("Semi-Retired", barista.badgeLabel)
    }

    @Test
    fun testSettingsEntity_emergencyReserveMode_and_backupManager_roundTrip() {
        val initial = SettingsEntity(
            emergencyReserveMode = "9M"
        )
        val json = BackupManager.serializeSettingsToJson(initial)
        val fallback = SettingsEntity()
        val restored = BackupManager.deserializeSettingsFromJson(json, fallback)
        assertNotNull(restored)
        assertEquals("9M", restored!!.emergencyReserveMode)

        // Invalid mode fallback sanitization
        val invalidJson = json.replace("\"emergencyReserveMode\":\"9M\"", "\"emergencyReserveMode\":\"INVALID\"")
        val sanitized = BackupManager.deserializeSettingsFromJson(invalidJson, fallback)
        assertNotNull(sanitized)
        assertEquals("6M", sanitized!!.emergencyReserveMode)
    }

    @Test
    fun testSingleHouseholdNetWorthParity() {
        val dualSettings = SettingsEntity(
            isSingleHousehold = false,
            liquidPortfolioCurrent = 500_000.0,
            eLiquidPortfolioCurrent = 200_000.0,
            dipBalanceCurrent = 50_000.0,
            eDipBalanceCurrent = 30_000.0,
            dpsBalanceCurrent = 40_000.0,
            eDpsBalanceCurrent = 20_000.0,
            emergencyReserveCurrent = 100_000.0
        )
        val dualState = FinancialEngine.calculate(dualSettings, runMonteCarlo = false)
        val expectedDualTotal = 500_000.0 + 200_000.0 + 50_000.0 + 30_000.0 + 40_000.0 + 20_000.0 + 100_000.0
        assertEquals(expectedDualTotal, dualState.netWorthTotal, 0.001)

        val singleSettings = dualSettings.copy(isSingleHousehold = true)
        val singleState = FinancialEngine.calculate(singleSettings, runMonteCarlo = false)
        val expectedSingleTotal = 500_000.0 + 50_000.0 + 40_000.0 + 100_000.0
        assertEquals(expectedSingleTotal, singleState.netWorthTotal, 0.001)
    }

    @Test
    fun testFreedomDaysDeficitCalculation() {
        val deficitSavings = -15_000.0
        val livingCostMonthly = 45_000.0
        val daysBurned = FinancialEngine.freedomDaysBoughtMonthly(deficitSavings, livingCostMonthly)
        assertTrue("Deficit should produce negative freedom days", daysBurned < 0.0)
        val dailyCost = livingCostMonthly / 30.42
        assertEquals(deficitSavings / dailyCost, daysBurned, 0.001)
    }
}
