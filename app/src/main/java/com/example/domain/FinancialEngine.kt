package com.example.domain

import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Facade and central calculation coordinator delegating to focused domain sub-engines:
 * - [HouseholdCashFlowEngine]: income, parental allowance, living costs, reserves, tax shield, pension optimization
 * - [FireTargetAndTrajectoryEngine]: FIRE targets, pre-FIRE accumulation, trajectories, stress scenarios, milestones
 * - [MonteCarloEngine]: parametric and historical block-bootstrap Monte Carlo and decumulation survival
 */
object FinancialEngine {

    val HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT: DoubleArray
        get() = MonteCarloEngine.HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT

    fun computeTrailingBurnRateMonthly(ledger: List<LedgerEntryEntity>, months: Int): Double? =
        HouseholdCashFlowEngine.computeTrailingBurnRateMonthly(ledger, months)

    fun computeYtdTaxShieldStatus(
        settings: SettingsEntity,
        currentYear: Int = settings.baseYear,
        currentMonth: Int = 1,
        importedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
        partnerBirthYearOverride: Int? = null
    ): YtdTaxShieldStatus = HouseholdCashFlowEngine.computeYtdTaxShieldStatus(
        settings = settings,
        currentYear = currentYear,
        currentMonth = currentMonth,
        importedTransactions = importedTransactions,
        partnerBirthYearOverride = partnerBirthYearOverride
    )

    fun perpetualFireLeverageMultiplier(settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.perpetualFireLeverageMultiplier(settings)

    fun lumpSumsForMonth(year: Int, month: Int, settings: SettingsEntity): List<CustomLumpSumItem> =
        com.example.domain.lumpSumsForMonth(year, month, settings)

    fun annuityFactor(rate: Double, years: Int): Double =
        FireTargetAndTrajectoryEngine.annuityFactor(rate, years)

    fun partnerBirthYear(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): Int =
        HouseholdCashFlowEngine.partnerBirthYear(settings, partnerBirthYearOverride)

    fun netRealReturnRate(settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.netRealReturnRate(settings)

    fun annualPartnerTaxCredit(
        settings: SettingsEntity,
        year: Int,
        vGrossOverride: Double? = null
    ): Double = HouseholdCashFlowEngine.annualPartnerTaxCredit(settings, year, vGrossOverride)

    internal fun goalsOutlayForYear(
        year: Int,
        parsedGoals: List<CustomLifeGoalItem>,
        hasGoals: Boolean
    ): Double = FireTargetAndTrajectoryEngine.goalsOutlayForYear(year, parsedGoals, hasGoals)

    fun goalsOutlayForYear(year: Int, settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.goalsOutlayForYear(year, settings)

    fun baselineLivingCostAtFireMonthly(settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.baselineLivingCostAtFireMonthly(settings)

    fun fireTargetBase(
        settings: SettingsEntity,
        age: Int = settings.primaryAge,
        partnerBirthYearOverride: Int? = null
    ): Double = FireTargetAndTrajectoryEngine.fireTargetBase(settings, age, partnerBirthYearOverride)

    fun fireTargetYear(
        year: Int,
        settings: SettingsEntity,
        age: Int = settings.primaryAge + (year - settings.baseYear),
        partnerBirthYearOverride: Int? = null
    ): Double = FireTargetAndTrajectoryEngine.fireTargetYear(year, settings, age, partnerBirthYearOverride)

    fun vaclavSalaryMonthly(year: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.vaclavSalaryMonthly(year, settings)

    fun eleonoraSalaryMonthly(year: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.eleonoraSalaryMonthly(year, settings)

    fun remainingParentalPotAtYearStart(targetYear: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.remainingParentalPotAtYearStart(targetYear, settings)

    fun eleonoraBenefitMonthly(year: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.eleonoraBenefitMonthly(year, settings)

    fun eleonoraBenefitForMonth(year: Int, month: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.eleonoraBenefitForMonth(year, month, settings)

    fun parentalAllowancePot(birthYear: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.parentalAllowancePot(birthYear, settings)

    fun eleonoraLecturingMonthly(year: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.eleonoraLecturingMonthly(year, settings)

    fun spouseOwnIncomeAnnual(year: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.spouseOwnIncomeAnnual(year, settings)

    fun householdIncome(
        year: Int,
        settings: SettingsEntity,
        activeLedgerEntry: LedgerEntryEntity? = null,
        month: Int? = null
    ): YearlyIncome = HouseholdCashFlowEngine.householdIncome(year, settings, activeLedgerEntry, month)

    fun dpsSubsidy(
        monthlyDeposit: Double,
        age: Int,
        settings: SettingsEntity? = null,
        year: Int = settings?.baseYear ?: 2026,
        statePensionAge: Int = settings?.vStatePensionAge ?: Int.MAX_VALUE
    ): Double = HouseholdCashFlowEngine.dpsSubsidy(monthlyDeposit, age, settings, year, statePensionAge)

    fun annualRetirementDeduction(
        settings: SettingsEntity,
        partnerBirthYearOverride: Int? = null,
        year: Int = settings.baseYear
    ): Double = CzechTaxEngine.annualRetirementDeduction(settings, year, partnerBirthYearOverride)

    fun annualRetirementTaxSaved(
        settings: SettingsEntity,
        year: Int = settings.baseYear,
        partnerBirthYearOverride: Int? = null
    ): Double = HouseholdCashFlowEngine.annualRetirementTaxSaved(settings, year, partnerBirthYearOverride)

    fun netToGrossAnnual(
        netMonthly: Double,
        taxpayerCreditAnnual: Double,
        highBracketThresholdAnnual: Double = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026,
        baseTaxRatePct: Double = RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_BASE_PCT,
        highTaxRatePct: Double = RegulatoryConstants.STATUTORY_INCOME_TAX_RATE_HIGH_PCT
    ): Double = HouseholdCashFlowEngine.netToGrossAnnual(
        netMonthly = netMonthly,
        taxpayerCreditAnnual = taxpayerCreditAnnual,
        highBracketThresholdAnnual = highBracketThresholdAnnual,
        baseTaxRatePct = baseTaxRatePct,
        highTaxRatePct = highTaxRatePct
    )

    fun dipTaxSavingYear(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): Double =
        CzechTaxEngine.dipTaxSavingYear(settings, partnerBirthYearOverride)

    fun childMonthlyExpense(birthYear: Int, currentYear: Int, settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.childMonthlyExpense(birthYear, currentYear, settings)

    fun totalLivingCostMonthly(settings: SettingsEntity, year: Int = settings.baseYear): Double =
        HouseholdCashFlowEngine.totalLivingCostMonthly(settings, year)

    fun computeEmergencyReserveTargetForMode(
        settings: SettingsEntity,
        totalLivingMonthly: Double,
        mode: String
    ): Double = HouseholdCashFlowEngine.computeEmergencyReserveTargetForMode(settings, totalLivingMonthly, mode)

    fun effectiveEmergencyReserveTarget(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.effectiveEmergencyReserveTarget(settings)

    fun autoEmergencyReserveContribution(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.autoEmergencyReserveContribution(settings)

    fun monthlyEmergencyReserveContribution(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.monthlyEmergencyReserveContribution(settings)

    fun monthlySavingsAndInvestmentTotal(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.monthlySavingsAndInvestmentTotal(settings)

    fun baseOwnInvestMonthly(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.baseOwnInvestMonthly(settings)

    fun baseEmployerMatchMonthly(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.baseEmployerMatchMonthly(settings)

    fun baseInvestMonthly(settings: SettingsEntity): Double =
        HouseholdCashFlowEngine.baseInvestMonthly(settings)

    fun buildLiquidPortfolio(
        settings: SettingsEntity,
        dualIncome: Boolean,
        initialCrashPct: Double = 0.0,
        partnerBirthYearOverride: Int? = null
    ): List<PortfolioYearPoint> =
        FireTargetAndTrajectoryEngine.buildLiquidPortfolio(settings, dualIncome, initialCrashPct, partnerBirthYearOverride)

    fun buildDpsProjection(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): DpsProjection =
        DpsDipEngine.buildDpsProjection(settings, partnerBirthYearOverride)

    fun buildDipProjection(settings: SettingsEntity, partnerBirthYearOverride: Int? = null): DipProjection =
        DpsDipEngine.buildDipProjection(settings, partnerBirthYearOverride)

    fun dividendTaxDragPct(settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.dividendTaxDragPct(settings)

    fun netTaxableNominalReturnPct(settings: SettingsEntity): Double =
        FireTargetAndTrajectoryEngine.netTaxableNominalReturnPct(settings)

    fun runMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = FireTargetAndTrajectoryEngine.projectionHorizonYears(settings),
        initialCrashPct: Double = 0.0,
        partnerBirthYearOverride: Int? = null
    ): MonteCarloResult = MonteCarloEngine.runMonteCarlo(settings, horizonYears, initialCrashPct, partnerBirthYearOverride)

    fun runHistoricalMonteCarlo(
        settings: SettingsEntity,
        horizonYears: Int = FireTargetAndTrajectoryEngine.projectionHorizonYears(settings),
        initialCrashPct: Double = 0.0,
        blockSize: Int = 3,
        partnerBirthYearOverride: Int? = null
    ): MonteCarloResult = MonteCarloEngine.runHistoricalMonteCarlo(settings, horizonYears, initialCrashPct, blockSize, partnerBirthYearOverride)

    fun runRetirementSurvival(
        settings: SettingsEntity,
        useHistoricalBootstrap: Boolean = false,
        partnerBirthYearOverride: Int? = null
    ): RetirementSurvivalResult = MonteCarloEngine.runRetirementSurvival(settings, useHistoricalBootstrap, partnerBirthYearOverride)

    fun calculateStressScenarios(
        settings: SettingsEntity,
        runMonteCarlo: Boolean = true,
        partnerBirthYearOverride: Int? = null
    ): List<StressScenarioResult> = FireTargetAndTrajectoryEngine.calculateStressScenarios(settings, runMonteCarlo, partnerBirthYearOverride)

    fun buildLiquidPortfolioWithInitialCrash(
        settings: SettingsEntity,
        crashPct: Double,
        partnerBirthYearOverride: Int? = null
    ): List<PortfolioYearPoint> = FireTargetAndTrajectoryEngine.buildLiquidPortfolioWithInitialCrash(settings, crashPct, partnerBirthYearOverride)

    fun buildDecumulationConfig(
        settings: SettingsEntity,
        initialPortfolio: Double,
        fireAge: Int,
        fireYear: Int,
        partnerAgeAtFire: Int,
        gkInitialWithdrawalMultiplier: Double = 1.0
    ): DecumulationConfig {
        val safeInitialPortfolio = initialPortfolio.finiteNonNegative().coerceAtLeast(1_000_000.0)
        val decumulationSwr = settings.safeWithdrawalRatePct.finiteIn(0.5, 20.0, 4.0)
        val horizonYears = settings.retirementHorizonYears.coerceIn(5, 60)
        val cpiRate = (settings.cpiInflationPct.finiteAtLeast(-99.0, 2.8) / 100.0).coerceAtLeast(-0.99)
        val pensionStreams = buildList {
            val vPenAnnual = settings.vStatePensionMonthly.finiteNonNegative() * 12.0
            if (vPenAnnual > 0.0) {
                add(max(0, settings.vStatePensionAge - fireAge) to vPenAnnual)
            }
            if (!settings.isSingleHousehold) {
                val ePenAnnual = settings.eStatePensionMonthly.finiteNonNegative() * 12.0
                if (ePenAnnual > 0.0) {
                    add(max(0, settings.eStatePensionAge - partnerAgeAtFire) to ePenAnnual)
                }
            }
        }
        val parsedLumpSums = parseCustomLumpSums(settings.customLumpSumsJson)
        val scheduledLumpSums = (0 until horizonYears).mapNotNull { offset ->
            val calYear = fireYear + offset
            val nomLump = lumpSumForYear(calYear, settings, parsedLumpSums)
            if (nomLump > 0.0) {
                val scaleToTodayReal = (1.0 + cpiRate).coerceAtLeast(0.01)
                    .pow(max(0, calYear - settings.baseYear)).finiteOr(1.0)
                val scaledAmount = (nomLump / scaleToTodayReal).finiteNonNegative()
                if (scaledAmount > 0.0) offset to scaledAmount else null
            } else null
        }
        val baristaStreams = (0 until horizonYears).mapNotNull { offset ->
            val calYear = fireYear + offset
            val realAnnual = FireTargetAndTrajectoryEngine.postFireBaristaNetAnnualForYear(
                calYear = calYear,
                settings = settings,
                inflationFactor = 1.0
            )
            if (realAnnual > 0.0) offset to realAnnual else null
        }
        return DecumulationConfig(
            initialPortfolio = safeInitialPortfolio,
            initialAnnualWithdrawal = safeInitialPortfolio * (decumulationSwr / 100.0),
            horizonYears = horizonYears,
            annualInflationPct = settings.cpiInflationPct.finiteAtLeast(-99.0, 2.5),
            expectedAnnualReturnPct = netTaxableNominalReturnPct(settings),
            returnVolatilityPct = settings.monteCarloVolatilityPct.finiteNonNegative(15.0),
            simulationsCount = settings.monteCarloN.coerceIn(50, 2000),
            randomSeed = settings.monteCarloSeed,
            gkUpperThresholdPct = settings.guardrailsUpperThresholdPct.finiteIn(5.0, 100.0, 20.0),
            gkLowerThresholdPct = settings.guardrailsLowerThresholdPct.finiteIn(5.0, 100.0, 20.0),
            gkCutPct = settings.guardrailsCutPct.finiteIn(1.0, 50.0, 10.0),
            gkBoostPct = settings.guardrailsBoostPct.finiteIn(1.0, 50.0, 10.0),
            gkFloorPctOfInitial = settings.guardrailsFloorPct.finiteIn(10.0, 100.0, 70.0),
            gkInitialWithdrawalMultiplier = gkInitialWithdrawalMultiplier,
            vpdRatePct = (decumulationSwr * 1.075).coerceIn(0.5, 20.0),
            vpdFloorPctOfInitial = settings.guardrailsFloorPct.finiteIn(10.0, 100.0, 70.0),
            pensionStreams = pensionStreams,
            scheduledLumpSums = scheduledLumpSums,
            baristaStreams = baristaStreams
        )
    }

    fun calculate(
        settings: SettingsEntity,
        actionStates: Map<String, Boolean> = emptyMap(),
        runMonteCarlo: Boolean = true,
        activeLedgerEntry: LedgerEntryEntity? = null,
        activeMonth: Int? = null,
        ledgerEntries: List<LedgerEntryEntity> = emptyList(),
        importedTransactions: List<ImportedBankTransactionEntity> = emptyList(),
        partnerBirthYearOverride: Int? = null
    ): FullCalculationState {
        val ledgerMatchesBaseYear = activeLedgerEntry != null &&
            (activeLedgerEntry.yearMonth.isBlank() || activeLedgerEntry.yearMonth.startsWith("${settings.baseYear}-"))
        val currentYear = if (ledgerMatchesBaseYear) {
            activeLedgerEntry!!.yearMonth.substringBefore('-').toIntOrNull() ?: settings.baseYear
        } else {
            settings.baseYear
        }
        val monthForTaxShield = activeMonth ?: 1
        val trailing3m = computeTrailingBurnRateMonthly(ledgerEntries, 3)
        val trailing12m = computeTrailingBurnRateMonthly(ledgerEntries, 12)

        val eLiquid = if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent.finiteNonNegative() else 0.0
        val eDps = if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent.finiteNonNegative() else 0.0
        val eDip = if (!settings.isSingleHousehold) settings.eDipBalanceCurrent.finiteNonNegative() else 0.0

        val hasLiquidOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.portfolioBalanceAtMonthEnd.finiteNonNegative() > 0.0
        val hasPensionOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.pensionBalanceAtMonthEnd.finiteNonNegative() > 0.0
        val hasReserveOverride = ledgerMatchesBaseYear && activeLedgerEntry!!.emergencyReserveAtMonthEnd.finiteNonNegative() > 0.0

        val snapLiquid = if (hasLiquidOverride) {
            activeLedgerEntry!!.portfolioBalanceAtMonthEnd.finiteNonNegative()
        } else {
            settings.liquidPortfolioCurrent.finiteNonNegative() + eLiquid
        }

        val snapPension = if (hasPensionOverride) {
            activeLedgerEntry!!.pensionBalanceAtMonthEnd.finiteNonNegative()
        } else {
            settings.dpsBalanceCurrent.finiteNonNegative() + eDps + settings.dipBalanceCurrent.finiteNonNegative() + eDip
        }

        val snapReserve = if (hasReserveOverride) {
            activeLedgerEntry!!.emergencyReserveAtMonthEnd.finiteNonNegative()
        } else {
            settings.emergencyReserveCurrent.finiteNonNegative()
        }

        val projectionSettings = run {
            var updated = settings
            if (hasLiquidOverride) {
                updated = if (settings.isSingleHousehold || snapLiquid >= eLiquid) {
                    updated.copy(liquidPortfolioCurrent = (snapLiquid - eLiquid).coerceAtLeast(0.0))
                } else {
                    updated.copy(liquidPortfolioCurrent = 0.0, eLiquidPortfolioCurrent = snapLiquid)
                }
            }
            if (hasPensionOverride) {
                val ePenSum = eDps + eDip
                if (settings.isSingleHousehold || snapPension >= ePenSum) {
                    val primaryPen = (snapPension - ePenSum).coerceAtLeast(0.0)
                    val curPrimary = settings.dpsBalanceCurrent.finiteNonNegative() + settings.dipBalanceCurrent.finiteNonNegative()
                    val vContribSum = settings.dpsOwnContributionMonthly.finiteNonNegative() +
                        settings.dipContributionMonthly.finiteNonNegative()
                    val dpsShare = when {
                        curPrimary > 0.0 -> settings.dpsBalanceCurrent.finiteNonNegative() / curPrimary
                        vContribSum > 0.0 -> settings.dpsOwnContributionMonthly.finiteNonNegative() / vContribSum
                        else -> 0.5
                    }
                    val newDps = primaryPen * dpsShare
                    updated = updated.copy(dpsBalanceCurrent = newDps, dipBalanceCurrent = primaryPen - newDps)
                } else {
                    val eContribSum = settings.eDpsOwnContributionMonthly.finiteNonNegative() +
                        settings.eDipContributionMonthly.finiteNonNegative()
                    val eDpsShare = when {
                        ePenSum > 0.0 -> eDps / ePenSum
                        eContribSum > 0.0 -> settings.eDpsOwnContributionMonthly.finiteNonNegative() / eContribSum
                        else -> 0.5
                    }
                    val newEDps = snapPension * eDpsShare
                    updated = updated.copy(
                        dpsBalanceCurrent = 0.0,
                        dipBalanceCurrent = 0.0,
                        eDpsBalanceCurrent = newEDps,
                        eDipBalanceCurrent = snapPension - newEDps
                    )
                }
            }
            if (hasReserveOverride) {
                updated = updated.copy(emergencyReserveCurrent = snapReserve)
            }
            updated
        }

        val ytdTaxShield = computeYtdTaxShieldStatus(
            settings = projectionSettings,
            currentYear = currentYear,
            currentMonth = monthForTaxShield,
            importedTransactions = importedTransactions,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val fireBase = fireTargetBase(projectionSettings, partnerBirthYearOverride = partnerBirthYearOverride)
        val dual = buildLiquidPortfolio(projectionSettings, true, partnerBirthYearOverride = partnerBirthYearOverride)
        val single = buildLiquidPortfolio(projectionSettings, false, partnerBirthYearOverride = partnerBirthYearOverride)

        val fireDualPoint = dual.firstOrNull { it.totalPortfolio >= it.target }
        val fireSinglePoint = single.firstOrNull { it.totalPortfolio >= it.target }
        val activeTrajectory = if (projectionSettings.isSingleHousehold) single else dual
        val fireLiquidBridgePoint = activeTrajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }

        val netWorth = (snapLiquid + snapReserve + snapPension).finiteNonNegative()
        val investableNetWorth = (snapLiquid + snapPension).finiteNonNegative()

        val rReal = netRealReturnRate(projectionSettings)
        val annualLiving = if (projectionSettings.lifestyleCostAtFireMonthly.finiteOr(0.0) > 0.0) {
            projectionSettings.lifestyleCostAtFireMonthly.finiteNonNegative() * 12.0
        } else {
            totalLivingCostMonthly(projectionSettings) * 12.0
        }
        val yearsTo60Today = max(0, 60 - projectionSettings.primaryAge)
        val bridgeReqToday = FireTargetAndTrajectoryEngine.computeBridgeTo60Required(
            annualLiving = annualLiving,
            yearsTo60 = yearsTo60Today,
            rReal = rReal,
            totalFireTarget = fireBase
        )
        val bridgeFundedToday = if (yearsTo60Today == 0) true else snapLiquid >= bridgeReqToday
        val bridgeDeficitToday = max(0.0, bridgeReqToday - snapLiquid)

        val currentIncome = householdIncome(projectionSettings.baseYear, projectionSettings, activeLedgerEntry, activeMonth)
        val ownInvestMonthly = baseOwnInvestMonthly(projectionSettings)
        val employerMatchMonthly = baseEmployerMatchMonthly(projectionSettings)
        val investMonthly = (ownInvestMonthly + employerMatchMonthly).finiteNonNegative()
        val livingCostTotal = totalLivingCostMonthly(projectionSettings)
        val emergencyMonths = if (livingCostTotal > 0.0) max(0.0, snapReserve / livingCostTotal) else 0.0
        val effectiveReserveTarget = effectiveEmergencyReserveTarget(projectionSettings)
        val isReserveFunded = snapReserve >= effectiveReserveTarget
        val reserveSurplusOrDeficit = snapReserve - effectiveReserveTarget
        val reserveContribMonthly = monthlyEmergencyReserveContribution(projectionSettings)

        val dps = buildDpsProjection(projectionSettings, partnerBirthYearOverride = partnerBirthYearOverride)
        val dip = buildDipProjection(projectionSettings, partnerBirthYearOverride = partnerBirthYearOverride)

        val spouseInc = spouseOwnIncomeAnnual(projectionSettings.baseYear, projectionSettings)
        val hasChildUnder3 = CzechTaxEngine.hasChildUnder3(projectionSettings, projectionSettings.baseYear)

        val spouseEligible = !projectionSettings.isSingleHousehold &&
            projectionSettings.includeSpouseCredit &&
            hasChildUnder3 &&
            (spouseInc <= projectionSettings.spouseIncomeLimitAnnual)

        val spouseCreditVal = if (spouseEligible) annualPartnerTaxCredit(projectionSettings, projectionSettings.baseYear) else 0.0
        val childBonusVal = CzechTaxEngine.annualChildTaxRelief(
            settings = projectionSettings,
            year = projectionSettings.baseYear,
            vSpouseCreditAnnual = spouseCreditVal,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val retirementTaxSaved = annualRetirementTaxSaved(
            projectionSettings,
            projectionSettings.baseYear,
            partnerBirthYearOverride = partnerBirthYearOverride
        )
        val baseYearEmbeddedChildCredit = CzechTaxEngine.baseYearChildCreditAnnual(projectionSettings)
        val unembeddedChildCredit = max(0.0, childBonusVal - min(childBonusVal, baseYearEmbeddedChildCredit))
        val incrementalValue = spouseCreditVal + unembeddedChildCredit + retirementTaxSaved

        val taxHelper = TaxReturnHelperData(
            year = projectionSettings.baseYear,
            taxpayerCredit = projectionSettings.taxpayerCreditAnnual,
            spouseCredit = spouseCreditVal,
            childBonus = childBonusVal,
            retirementDeductionBase = annualRetirementDeduction(
                projectionSettings,
                partnerBirthYearOverride = partnerBirthYearOverride,
                year = projectionSettings.baseYear
            ),
            dipSaving = retirementTaxSaved,
            totalIncrementalValue = incrementalValue,
            spouseOwnIncome = spouseInc,
            spouseEligible = spouseEligible
        )

        val monteCarlo = if (runMonteCarlo) {
            runMonteCarlo(projectionSettings, partnerBirthYearOverride = partnerBirthYearOverride)
        } else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val historicalMonteCarlo = if (runMonteCarlo) {
            runHistoricalMonteCarlo(projectionSettings, partnerBirthYearOverride = partnerBirthYearOverride)
        } else MonteCarloResult(0.0, null, null, null, emptyList(), emptyList())
        val retirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(
                projectionSettings,
                useHistoricalBootstrap = false,
                partnerBirthYearOverride = partnerBirthYearOverride
            )
        } else {
            RetirementSurvivalResult(
                successRatePct = 0.0,
                fireProbabilityPct = 0.0,
                horizonYears = projectionSettings.retirementHorizonYears.coerceIn(10, 60),
                medianEndBalanceToday = 0.0,
                p5EndBalanceToday = 0.0,
                medianDepletionAge = null,
                sampleSize = 0,
                guardrailsActive = projectionSettings.guardrailsEnabled,
                guardrailTriggeredPct = 0.0
            )
        }
        val historicalRetirementSurvival = if (runMonteCarlo) {
            runRetirementSurvival(
                projectionSettings,
                useHistoricalBootstrap = true,
                partnerBirthYearOverride = partnerBirthYearOverride
            )
        } else retirementSurvival
        val stressScenarios = calculateStressScenarios(
            projectionSettings,
            runMonteCarlo = runMonteCarlo,
            partnerBirthYearOverride = partnerBirthYearOverride
        )

        val monthlyTotalInflowsWithMatch = currentIncome.totalMonthly + employerMatchMonthly
        val savingsRate = if (currentIncome.totalMonthly > 0.0 && monthlyTotalInflowsWithMatch > 0.0) {
            ((investMonthly / monthlyTotalInflowsWithMatch) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        val savingsRateOwnCash = if (currentIncome.totalMonthly > 0.0) {
            ((ownInvestMonthly / currentIncome.totalMonthly) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        val actionsImpacts = mapOf(
            "ac1" to (if (!projectionSettings.isSingleHousehold && projectionSettings.eIncludeLecturing) projectionSettings.eLecturingMonthly.finiteNonNegative() * 12.0 else 0.0),
            "ac2" to taxHelper.totalIncrementalValue.finiteNonNegative(),
            "ac3" to (snapLiquid.finiteNonNegative() * 0.01),
            "ac4" to (snapReserve.finiteNonNegative() * 0.04),
            "ac5" to dip.taxSavedYear.finiteNonNegative(),
            "ac6" to (if (!projectionSettings.isSingleHousehold) {
                projectionSettings.eStartingSalary.finiteNonNegative() * 12.0 * (projectionSettings.eReinvestedPct.finiteIn(0.0, 100.0) / 100.0)
            } else 0.0),
            "ac8" to (projectionSettings.subscriptionsMonthly.finiteNonNegative() * 12.0),
            "ac9" to max(0.0, snapReserve.finiteNonNegative() - effectiveReserveTarget),
            "ac10" to (snapPension.finiteNonNegative() * 0.005)
        )

        val isFireAchievedToday = investableNetWorth >= fireBase && bridgeFundedToday
        val unconstrainedPoint = if (projectionSettings.isSingleHousehold) fireSinglePoint else fireDualPoint
        val resolvedActivePoint = fireLiquidBridgePoint ?: unconstrainedPoint
        val activeFireYear = if (isFireAchievedToday) projectionSettings.baseYear else resolvedActivePoint?.year
        val activeFireAge = if (isFireAchievedToday) projectionSettings.primaryAge else resolvedActivePoint?.age
        val bridgeWaitYears = if (resolvedActivePoint != null && unconstrainedPoint != null) {
            max(0, resolvedActivePoint.year - unconstrainedPoint.year)
        } else 0
        val bridgeRefPoint = fireLiquidBridgePoint ?: unconstrainedPoint
        val bridgeYearsAtFire = when {
            isFireAchievedToday -> yearsTo60Today
            bridgeRefPoint != null -> max(0, 60 - bridgeRefPoint.age)
            else -> yearsTo60Today
        }
        val bridgeReqAtFire = when {
            isFireAchievedToday -> bridgeReqToday
            bridgeRefPoint != null -> bridgeRefPoint.liquidBridgeTo60Required
            else -> bridgeReqToday
        }
        val bridgeFundedAtFire = when {
            isFireAchievedToday -> bridgeFundedToday
            bridgeRefPoint != null -> bridgeRefPoint.isLiquidBridgeFunded
            else -> bridgeFundedToday
        }
        val bridgeDeficitAtFire = when {
            isFireAchievedToday -> bridgeDeficitToday
            bridgeRefPoint != null -> max(0.0, bridgeRefPoint.liquidBridgeTo60Required - bridgeRefPoint.portfolio)
            else -> bridgeDeficitToday
        }

        val fireMilestones = FireTargetAndTrajectoryEngine.buildFireMilestones(
            settings = projectionSettings,
            fireBase = fireBase,
            activeTrajectory = activeTrajectory,
            activeFireYear = activeFireYear,
            activeFireAge = activeFireAge,
            isFireAchievedToday = isFireAchievedToday,
            snapLiquid = snapLiquid,
            snapPension = snapPension,
            bridgeReqToday = bridgeReqToday,
            yearsTo60Today = yearsTo60Today,
            partnerBirthYearOverride = partnerBirthYearOverride
        )

        val perpetualMultiplier = perpetualFireLeverageMultiplier(projectionSettings)
        val fireReductionFor100 = 100.0 * perpetualMultiplier
        val swr = (projectionSettings.safeWithdrawalRatePct.finiteOr(4.0) / 100.0).coerceAtLeast(0.001)
        val fireProgressPct = if (fireBase > 0.0 && fireBase.isFinite()) {
            ((investableNetWorth / fireBase) * 100.0).coerceIn(0.0, 100.0)
        } else 100.0

        val snapshot = CanonicalHouseholdSnapshot(
            isSingleHousehold = projectionSettings.isSingleHousehold,
            netWorthTotal = netWorth,
            investedLiquidTotal = snapLiquid,
            pensionTotal = snapPension,
            emergencyCashTotal = snapReserve,
            monthlyTakeHomeInflows = currentIncome.totalMonthly,
            monthlyEmployerMatch = employerMatchMonthly,
            monthlyTotalInflowsWithMatch = monthlyTotalInflowsWithMatch,
            monthlyLivingCosts = livingCostTotal,
            monthlyOwnInvesting = ownInvestMonthly,
            monthlyTotalInvesting = investMonthly,
            monthlyReserveContribution = reserveContribMonthly,
            monthlySavingsAndInvestingTotal = (investMonthly + reserveContribMonthly).finiteNonNegative(),
            monthlyOwnSavingsAndInvestingTotal = (ownInvestMonthly + reserveContribMonthly).finiteNonNegative(),
            monthlyLeftoverBeforeInvesting = currentIncome.totalMonthly - livingCostTotal,
            monthlyUnallocatedCash = currentIncome.totalMonthly - livingCostTotal - ownInvestMonthly - reserveContribMonthly,
            savingsRatePct = savingsRate,
            savingsRateOwnCashPct = savingsRateOwnCash,
            emergencyRunwayMonths = emergencyMonths,
            emergencyReserveTargetToday = effectiveReserveTarget,
            isEmergencyReserveFundedToday = isReserveFunded,
            emergencyReserveSurplusOrDeficitToday = reserveSurplusOrDeficit,
            activeFireTargetToday = fireBase,
            activeFirePoint = resolvedActivePoint,
            activeFireYear = activeFireYear,
            activeFireAge = activeFireAge,
            unconstrainedFirePoint = unconstrainedPoint,
            unconstrainedFireYear = unconstrainedPoint?.year,
            unconstrainedFireAge = unconstrainedPoint?.age,
            isFireAchievedToday = isFireAchievedToday,
            fireProgressPct = fireProgressPct,
            monthlyPassiveIncomeAtFire = (fireBase * swr) / 12.0,
            perpetualFireMultiplier = perpetualMultiplier,
            fireReductionPer100CzkMonthly = fireReductionFor100,
            taxRefundAnnualTotal = taxHelper.totalIncrementalValue.finiteNonNegative(),
            historicalFireSuccessPct = historicalMonteCarlo.successRatePct,
            bridgeRequiredToday = bridgeReqToday,
            isBridgeFundedToday = bridgeFundedToday,
            bridgeDeficitToday = bridgeDeficitToday,
            bridgeWaitYears = bridgeWaitYears,
            bridgeYearsFromFireTo60 = bridgeYearsAtFire,
            bridgeRequiredAtFire = bridgeReqAtFire,
            isBridgeFundedAtFire = bridgeFundedAtFire,
            bridgeDeficitAtFire = bridgeDeficitAtFire
        )

        val pensionOptimization = HouseholdCashFlowEngine.computePensionOptimizationSnapshot(
            s = projectionSettings,
            currentIncome = currentIncome,
            partnerBirthYearOverride = partnerBirthYearOverride
        )

        val decumulationBase = maxOf(
            (snapLiquid + snapPension).finiteNonNegative(),
            fireBase.finiteNonNegative(),
            1_000_000.0
        )
        val retirementAgeForDecum = activeFireAge ?: projectionSettings.primaryAge
        val retirementYearForDecum = activeFireYear ?: projectionSettings.baseYear
        val partnerAgeToday = projectionSettings.baseYear - partnerBirthYear(projectionSettings, partnerBirthYearOverride)
        val partnerAgeAtFire = partnerAgeToday + max(0, retirementAgeForDecum - projectionSettings.primaryAge)
        val decumulationComparison = if (runMonteCarlo) {
            DecumulationStrategies.compareStrategies(
                buildDecumulationConfig(
                    settings = projectionSettings,
                    initialPortfolio = decumulationBase,
                    fireAge = retirementAgeForDecum,
                    fireYear = retirementYearForDecum,
                    partnerAgeAtFire = partnerAgeAtFire
                )
            )
        } else null

        return FullCalculationState(
            settings = settings,
            fireBaseTargetToday = fireBase,
            dualTrajectory = dual,
            singleTrajectory = single,
            fireDualPoint = fireDualPoint,
            fireSinglePoint = fireSinglePoint,
            currentIncome = currentIncome,
            investMonthlyTotal = investMonthly,
            emergencyCoverageMonths = emergencyMonths,
            realReturnPct = rReal * 100.0,
            dps = dps,
            dip = dip,
            taxReturnHelper = taxHelper,
            monteCarlo = monteCarlo,
            historicalMonteCarlo = historicalMonteCarlo,
            retirementSurvival = retirementSurvival,
            historicalRetirementSurvival = historicalRetirementSurvival,
            stressScenarios = stressScenarios,
            fireMilestones = fireMilestones,
            savingsRatePct = savingsRate,
            totalLivingCostMonthly = livingCostTotal,
            netWorthTotal = netWorth,
            actionsImpacts = actionsImpacts,
            perpetualFireMultiplier = perpetualMultiplier,
            fireReductionPer100CzkMonthly = fireReductionFor100,
            fireLiquidBridgePoint = fireLiquidBridgePoint,
            liquidBridgeTo60RequiredToday = bridgeReqToday,
            isLiquidBridgeFundedToday = bridgeFundedToday,
            liquidBridgeDeficitToday = bridgeDeficitToday,
            currentLiquidPortfolio = snapLiquid,
            currentPensionPortfolio = snapPension,
            currentEmergencyReserve = snapReserve,
            effectiveEmergencyReserveTargetToday = effectiveReserveTarget,
            isEmergencyReserveFundedToday = isReserveFunded,
            emergencyReserveSurplusOrDeficitToday = reserveSurplusOrDeficit,
            trailingBurnRate3mMonthly = trailing3m,
            trailingBurnRate12mMonthly = trailing12m,
            ytdTaxShieldStatus = ytdTaxShield,
            decumulationComparison = decumulationComparison,
            yearEndTaxChecklist = CzechYearEndTaxChecklist.generateChecklist(
                settings = projectionSettings,
                taxYear = projectionSettings.baseYear,
                spouseOwnIncomeAnnual = spouseInc,
                hasChildUnder3 = hasChildUnder3,
                actualDipContributedAnnual = if (ytdTaxShield.isFromImportedTransactions) ytdTaxShield.ytdDipContributed else null,
                actualDpsQualifyingAnnual = if (ytdTaxShield.isFromImportedTransactions) ytdTaxShield.ytdDpsContributed else null,
                partnerBirthYearOverride = partnerBirthYearOverride
            ),
            effectiveSettings = projectionSettings,
            snapshot = snapshot,
            pensionOptimization = pensionOptimization,
            bridgeYearsFromFireTo60 = bridgeYearsAtFire,
            bridgeRequiredAtFire = bridgeReqAtFire,
            isBridgeFundedAtFire = bridgeFundedAtFire,
            bridgeDeficitAtFire = bridgeDeficitAtFire
        )
    }
}
