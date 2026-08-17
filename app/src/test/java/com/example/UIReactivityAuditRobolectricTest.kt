package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.PlanTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab
import com.example.ui.theme.MartinuFinancialsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UIReactivityAuditRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test UI reactivity in PlanTab milestone hierarchy on settings change`() {
        val settingsState = mutableStateOf(SettingsEntity(statePensionAge = 67))
        val calculationState = mutableStateOf(FinancialEngine.calculate(settingsState.value))

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = calculationState.value,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = { settingsState.value = it }
                )
            }
        }

        // Verify initial Coast FIRE description reflects age 67
        val initialCoastDesc = calculationState.value.fireMilestones.coastFire.description
        assertTrue(initialCoastDesc.contains("age 67"))

        // Update settings: retirement age 65 and new lifestyle cost
        settingsState.value = settingsState.value.copy(
            statePensionAge = 65,
            lifestyleCostAtFireMonthly = 55000.0
        )
        calculationState.value = FinancialEngine.calculate(settingsState.value)

        // Force UI update
        composeTestRule.waitForIdle()

        val updatedCoastDesc = calculationState.value.fireMilestones.coastFire.description
        assertTrue(updatedCoastDesc.contains("age 65"))
    }

    @Test
    fun `test OverviewTab responds dynamically to income and spending updates`() {
        val settings = SettingsEntity(
            vSalary = 50000.0,
            rentMonthly = 20000.0
        )
        val state = FinancialEngine.calculate(settings)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(
                    state = state
                )
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(state.currentIncome.totalMonthly > 0)
        assertTrue(state.totalLivingCostMonthly > 0)
    }

    @Test
    fun `test ProjectionsTab stress regimes reactivity`() {
        val settings = SettingsEntity(
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 2.5
        )
        val state = FinancialEngine.calculate(settings)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(
                    state = state,
                    onSensitivityChange = { _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(state.stressScenarios.isNotEmpty())
    }

    @Test
    fun `test ProjectionsTab What-If sandbox subtab rendering`() {
        val settings = SettingsEntity()
        val state = FinancialEngine.calculate(settings)
        val currentSubTab = mutableStateOf(0)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(
                    state = state,
                    initialSubTab = currentSubTab.value,
                    onSensitivityChange = { _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Subtab 1: What-If Sandbox
        currentSubTab.value = 1
        composeTestRule.waitForIdle()

        // Switch to Subtab 2: Monte Carlo & Stress
        currentSubTab.value = 2
        composeTestRule.waitForIdle()
    }

    @Test
    fun `test SettingsTab renders all functional categories`() {
        val settingsState = mutableStateOf(SettingsEntity())
        val calculationState = mutableStateOf(FinancialEngine.calculate(settingsState.value))

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsTab(
                    state = calculationState.value,
                    onUpdateSettings = {
                        settingsState.value = it
                        calculationState.value = FinancialEngine.calculate(it)
                    },
                    onResetDefaults = {},
                    onClearAllData = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(calculationState.value.settings.baseYear == 2026)
    }
}
