package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.domain.FullCalculationState
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact

@Composable
fun ActionChecklistCard(
    currentYear: Int,
    actionStates: Map<String, Boolean>,
    completedCount: Int,
    state: FullCalculationState,
    onToggleAction: (year: Int, actionId: String, currentIsDone: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var filterMode by rememberSaveable { mutableStateOf("ALL") } // "ALL", "PENDING", "COMPLETED"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("action_checklist_container"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GoodGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoodGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Execution Checklist",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            ColorPill(
                                text = "YEAR $currentYear",
                                color = GoodGreen,
                                fontSize = 9.sp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp
                            )
                        }
                        Text(
                            text = "High-impact tax & optimization moves for this year",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                ColorPill(
                    text = "$completedCount / ${ActionMeta.items.size} DONE",
                    color = GoodGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Filter Bar (All / Pending / Done)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pendingCount = ActionMeta.items.size - completedCount
                listOf(
                    "ALL" to "All (${ActionMeta.items.size})",
                    "PENDING" to "Pending ($pendingCount)",
                    "COMPLETED" to "Done ($completedCount)"
                ).forEach { (mode, label) ->
                    val isSelected = filterMode == mode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { filterMode = mode }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredItems = ActionMeta.items.filter { meta ->
                val isDone = actionStates["${currentYear}_${meta.id}"] == true
                when (filterMode) {
                    "PENDING" -> !isDone
                    "COMPLETED" -> isDone
                    else -> true
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filterMode == "PENDING") "All actions completed for Year $currentYear!" else "No completed actions yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                filteredItems.forEachIndexed { index, meta ->
                    val key = "${currentYear}_${meta.id}"
                    val isDone = actionStates[key] == true
                    val impact = state.actionsImpacts[meta.id] ?: 0.0

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isDone) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleAction(currentYear, meta.id, isDone)
                            }
                            .testTag("action_card_${meta.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleAction(currentYear, meta.id, isDone)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("action_cb_${meta.id}")
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    text = meta.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            if (impact > 0.0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoodGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "+${fmtCompact(impact)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = GoodGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (index < filteredItems.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
