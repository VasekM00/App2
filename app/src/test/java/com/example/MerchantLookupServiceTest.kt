package com.example

import com.example.util.BankTransactionType
import com.example.util.MerchantLookupService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantLookupServiceTest {

    @Test
    fun `test blank merchant input returns no category`() = runBlocking {
        val result = MerchantLookupService.lookupMerchant("   ")
        assertNull(result.suggestedCategory)
        assertEquals("No merchant name detected", result.snippet)
    }

    @Test
    fun `test direct catalog match has high confidence`() = runBlocking {
        // Albert is in CzechMerchantCatalog as GROCERIES
        val result = MerchantLookupService.lookupMerchant("Albert Supermarket Praha")
        assertNotNull(result.suggestedCategory)
        assertEquals(BankTransactionType.GROCERIES, result.suggestedCategory)
        assertEquals("Catalog match", result.snippet)
        assertEquals("High", result.confidence)
    }

    @Test
    fun `test dining keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Pizzerie Giovanni" to BankTransactionType.DINING_RESTAURANT,
            "Restaurace U Cerneho orla" to BankTransactionType.DINING_RESTAURANT,
            "Kavarna Slavia" to BankTransactionType.DINING_RESTAURANT,
            "Bistro Karlin" to BankTransactionType.DINING_RESTAURANT,
            "Smash Burger Vinohrady" to BankTransactionType.DINING_RESTAURANT,
            "Cukrarna Mysak" to BankTransactionType.DINING_RESTAURANT
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
            org.junit.Assert.assertTrue("Confidence should be Inferred or High for '$merchant'", res.confidence in setOf("Inferred", "High"))
        }
    }

    @Test
    fun `test groceries keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Vecerka U Nadrazi" to BankTransactionType.GROCERIES,
            "Pekarna Kabat" to BankTransactionType.GROCERIES,
            "Reznictvi Ksana" to BankTransactionType.GROCERIES,
            "Lahudky Svoboda" to BankTransactionType.GROCERIES,
            "Lokalni Potraviny" to BankTransactionType.GROCERIES
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test health and drugstore keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Lekarna U Salvatora" to BankTransactionType.HEALTH_DRUGSTORE,
            "Drogerie Teta Brno" to BankTransactionType.HEALTH_DRUGSTORE,
            "Grand Optical Optika" to BankTransactionType.HEALTH_DRUGSTORE,
            "Dentalni hygiena Clinic" to BankTransactionType.HEALTH_DRUGSTORE
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test transportation keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Autoservis Rychly" to BankTransactionType.TRANSPORTATION,
            "Pneuservis Novak" to BankTransactionType.TRANSPORTATION,
            "Cerpaci stanice Ono" to BankTransactionType.TRANSPORTATION,
            "Benzina Ceska Lipa" to BankTransactionType.TRANSPORTATION,
            "Taxi Praha Modry andel" to BankTransactionType.TRANSPORTATION,
            "Dopravni podnik DPP jizdenky" to BankTransactionType.TRANSPORTATION
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test services and utilities keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Kadernictvi Andrea" to BankTransactionType.SERVICES_UTILITIES,
            "Barber Shop Gentlemen" to BankTransactionType.SERVICES_UTILITIES,
            "Cistirna odevu Express" to BankTransactionType.SERVICES_UTILITIES,
            "Kosmeticky salon Krasa" to BankTransactionType.SERVICES_UTILITIES
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test shopping goods keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Elektro Spacil" to BankTransactionType.SHOPPING_GOODS,
            "Nabytek Jamall" to BankTransactionType.SHOPPING_GOODS,
            "Sportovni potreby Decat" to BankTransactionType.SHOPPING_GOODS,
            "Damske odevy Styl" to BankTransactionType.SHOPPING_GOODS
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test charity and donation keyword heuristics`() = runBlocking {
        val cases = listOf(
            "Nadace Dobry andel" to BankTransactionType.CHARITY_DONATION,
            "Charita Ceska republika" to BankTransactionType.CHARITY_DONATION,
            "Darcovske konto Pomoc" to BankTransactionType.CHARITY_DONATION
        )
        for ((merchant, expected) in cases) {
            val res = MerchantLookupService.lookupMerchant(merchant)
            assertEquals("Expected $expected for '$merchant'", expected, res.suggestedCategory)
        }
    }

    @Test
    fun `test unrecognized merchant returns null with none confidence`() = runBlocking {
        val res = MerchantLookupService.lookupMerchant("XYZQWE Corporation 12345")
        assertNull(res.suggestedCategory)
        assertEquals("Unrecognized merchant", res.snippet)
        assertEquals("None", res.confidence)
    }
}
