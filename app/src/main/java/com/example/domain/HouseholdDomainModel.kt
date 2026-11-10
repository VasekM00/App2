package com.example.domain

import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.data.SettingsEntity
import com.example.data.VACLAV_BIRTH_YEAR
import kotlin.math.max
import kotlin.math.pow

enum class HouseholdRole {
    PRIMARY_EARNER,
    SPOUSE_EARNER,
    DEPENDENT_CHILD
}

data class PensionProfile(
    val dpsBalanceCurrent: Double = 0.0,
    val dpsMonthlyContribution: Double = 0.0,
    val dipBalanceCurrent: Double = 0.0,
    val dipMonthlyContribution: Double = 0.0,
    val employerContributionMonthly: Double = 0.0
)

data class HouseholdMember(
    val id: String,
    val name: String,
    val role: HouseholdRole,
    val birthYear: Int,
    /** Net monthly take-home salary in CZK (mapped from `SettingsEntity.vSalary` / `eStartingSalary`). */
    val currentMonthlyGrossSalary: Double = 0.0,
    val salaryGrowthPct: Double = 0.0,
    val pensionProfile: PensionProfile = PensionProfile(),
    val targetPensionAge: Int = 67
)

enum class TimelineEventType {
    CHILD_BIRTH,
    CHILD_INDEPENDENCE,
    CAREER_RETURN,
    PRIMARY_RETIREMENT,
    SPOUSE_RETIREMENT,
    STATUTORY_UNLOCK,
    CUSTOM_GOAL
}

data class LifeTimelineEvent(
    val id: String,
    val name: String,
    val targetYear: Int,
    val type: TimelineEventType,
    val monthlyCashFlowImpact: Double = 0.0,
    val lumpSumCapitalImpact: Double = 0.0
)

data class HouseholdProfile(
    val primaryEarner: HouseholdMember,
    val spouseEarner: HouseholdMember?,
    val children: List<HouseholdMember>,
    val timelineEvents: List<LifeTimelineEvent>,
    val isSingleHousehold: Boolean,
    val liquidPortfolioCurrent: Double,
    val emergencyReserveCurrent: Double,
    val baseYear: Int
) {
    companion object {
        fun fromSettings(settings: SettingsEntity): HouseholdProfile {
            val primary = HouseholdMember(
                id = "primary",
                name = if (settings.primaryName.isNotBlank() && settings.primaryName != "Václav") settings.primaryName else "Vaclav",
                role = HouseholdRole.PRIMARY_EARNER,
                birthYear = settings.baseYear - settings.primaryAge,
                currentMonthlyGrossSalary = settings.vSalary,
                salaryGrowthPct = settings.vSalaryGrowthPct,
                pensionProfile = PensionProfile(
                    dpsBalanceCurrent = settings.dpsBalanceCurrent,
                    dpsMonthlyContribution = settings.dpsOwnContributionMonthly,
                    dipBalanceCurrent = settings.dipBalanceCurrent,
                    dipMonthlyContribution = settings.dipContributionMonthly,
                    employerContributionMonthly = settings.employerRetirementMonthly
                ),
                targetPensionAge = settings.vStatePensionAge
            )

            val spouse = if (!settings.isSingleHousehold) {
                HouseholdMember(
                    id = "spouse",
                    name = settings.spouseName.ifBlank { "Eleonora" },
                    role = HouseholdRole.SPOUSE_EARNER,
                    birthYear = FinancialEngine.partnerBirthYear(settings),
                    currentMonthlyGrossSalary = settings.eStartingSalary,
                    salaryGrowthPct = settings.eSalaryGrowthPct,
                    pensionProfile = PensionProfile(
                        dpsBalanceCurrent = settings.eDpsBalanceCurrent,
                        dpsMonthlyContribution = settings.eDpsOwnContributionMonthly,
                        dipBalanceCurrent = settings.eDipBalanceCurrent,
                        dipMonthlyContribution = settings.eDipContributionMonthly,
                        employerContributionMonthly = settings.eEmployerRetirementMonthly
                    ),
                    targetPensionAge = settings.eStatePensionAge
                )
            } else null

            val children = mutableListOf<HouseholdMember>()
            if (settings.child1Enabled) {
                children.add(
                    HouseholdMember(
                        id = "child_1",
                        name = settings.child1Name,
                        role = HouseholdRole.DEPENDENT_CHILD,
                        birthYear = settings.child1BirthYear
                    )
                )
            }
            if (settings.child2Enabled) {
                children.add(
                    HouseholdMember(
                        id = "child_2",
                        name = settings.child2Name,
                        role = HouseholdRole.DEPENDENT_CHILD,
                        birthYear = settings.child2BirthYear
                    )
                )
            }

            val events = mutableListOf<LifeTimelineEvent>()
            if (settings.child1Enabled) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_child1_birth",
                        name = "${settings.child1Name} Birth",
                        targetYear = settings.child1BirthYear,
                        type = TimelineEventType.CHILD_BIRTH
                    )
                )
                events.add(
                    LifeTimelineEvent(
                        id = "event_child1_independence",
                        name = "${settings.child1Name} Independence",
                        targetYear = settings.child1BirthYear + settings.childSupportUntilAge,
                        type = TimelineEventType.CHILD_INDEPENDENCE
                    )
                )
            }
            if (settings.child2Enabled) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_child2_birth",
                        name = if (settings.child2BirthYear > settings.baseYear) "${settings.child2Name} Planned (Birth)" else "${settings.child2Name} Birth",
                        targetYear = settings.child2BirthYear,
                        type = TimelineEventType.CHILD_BIRTH
                    )
                )
                events.add(
                    LifeTimelineEvent(
                        id = "event_child2_independence",
                        name = "${settings.child2Name} Independence",
                        targetYear = settings.child2BirthYear + settings.childSupportUntilAge,
                        type = TimelineEventType.CHILD_INDEPENDENCE
                    )
                )
            }

            if (!settings.isSingleHousehold && settings.eReturnYear > settings.baseYear) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_spouse_return_to_work",
                        name = "Career Return to Work",
                        targetYear = settings.eReturnYear,
                        type = TimelineEventType.CAREER_RETURN,
                        monthlyCashFlowImpact = settings.eStartingSalary
                    )
                )
            }

            val goals = parseCustomLifeGoals(settings.customGoalsJson)
            for (g in goals) {
                events.add(
                    LifeTimelineEvent(
                        id = "goal_${g.id}",
                        name = g.name,
                        targetYear = g.targetYear,
                        type = TimelineEventType.CUSTOM_GOAL,
                        lumpSumCapitalImpact = -max(0.0, g.targetAmountCzk - g.currentSavedCzk)
                    )
                )
            }

            val horizonYears = settings.retirementHorizonYears
            if (horizonYears > 0) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_fire_target",
                        name = "Target Investment Horizon (${horizonYears}y)",
                        targetYear = settings.baseYear + horizonYears,
                        type = TimelineEventType.PRIMARY_RETIREMENT
                    )
                )
            }

            val yearsTo60 = 60 - settings.primaryAge
            if (yearsTo60 > 0) {
                val pensionCapital = settings.dpsBalanceCurrent + settings.dipBalanceCurrent +
                    (if (!settings.isSingleHousehold) settings.eDpsBalanceCurrent + settings.eDipBalanceCurrent else 0.0)
                val nominalRet = FinancialEngine.netTaxableNominalReturnPct(settings) / 100.0
                val estUnlockCapital = pensionCapital * (1.0 + nominalRet).pow(yearsTo60)
                val safeEstCapital = if (estUnlockCapital.isFinite()) max(0.0, estUnlockCapital) else pensionCapital
                events.add(
                    LifeTimelineEvent(
                        id = "event_age_60_unlock",
                        name = "DIP & DPS Liquidity Unlock",
                        targetYear = settings.baseYear + yearsTo60,
                        type = TimelineEventType.STATUTORY_UNLOCK,
                        lumpSumCapitalImpact = safeEstCapital
                    )
                )
            }

            val vPensionYears = settings.vStatePensionAge - settings.primaryAge
            if (vPensionYears > 0) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_v_state_pension",
                        name = "${settings.primaryName.ifBlank { "Vaclav" }} State Pension",
                        targetYear = settings.baseYear + vPensionYears,
                        type = TimelineEventType.PRIMARY_RETIREMENT,
                        monthlyCashFlowImpact = settings.vStatePensionMonthly
                    )
                )
            }

            if (!settings.isSingleHousehold && spouse != null) {
                val eCurrentAge = settings.baseYear - spouse.birthYear
                val ePensionYears = settings.eStatePensionAge - eCurrentAge
                if (ePensionYears > 0) {
                    events.add(
                        LifeTimelineEvent(
                            id = "event_e_state_pension",
                            name = "${settings.spouseName.ifBlank { "Eleonora" }} State Pension",
                            targetYear = settings.baseYear + ePensionYears,
                            type = TimelineEventType.SPOUSE_RETIREMENT,
                            monthlyCashFlowImpact = settings.eStatePensionMonthly
                        )
                    )
                }
            }

            return HouseholdProfile(
                primaryEarner = primary,
                spouseEarner = spouse,
                children = children,
                timelineEvents = events,
                isSingleHousehold = settings.isSingleHousehold,
                liquidPortfolioCurrent = settings.liquidPortfolioCurrent + (if (!settings.isSingleHousehold) settings.eLiquidPortfolioCurrent else 0.0),
                emergencyReserveCurrent = settings.emergencyReserveCurrent,
                baseYear = settings.baseYear
            )
        }
    }

    fun toSettings(baseSettings: SettingsEntity = SettingsEntity()): SettingsEntity {
        val c1 = children.firstOrNull { it.id == "child_1" } ?: children.getOrNull(0)
        val c2 = children.firstOrNull { it.id == "child_2" } ?: (if (children.size > 1) children[1] else null)
        val spouse = spouseEarner
        val spouseLiquid = if (!this.isSingleHousehold) baseSettings.eLiquidPortfolioCurrent else 0.0
        val primaryLiquid = (this.liquidPortfolioCurrent - spouseLiquid).coerceAtLeast(0.0)

        return baseSettings.copy(
            isSingleHousehold = this.isSingleHousehold,
            baseYear = this.baseYear,
            primaryAge = max(18, this.baseYear - primaryEarner.birthYear),
            primaryName = primaryEarner.name,
            spouseName = spouse?.name ?: baseSettings.spouseName,
            vSalary = primaryEarner.currentMonthlyGrossSalary,
            vSalaryGrowthPct = primaryEarner.salaryGrowthPct,
            dpsBalanceCurrent = primaryEarner.pensionProfile.dpsBalanceCurrent,
            dpsOwnContributionMonthly = primaryEarner.pensionProfile.dpsMonthlyContribution,
            dipBalanceCurrent = primaryEarner.pensionProfile.dipBalanceCurrent,
            dipContributionMonthly = primaryEarner.pensionProfile.dipMonthlyContribution,
            employerRetirementMonthly = primaryEarner.pensionProfile.employerContributionMonthly,
            vStatePensionAge = primaryEarner.targetPensionAge,
            eStartingSalary = spouse?.currentMonthlyGrossSalary ?: baseSettings.eStartingSalary,
            eSalaryGrowthPct = spouse?.salaryGrowthPct ?: baseSettings.eSalaryGrowthPct,
            eDpsBalanceCurrent = spouse?.pensionProfile?.dpsBalanceCurrent ?: baseSettings.eDpsBalanceCurrent,
            eDpsOwnContributionMonthly = spouse?.pensionProfile?.dpsMonthlyContribution ?: baseSettings.eDpsOwnContributionMonthly,
            eDipBalanceCurrent = spouse?.pensionProfile?.dipBalanceCurrent ?: baseSettings.eDipBalanceCurrent,
            eDipContributionMonthly = spouse?.pensionProfile?.dipMonthlyContribution ?: baseSettings.eDipContributionMonthly,
            eEmployerRetirementMonthly = spouse?.pensionProfile?.employerContributionMonthly ?: baseSettings.eEmployerRetirementMonthly,
            eStatePensionAge = spouse?.targetPensionAge ?: baseSettings.eStatePensionAge,
            child1Enabled = c1 != null,
            child1Name = c1?.name ?: baseSettings.child1Name,
            child1BirthYear = c1?.birthYear ?: baseSettings.child1BirthYear,
            child2Enabled = c2 != null,
            child2Name = c2?.name ?: baseSettings.child2Name,
            child2BirthYear = c2?.birthYear ?: baseSettings.child2BirthYear,
            liquidPortfolioCurrent = primaryLiquid,
            emergencyReserveCurrent = this.emergencyReserveCurrent
        )
    }
}
