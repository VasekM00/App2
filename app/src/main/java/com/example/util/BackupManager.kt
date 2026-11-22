package com.example.util

import com.example.data.SettingsEntity
import org.json.JSONObject

object BackupManager {

    private const val MAX_BACKUP_JSON_CHARS = 10 * 1024 * 1024
    private const val MAX_NESTED_JSON_CHARS = 1 * 1024 * 1024
    private const val MAX_JSON_NESTING_DEPTH = 64
    private const val MAX_LEDGER_ENTRIES = 2_000
    private const val MAX_IMPORTED_TXS = 100_000
    private const val MAX_ACTION_STATES = 5_000
    private const val MAX_NAME_CHARS = 200
    private val YEAR_MONTH_PATTERN = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")
    private val ISO_DATE_PATTERN = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")

    private val KNOWN_SETTINGS_KEYS = setOf(
        "baseYear", "primaryAge", "primaryName", "spouseName", "isSingleHousehold",
        "vSalary", "eStartingSalary", "rentMonthly", "groceriesMonthly", "liquidPortfolioCurrent",
        "dpsBalanceCurrent", "dipBalanceCurrent", "emergencyReserveCurrent", "safeWithdrawalRatePct",
        "portfolioNominalReturnPct", "cpiInflationPct", "guardrailsUpperThresholdPct",
        "baristaTransitionEnabled", "monteCarloSeed", "merchantRulesJson"
    )

    private fun safeFinite(v: Double): Double = if (v.isFinite()) v else 0.0

    private fun exceedsJsonDepthLimit(json: String, maxDepth: Int = MAX_JSON_NESTING_DEPTH): Boolean {
        var depth = 0
        var inQuotes = false
        var escaped = false
        for (i in 0 until json.length) {
            val c = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inQuotes) {
                if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inQuotes = false
                }
                continue
            }
            when (c) {
                '"' -> inQuotes = true
                '{', '[' -> {
                    depth++
                    if (depth > maxDepth) return true
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return false
    }

    private fun hasAnyKnownSettingsKey(obj: JSONObject): Boolean =
        KNOWN_SETTINGS_KEYS.any { obj.has(it) }

    fun serializeSettingsToJson(s: SettingsEntity): String =
        serializeSettingsToJsonObject(s).toString()

    private fun serializeSettingsToJsonObject(s: SettingsEntity): JSONObject {
        val json = JSONObject()
        json.put("_format", "martinu-settings")
        json.put("_schema", 1)
        json.put("baseYear", s.baseYear)
        json.put("primaryAge", s.primaryAge)
        json.put("primaryName", s.primaryName)
        json.put("spouseName", s.spouseName)
        json.put("isSingleHousehold", s.isSingleHousehold)
        json.put("dcaAnnualGrowthPct", safeFinite(s.dcaAnnualGrowthPct))
        
        // Income & Work
        json.put("vSalary", safeFinite(s.vSalary))
        json.put("vSalaryGrowthPct", safeFinite(s.vSalaryGrowthPct))
        json.put("vMealVouchersMonthly", safeFinite(s.vMealVouchersMonthly))
        json.put("vOtherInflowsMonthly", safeFinite(s.vOtherInflowsMonthly))
        json.put("eReturnYear", s.eReturnYear)
        json.put("eReturnMonth", s.eReturnMonth)
        json.put("eStartingSalary", safeFinite(s.eStartingSalary))
        json.put("eSalaryGrowthPct", safeFinite(s.eSalaryGrowthPct))
        json.put("eReinvestedPct", safeFinite(s.eReinvestedPct))
        json.put("eParentalAllowanceMonthly", safeFinite(s.eParentalAllowanceMonthly))
        json.put("eLecturingMonthly", safeFinite(s.eLecturingMonthly))
        json.put("eIncludeLecturing", s.eIncludeLecturing)
        json.put("eOtherInflowsMonthly", safeFinite(s.eOtherInflowsMonthly))
        json.put("vGiftsMonthly", safeFinite(s.vGiftsMonthly))
        json.put("eGiftsMonthly", safeFinite(s.eGiftsMonthly))
        json.put("lumpSumYear", s.lumpSumYear)
        json.put("lumpSumAmount", safeFinite(s.lumpSumAmount))
        json.put("lumpSumInclude", s.lumpSumInclude)

        // Balances & Portfolios
        json.put("liquidPortfolioCurrent", safeFinite(s.liquidPortfolioCurrent))
        json.put("dpsBalanceCurrent", safeFinite(s.dpsBalanceCurrent))
        json.put("dipBalanceCurrent", safeFinite(s.dipBalanceCurrent))
        json.put("eLiquidPortfolioCurrent", safeFinite(s.eLiquidPortfolioCurrent))
        json.put("eDpsBalanceCurrent", safeFinite(s.eDpsBalanceCurrent))
        json.put("eDipBalanceCurrent", safeFinite(s.eDipBalanceCurrent))
        json.put("emergencyReserveCurrent", safeFinite(s.emergencyReserveCurrent))
        json.put("emergencyReserveTarget", safeFinite(s.emergencyReserveTarget))

        // Monthly DCA Flow
        json.put("portuDcaMonthly", safeFinite(s.portuDcaMonthly))
        json.put("dpsOwnContributionMonthly", safeFinite(s.dpsOwnContributionMonthly))
        json.put("dipContributionMonthly", safeFinite(s.dipContributionMonthly))
        json.put("employerRetirementMonthly", safeFinite(s.employerRetirementMonthly))
        json.put("ePortuDcaMonthly", safeFinite(s.ePortuDcaMonthly))
        json.put("eDpsOwnContributionMonthly", safeFinite(s.eDpsOwnContributionMonthly))
        json.put("eDipContributionMonthly", safeFinite(s.eDipContributionMonthly))
        json.put("eEmployerRetirementMonthly", safeFinite(s.eEmployerRetirementMonthly))

        // Core Assumptions & FIRE Targets
        json.put("portfolioNominalReturnPct", safeFinite(s.portfolioNominalReturnPct))
        json.put("cpiInflationPct", safeFinite(s.cpiInflationPct))
        json.put("safeWithdrawalRatePct", safeFinite(s.safeWithdrawalRatePct))
        json.put("safetyBufferPct", safeFinite(s.safetyBufferPct))
        json.put("fireTargetOverride", safeFinite(s.fireTargetOverride))
        json.put("vStatePensionAge", s.vStatePensionAge)
        json.put("eStatePensionAge", s.eStatePensionAge)
        json.put("vStatePensionMonthly", safeFinite(s.vStatePensionMonthly))
        json.put("eStatePensionMonthly", safeFinite(s.eStatePensionMonthly))
        json.put("lifestyleCostAtFireMonthly", safeFinite(s.lifestyleCostAtFireMonthly))

        // Living Expenses
        json.put("rentMonthly", safeFinite(s.rentMonthly))
        json.put("groceriesMonthly", safeFinite(s.groceriesMonthly))
        json.put("cafesMonthly", safeFinite(s.cafesMonthly))
        json.put("therapyMonthly", safeFinite(s.therapyMonthly))
        json.put("charityMonthly", safeFinite(s.charityMonthly))
        json.put("entertainmentMonthly", safeFinite(s.entertainmentMonthly))
        json.put("transportMonthly", safeFinite(s.transportMonthly))
        json.put("subscriptionsMonthly", safeFinite(s.subscriptionsMonthly))
        json.put("otherDiscretionaryMonthly", safeFinite(s.otherDiscretionaryMonthly))
        json.put("rentGrowthPct", safeFinite(s.rentGrowthPct))
        json.put("customExpensesJson", s.customExpensesJson)
        json.put("customGoalsJson", s.customGoalsJson)
        json.put("customLumpSumsJson", s.customLumpSumsJson)
        json.put("deletedCategoriesJson", s.deletedCategoriesJson)

        // Children & Family
        json.put("child1Name", s.child1Name)
        json.put("child2Name", s.child2Name)
        json.put("childExpensesEnabled", s.childExpensesEnabled)
        json.put("currentChildCostsInBaseline", s.currentChildCostsInBaseline)
        json.put("child1Enabled", s.child1Enabled)
        json.put("child1BirthYear", s.child1BirthYear)
        json.put("child1TaxBonusAnnual", safeFinite(s.child1TaxBonusAnnual))
        json.put("child2Enabled", s.child2Enabled)
        json.put("child2BirthYear", s.child2BirthYear)
        json.put("child2TaxBonusAnnual", safeFinite(s.child2TaxBonusAnnual))
        json.put("child3PlusTaxBonusAnnual", safeFinite(s.child3PlusTaxBonusAnnual))
        json.put("childToddlerMonthly", safeFinite(s.childToddlerMonthly))
        json.put("childPreschoolMonthly", safeFinite(s.childPreschoolMonthly))
        json.put("childSchoolMonthly", safeFinite(s.childSchoolMonthly))
        json.put("childTeenMonthly", safeFinite(s.childTeenMonthly))
        json.put("childUniMonthly", safeFinite(s.childUniMonthly))
        json.put("prolongChildSupportInFire", s.prolongChildSupportInFire)
        json.put("childSupportUntilAge", s.childSupportUntilAge)

        // Statutory & Tax parameters (Czech Tax Act No. 586/1992 Coll.)
        json.put("taxRatePct", safeFinite(s.taxRatePct))
        json.put("taxRateSecondPct", safeFinite(s.taxRateSecondPct))
        json.put("taxSecondBracketThresholdAnnual", safeFinite(s.taxSecondBracketThresholdAnnual))
        json.put("taxpayerCreditAnnual", safeFinite(s.taxpayerCreditAnnual))
        json.put("taxDeductionCeilingAnnual", safeFinite(s.taxDeductionCeilingAnnual))
        json.put("spouseTaxCreditAnnual", safeFinite(s.spouseTaxCreditAnnual))
        json.put("spouseIncomeLimitAnnual", safeFinite(s.spouseIncomeLimitAnnual))
        json.put("includeSpouseCredit", s.includeSpouseCredit)
        json.put("hasChildUnder3", s.hasChildUnder3)
        json.put("minWageMonthly", safeFinite(s.minWageMonthly))

        // DPS Reform parameters (Lepší penzijko)
        json.put("dpsGrossReturnPct", safeFinite(s.dpsGrossReturnPct))
        json.put("dpsAnnualFeePct", safeFinite(s.dpsAnnualFeePct))
        json.put("dpsDeductionThresholdMonthly", safeFinite(s.dpsDeductionThresholdMonthly))
        json.put("dpsStandardSubsidyMaxMonthly", safeFinite(s.dpsStandardSubsidyMaxMonthly))
        json.put("dpsYouthSubsidyMaxMonthly", safeFinite(s.dpsYouthSubsidyMaxMonthly))
        json.put("dpsMinDepositForSubsidy", safeFinite(s.dpsMinDepositForSubsidy))
        json.put("dpsYouthAgeLimit", s.dpsYouthAgeLimit)
        json.put("dpsSubsidyRateStandardPct", safeFinite(s.dpsSubsidyRateStandardPct))
        json.put("dpsSubsidyRateYouthPct", safeFinite(s.dpsSubsidyRateYouthPct))
        json.put("dpsStatutoryFeeCapPct", safeFinite(s.dpsStatutoryFeeCapPct))
        json.put("employerRetirementExemptionAnnual", safeFinite(s.employerRetirementExemptionAnnual))
        json.put("childBonusMinWageMultiplier", safeFinite(s.childBonusMinWageMultiplier))
        json.put("parentalAllowanceTotalBeforeCutoff", safeFinite(s.parentalAllowanceTotalBeforeCutoff))
        json.put("parentalAllowanceTotalFromCutoff", safeFinite(s.parentalAllowanceTotalFromCutoff))
        json.put("parentalAllowanceCutoffYear", s.parentalAllowanceCutoffYear)

        // Monte Carlo configuration
        json.put("monteCarloN", s.monteCarloN)
        json.put("monteCarloVolatilityPct", safeFinite(s.monteCarloVolatilityPct))
        json.put("monteCarloSeed", s.monteCarloSeed)
        json.put("retirementHorizonYears", s.retirementHorizonYears)
        json.put("dividendYieldPct", safeFinite(s.dividendYieldPct))
        json.put("dividendTaxRatePct", safeFinite(s.dividendTaxRatePct))
        json.put("useHistoricalBootstrap", s.useHistoricalBootstrap)
        json.put("guardrailsEnabled", s.guardrailsEnabled)
        json.put("guardrailsUpperThresholdPct", safeFinite(s.guardrailsUpperThresholdPct))
        json.put("guardrailsLowerThresholdPct", safeFinite(s.guardrailsLowerThresholdPct))
        json.put("guardrailsCutPct", safeFinite(s.guardrailsCutPct))
        json.put("guardrailsBoostPct", safeFinite(s.guardrailsBoostPct))
        json.put("guardrailsFloorPct", safeFinite(s.guardrailsFloorPct))
        json.put("baristaTransitionEnabled", s.baristaTransitionEnabled)
        json.put("baristaStartYear", s.baristaStartYear)
        json.put("baristaMonthlyIncome", safeFinite(s.baristaMonthlyIncome))
        json.put("baristaSavingsMonthly", safeFinite(s.baristaSavingsMonthly))
        json.put("merchantRulesJson", s.merchantRulesJson)
        json.put("emergencyReserveMode", s.emergencyReserveMode)
        json.put(
            "emergencyReserveMonthlyOverride",
            if (s.emergencyReserveMonthlyOverride.isFinite()) s.emergencyReserveMonthlyOverride else -1.0
        )
        json.put("knownFamilyAccountsJson", s.knownFamilyAccountsJson)
        json.put("knownParentalGiftAccountsJson", s.knownParentalGiftAccountsJson)

        return json
    }

    fun deserializeSettingsFromJson(jsonStr: String, fallback: SettingsEntity): SettingsEntity? {
        if (jsonStr.isBlank() || jsonStr.length > MAX_BACKUP_JSON_CHARS) return null
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{") || exceedsJsonDepthLimit(trimmed)) return null
        return try {
            val rawJson = JSONObject(trimmed)
            val rootFormat = rawJson.optString("_format", "").trim()
            if (rawJson.has("_format")) {
                val isSettingsFormat = rootFormat.equals("martinu-settings", ignoreCase = true)
                val isFullFormat = (rootFormat.equals("martinu-financials-full-backup", ignoreCase = true) ||
                    rootFormat.equals("martinu-full-backup", ignoreCase = true)) && rawJson.has("settings")
                if (!isSettingsFormat && !isFullFormat) {
                    return null
                }
            }
            if (rawJson.has("_schema")) {
                val rootSchema = rawJson.optInt("_schema", -1)
                val allowedRange = if (rootFormat.equals("martinu-settings", ignoreCase = true) || !rawJson.has("settings")) 1..1 else 1..2
                if (rootSchema !in allowedRange) {
                    return null
                }
            }
            val json = if (rawJson.has("settings")) {
                rawJson.optJSONObject("settings") ?: return null
            } else {
                rawJson
            }
            if (json !== rawJson && json.has("_schema") && json.optInt("_schema", -1) != 1) {
                return null
            }
            if (!rawJson.has("_format") && !hasAnyKnownSettingsKey(json)) {
                return null
            }
            val restoredVGifts = json.optFiniteDouble(
                "vGiftsMonthly",
                json.optFiniteDouble("familyGiftMonthly", fallback.vGiftsMonthly)
            )
            val restoredEGifts = json.optFiniteDouble("eGiftsMonthly", fallback.eGiftsMonthly)
            val restoredSingle = json.optSafeBoolean("isSingleHousehold", fallback.isSingleHousehold)
            val pensionSplit = if (restoredSingle) 1.0 else 2.0
            val restored = fallback.copy(
                baseYear = json.optSafeInt("baseYear", fallback.baseYear),
                primaryAge = json.optSafeInt("primaryAge", fallback.primaryAge),
                primaryName = json.optStringNonNull("primaryName", fallback.primaryName),
                spouseName = json.optStringNonNull("spouseName", fallback.spouseName),
                isSingleHousehold = restoredSingle,
                dcaAnnualGrowthPct = json.optFiniteDouble("dcaAnnualGrowthPct", fallback.dcaAnnualGrowthPct),

                vSalary = json.optFiniteDouble("vSalary", fallback.vSalary),
                vSalaryGrowthPct = json.optFiniteDouble("vSalaryGrowthPct", fallback.vSalaryGrowthPct),
                vMealVouchersMonthly = json.optFiniteDouble("vMealVouchersMonthly", fallback.vMealVouchersMonthly),
                vOtherInflowsMonthly = json.optFiniteDouble("vOtherInflowsMonthly", fallback.vOtherInflowsMonthly),
                eReturnYear = json.optSafeInt("eReturnYear", fallback.eReturnYear),
                eReturnMonth = json.optSafeInt("eReturnMonth", fallback.eReturnMonth),
                eStartingSalary = json.optFiniteDouble("eStartingSalary", fallback.eStartingSalary),
                eSalaryGrowthPct = json.optFiniteDouble("eSalaryGrowthPct", fallback.eSalaryGrowthPct),
                eReinvestedPct = json.optFiniteDouble("eReinvestedPct", fallback.eReinvestedPct),
                eParentalAllowanceMonthly = json.optFiniteDouble("eParentalAllowanceMonthly", fallback.eParentalAllowanceMonthly),
                eLecturingMonthly = json.optFiniteDouble("eLecturingMonthly", fallback.eLecturingMonthly),
                eIncludeLecturing = json.optSafeBoolean("eIncludeLecturing", fallback.eIncludeLecturing),
                eOtherInflowsMonthly = json.optFiniteDouble("eOtherInflowsMonthly", fallback.eOtherInflowsMonthly),
                vGiftsMonthly = restoredVGifts,
                eGiftsMonthly = restoredEGifts,
                lumpSumYear = json.optSafeInt("lumpSumYear", fallback.lumpSumYear),
                lumpSumAmount = json.optFiniteDouble("lumpSumAmount", fallback.lumpSumAmount),
                lumpSumInclude = json.optSafeBoolean("lumpSumInclude", fallback.lumpSumInclude),

                liquidPortfolioCurrent = json.optFiniteDouble("liquidPortfolioCurrent", fallback.liquidPortfolioCurrent),
                dpsBalanceCurrent = json.optFiniteDouble("dpsBalanceCurrent", fallback.dpsBalanceCurrent),
                dipBalanceCurrent = json.optFiniteDouble("dipBalanceCurrent", fallback.dipBalanceCurrent),
                eLiquidPortfolioCurrent = json.optFiniteDouble("eLiquidPortfolioCurrent", fallback.eLiquidPortfolioCurrent),
                eDpsBalanceCurrent = json.optFiniteDouble("eDpsBalanceCurrent", fallback.eDpsBalanceCurrent),
                eDipBalanceCurrent = json.optFiniteDouble("eDipBalanceCurrent", fallback.eDipBalanceCurrent),
                emergencyReserveCurrent = json.optFiniteDouble("emergencyReserveCurrent", fallback.emergencyReserveCurrent),
                emergencyReserveTarget = json.optFiniteDouble("emergencyReserveTarget", fallback.emergencyReserveTarget),

                portuDcaMonthly = json.optFiniteDouble("portuDcaMonthly", fallback.portuDcaMonthly),
                dpsOwnContributionMonthly = json.optFiniteDouble("dpsOwnContributionMonthly", fallback.dpsOwnContributionMonthly),
                dipContributionMonthly = json.optFiniteDouble("dipContributionMonthly", fallback.dipContributionMonthly),
                employerRetirementMonthly = json.optFiniteDouble("employerRetirementMonthly", fallback.employerRetirementMonthly),
                ePortuDcaMonthly = json.optFiniteDouble("ePortuDcaMonthly", fallback.ePortuDcaMonthly),
                eDpsOwnContributionMonthly = json.optFiniteDouble("eDpsOwnContributionMonthly", fallback.eDpsOwnContributionMonthly),
                eDipContributionMonthly = json.optFiniteDouble("eDipContributionMonthly", fallback.eDipContributionMonthly),
                eEmployerRetirementMonthly = json.optFiniteDouble("eEmployerRetirementMonthly", fallback.eEmployerRetirementMonthly),

                portfolioNominalReturnPct = json.optFiniteDouble("portfolioNominalReturnPct", fallback.portfolioNominalReturnPct),
                cpiInflationPct = json.optFiniteDouble("cpiInflationPct", fallback.cpiInflationPct),
                safeWithdrawalRatePct = json.optFiniteDouble("safeWithdrawalRatePct", fallback.safeWithdrawalRatePct),
                safetyBufferPct = json.optFiniteDouble("safetyBufferPct", fallback.safetyBufferPct),
                fireTargetOverride = json.optFiniteDouble("fireTargetOverride", fallback.fireTargetOverride),
                vStatePensionAge = json.optSafeInt(
                    "vStatePensionAge",
                    json.optSafeInt("statePensionAge", fallback.vStatePensionAge)
                ),
                eStatePensionAge = json.optSafeInt(
                    "eStatePensionAge",
                    json.optSafeInt("statePensionAge", fallback.eStatePensionAge)
                ),
                vStatePensionMonthly = when {
                    json.has("vStatePensionMonthly") && !json.isNull("vStatePensionMonthly") ->
                        json.optFiniteDouble("vStatePensionMonthly", fallback.vStatePensionMonthly)
                    json.has("statePensionMonthly") && !json.isNull("statePensionMonthly") ->
                        json.optFiniteDouble("statePensionMonthly", fallback.vStatePensionMonthly * pensionSplit) / pensionSplit
                    else -> fallback.vStatePensionMonthly
                },
                eStatePensionMonthly = when {
                    json.has("eStatePensionMonthly") && !json.isNull("eStatePensionMonthly") ->
                        json.optFiniteDouble("eStatePensionMonthly", fallback.eStatePensionMonthly)
                    json.has("statePensionMonthly") && !json.isNull("statePensionMonthly") ->
                        if (restoredSingle) fallback.eStatePensionMonthly
                        else json.optFiniteDouble("statePensionMonthly", fallback.eStatePensionMonthly * 2.0) / 2.0
                    else -> fallback.eStatePensionMonthly
                },
                lifestyleCostAtFireMonthly = json.optFiniteDouble("lifestyleCostAtFireMonthly", fallback.lifestyleCostAtFireMonthly),

                rentMonthly = json.optFiniteDouble("rentMonthly", fallback.rentMonthly),
                groceriesMonthly = json.optFiniteDouble("groceriesMonthly", fallback.groceriesMonthly),
                cafesMonthly = json.optFiniteDouble("cafesMonthly", fallback.cafesMonthly),
                therapyMonthly = json.optFiniteDouble("therapyMonthly", fallback.therapyMonthly),
                charityMonthly = json.optFiniteDouble("charityMonthly", fallback.charityMonthly),
                entertainmentMonthly = json.optFiniteDouble("entertainmentMonthly", fallback.entertainmentMonthly),
                transportMonthly = json.optFiniteDouble("transportMonthly", fallback.transportMonthly),
                subscriptionsMonthly = json.optFiniteDouble("subscriptionsMonthly", fallback.subscriptionsMonthly),
                otherDiscretionaryMonthly = json.optFiniteDouble("otherDiscretionaryMonthly", fallback.otherDiscretionaryMonthly),
                rentGrowthPct = json.optFiniteDouble("rentGrowthPct", fallback.rentGrowthPct),
                customExpensesJson = json.optStringNonNull("customExpensesJson", fallback.customExpensesJson),
                customGoalsJson = json.optStringNonNull("customGoalsJson", fallback.customGoalsJson),
                customLumpSumsJson = json.optStringNonNull("customLumpSumsJson", fallback.customLumpSumsJson),
                deletedCategoriesJson = json.optStringNonNull("deletedCategoriesJson", fallback.deletedCategoriesJson),

                child1Name = json.optStringNonNull("child1Name", fallback.child1Name),
                child2Name = json.optStringNonNull("child2Name", fallback.child2Name),
                childExpensesEnabled = json.optSafeBoolean("childExpensesEnabled", fallback.childExpensesEnabled),
                currentChildCostsInBaseline = json.optSafeBoolean("currentChildCostsInBaseline", fallback.currentChildCostsInBaseline),
                child1Enabled = json.optSafeBoolean("child1Enabled", fallback.child1Enabled),
                child1BirthYear = json.optSafeInt("child1BirthYear", fallback.child1BirthYear),
                child1TaxBonusAnnual = json.optFiniteDouble("child1TaxBonusAnnual", fallback.child1TaxBonusAnnual),
                child2Enabled = json.optSafeBoolean("child2Enabled", fallback.child2Enabled),
                child2BirthYear = json.optSafeInt("child2BirthYear", fallback.child2BirthYear),
                child2TaxBonusAnnual = json.optFiniteDouble("child2TaxBonusAnnual", fallback.child2TaxBonusAnnual),
                child3PlusTaxBonusAnnual = json.optFiniteDouble("child3PlusTaxBonusAnnual", fallback.child3PlusTaxBonusAnnual),
                childToddlerMonthly = json.optFiniteDouble("childToddlerMonthly", fallback.childToddlerMonthly),
                childPreschoolMonthly = json.optFiniteDouble("childPreschoolMonthly", fallback.childPreschoolMonthly),
                childSchoolMonthly = json.optFiniteDouble("childSchoolMonthly", fallback.childSchoolMonthly),
                childTeenMonthly = json.optFiniteDouble("childTeenMonthly", fallback.childTeenMonthly),
                childUniMonthly = json.optFiniteDouble("childUniMonthly", fallback.childUniMonthly),
                prolongChildSupportInFire = json.optSafeBoolean("prolongChildSupportInFire", fallback.prolongChildSupportInFire),
                childSupportUntilAge = json.optSafeInt("childSupportUntilAge", fallback.childSupportUntilAge),

                // Statutory & Tax parameters
                taxRatePct = json.optFiniteDouble("taxRatePct", fallback.taxRatePct),
                taxRateSecondPct = json.optFiniteDouble("taxRateSecondPct", fallback.taxRateSecondPct),
                taxSecondBracketThresholdAnnual = json.optFiniteDouble("taxSecondBracketThresholdAnnual", fallback.taxSecondBracketThresholdAnnual),
                taxpayerCreditAnnual = json.optFiniteDouble("taxpayerCreditAnnual", fallback.taxpayerCreditAnnual),
                taxDeductionCeilingAnnual = json.optFiniteDouble("taxDeductionCeilingAnnual", fallback.taxDeductionCeilingAnnual),
                spouseTaxCreditAnnual = json.optFiniteDouble("spouseTaxCreditAnnual", fallback.spouseTaxCreditAnnual),
                spouseIncomeLimitAnnual = json.optFiniteDouble("spouseIncomeLimitAnnual", fallback.spouseIncomeLimitAnnual),
                includeSpouseCredit = json.optSafeBoolean("includeSpouseCredit", fallback.includeSpouseCredit),
                hasChildUnder3 = json.optSafeBoolean("hasChildUnder3", fallback.hasChildUnder3),
                minWageMonthly = json.optFiniteDouble("minWageMonthly", fallback.minWageMonthly),

                // DPS Reform parameters
                dpsGrossReturnPct = json.optFiniteDouble("dpsGrossReturnPct", fallback.dpsGrossReturnPct),
                dpsAnnualFeePct = json.optFiniteDouble("dpsAnnualFeePct", fallback.dpsAnnualFeePct),
                dpsDeductionThresholdMonthly = json.optFiniteDouble("dpsDeductionThresholdMonthly", fallback.dpsDeductionThresholdMonthly),
                dpsStandardSubsidyMaxMonthly = json.optFiniteDouble("dpsStandardSubsidyMaxMonthly", fallback.dpsStandardSubsidyMaxMonthly),
                dpsYouthSubsidyMaxMonthly = json.optFiniteDouble("dpsYouthSubsidyMaxMonthly", fallback.dpsYouthSubsidyMaxMonthly),
                dpsMinDepositForSubsidy = json.optFiniteDouble("dpsMinDepositForSubsidy", fallback.dpsMinDepositForSubsidy),
                dpsYouthAgeLimit = json.optSafeInt("dpsYouthAgeLimit", fallback.dpsYouthAgeLimit),
                dpsSubsidyRateStandardPct = json.optFiniteDouble("dpsSubsidyRateStandardPct", fallback.dpsSubsidyRateStandardPct),
                dpsSubsidyRateYouthPct = json.optFiniteDouble("dpsSubsidyRateYouthPct", fallback.dpsSubsidyRateYouthPct),
                dpsStatutoryFeeCapPct = json.optFiniteDouble("dpsStatutoryFeeCapPct", fallback.dpsStatutoryFeeCapPct),
                employerRetirementExemptionAnnual = json.optFiniteDouble("employerRetirementExemptionAnnual", fallback.employerRetirementExemptionAnnual),
                childBonusMinWageMultiplier = json.optFiniteDouble("childBonusMinWageMultiplier", fallback.childBonusMinWageMultiplier),
                parentalAllowanceTotalBeforeCutoff = json.optFiniteDouble("parentalAllowanceTotalBeforeCutoff", fallback.parentalAllowanceTotalBeforeCutoff),
                parentalAllowanceTotalFromCutoff = json.optFiniteDouble("parentalAllowanceTotalFromCutoff", fallback.parentalAllowanceTotalFromCutoff),
                parentalAllowanceCutoffYear = json.optSafeInt("parentalAllowanceCutoffYear", fallback.parentalAllowanceCutoffYear),

                // Monte Carlo configuration
                monteCarloN = json.optSafeInt("monteCarloN", fallback.monteCarloN),
                monteCarloVolatilityPct = json.optFiniteDouble("monteCarloVolatilityPct", fallback.monteCarloVolatilityPct),
                monteCarloSeed = json.optSafeLong("monteCarloSeed", fallback.monteCarloSeed),
                retirementHorizonYears = json.optSafeInt("retirementHorizonYears", fallback.retirementHorizonYears),
                dividendYieldPct = json.optFiniteDouble("dividendYieldPct", fallback.dividendYieldPct),
                dividendTaxRatePct = json.optFiniteDouble("dividendTaxRatePct", fallback.dividendTaxRatePct),
                useHistoricalBootstrap = json.optSafeBoolean("useHistoricalBootstrap", fallback.useHistoricalBootstrap),
                guardrailsEnabled = json.optSafeBoolean("guardrailsEnabled", fallback.guardrailsEnabled),
                guardrailsUpperThresholdPct = json.optFiniteDouble("guardrailsUpperThresholdPct", fallback.guardrailsUpperThresholdPct),
                guardrailsLowerThresholdPct = json.optFiniteDouble("guardrailsLowerThresholdPct", fallback.guardrailsLowerThresholdPct),
                guardrailsCutPct = json.optFiniteDouble("guardrailsCutPct", fallback.guardrailsCutPct),
                guardrailsBoostPct = json.optFiniteDouble("guardrailsBoostPct", fallback.guardrailsBoostPct),
                guardrailsFloorPct = json.optFiniteDouble("guardrailsFloorPct", fallback.guardrailsFloorPct),
                baristaTransitionEnabled = json.optSafeBoolean("baristaTransitionEnabled", fallback.baristaTransitionEnabled),
                baristaStartYear = json.optSafeInt("baristaStartYear", fallback.baristaStartYear),
                baristaMonthlyIncome = json.optFiniteDouble("baristaMonthlyIncome", fallback.baristaMonthlyIncome),
                baristaSavingsMonthly = json.optFiniteDouble("baristaSavingsMonthly", fallback.baristaSavingsMonthly),
                merchantRulesJson = json.optStringNonNull("merchantRulesJson", fallback.merchantRulesJson),
                emergencyReserveMode = json.optStringNonNull("emergencyReserveMode", fallback.emergencyReserveMode),
                emergencyReserveMonthlyOverride = json.optFiniteDouble("emergencyReserveMonthlyOverride", fallback.emergencyReserveMonthlyOverride),
                knownFamilyAccountsJson = json.optStringNonNull("knownFamilyAccountsJson", fallback.knownFamilyAccountsJson),
                knownParentalGiftAccountsJson = json.optStringNonNull("knownParentalGiftAccountsJson", fallback.knownParentalGiftAccountsJson)
            )
            sanitizeSettings(restored, fallback)
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        }
    }

    fun sanitizeSettings(s: SettingsEntity, f: SettingsEntity = SettingsEntity.freshDefaults()): SettingsEntity {
        val cleanMerchantRules = if (s.merchantRulesJson.length <= MAX_NESTED_JSON_CHARS) {
            try {
                JSONObject(s.merchantRulesJson)
                s.merchantRulesJson
            } catch (_: Exception) {
                f.merchantRulesJson
            }
        } else {
            f.merchantRulesJson
        }
        fun cleanArrayJson(raw: String, fallback: String): String =
            if (raw.length <= MAX_NESTED_JSON_CHARS) {
                try {
                    org.json.JSONArray(raw)
                    raw
                } catch (_: Exception) {
                    fallback
                }
            } else {
                fallback
            }
        fun money(v: Double, fallback: Double): Double = if (v.isFinite() && v >= 0.0) v else fallback
        fun pct(v: Double, fallback: Double): Double = if (v.isFinite() && v in 0.0..100.0) v else fallback
        fun boundedPct(v: Double, fallback: Double, min: Double, max: Double): Double =
            if (v.isFinite() && v in min..max) v else fallback
        fun swr(v: Double, fallback: Double): Double = if (v.isFinite() && v in 0.1..100.0) v else fallback
        fun growthPct(v: Double, fallback: Double): Double = if (v.isFinite() && v in -99.0..1000.0) v else fallback
        fun year(v: Int, fallback: Int): Int = if (v in 1950..2200) v else fallback
        return s.copy(
            primaryName = s.primaryName.trim().take(MAX_NAME_CHARS).ifEmpty { f.primaryName },
            spouseName = s.spouseName.trim().take(MAX_NAME_CHARS).ifEmpty { f.spouseName },
            child1Name = s.child1Name.trim().take(MAX_NAME_CHARS).ifEmpty { f.child1Name },
            child2Name = s.child2Name.trim().take(MAX_NAME_CHARS).ifEmpty { f.child2Name },
            primaryAge = if (s.primaryAge in 1..120) s.primaryAge else f.primaryAge,
            baseYear = year(s.baseYear, f.baseYear),
            eReturnYear = year(s.eReturnYear, f.eReturnYear),
            eReturnMonth = if (s.eReturnMonth in 1..12) s.eReturnMonth else f.eReturnMonth,
            lumpSumYear = year(s.lumpSumYear, f.lumpSumYear),
            vStatePensionAge = if (s.vStatePensionAge in 40..90) s.vStatePensionAge else f.vStatePensionAge,
            eStatePensionAge = if (s.eStatePensionAge in 40..90) s.eStatePensionAge else f.eStatePensionAge,
            child1BirthYear = year(s.child1BirthYear, f.child1BirthYear),
            child2BirthYear = year(s.child2BirthYear, f.child2BirthYear),
            childSupportUntilAge = if (s.childSupportUntilAge in 0..50) s.childSupportUntilAge else f.childSupportUntilAge,
            prolongChildSupportInFire = s.prolongChildSupportInFire,
            dpsYouthAgeLimit = if (s.dpsYouthAgeLimit in 0..100) s.dpsYouthAgeLimit else f.dpsYouthAgeLimit,
            monteCarloN = if (s.monteCarloN in 10..10_000) s.monteCarloN else f.monteCarloN,
            retirementHorizonYears = if (s.retirementHorizonYears in 1..100) s.retirementHorizonYears else f.retirementHorizonYears,
            vSalary = money(s.vSalary, f.vSalary),
            vSalaryGrowthPct = growthPct(s.vSalaryGrowthPct, f.vSalaryGrowthPct),
            vMealVouchersMonthly = money(s.vMealVouchersMonthly, f.vMealVouchersMonthly),
            vOtherInflowsMonthly = money(s.vOtherInflowsMonthly, f.vOtherInflowsMonthly),
            eStartingSalary = money(s.eStartingSalary, f.eStartingSalary),
            eSalaryGrowthPct = growthPct(s.eSalaryGrowthPct, f.eSalaryGrowthPct),
            eReinvestedPct = pct(s.eReinvestedPct, f.eReinvestedPct),
            eParentalAllowanceMonthly = money(s.eParentalAllowanceMonthly, f.eParentalAllowanceMonthly),
            eLecturingMonthly = money(s.eLecturingMonthly, f.eLecturingMonthly),
            eOtherInflowsMonthly = money(s.eOtherInflowsMonthly, f.eOtherInflowsMonthly),
            vGiftsMonthly = money(s.vGiftsMonthly, f.vGiftsMonthly),
            eGiftsMonthly = money(s.eGiftsMonthly, f.eGiftsMonthly),
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
            portfolioNominalReturnPct = growthPct(s.portfolioNominalReturnPct, f.portfolioNominalReturnPct),
            dpsGrossReturnPct = growthPct(s.dpsGrossReturnPct, f.dpsGrossReturnPct),
            dpsAnnualFeePct = pct(s.dpsAnnualFeePct, f.dpsAnnualFeePct),
            cpiInflationPct = growthPct(s.cpiInflationPct, f.cpiInflationPct),
            safeWithdrawalRatePct = swr(s.safeWithdrawalRatePct, f.safeWithdrawalRatePct),
            safetyBufferPct = pct(s.safetyBufferPct, f.safetyBufferPct),
            rentGrowthPct = growthPct(s.rentGrowthPct, f.rentGrowthPct),
            dcaAnnualGrowthPct = growthPct(s.dcaAnnualGrowthPct, f.dcaAnnualGrowthPct),
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
            dpsStatutoryFeeCapPct = pct(s.dpsStatutoryFeeCapPct, f.dpsStatutoryFeeCapPct),
            employerRetirementExemptionAnnual = money(s.employerRetirementExemptionAnnual, f.employerRetirementExemptionAnnual),
            childBonusMinWageMultiplier = if (s.childBonusMinWageMultiplier.isFinite() && s.childBonusMinWageMultiplier >= 0.0) s.childBonusMinWageMultiplier else f.childBonusMinWageMultiplier,
            parentalAllowanceTotalBeforeCutoff = money(s.parentalAllowanceTotalBeforeCutoff, f.parentalAllowanceTotalBeforeCutoff),
            parentalAllowanceTotalFromCutoff = money(s.parentalAllowanceTotalFromCutoff, f.parentalAllowanceTotalFromCutoff),
            parentalAllowanceCutoffYear = year(s.parentalAllowanceCutoffYear, f.parentalAllowanceCutoffYear),
            child1TaxBonusAnnual = money(s.child1TaxBonusAnnual, f.child1TaxBonusAnnual),
            child2TaxBonusAnnual = money(s.child2TaxBonusAnnual, f.child2TaxBonusAnnual),
            child3PlusTaxBonusAnnual = money(s.child3PlusTaxBonusAnnual, f.child3PlusTaxBonusAnnual),
            childToddlerMonthly = money(s.childToddlerMonthly, f.childToddlerMonthly),
            childPreschoolMonthly = money(s.childPreschoolMonthly, f.childPreschoolMonthly),
            childSchoolMonthly = money(s.childSchoolMonthly, f.childSchoolMonthly),
            childTeenMonthly = money(s.childTeenMonthly, f.childTeenMonthly),
            childUniMonthly = money(s.childUniMonthly, f.childUniMonthly),
            monteCarloVolatilityPct = pct(s.monteCarloVolatilityPct, f.monteCarloVolatilityPct),
            dividendYieldPct = pct(s.dividendYieldPct, f.dividendYieldPct),
            dividendTaxRatePct = pct(s.dividendTaxRatePct, f.dividendTaxRatePct),
            guardrailsUpperThresholdPct = pct(s.guardrailsUpperThresholdPct, f.guardrailsUpperThresholdPct),
            guardrailsLowerThresholdPct = boundedPct(s.guardrailsLowerThresholdPct, f.guardrailsLowerThresholdPct, 0.0, 95.0),
            guardrailsCutPct = pct(s.guardrailsCutPct, f.guardrailsCutPct),
            guardrailsBoostPct = pct(s.guardrailsBoostPct, f.guardrailsBoostPct),
            guardrailsFloorPct = pct(s.guardrailsFloorPct, f.guardrailsFloorPct),
            baristaStartYear = year(s.baristaStartYear, f.baristaStartYear),
            baristaMonthlyIncome = money(s.baristaMonthlyIncome, f.baristaMonthlyIncome),
            baristaSavingsMonthly = money(s.baristaSavingsMonthly, f.baristaSavingsMonthly),
            customExpensesJson = cleanArrayJson(s.customExpensesJson, f.customExpensesJson),
            customGoalsJson = cleanArrayJson(s.customGoalsJson, f.customGoalsJson),
            customLumpSumsJson = cleanArrayJson(s.customLumpSumsJson, f.customLumpSumsJson),
            deletedCategoriesJson = cleanArrayJson(s.deletedCategoriesJson, f.deletedCategoriesJson),
            merchantRulesJson = cleanMerchantRules,
            emergencyReserveMode = if (s.emergencyReserveMode in setOf("3M", "6M", "9M", "12M", "Target")) s.emergencyReserveMode else f.emergencyReserveMode,
            emergencyReserveMonthlyOverride = when {
                !s.emergencyReserveMonthlyOverride.isFinite() -> f.emergencyReserveMonthlyOverride
                s.emergencyReserveMonthlyOverride < 0.0 -> -1.0
                else -> s.emergencyReserveMonthlyOverride
            },
            knownFamilyAccountsJson = cleanArrayJson(s.knownFamilyAccountsJson, f.knownFamilyAccountsJson),
            knownParentalGiftAccountsJson = cleanArrayJson(s.knownParentalGiftAccountsJson, f.knownParentalGiftAccountsJson)
        )
    }

    data class FullBackupPayload(
        val settings: SettingsEntity,
        val ledgerEntries: List<com.example.data.LedgerEntryEntity> = emptyList(),
        val actionStates: List<com.example.data.ActionStateEntity> = emptyList(),
        val importedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList(),
        val preferences: Map<String, String> = emptyMap(),
        val investmentLots: List<com.example.util.UserInvestmentLot> = emptyList()
    )

    fun serializeFullBackupToJson(payload: FullBackupPayload): String {
        val json = JSONObject()
        json.put("_format", "martinu-financials-full-backup")
        json.put("_schema", 2)
        json.put("settings", serializeSettingsToJsonObject(payload.settings))

        val ledgerArr = org.json.JSONArray()
        for (entry in payload.ledgerEntries) {
            val o = JSONObject()
            o.put("id", entry.id)
            o.put("yearMonth", entry.yearMonth)
            o.put("incVaclav", safeFinite(entry.incVaclav))
            o.put("incEleonora", safeFinite(entry.incEleonora))
            o.put("incUnforeseen", safeFinite(entry.incUnforeseen))
            o.put("expRent", safeFinite(entry.expRent))
            o.put("expGroceries", safeFinite(entry.expGroceries))
            o.put("expOther", safeFinite(entry.expOther))
            o.put("notes", entry.notes)
            o.put("portfolioBalanceAtMonthEnd", safeFinite(entry.portfolioBalanceAtMonthEnd))
            o.put("pensionBalanceAtMonthEnd", safeFinite(entry.pensionBalanceAtMonthEnd))
            o.put("emergencyReserveAtMonthEnd", safeFinite(entry.emergencyReserveAtMonthEnd))
            ledgerArr.put(o)
        }
        json.put("ledgerEntries", ledgerArr)

        val actionArr = org.json.JSONArray()
        for (action in payload.actionStates) {
            val o = JSONObject()
            o.put("actionKey", action.actionKey)
            o.put("year", action.year)
            o.put("actionId", action.actionId)
            o.put("isDone", action.isDone)
            actionArr.put(o)
        }
        json.put("actionStates", actionArr)

        val txArr = org.json.JSONArray()
        for (tx in payload.importedTransactions) {
            val o = JSONObject()
            o.put("id", tx.id)
            o.put("yearMonth", tx.yearMonth)
            o.put("bankName", tx.bankName)
            o.put("date", tx.date)
            o.put("amount", safeFinite(tx.amount))
            o.put("counterpartyAccount", tx.counterpartyAccount)
            o.put("counterpartyName", tx.counterpartyName)
            o.put("message", tx.message)
            o.put("variableSymbol", tx.variableSymbol)
            o.put("category", tx.category)
            o.put("isNetted", tx.isNetted)
            o.put("nettingReason", tx.nettingReason)
            if (tx.matchedTxId != null) o.put("matchedTxId", tx.matchedTxId)
            txArr.put(o)
        }
        json.put("importedTransactions", txArr)

        val prefObj = JSONObject()
        for ((k, v) in payload.preferences) {
            prefObj.put(k, v)
        }
        json.put("preferences", prefObj)

        val lotsArr = org.json.JSONArray()
        for (lot in payload.investmentLots) {
            val o = JSONObject()
            o.put("id", lot.id)
            o.put("batchName", lot.batchName)
            o.put("instrument", lot.instrument)
            o.put("purchaseDateStr", lot.purchaseDateStr)
            o.put("acquisitionCostCzk", safeFinite(lot.acquisitionCostCzk))
            o.put("currentValueCzk", safeFinite(lot.currentValueCzk))
            o.put("notes", lot.notes)
            lotsArr.put(o)
        }
        json.put("investmentLots", lotsArr)

        return json.toString()
    }

    fun deserializeFullBackupFromJson(jsonStr: String, fallback: SettingsEntity): FullBackupPayload? {
        if (jsonStr.isBlank() || jsonStr.length > MAX_BACKUP_JSON_CHARS) return null
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{") || exceedsJsonDepthLimit(trimmed)) return null
        return try {
            val root = JSONObject(trimmed)
            val formatTag = root.optString("_format", "").trim()
            // Settings-only backups ("martinu-settings" without a full-backup "settings" object) MUST NOT
            // be parsed as FullBackupPayload, otherwise restoring them would wipe ledger, transactions & lots.
            if (formatTag.equals("martinu-settings", ignoreCase = true) && !root.has("settings")) {
                return null
            }
            if (root.has("_format") &&
                !formatTag.equals("martinu-financials-full-backup", ignoreCase = true) &&
                !formatTag.equals("martinu-full-backup", ignoreCase = true)
            ) {
                return null
            }
            if (root.has("_schema") && root.optInt("_schema", -1) !in 1..2) {
                return null
            }
            val hasExplicitFormat = formatTag.equals("martinu-financials-full-backup", ignoreCase = true) ||
                formatTag.equals("martinu-full-backup", ignoreCase = true)
            val hasFullBackupEnvelope = hasExplicitFormat ||
                (root.optJSONObject("settings")?.let { hasAnyKnownSettingsKey(it) } == true) ||
                ((root.optJSONArray("ledgerEntries")?.length() ?: 0) > 0) ||
                ((root.optJSONArray("actionStates")?.length() ?: 0) > 0) ||
                ((root.optJSONArray("importedTransactions")?.length() ?: 0) > 0)
            if (!hasFullBackupEnvelope) {
                return null
            }
            val restoredSettings = if (root.has("settings")) {
                val settingsObj = root.optJSONObject("settings") ?: return null
                if (!hasExplicitFormat && !hasAnyKnownSettingsKey(settingsObj)) {
                    return null
                }
                // Pass directly; deserializeSettingsFromJson accepts missing _format when known keys exist
                deserializeSettingsFromJson(settingsObj.toString(), fallback)
                    ?: if (hasExplicitFormat && settingsObj.length() == 0) fallback else return null
            } else {
                deserializeSettingsFromJson(root.toString(), fallback)
                    ?: if (hasExplicitFormat) fallback else return null
            }

            val ledgerList = mutableListOf<com.example.data.LedgerEntryEntity>()
            val ledgerArr = root.optJSONArray("ledgerEntries")
            if (!root.isNull("ledgerEntries") && ledgerArr != null) {
                if (ledgerArr.length() > MAX_LEDGER_ENTRIES) return null
                for (i in 0 until ledgerArr.length()) {
                    val o = ledgerArr.optJSONObject(i) ?: continue
                    val ym = o.optStringNonNull("yearMonth", "").trim()
                    if (!YEAR_MONTH_PATTERN.matches(ym)) continue
                    ledgerList.add(
                        com.example.data.LedgerEntryEntity(
                            id = o.optSafeLong("id", 0L).coerceAtLeast(0L),
                            yearMonth = ym,
                            incVaclav = o.optFiniteDouble("incVaclav", 0.0).coerceAtLeast(0.0),
                            incEleonora = o.optFiniteDouble("incEleonora", 0.0).coerceAtLeast(0.0),
                            incUnforeseen = o.optFiniteDouble("incUnforeseen", 0.0).coerceAtLeast(0.0),
                            expRent = o.optFiniteDouble("expRent", 0.0).coerceAtLeast(0.0),
                            expGroceries = o.optFiniteDouble("expGroceries", 0.0).coerceAtLeast(0.0),
                            expOther = o.optFiniteDouble("expOther", 0.0).coerceAtLeast(0.0),
                            notes = o.optStringNonNull("notes", "").take(2_000),
                            portfolioBalanceAtMonthEnd = o.optFiniteDouble("portfolioBalanceAtMonthEnd", 0.0).coerceAtLeast(0.0),
                            pensionBalanceAtMonthEnd = o.optFiniteDouble("pensionBalanceAtMonthEnd", 0.0).coerceAtLeast(0.0),
                            emergencyReserveAtMonthEnd = o.optFiniteDouble("emergencyReserveAtMonthEnd", 0.0).coerceAtLeast(0.0)
                        )
                    )
                }
            }

            val actionList = mutableListOf<com.example.data.ActionStateEntity>()
            val actionArr = root.optJSONArray("actionStates")
            if (!root.isNull("actionStates") && actionArr != null) {
                if (actionArr.length() > MAX_ACTION_STATES) return null
                for (i in 0 until actionArr.length()) {
                    val o = actionArr.optJSONObject(i) ?: continue
                    val actionKey = o.optStringNonNull("actionKey", "").trim().take(120)
                    if (actionKey.isBlank()) continue
                    actionList.add(
                        com.example.data.ActionStateEntity(
                            actionKey = actionKey,
                            year = o.optSafeInt("year", restoredSettings.baseYear).coerceIn(1900, 2200),
                            actionId = o.optStringNonNull("actionId", "").trim().take(120),
                            isDone = o.optSafeBoolean("isDone", false)
                        )
                    )
                }
            }

            val txList = mutableListOf<com.example.data.ImportedBankTransactionEntity>()
            val txArr = root.optJSONArray("importedTransactions")
            if (!root.isNull("importedTransactions") && txArr != null) {
                if (txArr.length() > MAX_IMPORTED_TXS) return null
                val validCategoryNames = BankTransactionType.entries.map { it.name }.toSet()
                for (i in 0 until txArr.length()) {
                    val o = txArr.optJSONObject(i) ?: continue
                    val ym = o.optStringNonNull("yearMonth", "").trim()
                    if (!YEAR_MONTH_PATTERN.matches(ym)) continue
                    val rawDate = o.optStringNonNull("date", "").trim()
                    if (rawDate.isNotBlank() && !ISO_DATE_PATTERN.matches(rawDate)) continue
                    val amount = if (o.has("amount") && !o.isNull("amount")) {
                        o.optFiniteDoubleOrNull("amount") ?: continue
                    } else {
                        0.0
                    }
                    val rawCat = o.optStringNonNull("category", "LIFESTYLE_LIVING").trim()
                    val safeCat = if (rawCat in validCategoryNames) rawCat else BankTransactionType.UNCATEGORIZED.name
                    txList.add(
                        com.example.data.ImportedBankTransactionEntity(
                            id = o.optSafeLong("id", 0L).coerceAtLeast(0L),
                            yearMonth = ym,
                            bankName = o.optStringNonNull("bankName", "GENERIC").trim().take(64).ifBlank { "GENERIC" },
                            date = rawDate.ifBlank { "$ym-01" },
                            amount = amount,
                            counterpartyAccount = o.optStringNonNull("counterpartyAccount", "").take(120),
                            counterpartyName = o.optStringNonNull("counterpartyName", "").take(200),
                            message = o.optStringNonNull("message", "").take(1_000),
                            variableSymbol = o.optStringNonNull("variableSymbol", "").take(40),
                            category = safeCat,
                            isNetted = o.optSafeBoolean("isNetted", false),
                            nettingReason = o.optStringNonNull("nettingReason", "").take(200),
                            matchedTxId = o.optNullablePositiveLong("matchedTxId")
                        )
                    )
                }
            }

            val prefMap = mutableMapOf<String, String>()
            val prefObj = root.optJSONObject("preferences")
            if (!root.isNull("preferences") && prefObj != null) {
                val keys = prefObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (!prefObj.isNull(k)) {
                        val v = prefObj.opt(k)
                        if (v is String || v is Number || v is Boolean) {
                            prefMap[k] = v.toString()
                        }
                    }
                }
            }

            val lotsList = mutableListOf<com.example.util.UserInvestmentLot>()
            val lotsArr = root.optJSONArray("investmentLots")
            if (!root.isNull("investmentLots") && lotsArr != null) {
                lotsList.addAll(com.example.util.InvestmentLotManager.parseLotsFromJson(lotsArr.toString()))
            } else if (prefMap.containsKey("user_investment_lots_json")) {
                lotsList.addAll(com.example.util.InvestmentLotManager.parseLotsFromJson(prefMap["user_investment_lots_json"] ?: ""))
            }

            FullBackupPayload(
                settings = restoredSettings,
                ledgerEntries = ledgerList,
                actionStates = actionList,
                importedTransactions = txList,
                preferences = prefMap,
                investmentLots = lotsList
            )
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        }
    }

    private fun JSONObject.optStringNonNull(key: String, fallback: String): String {
        if (!has(key) || isNull(key)) return fallback
        return when (val v = opt(key)) {
            is String -> v
            else -> fallback
        }
    }

    private fun JSONObject.optFiniteDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull() ?: return null
            else -> return null
        }
        return if (d.isFinite()) d else null
    }

    private fun JSONObject.optFiniteDouble(key: String, fallback: Double): Double {
        return optFiniteDoubleOrNull(key) ?: fallback
    }

    private fun JSONObject.optSafeInt(key: String, fallback: Int): Int {
        if (!has(key) || isNull(key)) return fallback
        return when (val v = opt(key)) {
            is Int -> v
            is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else fallback
            is Number -> {
                val d = v.toDouble()
                if (d.isFinite() && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) d.toInt() else fallback
            }
            is String -> {
                val s = v.trim()
                s.toIntOrNull() ?: s.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }
                    ?.toInt()
                    ?: fallback
            }
            else -> fallback
        }
    }

    private fun JSONObject.optSafeLong(key: String, fallback: Long): Long {
        if (!has(key) || isNull(key)) return fallback
        return when (val v = opt(key)) {
            is Long -> v
            is Int -> v.toLong()
            is Number -> {
                val d = v.toDouble()
                if (d.isFinite() && d >= Long.MIN_VALUE.toDouble() && d <= Long.MAX_VALUE.toDouble()) d.toLong() else fallback
            }
            is String -> v.trim().toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun JSONObject.optNullablePositiveLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val parsed = optSafeLong(key, -1L)
        return if (parsed > 0L) parsed else null
    }

    private fun JSONObject.optSafeBoolean(key: String, fallback: Boolean): Boolean {
        if (!has(key) || isNull(key)) return fallback
        return when (val v = opt(key)) {
            is Boolean -> v
            is String -> when (v.trim().lowercase(java.util.Locale.ROOT)) {
                "true" -> true
                "false" -> false
                else -> fallback
            }
            else -> fallback
        }
    }
}
