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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandSky
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import com.example.ui.theme.brandSolidContentColor
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.InvestmentLotManager
import com.example.util.UserInvestmentLot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CapitalGainsTimeTestCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    customLots: List<UserInvestmentLot>? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Reactive lot storage (offload SharedPreferences IO from UI thread when customLots == null)
    var internalLots by remember(customLots) {
        mutableStateOf(customLots ?: emptyList())
    }
    LaunchedEffect(customLots, context, state.settings) {
        if (customLots == null) {
            internalLots = withContext(Dispatchers.IO) {
                InvestmentLotManager.getLots(context)
            }
        }
    }

    var editingLot by remember { mutableStateOf<UserInvestmentLot?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val domainLots = remember(internalLots) { internalLots.map { it.toDomainLot() } }
    val portfolioSummary = remember(domainLots) {
        com.example.domain.CzechCapitalGainsEngine.analyzePortfolio(domainLots)
    }
    val optimalHarvestRecommendation = remember(domainLots, state.effectiveSettings.taxRatePct) {
        if (domainLots.isEmpty()) null
        else {
            val totalValue = domainLots.sumOf { it.currentPricePerUnitCzk.finiteOrZero() * it.units.finiteOrZero() }
            val harvestTarget = if (totalValue > 0.0) minOf(totalValue, 100_000.0) else 0.0
            if (harvestTarget > 0.0) {
                com.example.domain.CzechCapitalGainsEngine.recommendOptimalHarvest(
                    lots = domainLots,
                    targetCashCzk = harvestTarget,
                    taxRatePct = state.effectiveSettings.taxRatePct
                )
            } else null
        }
    }

    val totalExemptValue = internalLots.filter { it.isExempt() }.sumOf { it.currentValueCzk.finiteOrZero() }
    val totalPendingValue = internalLots.filter { !it.isExempt() }.sumOf { it.currentValueCzk.finiteOrZero() }
    val taxSavedExempt = internalLots.filter { it.isExempt() }.sumOf { it.taxSavedCzk(state.settings.taxRatePct).finiteOrZero() }

    val accentGreen = GoodGreen
    val timeTestInfo = remember(accentGreen, optimalHarvestRecommendation) {
        val dynamicHarvestNote = optimalHarvestRecommendation?.let {
            " Next ${fmtCZK(it.targetCashCzk)} liquidation can be raised with ${fmtCZK(it.taxLiabilityCzk)} tax liability across ${it.saleOrders.size} prioritized lots."
        } ?: ""
        MetricInfo(
            title = "3-Year Capital Gains Time Test",
            category = "Income Tax Exemption",
            formulaOrRule = "Holding Period > 3 Years = 0% Czech Personal Income Tax",
            explanation = "Under the Czech Income Tax Act, capital gains realized from the sale of securities (shares, ETFs, mutual funds) are 100% tax-exempt once held for strictly more than 3 continuous years. An additional 100,000 Kč annual gross exemption applies to smaller security sales. Track your ETF/equity purchase lots to monitor the 3-year exemption window.$dynamicHarvestNote",
            statutoryReference = "§ 4(1)(u) & § 4(1)(w) of Act No. 586/1992 Coll. (ITA)",
            practicalImplication = "FIFO Tax Shield Strategy: Sell mature lots first (FIFO) during decumulation or rebalancing to eliminate 15% or 23% capital gains tax friction and preserve 100% of compound returns.",
            accentColor = accentGreen
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("capital_gains_card"),
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
                            GoodGreen.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Info and Add Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoodGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = GoodGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "3-Year Time Test",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoodGreen.copy(alpha = 0.15f),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = "0% TAX SHIELD",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GoodGreen,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Long-term ETF 0% tax shield",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editingLot = null
                                showDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = GoodGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("btn_add_lot")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add lot",
                                    tint = GoodGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GoodGreen,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowInfo?.invoke(timeTestInfo)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Time test info",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }



                // 3 Stat Boxes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeTestStatBox(
                        title = "Tax-Exempt Now",
                        value = fmtCompact(totalExemptValue),
                        subtitle = "Held > 3 Years",
                        accentColor = GoodGreen,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    TimeTestStatBox(
                        title = "Maturing Lots",
                        value = fmtCompact(totalPendingValue),
                        subtitle = "Under 36 Months",
                        accentColor = BrandSky,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    TimeTestStatBox(
                        title = "Tax Shielded",
                        value = fmtCompact(taxSavedExempt),
                        subtitle = "${com.example.util.Formatters.fmtPct(state.settings.taxRatePct.finiteOrZero().coerceIn(0.0, 100.0), 0)} Tax Saved",
                        accentColor = BrandTeal,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Annual Exemption (ZDP § 4(1)(w))",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "${fmtCZK(portfolioSummary.annualExemptionLimitRemainingCzk)} headroom",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoodGreen,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                if (optimalHarvestRecommendation != null && optimalHarvestRecommendation.saleOrders.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GoodGreen.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tax_optimal_harvest_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tax-Optimal Sale Order (0% Drag)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GoodGreen,
                                        fontSize = 10.5.sp
                                    )
                                )
                                Text(
                                    text = if (optimalHarvestRecommendation.taxLiabilityCzk <= 0.0) "0 Kč Tax Liability"
                                           else "Est. Tax: ${fmtCZK(optimalHarvestRecommendation.taxLiabilityCzk)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (optimalHarvestRecommendation.taxLiabilityCzk <= 0.0) GoodGreen else WarnAmber,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Text(
                                text = "Next ${fmtCompact(optimalHarvestRecommendation.totalCashRaisedCzk)} liquidation prioritizes ${optimalHarvestRecommendation.saleOrders.size} lot${if (optimalHarvestRecommendation.saleOrders.size > 1) "s" else ""} (saving ${fmtCompact(optimalHarvestRecommendation.taxSavedCzk)} tax via time-test).",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }



                if (internalLots.isEmpty()) {
                    // Compact Empty State
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No purchase lots yet",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Track 36-month tax shield",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    editingLot = null
                                    showDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoodGreen,
                                    contentColor = brandSolidContentColor(GoodGreen)
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Lot", maxLines = 1)
                            }
                        }
                    }
                }

 else {
                    // Lots List Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INVESTMENT LOT MATURATION SCHEDULE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "Tap any lot to edit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }

                    // Lots rows
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        internalLots.forEach { lot ->
                            UserInvestmentLotRow(
                                lot = lot,
                                onClick = {
                                    editingLot = lot
                                    showDialog = true
                                }
                            )
                        }
                    }
                }


            }
        }
    }

    if (showDialog) {
        val latestLot = remember(internalLots) { internalLots.maxByOrNull { it.purchaseDateStr } }
        EditInvestmentLotDialog(
            lot = editingLot,
            templateLot = if (editingLot == null) latestLot else null,
            onDismiss = { showDialog = false },
            onSave = { savedLot ->
                coroutineScope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        InvestmentLotManager.saveLot(context, savedLot)
                        InvestmentLotManager.getLots(context)
                    }
                    internalLots = updated
                    showDialog = false
                }
            },
            onDelete = { deleteId ->
                coroutineScope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        InvestmentLotManager.deleteLot(context, deleteId)
                        InvestmentLotManager.getLots(context)
                    }
                    internalLots = updated
                    showDialog = false
                }
            }
        )
    }
}

@Composable
private fun UserInvestmentLotRow(
    lot: UserInvestmentLot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animProgress by animateFloatAsState(
        targetValue = lot.progress(),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "lot_progress"
    )

    val isExempt = lot.isExempt()
    val monthsHeld = lot.monthsHeld()
    val monthsRemaining = lot.monthsRemaining()
    val gain = lot.unrealizedGainCzk

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("capital_gains_lot_row")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = lot.batchName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${lot.instrument} · Purchased ${lot.purchaseDateStr}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtCZK(lot.currentValueCzk),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.5.sp
                        )
                    )
                    Text(
                        text = "${if (gain >= 0) "+" else ""}${fmtCZK(gain)} (${String.format(java.util.Locale.US, "%.1f", lot.unrealizedGainPct)}%)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = BodyFontFamily,
                            fontSize = 10.5.sp,
                            color = if (gain >= 0) GoodGreen else MaterialTheme.colorScheme.error
                        )
                    )
                }
            }

            // Progress bar to 36 months
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (isExempt) GoodGreen else BrandSky)
                    )
                }

                Text(
                    text = if (isExempt) "Exempt (0% Tax)" else "$monthsRemaining months left",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        fontSize = 10.sp,
                        color = if (isExempt) GoodGreen else WarnAmber
                    )
                )
            }
        }
    }
}

@Composable
private fun TimeTestStatBox(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodyFontFamily,
                    fontSize = 13.sp,
                    color = accentColor
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
