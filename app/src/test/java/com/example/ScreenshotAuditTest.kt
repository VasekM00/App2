package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.components.QuickGuideDialog
import com.example.ui.components.SankeyCashFlowCard
import com.example.ui.components.charts.StressComparisonChart
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab
import com.example.ui.theme.MartinuFinancialsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
        composeTestRule.onNodeWithTag("decumulation_comparison_card").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_sandbox_lower.png")
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

    @Test
    fun captureOverviewTabDark() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OverviewTab(state = state)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/overview_dark.png")
    }

    @Test
    fun captureSettingsTabDark() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
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
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/settings_dark.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun captureOverviewTabNarrow() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OverviewTab(state = state)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/overview_narrow.png")
    }

    @Test
    fun captureCashFlowIncomeSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                CashFlowTab(
                    state = state,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {},
                    initialSubTab = 1
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/cashflow_income.png")
    }

    @Test
    fun captureCashFlowExpensesSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                CashFlowTab(
                    state = state,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {},
                    initialSubTab = 2
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/cashflow_expenses.png")
    }

    @Test
    fun captureCashFlowLedgerSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                CashFlowTab(
                    state = state,
                    ledgerEntries = emptyList(),
                    onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLedgerEntry = {},
                    onDeleteLedgerEntry = {},
                    onImportCsv = {},
                    initialSubTab = 3
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/cashflow_ledger.png")
    }

    @Test
    fun captureProjectionsPensionSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(state = state, initialSubTab = 3)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_pension.png")
    }

    @Test
    fun captureSettingsPortfolioSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsTab(
                    state = state,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {},
                    initialSubTab = 1
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/settings_portfolio.png")
    }

    @Test
    fun captureSettingsDataSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SettingsTab(
                    state = state,
                    onUpdateSettings = {},
                    onResetDefaults = {},
                    onClearAllData = {},
                    liveRegulatoryData = null,
                    isSyncing = false,
                    onSyncLiveCzechData = {},
                    initialSubTab = 2
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/settings_data.png")
    }

    @Test
    fun captureProjectionsGoalsSubTab() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ProjectionsTab(state = state, initialSubTab = 4)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_goals.png")
    }

    @Test
    fun captureCashFlowTabDark() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = true) {
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
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/cash_flow_dark.png")
    }

    @Test
    fun captureProjectionsTabDark() {
        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = true) {
                ProjectionsTab(state = state)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_dark.png")
    }

    @Test
    fun captureMainScreenHomeHelp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun clearDbSingleton() {
            runCatching {
                val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
                field.isAccessible = true
                (field.get(null) as? AppDatabase)?.close()
                field.set(null, null)
            }
            context.deleteDatabase("martinu_financials_db")
        }

        clearDbSingleton()
        val viewModel = MainViewModel(context)
        try {
            composeTestRule.setContent {
                MartinuFinancialsTheme {
                    MainScreen(
                        viewModel = viewModel,
                        isDarkTheme = false,
                        onToggleDarkTheme = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("action_quick_guide").assertIsDisplayed()
            composeTestRule.onNodeWithTag("hero_header_card").assertIsDisplayed()
            composeTestRule.onRoot().captureRoboImage("src/test/screenshots/mainscreen_home_help.png")
        } finally {
            runCatching {
                val job = viewModel.viewModelScope.coroutineContext[Job]
                runBlocking {
                    job?.cancel()
                    withTimeoutOrNull(3000) { job?.join() }
                }
                shadowOf(Looper.getMainLooper()).idle()
            }
            clearDbSingleton()
        }
    }

    @Test
    fun captureQuickGuideDialog() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                QuickGuideDialog(onDismiss = {}, onGoToSetup = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_guide_dialog").captureRoboImage("src/test/screenshots/quick_guide_dialog.png")
    }

    @Test
    fun captureQuickGuideDialogLargeFont() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.6f)
        try {
            composeTestRule.setContent {
                MartinuFinancialsTheme {
                    QuickGuideDialog(onDismiss = {}, onGoToSetup = {})
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("quick_guide_dialog").captureRoboImage("src/test/screenshots/quick_guide_dialog_large_font.png")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1.0f)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun captureSankeyByCategory() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        SankeyCashFlowCard(state = state)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sankey_chip_granular").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/sankey_by_category.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    fun captureSankeySelectedDetail() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        SankeyCashFlowCard(state = state)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sankey_chip_granular").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Rent").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("FOCUSED STREAM").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/sankey_selected_detail.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun captureSankeyNarrowLargeFont() {
        composeTestRule.setContent {
            val curDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = curDensity.density, fontScale = 1.6f)
            ) {
                MartinuFinancialsTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            SankeyCashFlowCard(state = state)
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sankey_chip_granular").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/sankey_narrow_large_font.png")
    }

    @Test
    fun captureProjectionsStressChart() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(16.dp)
                ) {
                    StressComparisonChart(scenarios = state.stressScenarios)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/projections_stress_chart.png")
    }

    @Test
    fun quickGuideDialogDisplaysGiftExemptionAndManualPrecedenceAndFiltersCleanly() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                QuickGuideDialog(onDismiss = {}, onGoToSetup = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome! How the app works").assertIsDisplayed()

        // Filter to Financial Engine -> manual edits vs bank imports precedence must be visible
        composeTestRule.onNodeWithTag("guide_filter_engine").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Manual Edits vs Bank Imports & Ledger").assertIsDisplayed()
        composeTestRule.onNodeWithText("Czech Statutory Tax Perks (§ ZDP)").assertDoesNotExist()

        // Filter to Czech Tax Perks -> parental gift tax exemption & 68k exclusion must be visible
        composeTestRule.onNodeWithTag("guide_filter_tax_rules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Parental & Direct-Line Family Gifts (§ 4a & § 10(3)(c) ZDP)").assertIsDisplayed()
        composeTestRule.onNodeWithText("disregards all family gifts", substring = true).assertIsDisplayed()

        // Filter to Privacy & Banking -> supported banks & cross-account netting audit visible
        composeTestRule.onNodeWithTag("guide_filter_privacy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Automated Bank Statement Parser").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cross-Account Netting Audit").performScrollTo().assertIsDisplayed()
    }
}
