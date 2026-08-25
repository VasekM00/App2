package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.DEFAULT_CUSTOM_LIFE_GOALS
import com.example.domain.serializeCustomLifeGoals
import java.util.Calendar

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val baseYear: Int = 2026,
    val primaryAge: Int = 26,
    val primaryName: String = "Václav",
    val spouseName: String = "Eleonora",
    val isSingleHousehold: Boolean = false,
    val dcaAnnualGrowthPct: Double = 0.0,
    val vSalary: Double = 33500.0,
    val vMealVouchersMonthly: Double = 2090.0,
    val vOtherInflowsMonthly: Double = 0.0,
    val eReturnYear: Int = 2029,
    val eReturnMonth: Int = 1,
    val eStartingSalary: Double = 22000.0,
    val eSalaryGrowthPct: Double = 3.0,
    val eReinvestedPct: Double = 75.0,
    val eParentalAllowanceMonthly: Double = 13000.0,
    val eLecturingMonthly: Double = 6900.0,
    val eIncludeLecturing: Boolean = true,
    val eOtherInflowsMonthly: Double = 0.0,
    val familyGiftMonthly: Double = 16000.0,
    val lumpSumYear: Int = 2030,
    val lumpSumAmount: Double = 500000.0,
    val lumpSumInclude: Boolean = true,
    val liquidPortfolioCurrent: Double = 200000.0,
    val dpsBalanceCurrent: Double = 0.0,
    val dipBalanceCurrent: Double = 0.0,
    val portuDcaMonthly: Double = 11000.0,
    val portfolioNominalReturnPct: Double = 7.0,
    val dpsOwnContributionMonthly: Double = 1700.0,
    val dpsGrossReturnPct: Double = 6.0,
    val dpsAnnualFeePct: Double = 0.5, // Statutory cap under Lepší penzijko
    val dipContributionMonthly: Double = 1700.0,
    val employerRetirementMonthly: Double = 2800.0,
    // Eleonora's Investments & Accounts
    val eLiquidPortfolioCurrent: Double = 50000.0,
    val ePortuDcaMonthly: Double = 3000.0,
    val eDpsBalanceCurrent: Double = 0.0,
    val eDpsOwnContributionMonthly: Double = 0.0,
    val eDipBalanceCurrent: Double = 0.0,
    val eDipContributionMonthly: Double = 0.0,
    val eEmployerRetirementMonthly: Double = 0.0,
    val emergencyReserveCurrent: Double = 200000.0,
    val emergencyReserveTarget: Double = 250000.0,
    val lifestyleCostAtFireMonthly: Double = 0.0, // 0.0 = Auto-sync with current living expenses
    val vStatePensionMonthly: Double = 12000.0,
    val eStatePensionMonthly: Double = 12000.0,
    val statePensionAge: Int = 67,
    val safeWithdrawalRatePct: Double = 4.0,
    val safetyBufferPct: Double = 10.0,
    val cpiInflationPct: Double = 2.8, // Default matches ČSÚ live benchmark; updated by Live Sync
    val fireTargetOverride: Double = 0.0,
    // Monthly Living Costs
    val rentMonthly: Double = 21770.0,
    val groceriesMonthly: Double = 4800.0,
    val cafesMonthly: Double = 2250.0,
    val therapyMonthly: Double = 2000.0,
    val charityMonthly: Double = 2000.0,
    val entertainmentMonthly: Double = 1200.0,
    val transportMonthly: Double = 650.0,
    val subscriptionsMonthly: Double = 584.0,
    val otherDiscretionaryMonthly: Double = 1500.0,
    // Statutory & Tax parameters (Czech Tax Act No. 586/1992 Coll. & 2026/2027 Lepší penzijko)
    val taxRatePct: Double = 15.0, // ZDP § 16 basic bracket
    val taxRateSecondPct: Double = 23.0, // ZDP § 16 higher bracket
    val taxSecondBracketThresholdAnnual: Double = 1582812.0, // 36x average wage
    val taxpayerCreditAnnual: Double = 30840.0, // ZDP § 35ba(1)(a)
    val taxDeductionCeilingAnnual: Double = 48000.0, // ZDP § 15 & § 15a combined DPS + DIP ceiling
    val spouseTaxCreditAnnual: Double = 24840.0, // ZDP § 35ba(1)(b)
    val spouseIncomeLimitAnnual: Double = 68000.0, // ZDP § 35ba(1)(b) income ceiling
    val includeSpouseCredit: Boolean = true,
    val hasChildUnder3: Boolean = true,
    val minWageMonthly: Double = 22400.0,
    val dpsDeductionThresholdMonthly: Double = 1700.0, // ZDP § 15(5)
    val dpsStandardSubsidyMaxMonthly: Double = 340.0, // Lepší penzijko 20% cap
    val dpsYouthSubsidyMaxMonthly: Double = 680.0, // Lepší penzijko 40% youth cap (<30y)
    val dpsMinDepositForSubsidy: Double = 500.0, // Lepší penzijko min deposit
    val dpsYouthAgeLimit: Int = 30,
    val dpsSubsidyRateStandardPct: Double = 20.0,
    val dpsSubsidyRateYouthPct: Double = 40.0,
    // Child expenses & Multi-child settings (ZDP § 35c)
    val childExpensesEnabled: Boolean = true,
    val child1Enabled: Boolean = true,
    val child1BirthYear: Int = 2024,
    val child2Enabled: Boolean = true,
    val child2BirthYear: Int = 2027,
    val child1TaxBonusAnnual: Double = 15204.0, // ZDP § 35c 1st child
    val child2TaxBonusAnnual: Double = 22320.0, // ZDP § 35c 2nd child
    val child3PlusTaxBonusAnnual: Double = 27840.0, // ZDP § 35c 3rd+ child
    val childToddlerMonthly: Double = 4800.0,
    val childPreschoolMonthly: Double = 6500.0,
    val childSchoolMonthly: Double = 8500.0,
    val childTeenMonthly: Double = 13000.0,
    val childUniMonthly: Double = 10000.0,
    val rentGrowthPct: Double = 4.0,
    val monteCarloN: Int = 400,
    val monteCarloVolatilityPct: Double = 15.0,
    val monteCarloSeed: Long = 42L,
    val customExpensesJson: String = "[]",
    val customGoalsJson: String = "[]",
    val customLumpSumsJson: String = "[]",
    val deletedCategoriesJson: String = "[]"
) {
    companion object {
        fun freshDefaults(): SettingsEntity = SettingsEntity(
            baseYear = Calendar.getInstance().get(Calendar.YEAR),
            customGoalsJson = serializeCustomLifeGoals(DEFAULT_CUSTOM_LIFE_GOALS)
        )
    }
}
