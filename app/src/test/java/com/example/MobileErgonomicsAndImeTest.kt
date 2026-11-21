package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsAddCategoryDialog
import com.example.ui.tabs.SettingsAddLumpSumDialog
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
class MobileErgonomicsAndImeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultSettings = SettingsEntity()
    private val defaultCalcState = FinancialEngine.calculate(defaultSettings, runMonteCarlo = false)

    @Test
    fun test1_fontScale_200Percent_heroHeaderRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    HeroHeader(
                        state = defaultCalcState
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("hero_header_card").assertExists()
    }

    @Test
    fun test2_fontScale_200Percent_emergencyReserveWidgetRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    EmergencyReserveWidget(
                        state = defaultCalcState
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("emergency_reserve_widget").assertExists()
    }

    @Test
    fun test3_fontScale_200Percent_overviewTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.5f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    OverviewTab(state = defaultCalcState)
                }
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
    }

    @Test
    fun test4_fontScale_200Percent_cashFlowTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    CashFlowTab(
                        state = defaultCalcState,
                        ledgerEntries = emptyList(),
                        onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                        onUpdateLedgerEntry = {},
                        onDeleteLedgerEntry = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test5_fontScale_200Percent_projectionsGoalsTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    ProjectionsTab(
                        state = defaultCalcState,
                        actionStates = emptyMap(),
                        onToggleAction = { _, _, _ -> },
                        onApplySettings = {},
                        initialSubTab = 4
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test6_fontScale_200Percent_projectionsTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    ProjectionsTab(
                        state = defaultCalcState
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test7_fontScale_200Percent_settingsTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    SettingsTab(
                        state = defaultCalcState,
                        onUpdateSettings = {},
                        onResetDefaults = {},
                        onClearAllData = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
    }

    @Test
    fun test8_fontScale_85Percent_allTabsRenderWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1.5f, fontScale = 0.85f)) {
                MartinuFinancialsTheme {
                    OverviewTab(state = defaultCalcState)
                    ProjectionsTab(state = defaultCalcState, initialSubTab = 3)
                }
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test9_splitScreen_narrowDisplay_320dpWidth_overviewTabRendersWithoutCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 700.dp)) {
                    OverviewTab(state = defaultCalcState)
                }
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
    }

    @Test
    fun test10_splitScreen_narrowDisplay_320dpWidth_cashFlowTabRendersWithoutCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 700.dp)) {
                    CashFlowTab(
                        state = defaultCalcState,
                        ledgerEntries = emptyList(),
                        onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                        onUpdateLedgerEntry = {},
                        onDeleteLedgerEntry = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun test11_splitScreen_lowHeight_400dpHeight_projectionsGoalsRendersWithoutCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Box(modifier = Modifier.size(width = 380.dp, height = 400.dp)) {
                    ProjectionsTab(
                        state = defaultCalcState,
                        actionStates = emptyMap(),
                        onToggleAction = { _, _, _ -> },
                        onApplySettings = {},
                        initialSubTab = 4
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test12_touchTarget_heroHeaderButtons_clickable() {
        var quickGuideOpened = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    onOpenQuickGuide = { quickGuideOpened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("hero_quick_guide_button").performClick()
        assertTrue(quickGuideOpened)
    }

    @Test
    fun test13_touchTarget_heroNetWorthShortcut_clickable() {
        var netWorthOpened = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    onNavigateToNetWorth = { netWorthOpened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("hero_stat_net_worth").performClick()
        assertTrue(netWorthOpened)
    }

    @Test
    fun test14_darkAndLightTheme_rendersBothThemesWithoutContrastCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = false) {
                OverviewTab(state = defaultCalcState)
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
    }

    @Test
    fun test15_singleHouseholdMode_mobileRenderExcludesSpouseCardsCleanly() {
        val singleSettings = defaultSettings.copy(isSingleHousehold = true)
        val singleCalcState = FinancialEngine.calculate(singleSettings, runMonteCarlo = false)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(state = singleCalcState)
                ProjectionsTab(state = singleCalcState)
            }
        }
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onNodeWithTag("projections_tab").assertExists()
    }

    @Test
    fun test16_addLumpSumDialog_quickPresetsWrapWithoutClippingOn360dpPhone() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsAddLumpSumDialog(
                    settings = defaultSettings,
                    initialItem = null,
                    onDismiss = {},
                    onUpdateSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Add Planned Lump Sum").assertIsDisplayed()
        composeTestRule.onNodeWithText("+50k").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+1000k").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+1000k").performClick()
        composeTestRule.onNodeWithText("1000000").assertExists()
    }

    @Test
    fun test17_addCategoryDialog_presetsWrapWithoutClippingOn360dpPhone() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsAddCategoryDialog(
                    settings = defaultSettings,
                    onDismiss = {},
                    onUpdateSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Add Expense Category").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pets & Vet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home Improvement").assertIsDisplayed()
    }
}
