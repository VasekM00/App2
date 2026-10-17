package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import kotlin.math.max

private data class SankeyNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val color: Color,
    val category: String,
    val description: String
)

private data class LayoutNode(
    val node: SankeyNode,
    val yTop: Float,
    val yBottom: Float,
    val bounds: RoundRect
)

@Composable
fun SankeyCashFlowCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val s = state.settings
    val haptic = LocalHapticFeedback.current
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Granular Streams, 1 = 3-Pillar Allocation
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cGold = BrandGold
    val cRed = BadRed
    val cPurple = Color(0xFF6366F1)
    val cBlue = BrandBlue
    val cOrange = Color(0xFFF97316)
    val cAmber = Color(0xFFF59E0B)

    val currentInc = state.currentIncome
    val totalInflows = currentInc.totalMonthly.coerceAtLeast(1.0)
    val totalExpenses = state.totalLivingCostMonthly
    val totalInvest = state.investMonthlyTotal
    val unallocatedSurplus = (totalInflows - totalExpenses - totalInvest).coerceAtLeast(0.0)

    val childCosts = if (s.childExpensesEnabled && !s.currentChildCostsInBaseline) {
        val c1 = if (s.child1Enabled) FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s) else 0.0
        val c2 = if (s.child2Enabled) FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s) else 0.0
        c1 + c2
    } else 0.0

    val inflowNodes = remember(state, viewMode, currentInc) {
        if (viewMode == 1) {
            listOf(
                SankeyNode(
                    id = "in_total",
                    title = "Household Net Inflows",
                    subtitle = "All Salaries & Support",
                    amount = currentInc.totalMonthly,
                    color = cTeal,
                    category = "Combined Cash Generation",
                    description = "Total disposable take-home pay received into household accounts after Czech taxes, social and health deductions."
                )
            )
        } else {
            buildList {
                if (currentInc.vaclavNet > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_vaclav",
                            title = "${s.primaryName}'s Net Salary",
                            subtitle = "Take-home pay",
                            amount = currentInc.vaclavNet,
                            color = cTeal,
                            category = "Earned Employment Income",
                            description = "Net monthly salary after statutory 15%/23% personal income tax, 7.1% social, and 4.5% health insurance."
                        )
                    )
                }
                if (!s.isSingleHousehold && currentInc.eleonoraSalary > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_eleonora",
                            title = "${s.spouseName}'s Net Salary",
                            subtitle = "Take-home pay",
                            amount = currentInc.eleonoraSalary,
                            color = Color(0xFF0D9488),
                            category = "Earned Employment Income",
                            description = "Net monthly salary upon return to employment, accounting for progressive tax credits."
                        )
                    )
                }
                if (currentInc.vouchers > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_vouchers",
                            title = "Meal Vouchers",
                            subtitle = "Tax-exempt allowance",
                            amount = currentInc.vouchers,
                            color = cGreen,
                            category = "Non-Taxable Allowance",
                            description = "Monetary meal allowance (§ 6(9)(b) ZDP), 100% tax-free and exempt from social/health deductions."
                        )
                    )
                }
                val totalGifts = currentInc.gift + currentInc.vaclavGifts + currentInc.eleonoraGifts
                if (totalGifts > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_gifts",
                            title = "Family Support",
                            subtitle = "Lineage gift exemption",
                            amount = totalGifts,
                            color = Color(0xFF10B981),
                            category = "Tax-Exempt Transfer",
                            description = "Financial assistance from direct-line family members (§ 10(3)(c) ZDP), fully exempt from income tax."
                        )
                    )
                }
                if (currentInc.benefit > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_parental",
                            title = "Parental Allowance",
                            subtitle = "State family benefit",
                            amount = currentInc.benefit,
                            color = cBlue,
                            category = "State Social Support",
                            description = "Monthly parental allowance (Rodičovský příspěvek, Act No. 117/1995 Coll.), fully tax-exempt."
                        )
                    )
                }
                if (currentInc.lecturing > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_lecturing",
                            title = "Lecturing & Advisory",
                            subtitle = "Academic side income",
                            amount = currentInc.lecturing,
                            color = Color(0xFF38BDF8),
                            category = "Professional Side Income",
                            description = "Academic lecturing and consulting inflows."
                        )
                    )
                }
                val otherInflows = currentInc.vaclavOther + currentInc.eleonoraOther + currentInc.lumpSumMonthly
                if (otherInflows > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_other",
                            title = "Other Inflows",
                            subtitle = "Lump sums & misc",
                            amount = otherInflows,
                            color = Color(0xFF818CF8),
                            category = "Supplementary Inflows",
                            description = "Other planned monthly cash inflows and annualized lump-sum deposits."
                        )
                    )
                }
            }
        }
    }

    val outflowNodes = remember(state, viewMode, s, childCosts, unallocatedSurplus) {
        if (viewMode == 1) {
            buildList {
                add(
                    SankeyNode(
                        id = "out_living",
                        title = "Living Burn Rate",
                        subtitle = "Rent, groceries & lifestyle",
                        amount = totalExpenses,
                        color = cRed,
                        category = "Baseline Living Expenses",
                        description = "Baseline operational expenditures required to maintain your household standard of living."
                    )
                )
                if (totalInvest > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_invest",
                            title = "Automated Wealth DCA",
                            subtitle = "Brokerage, DIP & DPS",
                            amount = totalInvest,
                            color = cTeal,
                            category = "Compounding Capital Formation",
                            description = "Automated monthly investments directly compounding toward your early retirement independence target."
                        )
                    )
                }
                if (unallocatedSurplus > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_buffer",
                            title = "Unallocated Cash Buffer",
                            subtitle = "Reserve & discretionary",
                            amount = unallocatedSurplus,
                            color = cGold,
                            category = "Liquid Capital Buffer",
                            description = "Uncommitted monthly surplus retained in high-yield cash buffers to fund emergency reserves and future lump sums."
                        )
                    )
                }
            }
        } else {
            buildList {
                if (s.rentMonthly > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_rent",
                            title = "Housing & Rent",
                            subtitle = "Primary residence",
                            amount = s.rentMonthly,
                            color = cRed,
                            category = "Fixed Essential Living",
                            description = "Monthly apartment lease and housing facility maintenance costs."
                        )
                    )
                }
                if (s.groceriesMonthly > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_groceries",
                            title = "Groceries & Food",
                            subtitle = "Supermarket & nutrition",
                            amount = s.groceriesMonthly,
                            color = cOrange,
                            category = "Essential Living Cost",
                            description = "Household food, cooking, and daily household supplies."
                        )
                    )
                }
                if (childCosts > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_child",
                            title = "Child Lifecycle",
                            subtitle = "Care & development",
                            amount = childCosts,
                            color = cAmber,
                            category = "Family Living Cost",
                            description = "Age-graded education, medical, clothing, and activity costs for dependent children."
                        )
                    )
                }
                val otherLiving = (totalExpenses - s.rentMonthly - s.groceriesMonthly - childCosts).coerceAtLeast(0.0)
                if (otherLiving > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_other_living",
                            title = "Lifestyle & Discretionary",
                            subtitle = "Transport, leisure, utilities",
                            amount = otherLiving,
                            color = Color(0xFFF43F5E),
                            category = "Variable Living Expense",
                            description = "Transportation, telecommunications, travel, and discretionary living expenditures."
                        )
                    )
                }

                val portuTotal = s.portuDcaMonthly + (if (!s.isSingleHousehold) s.ePortuDcaMonthly else 0.0)
                if (portuTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_portu",
                            title = "Brokerage ETF (Portu)",
                            subtitle = "Global equity index",
                            amount = portuTotal,
                            color = cTeal,
                            category = "Liquid Wealth Compounding",
                            description = "Automated monthly dollar-cost averaging into globally diversified equity index ETFs (qualifying for the Czech 3-year time test exemption § 4(1)(w) ZDP)."
                        )
                    )
                }
                val dipTotal = s.dipContributionMonthly + (if (!s.isSingleHousehold) s.eDipContributionMonthly else 0.0)
                if (dipTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_dip",
                            title = "DIP Retirement",
                            subtitle = "Tax-sheltered account",
                            amount = dipTotal,
                            color = cGreen,
                            category = "Statutory Tax-Sheltered Account",
                            description = "Dlouhodobý investiční produkt (§ 15a ZDP) enabling index ETF compounding with pre-tax income."
                        )
                    )
                }
                val dpsTotal = s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
                if (dpsTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_dps",
                            title = "DPS Pension",
                            subtitle = "With state subsidies",
                            amount = dpsTotal,
                            color = cPurple,
                            category = "Supplementary State-Subsidized Pension",
                            description = "Doplňkové penzijní spoření with up to 340 Kč/mo direct state matching (Act No. 427/2011 Coll.)."
                        )
                    )
                }
                if (unallocatedSurplus > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_buffer",
                            title = "Unallocated Buffer",
                            subtitle = "Liquid reserve building",
                            amount = unallocatedSurplus,
                            color = cGold,
                            category = "Liquid Reserve Capital",
                            description = "Disposable monthly surplus uncommitted to fixed living burn or automated DCA, accumulating in cash reserves."
                        )
                    )
                }
            }
        }
    }

    val centralHubNode = remember(currentInc) {
        SankeyNode(
            id = "hub_central",
            title = "Household Pool",
            subtitle = "Combined disposable cash",
            amount = currentInc.totalMonthly,
            color = cTeal,
            category = "Central Cash Flow Hub",
            description = "Central clearing pool representing 100% of disposable household cash generated each month."
        )
    }

    val selectedNode = remember(selectedNodeId, inflowNodes, outflowNodes, centralHubNode) {
        when (selectedNodeId) {
            null -> null
            centralHubNode.id -> centralHubNode
            else -> inflowNodes.firstOrNull { it.id == selectedNodeId } ?: outflowNodes.firstOrNull { it.id == selectedNodeId }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sankey_cashflow_card")
            .semantics {
                contentDescription = "Interactive Sankey cash flow diagram visualizing the flow of household income to living expenses, DCA investments, and cash reserves."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardHeaderPill(
                    title = "Cash Flow Waterfall & Sankey",
                    subtitle = "Visual journey from income to wealth assets",
                    badgeText = "SANKEY",
                    accentColor = cTeal
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewMode = 0
                        selectedNodeId = null
                    },
                    label = {
                        Text(
                            "Granular Streams",
                            fontSize = 11.sp,
                            fontWeight = if (viewMode == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (viewMode == 0) cTeal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = if (viewMode == 0) cTeal else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("sankey_chip_granular")
                )
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewMode = 1
                        selectedNodeId = null
                    },
                    label = {
                        Text(
                            "3-Pillar Allocation",
                            fontSize = 11.sp,
                            fontWeight = if (viewMode == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (viewMode == 1) GoodGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = if (viewMode == 1) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("sankey_chip_3pillar")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas drawing area
            val density = LocalDensity.current
            val textPx = with(density) { 9.5.sp.toPx() }
            val boldTextPx = with(density) { 10.sp.toPx() }
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            val textPaint = remember(textColor, textPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = textPx
                    isAntiAlias = true
                }
            }
            val boldPaint = remember(textColor, boldTextPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = boldTextPx
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            }

            val canvasHeight = if (viewMode == 0) 310.dp else 230.dp

            var layoutNodesCache by remember { mutableStateOf<List<LayoutNode>>(emptyList()) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                        .pointerInput(inflowNodes, outflowNodes, viewMode) {
                            detectTapGestures { offset ->
                                val clicked = layoutNodesCache.firstOrNull { it.bounds.contains(offset) }
                                if (clicked != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedNodeId = if (selectedNodeId == clicked.node.id) null else clicked.node.id
                                } else {
                                    selectedNodeId = null
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val paddingTop = 12f
                    val paddingBottom = 12f
                    val nodeWidth = 14.dp.toPx()
                    val cornerR = 5.dp.toPx()

                    val xInflow = 16f
                    val xCenter = (w - nodeWidth) / 2f
                    val xOutflow = w - 16f - nodeWidth

                    val availableH = h - paddingTop - paddingBottom
                    val gap = 6.dp.toPx()

                    // 1. Layout Inflow Nodes
                    val totalInflowGap = (inflowNodes.size - 1) * gap
                    val usableInflowH = (availableH - totalInflowGap).coerceAtLeast(10f)
                    val inflowLayouts = mutableListOf<LayoutNode>()
                    var currentY = paddingTop

                    inflowNodes.forEach { node ->
                        val nodeH = ((node.amount / totalInflows) * usableInflowH).toFloat().coerceAtLeast(14f)
                        val bounds = RoundRect(
                            left = xInflow,
                            top = currentY,
                            right = xInflow + nodeWidth,
                            bottom = currentY + nodeH,
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                        inflowLayouts.add(LayoutNode(node, currentY, currentY + nodeH, bounds))
                        currentY += nodeH + gap
                    }

                    // 2. Layout Central Hub Node
                    val hubTop = paddingTop
                    val hubBottom = h - paddingBottom
                    val hubBounds = RoundRect(
                        left = xCenter,
                        top = hubTop,
                        right = xCenter + nodeWidth,
                        bottom = hubBottom,
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                    val hubLayout = LayoutNode(centralHubNode, hubTop, hubBottom, hubBounds)

                    // 3. Layout Outflow Nodes
                    val totalDestinations = (totalExpenses + totalInvest + unallocatedSurplus).coerceAtLeast(1.0)
                    val totalOutflowGap = (outflowNodes.size - 1) * gap
                    val usableOutflowH = (availableH - totalOutflowGap).coerceAtLeast(10f)
                    val outflowLayouts = mutableListOf<LayoutNode>()
                    var currentOutY = paddingTop

                    outflowNodes.forEach { node ->
                        val nodeH = ((node.amount / totalDestinations) * usableOutflowH).toFloat().coerceAtLeast(14f)
                        val bounds = RoundRect(
                            left = xOutflow,
                            top = currentOutY,
                            right = xOutflow + nodeWidth,
                            bottom = currentOutY + nodeH,
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                        outflowLayouts.add(LayoutNode(node, currentOutY, currentOutY + nodeH, bounds))
                        currentOutY += nodeH + gap
                    }

                    layoutNodesCache = inflowLayouts + hubLayout + outflowLayouts

                    // 4. Draw Inflow-to-Center Ribbons
                    var hubInY = hubTop
                    val hubUsableH = hubBottom - hubTop

                    inflowLayouts.forEach { inLayout ->
                        val ribbonH = ((inLayout.node.amount / totalInflows) * hubUsableH).toFloat()
                        val hubTargetTop = hubInY
                        val hubTargetBottom = hubInY + ribbonH
                        hubInY += ribbonH

                        val isHighlighted = selectedNodeId == null || selectedNodeId == inLayout.node.id || selectedNodeId == centralHubNode.id
                        val alpha = if (isHighlighted) 0.50f else 0.10f

                        val ribbonPath = Path().apply {
                            moveTo(inLayout.bounds.right, inLayout.yTop)
                            val dx = (xCenter - inLayout.bounds.right) * 0.5f
                            cubicTo(
                                inLayout.bounds.right + dx, inLayout.yTop,
                                xCenter - dx, hubTargetTop,
                                xCenter, hubTargetTop
                            )
                            lineTo(xCenter, hubTargetBottom)
                            cubicTo(
                                xCenter - dx, hubTargetBottom,
                                inLayout.bounds.right + dx, inLayout.yBottom,
                                inLayout.bounds.right, inLayout.yBottom
                            )
                            close()
                        }

                        drawPath(
                            path = ribbonPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    inLayout.node.color.copy(alpha = alpha),
                                    cTeal.copy(alpha = alpha)
                                ),
                                startX = inLayout.bounds.right,
                                endX = xCenter
                            )
                        )
                    }

                    // 5. Draw Center-to-Outflow Ribbons
                    var hubOutY = hubTop

                    outflowLayouts.forEach { outLayout ->
                        val ribbonH = ((outLayout.node.amount / totalDestinations) * hubUsableH).toFloat()
                        val hubSourceTop = hubOutY
                        val hubSourceBottom = hubOutY + ribbonH
                        hubOutY += ribbonH

                        val isHighlighted = selectedNodeId == null || selectedNodeId == outLayout.node.id || selectedNodeId == centralHubNode.id
                        val alpha = if (isHighlighted) 0.50f else 0.10f

                        val ribbonPath = Path().apply {
                            moveTo(xCenter + nodeWidth, hubSourceTop)
                            val dx = (outLayout.bounds.left - (xCenter + nodeWidth)) * 0.5f
                            cubicTo(
                                xCenter + nodeWidth + dx, hubSourceTop,
                                outLayout.bounds.left - dx, outLayout.yTop,
                                outLayout.bounds.left, outLayout.yTop
                            )
                            lineTo(outLayout.bounds.left, outLayout.yBottom)
                            cubicTo(
                                outLayout.bounds.left - dx, outLayout.yBottom,
                                xCenter + nodeWidth + dx, hubSourceBottom,
                                xCenter + nodeWidth, hubSourceBottom
                            )
                            close()
                        }

                        drawPath(
                            path = ribbonPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    cTeal.copy(alpha = alpha),
                                    outLayout.node.color.copy(alpha = alpha)
                                ),
                                startX = xCenter + nodeWidth,
                                endX = outLayout.bounds.left
                            )
                        )
                    }

                    // 6. Draw Node Bars & Text Labels
                    inflowLayouts.forEach { layout ->
                        val isSel = selectedNodeId == layout.node.id
                        drawRoundRect(
                            color = layout.node.color,
                            topLeft = Offset(layout.bounds.left, layout.bounds.top),
                            size = Size(layout.bounds.width, layout.bounds.height),
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                        if (isSel) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(layout.bounds.left - 2f, layout.bounds.top - 2f),
                                size = Size(layout.bounds.width + 4f, layout.bounds.height + 4f),
                                cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                            )
                        }

                        // Label
                        val labelX = layout.bounds.right + 6f
                        val labelY = ((layout.yTop + layout.yBottom) / 2f) + 4f
                        val titleText = layout.node.title.take(12)
                        val amtText = fmtCompact(layout.node.amount)
                        drawContext.canvas.nativeCanvas.drawText(
                            "$titleText ($amtText)",
                            labelX,
                            labelY,
                            textPaint
                        )
                    }

                    // Draw Central Hub Bar
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(cTeal, cGreen)),
                        topLeft = Offset(hubBounds.left, hubBounds.top),
                        size = Size(hubBounds.width, hubBounds.height),
                        cornerRadius = CornerRadius(cornerR, cornerR)
                    )
                    if (selectedNodeId == centralHubNode.id) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(hubBounds.left - 2f, hubBounds.top - 2f),
                            size = Size(hubBounds.width + 4f, hubBounds.height + 4f),
                            cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                        )
                    }

                    outflowLayouts.forEach { layout ->
                        val isSel = selectedNodeId == layout.node.id
                        drawRoundRect(
                            color = layout.node.color,
                            topLeft = Offset(layout.bounds.left, layout.bounds.top),
                            size = Size(layout.bounds.width, layout.bounds.height),
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                        if (isSel) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(layout.bounds.left - 2f, layout.bounds.top - 2f),
                                size = Size(layout.bounds.width + 4f, layout.bounds.height + 4f),
                                cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                            )
                        }

                        // Label to left of outflow node
                        val titleText = layout.node.title.take(12)
                        val amtText = fmtCompact(layout.node.amount)
                        val fullStr = "$titleText ($amtText)"
                        val textW = textPaint.measureText(fullStr)
                        val labelX = layout.bounds.left - 6f - textW
                        val labelY = ((layout.yTop + layout.yBottom) / 2f) + 4f
                        drawContext.canvas.nativeCanvas.drawText(
                            fullStr,
                            labelX,
                            labelY,
                            textPaint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detail Panel for Selected Flow
            if (selectedNode != null) {
                val pctOfTotal = if (totalInflows > 0) (selectedNode.amount / totalInflows * 100.0) else 0.0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = selectedNode.color.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, selectedNode.color.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("sankey_selected_detail")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(selectedNode.color)
                                )
                                Column {
                                    Text(
                                        text = selectedNode.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = selectedNode.category,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${fmtCZK(selectedNode.amount)} / mo (${fmtPct(pctOfTotal, 1)})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = selectedNode.color
                                    )
                                )
                                IconButton(
                                    onClick = { selectedNodeId = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear selection",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = selectedNode.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            } else {
                // Interactive guidance banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap any stream or node to inspect details & statutory rules",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Text(
                            text = "Net Inflow: ${fmtCompact(totalInflows)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = cTeal
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 KPI Footers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val burnPct = (totalExpenses / totalInflows * 100.0).coerceIn(0.0, 100.0)
                val dcaPct = (totalInvest / totalInflows * 100.0).coerceIn(0.0, 100.0)
                val bufferPct = (unallocatedSurplus / totalInflows * 100.0).coerceIn(0.0, 100.0)

                SankeyStatChip(
                    label = "Living Burn",
                    value = fmtCompact(totalExpenses),
                    pct = "${fmtPct(burnPct, 0)}",
                    color = cRed,
                    modifier = Modifier.weight(1f)
                )
                SankeyStatChip(
                    label = "Invested DCA",
                    value = fmtCompact(totalInvest),
                    pct = "${fmtPct(dcaPct, 0)}",
                    color = cTeal,
                    modifier = Modifier.weight(1f)
                )
                SankeyStatChip(
                    label = "Buffer Surplus",
                    value = fmtCompact(unallocatedSurplus),
                    pct = "${fmtPct(bufferPct, 0)}",
                    color = cGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SankeyStatChip(
    label: String,
    value: String,
    pct: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = pct,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = color.copy(alpha = 0.85f),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
