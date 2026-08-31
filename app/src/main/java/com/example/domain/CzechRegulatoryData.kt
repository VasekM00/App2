package com.example.domain

/**
 * Live Czech Macroeconomic & Statutory Data Model.
 * Represents verified official statistics from ČSÚ, ČNB, and statutory tax parameters from Zákon o daních z příjmů (ZDP).
 */
data class CzechRegulatoryData(
    val timestamp: Long = System.currentTimeMillis(),
    val sourceName: String = "ČSÚ, ČNB & Statutory Registry",
    val effectiveYear: Int = 2026,
    
    // Macroeconomics (ČSÚ & ČNB)
    val csuCpiInflationPct: Double = 2.8,
    val csuAnnualAverageCpiPct: Double = 2.5,
    val csuNationalAverageWageMonthly: Double = 43967.0,
    val eurCzkRate: Double = 25.15,
    val usdCzkRate: Double = 23.25,
    val rateDate: String = "",
    
    // Global Asset Benchmarks (Long-Term Realized Real CAGR)
    val msciWorld10yCagrPct: Double = 8.9,
    val sp50010yCagrPct: Double = 12.1,
    
    // Czech Income Tax (Zákon č. 586/1992 Sb. - ZDP)
    val baseTaxRatePct: Double = 15.0,
    val progressiveTaxRatePct: Double = 23.0,
    val progressive23ThresholdAnnual: Double = 1582812.0, // 36x average national wage
    val taxpayerCreditAnnual: Double = 30840.0, // § 35ba(1)(a)
    val spouseTaxCreditAnnual: Double = 24840.0, // § 35ba(1)(b)
    val spouseIncomeLimitAnnual: Double = 68000.0, // § 35ba(1)(b)
    val minWageMonthly: Double = 22400.0,
    
    // Child Tax Credits (ZDP § 35c)
    val child1TaxBonusAnnual: Double = 15204.0,
    val child2TaxBonusAnnual: Double = 22320.0,
    val child3PlusTaxBonusAnnual: Double = 27840.0,
    
    // Retirement Tax Shield & Pension Reform (ZDP § 15, § 15a & Lepší penzijko)
    val dipDpsCombinedCeilingAnnual: Double = 48000.0, // Combined statutory tax deduction ceiling
    val employerRetirementExemptionAnnual: Double = 50000.0, // § 6(9)(m) tax-free employer contribution
    val dpsMinDepositForSubsidy: Double = 500.0,
    val dpsDeductionThresholdMonthly: Double = 1700.0,
    val dpsStandardSubsidyMaxMonthly: Double = 340.0, // 20% on 1,700 CZK
    val dpsYouthSubsidyMaxMonthly: Double = 680.0, // 40% youth subsidy under 30 yrs
    val dpsYouthAgeLimit: Int = 30,
    val dpsStatutoryFeeCapPct: Double = 0.5 // Maximum TER management fee cap
)

data class SyncDifferenceItem(
    val category: String,
    val label: String,
    val currentValueFormatted: String,
    val liveValueFormatted: String,
    val isDifferent: Boolean,
    val impactHint: String
)
