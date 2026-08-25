package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingsEntity::class, LedgerEntryEntity::class, ActionStateEntity::class],
    version = 19,
    exportSchema = true
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
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloVolatilityPct REAL NOT NULL DEFAULT 15.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloSeed INTEGER NOT NULL DEFAULT 42")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN primaryName TEXT NOT NULL DEFAULT 'Václav'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN spouseName TEXT NOT NULL DEFAULT 'Eleonora'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN isSingleHousehold INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN dcaAnnualGrowthPct REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Check existing columns in app_settings to handle any previous column naming safely
                    val cursor = db.query("PRAGMA table_info(app_settings)")
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        val idx = cursor.getColumnIndex("name")
                        if (idx != -1) {
                            columns.add(cursor.getString(idx))
                        }
                    }
                    cursor.close()

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
                            eReturnMonth INTEGER NOT NULL,
                            eStartingSalary REAL NOT NULL,
                            eBonusAnnual REAL NOT NULL,
                            eSalaryGrowthPct REAL NOT NULL,
                            eReinvestedPct REAL NOT NULL,
                            eParentalAllowanceMonthly REAL NOT NULL,
                            eLecturingMonthly REAL NOT NULL,
                            eIncludeLecturing INTEGER NOT NULL,
                            eOtherInflowsMonthly REAL NOT NULL,
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

                    fun colOr(colName: String, fallbackExpr: String): String =
                        if (columns.contains(colName)) colName else fallbackExpr

                    val parentalAllowanceCol = when {
                        columns.contains("eParentalAllowanceMonthly") -> "eParentalAllowanceMonthly"
                        columns.contains("eParentalBenefitTotal") -> "eParentalBenefitTotal"
                        else -> "15000.0"
                    }

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
                            ${colOr("id", "1")},
                            ${colOr("baseYear", "2026")},
                            ${colOr("primaryAge", "28")},
                            ${colOr("primaryName", "'Václav'")},
                            ${colOr("spouseName", "'Eleonora'")},
                            ${colOr("isSingleHousehold", "0")},
                            ${colOr("dcaAnnualGrowthPct", "0.0")},
                            ${colOr("vSalary", "50000.0")},
                            ${colOr("vBonusAnnual", "0.0")},
                            ${colOr("vMealVouchersMonthly", "2500.0")},
                            ${colOr("vOtherInflowsMonthly", "0.0")},
                            ${colOr("eReturnYear", "2028")},
                            ${colOr("eReturnMonth", "1")},
                            ${colOr("eStartingSalary", "40000.0")},
                            ${colOr("eBonusAnnual", "0.0")},
                            ${colOr("eSalaryGrowthPct", "3.0")},
                            ${colOr("eReinvestedPct", "80.0")},
                            $parentalAllowanceCol,
                            ${colOr("eLecturingMonthly", "8000.0")},
                            ${colOr("eIncludeLecturing", "1")},
                            ${colOr("eOtherInflowsMonthly", "0.0")},
                            ${colOr("familyGiftMonthly", "0.0")},
                            ${colOr("annualOtherGifts", "0.0")},
                            ${colOr("lumpSumYear", "2030")},
                            ${colOr("lumpSumAmount", "0.0")},
                            ${colOr("lumpSumInclude", "0")},
                            ${colOr("liquidPortfolioCurrent", "350000.0")},
                            ${colOr("dpsBalanceCurrent", "45000.0")},
                            ${colOr("dipBalanceCurrent", "0.0")},
                            ${colOr("portuDcaMonthly", "15000.0")},
                            ${colOr("portfolioNominalReturnPct", "7.0")},
                            ${colOr("dpsOwnContributionMonthly", "1700.0")},
                            ${colOr("dpsGrossReturnPct", "7.0")},
                            ${colOr("dpsAnnualFeePct", "0.5")},
                            ${colOr("dipContributionMonthly", "4000.0")},
                            ${colOr("employerRetirementAnnual", "50000.0")},
                            ${colOr("eLiquidPortfolioCurrent", "0.0")},
                            ${colOr("ePortuDcaMonthly", "0.0")},
                            ${colOr("eDpsBalanceCurrent", "0.0")},
                            ${colOr("eDpsOwnContributionMonthly", "0.0")},
                            ${colOr("eDipBalanceCurrent", "0.0")},
                            ${colOr("eDipContributionMonthly", "0.0")},
                            ${colOr("eEmployerRetirementAnnual", "0.0")},
                            ${colOr("emergencyReserveCurrent", "300000.0")},
                            ${colOr("emergencyReserveTarget", "300000.0")},
                            ${colOr("lifestyleCostAtFireMonthly", "50000.0")},
                            ${colOr("statePensionMonthly", "20000.0")},
                            ${colOr("statePensionAge", "65")},
                            ${colOr("safeWithdrawalRatePct", "4.0")},
                            ${colOr("safetyBufferPct", "10.0")},
                            ${colOr("cpiInflationPct", "3.0")},
                            ${colOr("fireTargetOverride", "0.0")},
                            ${colOr("rentMonthly", "22000.0")},
                            ${colOr("groceriesMonthly", "6000.0")},
                            ${colOr("cafesMonthly", "2500.0")},
                            ${colOr("therapyMonthly", "2000.0")},
                            ${colOr("charityMonthly", "1000.0")},
                            ${colOr("entertainmentMonthly", "1500.0")},
                            ${colOr("transportMonthly", "800.0")},
                            ${colOr("subscriptionsMonthly", "750.0")},
                            ${colOr("otherDiscretionaryMonthly", "2000.0")},
                            ${colOr("taxRatePct", "15.0")},
                            ${colOr("taxRateSecondPct", "23.0")},
                            ${colOr("taxSecondBracketThresholdAnnual", "1610496.0")},
                            ${colOr("taxpayerCreditAnnual", "30840.0")},
                            ${colOr("taxDeductionCeilingAnnual", "48000.0")},
                            ${colOr("spouseTaxCreditAnnual", "24840.0")},
                            ${colOr("spouseIncomeLimitAnnual", "68000.0")},
                            ${colOr("includeSpouseCredit", "1")},
                            ${colOr("hasChildUnder3", "1")},
                            ${colOr("minWageMonthly", "20800.0")},
                            ${colOr("dpsDeductionThresholdMonthly", "1700.0")},
                            ${colOr("dpsStandardSubsidyMaxMonthly", "340.0")},
                            ${colOr("dpsYouthSubsidyMaxMonthly", "680.0")},
                            ${colOr("dpsMinDepositForSubsidy", "500.0")},
                            ${colOr("dpsYouthAgeLimit", "30")},
                            ${colOr("dpsSubsidyRateStandardPct", "20.0")},
                            ${colOr("dpsSubsidyRateYouthPct", "40.0")},
                            ${colOr("childExpensesEnabled", "1")},
                            ${colOr("child1Enabled", "1")},
                            ${colOr("child1BirthYear", "2024")},
                            ${colOr("child2Enabled", "0")},
                            ${colOr("child2BirthYear", "2026")},
                            ${colOr("child1TaxBonusAnnual", "15204.0")},
                            ${colOr("child2TaxBonusAnnual", "22320.0")},
                            ${colOr("child3PlusTaxBonusAnnual", "27840.0")},
                            ${colOr("childToddlerMonthly", "4000.0")},
                            ${colOr("childPreschoolMonthly", "5000.0")},
                            ${colOr("childSchoolMonthly", "7000.0")},
                            ${colOr("childTeenMonthly", "10000.0")},
                            ${colOr("childUniMonthly", "8000.0")},
                            ${colOr("rentGrowthPct", "3.0")},
                            ${colOr("monteCarloN", "250")},
                            ${colOr("monteCarloVolatilityPct", "15.0")},
                            ${colOr("monteCarloSeed", "42")},
                            ${colOr("customExpensesJson", "'[]'")},
                            ${colOr("customGoalsJson", "'[]'")},
                            ${colOr("customLumpSumsJson", "'[]'")},
                            ${colOr("deletedCategoriesJson", "'[]'")}
                        FROM app_settings
                    """.trimIndent())

                    db.execSQL("DROP TABLE app_settings")
                    db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        db.execSQL("DROP TABLE IF EXISTS app_settings_new")
                    } catch (_: Exception) {}
                }
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN eReturnMonth INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN eOtherInflowsMonthly REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN employerRetirementMonthly REAL NOT NULL DEFAULT 2800.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN eEmployerRetirementMonthly REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN vStatePensionMonthly REAL NOT NULL DEFAULT 12000.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN eStatePensionMonthly REAL NOT NULL DEFAULT 12000.0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN vStatePensionAge INTEGER NOT NULL DEFAULT 65")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN eStatePensionAge INTEGER NOT NULL DEFAULT 65")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "martinu_financials_db"
                )
                    .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
