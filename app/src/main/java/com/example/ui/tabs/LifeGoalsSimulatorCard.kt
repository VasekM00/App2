package com.example.ui.tabs

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomLifeGoalItem
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomLifeGoals
import com.example.domain.serializeCustomLifeGoals
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.parseUserAmountOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LifeGoalsSimulatorCard(
    state: FullCalculationState,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onShowInfo: (MetricInfo) -> Unit = {},
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val baseYear = state.settings.baseYear

    val customGoalsList = remember(state.settings.customGoalsJson) {
        parseCustomLifeGoals(state.settings.customGoalsJson)
    }

    var showAddGoalDialog by rememberSaveable { mutableStateOf(false) }

    // Cash flow feasibility calculations
    val monthlyNetIncome = state.currentIncome.totalMonthly
    val monthlyExpenses = state.totalLivingCostMonthly
    val monthlyNetSurplus = (monthlyNetIncome - monthlyExpenses).coerceAtLeast(0.0)
    val monthlyInvest = state.investMonthlyTotal

    val totalRequiredMonthlyGoals = customGoalsList.sumOf { goal ->
        val yrs = (goal.targetYear - baseYear).coerceAtLeast(1)
        val rem = (goal.targetAmountCzk - goal.currentSavedCzk).coerceAtLeast(0.0)
        rem / (yrs * 12.0)
    }
    val totalRemainingCapitalNeeded = customGoalsList.sumOf { (it.targetAmountCzk - it.currentSavedCzk).coerceAtLeast(0.0) }
    val totalFireDelayYears = if (monthlyInvest > 0) totalRemainingCapitalNeeded / (monthlyInvest * 12.0) else 0.0

    val surplusAfterGoals = monthlyNetSurplus - totalRequiredMonthlyGoals
    val capacityRatio = when {
        monthlyNetSurplus > 0 -> (totalRequiredMonthlyGoals / monthlyNetSurplus).coerceIn(0.0, 2.0)
        totalRequiredMonthlyGoals > 0 -> 2.0
        else -> 0.0
    }
    val isOverBudget = surplusAfterGoals < 0
    val tealColor = BrandTeal
    val goldColor = BrandGold

    val lifeGoalInfo = MetricInfo(
        title = "Life Event & Goal Simulator",
        category = "Milestone Planning",
        explanation = "Simulate non-FIRE financial milestones (home purchase, education, sabbatical). Evaluates cash flow feasibility against your monthly net income surplus and projects the opportunity cost delay on your primary FIRE target.",
        formulaOrRule = "Monthly Need: ${fmtCZK(totalRequiredMonthlyGoals)} · Surplus: ${fmtCZK(monthlyNetSurplus)}",
        accentColor = tealColor
    )

    val delayInfo = MetricInfo(
        title = "FIRE Goal Delay Impact",
        category = "Opportunity Cost",
        explanation = if (monthlyInvest > 0) {
            "Funding these milestones requires ${fmtCZK(totalRemainingCapitalNeeded)} in capital. Based on your current monthly investment flow of ${fmtCZK(monthlyInvest)}, this delays your primary FIRE target date by approximately ${String.format(java.util.Locale.ROOT, "%.1f", totalFireDelayYears)} years."
        } else {
            "No active monthly investment flow (DCA) detected. Capital delay cannot be projected until monthly surplus is invested."
        },
        formulaOrRule = "Remaining Capital / (Monthly DCA × 12)",
        accentColor = goldColor
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Summary & Feasibility Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("life_goals_simulator_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowInfo(lifeGoalInfo) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Life Event & Goal Simulator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = BrandTeal.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAddGoalDialog = true },
                        modifier = Modifier.testTag("add_goal_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Life Goal", tint = BrandTeal)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Feasibility Capacity Meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Goal Commitment vs Net Capacity",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    ColorPill(
                        text = if (isOverBudget) "Over Capacity" else "${(capacityRatio * 100).roundToInt()}% Allocated",
                        color = if (isOverBudget) MaterialTheme.colorScheme.error else GoodGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val p = capacityRatio.toFloat().coerceIn(0f, 1f)
                    if (p > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(p)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (isOverBudget) MaterialTheme.colorScheme.error else BrandTeal)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Monthly Need: ${fmtCZK(totalRequiredMonthlyGoals)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Net Surplus: ${fmtCZK(monthlyNetSurplus)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = BodyFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FIRE Impact Metric (info-on-touch)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandGold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowInfo(delayInfo) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = BrandGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (monthlyInvest > 0) {
                                    "FIRE Delay: ~${String.format(java.util.Locale.ROOT, "%.1f", totalFireDelayYears)} years"
                                } else {
                                    "FIRE Delay: N/A (no DCA flow)"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Delay Details",
                            tint = BrandGold.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Goals List
        customGoalsList.forEachIndexed { index, goal ->
            val yearsRemaining = (goal.targetYear - baseYear).coerceAtLeast(1)
            val monthsRemaining = yearsRemaining * 12
            val remainingAmount = (goal.targetAmountCzk - goal.currentSavedCzk).coerceAtLeast(0.0)
            val requiredMonthly = remainingAmount / monthsRemaining
            val progress = if (goal.targetAmountCzk > 0.0) {
                (goal.currentSavedCzk / goal.targetAmountCzk).coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            val goalFireDelay = if (monthlyInvest > 0) remainingAmount / (monthlyInvest * 12.0) else 0.0

            val iconVec = when (goal.iconName) {
                "home" -> Icons.Default.Home
                "school" -> Icons.Default.School
                "star" -> Icons.Default.Star
                else -> Icons.Default.Flag
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("life_goal_card_$index"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BrandTeal.copy(alpha = 0.15f))
                                    .padding(8.dp)
                            ) {
                                Icon(imageVector = iconVec, contentDescription = null, tint = BrandTeal)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Target Year: ${goal.targetYear} ($yearsRemaining ${if (yearsRemaining == 1) "year" else "years"} left)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                com.example.ui.state.dispatchSettingsPatch(
                                    state.settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { cur ->
                                    val curList = parseCustomLifeGoals(cur.customGoalsJson)
                                    val updatedList = curList.filterNot { it.id == goal.id }
                                    cur.copy(customGoalsJson = serializeCustomLifeGoals(updatedList))
                                }
                            },
                            modifier = Modifier.testTag("delete_goal_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Goal",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Smooth Continuous Progress Bar (Zero trailing dots)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val p = if (progress.isFinite()) progress.toFloat().coerceIn(0f, 1f) else 0f
                        if (p > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(BrandTeal)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Saved: ${fmtCompact(goal.currentSavedCzk)} / ${fmtCompact(goal.targetAmountCzk)} (${(progress * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = BodyFontFamily)
                        )
                        Text(
                            text = "Need: ${fmtCZK(requiredMonthly)}/month",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = BrandGold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ColorPill(
                        text = if (monthlyInvest > 0) "+${String.format(java.util.Locale.ROOT, "%.1f", goalFireDelay)} years to FIRE" else "No DCA flow",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 4.dp
                    )
                }
            }
        }
    }

    if (showAddGoalDialog) {
        var goalName by rememberSaveable(state.settings.baseYear) { mutableStateOf("") }
        var targetYearStr by rememberSaveable(state.settings.baseYear) { mutableStateOf((state.settings.baseYear + 5).toString()) }
        var targetAmountStr by rememberSaveable(state.settings.baseYear) { mutableStateOf("500000") }
        var currentSavedStr by rememberSaveable(state.settings.baseYear) { mutableStateOf("50000") }
        var validationError by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Add Custom Life Goal", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = {
                            goalName = it
                            validationError = null
                        },
                        label = { Text("Goal Name (e.g. Dream Cottage)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetYearStr,
                        onValueChange = {
                            targetYearStr = it
                            validationError = null
                        },
                        label = { Text("Target Year", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAmountStr,
                        onValueChange = {
                            targetAmountStr = it
                            validationError = null
                        },
                        label = { Text("Target Capital (Kč)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentSavedStr,
                        onValueChange = {
                            currentSavedStr = it
                            validationError = null
                        },
                        label = { Text("Current Savings (Kč)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val yr = targetYearStr.trim().toIntOrNull()
                        val targetAmt = targetAmountStr.parseUserAmountOrNull()
                        val savedAmt = if (currentSavedStr.isBlank()) 0.0 else currentSavedStr.parseUserAmountOrNull()

                        when {
                            yr == null || yr < state.settings.baseYear || yr > state.settings.baseYear + 80 ->
                                validationError = "Enter a valid target year (${state.settings.baseYear}-${state.settings.baseYear + 80})."
                            targetAmt == null || targetAmt <= 0.0 ->
                                validationError = "Target capital must be greater than 0 Kč."
                            savedAmt == null || savedAmt < 0.0 ->
                                validationError = "Current savings must be 0 Kč or greater."
                            else -> {
                                val name = goalName.ifBlank { "Custom Life Goal" }
                                val newItem = CustomLifeGoalItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = name,
                                    iconName = "flag",
                                    targetYear = yr,
                                    targetAmountCzk = targetAmt,
                                    currentSavedCzk = savedAmt
                                )
                                com.example.ui.state.dispatchSettingsPatch(
                                    state.settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { cur ->
                                    val curList = parseCustomLifeGoals(cur.customGoalsJson)
                                    cur.copy(customGoalsJson = serializeCustomLifeGoals(curList + newItem))
                                }
                                showAddGoalDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("save_custom_goal_button")
                ) {
                    Text("Add Goal")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddGoalDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
