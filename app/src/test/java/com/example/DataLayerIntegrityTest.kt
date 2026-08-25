package com.example

import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.CustomLifeGoalItem
import com.example.domain.CustomLumpSumItem
import com.example.domain.DEFAULT_CUSTOM_LIFE_GOALS
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLifeGoals
import com.example.domain.parseCustomLumpSums
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeCustomLifeGoals
import com.example.domain.serializeCustomLumpSums
import com.example.domain.serializeDeletedCategories
import com.example.util.BackupManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.full.memberProperties

@RunWith(RobolectricTestRunner::class)
class DataLayerIntegrityTest {

    // 2.1: Reflection — check that serializeSettingsToJson contains every SettingsEntity property (excluding id)
    @Test
    fun test2_1_reflectionAllFieldsSerializedAsJsonKeys() {
        val defaultEntity = SettingsEntity()
        val jsonStr = BackupManager.serializeSettingsToJson(defaultEntity)
        val jsonObject = JSONObject(jsonStr)

        val properties = SettingsEntity::class.memberProperties.filter { it.name != "id" }
        assertTrue("SettingsEntity must have properties", properties.isNotEmpty())

        for (prop in properties) {
            assertTrue("JSON must contain key for property '${prop.name}'", jsonObject.has(prop.name))
        }
    }

    // 2.2: Full round-trip — create custom SettingsEntity, serialize, deserialize with fallback, assertEquals on all fields
    @Test
    fun test2_2_fullRoundTripSettingsEntity() {
        val custom = SettingsEntity(
            id = 1,
            baseYear = 2028,
            primaryAge = 30,
            primaryName = "Petr",
            spouseName = "Klara",
            isSingleHousehold = true,
            dcaAnnualGrowthPct = 4.2,
            vSalary = 65000.0,
            vBonusAnnual = 50000.0,
            vMealVouchersMonthly = 3000.0,
            vOtherInflowsMonthly = 5000.0,
            eReturnYear = 2031,
            eReturnMonth = 7,
            eStartingSalary = 35000.0,
            eBonusAnnual = 20000.0,
            eSalaryGrowthPct = 5.0,
            eReinvestedPct = 85.0,
            eParentalAllowanceMonthly = 15000.0,
            eLecturingMonthly = 10000.0,
            eIncludeLecturing = false,
            eOtherInflowsMonthly = 4500.0,
            familyGiftMonthly = 20000.0,
            annualOtherGifts = 30000.0,
            lumpSumYear = 2033,
            lumpSumAmount = 1000000.0,
            lumpSumInclude = false,
            liquidPortfolioCurrent = 500000.0,
            dpsBalanceCurrent = 50000.0,
            dipBalanceCurrent = 40000.0,
            portuDcaMonthly = 18000.0,
            portfolioNominalReturnPct = 8.0,
            dpsOwnContributionMonthly = 2500.0,
            dpsGrossReturnPct = 7.5,
            dpsAnnualFeePct = 0.4,
            dipContributionMonthly = 3000.0,
            employerRetirementAnnual = 6000.0,
            eLiquidPortfolioCurrent = 100000.0,
            ePortuDcaMonthly = 4000.0,
            eDpsBalanceCurrent = 20000.0,
            eDpsOwnContributionMonthly = 1500.0,
            eDipBalanceCurrent = 15000.0,
            eDipContributionMonthly = 2500.0,
            eEmployerRetirementAnnual = 4000.0,
            emergencyReserveCurrent = 350000.0,
            emergencyReserveTarget = 400000.0,
            lifestyleCostAtFireMonthly = 45000.0,
            statePensionMonthly = 18000.0,
            statePensionAge = 65,
            safeWithdrawalRatePct = 3.8,
            safetyBufferPct = 12.0,
            cpiInflationPct = 2.5,
            fireTargetOverride = 15000000.0,
            rentMonthly = 28000.0,
            groceriesMonthly = 7500.0,
            cafesMonthly = 3500.0,
            therapyMonthly = 3000.0,
            charityMonthly = 2500.0,
            entertainmentMonthly = 2000.0,
            transportMonthly = 1200.0,
            subscriptionsMonthly = 950.0,
            otherDiscretionaryMonthly = 3000.0,
            taxRatePct = 15.0,
            taxRateSecondPct = 23.0,
            taxSecondBracketThresholdAnnual = 1700000.0,
            taxpayerCreditAnnual = 30840.0,
            taxDeductionCeilingAnnual = 48000.0,
            spouseTaxCreditAnnual = 24840.0,
            spouseIncomeLimitAnnual = 68000.0,
            includeSpouseCredit = false,
            hasChildUnder3 = false,
            minWageMonthly = 25000.0,
            dpsDeductionThresholdMonthly = 1700.0,
            dpsStandardSubsidyMaxMonthly = 340.0,
            dpsYouthSubsidyMaxMonthly = 680.0,
            dpsMinDepositForSubsidy = 500.0,
            dpsYouthAgeLimit = 30,
            dpsSubsidyRateStandardPct = 20.0,
            dpsSubsidyRateYouthPct = 40.0,
            childExpensesEnabled = false,
            child1Enabled = false,
            child1BirthYear = 2023,
            child2Enabled = true,
            child2BirthYear = 2026,
            child1TaxBonusAnnual = 16000.0,
            child2TaxBonusAnnual = 23000.0,
            child3PlusTaxBonusAnnual = 29000.0,
            childToddlerMonthly = 5500.0,
            childPreschoolMonthly = 7000.0,
            childSchoolMonthly = 9500.0,
            childTeenMonthly = 14000.0,
            childUniMonthly = 12000.0,
            rentGrowthPct = 5.0,
            monteCarloN = 500,
            monteCarloVolatilityPct = 18.0,
            monteCarloSeed = 12345L,
            customExpensesJson = """[{"id":"e1","name":"Gym","amount":1500.0}]""",
            customGoalsJson = """[{"id":"g1","name":"Car","iconName":"car","targetYear":2029,"targetAmountCzk":400000.0,"currentSavedCzk":100000.0}]""",
            customLumpSumsJson = """[{"id":"l1","name":"Bonus","year":2031,"amount":300000.0,"enabled":true}]""",
            deletedCategoriesJson = """["therapyMonthly","charityMonthly"]"""
        )

        val jsonStr = BackupManager.serializeSettingsToJson(custom)
        val deserialized = BackupManager.deserializeSettingsFromJson(jsonStr, fallback = SettingsEntity())

        assertNotNull("Deserialized object should not be null", deserialized)
        assertEquals("Full round-trip should match all fields exactly", custom, deserialized)
    }

    // 2.3: Missing keys — deserialize "{}" with fallback=SettingsEntity(). Should return non-null with default values
    @Test
    fun test2_3_missingKeysReturnsFallbackDefaults() {
        val fallback = SettingsEntity()
        val result = BackupManager.deserializeSettingsFromJson("{}", fallback)

        assertNotNull("Deserializing empty JSON object should return non-null", result)
        assertEquals("Empty JSON should return fallback entity values", fallback, result)
    }

    // 2.4: Extra keys — deserialize '{"unknownField": 999, "baseYear": 2030}' with fallback. Should return non-null with baseYear=2030
    @Test
    fun test2_4_extraKeysIgnoredAndKnownFieldsUpdated() {
        val fallback = SettingsEntity(baseYear = 2026)
        val jsonStr = """{"unknownField": 999, "baseYear": 2030}"""
        val result = BackupManager.deserializeSettingsFromJson(jsonStr, fallback)

        assertNotNull("Deserializing JSON with unknown fields should succeed", result)
        assertEquals(2030, result!!.baseYear)
        assertEquals("Other fields should retain fallback values", fallback.primaryAge, result.primaryAge)
        assertEquals("Other fields should retain fallback values", fallback.primaryName, result.primaryName)
    }

    // 2.5: Malformed JSON — deserialize "{{not json}}". Should return null
    @Test
    fun test2_5_malformedJsonReturnsNull() {
        val fallback = SettingsEntity()
        val result = BackupManager.deserializeSettingsFromJson("{{not json}}", fallback)
        assertNull("Malformed JSON should return null", result)
    }

    // 2.6: Empty string — deserialize "". Should return null
    @Test
    fun test2_6_emptyStringReturnsNull() {
        val fallback = SettingsEntity()
        val result = BackupManager.deserializeSettingsFromJson("", fallback)
        assertNull("Empty string should return null", result)
    }

    // 2.7: parseCustomExpenses("{broken}") returns emptyList
    @Test
    fun test2_7_parseCustomExpensesBrokenReturnsEmptyList() {
        val result = parseCustomExpenses("{broken}")
        assertEquals("Broken JSON should return emptyList for custom expenses", emptyList<CustomExpenseItem>(), result)
    }

    // 2.8: parseCustomLifeGoals("") returns list of size 3 (the defaults)
    @Test
    fun test2_8_parseCustomLifeGoalsEmptyStringReturnsDefaultsOfSize3() {
        val result = parseCustomLifeGoals("")
        assertEquals("Empty string should return 3 default life goals", 3, result.size)
        assertEquals("Defaults should match DEFAULT_CUSTOM_LIFE_GOALS", DEFAULT_CUSTOM_LIFE_GOALS, result)
    }

    // 2.9: parseDeletedCategories("{broken}") returns emptySet
    @Test
    fun test2_9_parseDeletedCategoriesBrokenReturnsEmptySet() {
        val result = parseDeletedCategories("{broken}")
        assertEquals("Broken JSON should return emptySet for deleted categories", emptySet<String>(), result)
    }

    // 2.10: CustomExpenseItem round-trip — serialize and parse back
    @Test
    fun test2_10_customExpenseItemRoundTrip() {
        val originalItems = listOf(
            CustomExpenseItem("1", "Pet Care", 1500.0),
            CustomExpenseItem("2", "Coworking Space", 4000.0),
            CustomExpenseItem("3", "Online Education", 950.0)
        )
        val serialized = serializeCustomExpenses(originalItems)
        val parsed = parseCustomExpenses(serialized)

        assertEquals("Serialized and parsed custom expenses should match original", originalItems, parsed)
    }

    // 2.11: CustomLifeGoalItem round-trip
    @Test
    fun test2_11_customLifeGoalItemRoundTrip() {
        val originalGoals = listOf(
            CustomLifeGoalItem("g1", "Cabin Purchase", "home", 2029, 1200000.0, 300000.0),
            CustomLifeGoalItem("g2", "Electric Vehicle", "car", 2027, 750000.0, 250000.0),
            CustomLifeGoalItem("g3", "Sabbatical Trip", "star", 2031, 350000.0, 100000.0)
        )
        val serialized = serializeCustomLifeGoals(originalGoals)
        val parsed = parseCustomLifeGoals(serialized)

        assertEquals("Serialized and parsed custom life goals should match original", originalGoals, parsed)
    }

    // 2.12: deletedCategories round-trip
    @Test
    fun test2_12_deletedCategoriesRoundTrip() {
        val originalSet = setOf("therapyMonthly", "charityMonthly", "subscriptionsMonthly", "entertainmentMonthly")
        val serialized = serializeDeletedCategories(originalSet)
        val parsed = parseDeletedCategories(serialized)

        assertEquals("Serialized and parsed deleted categories should match original", originalSet, parsed)
    }

    // 2.13: CustomLumpSumItem round-trip
    @Test
    fun test2_13_customLumpSumItemRoundTrip() {
        val originalList = listOf(
            CustomLumpSumItem("l1", "Inheritance", 2032, 500000.0, true),
            CustomLumpSumItem("l2", "Property Sale", 2035, 1200000.0, false)
        )
        val serialized = serializeCustomLumpSums(originalList)
        val parsed = parseCustomLumpSums(serialized)

        assertEquals("Serialized and parsed custom lump sums should match original", originalList, parsed)
    }
}
