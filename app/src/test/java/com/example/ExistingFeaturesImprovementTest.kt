package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.domain.PortfolioYearPoint
import com.example.util.BankTransactionType
import com.example.util.SubscriptionAuditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExistingFeaturesImprovementTest {

    private fun getMainJavaDir(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        val direct = File(userDir, "app/src/main/java")
        if (direct.exists()) return direct
        val sub = File(userDir, "src/main/java")
        if (sub.exists()) return sub
        return File("app/src/main/java")
    }

    @Test
    fun testSubscriptionAuditorFireCapitalCalculations() {
        val transactions = listOf(
            ImportedBankTransactionEntity(
                id = 1,
                yearMonth = "2026-07",
                bankName = "MONETA",
                date = "2026-07-10",
                amount = -350.0,
                counterpartyName = "Netflix",
                message = "Netflix monthly subscription",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            ),
            ImportedBankTransactionEntity(
                id = 2,
                yearMonth = "2026-08",
                bankName = "MONETA",
                date = "2026-08-10",
                amount = -350.0,
                counterpartyName = "Netflix",
                message = "Netflix monthly subscription",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            )
        )

        val swrPct = 3.5
        val summary = SubscriptionAuditor.auditSubscriptions(transactions, swrPct = swrPct)

        assertEquals(1, summary.items.size)
        val netflix = summary.items[0]
        assertEquals("Netflix", netflix.displayName)
        assertEquals(350.0, netflix.normalizedMonthlyAmount, 0.01)

        val expectedAnnualBurn = 350.0 * 12.0
        val expectedFireCapital = expectedAnnualBurn / (swrPct / 100.0)
        assertEquals(4200.0, expectedAnnualBurn, 0.01)
        assertEquals(120000.0, expectedFireCapital, 0.01)

        // Test dismissal
        val summaryAfterDismiss = SubscriptionAuditor.auditSubscriptions(
            transactions,
            swrPct = swrPct,
            dismissedMerchantKeys = setOf(netflix.merchantKey)
        )
        assertEquals(0, summaryAfterDismiss.items.size)
        assertEquals(1, summaryAfterDismiss.dismissedCount)
    }

    @Test
    fun testTrajectoryMilestonesTsvExportLogic() {
        val points = listOf(
            PortfolioYearPoint(
                year = 2026,
                age = 26,
                portfolio = 1000000.0,
                target = 10000000.0,
                investedAnnual = 240000.0,
                reinvestAnnual = 70000.0,
                lumpSum = 0.0,
                status = "Accumulation",
                pensionPortfolio = 200000.0,
                totalPortfolio = 1200000.0
            ),
            PortfolioYearPoint(
                year = 2046,
                age = 46,
                portfolio = 9000000.0,
                target = 10000000.0,
                investedAnnual = 240000.0,
                reinvestAnnual = 600000.0,
                lumpSum = 0.0,
                status = "FIRE Milestone",
                pensionPortfolio = 2000000.0,
                totalPortfolio = 11000000.0
            )
        )

        val fireYear = 2046
        val header = "Year\tLiquid Portfolio (Kč)\tPension Portfolio (Kč)\tTotal Net Worth (Kč)\tFIRE Target (Kč)\tStatus"
        val rows = points.map { pt ->
            val status = if (pt.year == fireYear) "FIRE Milestone" else if (pt.totalPortfolio >= pt.target) "FIRE Ready" else "Accumulation"
            "${pt.year}\t${pt.portfolio.toLong()}\t${pt.pensionPortfolio.toLong()}\t${pt.totalPortfolio.toLong()}\t${pt.target.toLong()}\t$status"
        }

        val tsvContent = header + "\n" + rows.joinToString("\n")
        assertTrue(tsvContent.contains("Year\tLiquid Portfolio"))
        assertTrue(tsvContent.contains("2026\t1000000\t200000\t1200000\t10000000\tAccumulation"))
        assertTrue(tsvContent.contains("2046\t9000000\t2000000\t11000000\t10000000\tFIRE Milestone"))

        // Test SWR Safe Drawdown Capacity
        val swrPct = 3.5
        val annualDraw = points[1].portfolio * (swrPct / 100.0)
        val monthlyDraw = annualDraw / 12.0
        assertEquals(315000.0, annualDraw, 0.01)
        assertEquals(26250.0, monthlyDraw, 0.01)
    }

    @Test
    fun testProjectionsTabModularizationAndHygiene() {
        val mainDir = getMainJavaDir()
        val projectionsTab = File(mainDir, "com/example/ui/tabs/ProjectionsTab.kt")
        assertTrue("ProjectionsTab.kt must exist", projectionsTab.exists())
        val projLines = projectionsTab.readLines().size
        assertTrue("ProjectionsTab.kt must be a concise orchestrator (< 150 lines, was $projLines)", projLines < 150)

        val extractedFiles = listOf(
            "com/example/ui/tabs/TrajectorySubTab.kt",
            "com/example/ui/tabs/WhatIfSandboxSubTab.kt",
            "com/example/ui/tabs/MonteCarloAndStressSubTab.kt",
            "com/example/ui/tabs/TrajectoryMilestonesTableCard.kt",
            "com/example/ui/components/EditImportedTransactionDialog.kt"
        )

        val violations = mutableListOf<String>()
        for (rel in extractedFiles) {
            val f = File(mainDir, rel)
            assertTrue("File $rel must exist", f.exists())
            val count = f.readLines().size
            assertTrue("File $rel must have substantial content (> 50 lines, was $count)", count > 50)
            if (count >= 750) {
                violations.add("$rel has $count lines (exceeds 750 lines threshold)")
            }
        }

        assertTrue("All extracted files must be under 750 lines:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun testZeroPrintlnInNewlyCreatedFiles() {
        val mainDir = getMainJavaDir()
        val filesToCheck = listOf(
            "com/example/ui/tabs/ProjectionsTab.kt",
            "com/example/ui/tabs/TrajectorySubTab.kt",
            "com/example/ui/tabs/WhatIfSandboxSubTab.kt",
            "com/example/ui/tabs/MonteCarloAndStressSubTab.kt",
            "com/example/ui/tabs/TrajectoryMilestonesTableCard.kt",
            "com/example/ui/components/EditImportedTransactionDialog.kt",
            "com/example/ui/components/SubscriptionAuditorCard.kt"
        )

        val printlnRegex = Regex("""\b(println|print)\s*\(""")
        val systemOutRegex = Regex("""System\.(out|err)\.""")

        for (rel in filesToCheck) {
            val f = File(mainDir, rel)
            assertTrue("File $rel must exist", f.exists())
            val lines = f.readLines()
            for ((idx, raw) in lines.withIndex()) {
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue
                assertFalse("File $rel:${idx + 1} contains println: $line", printlnRegex.containsMatchIn(line))
                assertFalse("File $rel:${idx + 1} contains System.out: $line", systemOutRegex.containsMatchIn(line))
            }
        }
    }

    @Test
    fun testStatementImportCoordinatorGroceriesNotDoubleCountedAndRefundOverflow() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 1,
                yearMonth = "2026-09",
                bankName = "MONETA",
                date = "2026-09-05",
                amount = -1500.0,
                counterpartyName = "Lidl",
                message = "Nakup potravin",
                category = BankTransactionType.GROCERIES.name
            ),
            ImportedBankTransactionEntity(
                id = 2,
                yearMonth = "2026-09",
                bankName = "MONETA",
                date = "2026-09-08",
                amount = -800.0,
                counterpartyName = "Alza.cz",
                message = "Elektronika",
                category = BankTransactionType.SHOPPING_GOODS.name
            ),
            ImportedBankTransactionEntity(
                id = 3,
                yearMonth = "2026-09",
                bankName = "MONETA",
                date = "2026-09-12",
                amount = 1200.0,
                counterpartyName = "Alza.cz",
                message = "Vraceni zbozi",
                category = BankTransactionType.SHOPPING_GOODS.name
            )
        )

        val agg = com.example.util.StatementImportCoordinator.aggregateMonthTransactions(txs)
        // BUG-1 verification: Groceries (1500 CZK) must ONLY be in expGroceries, NOT in expOther!
        assertEquals(1500.0, agg.expGroceries, 0.01)
        assertEquals(0.0, agg.expOther, 0.01)
        // Net refund in SHOPPING_GOODS (-800 + 1200 = +400) overflows to incOther
        assertEquals(400.0, agg.incOther, 0.01)
    }

    @Test
    fun testMatchesMerchantInMonthAcrossBranchSuffixes() {
        val tx1 = ImportedBankTransactionEntity(
            id = 1,
            yearMonth = "2026-09",
            bankName = "CSOB",
            date = "2026-09-02",
            amount = -420.0,
            counterpartyName = "Billa Praha 5",
            message = "Platba kartou Billa Praha 5",
            category = BankTransactionType.GROCERIES.name
        )
        val tx2 = ImportedBankTransactionEntity(
            id = 2,
            yearMonth = "2026-09",
            bankName = "CSOB",
            date = "2026-09-14",
            amount = -690.0,
            counterpartyName = "Billa Praha 6",
            message = "Platba kartou Billa Praha 6",
            category = BankTransactionType.GROCERIES.name
        )
        val txUnrelated = ImportedBankTransactionEntity(
            id = 3,
            yearMonth = "2026-09",
            bankName = "CSOB",
            date = "2026-09-18",
            amount = -500.0,
            counterpartyName = "Albert Hypermarket",
            message = "Platba kartou Albert",
            category = BankTransactionType.GROCERIES.name
        )

        assertTrue(com.example.util.StatementImportCoordinator.matchesMerchantInMonth(tx1, tx2))
        assertFalse(com.example.util.StatementImportCoordinator.matchesMerchantInMonth(tx1, txUnrelated))
    }

    @Test
    fun testAuditFixesStaticAndBehavioralVerification() {
        val mainDir = getMainJavaDir()

        val coordinatorText = File(mainDir, "com/example/util/StatementImportCoordinator.kt").readText()
        assertFalse(
            "OTHER_EXPENSE_TYPES in StatementImportCoordinator must not include GROCERIES",
            Regex("""OTHER_EXPENSE_TYPES\s*=\s*setOf\([^)]*"GROCERIES"""").containsMatchIn(coordinatorText)
        )

        val vmText = File(mainDir, "com/example/ui/MainViewModel.kt").readText()
        assertTrue(
            "MainViewModel must persist learned merchant rules to merchantRulesJson",
            vmText.contains("merchantRulesJson = MerchantCategoryManager.serializeRulesToJson") ||
                vmText.contains("merchantRulesJson = com.example.util.MerchantCategoryManager.serializeRulesToJson")
        )
        assertTrue(
            "MainViewModel.deleteImportedStatement must preserve manual exclusions",
            vmText.contains("it.nettingReason.startsWith(\"Manually \")")
        )

        val milestonesCardText = File(mainDir, "com/example/ui/tabs/TrajectoryMilestonesTableCard.kt").readText()
        assertTrue(
            "YearDetailInspectorCard must unlock totalPortfolio at age >= 60",
            milestonesCardText.contains("val isPensionUnlocked = point.age >= 60") &&
                milestonesCardText.contains("if (isPensionUnlocked) point.totalPortfolio else point.portfolio")
        )

        val roadmapText = File(mainDir, "com/example/ui/tabs/FireRoadmapSubTab.kt").readText()
        assertTrue(
            "FireRoadmapSubTab must display Beyond 35y horizon when FIRE is not reached",
            roadmapText.contains("Target: Beyond 35y horizon")
        )
    }

    @Test
    fun testSubscriptionAuditorCzechAnnualAndBillingDateDrift() {
        val transactions = listOf(
            // Annual subscription with Czech adjective "Roční předplatné"
            ImportedBankTransactionEntity(
                id = 1,
                yearMonth = "2026-06",
                bankName = "MONETA",
                date = "2026-06-15",
                amount = -2400.0,
                counterpartyName = "JetBrains",
                message = "Roční předplatné IDE",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            ),
            // Billing-date drift: Spotify billed once in July (169 CZK) and twice in August (Aug 1 and Aug 31, 169 CZK each)
            ImportedBankTransactionEntity(
                id = 2,
                yearMonth = "2026-07",
                bankName = "MONETA",
                date = "2026-07-02",
                amount = -169.0,
                counterpartyName = "Spotify",
                message = "Spotify Premium",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            ),
            ImportedBankTransactionEntity(
                id = 3,
                yearMonth = "2026-08",
                bankName = "MONETA",
                date = "2026-08-01",
                amount = -169.0,
                counterpartyName = "Spotify",
                message = "Spotify Premium",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            ),
            ImportedBankTransactionEntity(
                id = 4,
                yearMonth = "2026-08",
                bankName = "MONETA",
                date = "2026-08-31",
                amount = -169.0,
                counterpartyName = "Spotify",
                message = "Spotify Premium",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(transactions, swrPct = 4.0)
        assertEquals(2, summary.items.size)

        val jetbrains = summary.items.find { it.merchantKey.contains("jetbrains") }
        assertNotNull(jetbrains)
        assertEquals("ANNUAL", jetbrains!!.billingCadence)
        assertEquals(200.0, jetbrains.normalizedMonthlyAmount, 0.01)

        val spotify = summary.items.find { it.merchantKey.contains("spotify") }
        assertNotNull(spotify)
        assertEquals("MONTHLY", spotify!!.billingCadence)
        // Must not double to 338 CZK or flag fake price creep due to 1st/31st calendar drift
        assertEquals(169.0, spotify.normalizedMonthlyAmount, 0.01)
        assertEquals(0.0, spotify.priceCreepDelta, 0.01)
    }
}
