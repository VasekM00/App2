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

    @Test
    fun testPortuAccountNumberCategorization() {
        val portuTxs = listOf(
            "76788295/2010" to BankTransactionType.INVESTMENT_PORTU,
            "76788295" to BankTransactionType.INVESTMENT_PORTU,
            "WOOD Retail Investments a.s." to BankTransactionType.INVESTMENT_PORTU,
            "WOOD & Company" to BankTransactionType.INVESTMENT_PORTU,
            "Portu vklad" to BankTransactionType.INVESTMENT_PORTU
        )
        for ((desc, expected) in portuTxs) {
            val matched = CzechMerchantCatalog.matchCategory(desc)
            assertEquals("Portu recipient '$desc' should map to $expected", expected, matched)
        }

        val csv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce;Variabilní symbol
            15.09.2026;76788295/2010;;-5 000,00;CZK;Pravidelna investice;10452389
        """.trimIndent()
        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(1, summary.transactions.size)
        assertEquals(BankTransactionType.INVESTMENT_PORTU, summary.transactions[0].category)
        assertEquals(5000.0, summary.invPortu, 0.01)
    }

    @Test
    fun testDpsAndXtbAccountNumberCategorization() {
        val investmentTxs = listOf(
            "5005004433/0800" to BankTransactionType.INVESTMENT_DPS,
            "5005004433" to BankTransactionType.INVESTMENT_DPS,
            "NN Penzijní společnost" to BankTransactionType.INVESTMENT_DPS,
            "518746050/2700" to BankTransactionType.INVESTMENT_PORTU,
            "518746050" to BankTransactionType.INVESTMENT_PORTU,
            "XTB S.A." to BankTransactionType.INVESTMENT_PORTU,
            "X-Trade Brokers" to BankTransactionType.INVESTMENT_PORTU
        )
        for ((desc, expected) in investmentTxs) {
            val matched = CzechMerchantCatalog.matchCategory(desc)
            assertEquals("Investment recipient '$desc' should map to $expected", expected, matched)
        }

        // Test end-to-end statement parsing for NN DPS and XTB
        val csv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce;Variabilní symbol
            10.09.2026;5005004433/0800;NN Penzijni;-1 700,00;CZK;DPS sporeni;99887766
            12.09.2026;518746050/2700;;-10 000,00;CZK;Vklad na obchodni ucet;123456
        """.trimIndent()
        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(2, summary.transactions.size)
        assertEquals(BankTransactionType.INVESTMENT_DPS, summary.transactions[0].category)
        assertEquals(BankTransactionType.INVESTMENT_PORTU, summary.transactions[1].category)
        assertEquals(1700.0, summary.invDps, 0.01)
        assertEquals(10000.0, summary.invPortu, 0.01)
    }

    @Test
    fun testBrnoRetailAndCafesCategorization() {
        val brnoMerchants = listOf(
            // Brno Specialty Coffee, Bakeries & Roasters
            "Industra Coffee Brno" to BankTransactionType.DINING_RESTAURANT,
            "Monogram Espresso Bar" to BankTransactionType.DINING_RESTAURANT,
            "Kafe Mitte Brno" to BankTransactionType.DINING_RESTAURANT,
            "Skog Urban Hub" to BankTransactionType.DINING_RESTAURANT,
            "Kafec Orli" to BankTransactionType.DINING_RESTAURANT,
            "Buchta Caffe" to BankTransactionType.DINING_RESTAURANT,
            "Kocici kavarna Pelisek" to BankTransactionType.DINING_RESTAURANT,
            "Cafe Falk" to BankTransactionType.DINING_RESTAURANT,
            "Cafe Atlas" to BankTransactionType.DINING_RESTAURANT,
            "Kavarna Spolek" to BankTransactionType.DINING_RESTAURANT,
            "Cafe Momenta" to BankTransactionType.DINING_RESTAURANT,
            "KofiKofi" to BankTransactionType.DINING_RESTAURANT,
            "Sorry, peceme jinak" to BankTransactionType.DINING_RESTAURANT,
            "Mlsna holka" to BankTransactionType.DINING_RESTAURANT,
            "Bozsky kopecek" to BankTransactionType.DINING_RESTAURANT,
            "Cukrarna Vetrnik" to BankTransactionType.DINING_RESTAURANT,

            // Brno Bars, Bistros, Pubs & Dining
            "Bar, ktery neexistuje" to BankTransactionType.DINING_RESTAURANT,
            "Super Panda Circus" to BankTransactionType.DINING_RESTAURANT,
            "4pokoje Brno" to BankTransactionType.DINING_RESTAURANT,
            "Bar Slast" to BankTransactionType.DINING_RESTAURANT,
            "Atelier Bar & Bistro" to BankTransactionType.DINING_RESTAURANT,
            "Element Bar & Restaurant" to BankTransactionType.DINING_RESTAURANT,
            "Bucheck food truck" to BankTransactionType.DINING_RESTAURANT,
            "Burger Inn Brno" to BankTransactionType.DINING_RESTAURANT,
            "Forky's Brno" to BankTransactionType.DINING_RESTAURANT,
            "Eggo Bistro" to BankTransactionType.DINING_RESTAURANT,
            "Ramen Brno" to BankTransactionType.DINING_RESTAURANT,
            "Vycep Na stojaka" to BankTransactionType.DINING_RESTAURANT,
            "Pivovar Pegas" to BankTransactionType.DINING_RESTAURANT,
            "Stopkova plzenska pivnice" to BankTransactionType.DINING_RESTAURANT,
            "Lokal U Caipla" to BankTransactionType.DINING_RESTAURANT,
            "Ochutnavkova pivnice" to BankTransactionType.DINING_RESTAURANT,
            "Pivnice U Capa" to BankTransactionType.DINING_RESTAURANT,
            "Monte Bu Restaurant" to BankTransactionType.DINING_RESTAURANT,
            "Borgo Agnese" to BankTransactionType.DINING_RESTAURANT,
            "Castellana Trattoria" to BankTransactionType.DINING_RESTAURANT,

            // Brno Bakeries, Markets, Butchers & Groceries
            "William Thomas Bakery Jaselska" to BankTransactionType.GROCERIES,
            "WT Bakery Brno" to BankTransactionType.GROCERIES,
            "Pekarstvi Carlini" to BankTransactionType.GROCERIES,
            "Pekarstvi Makovec" to BankTransactionType.GROCERIES,
            "Pekarstvi Krizak" to BankTransactionType.GROCERIES,
            "Karlova pekarna Brno" to BankTransactionType.GROCERIES,
            "Sklizeno Josefska" to BankTransactionType.GROCERIES,
            "Brana ke zdravi" to BankTransactionType.GROCERIES,
            "Trhy na Zelnaku" to BankTransactionType.GROCERIES,
            "Mikrofarma Brno" to BankTransactionType.GROCERIES,
            "Reznictvi u Krejcara" to BankTransactionType.GROCERIES,
            "Steinhauser s.r.o." to BankTransactionType.GROCERIES,
            "La Formaggeria Gran Moravia" to BankTransactionType.GROCERIES,

            // Brno Shopping, Books & Design
            "Galerie Vankovka" to BankTransactionType.SHOPPING_GOODS,
            "Olympia Brno" to BankTransactionType.SHOPPING_GOODS,
            "Avion Shopping Park Brno" to BankTransactionType.SHOPPING_GOODS,
            "NC Kralovo Pole" to BankTransactionType.SHOPPING_GOODS,
            "Velky Spalicek" to BankTransactionType.SHOPPING_GOODS,
            "Knihkupectvi Barvic a Novotny" to BankTransactionType.SHOPPING_GOODS,
            "Place Store Brno" to BankTransactionType.SHOPPING_GOODS,

            // Brno Health, Wellness & STAREZ
            "Chytra lekarna Brno" to BankTransactionType.HEALTH_DRUGSTORE,
            "STAREZ Kravi hora" to BankTransactionType.HEALTH_DRUGSTORE,
            "Koupaliste Riviera" to BankTransactionType.HEALTH_DRUGSTORE,
            "Infinit Maximus" to BankTransactionType.HEALTH_DRUGSTORE,
            "Big One Fitness" to BankTransactionType.HEALTH_DRUGSTORE,
            "FN Brno Bohunice" to BankTransactionType.HEALTH_DRUGSTORE,

            // Brno Transit
            "DPMB pipni a jed" to BankTransactionType.TRANSPORTATION,
            "KORDIS JMK" to BankTransactionType.TRANSPORTATION,

            // Brno Culture & Utilities
            "Kino Scala" to BankTransactionType.SERVICES_UTILITIES,
            "Divadlo Husa na provazku" to BankTransactionType.SERVICES_UTILITIES,
            "Narodni divadlo Brno" to BankTransactionType.SERVICES_UTILITIES,
            "Teplarny Brno a.s." to BankTransactionType.SERVICES_UTILITIES
        )

        for ((merchant, expectedCategory) in brnoMerchants) {
            val matched = CzechMerchantCatalog.matchCategory(merchant)
            assertEquals(
                "Brno merchant '$merchant' should map to $expectedCategory",
                expectedCategory,
                matched
            )
        }
    }
}
