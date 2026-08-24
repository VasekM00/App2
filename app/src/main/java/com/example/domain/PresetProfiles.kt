package com.example.domain

import com.example.data.SettingsEntity

/**
 * Pre-configured financial profile presets for different Czech personal finance scenarios.
 */
data class FinancialProfilePreset(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val iconEmoji: String,
    val settings: SettingsEntity
)

object PresetProfiles {

    val CZECH_HOUSEHOLD_BASELINE = FinancialProfilePreset(
        id = "czech_household_baseline",
        title = "Czech Household (Baseline)",
        description = "Dual-income family with parental allowance, DPS with 2026 Lepší penzijko youth subsidy, DIP tax deductions, and planned return to work.",
        badge = "DEFAULT",
        iconEmoji = "",
        settings = SettingsEntity()
    )

    val SINGLE_PRO_FIRE = FinancialProfilePreset(
        id = "single_pro_fire",
        title = "Single Tech Professional",
        description = "Single earner with 65k CZK net income, high savings rate (35k/mo DCA), aggressive ETF growth, DIP tax deduction max-out, and solo lean lifestyle.",
        badge = "AGGRESSIVE",
        iconEmoji = "",
        settings = SettingsEntity(
            isSingleHousehold = true,
            vSalary = 65000.0,
            vBonusAnnual = 30000.0,
            vMealVouchersMonthly = 2200.0,
            eReturnYear = 2029,
            eStartingSalary = 0.0,
            eBonusAnnual = 0.0,
            eSalaryGrowthPct = 0.0,
            eReinvestedPct = 0.0,
            eParentalAllowanceMonthly = 0.0,
            eLecturingMonthly = 0.0,
            eIncludeLecturing = false,
            familyGiftMonthly = 0.0,
            lumpSumInclude = false,
            liquidPortfolioCurrent = 450000.0,
            eLiquidPortfolioCurrent = 0.0,
            ePortuDcaMonthly = 0.0,
            portuDcaMonthly = 25000.0,
            dipContributionMonthly = 4000.0,
            dpsOwnContributionMonthly = 1700.0,
            lifestyleCostAtFireMonthly = 26000.0,
            statePensionMonthly = 14000.0,
            rentMonthly = 18000.0,
            groceriesMonthly = 6000.0,
            cafesMonthly = 3000.0,
            entertainmentMonthly = 2500.0,
            transportMonthly = 550.0,
            subscriptionsMonthly = 950.0,
            therapyMonthly = 0.0,
            charityMonthly = 1000.0,
            otherDiscretionaryMonthly = 2000.0,
            includeSpouseCredit = false,
            hasChildUnder3 = false,
            childExpensesEnabled = false,
            child1Enabled = false,
            child2Enabled = false
        )
    )

    val YOUNG_STARTER_LEPSI_PENZIJKO = FinancialProfilePreset(
        id = "young_starter_lepsi_penzijko",
        title = "Young Starter (<30y Lepší Penzijko)",
        description = "Early career professional (age 24) capturing the 40% youth state subsidy on DPS (up to 680 CZK/mo), starting DIP early, and accumulating first ETF capital.",
        badge = "REFORM FOCUS",
        iconEmoji = "",
        settings = SettingsEntity(
            isSingleHousehold = true,
            primaryAge = 24,
            vSalary = 38000.0,
            vBonusAnnual = 15000.0,
            vMealVouchersMonthly = 1800.0,
            eReturnYear = 2029,
            eIncludeLecturing = false,
            familyGiftMonthly = 0.0,
            lumpSumInclude = false,
            liquidPortfolioCurrent = 100000.0,
            eLiquidPortfolioCurrent = 0.0,
            ePortuDcaMonthly = 0.0,
            portuDcaMonthly = 9000.0,
            dpsOwnContributionMonthly = 1700.0, // Triggers full 40% youth state subsidy = 680 CZK
            dipContributionMonthly = 1700.0,
            emergencyReserveCurrent = 80000.0,
            emergencyReserveTarget = 120000.0,
            lifestyleCostAtFireMonthly = 28000.0,
            rentMonthly = 14000.0,
            groceriesMonthly = 4500.0,
            cafesMonthly = 2000.0,
            entertainmentMonthly = 1500.0,
            transportMonthly = 550.0,
            subscriptionsMonthly = 600.0,
            otherDiscretionaryMonthly = 1500.0,
            includeSpouseCredit = false,
            hasChildUnder3 = false,
            childExpensesEnabled = false,
            child1Enabled = false,
            child2Enabled = false
        )
    )

    val HIGH_SAVERS_COUPLE = FinancialProfilePreset(
        id = "high_savers_couple",
        title = "Accelerated Dual FIRE Couple",
        description = "Both partners working full-time with 95k CZK combined household income, 45k monthly investment rate, aggressive compounding, and Fat FIRE ambitions.",
        badge = "FAT FIRE",
        iconEmoji = "",
        settings = SettingsEntity(
            vSalary = 52000.0,
            vBonusAnnual = 25000.0,
            eReturnYear = 2026, // Active now
            eStartingSalary = 43000.0,
            eBonusAnnual = 20000.0,
            eSalaryGrowthPct = 4.0,
            eReinvestedPct = 80.0,
            eParentalAllowanceMonthly = 0.0,
            eIncludeLecturing = true,
            eLecturingMonthly = 8000.0,
            familyGiftMonthly = 0.0,
            lumpSumInclude = false,
            liquidPortfolioCurrent = 600000.0,
            eLiquidPortfolioCurrent = 250000.0,
            portuDcaMonthly = 22000.0,
            ePortuDcaMonthly = 18000.0,
            dpsOwnContributionMonthly = 1700.0,
            dipContributionMonthly = 4000.0,
            eDpsOwnContributionMonthly = 1700.0,
            eDipContributionMonthly = 4000.0,
            lifestyleCostAtFireMonthly = 45000.0,
            safeWithdrawalRatePct = 3.75,
            rentMonthly = 25000.0,
            groceriesMonthly = 8000.0,
            cafesMonthly = 3500.0,
            entertainmentMonthly = 3000.0,
            transportMonthly = 1200.0,
            subscriptionsMonthly = 1100.0,
            otherDiscretionaryMonthly = 3000.0,
            includeSpouseCredit = false,
            hasChildUnder3 = false,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2023,
            child2Enabled = false
        )
    )

    val ALL_PRESETS = listOf(
        CZECH_HOUSEHOLD_BASELINE,
        SINGLE_PRO_FIRE,
        YOUNG_STARTER_LEPSI_PENZIJKO,
        HIGH_SAVERS_COUPLE
    )
}
