package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FullCalculationState
import com.example.ui.theme.BadRed
import com.example.ui.components.ColorPill
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.util.Formatters.fmtCompact

@Composable
fun EmergencyReserveWidget(
    state: FullCalculationState,
    modifier: Modifier = Modifier,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var selectedTargetMode by remember {
        mutableStateOf(prefs.getString("emergency_reserve_mode", "6M") ?: "6M")
    }

    val monthlyExpense = state.totalLivingCostMonthly.coerceAtLeast(1.0)
    val currentLiquidCash = state.settings.emergencyReserveCurrent

    val targetAmount = when (selectedTargetMode) {
        "3M" -> monthlyExpense * 3
        "6M" -> monthlyExpense * 6
        "9M" -> monthlyExpense * 9
        "12M" -> monthlyExpense * 12
        "Target" -> if (state.settings.emergencyReserveTarget > 0.0) state.settings.emergencyReserveTarget else monthlyExpense * 6
        else -> monthlyExpense * 6
    }

    val progress = (currentLiquidCash / targetAmount.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
    val monthsCovered = currentLiquidCash / monthlyExpense
    val targetMonthsEquivalent = targetAmount / monthlyExpense

    val cGreen = GoodGreen
    val cAmber = WarnAmber
    val cRed = BadRed

    val pctCovered = ((currentLiquidCash / targetAmount.coerceAtLeast(1.0)) * 100).toInt()

    val (statusText, statusColor, statusIcon) = when {
        currentLiquidCash >= targetAmount -> Triple("Fully Funded ($pctCovered%)", cGreen, Icons.Default.CheckCircle)
        currentLiquidCash >= (targetAmount * 0.5) -> Triple("Building ($pctCovered%)", cAmber, Icons.Default.Info)
        else -> Triple("Low Reserve ($pctCovered%)", cRed, Icons.Default.Warning)
    }

    val reserveInfo = MetricInfo(
        title = "Emergency Runway & Buffer",
        category = "Liquidity & Risk Buffer",
        formulaOrRule = "Runway = Liquid Reserve (${fmtCompact(currentLiquidCash)}) / Monthly Burn (${fmtCompact(monthlyExpense)})",
        explanation = "Measures how many months your household can sustain full baseline expenditures with zero incoming salary. Storing this cash in high-yield savings (e.g. ČNB repo-rate linked accounts) insulates your long-term equity DCA portfolio from forced liquidation during market downturns.",
        practicalImplication = "A 6-month buffer provides peace of mind while eliminating sequence-of-returns destruction during sudden market corrections.",
        accentColor = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_reserve_widget"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onShowInfo != null) Modifier.infoTapHold(reserveInfo, onShowInfo)
                        else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Emergency Fund & Runway",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = "${String.format("%.1f", monthsCovered)} Mo. Expenses Covered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }

                // Status Pill Badge (Guaranteed full visibility)
                ColorPill(
                    text = statusText,
                    color = statusColor,
                    icon = statusIcon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    info = reserveInfo,
                    onShowInfo = onShowInfo
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar & Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current: ${fmtCompact(currentLiquidCash)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "Target ($selectedTargetMode): ${fmtCompact(targetAmount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Smooth Continuous Progress Bar (Zero trailing dots)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Target Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Runway:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("3M", "6M", "9M", "12M", "Target").forEach { mode ->
                        FilterChip(
                            selected = selectedTargetMode == mode,
                            onClick = {
                                selectedTargetMode = mode
                                prefs.edit().putString("emergency_reserve_mode", mode).apply()
                            },
                            label = {
                                Text(
                                    text = mode,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("reserve_chip_${mode.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}
