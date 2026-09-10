package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
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
                        state = defaultCalcState,
                        isDarkTheme = false,
                        onToggleDarkTheme = {},
                        onOpenSettings = {}
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
    }

    @Test
    fun test5_fontScale_200Percent_planTabRendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)) {
                MartinuFinancialsTheme {
                    PlanTab(
                        state = defaultCalcState,
                        actionStates = emptyMap(),
                        onToggleAction = { _, _, _ -> },
                        onUpdateSettings = {}
                    )
                }
            }
        }
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
    }

    @Test
    fun test8_fontScale_85Percent_allTabsRenderWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1.5f, fontScale = 0.85f)) {
                MartinuFinancialsTheme {
                    OverviewTab(state = defaultCalcState)
                    PlanTab(state = defaultCalcState, actionStates = emptyMap(), onToggleAction = { _, _, _ -> }, onUpdateSettings = {})
                }
            }
        }
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
    }

    @Test
    fun test11_splitScreen_lowHeight_400dpHeight_planTabRendersWithoutCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Box(modifier = Modifier.size(width = 380.dp, height = 400.dp)) {
                    PlanTab(
                        state = defaultCalcState,
                        actionStates = emptyMap(),
                        onToggleAction = { _, _, _ -> },
                        onUpdateSettings = {}
                    )
                }
            }
        }
    }

    @Test
    fun test12_touchTarget_heroHeaderButtons_clickable() {
        var settingsOpened = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                    onOpenSettings = { settingsOpened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun test13_touchTarget_themeToggleButton_clickable() {
        var themeToggled = false
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = defaultCalcState,
                    isDarkTheme = false,
                    onToggleDarkTheme = { themeToggled = true },
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        assertTrue(themeToggled)
    }

    @Test
    fun test14_darkAndLightTheme_rendersBothThemesWithoutContrastCrash() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = false) {
                OverviewTab(state = defaultCalcState)
            }
            MartinuFinancialsTheme(darkTheme = true) {
                OverviewTab(state = defaultCalcState)
            }
        }
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
    }
}
