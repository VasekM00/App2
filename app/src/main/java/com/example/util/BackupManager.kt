package com.example.util

import com.example.data.SettingsEntity
import org.json.JSONObject

object BackupManager {

    fun serializeSettingsToJson(s: SettingsEntity): String {
        val json = JSONObject()
        json.put("baseYear", s.baseYear)
        json.put("primaryAge", s.primaryAge)
        
        // Income & Work
        json.put("vSalary", s.vSalary)
        json.put("vRaiseAnnual", s.vRaiseAnnual)
        json.put("vBonusAnnual", s.vBonusAnnual)
        json.put("vMealVouchersMonthly", s.vMealVouchersMonthly)
        json.put("eReturnYear", s.eReturnYear)
        json.put("eStartingSalary", s.eStartingSalary)
        json.put("eBonusAnnual", s.eBonusAnnual)
        json.put("eSalaryGrowthPct", s.eSalaryGrowthPct)
        json.put("eReinvestedPct", s.eReinvestedPct)
        json.put("eParentalAllowanceMonthly", s.eParentalAllowanceMonthly)
        json.put("eLecturingMonthly", s.eLecturingMonthly)
        json.put("eIncludeLecturing", s.eIncludeLecturing)
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
        json.put("employerRetirementAnnual", s.employerRetirementAnnual)
        json.put("ePortuDcaMonthly", s.ePortuDcaMonthly)
        json.put("eDpsOwnContributionMonthly", s.eDpsOwnContributionMonthly)
        json.put("eDipContributionMonthly", s.eDipContributionMonthly)
        json.put("eEmployerRetirementAnnual", s.eEmployerRetirementAnnual)

        // Core Assumptions & FIRE Targets
        json.put("portfolioNominalReturnPct", s.portfolioNominalReturnPct)
        json.put("cpiInflationPct", s.cpiInflationPct)
        json.put("safeWithdrawalRatePct", s.safeWithdrawalRatePct)
        json.put("safetyBufferPct", s.safetyBufferPct)
        json.put("fireTargetOverride", s.fireTargetOverride)
        json.put("statePensionAge", s.statePensionAge)
        json.put("statePensionMonthly", s.statePensionMonthly)
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

        return json.toString(2)
    }

    fun deserializeSettingsFromJson(jsonStr: String, fallback: SettingsEntity): SettingsEntity? {
        return try {
            val json = JSONObject(jsonStr.trim())
            fallback.copy(
                baseYear = json.optInt("baseYear", fallback.baseYear),
                primaryAge = json.optInt("primaryAge", fallback.primaryAge),

                vSalary = json.optDouble("vSalary", fallback.vSalary),
                vRaiseAnnual = json.optDouble("vRaiseAnnual", fallback.vRaiseAnnual),
                vBonusAnnual = json.optDouble("vBonusAnnual", fallback.vBonusAnnual),
                vMealVouchersMonthly = json.optDouble("vMealVouchersMonthly", fallback.vMealVouchersMonthly),
                eReturnYear = json.optInt("eReturnYear", fallback.eReturnYear),
                eStartingSalary = json.optDouble("eStartingSalary", fallback.eStartingSalary),
                eBonusAnnual = json.optDouble("eBonusAnnual", fallback.eBonusAnnual),
                eSalaryGrowthPct = json.optDouble("eSalaryGrowthPct", fallback.eSalaryGrowthPct),
                eReinvestedPct = json.optDouble("eReinvestedPct", fallback.eReinvestedPct),
                eParentalAllowanceMonthly = json.optDouble("eParentalAllowanceMonthly", fallback.eParentalAllowanceMonthly),
                eLecturingMonthly = json.optDouble("eLecturingMonthly", fallback.eLecturingMonthly),
                eIncludeLecturing = json.optBoolean("eIncludeLecturing", fallback.eIncludeLecturing),
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
                employerRetirementAnnual = json.optDouble("employerRetirementAnnual", fallback.employerRetirementAnnual),
                ePortuDcaMonthly = json.optDouble("ePortuDcaMonthly", fallback.ePortuDcaMonthly),
                eDpsOwnContributionMonthly = json.optDouble("eDpsOwnContributionMonthly", fallback.eDpsOwnContributionMonthly),
                eDipContributionMonthly = json.optDouble("eDipContributionMonthly", fallback.eDipContributionMonthly),
                eEmployerRetirementAnnual = json.optDouble("eEmployerRetirementAnnual", fallback.eEmployerRetirementAnnual),

                portfolioNominalReturnPct = json.optDouble("portfolioNominalReturnPct", fallback.portfolioNominalReturnPct),
                cpiInflationPct = json.optDouble("cpiInflationPct", fallback.cpiInflationPct),
                safeWithdrawalRatePct = json.optDouble("safeWithdrawalRatePct", fallback.safeWithdrawalRatePct),
                safetyBufferPct = json.optDouble("safetyBufferPct", fallback.safetyBufferPct),
                fireTargetOverride = json.optDouble("fireTargetOverride", fallback.fireTargetOverride),
                statePensionAge = json.optInt("statePensionAge", fallback.statePensionAge),
                statePensionMonthly = json.optDouble("statePensionMonthly", fallback.statePensionMonthly),
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
                childUniMonthly = json.optDouble("childUniMonthly", fallback.childUniMonthly)
            )
        } catch (_: Exception) {
            null
        }
    }
}
