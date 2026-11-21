package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.MetricInfo
import com.example.ui.components.charts.DcaTrajectoryBarChart
import com.example.ui.components.charts.NetWorthChart
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct

@Composable
internal fun TrajectorySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onOpenSandbox: (() -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    var isRealPurchasingPower by rememberSaveable { mutableStateOf(false) }
    var dcaExpanded by rememberSaveable { mutableStateOf(false) }

    val sEff = state.effectiveSettings
    val snap = state.snapshot
    val isSingleHh = sEff.isSingleHousehold
    val activeTrajectory = if (isSingleHh) state.singleTrajectory else state.dualTrajectory
    val activeFirePoint = snap.activeFirePoint
    val combinedPension = sEff.vStatePensionMonthly + if (isSingleHh) 0.0 else sEff.eStatePensionMonthly
    val pensionAgeDisplay = if (isSingleHh || sEff.vStatePensionAge == sEff.eStatePensionAge) {
        "${sEff.vStatePensionAge}"
    } else {
        "${sEff.vStatePensionAge}/${sEff.eStatePensionAge}"
    }

    val assumptionsInfo = MetricInfo(
        title = "Forecast Assumptions",
        category = "Actuarial Baseline",
        formulaOrRule = "SWR ${fmtPct(sEff.safeWithdrawalRatePct)} · Nominal Return ${fmtPct(sEff.portfolioNominalReturnPct)} (Net ${fmtPct(FinancialEngine.netTaxableNominalReturnPct(sEff), 2)}) · CPI ${fmtPct(sEff.cpiInflationPct)}",
        explanation = "Real CAGR is ${fmtPct(state.realReturnPct, 2)} after dividend tax drag and CPI inflation. State pension kicks in at age $pensionAgeDisplay (${fmtCZK(combinedPension)}/mo). Adjust any parameter in More > Portfolio & Tax or test locally in What-If.",
        statutoryReference = "Act No. 586/1992 Coll. & Act No. 155/1995 Coll.",
        practicalImplication = "Using constant-purchasing-power (Real) view keeps future FIRE targets intuitive in today's Kč.",
        accentColor = BrandTeal
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary 35-Year Net Worth Chart (direct without L3 tab bar)
        NetWorthChart(
            data = activeTrajectory,
            cpiInflationPct = sEff.cpiInflationPct,
            ledgerEntries = ledgerEntries,
            isRealPurchasingPower = isRealPurchasingPower,
            onRealPurchasingPowerChange = { isRealPurchasingPower = it },
            onOpenSandbox = onOpenSandbox
        )

        // Compact 1-line Forecast Assumptions Summary Chip
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .infoTapHold(assumptionsInfo, onShowInfo)
                .testTag("forecast_assumptions_summary_chip")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "SWR ${fmtPct(sEff.safeWithdrawalRatePct)} · Return ${fmtPct(sEff.portfolioNominalReturnPct)} · CPI ${fmtPct(sEff.cpiInflationPct)} · Pension $pensionAgeDisplay",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "FIRE year: ${snap.activeFireYear ?: "Beyond 35 years"} · Tap for formula",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View forecast assumptions details",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Collapsible Accordion: Contributions & Growth (replaces L3 tab bar)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { dcaExpanded = !dcaExpanded }
                        .testTag("accordion_contributions_growth"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Contributions & Growth",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Annual DCA, match & compounding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (dcaExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (dcaExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (dcaExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DcaTrajectoryBarChart(
                        data = activeTrajectory,
                        settings = sEff
                    )
                }
            }
        }

        // 35-Year Trajectory Milestones Table with Interactive Inspector & CSV Export
        TrajectoryMilestonesTableCard(
            trajectory = activeTrajectory,
            isRealPurchasingPower = isRealPurchasingPower,
            cpiInflationPct = sEff.cpiInflationPct,
            swrPct = sEff.safeWithdrawalRatePct,
            firePoint = activeFirePoint
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
internal fun ProjectionMetricRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    highlightColor: Color? = null,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 4.dp)
    ) {
        val maxValueWidth = maxWidth * 0.54f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(
                    text = label,
                    style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (info != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info available",
                        tint = BrandTeal,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            val isNumericValue = value.any { it.isDigit() }
            Text(
                text = value,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxValueWidth),
                style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BodyFontFamily,
                    fontFeatureSettings = if (isNumericValue) "tnum" else null,
                    fontWeight = FontWeight.Bold,
                    color = highlightColor ?: MaterialTheme.colorScheme.onSurface
                ) else MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BodyFontFamily,
                    fontFeatureSettings = if (isNumericValue) "tnum" else null,
                    fontWeight = FontWeight.SemiBold,
                    color = highlightColor ?: MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
