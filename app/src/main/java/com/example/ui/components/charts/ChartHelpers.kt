package com.example.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ChartMetricPill(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = color
                ),
                maxLines = 2,
                softWrap = true
            )
        }
    }
}

/** Returns `this` if finite, or `0.0` when `NaN` or infinite. */
fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0

/** Returns `this` if finite, or `0f` when `NaN` or infinite. */
fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

/** Safely computes a clamped `[0f, 1f]` fraction, returning `0f` for non-finite or non-positive inputs. */
fun safeFraction(numerator: Double, denominator: Double): Float {
    if (!numerator.isFinite() || !denominator.isFinite() || denominator <= 0.0 || numerator <= 0.0) {
        return 0f
    }
    return (numerator / denominator).toFloat().coerceIn(0f, 1f)
}

/**
 * Computes a human-friendly ceiling for chart axes (e.g. 17.3M -> 20.0M with 4 steps of 5M).
 */
fun computeNiceAxisMax(rawMax: Double, steps: Int = 4): Double {
    if (!rawMax.isFinite() || rawMax <= 0.0 || steps <= 0) return 1.0
    val rawStep = rawMax / steps
    if (!rawStep.isFinite() || rawStep <= 0.0) return 1.0
    val exponent = floor(log10(rawStep))
    val base = 10.0.pow(exponent)
    if (!base.isFinite() || base <= 0.0) return rawMax
    val fraction = rawStep / base
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    val niceStep = niceFraction * base
    if (!niceStep.isFinite() || niceStep <= 0.0) return rawMax
    var ceilMax = niceStep * steps
    var guard = 0
    while (ceilMax < rawMax && guard < 16) {
        ceilMax += niceStep
        guard++
    }
    return if (ceilMax.isFinite() && ceilMax > 0.0) ceilMax else rawMax
}

private fun Offset.isFiniteOffset(): Boolean = x.isFinite() && y.isFinite()

/**
 * Populates [target] (after resetting it) with a smooth monotone cubic Bezier path
 * passing through the finite [points].
 */
fun fillMonotoneCubicPath(target: Path, points: List<Offset>) {
    target.reset()
    val valid = points.filter { it.isFiniteOffset() }
    if (valid.isEmpty()) return
    target.moveTo(valid[0].x, valid[0].y)
    if (valid.size == 1) return
    if (valid.size == 2) {
        target.lineTo(valid[1].x, valid[1].y)
        return
    }
    for (i in 0 until valid.size - 1) {
        val p0 = if (i > 0) valid[i - 1] else valid[i]
        val p1 = valid[i]
        val p2 = valid[i + 1]
        val p3 = if (i + 2 < valid.size) valid[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        target.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
}

/**
 * Constructs a smooth monotone cubic Bezier path passing through the given points.
 */
fun buildMonotoneCubicPath(points: List<Offset>): Path {
    return Path().also { fillMonotoneCubicPath(it, points) }
}

/**
 * Populates [target] (after resetting it) with a smooth monotone cubic area path
 * closed against [baselineY].
 */
fun fillMonotoneCubicAreaPath(target: Path, points: List<Offset>, baselineY: Float) {
    target.reset()
    val valid = points.filter { it.isFiniteOffset() }
    if (valid.isEmpty() || !baselineY.isFinite()) return
    target.moveTo(valid[0].x, baselineY)
    target.lineTo(valid[0].x, valid[0].y)
    if (valid.size == 1) {
        target.lineTo(valid[0].x, baselineY)
        target.close()
        return
    }
    for (i in 0 until valid.size - 1) {
        val p0 = if (i > 0) valid[i - 1] else valid[i]
        val p1 = valid[i]
        val p2 = valid[i + 1]
        val p3 = if (i + 2 < valid.size) valid[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        target.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    target.lineTo(valid.last().x, baselineY)
    target.close()
}

/**
 * Constructs a smooth monotone cubic area path closed against [baselineY].
 */
fun buildMonotoneCubicAreaPath(points: List<Offset>, baselineY: Float): Path {
    return Path().also { fillMonotoneCubicAreaPath(it, points, baselineY) }
}

/**
 * Populates [target] (after resetting it) with a smooth closed band path between
 * [topPoints] and [bottomPoints].
 */
fun fillMonotoneBandPath(target: Path, topPoints: List<Offset>, bottomPoints: List<Offset>) {
    target.reset()
    val validTop = topPoints.filter { it.isFiniteOffset() }
    val validBottom = bottomPoints.filter { it.isFiniteOffset() }
    if (validTop.isEmpty() || validBottom.isEmpty()) return
    target.moveTo(validTop[0].x, validTop[0].y)
    for (i in 0 until validTop.size - 1) {
        val p0 = if (i > 0) validTop[i - 1] else validTop[i]
        val p1 = validTop[i]
        val p2 = validTop[i + 1]
        val p3 = if (i + 2 < validTop.size) validTop[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        target.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }

    target.lineTo(validBottom.last().x, validBottom.last().y)
    for (i in validBottom.size - 1 downTo 1) {
        val p0 = if (i < validBottom.size - 1) validBottom[i + 1] else validBottom[i]
        val p1 = validBottom[i]
        val p2 = validBottom[i - 1]
        val p3 = if (i > 1) validBottom[i - 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        target.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    target.close()
}

/**
 * Constructs a smooth closed band path between [topPoints] and [bottomPoints].
 */
fun buildMonotoneBandPath(topPoints: List<Offset>, bottomPoints: List<Offset>): Path {
    return Path().also { fillMonotoneBandPath(it, topPoints, bottomPoints) }
}

/**
 * Animates a 0f..1f path reveal progress on initial appearance or when [key] changes.
 */
@Composable
fun rememberChartPathReveal(
    key: Any? = null,
    durationMillis: Int = 850,
    delayMillis: Int = 0
): State<Float> {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }
    return progress.asState()
}

/**
 * Smoothly animates layer visibility from 0f to 1f (or reverse) with spring/easing.
 */
@Composable
fun rememberLayerVisibilityAnimation(
    isVisible: Boolean,
    durationMillis: Int = 320
): State<Float> {
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "layerVisibility"
    )
}

/**
 * Trims a Compose [Path] to a fractional [progress] in 0f..1f across all contours.
 */
fun trimMultiContourPath(sourcePath: Path, progress: Float): Path {
    if (!progress.isFinite()) return Path()
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped >= 1f) return sourcePath
    if (clamped <= 0f) return Path()

    val androidPath = sourcePath.asAndroidPath()
    val measure = android.graphics.PathMeasure(androidPath, false)
    var totalLength = 0f
    do {
        val len = measure.length
        if (len.isFinite() && len > 0f) totalLength += len
    } while (measure.nextContour())

    if (!totalLength.isFinite() || totalLength <= 0f) return sourcePath

    val targetLength = totalLength * clamped
    val destAndroidPath = android.graphics.Path()
    measure.setPath(androidPath, false)
    var accumulated = 0f

    do {
        val contourLen = measure.length
        if (!contourLen.isFinite() || contourLen <= 0f) continue
        if (accumulated + contourLen <= targetLength) {
            measure.getSegment(0f, contourLen, destAndroidPath, true)
            accumulated += contourLen
        } else {
            val remain = targetLength - accumulated
            if (remain > 0f) {
                measure.getSegment(0f, remain, destAndroidPath, true)
            }
            break
        }
    } while (measure.nextContour())

    return destAndroidPath.asComposePath()
}

/** Convenience alias for [trimMultiContourPath]. */
fun trimPath(sourcePath: Path, progress: Float): Path = trimMultiContourPath(sourcePath, progress)

/**
 * Computes how many fractional years have elapsed since `1/[baseYear]` for the given
 * [currentYearMonth] (defaulting to Europe/Prague wall clock), clamped to `[0, maxSpanYears]`.
 */
fun computeNowYearFraction(
    baseYear: Int,
    maxSpanYears: Int,
    currentYearMonth: java.time.YearMonth? = null,
): Float {
    val ym = currentYearMonth ?: java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague"))
    val decimalYear = ym.year + (ym.monthValue - 1) / 12f
    return (decimalYear - baseYear.toFloat()).coerceIn(0f, maxSpanYears.coerceAtLeast(1).toFloat())
}

/**
 * Draws the vertical dashed `"Now"` indicator, concentric marker dot on the curve,
 * and `[Now]` badge, returning early if [curX] falls outside the visible plot bounds.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNowMarker(
    curX: Float,
    curY: Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    accentColor: Color,
    pillTextArgb: Int? = null,
    dotHaloColor: Color? = null,
    label: String = "Now",
    showDot: Boolean = true,
) {
    if (curX !in plotLeft..plotRight) return
    val resolvedPillTextArgb = pillTextArgb ?: if (accentColor.luminance() > 0.35f) {
        android.graphics.Color.rgb(15, 23, 42)
    } else {
        android.graphics.Color.WHITE
    }
    val resolvedHalo = dotHaloColor ?: if (accentColor.luminance() > 0.35f) {
        Color(0xFF0F172A)
    } else {
        Color.White
    }
    drawLine(
        color = accentColor.copy(alpha = 0.55f),
        start = androidx.compose.ui.geometry.Offset(curX, plotTop),
        end = androidx.compose.ui.geometry.Offset(curX, plotBottom),
        strokeWidth = 1.25.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(8f, 6f),
            0f,
        ),
    )
    if (showDot) {
        drawCircle(
            color = resolvedHalo,
            radius = 6.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(curX, curY),
        )
        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(curX, curY),
        )
    }

    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 9.5.sp.toPx()
        color = resolvedPillTextArgb
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val fm = textPaint.fontMetrics
    val textW = textPaint.measureText(label)
    val pillW = textW + 10.dp.toPx()
    val pillH = maxOf(16.dp.toPx(), (fm.descent - fm.ascent) + 5.dp.toPx())
    val clampedCenterX = curX.coerceIn(
        (plotLeft + pillW / 2f),
        (plotRight - pillW / 2f).coerceAtLeast(plotLeft + pillW / 2f),
    )
    val badgeTop = if (!showDot) {
        plotTop + 4.dp.toPx()
    } else if (curY - pillH - 10.dp.toPx() >= plotTop) {
        curY - pillH - 8.dp.toPx()
    } else {
        (curY + 8.dp.toPx()).coerceAtMost(plotBottom - pillH)
    }
    drawRoundRect(
        color = accentColor,
        topLeft = androidx.compose.ui.geometry.Offset(clampedCenterX - pillW / 2f, badgeTop),
        size = androidx.compose.ui.geometry.Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f),
    )
    val baselineY = badgeTop + pillH / 2f - (fm.ascent + fm.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText(
        label,
        clampedCenterX,
        baselineY,
        textPaint,
    )
}

/**
 * Interpolates the current wall-clock position along [items] and renders [drawNowMarker]
 * cleanly inside `[plotLeft..plotRight]`.
 */
fun <T> androidx.compose.ui.graphics.drawscope.DrawScope.drawNowMarkerOnSeries(
    items: List<T>,
    yearOf: (T) -> Int,
    valueOf: (T) -> Double,
    xForFraction: (Float) -> Float,
    yPix: (Double) -> Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    accentColor: Color,
    currentYearMonth: java.time.YearMonth? = null,
    showDot: Boolean = true,
) {
    if (items.isEmpty()) return
    val nowFraction = computeNowYearFraction(
        baseYear = yearOf(items[0]),
        maxSpanYears = (items.size - 1).coerceAtLeast(0),
        currentYearMonth = currentYearMonth,
    )
    val loIdx = kotlin.math.floor(nowFraction).toInt().coerceIn(0, items.lastIndex)
    val hiIdx = (loIdx + 1).coerceIn(0, items.lastIndex)
    val segFrac = (nowFraction - loIdx).coerceIn(0f, 1f).toDouble()
    val vLo = valueOf(items[loIdx])
    val vHi = valueOf(items[hiIdx])
    val curValue = vLo + (vHi - vLo) * segFrac
    val curX = xForFraction(nowFraction)
    val curY = yPix(curValue)
    drawNowMarker(
        curX = curX,
        curY = curY,
        plotLeft = plotLeft,
        plotRight = plotRight,
        plotTop = plotTop,
        plotBottom = plotBottom,
        accentColor = accentColor,
        showDot = showDot,
    )
}
