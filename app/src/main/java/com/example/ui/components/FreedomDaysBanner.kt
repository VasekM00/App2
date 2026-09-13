package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FinancialEngine
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCZK
import java.util.Locale
import kotlin.math.abs

@Composable
fun FreedomDaysBanner(
    monthlySavings: Double,
    monthlyLivingCost: Double,
    portfolioBalance: Double,
    modifier: Modifier = Modifier
) {
    val isDeficit = monthlySavings < 0.0
    val accentColor = if (isDeficit) BadRed else BrandTeal
    val daysBought = FinancialEngine.freedomDaysBoughtMonthly(monthlySavings, monthlyLivingCost)
    val daysCovered = FinancialEngine.freedomDaysCoveredByPortfolio(portfolioBalance, monthlyLivingCost)
    val yearsCovered = daysCovered / 365.25
    val dailyBurn = if (monthlyLivingCost > 0.0) monthlyLivingCost / 30.42 else 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("freedom_days_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Freedom Days Metric",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isDeficit) "Runway consumed by monthly deficit" else "Time purchased with monthly capital surplus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ColorPill(
                    text = "${String.format(Locale.ROOT, "%.1f", daysBought)} DAYS/MO",
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDeficit) "Monthly Deficit Burn:" else "Monthly Savings Bought:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isDeficit) {
                                "${String.format(Locale.ROOT, "%.1f", daysBought)} days of freedom"
                            } else {
                                "+${String.format(Locale.ROOT, "%.1f", daysBought)} days of freedom"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isDeficit) {
                            "This month's ${fmtCZK(abs(monthlySavings))} deficit consumed ${String.format(Locale.ROOT, "%.1f", abs(daysBought))} days of freedom from your portfolio at your current burn rate (${fmtCZK(dailyBurn)}/day)."
                        } else {
                            "This month's ${fmtCZK(monthlySavings)} savings permanently purchased ${String.format(Locale.ROOT, "%.1f", daysBought)} days of freedom at your current burn rate (${fmtCZK(dailyBurn)}/day)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Liquid Portfolio Runway:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.ROOT, "%.1f", yearsCovered)} years (${daysCovered.toInt()} days)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = BrandGold
                        )
                    }
                }
            }
        }
    }
}
