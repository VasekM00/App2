package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingsEntity::class, LedgerEntryEntity::class, ActionStateEntity::class],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun actionStateDao(): ActionStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloVolatilityPct REAL NOT NULL DEFAULT 15.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloSeed INTEGER NOT NULL DEFAULT 42")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN primaryName TEXT NOT NULL DEFAULT 'Václav'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN spouseName TEXT NOT NULL DEFAULT 'Eleonora'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isSingleHousehold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dcaAnnualGrowthPct REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Table-rebuild migration for backward compatibility on SQLite < 3.35 (Android <= API 33)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_settings_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        baseYear INTEGER NOT NULL,
                        primaryAge INTEGER NOT NULL,
                        primaryName TEXT NOT NULL,
                        spouseName TEXT NOT NULL,
                        isSingleHousehold INTEGER NOT NULL,
                        dcaAnnualGrowthPct REAL NOT NULL,
                        vSalary REAL NOT NULL,
                        vBonusAnnual REAL NOT NULL,
                        vMealVouchersMonthly REAL NOT NULL,
                        vOtherInflowsMonthly REAL NOT NULL,
                        eReturnYear INTEGER NOT NULL,
                        eStartingSalary REAL NOT NULL,
                        eBonusAnnual REAL NOT NULL,
                        eSalaryGrowthPct REAL NOT NULL,
                        eReinvestedPct REAL NOT NULL,
                        eParentalAllowanceMonthly REAL NOT NULL,
                        eLecturingMonthly REAL NOT NULL,
                        eIncludeLecturing INTEGER NOT NULL,
                        familyGiftMonthly REAL NOT NULL,
                        annualOtherGifts REAL NOT NULL,
                        lumpSumYear INTEGER NOT NULL,
                        lumpSumAmount REAL NOT NULL,
                        lumpSumInclude INTEGER NOT NULL,
                        liquidPortfolioCurrent REAL NOT NULL,
                        dpsBalanceCurrent REAL NOT NULL,
                        dipBalanceCurrent REAL NOT NULL,
                        portuDcaMonthly REAL NOT NULL,
                        portfolioNominalReturnPct REAL NOT NULL,
                        dpsOwnContributionMonthly REAL NOT NULL,
                        dpsGrossReturnPct REAL NOT NULL,
                        dpsAnnualFeePct REAL NOT NULL,
                        dipContributionMonthly REAL NOT NULL,
                        employerRetirementAnnual REAL NOT NULL,
                        eLiquidPortfolioCurrent REAL NOT NULL,
                        ePortuDcaMonthly REAL NOT NULL,
                        eDpsBalanceCurrent REAL NOT NULL,
                        eDpsOwnContributionMonthly REAL NOT NULL,
                        eDipBalanceCurrent REAL NOT NULL,
                        eDipContributionMonthly REAL NOT NULL,
                        eEmployerRetirementAnnual REAL NOT NULL,
                        emergencyReserveCurrent REAL NOT NULL,
                        emergencyReserveTarget REAL NOT NULL,
                        lifestyleCostAtFireMonthly REAL NOT NULL,
                        statePensionMonthly REAL NOT NULL,
                        statePensionAge INTEGER NOT NULL,
                        safeWithdrawalRatePct REAL NOT NULL,
                        safetyBufferPct REAL NOT NULL,
                        cpiInflationPct REAL NOT NULL,
                        fireTargetOverride REAL NOT NULL,
                        rentMonthly REAL NOT NULL,
                        groceriesMonthly REAL NOT NULL,
                        cafesMonthly REAL NOT NULL,
                        therapyMonthly REAL NOT NULL,
                        charityMonthly REAL NOT NULL,
                        entertainmentMonthly REAL NOT NULL,
                        transportMonthly REAL NOT NULL,
                        subscriptionsMonthly REAL NOT NULL,
                        otherDiscretionaryMonthly REAL NOT NULL,
                        taxRatePct REAL NOT NULL,
                        taxRateSecondPct REAL NOT NULL,
                        taxSecondBracketThresholdAnnual REAL NOT NULL,
                        taxpayerCreditAnnual REAL NOT NULL,
                        taxDeductionCeilingAnnual REAL NOT NULL,
                        spouseTaxCreditAnnual REAL NOT NULL,
                        spouseIncomeLimitAnnual REAL NOT NULL,
                        includeSpouseCredit INTEGER NOT NULL,
                        hasChildUnder3 INTEGER NOT NULL,
                        minWageMonthly REAL NOT NULL,
                        dpsDeductionThresholdMonthly REAL NOT NULL,
                        dpsStandardSubsidyMaxMonthly REAL NOT NULL,
                        dpsYouthSubsidyMaxMonthly REAL NOT NULL,
                        dpsMinDepositForSubsidy REAL NOT NULL,
                        dpsYouthAgeLimit INTEGER NOT NULL,
                        dpsSubsidyRateStandardPct REAL NOT NULL,
                        dpsSubsidyRateYouthPct REAL NOT NULL,
                        childExpensesEnabled INTEGER NOT NULL,
                        child1Enabled INTEGER NOT NULL,
                        child1BirthYear INTEGER NOT NULL,
                        child2Enabled INTEGER NOT NULL,
                        child2BirthYear INTEGER NOT NULL,
                        child1TaxBonusAnnual REAL NOT NULL,
                        child2TaxBonusAnnual REAL NOT NULL,
                        child3PlusTaxBonusAnnual REAL NOT NULL,
                        childToddlerMonthly REAL NOT NULL,
                        childPreschoolMonthly REAL NOT NULL,
                        childSchoolMonthly REAL NOT NULL,
                        childTeenMonthly REAL NOT NULL,
                        childUniMonthly REAL NOT NULL,
                        rentGrowthPct REAL NOT NULL,
                        monteCarloN INTEGER NOT NULL,
                        monteCarloVolatilityPct REAL NOT NULL,
                        monteCarloSeed INTEGER NOT NULL,
                        customExpensesJson TEXT NOT NULL,
                        customGoalsJson TEXT NOT NULL,
                        customLumpSumsJson TEXT NOT NULL,
                        deletedCategoriesJson TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO app_settings_new (
                        id, baseYear, primaryAge, primaryName, spouseName, isSingleHousehold, dcaAnnualGrowthPct,
                        vSalary, vBonusAnnual, vMealVouchersMonthly, vOtherInflowsMonthly, eReturnYear, eStartingSalary,
                        eBonusAnnual, eSalaryGrowthPct, eReinvestedPct, eParentalAllowanceMonthly, eLecturingMonthly,
                        eIncludeLecturing, familyGiftMonthly, annualOtherGifts, lumpSumYear, lumpSumAmount,
                        lumpSumInclude, liquidPortfolioCurrent, dpsBalanceCurrent, dipBalanceCurrent, portuDcaMonthly,
                        portfolioNominalReturnPct, dpsOwnContributionMonthly, dpsGrossReturnPct, dpsAnnualFeePct,
                        dipContributionMonthly, employerRetirementAnnual, eLiquidPortfolioCurrent, ePortuDcaMonthly,
                        eDpsBalanceCurrent, eDpsOwnContributionMonthly, eDipBalanceCurrent, eDipContributionMonthly,
                        eEmployerRetirementAnnual, emergencyReserveCurrent, emergencyReserveTarget,
                        lifestyleCostAtFireMonthly, statePensionMonthly, statePensionAge, safeWithdrawalRatePct,
                        safetyBufferPct, cpiInflationPct, fireTargetOverride, rentMonthly, groceriesMonthly,
                        cafesMonthly, therapyMonthly, charityMonthly, entertainmentMonthly, transportMonthly,
                        subscriptionsMonthly, otherDiscretionaryMonthly, taxRatePct, taxRateSecondPct,
                        taxSecondBracketThresholdAnnual, taxpayerCreditAnnual, taxDeductionCeilingAnnual,
                        spouseTaxCreditAnnual, spouseIncomeLimitAnnual, includeSpouseCredit, hasChildUnder3,
                        minWageMonthly, dpsDeductionThresholdMonthly, dpsStandardSubsidyMaxMonthly,
                        dpsYouthSubsidyMaxMonthly, dpsMinDepositForSubsidy, dpsYouthAgeLimit,
                        dpsSubsidyRateStandardPct, dpsSubsidyRateYouthPct, childExpensesEnabled, child1Enabled,
                        child1BirthYear, child2Enabled, child2BirthYear, child1TaxBonusAnnual, child2TaxBonusAnnual,
                        child3PlusTaxBonusAnnual, childToddlerMonthly, childPreschoolMonthly, childSchoolMonthly,
                        childTeenMonthly, childUniMonthly, rentGrowthPct, monteCarloN, monteCarloVolatilityPct,
                        monteCarloSeed, customExpensesJson, customGoalsJson, customLumpSumsJson, deletedCategoriesJson
                    )
                    SELECT
                        id, baseYear, primaryAge, primaryName, spouseName, isSingleHousehold, dcaAnnualGrowthPct,
                        vSalary, vBonusAnnual, vMealVouchersMonthly, vOtherInflowsMonthly, eReturnYear, eStartingSalary,
                        eBonusAnnual, eSalaryGrowthPct, eReinvestedPct, eParentalAllowanceMonthly, eLecturingMonthly,
                        eIncludeLecturing, familyGiftMonthly, annualOtherGifts, lumpSumYear, lumpSumAmount,
                        lumpSumInclude, liquidPortfolioCurrent, dpsBalanceCurrent, dipBalanceCurrent, portuDcaMonthly,
                        portfolioNominalReturnPct, dpsOwnContributionMonthly, dpsGrossReturnPct, dpsAnnualFeePct,
                        dipContributionMonthly, employerRetirementAnnual, eLiquidPortfolioCurrent, ePortuDcaMonthly,
                        eDpsBalanceCurrent, eDpsOwnContributionMonthly, eDipBalanceCurrent, eDipContributionMonthly,
                        eEmployerRetirementAnnual, emergencyReserveCurrent, emergencyReserveTarget,
                        lifestyleCostAtFireMonthly, statePensionMonthly, statePensionAge, safeWithdrawalRatePct,
                        safetyBufferPct, cpiInflationPct, fireTargetOverride, rentMonthly, groceriesMonthly,
                        cafesMonthly, therapyMonthly, charityMonthly, entertainmentMonthly, transportMonthly,
                        subscriptionsMonthly, otherDiscretionaryMonthly, taxRatePct, taxRateSecondPct,
                        taxSecondBracketThresholdAnnual, taxpayerCreditAnnual, taxDeductionCeilingAnnual,
                        spouseTaxCreditAnnual, spouseIncomeLimitAnnual, includeSpouseCredit, hasChildUnder3,
                        minWageMonthly, dpsDeductionThresholdMonthly, dpsStandardSubsidyMaxMonthly,
                        dpsYouthSubsidyMaxMonthly, dpsMinDepositForSubsidy, dpsYouthAgeLimit,
                        dpsSubsidyRateStandardPct, dpsSubsidyRateYouthPct, childExpensesEnabled, child1Enabled,
                        child1BirthYear, child2Enabled, child2BirthYear, child1TaxBonusAnnual, child2TaxBonusAnnual,
                        child3PlusTaxBonusAnnual, childToddlerMonthly, childPreschoolMonthly, childSchoolMonthly,
                        childTeenMonthly, childUniMonthly, rentGrowthPct, monteCarloN, monteCarloVolatilityPct,
                        monteCarloSeed, customExpensesJson, customGoalsJson, customLumpSumsJson, deletedCategoriesJson
                    FROM app_settings
                """.trimIndent())

                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "martinu_financials_db"
                )
                    .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
