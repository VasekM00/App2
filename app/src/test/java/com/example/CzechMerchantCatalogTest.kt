package com.example

import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CzechMerchantCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CzechMerchantCatalogTest {

    @Test
    fun testTextNormalization() {
        val normalized = CzechMerchantCatalog.normalize("Platba kartou v ČR: RESTAURACE DALEŠICE s.r.o. Brno")
        assertTrue("Normalized text must not contain accents: $normalized", !normalized.contains("š"))
        assertTrue("Normalized text must be lowercase: $normalized", normalized == normalized.lowercase())
        assertTrue("Normalized text should retain restaurant keyword: $normalized", normalized.contains("restaurace"))
        assertTrue("Normalized text should retain dalesice: $normalized", normalized.contains("dalesice"))
    }

    @Test
    fun testRealStatementMerchantsClassification() {
        val testCases = listOf(
            "DONER KEBAB GASHI" to BankTransactionType.DINING_RESTAURANT,
            "DATART.CZ" to BankTransactionType.SHOPPING_GOODS,
            "Ceske drahy / cd.cz" to BankTransactionType.TRANSPORTATION,
            "RESTAURACE DALESICE" to BankTransactionType.DINING_RESTAURANT,
            "IKEA BRNO OD" to BankTransactionType.SHOPPING_GOODS,
            "REBELBEAN BRNO" to BankTransactionType.DINING_RESTAURANT,
            "GOPAY *FORENDORS.CZ" to BankTransactionType.SUBSCRIPTIONS_MEDIA,
            "Pivni burza" to BankTransactionType.DINING_RESTAURANT,
            "Alza Brno" to BankTransactionType.SHOPPING_GOODS,
            "dm drogerie markt" to BankTransactionType.HEALTH_DRUGSTORE,
            "Google Play Apps" to BankTransactionType.SUBSCRIPTIONS_MEDIA,
            "KAVARNA POHODICKA" to BankTransactionType.DINING_RESTAURANT,
            "Patreon* Membership" to BankTransactionType.SUBSCRIPTIONS_MEDIA,
            "4CAMPING.CZ" to BankTransactionType.SHOPPING_GOODS,
            "ANODA CAFE BRNO" to BankTransactionType.DINING_RESTAURANT,
            "vyber bankomat" to BankTransactionType.ATM_CASH,
            "SALON GALAPA" to BankTransactionType.SERVICES_UTILITIES,
            "Zivotni pojisteni" to BankTransactionType.SERVICES_UTILITIES,
            "SHOPTET*BAKTOMA" to BankTransactionType.SHOPPING_GOODS,
            "RESTAURACE GOPAL" to BankTransactionType.DINING_RESTAURANT
        )

        for ((merchant, expectedCategory) in testCases) {
            val matched = CzechMerchantCatalog.matchCategory(merchant)
            assertEquals(
                "Merchant '$merchant' should map to $expectedCategory",
                expectedCategory,
                matched
            )
        }
    }

    @Test
    fun testMajorCzechGroceryChains() {
        val groceries = listOf(
            "Albert supermarket",
            "Billa s.r.o.",
            "Lidl Ceska republika",
            "Tesco hypermarket",
            "Penny Market",
            "Kaufland v.o.s.",
            "Rohlik.cz potraviny",
            "Kosik.cz"
        )
        for (g in groceries) {
            val matched = CzechMerchantCatalog.matchCategory(g)
            assertEquals("Grocery '$g' should map to GROCERIES", BankTransactionType.GROCERIES, matched)
        }
    }

    @Test
    fun testTransitAndFuelBrands() {
        val transport = listOf(
            "RegioJet a.s.",
            "Leo Express",
            "Dopravni podnik hl. m. Prahy",
            "DPP jizdenka",
            "DPMB Brno",
            "ORLEN Benzina",
            "MOL Ceska republika",
            "Shell cerpaci stanice",
            "Tank ONO s.r.o.",
            "EasyPark parkovani"
        )
        for (t in transport) {
            val matched = CzechMerchantCatalog.matchCategory(t)
            assertEquals("Transport '$t' should map to TRANSPORTATION", BankTransactionType.TRANSPORTATION, matched)
        }
    }

    @Test
    fun testDigitalSubscriptionsAndGaming() {
        val subs = listOf(
            "Spotify AB",
            "Netflix International",
            "YouTube Premium",
            "Steam Games Valve",
            "OpenAI ChatGPT Subscription"
        )
        for (s in subs) {
            val matched = CzechMerchantCatalog.matchCategory(s)
            assertEquals("Sub '$s' should map to SUBSCRIPTIONS_MEDIA", BankTransactionType.SUBSCRIPTIONS_MEDIA, matched)
        }
    }

    @Test
    fun testCharityAndFoundationClassification() {
        val foundations = listOf(
            "Nadace Sirius" to BankTransactionType.CHARITY_DONATION,
            "Sirius nadacni fond" to BankTransactionType.CHARITY_DONATION,
            "Clovek v tisni o.p.s." to BankTransactionType.CHARITY_DONATION,
            "Dobry andel, nadace" to BankTransactionType.CHARITY_DONATION,
            "Post Bellum - Pamet naroda" to BankTransactionType.CHARITY_DONATION,
            "Centrum Paraple z.s." to BankTransactionType.CHARITY_DONATION,
            "Konto Bariery Nadace Charty 77" to BankTransactionType.CHARITY_DONATION,
            "Svetluska" to BankTransactionType.CHARITY_DONATION,
            "Darujme.cz" to BankTransactionType.CHARITY_DONATION,
            "Donio.cz dar" to BankTransactionType.CHARITY_DONATION,
            "Charita Ceska republika" to BankTransactionType.CHARITY_DONATION,
            "UNICEF CR" to BankTransactionType.CHARITY_DONATION
        )
        for ((f, expected) in foundations) {
            val matched = CzechMerchantCatalog.matchCategory(f)
            assertEquals("Foundation '$f' should map to $expected", expected, matched)
        }
    }

    @Test
    fun testUserCustomOverridesPrecedence() {
        val defaultCategory = CzechMerchantCatalog.matchCategory("Rebelbean Brno")
        assertEquals(BankTransactionType.DINING_RESTAURANT, defaultCategory)

        // Custom override: User treats Rebelbean as GROCERIES (e.g. buying whole coffee beans)
        val userOverrides = mapOf("rebelbean" to BankTransactionType.GROCERIES)
        val overriddenCategory = CzechMerchantCatalog.matchCategory("Rebelbean Brno", userOverrides)
        assertEquals(
            "User custom override should take precedence",
            BankTransactionType.GROCERIES,
            overriddenCategory
        )
    }

    @Test
    fun testEndToEndStatementParsingWithNewCategories() {
        val statementCsv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce;Variabilní symbol
            10.09.2026;123456/0800;Zaměstnavatel s.r.o.;80 000,00;CZK;Mzda za 08/2026;
            12.09.2026;987654/0300;České dráhy cd.cz;-185,00;CZK;Vlak Brno-Praha;
            14.09.2026;222333/0100;RESTAURACE DALEŠICE;-450,00;CZK;Obed;
            15.09.2026;333444/2010;DATART.CZ elektro;-2 490,00;CZK;Sluchatka;
            16.09.2026;444555/0800;dm drogerie markt;-320,00;CZK;Drogerie;
            17.09.2026;555666/0300;Google Play Apps;-149,00;CZK;Aplikace;
            18.09.2026;666777/0600;Vyber z bankomatu;-2 000,00;CZK;Hotovost;
            19.09.2026;777888/0100;Lidl Ceska republika;-1 200,00;CZK;Potraviny;
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(statementCsv.byteInputStream(Charsets.UTF_8))
        assertEquals(BankType.MONETA, summary.detectedBank)
        assertEquals(8, summary.transactions.size)

        val categories = summary.transactions.associate { it.date to it.category }
        assertEquals(BankTransactionType.SALARY_VACLAV, categories["2026-09-10"])
        assertEquals(BankTransactionType.TRANSPORTATION, categories["2026-09-12"])
        assertEquals(BankTransactionType.DINING_RESTAURANT, categories["2026-09-14"])
        assertEquals(BankTransactionType.SHOPPING_GOODS, categories["2026-09-15"])
        assertEquals(BankTransactionType.HEALTH_DRUGSTORE, categories["2026-09-16"])
        assertEquals(BankTransactionType.SUBSCRIPTIONS_MEDIA, categories["2026-09-17"])
        assertEquals(BankTransactionType.ATM_CASH, categories["2026-09-18"])
        assertEquals(BankTransactionType.GROCERIES, categories["2026-09-19"])

        // Ensure non-lifestyle: none of these transactions are classified as generic LIFESTYLE_LIVING!
        val lifestyleCount = summary.transactions.count { it.category == BankTransactionType.LIFESTYLE_LIVING }
        assertEquals("None of the real merchants should fall back to generic LIFESTYLE_LIVING", 0, lifestyleCount)

        // Ledger aggregation verification: expGroceries = 1200, expOther = sum of other expenses
        assertEquals(1200.0, summary.expGroceries, 0.01)
        val expectedOther = 185.0 + 450.0 + 2490.0 + 320.0 + 149.0 + 2000.0
        assertEquals(expectedOther, summary.expOther, 0.01)
    }
}
