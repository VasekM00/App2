package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandTeal

/**
 * Standard Color Pill for tags, badges, statuses, and metric categories.
 * Features animated scale on achievement, smooth content size transitions,
 * and high-contrast Material 3 tinted containers.
 */
@Composable
fun ColorPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BrandTeal,
    icon: ImageVector? = null,
    iconEmoji: String? = null,
    fontSize: TextUnit = 10.5.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 3.5.dp,
    cornerRadius: Dp = 8.dp,
    alpha: Float = 0.12f,
    borderAlpha: Float = 0.28f,
    maxLines: Int = 2,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    isAchieved: Boolean = false,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    val scaleAnim by animateFloatAsState(
        targetValue = if (isAchieved) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pill_achievement_scale"
    )

    Surface(
        modifier = modifier
            .scale(scaleAnim)
            .animateContentSize()
            .then(clickModifier),
        shape = RoundedCornerShape(cornerRadius),
        color = color.copy(alpha = alpha),
        border = BorderStroke(1.dp, color.copy(alpha = borderAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (iconEmoji != null) {
                Text(
                    text = iconEmoji,
                    fontSize = (fontSize.value * 0.95f).sp
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = color,
                    letterSpacing = 0.3.sp
                ),
                maxLines = maxLines,
                softWrap = softWrap,
                overflow = overflow
            )
        }
    }
}

/**
 * Modern Card Header with leading icon in pill container, title, subtitle,
 * and an optional top-right pill tag.
 */
@Composable
fun CardHeaderPill(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconEmoji: String? = null,
    accentColor: Color = BrandTeal,
    badgeText: String? = null,
    badgeColor: Color = accentColor,
    isBadgeAchieved: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            if (icon != null || iconEmoji != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconEmoji != null) {
                        Text(text = iconEmoji, fontSize = 15.sp)
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.animateContentSize()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (badgeText != null || trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (badgeText != null) {
                    ColorPill(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp,
                        isAchieved = isBadgeAchieved
                    )
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    }
}

/**
 * Single-pass wrapping row for chips and quick presets that works reliably inside dialogs
 * without horizontal clipping or FlowRow intrinsic-measurement loops.
 */
@Composable
fun ChipWrapRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val rows = mutableListOf<List<Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<Placeable>()
        var currentW = 0
        var currentH = 0

        for (measurable in measurables) {
            val placeable = measurable.measure(childConstraints)
            val neededW = if (currentRow.isEmpty()) placeable.width else currentW + hGap + placeable.width
            if (currentRow.isNotEmpty() && neededW > maxWidth) {
                rows.add(currentRow)
                rowWidths.add(currentW)
                rowHeights.add(currentH)
                currentRow = mutableListOf(placeable)
                currentW = placeable.width
                currentH = placeable.height
            } else {
                currentRow.add(placeable)
                currentW = neededW
                currentH = maxOf(currentH, placeable.height)
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentW)
            rowHeights.add(currentH)
        }

        val totalWidth = maxOf(constraints.minWidth, rowWidths.maxOrNull() ?: 0)
            .let { if (constraints.hasBoundedWidth) it.coerceAtMost(constraints.maxWidth) else it }
        val totalHeight = (rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * vGap)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIdx, rowPlaceables ->
                var x = 0
                val rH = rowHeights[rowIdx]
                for (placeable in rowPlaceables) {
                    val yOffset = (rH - placeable.height) / 2
                    placeable.placeRelative(x, y + yOffset)
                    x += placeable.width + hGap
                }
                y += rH + vGap
            }
        }
    }
}
