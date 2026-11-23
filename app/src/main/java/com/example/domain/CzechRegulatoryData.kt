package com.example.domain

/**
 * Live Czech Macroeconomic & Statutory Data Model.
 * Represents verified official statistics from ČSÚ, ČNB, and statutory tax parameters from Zákon o daních z příjmů (ZDP).
 */
data class CzechRegulatoryData(
    val timestamp: Long = System.currentTimeMillis(),
    val sourceName: String = "Bundled 2026 Czech Statutory Constants",
    val isFxLive: Boolean = false,
    val effectiveYear: Int = 2026,
    
    // Macroeconomics (ČSÚ & ČNB)
    val csuCpiInflationPct: Double = 2.8,
    val csuNationalAverageWageMonthly: Double = 48967.0,
    val eurCzkRate: Double = 25.20,
    val usdCzkRate: Double = 23.50,
    val gbpCzkRate: Double = 29.80,
    val plnCzkRate: Double = 5.90,
    val chfCzkRate: Double = 26.90,
    val hufCzkRate: Double = 0.063,
    val rateDate: String = "",
    
    // Czech Income Tax (Zákon č. 586/1992 Sb. - ZDP)
    val baseTaxRatePct: Double = 15.0,
    val progressiveTaxRatePct: Double = 23.0,
    val progressive23ThresholdAnnual: Double = 1762812.0, // 36x average national wage
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
    val dpsYouthSubsidyMaxMonthly: Double = 340.0, // Statutory cap on state subsidy (340 CZK/mo)
    val dpsYouthAgeLimit: Int = 30,
    val dpsSubsidyRateStandardPct: Double = 20.0,
    val dpsSubsidyRateYouthPct: Double = 20.0,
    val dpsStatutoryFeeCapPct: Double = 1.0, // Statutory dynamic/balanced DPS management fee cap (Act No. 427/2011 Coll. § 60(3))
    val childBonusMinWageMultiplier: Double = 6.0, // ZDP § 35c(4) min wage multiplier
    val parentalAllowanceTotalBeforeCutoff: Double = 350000.0, // Statutory total pot (350 000 CZK)
    val parentalAllowanceTotalFromCutoff: Double = 400000.0, // Statutory total pot from 1.1.2027 (400 000 CZK; 800 000 for twins)
    val parentalAllowanceCutoffYear: Int = 2027
)

data class SyncDifferenceItem(
    val category: String,
    val label: String,
    val currentValueFormatted: String,
    val liveValueFormatted: String,
    val isDifferent: Boolean,
    val impactHint: String
)
