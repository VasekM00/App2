package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FireMilestone
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.brandSolidContentColor
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import com.example.util.Formatters.roundTo10k
import com.example.util.Formatters.roundTo1k
import kotlin.math.roundToInt

private data class MilestoneConfig(
    val milestone: FireMilestone,
    val accentColor: Color,
    val icon: ImageVector,
    val shortLabel: String,
    val levelIndex: Int,
    val metricInfo: MetricInfo
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FireMilestonesComparisonCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val milestones = state.fireMilestones
    val investableNetWorth = state.snapshot.investedLiquidTotal + state.snapshot.pensionTotal

    val baristaMilestone = milestones.baristaFire
    val items = buildList {
        add(
            MilestoneConfig(
                milestone = milestones.coastFire,
                accentColor = BrandTeal,
                icon = Icons.Default.Spa,
                shortLabel = "Coast",
                levelIndex = 1,
                metricInfo = PlanMetricInfos.coastFire
            )
        )
        if (baristaMilestone != null) {
            add(
                MilestoneConfig(
                    milestone = baristaMilestone,
                    accentColor = BrandTeal,
                    icon = Icons.Default.LocalCafe,
                    shortLabel = "Barista",
                    levelIndex = 2,
                    metricInfo = PlanMetricInfos.baristaFire
                )
            )
        }
        add(
            MilestoneConfig(
                milestone = milestones.leanFire,
                accentColor = BrandBlue,
                icon = Icons.Default.Home,
                shortLabel = "Lean",
                levelIndex = if (baristaMilestone != null) 3 else 2,
                metricInfo = PlanMetricInfos.leanFire
            )
        )
        add(
            MilestoneConfig(
                milestone = milestones.standardFire,
                accentColor = GoodGreen,
                icon = Icons.Default.Shield,
                shortLabel = "Standard",
                levelIndex = if (baristaMilestone != null) 4 else 3,
                metricInfo = PlanMetricInfos.standardFire
            )
        )
        add(
            MilestoneConfig(
                milestone = milestones.fatFire,
                accentColor = BrandGold,
                icon = Icons.Default.Diamond,
                shortLabel = "Fat",
                levelIndex = if (baristaMilestone != null) 5 else 4,
                metricInfo = PlanMetricInfos.fatFire
            )
        )
    }

    // Current unlocked level determination
    val currentLevel = when {
        milestones.fatFire.isAchieved -> "Level ${items.size}: Fat FIRE"
        milestones.standardFire.isAchieved -> "Level ${if (baristaMilestone != null) 4 else 3}: Standard FIRE"
        milestones.leanFire.isAchieved -> "Level ${if (baristaMilestone != null) 3 else 2}: Lean FIRE"
        baristaMilestone?.isAchieved == true -> "Level 2: Barista FIRE"
        milestones.coastFire.isAchieved -> "Level 1: Coast FIRE"
        else -> "Level 0: Accumulation"
    }

    val defaultTargetId = items.firstOrNull { !it.milestone.isAchieved }?.milestone?.id ?: items.lastOrNull()?.milestone?.id ?: "standard"
    var selectedMilestoneId by rememberSaveable { mutableStateOf(defaultTargetId) }
    val activeConfig = items.find { it.milestone.id == selectedMilestoneId } ?: items.firstOrNull() ?: items[0]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fire_milestones_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "Independence Milestones",
                subtitle = "From compound freedom (Coast) to full retirement",
                badgeText = null,
                badgeColor = BrandGold,
                icon = Icons.Default.Flag,
                accentColor = BrandGold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Current Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentLevel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ColorPill(
                        text = "Invested: ${fmtCompact(investableNetWorth)}",
                        color = BrandTeal,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 7.dp,
                        verticalPadding = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Matrix Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.30f)
                )
                Text(
                    text = "TARGET",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.10f)
                )
                Text(
                    text = "MONTHLY",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.00f)
                )
                Text(
                    text = "STATUS",
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(0.85f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(4.dp))

            // Matrix Table Rows
            items.forEach { config ->
                val isSelected = selectedMilestoneId == config.milestone.id
                val m = config.milestone
                val roundedTarget = if (m.id == "standard") m.targetAmountToday else roundTo10k(m.targetAmountToday)
                val roundedSWR = if (m.id == "standard") m.monthlyPassiveIncome else roundTo1k(m.monthlyPassiveIncome)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) config.accentColor.copy(alpha = 0.12f)
                    else if (m.isAchieved) GoodGreen.copy(alpha = 0.05f)
                    else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, config.accentColor.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedMilestoneId = m.id
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tier Name + Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.30f)
                        ) {
                            Icon(
                                imageVector = config.icon,
                                contentDescription = null,
                                tint = config.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = config.shortLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) config.accentColor else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = m.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        lineHeight = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Target Capital
                        Text(
                            text = fmtCompact(roundedTarget),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1.10f)
                        )

                        // SWR
                        Text(
                            text = fmtCompact(roundedSWR),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = config.accentColor
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1.00f)
                        )

                        // Status / ETA
                        Box(
                            modifier = Modifier.weight(0.85f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (m.isAchieved) {
                                ColorPill(
                                    text = "DONE",
                                    color = GoodGreen,
                                    icon = Icons.Default.Check,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            } else {
                                ColorPill(
                                    text = m.estimatedYear?.let { "$it" } ?: "${state.settings.baseYear + 35}+",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Tier Detail Card
            val activeM = activeConfig.milestone
            val activeTarget = if (activeM.id == "standard") activeM.targetAmountToday else roundTo10k(activeM.targetAmountToday)
            val activeGap = (activeTarget - investableNetWorth).coerceAtLeast(0.0)
            val progressFloat = (activeM.progressPct / 100.0).toFloat().coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = activeConfig.accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, activeConfig.accentColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Active Target: ${activeM.name}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (onShowInfo != null) {
                                IconButton(
                                    onClick = { onShowInfo(activeConfig.metricInfo) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Info on ${activeM.name}",
                                        tint = activeConfig.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            ColorPill(
                                text = "${activeM.progressPct.roundToInt()}%",
                                color = if (activeM.isAchieved) GoodGreen else activeConfig.accentColor,
                                fontSize = 9.5.sp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }

                        if (activeM.isAchieved) {
                            ColorPill(
                                text = "+${fmtCompact(investableNetWorth - activeTarget)} Surplus",
                                color = GoodGreen,
                                fontSize = 10.sp,
                                horizontalPadding = 7.dp,
                                verticalPadding = 3.dp
                            )
                        } else {
                            Text(
                                text = "${fmtCompact(activeGap)} gap",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Smooth Continuous Progress Bar (Zero trailing dots)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        val p = progressFloat.coerceIn(0f, 1f)
                        if (p > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (activeM.isAchieved) GoodGreen else activeConfig.accentColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Detail Metrics Summary Row
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val useCompact = maxWidth < 360.dp
                        val targetText = if (useCompact) fmtCompact(activeTarget) else fmtCZK(activeTarget)
                        val swrVal = roundTo1k(activeM.monthlyPassiveIncome)
                        val swrText = if (useCompact) fmtCompact(swrVal) else fmtCZK(swrVal)
                        val gapVal = if (activeM.isAchieved) investableNetWorth - activeTarget else activeGap
                        val gapText = (if (activeM.isAchieved) "+" else "") + (if (useCompact) fmtCompact(gapVal) else fmtCZK(gapVal))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Target Capital",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 2,
                                    softWrap = true
                                )
                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = BodyFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Monthly Income",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 2,
                                    softWrap = true
                                )
                                Text(
                                    text = swrText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = BodyFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = activeConfig.accentColor
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (activeM.isAchieved) "Surplus" else "Remaining Gap",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 2,
                                    softWrap = true
                                )
                                Text(
                                    text = gapText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = BodyFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (activeM.isAchieved) GoodGreen else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val milestoneDetailInfo = MetricInfo(
                        title = "${activeM.name} Strategy & Rules",
                        category = "FIRE Milestone",
                        explanation = "${activeM.description}\n\n${
                            if (activeM.id == "coast") "Compound interest alone turns current capital into full financial independence without future savings."
                            else if (activeM.isAchieved) "Milestone unlocked! Your current portfolio exceeds this threshold."
                            else "At ${fmtPct(state.settings.safeWithdrawalRatePct)} SWR, reaching ${fmtCZK(activeTarget)} generates sustainable passive cash flow of ${fmtCZK(roundTo1k(activeM.monthlyPassiveIncome))} / month."
                        }",
                        formulaOrRule = "${fmtCZK(activeTarget)} @ ${fmtPct(state.settings.safeWithdrawalRatePct)} SWR",
                        accentColor = activeConfig.accentColor
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = activeConfig.accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, activeConfig.accentColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowInfo?.invoke(milestoneDetailInfo) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Milestone Strategy Details",
                                    tint = activeConfig.accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to view milestone strategy & rules",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = activeConfig.accentColor
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = activeConfig.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if ((onUpdateSettings != null || onReduceSettings != null) && activeM.id != "coast") {
                        val hasManualOverride = state.settings.fireTargetOverride > 0
                        val isCurrentOverride = (hasManualOverride && kotlin.math.abs(state.settings.fireTargetOverride - activeTarget) < 1.0) ||
                                (!hasManualOverride && activeM.id == "standard")

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = activeConfig.accentColor.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isCurrentOverride) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ColorPill(
                                        text = if (hasManualOverride) "Active Primary FIRE Goal (Manual)" else "Active Primary FIRE Goal (Auto)",
                                        color = BrandGold,
                                        fontSize = 10.sp,
                                        horizontalPadding = 7.dp,
                                        verticalPadding = 3.dp
                                    )
                                }
                                if (hasManualOverride) {
                                    TextButton(
                                        onClick = {
                                            com.example.ui.state.dispatchSettingsPatch(
                                    state.settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { it.copy(fireTargetOverride = 0.0) }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = "Reset to Auto",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Make this your target:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    )
                                )
                                Button(
                                    onClick = {
                                        com.example.ui.state.dispatchSettingsPatch(
                                    state.settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { it.copy(fireTargetOverride = activeTarget) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = activeConfig.accentColor,
                                        contentColor = brandSolidContentColor(activeConfig.accentColor)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Set as Primary Goal (${fmtCompact(activeTarget)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
