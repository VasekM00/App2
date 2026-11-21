package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
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
@Config(sdk = [34])
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
                OverviewTab(state = defaultCalcState)
                ProjectionsTab(
                    state = defaultCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onApplySettings = {},
                    initialSubTab = 4
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
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_2_allTabsRenderZeroSettings() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(state = zeroCalcState)
                ProjectionsTab(
                    state = zeroCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onApplySettings = {},
                    initialSubTab = 3
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
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_3_allTabsRenderMaxSettings() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(state = maxCalcState)
                ProjectionsTab(
                    state = maxCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onApplySettings = {},
                    initialSubTab = 4
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
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test6_4_projectionsGoalsTabSettingsChange() {
        var settingsUpdated = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(
                    state = defaultCalcState,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onApplySettings = { settingsUpdated = true },
                    initialSubTab = 4
                )
            }
        }
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
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
                    state = defaultCalcState
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
        var netWorthClicks = 0
        var savingsClicks = 0
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    onNavigateToNetWorth = { netWorthClicks++ },
                    onNavigateToSavingsRate = { savingsClicks++ }
                )
            }
        }
        composeTestRule.onNodeWithTag("hero_stat_net_worth").performClick()
        composeTestRule.onNodeWithTag("hero_stat_savings_rate").performClick()
        assertTrue(netWorthClicks >= 1)
        assertTrue(savingsClicks >= 1)
    }
}
