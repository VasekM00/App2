package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.components.sankey.SankeyCanvasDiagram
import com.example.ui.components.sankey.SankeyDetailPanel
import com.example.ui.components.sankey.SankeyGuidanceBanner
import com.example.ui.components.sankey.SankeyNode
import com.example.ui.components.sankey.SankeyStreamMatrix
import com.example.ui.components.sankey.buildCentralHubNode
import com.example.ui.components.sankey.buildInflowNodes
import com.example.ui.components.sankey.buildOutflowNodes
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.SurfaceDark

// Re-export SankeyNode for backward compatibility with existing callers
typealias SankeyNode = com.example.ui.components.sankey.SankeyNode

@Composable
fun SankeyCashFlowCard(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    onShowInfo: ((com.example.ui.components.MetricInfo) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var viewMode by rememberSaveable { mutableIntStateOf(0) } // 0 = 3-Pillar Cascade, 1 = Granular Streams
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }

    val isDark = MaterialTheme.colorScheme.surface == SurfaceDark
    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cBlue = BrandBlue
    val cGold = BrandGold
    val cRed = BadRed
    val totalInflows = state.snapshot.monthlyTotalInflowsWithMatch.finiteOrZero().coerceAtLeast(1.0)

    val inflowNodes = remember(state, viewMode, cTeal, cGreen, cBlue, isDark) {
        buildInflowNodes(state, viewMode, cTeal, cGreen, cBlue, isDark)
    }
    val outflowNodes = remember(state, viewMode, cTeal, cGreen, cGold, cRed, isDark) {
        buildOutflowNodes(state, viewMode, cTeal, cGreen, cGold, cRed, isDark)
    }
    val centralHubNode = remember(state, cTeal, viewMode) {
        buildCentralHubNode(state, cTeal, viewMode)
    }

    val sumInflows = remember(inflowNodes) {
        inflowNodes.sumOf { it.amount.finiteOrZero().coerceAtLeast(0.0) }.coerceAtLeast(1.0)
    }
    val sumOutflows = remember(outflowNodes) {
        outflowNodes.sumOf { it.amount.finiteOrZero().coerceAtLeast(0.0) }.coerceAtLeast(1.0)
    }
    val deficitAmount = (sumOutflows - sumInflows).coerceAtLeast(0.0)

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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    title = "Where Your Money Goes",
                    subtitle = "Tap any stream to see breakdown",
                    accentColor = cTeal
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector: Sleek Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Overview", "By Category").forEachIndexed { index, title ->
                    val isSelected = viewMode == index
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewMode = index
                            selectedNodeId = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (index == 0) "sankey_chip_3pillar" else "sankey_chip_granular")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                maxLines = 2,
                                softWrap = true,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) cTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas drawing area (scales height smoothly for dense granular streams & font scaling)
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val effHScale = fontScale.coerceIn(1f, 1.45f)
            val maxColumnCount = maxOf(inflowNodes.size, outflowNodes.size, 1)
            val canvasHeight = if (viewMode == 0) {
                (210f * effHScale).dp
            } else {
                maxOf((280f * effHScale).dp, (maxColumnCount * 46f * effHScale).dp)
            }
            SankeyCanvasDiagram(
                inflowNodes = inflowNodes,
                outflowNodes = outflowNodes,
                centralHubNode = centralHubNode,
                sumInflows = sumInflows,
                sumOutflows = sumOutflows,
                viewMode = viewMode,
                selectedNodeId = selectedNodeId,
                canvasHeight = canvasHeight,
                onSelectNode = { selectedNodeId = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Detail Inspection Panel (When Selected)
            SankeyDetailPanel(
                selectedNode = selectedNode,
                totalInflows = sumInflows,
                onDismiss = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedNodeId = null
                }
            )

            // Guidance Banner (When Nothing Selected)
            if (selectedNode == null) {
                SankeyGuidanceBanner(
                    totalInflows = sumInflows,
                    deficitAmount = deficitAmount
                )
            }

            // In Granular Mode: Show 2-Column Stream Matrix for Easy Mobile Touch & Inspection
            if (viewMode == 1) {
                Spacer(modifier = Modifier.height(4.dp))
                SankeyStreamMatrix(
                    inflowNodes = inflowNodes,
                    outflowNodes = outflowNodes,
                    sumInflows = sumInflows,
                    sumOutflows = sumOutflows,
                    selectedNodeId = selectedNodeId,
                    onNodeClick = { id ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedNodeId = if (selectedNodeId == id) null else id
                    }
                )
            }
        }
    }
}
