package com.example.ui.components.sankey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SankeyDetailPanel(
    selectedNode: SankeyNode?,
    totalInflows: Double,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = selectedNode != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        if (selectedNode != null) {
            val safeAmount = selectedNode.amount.finiteOrZero().coerceAtLeast(0.0)
            val safeTotalInflows = totalInflows.finiteOrZero()
            val pctOfTotal = if (safeTotalInflows > 0.0) (safeAmount / safeTotalInflows * 100.0) else 0.0
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = selectedNode.color.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, selectedNode.color.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sankey_selected_detail")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Row 1: Stream Color, Full Title (2 lines wrap, no truncation) & Dismiss Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(selectedNode.color)
                            )
                            Text(
                                text = selectedNode.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                softWrap = true,
                                maxLines = 2
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sankey_detail_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Category Badge, Focused Stream Pill & Monthly Amount
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedNode.category,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = selectedNode.color,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            softWrap = true,
                            maxLines = 2
                        )
                        ColorPill(
                            text = "FOCUSED STREAM",
                            color = selectedNode.color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp,
                            cornerRadius = 6.dp
                        )
                        Text(
                            text = "${fmtCZK(safeAmount)}/month (${fmtPct(pctOfTotal, 1)})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                color = selectedNode.color,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = selectedNode.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp
                        ),
                        softWrap = true
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
                                    text = "RULE",
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
                                    maxLines = 4,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SankeyGuidanceBanner(
    totalInflows: Double,
    deficitAmount: Double = 0.0
) {
    val safeInflows = totalInflows.finiteOrZero().coerceAtLeast(0.0)
    val safeDeficit = deficitAmount.finiteOrZero().coerceAtLeast(0.0)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Tap any stream to inspect breakdown",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (safeDeficit > 1.0) {
                    ColorPill(
                        text = "-${fmtCompact(safeDeficit)}",
                        color = BadRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        horizontalPadding = 7.dp,
                        verticalPadding = 2.5.dp,
                        cornerRadius = 6.dp
                    )
                }
                ColorPill(
                    text = "Net ${fmtCompact(safeInflows)}",
                    color = BrandTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodyFontFamily,
                    horizontalPadding = 7.dp,
                    verticalPadding = 2.5.dp,
                    cornerRadius = 6.dp
                )
            }
        }
    }
}

@Composable
private fun StreamColumnHeader(
    title: String,
    totalAmount: Double,
    amountColor: Color,
    stackVertical: Boolean
) {
    val titleText: @Composable (Modifier) -> Unit = { mod ->
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = mod
        )
    }
    val totalText = fmtCompact(totalAmount.finiteOrZero().coerceAtLeast(0.0))
    val amountText: @Composable () -> Unit = {
        Text(
            text = totalText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = BodyFontFamily,
                fontSize = 10.5.sp,
                color = amountColor
            ),
            maxLines = 1,
            softWrap = true
        )
    }
    if (stackVertical) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            titleText(Modifier.fillMaxWidth())
            amountText()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            titleText(
                Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            amountText()
        }
    }
}

@Composable
fun SankeyStreamMatrix(
    inflowNodes: List<SankeyNode>,
    outflowNodes: List<SankeyNode>,
    sumInflows: Double,
    sumOutflows: Double,
    selectedNodeId: String?,
    onNodeClick: (String) -> Unit
) {
    val stackHeaders = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Inflow Column
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StreamColumnHeader(
                title = "INFLOW SOURCES",
                totalAmount = sumInflows,
                amountColor = BrandTeal,
                stackVertical = stackHeaders
            )
            inflowNodes.forEach { node ->
                val isSel = selectedNodeId == node.id
                StreamMatrixItem(
                    title = node.shortLabel,
                    amount = node.amount,
                    color = node.color,
                    isSelected = isSel,
                    onClick = { onNodeClick(node.id) }
                )
            }
        }

        // Destination Column
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StreamColumnHeader(
                title = "ALLOCATIONS",
                totalAmount = sumOutflows,
                amountColor = BadRed,
                stackVertical = stackHeaders
            )
            outflowNodes.forEach { node ->
                val isSel = selectedNodeId == node.id
                StreamMatrixItem(
                    title = node.shortLabel,
                    amount = node.amount,
                    color = node.color,
                    isSelected = isSel,
                    onClick = { onNodeClick(node.id) }
                )
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
    val shape = RoundedCornerShape(8.dp)
    val isLargeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    Surface(
        shape = shape,
        color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            1.dp,
            if (isSelected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        if (isLargeFont) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = fmtCompact(amount.finiteOrZero().coerceAtLeast(0.0)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    softWrap = true
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = fmtCompact(amount.finiteOrZero().coerceAtLeast(0.0)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    softWrap = true
                )
            }
        }
    }
}
