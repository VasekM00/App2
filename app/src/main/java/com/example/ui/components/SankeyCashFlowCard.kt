package com.example.ui.components

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextOverflow
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

data class SankeyNode(
    val id: String,
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val amount: Double,
    val color: Color,
    val category: String,
    val description: String,
    val statutoryNote: String = ""
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
    var viewMode by remember { mutableIntStateOf(0) } // 0 = 3-Pillar Cascade (clean default), 1 = Granular Streams
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
        if (viewMode == 0) {
            listOf(
                SankeyNode(
                    id = "in_total",
                    title = "Household Net Inflow",
                    shortLabel = "Inflows",
                    subtitle = "All Salaries & Support",
                    amount = currentInc.totalMonthly,
                    color = cTeal,
                    category = "Combined Take-Home Cash",
                    description = "Total disposable take-home pay received into household accounts after Czech personal income taxes, social, and health deductions.",
                    statutoryNote = "§ 6 ZDP · Zálohová daň 15 % / 23 % + 7,1 % SP + 4,5 % ZP"
                )
            )
        } else {
            buildList {
                if (currentInc.vaclavNet > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_vaclav",
                            title = "${s.primaryName}'s Net Salary",
                            shortLabel = s.primaryName,
                            subtitle = "Take-home pay",
                            amount = currentInc.vaclavNet,
                            color = cTeal,
                            category = "Earned Employment Income",
                            description = "Net monthly salary after statutory 15%/23% personal income tax, 7.1% social, and 4.5% health insurance.",
                            statutoryNote = "§ 6 ZDP · Zálohová daň 15 % / 23 % + 7,1 % SP + 4,5 % ZP"
                        )
                    )
                }
                if (!s.isSingleHousehold && currentInc.eleonoraSalary > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_eleonora",
                            title = "${s.spouseName}'s Net Salary",
                            shortLabel = s.spouseName,
                            subtitle = "Take-home pay",
                            amount = currentInc.eleonoraSalary,
                            color = Color(0xFF0D9488),
                            category = "Earned Employment Income",
                            description = "Net monthly salary upon return to employment, accounting for progressive tax credits.",
                            statutoryNote = "§ 6 ZDP · Sleva na poplatníka 30 840 Kč/rok"
                        )
                    )
                }
                if (currentInc.vouchers > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_vouchers",
                            title = "Meal Vouchers",
                            shortLabel = "Vouchers",
                            subtitle = "Tax-exempt allowance",
                            amount = currentInc.vouchers,
                            color = cGreen,
                            category = "Non-Taxable Allowance",
                            description = "Monetary meal allowance (§ 6(9)(b) ZDP), 100% tax-free and exempt from social and health deductions.",
                            statutoryNote = "§ 6(9)(b) ZDP · Osvobozený stravenkový paušál do zákonného limitu"
                        )
                    )
                }
                val totalGifts = currentInc.gift + currentInc.vaclavGifts + currentInc.eleonoraGifts
                if (totalGifts > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_gifts",
                            title = "Family Support",
                            shortLabel = "Support",
                            subtitle = "Lineage gift exemption",
                            amount = totalGifts,
                            color = Color(0xFF10B981),
                            category = "Tax-Exempt Transfer",
                            description = "Financial assistance from direct-line family members (§ 10(3)(c) ZDP), fully exempt from income tax.",
                            statutoryNote = "§ 10(3)(c) ZDP · Bezúplatné příjmy od příbuzných v linii přímé osvobozeny od daně"
                        )
                    )
                }
                if (currentInc.benefit > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_parental",
                            title = "Parental Allowance",
                            shortLabel = "Parental",
                            subtitle = "State family benefit",
                            amount = currentInc.benefit,
                            color = cBlue,
                            category = "State Social Support",
                            description = "Monthly parental allowance (Rodičovský příspěvek, Act No. 117/1995 Coll.), fully tax-exempt.",
                            statutoryNote = "Zákon č. 117/1995 Sb. · Celkový nárok 350 000 Kč nepodléhá dani ani odvodům"
                        )
                    )
                }
                if (currentInc.lecturing > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_lecturing",
                            title = "Lecturing & Advisory",
                            shortLabel = "Lecturing",
                            subtitle = "Academic side income",
                            amount = currentInc.lecturing,
                            color = Color(0xFF38BDF8),
                            category = "Professional Side Income",
                            description = "Academic lecturing and consulting inflows.",
                            statutoryNote = "§ 7 / § 10 ZDP · Autorské honoráře a vedlejší odborná činnost"
                        )
                    )
                }
                val otherInflows = currentInc.vaclavOther + currentInc.eleonoraOther + currentInc.lumpSumMonthly
                if (otherInflows > 0.0) {
                    add(
                        SankeyNode(
                            id = "in_other",
                            title = "Other Inflows",
                            shortLabel = "Other",
                            subtitle = "Lump sums & misc",
                            amount = otherInflows,
                            color = Color(0xFF818CF8),
                            category = "Supplementary Inflows",
                            description = "Other planned monthly cash inflows and annualized lump-sum deposits.",
                            statutoryNote = "Ostatní příjmy domácnosti rozpočtené do pravidelného měsíčního toku"
                        )
                    )
                }
            }
        }
    }

    val outflowNodes = remember(state, viewMode, s, childCosts, unallocatedSurplus) {
        if (viewMode == 0) {
            buildList {
                add(
                    SankeyNode(
                        id = "out_living",
                        title = "Living Burn Rate",
                        shortLabel = "Living Burn",
                        subtitle = "Rent, groceries & lifestyle",
                        amount = totalExpenses,
                        color = cRed,
                        category = "Baseline Living Expenses",
                        description = "Baseline operational expenditures required to maintain your household standard of living.",
                        statutoryNote = "Základní spotřební koš domácnosti, bydlení a provozní výdaje"
                    )
                )
                if (totalInvest > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_invest",
                            title = "Wealth Compounding",
                            shortLabel = "Wealth DCA",
                            subtitle = "Brokerage, DIP & DPS",
                            amount = totalInvest,
                            color = cTeal,
                            category = "Compounding Capital Formation",
                            description = "Automated monthly investments directly compounding toward your early retirement independence target.",
                            statutoryNote = "§ 4(1)(w) ZDP (3letý časový test) + § 15a ZDP (DIP) + Zákon č. 427/2011 Sb. (DPS)"
                        )
                    )
                }
                if (unallocatedSurplus > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_buffer",
                            title = "Unallocated Cash Buffer",
                            shortLabel = "Cash Buffer",
                            subtitle = "Reserve & discretionary",
                            amount = unallocatedSurplus,
                            color = cGold,
                            category = "Liquid Capital Buffer",
                            description = "Uncommitted monthly surplus retained in high-yield cash buffers to fund emergency reserves and future lump sums.",
                            statutoryNote = "Pohotovostní likvidní rezerva chránící před sekvenčním rizikem (SORR)"
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
                            shortLabel = "Rent",
                            subtitle = "Primary residence",
                            amount = s.rentMonthly,
                            color = cRed,
                            category = "Fixed Essential Living",
                            description = "Monthly apartment lease and housing facility maintenance costs.",
                            statutoryNote = "Pevné provozní výdaje na nájemné a energie"
                        )
                    )
                }
                if (s.groceriesMonthly > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_groceries",
                            title = "Groceries & Food",
                            shortLabel = "Groceries",
                            subtitle = "Supermarket & nutrition",
                            amount = s.groceriesMonthly,
                            color = cOrange,
                            category = "Essential Living Cost",
                            description = "Household food, cooking, and daily household supplies.",
                            statutoryNote = "Základní spotřební koš domácnosti s 12 % sníženou sazbou DPH"
                        )
                    )
                }
                if (childCosts > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_child",
                            title = "Child Lifecycle",
                            shortLabel = "Children",
                            subtitle = "Care & development",
                            amount = childCosts,
                            color = cAmber,
                            category = "Family Living Cost",
                            description = "Age-graded education, medical, clothing, and activity costs for dependent children.",
                            statutoryNote = "Věkově odstupňované náklady na děti (0-3, 3-6, 6-15, 15-26 let)"
                        )
                    )
                }
                val otherLiving = (totalExpenses - s.rentMonthly - s.groceriesMonthly - childCosts).coerceAtLeast(0.0)
                if (otherLiving > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_other_living",
                            title = "Lifestyle & Discretionary",
                            shortLabel = "Lifestyle",
                            subtitle = "Transport, leisure, utilities",
                            amount = otherLiving,
                            color = Color(0xFFF43F5E),
                            category = "Variable Living Expense",
                            description = "Transportation, telecommunications, travel, and discretionary living expenditures.",
                            statutoryNote = "Variabilní výdaje s možností okamžitého snížení při krizovém scénáři"
                        )
                    )
                }

                val portuTotal = s.portuDcaMonthly + (if (!s.isSingleHousehold) s.ePortuDcaMonthly else 0.0)
                if (portuTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_portu",
                            title = "Brokerage ETF (Portu)",
                            shortLabel = "Portu ETF",
                            subtitle = "Global equity index",
                            amount = portuTotal,
                            color = cTeal,
                            category = "Liquid Wealth Compounding",
                            description = "Automated monthly dollar-cost averaging into globally diversified equity index ETFs (qualifying for the Czech 3-year time test exemption § 4(1)(w) ZDP).",
                            statutoryNote = "§ 4(1)(w) ZDP · Osvobozeno od 15% daně ze zisku po 3 letech držby"
                        )
                    )
                }
                val dipTotal = s.dipContributionMonthly + (if (!s.isSingleHousehold) s.eDipContributionMonthly else 0.0)
                if (dipTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_dip",
                            title = "DIP Retirement",
                            shortLabel = "DIP",
                            subtitle = "Tax-sheltered account",
                            amount = dipTotal,
                            color = cGreen,
                            category = "Statutory Tax-Sheltered Account",
                            description = "Dlouhodobý investiční produkt (§ 15a ZDP) enabling index ETF compounding with pre-tax income.",
                            statutoryNote = "§ 15a ZDP · Daňový odpočet až 48 000 Kč/rok s pravidlem 120 měsíců a věkem 60 let"
                        )
                    )
                }
                val dpsTotal = s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
                if (dpsTotal > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_dps",
                            title = "DPS Pension",
                            shortLabel = "DPS",
                            subtitle = "With state subsidies",
                            amount = dpsTotal,
                            color = cPurple,
                            category = "Supplementary State-Subsidized Pension",
                            description = "Doplňkové penzijní spoření with up to 340 Kč/mo direct state matching (Act No. 427/2011 Coll.).",
                            statutoryNote = "Zákon č. 427/2011 Sb. · Státní příspěvek 20 % z vkladu nad 500 Kč (max 340 Kč/měsíc)"
                        )
                    )
                }
                if (unallocatedSurplus > 0.0) {
                    add(
                        SankeyNode(
                            id = "out_buffer",
                            title = "Unallocated Buffer",
                            shortLabel = "Buffer",
                            subtitle = "Liquid reserve building",
                            amount = unallocatedSurplus,
                            color = cGold,
                            category = "Liquid Reserve Capital",
                            description = "Disposable monthly surplus uncommitted to fixed living burn or automated DCA, accumulating in cash reserves.",
                            statutoryNote = "Likvidní pohotovostní rezerva na spořicím účtu chránící před sekvenčním rizikem"
                        )
                    )
                }
            }
        }
    }

    val centralHubNode = remember(currentInc) {
        SankeyNode(
            id = "hub_central",
            title = "Household Clearing Pool",
            shortLabel = "Pool",
            subtitle = "Combined disposable cash",
            amount = currentInc.totalMonthly,
            color = cTeal,
            category = "Central Cash Flow Clearing Hub",
            description = "Central clearing pool representing 100% of disposable household cash generated each month.",
            statutoryNote = "Disponibilní peněžní toky domácnosti před alokací do výdajů a investic"
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardHeaderPill(
                    title = "Cash Flow Waterfall",
                    subtitle = "Interactive inflow-to-asset cascade",
                    badgeText = "SANKEY",
                    accentColor = cTeal
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector: Sleek Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("3-Pillar Cascade", "Granular Streams").forEachIndexed { index, title ->
                    val isSelected = viewMode == index
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewMode = index
                            selectedNodeId = null
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                        shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (index == 0) "sankey_chip_3pillar" else "sankey_chip_granular")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) cTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas drawing area
            val density = LocalDensity.current
            val textPx = with(density) { 9.5.sp.toPx() }
            val boldTextPx = with(density) { 10.sp.toPx() }
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            val labelPaint = remember(textColor, textPx) {
                Paint().apply {
                    color = textColor
                    textSize = textPx
                    isAntiAlias = true
                }
            }
            val boldPaint = remember(textColor, boldTextPx) {
                Paint().apply {
                    color = textColor
                    textSize = boldTextPx
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
            }
            val poolLabelPaint = remember(textMutedColor, textPx) {
                Paint().apply {
                    color = textMutedColor
                    textSize = textPx * 0.95f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.08f
                    isAntiAlias = true
                }
            }

            val sumInflows = remember(inflowNodes) { inflowNodes.sumOf { it.amount }.coerceAtLeast(1.0) }
            val sumOutflows = remember(outflowNodes) { outflowNodes.sumOf { it.amount }.coerceAtLeast(1.0) }

            val canvasHeight = if (viewMode == 0) 210.dp else 270.dp
            var layoutNodesCache by remember { mutableStateOf<List<LayoutNode>>(emptyList()) }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(inflowNodes, outflowNodes, viewMode) {
                            detectTapGestures { offset ->
                                val padX = 20.dp.toPx()
                                val clicked = layoutNodesCache.firstOrNull { node ->
                                    offset.x in (node.bounds.left - padX)..(node.bounds.right + padX) &&
                                        offset.y in node.bounds.top..node.bounds.bottom
                                }
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

                    val is3Pillar = viewMode == 0

                    val paddingTop = 14.dp.toPx()
                    val paddingBottom = 22.dp.toPx()
                    val nodeWidth = 8.5f.dp.toPx()
                    val cornerR = 4.dp.toPx()

                    // Right margin: 3-Pillar mode requires room for up to 18 chars ("Living Burn (55%)").
                    // 118.dp ensures 112.dp for text, preventing right clipping.
                    val leftMargin = if (is3Pillar) 68.dp.toPx() else 74.dp.toPx()
                    val rightMargin = if (is3Pillar) 118.dp.toPx() else 96.dp.toPx()

                    val xInflow = leftMargin
                    val xOutflow = w - rightMargin - nodeWidth

                    // In Granular mode, center POOL node exactly between inflow and outflow bars
                    val ribbonStart = xInflow + nodeWidth
                    val ribbonEnd = xOutflow
                    val xCenter = (ribbonStart + ribbonEnd - nodeWidth) / 2f

                    val availableH = h - paddingTop - paddingBottom
                    val gap = if (is3Pillar) 10.dp.toPx() else 4.dp.toPx()

                    // 1. Layout Inflow Nodes
                    val totalInflowGap = (inflowNodes.size - 1) * gap
                    val usableInflowH = (availableH - totalInflowGap).coerceAtLeast(10f)
                    val inflowLayouts = mutableListOf<LayoutNode>()
                    var currentY = paddingTop

                    val minNodeH = if (is3Pillar) 36.dp.toPx() else 15.dp.toPx()
                    val extraInflowH = (usableInflowH - (inflowNodes.size * minNodeH)).coerceAtLeast(0f)

                    inflowNodes.forEach { node ->
                        val fraction = (node.amount / sumInflows).toFloat()
                        val nodeH = if (is3Pillar) {
                            usableInflowH
                        } else {
                            minNodeH + (fraction * extraInflowH)
                        }
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

                    // 2. Layout Central Hub Node (used in Granular Streams mode)
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

                    val extraOutflowH = (usableOutflowH - (outflowNodes.size * minNodeH)).coerceAtLeast(0f)

                    outflowNodes.forEach { node ->
                        val fraction = (node.amount / sumOutflows).toFloat()
                        val nodeH = minNodeH + (fraction * extraOutflowH)
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

                    layoutNodesCache = if (is3Pillar) {
                        inflowLayouts + outflowLayouts
                    } else {
                        inflowLayouts + hubLayout + outflowLayouts
                    }

                    if (is3Pillar) {
                        // Direct 2-Column Cascade: Flow directly from single Inflow bar to the 3 Outflow Pillars
                        val inLayout = inflowLayouts.firstOrNull()
                        if (inLayout != null) {
                            val inUsableH = inLayout.yBottom - inLayout.yTop
                            var currentSourceY = inLayout.yTop

                            outflowLayouts.forEach { outLayout ->
                                val fraction = (outLayout.node.amount / sumOutflows).toFloat()
                                val sliceH = fraction * inUsableH
                                val sourceTop = currentSourceY
                                val sourceBottom = currentSourceY + sliceH
                                currentSourceY += sliceH

                                val isHighlighted = selectedNodeId == null ||
                                    selectedNodeId == inLayout.node.id ||
                                    selectedNodeId == outLayout.node.id
                                val fillAlpha = if (isHighlighted) 0.44f else 0.08f
                                val strokeAlpha = if (isHighlighted) 0.75f else 0.18f
                                val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                                val x1 = inLayout.bounds.right
                                val x2 = outLayout.bounds.left
                                val cX = (x2 - x1) * 0.52f

                                val ribbonPath = Path().apply {
                                    moveTo(x1, sourceTop)
                                    cubicTo(x1 + cX, sourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                                    lineTo(x2, outLayout.yBottom)
                                    cubicTo(x2 - cX, outLayout.yBottom, x1 + cX, sourceBottom, x1, sourceBottom)
                                    close()
                                }

                                drawPath(
                                    path = ribbonPath,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            inLayout.node.color.copy(alpha = fillAlpha),
                                            outLayout.node.color.copy(alpha = fillAlpha)
                                        ),
                                        startX = x1,
                                        endX = x2
                                    )
                                )

                                // Top & Bottom boundary stroke
                                val topEdge = Path().apply {
                                    moveTo(x1, sourceTop)
                                    cubicTo(x1 + cX, sourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                                }
                                val bottomEdge = Path().apply {
                                    moveTo(x1, sourceBottom)
                                    cubicTo(x1 + cX, sourceBottom, x2 - cX, outLayout.yBottom, x2, outLayout.yBottom)
                                }
                                drawPath(topEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                                drawPath(bottomEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                            }
                        }
                    } else {
                        // 4. Draw Inflow-to-Center Ribbons with Smooth Tangents & Crisp Edge Strokes
                        var hubInY = hubTop
                        val hubUsableH = hubBottom - hubTop

                        inflowLayouts.forEach { inLayout ->
                            val ribbonH = ((inLayout.node.amount / sumInflows) * hubUsableH).toFloat()
                            val hubTargetTop = hubInY
                            val hubTargetBottom = hubInY + ribbonH
                            hubInY += ribbonH

                            val isHighlighted = selectedNodeId == null || selectedNodeId == inLayout.node.id || selectedNodeId == centralHubNode.id
                            val fillAlpha = if (isHighlighted) 0.42f else 0.08f
                            val strokeAlpha = if (isHighlighted) 0.75f else 0.20f
                            val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                            val x1 = inLayout.bounds.right
                            val x2 = xCenter
                            val cX = (x2 - x1) * 0.52f

                            val ribbonPath = Path().apply {
                                moveTo(x1, inLayout.yTop)
                                cubicTo(x1 + cX, inLayout.yTop, x2 - cX, hubTargetTop, x2, hubTargetTop)
                                lineTo(x2, hubTargetBottom)
                                cubicTo(x2 - cX, hubTargetBottom, x1 + cX, inLayout.yBottom, x1, inLayout.yBottom)
                                close()
                            }

                            drawPath(
                                path = ribbonPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(inLayout.node.color.copy(alpha = fillAlpha), cTeal.copy(alpha = fillAlpha)),
                                    startX = x1,
                                    endX = x2
                                )
                            )

                            // Top & Bottom boundary stroke
                            val topEdge = Path().apply {
                                moveTo(x1, inLayout.yTop)
                                cubicTo(x1 + cX, inLayout.yTop, x2 - cX, hubTargetTop, x2, hubTargetTop)
                            }
                            val bottomEdge = Path().apply {
                                moveTo(x1, inLayout.yBottom)
                                cubicTo(x1 + cX, inLayout.yBottom, x2 - cX, hubTargetBottom, x2, hubTargetBottom)
                            }
                            drawPath(topEdge, color = inLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                            drawPath(bottomEdge, color = inLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                        }

                        // 5. Draw Center-to-Outflow Ribbons with Smooth Tangents & Crisp Edge Strokes
                        var hubOutY = hubTop

                        outflowLayouts.forEach { outLayout ->
                            val ribbonH = ((outLayout.node.amount / sumOutflows) * hubUsableH).toFloat()
                            val hubSourceTop = hubOutY
                            val hubSourceBottom = hubOutY + ribbonH
                            hubOutY += ribbonH

                            val isHighlighted = selectedNodeId == null || selectedNodeId == outLayout.node.id || selectedNodeId == centralHubNode.id
                            val fillAlpha = if (isHighlighted) 0.42f else 0.08f
                            val strokeAlpha = if (isHighlighted) 0.75f else 0.20f
                            val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                            val x1 = xCenter + nodeWidth
                            val x2 = outLayout.bounds.left
                            val cX = (x2 - x1) * 0.52f

                            val ribbonPath = Path().apply {
                                moveTo(x1, hubSourceTop)
                                cubicTo(x1 + cX, hubSourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                                lineTo(x2, outLayout.yBottom)
                                cubicTo(x2 - cX, outLayout.yBottom, x1 + cX, hubSourceBottom, x1, hubSourceBottom)
                                close()
                            }

                            drawPath(
                                path = ribbonPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(cTeal.copy(alpha = fillAlpha), outLayout.node.color.copy(alpha = fillAlpha)),
                                    startX = x1,
                                    endX = x2
                                )
                            )

                            val topEdge = Path().apply {
                                moveTo(x1, hubSourceTop)
                                cubicTo(x1 + cX, hubSourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                            }
                            val bottomEdge = Path().apply {
                                moveTo(x1, hubSourceBottom)
                                cubicTo(x1 + cX, hubSourceBottom, x2 - cX, outLayout.yBottom, x2, outLayout.yBottom)
                            }
                            drawPath(topEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                            drawPath(bottomEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                        }
                    }

                    // 6. Draw Inflow Nodes & Margin Labels (Never overlapping ribbons!)
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
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Right-aligned label in left margin
                        labelPaint.textAlign = Paint.Align.RIGHT
                        boldPaint.textAlign = Paint.Align.RIGHT
                        val xLabel = layout.bounds.left - 6.dp.toPx()
                        val yCenter = (layout.yTop + layout.yBottom) / 2f
                        val maxLeftW = (xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                        val labelText = layout.node.shortLabel
                        val originalBoldSize = boldPaint.textSize
                        val measuredBoldW = boldPaint.measureText(labelText)
                        if (measuredBoldW > maxLeftW) {
                            boldPaint.textSize = (originalBoldSize * (maxLeftW / measuredBoldW)).coerceAtLeast(7f)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            xLabel,
                            yCenter - 2.dp.toPx(),
                            boldPaint
                        )
                        boldPaint.textSize = originalBoldSize

                        val amountText = fmtCompact(layout.node.amount)
                        val originalLabelSize = labelPaint.textSize
                        val measuredLabelW = labelPaint.measureText(amountText)
                        if (measuredLabelW > maxLeftW) {
                            labelPaint.textSize = (originalLabelSize * (maxLeftW / measuredLabelW)).coerceAtLeast(7f)
                        }
                        labelPaint.color = layout.node.color.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            amountText,
                            xLabel,
                            yCenter + 9.dp.toPx(),
                            labelPaint
                        )
                        labelPaint.textSize = originalLabelSize
                        labelPaint.color = textColor
                    }

                    if (!is3Pillar) {
                        // Draw Central Hub Node ONLY in Granular mode
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
                                style = Stroke(width = 2.5f)
                            )
                        }
                        poolLabelPaint.textAlign = Paint.Align.CENTER
                        drawContext.canvas.nativeCanvas.drawText(
                            "POOL",
                            xCenter + (nodeWidth / 2f),
                            hubTop - 5.dp.toPx(),
                            poolLabelPaint
                        )
                    }

                    // 7. Draw Outflow Nodes & Margin Labels (Never overlapping ribbons!)
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
                                style = Stroke(width = 2.5f)
                            )
                        }

                        // Left-aligned label in right margin
                        labelPaint.textAlign = Paint.Align.LEFT
                        boldPaint.textAlign = Paint.Align.LEFT
                        val xLabel = layout.bounds.right + 6.dp.toPx()
                        val yCenter = (layout.yTop + layout.yBottom) / 2f
                        val pct = (layout.node.amount / sumInflows * 100.0).coerceIn(0.0, 100.0)

                        val maxRightW = (w - xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                        val titleText = "${layout.node.shortLabel} (${fmtPct(pct, 0)})"
                        val originalBoldSize = boldPaint.textSize
                        val measuredBoldW = boldPaint.measureText(titleText)
                        if (measuredBoldW > maxRightW) {
                            boldPaint.textSize = (originalBoldSize * (maxRightW / measuredBoldW)).coerceAtLeast(7f)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            titleText,
                            xLabel,
                            yCenter - 2.dp.toPx(),
                            boldPaint
                        )
                        boldPaint.textSize = originalBoldSize

                        val amountText = fmtCompact(layout.node.amount)
                        val originalLabelSize = labelPaint.textSize
                        val measuredLabelW = labelPaint.measureText(amountText)
                        if (measuredLabelW > maxRightW) {
                            labelPaint.textSize = (originalLabelSize * (maxRightW / measuredLabelW)).coerceAtLeast(7f)
                        }
                        labelPaint.color = layout.node.color.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            amountText,
                            xLabel,
                            yCenter + 9.dp.toPx(),
                            labelPaint
                        )
                        labelPaint.textSize = originalLabelSize
                        labelPaint.color = textColor
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detail Inspection Panel (When Selected) vs Guidance Banner
            AnimatedVisibility(
                visible = selectedNode != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedNode != null) {
                    val pctOfTotal = if (totalInflows > 0) (selectedNode.amount / totalInflows * 100.0) else 0.0
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = selectedNode.color.copy(alpha = 0.08f),
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
                                            .size(9.dp)
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
                                                color = selectedNode.color,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
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
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedNodeId = null
                                        },
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
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.5.sp
                                )
                            )

                            if (selectedNode.statutoryNote.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "CZ LAW",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = selectedNode.color
                                            )
                                        )
                                        Text(
                                            text = selectedNode.statutoryNote,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedNode == null) {
                // Interactive Guidance Banner (Bulletproof layout, never wraps characters)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = cTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap any stream to inspect statutory rules",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ColorPill(
                            text = "Net ${fmtCompact(totalInflows)}",
                            color = cTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 7.dp,
                            verticalPadding = 2.5.dp
                        )
                    }
                }
            }

            // In Granular Mode: Show 2-Column Stream Matrix for Easy Mobile Touch & Inspection
            if (viewMode == 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Inflow Column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INFLOW SOURCES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    letterSpacing = 0.3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = fmtCompact(sumInflows),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = cTeal
                                )
                            )
                        }
                        inflowNodes.forEach { node ->
                            val isSel = selectedNodeId == node.id
                            StreamMatrixItem(
                                title = node.shortLabel,
                                amount = node.amount,
                                color = node.color,
                                isSelected = isSel,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedNodeId = if (isSel) null else node.id
                                }
                            )
                        }
                    }

                    // Destination Column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ALLOCATIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    letterSpacing = 0.3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = fmtCompact(sumOutflows),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = cRed
                                )
                            )
                        }
                        outflowNodes.forEach { node ->
                            val isSel = selectedNodeId == node.id
                            StreamMatrixItem(
                                title = node.shortLabel,
                                amount = node.amount,
                                color = node.color,
                                isSelected = isSel,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedNodeId = if (isSel) null else node.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamMatrixItem(
    title: String,
    amount: Double,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(5.dp)
    Surface(
        shape = shape,
        color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            1.dp,
            if (isSelected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 8.8.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = fmtCompact(amount),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.2.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1
            )
        }
    }
}
