package com.example

import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.RegulatoryConstants
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Guards against divergence between the engine defaults (SettingsEntity / RegulatoryConstants)
 * and the remotely-synced regulatory_manifest.json served from the repository root.
 *
 * A stale manifest silently overwrites user tax parameters during "Live Czech Sync"
 * (see CzechEconomicSyncService.applyRegulatoryUpdates), which is exactly the class of
 * bug that must never reach a financial-planning app.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestEngineConsistencyTest {

    private fun locateManifest(): File {
        val start = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val direct = listOf(
            File(start, "regulatory_manifest.json"),
            File(start, "../regulatory_manifest.json")
        )
        for (candidate in direct) {
            if (candidate.exists()) return candidate.canonicalFile
        }
        var current: File? = start
        while (current != null) {
            val candidate = File(current, "regulatory_manifest.json")
            if (candidate.exists()) return candidate.canonicalFile
            current = current.parentFile
        }
        throw AssertionError("regulatory_manifest.json not found walking up from ${start.absolutePath}")
    }

    @Test
    fun manifestMatchesEngineDefaults() {
        val manifest = JSONObject(locateManifest().readText())
        val settings = SettingsEntity()

        assertEquals(
            "Progressive 23% threshold in manifest must match SettingsEntity default",
            settings.taxSecondBracketThresholdAnnual,
            manifest.getDouble("progressive23ThresholdAnnual"),
            1.0
        )
        assertEquals(
            "Progressive 23% threshold must equal 36x the declared average monthly wage",
            settings.taxSecondBracketThresholdAnnual,
            manifest.getDouble("csuNationalAverageWageMonthly") * 36.0,
            1.0
        )
        assertEquals(
            "RegulatoryConstants 2026 threshold must match the manifest",
            RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
            manifest.getDouble("progressive23ThresholdAnnual"),
            1.0
        )

        assertEquals("base tax rate", settings.taxRatePct, manifest.getDouble("baseTaxRatePct"), 0.001)
        assertEquals("progressive tax rate", settings.taxRateSecondPct, manifest.getDouble("progressiveTaxRatePct"), 0.001)
        assertEquals("taxpayer credit", RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026, manifest.getDouble("taxpayerCreditAnnual"), 0.001)
        assertEquals("spouse credit", RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026, manifest.getDouble("spouseTaxCreditAnnual"), 0.001)
        assertEquals("spouse income limit", RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026, manifest.getDouble("spouseIncomeLimitAnnual"), 0.001)
        assertEquals("child 1 bonus", RegulatoryConstants.STATUTORY_CHILD_1_BONUS_ANNUAL_2026, manifest.getDouble("child1TaxBonusAnnual"), 0.001)
        assertEquals("child 2 bonus", RegulatoryConstants.STATUTORY_CHILD_2_BONUS_ANNUAL_2026, manifest.getDouble("child2TaxBonusAnnual"), 0.001)
        assertEquals("child 3+ bonus", RegulatoryConstants.STATUTORY_CHILD_3_PLUS_BONUS_ANNUAL_2026, manifest.getDouble("child3PlusTaxBonusAnnual"), 0.001)
        assertEquals("retirement deduction ceiling", RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026, manifest.getDouble("dipDpsCombinedCeilingAnnual"), 0.001)
        assertEquals("employer exemption", RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL, manifest.getDouble("employerRetirementExemptionAnnual"), 0.001)
        assertEquals("minimum wage", RegulatoryConstants.STATUTORY_MIN_WAGE_MONTHLY_2026, manifest.getDouble("minWageMonthly"), 0.001)
        assertEquals("DPS minimum deposit", RegulatoryConstants.LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY, manifest.getDouble("dpsMinDepositForSubsidy"), 0.001)
        assertEquals("DPS deduction threshold", RegulatoryConstants.STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026, manifest.getDouble("dpsDeductionThresholdMonthly"), 0.001)
        assertEquals("DPS standard subsidy cap", RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY, manifest.getDouble("dpsStandardSubsidyMaxMonthly"), 0.001)
        assertEquals("DPS youth subsidy cap", RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY, manifest.getDouble("dpsYouthSubsidyMaxMonthly"), 0.001)
        assertEquals("DPS youth age limit", RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_AGE_LIMIT, manifest.getInt("dpsYouthAgeLimit"))
        assertEquals("DPS statutory fee cap", RegulatoryConstants.LEPSI_PENZIJKO_STATUTORY_FEE_CAP_PCT, manifest.getDouble("dpsStatutoryFeeCapPct"), 0.001)
    }

    @Test
    fun czechRegulatoryDataDefaultsMatchEngineConstants() {
        // The offline "live sync" defaults are a third copy of the statutory parameters;
        // they must not drift from SettingsEntity / RegulatoryConstants.
        val live = CzechRegulatoryData()

        assertEquals(settings().taxSecondBracketThresholdAnnual, live.progressive23ThresholdAnnual, 1.0)
        assertEquals(48_967.0, live.csuNationalAverageWageMonthly, 1.0)
        assertEquals(settings().taxRatePct, live.baseTaxRatePct, 0.001)
        assertEquals(settings().taxRateSecondPct, live.progressiveTaxRatePct, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026, live.taxpayerCreditAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_SPOUSE_CREDIT_ANNUAL_2026, live.spouseTaxCreditAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026, live.spouseIncomeLimitAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_CHILD_1_BONUS_ANNUAL_2026, live.child1TaxBonusAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_CHILD_2_BONUS_ANNUAL_2026, live.child2TaxBonusAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_CHILD_3_PLUS_BONUS_ANNUAL_2026, live.child3PlusTaxBonusAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026, live.dipDpsCombinedCeilingAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL, live.employerRetirementExemptionAnnual, 0.001)
        assertEquals(RegulatoryConstants.STATUTORY_MIN_WAGE_MONTHLY_2026, live.minWageMonthly, 0.001)
        assertEquals(RegulatoryConstants.LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY, live.dpsStandardSubsidyMaxMonthly, 0.001)
        assertEquals(RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY, live.dpsYouthSubsidyMaxMonthly, 0.001)
        assertEquals(RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_AGE_LIMIT, live.dpsYouthAgeLimit)
    }

    private fun settings() = SettingsEntity()
}
