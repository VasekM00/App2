package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.RetirementSurvivalResult
import com.example.ui.MainViewModel
import com.example.ui.components.PhasedPreRetirementBaristaCard
import com.example.ui.tabs.OverviewTab
import com.example.ui.theme.MartinuFinancialsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class Section3APerformanceAndBuildTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun resolveAppDir(): File {
        val candidates = listOf(File("app"), File("."))
        return candidates.first { File(it, "build.gradle.kts").exists() }
    }

    @Test
    fun mainViewModel_actionChecklistToggleSkipsRedundantMonteCarloRecompute() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runCatching {
            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            (field.get(null) as? AppDatabase)?.close()
            field.set(null, null)
        }
        app.deleteDatabase("martinu_financials_db")

        val vm = MainViewModel(app, SavedStateHandle())
        val collectJob = vm.viewModelScope.launch(Dispatchers.Default) {
            vm.calculationState.collect {}
        }
        val deadline = System.currentTimeMillis() + 8000L
        while (System.currentTimeMillis() < deadline && vm.stochasticRecomputeCount.get() < 1) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
        assertTrue("Initial stochastic calculation must complete", vm.stochasticRecomputeCount.get() >= 1)
        val countAfterInit = vm.stochasticRecomputeCount.get()

        vm.toggleAction(2026, "ac1", false)
        val toggleDeadline = System.currentTimeMillis() + 600L
        while (System.currentTimeMillis() < toggleDeadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }

        assertEquals(
            "Action checklist toggle must NOT trigger redundant stochastic Monte Carlo recomputation",
            countAfterInit,
            vm.stochasticRecomputeCount.get()
        )

        collectJob.cancel()
        runBlocking {
            val job = vm.viewModelScope.coroutineContext[Job]
            job?.cancel()
            withTimeoutOrNull(2000) { job?.join() }
        }
        runCatching {
            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            (field.get(null) as? AppDatabase)?.close()
            field.set(null, null)
        }
        app.deleteDatabase("martinu_financials_db")
    }

    @Test
    fun overviewTab_respectsUseHistoricalBootstrapFlagForSurvivalTile() {
        val baseSettings = SettingsEntity.freshDefaults().copy(
            monteCarloN = 100,
            useHistoricalBootstrap = false
        )
        val fakeState = FinancialEngine.calculate(baseSettings, runMonteCarlo = false).copy(
            retirementSurvival = RetirementSurvivalResult(
                successRatePct = 91.0,
                fireProbabilityPct = 100.0,
                horizonYears = 35,
                medianEndBalanceToday = 10_000_000.0,
                p5EndBalanceToday = 2_000_000.0,
                medianDepletionAge = null,
                sampleSize = 100,
                fireReachedCount = 100
            ),
            historicalRetirementSurvival = RetirementSurvivalResult(
                successRatePct = 44.0,
                fireProbabilityPct = 100.0,
                horizonYears = 35,
                medianEndBalanceToday = 1_000_000.0,
                p5EndBalanceToday = 0.0,
                medianDepletionAge = 72,
                sampleSize = 100,
                fireReachedCount = 100
            )
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(state = fakeState)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("strategic_tile_survival").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("91% Pass", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monte Carlo stress test", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun buildProguardBrandingAndEnglishOnlyHygieneVerified() {
        val appDir = resolveAppDir()
        val gradleText = File(appDir, "build.gradle.kts").readText()
        val proguardText = File(appDir, "proguard-rules.pro").readText()
        val mainActivityText = File(appDir, "src/main/java/com/example/MainActivity.kt").readText()
        val valuesCsDir = File(appDir, "src/main/res/values-cs")

        assertTrue(gradleText.contains("""applicationId = "com.aistudio.martinufinancials.fire2""""))
        assertTrue(gradleText.contains("""namespace = "com.example""""))
        assertTrue(gradleText.contains("targetSdk = 35"))

        assertFalse("Blanket com.example.domain.** keep must be removed", proguardText.contains("-keep class com.example.domain.**"))
        assertFalse("Blanket com.example.data.** keep must be removed", proguardText.contains("-keep class com.example.data.**"))
        assertTrue("SettingsEntity keep must be present", proguardText.contains("-keep class com.example.data.SettingsEntity"))
        assertTrue("UserInvestmentLot keep must be present", proguardText.contains("-keep class com.example.util.UserInvestmentLot"))

        assertTrue(mainActivityText.contains("Unlock Personal Finance Tracker"))
        assertFalse(mainActivityText.contains("Unlock Martinu Financials"))
        assertFalse("App must stay English only (no values-cs per user request)", valuesCsDir.exists())
    }

    @Test
    fun phasedBaristaCard_newPostFireControlsUpdateSettings() {
        val settingsState = mutableStateOf(
            SettingsEntity.freshDefaults().copy(
                baristaTransitionEnabled = true,
                baristaStartAtFire = false,
                baristaContinuePostFire = true,
                baristaEndAge = 0
            )
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PhasedPreRetirementBaristaCard(
                        state = FinancialEngine.calculate(settingsState.value, runMonteCarlo = false),
                        onUpdateSettings = { settingsState.value = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("barista_continue_post_fire_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(settingsState.value.baristaContinuePostFire)

        composeTestRule.onNodeWithTag("barista_end_age_input").performScrollTo().performTextReplacement("58")
        composeTestRule.waitForIdle()
        assertEquals(58, settingsState.value.baristaEndAge)

        composeTestRule.onNodeWithTag("barista_start_at_fire_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(settingsState.value.baristaStartAtFire)
    }
}
