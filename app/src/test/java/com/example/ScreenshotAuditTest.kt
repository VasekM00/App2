package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.PlanTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab
import com.example.ui.theme.MartinuFinancialsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each tab to a PNG via Roborazzi so layout/design regressions are reviewable in CI.
 * Record with: ./gradlew testDebugUnitTest --tests "*ScreenshotAuditTest*" -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val settings = SettingsEntity(monteCarloN = 100)
    private val state = FinancialEngine.calculate(settings, runMonteCarlo = true)

    @Test
    fun captureOverviewTab() {
        composeTestRule.setContent { MartinuFinancialsTheme { OverviewTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/overview.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun captureOverviewTabTall() {
        composeTestRule.setContent { MartinuFinancialsTheme { OverviewTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/overview_tall.png")
    }

    @Test
    fun captureCashFlowTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                CashFlowTab(
                    state = state,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/cashflow.png")
    }

    @Test
    fun captureProjectionsTab() {
        composeTestRule.setContent { MartinuFinancialsTheme { ProjectionsTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections.png")
    }

    @Test
    fun captureProjectionsMonteCarloSubTab() {
        composeTestRule.setContent { MartinuFinancialsTheme { ProjectionsTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("projections_subtab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_montecarlo.png")
    }

    @Test
    fun captureProjectionsSandboxSubTab() {
        composeTestRule.setContent { MartinuFinancialsTheme { ProjectionsTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("projections_subtab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_sandbox.png")
    }

    @Test
    fun captureProjectionsSandboxLowerSection() {
        composeTestRule.setContent { MartinuFinancialsTheme { ProjectionsTab(state = state) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("projections_subtab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_sandbox_lower.png")
    }

    @Test
    fun capturePlanTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                PlanTab(
                    state = state,
                    actionStates = emptyMap(),
                    onToggleAction = { _, _, _ -> },
                    onUpdateSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/plan.png")
    }

    @Test
    fun captureSettingsTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsTab(
                    state = state,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/settings.png")
    }
}
