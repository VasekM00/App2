package com.example.util

import com.example.data.SettingsEntity
import org.json.JSONObject

object BackupManager {

    fun serializeSettingsToJson(s: SettingsEntity): String {
        val json = JSONObject()
        json.put("baseYear", s.baseYear)
        json.put("primaryAge", s.primaryAge)
        json.put("primaryName", s.primaryName)
        json.put("spouseName", s.spouseName)
        json.put("isSingleHousehold", s.isSingleHousehold)
        json.put("dcaAnnualGrowthPct", s.dcaAnnualGrowthPct)
        
        // Income & Work
        json.put("vSalary", s.vSalary)
        json.put("vMealVouchersMonthly", s.vMealVouchersMonthly)
        json.put("vOtherInflowsMonthly", s.vOtherInflowsMonthly)
        json.put("eReturnYear", s.eReturnYear)
        json.put("eReturnMonth", s.eReturnMonth)
        json.put("eStartingSalary", s.eStartingSalary)
        json.put("eSalaryGrowthPct", s.eSalaryGrowthPct)
        json.put("eReinvestedPct", s.eReinvestedPct)
        json.put("eParentalAllowanceMonthly", s.eParentalAllowanceMonthly)
        json.put("eLecturingMonthly", s.eLecturingMonthly)
        json.put("eIncludeLecturing", s.eIncludeLecturing)
        json.put("eOtherInflowsMonthly", s.eOtherInflowsMonthly)
        json.put("familyGiftMonthly", s.familyGiftMonthly)
        json.put("lumpSumYear", s.lumpSumYear)
        json.put("lumpSumAmount", s.lumpSumAmount)
        json.put("lumpSumInclude", s.lumpSumInclude)

        // Balances & Portfolios
        json.put("liquidPortfolioCurrent", s.liquidPortfolioCurrent)
        json.put("dpsBalanceCurrent", s.dpsBalanceCurrent)
        json.put("dipBalanceCurrent", s.dipBalanceCurrent)
        json.put("eLiquidPortfolioCurrent", s.eLiquidPortfolioCurrent)
        json.put("eDpsBalanceCurrent", s.eDpsBalanceCurrent)
        json.put("eDipBalanceCurrent", s.eDipBalanceCurrent)
        json.put("emergencyReserveCurrent", s.emergencyReserveCurrent)
        json.put("emergencyReserveTarget", s.emergencyReserveTarget)

        // Monthly DCA Flow
        json.put("portuDcaMonthly", s.portuDcaMonthly)
        json.put("dpsOwnContributionMonthly", s.dpsOwnContributionMonthly)
        json.put("dipContributionMonthly", s.dipContributionMonthly)
        json.put("employerRetirementMonthly", s.employerRetirementMonthly)
        json.put("ePortuDcaMonthly", s.ePortuDcaMonthly)
        json.put("eDpsOwnContributionMonthly", s.eDpsOwnContributionMonthly)
        json.put("eDipContributionMonthly", s.eDipContributionMonthly)
        json.put("eEmployerRetirementMonthly", s.eEmployerRetirementMonthly)

        // Core Assumptions & FIRE Targets
        json.put("portfolioNominalReturnPct", s.portfolioNominalReturnPct)
        json.put("cpiInflationPct", s.cpiInflationPct)
        json.put("safeWithdrawalRatePct", s.safeWithdrawalRatePct)
        json.put("safetyBufferPct", s.safetyBufferPct)
        json.put("fireTargetOverride", s.fireTargetOverride)
        json.put("statePensionAge", s.statePensionAge)
        json.put("vStatePensionMonthly", s.vStatePensionMonthly)
        json.put("eStatePensionMonthly", s.eStatePensionMonthly)
        json.put("lifestyleCostAtFireMonthly", s.lifestyleCostAtFireMonthly)

        // Living Expenses
        json.put("rentMonthly", s.rentMonthly)
        json.put("groceriesMonthly", s.groceriesMonthly)
        json.put("cafesMonthly", s.cafesMonthly)
        json.put("therapyMonthly", s.therapyMonthly)
        json.put("charityMonthly", s.charityMonthly)
        json.put("entertainmentMonthly", s.entertainmentMonthly)
        json.put("transportMonthly", s.transportMonthly)
        json.put("subscriptionsMonthly", s.subscriptionsMonthly)
        json.put("otherDiscretionaryMonthly", s.otherDiscretionaryMonthly)
        json.put("rentGrowthPct", s.rentGrowthPct)
        json.put("customExpensesJson", s.customExpensesJson)
        json.put("customGoalsJson", s.customGoalsJson)
        json.put("customLumpSumsJson", s.customLumpSumsJson)
        json.put("deletedCategoriesJson", s.deletedCategoriesJson)

        // Children & Family
        json.put("childExpensesEnabled", s.childExpensesEnabled)
        json.put("child1Enabled", s.child1Enabled)
        json.put("child1BirthYear", s.child1BirthYear)
        json.put("child1TaxBonusAnnual", s.child1TaxBonusAnnual)
        json.put("child2Enabled", s.child2Enabled)
        json.put("child2BirthYear", s.child2BirthYear)
        json.put("child2TaxBonusAnnual", s.child2TaxBonusAnnual)
        json.put("child3PlusTaxBonusAnnual", s.child3PlusTaxBonusAnnual)
        json.put("childToddlerMonthly", s.childToddlerMonthly)
        json.put("childPreschoolMonthly", s.childPreschoolMonthly)
        json.put("childSchoolMonthly", s.childSchoolMonthly)
        json.put("childTeenMonthly", s.childTeenMonthly)
        json.put("childUniMonthly", s.childUniMonthly)

        // Statutory & Tax parameters (Czech Tax Act No. 586/1992 Coll.)
        json.put("taxRatePct", s.taxRatePct)
        json.put("taxRateSecondPct", s.taxRateSecondPct)
        json.put("taxSecondBracketThresholdAnnual", s.taxSecondBracketThresholdAnnual)
        json.put("taxpayerCreditAnnual", s.taxpayerCreditAnnual)
        json.put("taxDeductionCeilingAnnual", s.taxDeductionCeilingAnnual)
        json.put("spouseTaxCreditAnnual", s.spouseTaxCreditAnnual)
        json.put("spouseIncomeLimitAnnual", s.spouseIncomeLimitAnnual)
        json.put("includeSpouseCredit", s.includeSpouseCredit)
        json.put("hasChildUnder3", s.hasChildUnder3)
        json.put("minWageMonthly", s.minWageMonthly)

        // DPS Reform parameters (Lepší penzijko)
        json.put("dpsGrossReturnPct", s.dpsGrossReturnPct)
        json.put("dpsAnnualFeePct", s.dpsAnnualFeePct)
        json.put("dpsDeductionThresholdMonthly", s.dpsDeductionThresholdMonthly)
        json.put("dpsStandardSubsidyMaxMonthly", s.dpsStandardSubsidyMaxMonthly)
        json.put("dpsYouthSubsidyMaxMonthly", s.dpsYouthSubsidyMaxMonthly)
        json.put("dpsMinDepositForSubsidy", s.dpsMinDepositForSubsidy)
        json.put("dpsYouthAgeLimit", s.dpsYouthAgeLimit)
        json.put("dpsSubsidyRateStandardPct", s.dpsSubsidyRateStandardPct)
        json.put("dpsSubsidyRateYouthPct", s.dpsSubsidyRateYouthPct)

        // Monte Carlo configuration
        json.put("monteCarloN", s.monteCarloN)
        json.put("monteCarloVolatilityPct", s.monteCarloVolatilityPct)
        json.put("monteCarloSeed", s.monteCarloSeed)

        return json.toString()
    }

    fun deserializeSettingsFromJson(jsonStr: String, fallback: SettingsEntity): SettingsEntity? {
        return try {
            val json = JSONObject(jsonStr.trim())
            val restored = fallback.copy(
                baseYear = json.optInt("baseYear", fallback.baseYear),
                primaryAge = json.optInt("primaryAge", fallback.primaryAge),
                primaryName = json.optString("primaryName", fallback.primaryName),
                spouseName = json.optString("spouseName", fallback.spouseName),
                isSingleHousehold = json.optBoolean("isSingleHousehold", fallback.isSingleHousehold),
                dcaAnnualGrowthPct = json.optDouble("dcaAnnualGrowthPct", fallback.dcaAnnualGrowthPct),

                vSalary = json.optDouble("vSalary", fallback.vSalary),
                vMealVouchersMonthly = json.optDouble("vMealVouchersMonthly", fallback.vMealVouchersMonthly),
                vOtherInflowsMonthly = json.optDouble("vOtherInflowsMonthly", fallback.vOtherInflowsMonthly),
                eReturnYear = json.optInt("eReturnYear", fallback.eReturnYear),
                eReturnMonth = json.optInt("eReturnMonth", fallback.eReturnMonth),
                eStartingSalary = json.optDouble("eStartingSalary", fallback.eStartingSalary),
                eSalaryGrowthPct = json.optDouble("eSalaryGrowthPct", fallback.eSalaryGrowthPct),
                eReinvestedPct = json.optDouble("eReinvestedPct", fallback.eReinvestedPct),
                eParentalAllowanceMonthly = json.optDouble("eParentalAllowanceMonthly", fallback.eParentalAllowanceMonthly),
                eLecturingMonthly = json.optDouble("eLecturingMonthly", fallback.eLecturingMonthly),
                eIncludeLecturing = json.optBoolean("eIncludeLecturing", fallback.eIncludeLecturing),
                eOtherInflowsMonthly = json.optDouble("eOtherInflowsMonthly", fallback.eOtherInflowsMonthly),
                familyGiftMonthly = json.optDouble("familyGiftMonthly", fallback.familyGiftMonthly),
                lumpSumYear = json.optInt("lumpSumYear", fallback.lumpSumYear),
                lumpSumAmount = json.optDouble("lumpSumAmount", fallback.lumpSumAmount),
                lumpSumInclude = json.optBoolean("lumpSumInclude", fallback.lumpSumInclude),

                liquidPortfolioCurrent = json.optDouble("liquidPortfolioCurrent", fallback.liquidPortfolioCurrent),
                dpsBalanceCurrent = json.optDouble("dpsBalanceCurrent", fallback.dpsBalanceCurrent),
                dipBalanceCurrent = json.optDouble("dipBalanceCurrent", fallback.dipBalanceCurrent),
                eLiquidPortfolioCurrent = json.optDouble("eLiquidPortfolioCurrent", fallback.eLiquidPortfolioCurrent),
                eDpsBalanceCurrent = json.optDouble("eDpsBalanceCurrent", fallback.eDpsBalanceCurrent),
                eDipBalanceCurrent = json.optDouble("eDipBalanceCurrent", fallback.eDipBalanceCurrent),
                emergencyReserveCurrent = json.optDouble("emergencyReserveCurrent", fallback.emergencyReserveCurrent),
                emergencyReserveTarget = json.optDouble("emergencyReserveTarget", fallback.emergencyReserveTarget),

                portuDcaMonthly = json.optDouble("portuDcaMonthly", fallback.portuDcaMonthly),
                dpsOwnContributionMonthly = json.optDouble("dpsOwnContributionMonthly", fallback.dpsOwnContributionMonthly),
                dipContributionMonthly = json.optDouble("dipContributionMonthly", fallback.dipContributionMonthly),
                employerRetirementMonthly = json.optDouble("employerRetirementMonthly", fallback.employerRetirementMonthly),
                ePortuDcaMonthly = json.optDouble("ePortuDcaMonthly", fallback.ePortuDcaMonthly),
                eDpsOwnContributionMonthly = json.optDouble("eDpsOwnContributionMonthly", fallback.eDpsOwnContributionMonthly),
                eDipContributionMonthly = json.optDouble("eDipContributionMonthly", fallback.eDipContributionMonthly),
                eEmployerRetirementMonthly = json.optDouble("eEmployerRetirementMonthly", fallback.eEmployerRetirementMonthly),

                portfolioNominalReturnPct = json.optDouble("portfolioNominalReturnPct", fallback.portfolioNominalReturnPct),
                cpiInflationPct = json.optDouble("cpiInflationPct", fallback.cpiInflationPct),
                safeWithdrawalRatePct = json.optDouble("safeWithdrawalRatePct", fallback.safeWithdrawalRatePct),
                safetyBufferPct = json.optDouble("safetyBufferPct", fallback.safetyBufferPct),
                fireTargetOverride = json.optDouble("fireTargetOverride", fallback.fireTargetOverride),
                statePensionAge = json.optInt("statePensionAge", fallback.statePensionAge),
                vStatePensionMonthly = json.optDouble("vStatePensionMonthly", json.optDouble("statePensionMonthly", fallback.vStatePensionMonthly)),
                eStatePensionMonthly = json.optDouble("eStatePensionMonthly", fallback.eStatePensionMonthly),
                lifestyleCostAtFireMonthly = json.optDouble("lifestyleCostAtFireMonthly", fallback.lifestyleCostAtFireMonthly),

                rentMonthly = json.optDouble("rentMonthly", fallback.rentMonthly),
                groceriesMonthly = json.optDouble("groceriesMonthly", fallback.groceriesMonthly),
                cafesMonthly = json.optDouble("cafesMonthly", fallback.cafesMonthly),
                therapyMonthly = json.optDouble("therapyMonthly", fallback.therapyMonthly),
                charityMonthly = json.optDouble("charityMonthly", fallback.charityMonthly),
                entertainmentMonthly = json.optDouble("entertainmentMonthly", fallback.entertainmentMonthly),
                transportMonthly = json.optDouble("transportMonthly", fallback.transportMonthly),
                subscriptionsMonthly = json.optDouble("subscriptionsMonthly", fallback.subscriptionsMonthly),
                otherDiscretionaryMonthly = json.optDouble("otherDiscretionaryMonthly", fallback.otherDiscretionaryMonthly),
                rentGrowthPct = json.optDouble("rentGrowthPct", fallback.rentGrowthPct),
                customExpensesJson = json.optString("customExpensesJson", fallback.customExpensesJson),
                customGoalsJson = json.optString("customGoalsJson", fallback.customGoalsJson),
                customLumpSumsJson = json.optString("customLumpSumsJson", fallback.customLumpSumsJson),
                deletedCategoriesJson = json.optString("deletedCategoriesJson", fallback.deletedCategoriesJson),

                childExpensesEnabled = json.optBoolean("childExpensesEnabled", fallback.childExpensesEnabled),
                child1Enabled = json.optBoolean("child1Enabled", fallback.child1Enabled),
                child1BirthYear = json.optInt("child1BirthYear", fallback.child1BirthYear),
                child1TaxBonusAnnual = json.optDouble("child1TaxBonusAnnual", fallback.child1TaxBonusAnnual),
                child2Enabled = json.optBoolean("child2Enabled", fallback.child2Enabled),
                child2BirthYear = json.optInt("child2BirthYear", fallback.child2BirthYear),
                child2TaxBonusAnnual = json.optDouble("child2TaxBonusAnnual", fallback.child2TaxBonusAnnual),
                child3PlusTaxBonusAnnual = json.optDouble("child3PlusTaxBonusAnnual", fallback.child3PlusTaxBonusAnnual),
                childToddlerMonthly = json.optDouble("childToddlerMonthly", fallback.childToddlerMonthly),
                childPreschoolMonthly = json.optDouble("childPreschoolMonthly", fallback.childPreschoolMonthly),
                childSchoolMonthly = json.optDouble("childSchoolMonthly", fallback.childSchoolMonthly),
                childTeenMonthly = json.optDouble("childTeenMonthly", fallback.childTeenMonthly),
                childUniMonthly = json.optDouble("childUniMonthly", fallback.childUniMonthly),

                // Statutory & Tax parameters
                taxRatePct = json.optDouble("taxRatePct", fallback.taxRatePct),
                taxRateSecondPct = json.optDouble("taxRateSecondPct", fallback.taxRateSecondPct),
                taxSecondBracketThresholdAnnual = json.optDouble("taxSecondBracketThresholdAnnual", fallback.taxSecondBracketThresholdAnnual),
                taxpayerCreditAnnual = json.optDouble("taxpayerCreditAnnual", fallback.taxpayerCreditAnnual),
                taxDeductionCeilingAnnual = json.optDouble("taxDeductionCeilingAnnual", fallback.taxDeductionCeilingAnnual),
                spouseTaxCreditAnnual = json.optDouble("spouseTaxCreditAnnual", fallback.spouseTaxCreditAnnual),
                spouseIncomeLimitAnnual = json.optDouble("spouseIncomeLimitAnnual", fallback.spouseIncomeLimitAnnual),
                includeSpouseCredit = json.optBoolean("includeSpouseCredit", fallback.includeSpouseCredit),
                hasChildUnder3 = json.optBoolean("hasChildUnder3", fallback.hasChildUnder3),
                minWageMonthly = json.optDouble("minWageMonthly", fallback.minWageMonthly),

                // DPS Reform parameters
                dpsGrossReturnPct = json.optDouble("dpsGrossReturnPct", fallback.dpsGrossReturnPct),
                dpsAnnualFeePct = json.optDouble("dpsAnnualFeePct", fallback.dpsAnnualFeePct),
                dpsDeductionThresholdMonthly = json.optDouble("dpsDeductionThresholdMonthly", fallback.dpsDeductionThresholdMonthly),
                dpsStandardSubsidyMaxMonthly = json.optDouble("dpsStandardSubsidyMaxMonthly", fallback.dpsStandardSubsidyMaxMonthly),
                dpsYouthSubsidyMaxMonthly = json.optDouble("dpsYouthSubsidyMaxMonthly", fallback.dpsYouthSubsidyMaxMonthly),
                dpsMinDepositForSubsidy = json.optDouble("dpsMinDepositForSubsidy", fallback.dpsMinDepositForSubsidy),
                dpsYouthAgeLimit = json.optInt("dpsYouthAgeLimit", fallback.dpsYouthAgeLimit),
                dpsSubsidyRateStandardPct = json.optDouble("dpsSubsidyRateStandardPct", fallback.dpsSubsidyRateStandardPct),
                dpsSubsidyRateYouthPct = json.optDouble("dpsSubsidyRateYouthPct", fallback.dpsSubsidyRateYouthPct),

                // Monte Carlo configuration
                monteCarloN = json.optInt("monteCarloN", fallback.monteCarloN),
                monteCarloVolatilityPct = json.optDouble("monteCarloVolatilityPct", fallback.monteCarloVolatilityPct),
                monteCarloSeed = json.optLong("monteCarloSeed", fallback.monteCarloSeed)
            )
            sanitizeRestored(restored, fallback)
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeRestored(s: SettingsEntity, f: SettingsEntity): SettingsEntity {
        fun money(v: Double, fallback: Double): Double = if (v.isFinite() && v in 0.0..1.0e12) v else fallback
        fun pct(v: Double, fallback: Double): Double = if (v.isFinite() && v in 0.0..100.0) v else fallback
        fun year(v: Int, fallback: Int): Int = if (v in 2000..2200) v else fallback
        return s.copy(
            primaryAge = if (s.primaryAge in 15..80) s.primaryAge else f.primaryAge,
            baseYear = year(s.baseYear, f.baseYear),
            eReturnYear = year(s.eReturnYear, f.eReturnYear),
            eReturnMonth = if (s.eReturnMonth in 1..12) s.eReturnMonth else f.eReturnMonth,
            lumpSumYear = year(s.lumpSumYear, f.lumpSumYear),
            statePensionAge = if (s.statePensionAge in 55..75) s.statePensionAge else f.statePensionAge,
            child1BirthYear = year(s.child1BirthYear, f.child1BirthYear),
            child2BirthYear = year(s.child2BirthYear, f.child2BirthYear),
            dpsYouthAgeLimit = if (s.dpsYouthAgeLimit in 18..40) s.dpsYouthAgeLimit else f.dpsYouthAgeLimit,
            monteCarloN = if (s.monteCarloN in 100..1000) s.monteCarloN else f.monteCarloN,
            vSalary = money(s.vSalary, f.vSalary),
            vMealVouchersMonthly = money(s.vMealVouchersMonthly, f.vMealVouchersMonthly),
            vOtherInflowsMonthly = money(s.vOtherInflowsMonthly, f.vOtherInflowsMonthly),
            eStartingSalary = money(s.eStartingSalary, f.eStartingSalary),
            eSalaryGrowthPct = pct(s.eSalaryGrowthPct, f.eSalaryGrowthPct),
            eReinvestedPct = pct(s.eReinvestedPct, f.eReinvestedPct),
            eParentalAllowanceMonthly = money(s.eParentalAllowanceMonthly, f.eParentalAllowanceMonthly),
            eLecturingMonthly = money(s.eLecturingMonthly, f.eLecturingMonthly),
            eOtherInflowsMonthly = money(s.eOtherInflowsMonthly, f.eOtherInflowsMonthly),
            familyGiftMonthly = money(s.familyGiftMonthly, f.familyGiftMonthly),
            lumpSumAmount = money(s.lumpSumAmount, f.lumpSumAmount),
            liquidPortfolioCurrent = money(s.liquidPortfolioCurrent, f.liquidPortfolioCurrent),
            dpsBalanceCurrent = money(s.dpsBalanceCurrent, f.dpsBalanceCurrent),
            dipBalanceCurrent = money(s.dipBalanceCurrent, f.dipBalanceCurrent),
            eLiquidPortfolioCurrent = money(s.eLiquidPortfolioCurrent, f.eLiquidPortfolioCurrent),
            eDpsBalanceCurrent = money(s.eDpsBalanceCurrent, f.eDpsBalanceCurrent),
            eDipBalanceCurrent = money(s.eDipBalanceCurrent, f.eDipBalanceCurrent),
            emergencyReserveCurrent = money(s.emergencyReserveCurrent, f.emergencyReserveCurrent),
            emergencyReserveTarget = money(s.emergencyReserveTarget, f.emergencyReserveTarget),
            portuDcaMonthly = money(s.portuDcaMonthly, f.portuDcaMonthly),
            dpsOwnContributionMonthly = money(s.dpsOwnContributionMonthly, f.dpsOwnContributionMonthly),
            dipContributionMonthly = money(s.dipContributionMonthly, f.dipContributionMonthly),
            employerRetirementMonthly = money(s.employerRetirementMonthly, f.employerRetirementMonthly),
            ePortuDcaMonthly = money(s.ePortuDcaMonthly, f.ePortuDcaMonthly),
            eDpsOwnContributionMonthly = money(s.eDpsOwnContributionMonthly, f.eDpsOwnContributionMonthly),
            eDipContributionMonthly = money(s.eDipContributionMonthly, f.eDipContributionMonthly),
            eEmployerRetirementMonthly = money(s.eEmployerRetirementMonthly, f.eEmployerRetirementMonthly),
            portfolioNominalReturnPct = pct(s.portfolioNominalReturnPct, f.portfolioNominalReturnPct),
            dpsGrossReturnPct = pct(s.dpsGrossReturnPct, f.dpsGrossReturnPct),
            dpsAnnualFeePct = pct(s.dpsAnnualFeePct, f.dpsAnnualFeePct),
            cpiInflationPct = pct(s.cpiInflationPct, f.cpiInflationPct),
            safeWithdrawalRatePct = pct(s.safeWithdrawalRatePct, f.safeWithdrawalRatePct),
            safetyBufferPct = pct(s.safetyBufferPct, f.safetyBufferPct),
            rentGrowthPct = pct(s.rentGrowthPct, f.rentGrowthPct),
            dcaAnnualGrowthPct = pct(s.dcaAnnualGrowthPct, f.dcaAnnualGrowthPct),
            vStatePensionMonthly = money(s.vStatePensionMonthly, f.vStatePensionMonthly),
            eStatePensionMonthly = money(s.eStatePensionMonthly, f.eStatePensionMonthly),
            lifestyleCostAtFireMonthly = money(s.lifestyleCostAtFireMonthly, f.lifestyleCostAtFireMonthly),
            fireTargetOverride = money(s.fireTargetOverride, f.fireTargetOverride),
            rentMonthly = money(s.rentMonthly, f.rentMonthly),
            groceriesMonthly = money(s.groceriesMonthly, f.groceriesMonthly),
            cafesMonthly = money(s.cafesMonthly, f.cafesMonthly),
            therapyMonthly = money(s.therapyMonthly, f.therapyMonthly),
            charityMonthly = money(s.charityMonthly, f.charityMonthly),
            entertainmentMonthly = money(s.entertainmentMonthly, f.entertainmentMonthly),
            transportMonthly = money(s.transportMonthly, f.transportMonthly),
            subscriptionsMonthly = money(s.subscriptionsMonthly, f.subscriptionsMonthly),
            otherDiscretionaryMonthly = money(s.otherDiscretionaryMonthly, f.otherDiscretionaryMonthly),
            taxRatePct = pct(s.taxRatePct, f.taxRatePct),
            taxRateSecondPct = pct(s.taxRateSecondPct, f.taxRateSecondPct),
            taxSecondBracketThresholdAnnual = money(s.taxSecondBracketThresholdAnnual, f.taxSecondBracketThresholdAnnual),
            taxpayerCreditAnnual = money(s.taxpayerCreditAnnual, f.taxpayerCreditAnnual),
            taxDeductionCeilingAnnual = money(s.taxDeductionCeilingAnnual, f.taxDeductionCeilingAnnual),
            spouseTaxCreditAnnual = money(s.spouseTaxCreditAnnual, f.spouseTaxCreditAnnual),
            spouseIncomeLimitAnnual = money(s.spouseIncomeLimitAnnual, f.spouseIncomeLimitAnnual),
            minWageMonthly = money(s.minWageMonthly, f.minWageMonthly),
            dpsDeductionThresholdMonthly = money(s.dpsDeductionThresholdMonthly, f.dpsDeductionThresholdMonthly),
            dpsStandardSubsidyMaxMonthly = money(s.dpsStandardSubsidyMaxMonthly, f.dpsStandardSubsidyMaxMonthly),
            dpsYouthSubsidyMaxMonthly = money(s.dpsYouthSubsidyMaxMonthly, f.dpsYouthSubsidyMaxMonthly),
            dpsMinDepositForSubsidy = money(s.dpsMinDepositForSubsidy, f.dpsMinDepositForSubsidy),
            dpsSubsidyRateStandardPct = pct(s.dpsSubsidyRateStandardPct, f.dpsSubsidyRateStandardPct),
            dpsSubsidyRateYouthPct = pct(s.dpsSubsidyRateYouthPct, f.dpsSubsidyRateYouthPct),
            child1TaxBonusAnnual = money(s.child1TaxBonusAnnual, f.child1TaxBonusAnnual),
            child2TaxBonusAnnual = money(s.child2TaxBonusAnnual, f.child2TaxBonusAnnual),
            child3PlusTaxBonusAnnual = money(s.child3PlusTaxBonusAnnual, f.child3PlusTaxBonusAnnual),
            childToddlerMonthly = money(s.childToddlerMonthly, f.childToddlerMonthly),
            childPreschoolMonthly = money(s.childPreschoolMonthly, f.childPreschoolMonthly),
            childSchoolMonthly = money(s.childSchoolMonthly, f.childSchoolMonthly),
            childTeenMonthly = money(s.childTeenMonthly, f.childTeenMonthly),
            childUniMonthly = money(s.childUniMonthly, f.childUniMonthly),
            monteCarloVolatilityPct = pct(s.monteCarloVolatilityPct, f.monteCarloVolatilityPct)
        )
    }
}
