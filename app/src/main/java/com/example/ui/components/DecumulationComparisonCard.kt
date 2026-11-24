package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DecumulationComparisonResult
import com.example.domain.DecumulationConfig
import com.example.domain.DecumulationStrategies
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import kotlin.math.roundToInt

private enum class DecumulationStrategy(
    val title: String,
    val shortLabel: String,
    val swrMultiplier: Double,
    val description: String,
    val bearMarketRule: String,
    val bullMarketRule: String,
    val taxEfficiency: String
) {
    BENGEN_4PCT(
        title = "Constant Real SWR",
        shortLabel = "Bengen SWR",
        swrMultiplier = 1.0,
        description = "Classic Trinity Study benchmark. Fixed annual spending adjusted only for CPI inflation each year.",
        bearMarketRule = "Rigid spending; no voluntary cuts during market drawdowns. Leaves sequence-of-returns risk exposed.",
        bullMarketRule = "Constant real purchasing power; doesn't ratchet up during multi-year bull runs.",
        taxEfficiency = "FIFO harvesting of 3-year exempt lots gives 0% tax drag until lots mature."
    ),
    GUYTON_KLINGER(
        title = "Guyton-Klinger Guardrails",
        shortLabel = "Guardrails",
        swrMultiplier = 1.0,
        description = "Dynamic capital preservation rules starting at baseline SWR with CPI inflation-skip and adaptive cut/boost guardrails.",
        bearMarketRule = "Capital Preservation: skips CPI raise after losing years if withdrawal rate rises, and cuts spending when upper threshold is breached.",
        bullMarketRule = "Prosperity Rule: boosts spending when live withdrawal rate drops below your lower threshold.",
        taxEfficiency = "Absorbs bear drawdowns with spending agility, minimizing forced asset liquidations."
    ),
    VARIABLE_PERCENTAGE(
        title = "Dynamic Variable (VPW)",
        shortLabel = "Variable",
        swrMultiplier = 1.075,
        description = "Proportional drawdown recalculated annually based on live portfolio balance and remaining actuarial horizon.",
        bearMarketRule = "Drawdowns shrink smoothly down to your configured essential spending floor.",
        bullMarketRule = "Super-normal gains directly expand discretionary travel and life goals budget.",
        taxEfficiency = "Optimizes timing of capital gains exemption windows across tax years."
    );

    fun effectiveSwrPct(baseSwrPct: Double): Double = (baseSwrPct * swrMultiplier).coerceIn(0.5, 20.0)

    @Composable
    fun accentColor(): Color = when (this) {
        BENGEN_4PCT -> BrandBlue
        GUYTON_KLINGER -> GoodGreen
        VARIABLE_PERCENTAGE -> BrandTeal
    }
}

@Composable
fun DecumulationComparisonCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(1) } // Default to Guyton-Klinger Guardrails
    val strategy = DecumulationStrategy.entries[selectedIndex.coerceIn(0, DecumulationStrategy.entries.lastIndex)]
    val activeAccent = strategy.accentColor()

    // Use canonical investable portfolio or FIRE target, whichever is higher
    val snap = state.snapshot
    val s = state.effectiveSettings
    val combinedPortfolio = (snap.investedLiquidTotal.finiteOrZero() + snap.pensionTotal.finiteOrZero()).coerceAtLeast(0.0)
    val portfolioBase = maxOf(
        combinedPortfolio,
        snap.activeFireTargetToday.finiteOrZero(),
        1_000_000.0
    )

    val baseSwr = s.safeWithdrawalRatePct.finiteOrZero().coerceIn(0.5, 20.0)
    val horizonYears = s.retirementHorizonYears.coerceIn(5, 60)
    var asyncComparison by remember { mutableStateOf<DecumulationComparisonResult?>(null) }
    LaunchedEffect(
        state.decumulationComparison,
        portfolioBase, baseSwr, horizonYears, s.cpiInflationPct, s.portfolioNominalReturnPct,
        s.dividendYieldPct, s.dividendTaxRatePct,
        s.monteCarloVolatilityPct, s.guardrailsUpperThresholdPct, s.guardrailsLowerThresholdPct,
        s.guardrailsCutPct, s.guardrailsBoostPct, s.guardrailsFloorPct, s.monteCarloN, s.monteCarloSeed,
        s.vStatePensionMonthly, s.vStatePensionAge, s.eStatePensionMonthly, s.eStatePensionAge,
        s.isSingleHousehold, s.primaryAge, snap.activeFireAge, state.pensionOptimization.partnerAge
    ) {
        val provided = state.decumulationComparison
        if (provided != null) {
            asyncComparison = provided
            return@LaunchedEffect
        }
        delay(80)
        val result = withContext(Dispatchers.Default) {
            val netReturnPct = FinancialEngine.netTaxableNominalReturnPct(s).finiteOrZero()
            val fireAge = snap.activeFireAge ?: s.primaryAge
            val partnerAgeAtFire = state.pensionOptimization.partnerAge + maxOf(0, fireAge - s.primaryAge)
            val streams = buildList {
                val vPen = s.vStatePensionMonthly.coerceAtLeast(0.0) * 12.0
                if (vPen > 0.0) add(maxOf(0, s.vStatePensionAge - fireAge) to vPen)
                if (!s.isSingleHousehold) {
                    val ePen = s.eStatePensionMonthly.coerceAtLeast(0.0) * 12.0
                    if (ePen > 0.0) add(maxOf(0, s.eStatePensionAge - partnerAgeAtFire) to ePen)
                }
            }
            DecumulationStrategies.compareStrategies(
                DecumulationConfig(
                    initialPortfolio = portfolioBase,
                    initialAnnualWithdrawal = portfolioBase * (baseSwr / 100.0),
                    horizonYears = horizonYears,
                    annualInflationPct = s.cpiInflationPct.finiteOrZero(),
                    expectedAnnualReturnPct = netReturnPct,
                    returnVolatilityPct = s.monteCarloVolatilityPct.finiteOrZero().coerceAtLeast(1.0),
                    simulationsCount = s.monteCarloN.coerceIn(50, 2000),
                    randomSeed = s.monteCarloSeed,
                    gkUpperThresholdPct = s.guardrailsUpperThresholdPct.finiteOrZero().coerceIn(5.0, 100.0),
                    gkLowerThresholdPct = s.guardrailsLowerThresholdPct.finiteOrZero().coerceIn(5.0, 100.0),
                    gkCutPct = s.guardrailsCutPct.finiteOrZero().coerceIn(1.0, 50.0),
                    gkBoostPct = s.guardrailsBoostPct.finiteOrZero().coerceIn(1.0, 50.0),
                    gkFloorPctOfInitial = s.guardrailsFloorPct.finiteOrZero().coerceIn(10.0, 100.0),
                    gkInitialWithdrawalMultiplier = DecumulationStrategy.GUYTON_KLINGER.swrMultiplier,
                    vpdRatePct = (baseSwr * 1.075).coerceIn(0.5, 20.0),
                    vpdFloorPctOfInitial = s.guardrailsFloorPct.finiteOrZero().coerceIn(10.0, 100.0),
                    pensionStreams = streams
                )
            )
        }
        asyncComparison = result
    }
    val decumulationRes = state.decumulationComparison ?: asyncComparison

    val dynamicSurvivalRate: Double? = decumulationRes?.let { res ->
        when (strategy) {
            DecumulationStrategy.BENGEN_4PCT -> res.bengenMetrics.survivalRatePct
            DecumulationStrategy.GUYTON_KLINGER -> res.guytonKlingerMetrics.survivalRatePct
            DecumulationStrategy.VARIABLE_PERCENTAGE -> res.variablePercentageMetrics.survivalRatePct
        }
    }
    val dynamicSwrPct = strategy.effectiveSwrPct(baseSwr)
    val bengenMonthly = (portfolioBase * (DecumulationStrategy.BENGEN_4PCT.effectiveSwrPct(baseSwr) / 100.0) / 12.0).finiteOrZero()
    val gkMonthly = (portfolioBase * (DecumulationStrategy.GUYTON_KLINGER.effectiveSwrPct(baseSwr) / 100.0) / 12.0).finiteOrZero()

    val annualSafeSpend = (portfolioBase * (dynamicSwrPct / 100.0)).finiteOrZero()
    val monthlySafeSpend = (annualSafeSpend / 12.0).finiteOrZero()
    val survivalFraction = dynamicSurvivalRate?.let { (it / 100.0).toFloat().finiteOrZero().coerceIn(0f, 1f) } ?: 0f

    val animSurvival by animateFloatAsState(
        targetValue = survivalFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "survival_progress"
    )

    val accentGreen = GoodGreen
    val decumulationInfo = remember(portfolioBase, strategy, s, decumulationRes, accentGreen) {
        val guardrailsDetail = when (strategy) {
            DecumulationStrategy.BENGEN_4PCT -> strategy.bearMarketRule
            DecumulationStrategy.GUYTON_KLINGER ->
                "Capital Preservation (+${s.guardrailsUpperThresholdPct.finiteOrZero().roundToInt()}% trigger -> -${s.guardrailsCutPct.finiteOrZero().roundToInt()}% spend) & Prosperity (-${s.guardrailsLowerThresholdPct.finiteOrZero().roundToInt()}% trigger -> +${s.guardrailsBoostPct.finiteOrZero().roundToInt()}% spend)."
            DecumulationStrategy.VARIABLE_PERCENTAGE ->
                "Dynamic variable withdrawal with ${s.guardrailsFloorPct.finiteOrZero().roundToInt()}% essential spending floor."
        }
        val gkSurvivalText = decumulationRes?.let {
            "${fmtPct(it.guytonKlingerMetrics.survivalRatePct, 1)} simulated survival"
        } ?: "simulated survival calculation pending"
        MetricInfo(
            title = "Decumulation · ${strategy.title}",
            category = "Post-FIRE Capital Drawdown",
            formulaOrRule = "Safe Monthly Spend = (Portfolio * SWR%) / 12 with Adaptive Guardrails ($horizonYears-year simulation)",
            explanation = "${strategy.description} Rules: $guardrailsDetail Prosperity Rule: ${strategy.bullMarketRule} Czech Tax Drag: ${strategy.taxEfficiency}",
            statutoryReference = "§ 4(1)(u) of Act No. 586/1992 Coll. (ITA, 3-Year Time Test)",
            practicalImplication = "Adopting Guyton-Klinger rules provides an extra ${fmtCZK((gkMonthly - bengenMonthly).coerceAtLeast(0.0))}/month vs baseline SWR with $gkSurvivalText.",
            accentColor = accentGreen
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("decumulation_comparison_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                CardHeaderPill(
                    title = "Decumulation Strategy",
                    subtitle = "Safe retirement withdrawal rules · Tap info for details",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = activeAccent,
                    badgeText = null,
                    badgeColor = activeAccent,
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowInfo?.invoke(decumulationInfo)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Decumulation Details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                // 3-way Segmented Selector (min 48dp touch target)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag("decumulation_strategy_toggle"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DecumulationStrategy.entries.forEachIndexed { index, opt ->
                        val isSelected = selectedIndex == index
                        val sColor = opt.accentColor()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) sColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) sColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = isSelected,
                                    role = androidx.compose.ui.semantics.Role.Tab,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedIndex = index
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = opt.shortLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) sColor else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${fmtPct(opt.effectiveSwrPct(baseSwr), 1)} SWR",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontFamily = BodyFontFamily,
                                            color = if (isSelected) sColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Strategy Highlight Metrics Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .testTag("decumulation_spend_value")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Safe Monthly Withdrawal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2,
                                softWrap = true,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fmtCZK(monthlySafeSpend),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = BodyFontFamily,
                                    color = activeAccent,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 2,
                                softWrap = true,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${fmtCompact(annualSafeSpend)} / year",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = BodyFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                maxLines = 1,
                                softWrap = true
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Portfolio Survival Rate",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2,
                                softWrap = true,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dynamicSurvivalRate?.let { fmtPct(it, 1) } ?: "Simulating…",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = BodyFontFamily,
                                    color = GoodGreen,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 1,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$horizonYears-Year Horizon",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                maxLines = 1,
                                softWrap = true
                            )
                        }
                    }
                }

                // Survival Probability Animated Progress Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PORTFOLIO SURVIVAL CONFIDENCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.4.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = if (dynamicSurvivalRate != null) {
                                "${(animSurvival.finiteOrZero() * 100f).roundToInt()}% Simulated Success"
                            } else {
                                "Calculating…"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                fontSize = 10.sp,
                                color = GoodGreen
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animSurvival)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(activeAccent, GoodGreen)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
