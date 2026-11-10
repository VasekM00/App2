package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtCZK
import kotlin.math.min

private data class DcaStreamItem(
    val label: String,
    val amount: Double,
    val color: Color
)

@Composable
fun DcaAllocationBreakdownBar(
    settings: SettingsEntity,
    modifier: Modifier = Modifier
) {
    val isSingle = settings.isSingleHousehold
    val portuV = settings.portuDcaMonthly
    val portuE = if (!isSingle) settings.ePortuDcaMonthly else 0.0
    val dipV = settings.dipContributionMonthly
    val dipE = if (!isSingle) settings.eDipContributionMonthly else 0.0
    val dpsV = settings.dpsOwnContributionMonthly
    val dpsE = if (!isSingle) settings.eDpsOwnContributionMonthly else 0.0
    val empV = min(settings.employerRetirementMonthly, settings.employerRetirementExemptionAnnual / 12.0)
    val empE = if (!isSingle) min(settings.eEmployerRetirementMonthly, settings.employerRetirementExemptionAnnual / 12.0) else 0.0

    val cPortuV = BrandTeal
    val cPortuE = BrandGold
    val cDipV = GoodGreen
    val cDipE = Color(0xFF059669)
    val cDpsV = BrandBlue
    val cDpsE = Color(0xFF38BDF8)
    val cEmpV = Color(0xFF8B5CF6)
    val cEmpE = Color(0xFFA78BFA)

    val streams = remember(settings, isSingle, portuV, portuE, dipV, dipE, dpsV, dpsE, empV, empE, cPortuV, cPortuE, cDipV, cDipE, cDpsV, cDpsE, cEmpV, cEmpE) {
        buildList {
            if (portuV > 0.0) {
                add(DcaStreamItem(label = "Brokerage · ${settings.primaryName}", amount = portuV, color = cPortuV))
            }
            if (portuE > 0.0) {
                add(DcaStreamItem(label = "Brokerage · ${settings.spouseName}", amount = portuE, color = cPortuE))
            }
            if (dipV > 0.0) {
                add(DcaStreamItem(label = "DIP · ${settings.primaryName}", amount = dipV, color = cDipV))
            }
            if (dipE > 0.0) {
                add(DcaStreamItem(label = "DIP · ${settings.spouseName}", amount = dipE, color = cDipE))
            }
            if (dpsV > 0.0) {
                add(DcaStreamItem(label = "DPS · ${settings.primaryName}", amount = dpsV, color = cDpsV))
            }
            if (dpsE > 0.0) {
                add(DcaStreamItem(label = "DPS · ${settings.spouseName}", amount = dpsE, color = cDpsE))
            }
            if (empV > 0.0) {
                add(DcaStreamItem(label = "Employer · ${settings.primaryName}", amount = empV, color = cEmpV))
            }
            if (empE > 0.0) {
                add(DcaStreamItem(label = "Employer · ${settings.spouseName}", amount = empE, color = cEmpE))
            }
        }
    }

    val totalDca = streams.sumOf { it.amount }
    if (totalDca <= 0.0 || streams.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorPill(
                    text = "DCA ALLOCATIONS",
                    color = BrandTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp,
                    cornerRadius = 6.dp
                )
            }
            ColorPill(
                text = "${fmtCompact(totalDca)} / mo",
                color = BrandTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                horizontalPadding = 8.dp,
                verticalPadding = 2.5.dp,
                cornerRadius = 8.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            streams.forEachIndexed { index, stream ->
                val ratio = (stream.amount / totalDca).toFloat()
                if (ratio > 0f) {
                    val startRadius = if (index == 0) 6.dp else 0.dp
                    val endRadius = if (index == streams.size - 1) 6.dp else 0.dp
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxHeight()
                            .background(
                                stream.color,
                                RoundedCornerShape(
                                    topStart = startRadius,
                                    bottomStart = startRadius,
                                    topEnd = endRadius,
                                    bottomEnd = endRadius
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Single-line Pillbox Stream Rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            streams.forEach { stream ->
                val pct = ((stream.amount / totalDca) * 100).toInt()
                DcaPillBox(
                    label = stream.label,
                    amount = stream.amount,
                    pct = pct,
                    color = stream.color
                )
            }
        }
    }
}

@Composable
private fun DcaPillBox(
    label: String,
    amount: Double,
    pct: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "${fmtCZK(amount)} · $pct%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = color
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
