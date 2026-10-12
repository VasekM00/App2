package com.example.domain

import com.example.data.ELEONORA_BIRTH_YEAR
import com.example.data.SettingsEntity
import com.example.data.VACLAV_BIRTH_YEAR

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
                name = "Vaclav",
                role = HouseholdRole.PRIMARY_EARNER,
                birthYear = VACLAV_BIRTH_YEAR,
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
                    name = "Eleonora",
                    role = HouseholdRole.SPOUSE_EARNER,
                    birthYear = ELEONORA_BIRTH_YEAR,
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
                        name = "Child 1",
                        role = HouseholdRole.DEPENDENT_CHILD,
                        birthYear = settings.child1BirthYear
                    )
                )
            }
            if (settings.child2Enabled) {
                children.add(
                    HouseholdMember(
                        id = "child_2",
                        name = "Child 2",
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
                        name = "Child 1 Birth",
                        targetYear = settings.child1BirthYear,
                        type = TimelineEventType.CHILD_BIRTH
                    )
                )
                events.add(
                    LifeTimelineEvent(
                        id = "event_child1_independence",
                        name = "Child 1 Independence",
                        targetYear = settings.child1BirthYear + settings.childSupportUntilAge,
                        type = TimelineEventType.CHILD_INDEPENDENCE
                    )
                )
            }
            if (settings.child2Enabled) {
                events.add(
                    LifeTimelineEvent(
                        id = "event_child2_birth",
                        name = "Child 2 Birth",
                        targetYear = settings.child2BirthYear,
                        type = TimelineEventType.CHILD_BIRTH
                    )
                )
                events.add(
                    LifeTimelineEvent(
                        id = "event_child2_independence",
                        name = "Child 2 Independence",
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
                        type = TimelineEventType.CAREER_RETURN
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
                        lumpSumCapitalImpact = -g.targetAmountCzk
                    )
                )
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
        val c1 = children.getOrNull(0)
        val c2 = children.getOrNull(1)
        val spouse = spouseEarner

        return baseSettings.copy(
            isSingleHousehold = this.isSingleHousehold,
            baseYear = this.baseYear,
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
            child1BirthYear = c1?.birthYear ?: baseSettings.child1BirthYear,
            child2Enabled = c2 != null,
            child2BirthYear = c2?.birthYear ?: baseSettings.child2BirthYear,
            emergencyReserveCurrent = this.emergencyReserveCurrent
        )
    }
}
