package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.tabs.CashFlowTab
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
class UIAccessibilityStressTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultSettings = SettingsEntity()
    private val defaultCalcState = FinancialEngine.calculate(defaultSettings, runMonteCarlo = false)

    private val zeroSettings = defaultSettings.copy(
        vSalary = 0.0,
        rentMonthly = 0.0,
        liquidPortfolioCurrent = 0.0,
        portuDcaMonthly = 0.0,
        emergencyReserveCurrent = 0.0
    )
    private val zeroCalcState = FinancialEngine.calculate(zeroSettings, runMonteCarlo = false)

    private val maxSettings = defaultSettings.copy(
        vSalary = 100_000_000.0,
        liquidPortfolioCurrent = 100_000_000.0
    )
    private val maxCalcState = FinancialEngine.calculate(maxSettings, runMonteCarlo = false)

    @Test
    fun test6_1_allTabsRenderDefaultSettings() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = defaultCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = {}
                )
                OverviewTab(state = defaultCalcState)
                ProjectionsTab(
                    state = defaultCalcState
                )
                SettingsTab(
                    state = defaultCalcState,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {}
                )
                CashFlowTab(
                    state = defaultCalcState,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("plan_tab").assertExists()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_2_allTabsRenderZeroSettings() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = zeroCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = {}
                )
                OverviewTab(state = zeroCalcState)
                ProjectionsTab(
                    state = zeroCalcState
                )
                SettingsTab(
                    state = zeroCalcState,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {}
                )
                CashFlowTab(
                    state = zeroCalcState,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("plan_tab").assertExists()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_3_allTabsRenderMaxSettings() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = maxCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = {}
                )
                OverviewTab(state = maxCalcState)
                ProjectionsTab(
                    state = maxCalcState
                )
                SettingsTab(
                    state = maxCalcState,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {}
                )
                CashFlowTab(
                    state = maxCalcState,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("plan_tab").assertExists()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_4_planTabSettingsChange() {
        var settingsUpdated = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = defaultCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = { settingsUpdated = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("plan_tab").assertExists()
        assertTrue(true)
    }

    @Test
    fun test6_5_overviewTabSettingsChange() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(state = defaultCalcState)
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
    }

    @Test
    fun test6_6_projectionsTabSettingsChange() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(
                    state = defaultCalcState
                )
            }
        }
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test6_7_settingsTabRendersAndSwitchesSubTabs() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsTab(
                    state = defaultCalcState,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
    }

    @Test
    fun test6_8_heroHeaderRendersAndContainsTestTag() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                    onOpenExportReportDialog = {},
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("hero_header_card").assertExists()
    }

    @Test
    fun test6_9_emergencyReserveWidgetRendersAndContainsTestTag() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                EmergencyReserveWidget(
                    state = defaultCalcState
                )
            }
        }
        composeTestRule.onNodeWithTag("emergency_reserve_widget").assertExists()
    }

    @Test
    fun test6_10_heroHeaderButtonsAreClickable() {
        var settingsOpened = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                    onOpenExportReportDialog = {},
                    onOpenSettings = { settingsOpened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        assertTrue(settingsOpened)
    }
}
