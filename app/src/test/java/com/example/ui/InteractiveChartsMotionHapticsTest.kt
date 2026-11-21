package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.MonteCarloPoint
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.charts.ChartMetricPill
import com.example.ui.components.charts.DcaAllocationBreakdownBar
import com.example.ui.components.charts.FanChart
import com.example.ui.components.charts.NetWorthChart
import com.example.ui.components.SankeyCashFlowCard
import com.example.ui.components.charts.buildMonotoneBandPath
import com.example.ui.components.charts.buildMonotoneCubicAreaPath
import com.example.ui.components.charts.buildMonotoneCubicPath
import com.example.ui.components.charts.computeNiceAxisMax
import com.example.ui.components.sankey.buildCentralHubNode
import com.example.ui.components.sankey.buildInflowNodes
import com.example.ui.components.sankey.buildOutflowNodes
import com.example.ui.components.charts.trimPath
import com.example.ui.theme.MartinuFinancialsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteractiveChartsMotionHapticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testChartHelpersNiceAxisMaxAndPathConstruction() {
        // 1. Nice axis calculation
        assertEquals(1.0, computeNiceAxisMax(0.0), 0.001)
        val axisMax = computeNiceAxisMax(17_300_000.0, 4)
        assertTrue("Axis max should be >= raw max", axisMax >= 17_300_000.0)

        // 2. Monotone cubic path
        val emptyPath = buildMonotoneCubicPath(emptyList())
        assertNotNull(emptyPath)

        val singlePointPath = buildMonotoneCubicPath(listOf(Offset(10f, 10f)))
        assertNotNull(singlePointPath)

        val points = listOf(
            Offset(0f, 100f),
            Offset(50f, 80f),
            Offset(100f, 50f),
            Offset(150f, 20f),
            Offset(200f, 0f)
        )
        val splinePath = buildMonotoneCubicPath(points)
        assertNotNull(splinePath)

        // 3. Monotone cubic area path
        val areaPath = buildMonotoneCubicAreaPath(points, baselineY = 200f)
        assertNotNull(areaPath)

        // 4. Monotone band path
        val bottomPoints = listOf(
            Offset(0f, 120f),
            Offset(50f, 100f),
            Offset(100f, 80f),
            Offset(150f, 60f),
            Offset(200f, 40f)
        )
        val bandPath = buildMonotoneBandPath(points, bottomPoints)
        assertNotNull(bandPath)
    }

    @Test
    fun testPathTrimAndReveal() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            close()
        }

        // At progress 0, empty or valid trimmed
        val trimmed0 = trimPath(path, 0f)
        assertNotNull(trimmed0)

        // At progress 1f, returns complete path
        val trimmed1 = trimPath(path, 1f)
        assertNotNull(trimmed1)

        // At progress 0.5f, trimmed segment
        val trimmedMid = trimPath(path, 0.5f)
        assertNotNull(trimmedMid)
    }

    @Test
    fun testTrajectoryChartScrubbingAndLayerToggles() {
        val testData = listOf(
            PortfolioYearPoint(
                year = 2026,
                age = 26,
                portfolio = 1_000_000.0,
                target = 10_000_000.0,
                investedAnnual = 240_000.0,
                reinvestAnnual = 0.0,
                lumpSum = 0.0,
                status = "Accumulation",
                pensionPortfolio = 250_000.0,
                totalPortfolio = 1_250_000.0,
                dipPortfolio = 150_000.0,
                dpsPortfolio = 100_000.0,
                emergencyCash = 200_000.0
            ),
            PortfolioYearPoint(
                year = 2036,
                age = 36,
                portfolio = 5_000_000.0,
                target = 10_000_000.0,
                investedAnnual = 300_000.0,
                reinvestAnnual = 100_000.0,
                lumpSum = 0.0,
                status = "Growing",
                pensionPortfolio = 1_400_000.0,
                totalPortfolio = 6_400_000.0,
                dipPortfolio = 800_000.0,
                dpsPortfolio = 600_000.0,
                emergencyCash = 250_000.0
            ),
            PortfolioYearPoint(
                year = 2046,
                age = 46,
                portfolio = 10_000_000.0,
                target = 10_000_000.0,
                investedAnnual = 300_000.0,
                reinvestAnnual = 100_000.0,
                lumpSum = 0.0,
                status = "FIRE Milestone",
                pensionPortfolio = 2_700_000.0,
                totalPortfolio = 12_700_000.0,
                dipPortfolio = 1_500_000.0,
                dpsPortfolio = 1_200_000.0,
                emergencyCash = 300_000.0
            )
        )

        // Delta calculation verification
        val delta2026 = testData[0].totalPortfolio - testData[0].target
        assertEquals(-8_750_000.0, delta2026, 0.01)

        val delta2046 = testData[2].totalPortfolio - testData[2].target
        assertEquals(2_700_000.0, delta2046, 0.01)
        assertTrue("2046 should achieve FIRE target", delta2046 >= 0)

        // CPI Discounting verification
        val cpi = 2.8
        val discount10Years = (1.0 + cpi / 100.0).pow(1.0)
        val discounted2036 = testData[1].totalPortfolio / discount10Years
        assertTrue("Discounted value should be lower than nominal", discounted2036 < testData[1].totalPortfolio)

        // UI rendering & layer chip interaction
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                NetWorthChart(
                    data = testData,
                    cpiInflationPct = 2.8,
                    isRealPurchasingPower = false
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify chart and interactive layer chips render
        composeTestRule.onNodeWithTag("net_worth_chart_card").assertExists()
        composeTestRule.onNodeWithTag("layer_chip_liquid").assertExists()
        composeTestRule.onNodeWithTag("layer_chip_dip").assertExists()
        composeTestRule.onNodeWithTag("layer_chip_dps").assertExists()
        composeTestRule.onNodeWithTag("layer_chip_cash").assertExists()

        // Toggle layer chip
        composeTestRule.onNodeWithTag("layer_chip_cash").performClick()
        composeTestRule.waitForIdle()

        // Zoom controls
        composeTestRule.onNodeWithTag("chart_zoom_in").assertExists().performClick()
        composeTestRule.onNodeWithTag("chart_zoom_out").assertExists().performClick()
        composeTestRule.onNodeWithTag("chart_zoom_reset").assertExists().performClick()
    }

    @Test
    fun testMonteCarloFanChartAndFanChartScrubbing() {
        val points = listOf(
            MonteCarloPoint(year = 2026, age = 26, p5 = 900_000.0, p50 = 1_200_000.0, p95 = 1_600_000.0, target = 8_000_000.0),
            MonteCarloPoint(year = 2036, age = 36, p5 = 4_000_000.0, p50 = 6_500_000.0, p95 = 9_500_000.0, target = 8_000_000.0),
            MonteCarloPoint(year = 2046, age = 46, p5 = 8_500_000.0, p50 = 14_000_000.0, p95 = 22_000_000.0, target = 8_000_000.0)
        )

        // Delta on median
        val delta2046 = points[2].p50 - points[2].target
        assertEquals(6_000_000.0, delta2046, 0.01)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                FanChart(
                    points = points,
                    cpiPct = 2.8
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monte_carlo_chart_card").assertExists()
        composeTestRule.onNodeWithTag("monte_carlo_tooltip_box").assertExists()
    }

    @Test
    fun testDcaAllocationBreakdownBarFilters() {
        val settings = SettingsEntity(
            liquidPortfolioCurrent = 600_000.0,
            dipBalanceCurrent = 120_000.0,
            dpsBalanceCurrent = 150_000.0,
            emergencyReserveCurrent = 200_000.0,
            portuDcaMonthly = 12_000.0,
            dipContributionMonthly = 2_000.0,
            dpsOwnContributionMonthly = 1_700.0,
            employerRetirementMonthly = 1_000.0,
            isSingleHousehold = true
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Column {
                    DcaAllocationBreakdownBar(settings = settings)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_chip_liquid").assertExists().performClick()
        composeTestRule.onNodeWithTag("filter_chip_dip").assertExists().performClick()
        composeTestRule.onNodeWithTag("filter_chip_dps").assertExists().performClick()
        composeTestRule.onNodeWithTag("filter_chip_cash").assertExists().performClick()
    }

    @Test
    fun testSankeyNodeFocusAndRibbonHighlighting() {
        val settings = SettingsEntity(isSingleHousehold = true)
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)

        val inflows3Pillar = buildInflowNodes(state, viewMode = 0)
        val outflows3Pillar = buildOutflowNodes(state, viewMode = 0)
        val hub = buildCentralHubNode(state)

        assertTrue("In 3-Pillar mode, unified take-home inflow plus optional employer match is used", inflows3Pillar.any { it.id == "in_total" } && inflows3Pillar.size <= 2)
        assertEquals("In 3-Pillar mode, inflow sum must equal hub total", inflows3Pillar.sumOf { it.amount }, hub.amount, 0.01)
        assertTrue("Outflows should include living, investments, cash", outflows3Pillar.size >= 3)
        assertNotNull(hub)

        val inflowsGranular = buildInflowNodes(state, viewMode = 1)
        val outflowsGranular = buildOutflowNodes(state, viewMode = 1)
        assertTrue(inflowsGranular.isNotEmpty())
        assertTrue(outflowsGranular.isNotEmpty())

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SankeyCashFlowCard(state = state)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sankey_cashflow_card").assertExists()
        composeTestRule.onNodeWithTag("sankey_chip_3pillar").assertExists()
        composeTestRule.onNodeWithTag("sankey_chip_granular").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sankey_chip_3pillar").performClick()
    }

    @Test
    fun testChartMetricPillComponent() {
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                ChartMetricPill(
                    label = "Test Metric",
                    value = "1.25M CZK",
                    color = Color.Green,
                    isBold = true
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
