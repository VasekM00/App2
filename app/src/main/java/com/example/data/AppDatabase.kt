package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingsEntity::class, LedgerEntryEntity::class, ActionStateEntity::class],
    version = 21,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun actionStateDao(): ActionStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun recreateAppSettingsTable(db: SupportSQLiteDatabase) {
            try {
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
                        vMealVouchersMonthly REAL NOT NULL,
                        vOtherInflowsMonthly REAL NOT NULL,
                        eReturnYear INTEGER NOT NULL,
                        eReturnMonth INTEGER NOT NULL,
                        eStartingSalary REAL NOT NULL,
                        eSalaryGrowthPct REAL NOT NULL,
                        eReinvestedPct REAL NOT NULL,
                        eParentalAllowanceMonthly REAL NOT NULL,
                        eLecturingMonthly REAL NOT NULL,
                        eIncludeLecturing INTEGER NOT NULL,
                        eOtherInflowsMonthly REAL NOT NULL,
                        familyGiftMonthly REAL NOT NULL,
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
                        employerRetirementMonthly REAL NOT NULL,
                        eLiquidPortfolioCurrent REAL NOT NULL,
                        ePortuDcaMonthly REAL NOT NULL,
                        eDpsBalanceCurrent REAL NOT NULL,
                        eDpsOwnContributionMonthly REAL NOT NULL,
                        eDipBalanceCurrent REAL NOT NULL,
                        eDipContributionMonthly REAL NOT NULL,
                        eEmployerRetirementMonthly REAL NOT NULL,
                        emergencyReserveCurrent REAL NOT NULL,
                        emergencyReserveTarget REAL NOT NULL,
                        lifestyleCostAtFireMonthly REAL NOT NULL,
                        vStatePensionMonthly REAL NOT NULL,
                        eStatePensionMonthly REAL NOT NULL,
                        vStatePensionAge INTEGER NOT NULL,
                        eStatePensionAge INTEGER NOT NULL,
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
                    else -> "13000.0"
                }

                val vPensionAgeCol = when {
                    columns.contains("vStatePensionAge") -> "vStatePensionAge"
                    columns.contains("statePensionAge") -> "statePensionAge"
                    else -> "65"
                }

                val ePensionAgeCol = when {
                    columns.contains("eStatePensionAge") -> "eStatePensionAge"
                    columns.contains("statePensionAge") -> "statePensionAge"
                    else -> "65"
                }

                val vPensionMonthlyCol = when {
                    columns.contains("vStatePensionMonthly") -> "vStatePensionMonthly"
                    columns.contains("statePensionMonthly") -> "statePensionMonthly / 2.0"
                    else -> "12000.0"
                }

                val ePensionMonthlyCol = when {
                    columns.contains("eStatePensionMonthly") -> "eStatePensionMonthly"
                    columns.contains("statePensionMonthly") -> "statePensionMonthly / 2.0"
                    else -> "12000.0"
                }

                val employerRetirementMonthlyCol = when {
                    columns.contains("employerRetirementMonthly") -> "CASE WHEN employerRetirementMonthly > 230.0 AND employerRetirementMonthly < 235.0 THEN 2800.0 ELSE employerRetirementMonthly END"
                    columns.contains("employerRetirementAnnual") -> "CASE WHEN employerRetirementAnnual <= 5000.0 THEN employerRetirementAnnual ELSE (employerRetirementAnnual / 12.0) END"
                    else -> "2800.0"
                }

                val eEmployerRetirementMonthlyCol = when {
                    columns.contains("eEmployerRetirementMonthly") -> "eEmployerRetirementMonthly"
                    columns.contains("eEmployerRetirementAnnual") -> "(eEmployerRetirementAnnual / 12.0)"
                    else -> "0.0"
                }

                db.execSQL("""
                    INSERT INTO app_settings_new (
                        id, baseYear, primaryAge, primaryName, spouseName, isSingleHousehold, dcaAnnualGrowthPct,
                        vSalary, vMealVouchersMonthly, vOtherInflowsMonthly,
                        eReturnYear, eReturnMonth, eStartingSalary, eSalaryGrowthPct, eReinvestedPct,
                        eParentalAllowanceMonthly, eLecturingMonthly, eIncludeLecturing, eOtherInflowsMonthly,
                        familyGiftMonthly, lumpSumYear, lumpSumAmount, lumpSumInclude,
                        liquidPortfolioCurrent, dpsBalanceCurrent, dipBalanceCurrent, portuDcaMonthly,
                        portfolioNominalReturnPct, dpsOwnContributionMonthly, dpsGrossReturnPct, dpsAnnualFeePct,
                        dipContributionMonthly, employerRetirementMonthly,
                        eLiquidPortfolioCurrent, ePortuDcaMonthly, eDpsBalanceCurrent, eDpsOwnContributionMonthly,
                        eDipBalanceCurrent, eDipContributionMonthly, eEmployerRetirementMonthly,
                        emergencyReserveCurrent, emergencyReserveTarget, lifestyleCostAtFireMonthly,
                        vStatePensionMonthly, eStatePensionMonthly, vStatePensionAge, eStatePensionAge,
                        safeWithdrawalRatePct, safetyBufferPct, cpiInflationPct, fireTargetOverride,
                        rentMonthly, groceriesMonthly, cafesMonthly, therapyMonthly, charityMonthly,
                        entertainmentMonthly, transportMonthly, subscriptionsMonthly, otherDiscretionaryMonthly,
                        taxRatePct, taxRateSecondPct, taxSecondBracketThresholdAnnual,
                        taxpayerCreditAnnual, taxDeductionCeilingAnnual, spouseTaxCreditAnnual,
                        spouseIncomeLimitAnnual, includeSpouseCredit, hasChildUnder3, minWageMonthly,
                        dpsDeductionThresholdMonthly, dpsStandardSubsidyMaxMonthly, dpsYouthSubsidyMaxMonthly,
                        dpsMinDepositForSubsidy, dpsYouthAgeLimit, dpsSubsidyRateStandardPct, dpsSubsidyRateYouthPct,
                        childExpensesEnabled, child1Enabled, child1BirthYear, child2Enabled, child2BirthYear,
                        child1TaxBonusAnnual, child2TaxBonusAnnual, child3PlusTaxBonusAnnual,
                        childToddlerMonthly, childPreschoolMonthly, childSchoolMonthly, childTeenMonthly, childUniMonthly,
                        rentGrowthPct, monteCarloN, monteCarloVolatilityPct, monteCarloSeed,
                        customExpensesJson, customGoalsJson, customLumpSumsJson, deletedCategoriesJson
                    )
                    SELECT
                        ${colOr("id", "1")},
                        ${colOr("baseYear", "2026")},
                        ${colOr("primaryAge", "26")},
                        ${colOr("primaryName", "'Václav'")},
                        ${colOr("spouseName", "'Eleonora'")},
                        ${colOr("isSingleHousehold", "0")},
                        ${colOr("dcaAnnualGrowthPct", "0.0")},
                        ${colOr("vSalary", "33500.0")},
                        ${colOr("vMealVouchersMonthly", "2090.0")},
                        ${colOr("vOtherInflowsMonthly", "0.0")},
                        ${colOr("eReturnYear", "2029")},
                        ${colOr("eReturnMonth", "1")},
                        ${colOr("eStartingSalary", "22000.0")},
                        ${colOr("eSalaryGrowthPct", "3.0")},
                        ${colOr("eReinvestedPct", "75.0")},
                        $parentalAllowanceCol,
                        ${colOr("eLecturingMonthly", "6900.0")},
                        ${colOr("eIncludeLecturing", "1")},
                        ${colOr("eOtherInflowsMonthly", "0.0")},
                        ${colOr("familyGiftMonthly", "16000.0")},
                        ${colOr("lumpSumYear", "2030")},
                        ${colOr("lumpSumAmount", "500000.0")},
                        ${colOr("lumpSumInclude", "1")},
                        ${colOr("liquidPortfolioCurrent", "200000.0")},
                        ${colOr("dpsBalanceCurrent", "0.0")},
                        ${colOr("dipBalanceCurrent", "0.0")},
                        ${colOr("portuDcaMonthly", "11000.0")},
                        ${colOr("portfolioNominalReturnPct", "7.0")},
                        ${colOr("dpsOwnContributionMonthly", "1700.0")},
                        ${colOr("dpsGrossReturnPct", "6.0")},
                        ${colOr("dpsAnnualFeePct", "0.5")},
                        ${colOr("dipContributionMonthly", "1700.0")},
                        $employerRetirementMonthlyCol,
                        ${colOr("eLiquidPortfolioCurrent", "50000.0")},
                        ${colOr("ePortuDcaMonthly", "3000.0")},
                        ${colOr("eDpsBalanceCurrent", "0.0")},
                        ${colOr("eDpsOwnContributionMonthly", "0.0")},
                        ${colOr("eDipBalanceCurrent", "0.0")},
                        ${colOr("eDipContributionMonthly", "0.0")},
                        $eEmployerRetirementMonthlyCol,
                        ${colOr("emergencyReserveCurrent", "200000.0")},
                        ${colOr("emergencyReserveTarget", "250000.0")},
                        ${colOr("lifestyleCostAtFireMonthly", "0.0")},
                        $vPensionMonthlyCol,
                        $ePensionMonthlyCol,
                        $vPensionAgeCol,
                        $ePensionAgeCol,
                        ${colOr("safeWithdrawalRatePct", "4.0")},
                        ${colOr("safetyBufferPct", "10.0")},
                        ${colOr("cpiInflationPct", "2.8")},
                        ${colOr("fireTargetOverride", "0.0")},
                        ${colOr("rentMonthly", "21770.0")},
                        ${colOr("groceriesMonthly", "4800.0")},
                        ${colOr("cafesMonthly", "2250.0")},
                        ${colOr("therapyMonthly", "2000.0")},
                        ${colOr("charityMonthly", "2000.0")},
                        ${colOr("entertainmentMonthly", "1200.0")},
                        ${colOr("transportMonthly", "650.0")},
                        ${colOr("subscriptionsMonthly", "584.0")},
                        ${colOr("otherDiscretionaryMonthly", "1500.0")},
                        ${colOr("taxRatePct", "15.0")},
                        ${colOr("taxRateSecondPct", "23.0")},
                        ${colOr("taxSecondBracketThresholdAnnual", "1582812.0")},
                        ${colOr("taxpayerCreditAnnual", "30840.0")},
                        ${colOr("taxDeductionCeilingAnnual", "48000.0")},
                        ${colOr("spouseTaxCreditAnnual", "24840.0")},
                        ${colOr("spouseIncomeLimitAnnual", "68000.0")},
                        ${colOr("includeSpouseCredit", "1")},
                        ${colOr("hasChildUnder3", "1")},
                        ${colOr("minWageMonthly", "22400.0")},
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
                        ${colOr("child2Enabled", "1")},
                        ${colOr("child2BirthYear", "2027")},
                        ${colOr("child1TaxBonusAnnual", "15204.0")},
                        ${colOr("child2TaxBonusAnnual", "22320.0")},
                        ${colOr("child3PlusTaxBonusAnnual", "27840.0")},
                        ${colOr("childToddlerMonthly", "4800.0")},
                        ${colOr("childPreschoolMonthly", "6500.0")},
                        ${colOr("childSchoolMonthly", "8500.0")},
                        ${colOr("childTeenMonthly", "13000.0")},
                        ${colOr("childUniMonthly", "10000.0")},
                        ${colOr("rentGrowthPct", "4.0")},
                        ${colOr("monteCarloN", "400")},
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

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_19_21 = object : Migration(19, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_18_21 = object : Migration(18, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_17_21 = object : Migration(17, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        val MIGRATION_16_21 = object : Migration(16, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                recreateAppSettingsTable(db)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "martinu_financials_db"
                )
                    .addMigrations(
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_19_21, MIGRATION_18_21, MIGRATION_17_21, MIGRATION_16_21
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
