package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.CustomLumpSumItem
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLumpSums
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeCustomLumpSums
import com.example.ui.components.ChipWrapRow
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull
import java.util.UUID

@Composable
fun SettingsAddCategoryDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var newCategoryAmount by rememberSaveable { mutableStateOf("") }
    var categoryErrorText by rememberSaveable { mutableStateOf<String?>(null) }

    val presetSuggestions = listOf("Pets & Vet", "Sports & Gym", "Education & Courses", "Subscriptions", "Home Improvement")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text("Add Expense Category", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Customize household budget",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Preset Suggestions Row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PRESET SUGGESTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontSize = 10.sp,
                            color = BrandTeal
                        )
                    )
                    ChipWrapRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetSuggestions.forEach { preset ->
                            val isSelected = newCategoryName == preset
                            Surface(
                                onClick = {
                                    newCategoryName = preset
                                    categoryErrorText = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BrandTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandTeal.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = preset,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = {
                        newCategoryName = it
                        categoryErrorText = null
                    },
                    label = { Text("Category Name (e.g. Pets & Vet)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    isError = categoryErrorText != null && newCategoryName.trim().isEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_category_name_input")
                )
                OutlinedTextField(
                    value = newCategoryAmount,
                    onValueChange = {
                        newCategoryAmount = it
                        categoryErrorText = null
                    },
                    label = { Text("Monthly Amount", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    suffix = { Text("Kč") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = categoryErrorText != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_category_amount_input")
                )
                if (categoryErrorText != null) {
                    Text(
                        text = categoryErrorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newCategoryName.trim()
                    val amt = newCategoryAmount.parseUserAmountOrNull()
                    if (name.isEmpty()) {
                        categoryErrorText = "Please enter a category name."
                    } else if (amt == null || amt <= 0.0) {
                        categoryErrorText = "Please enter a positive monthly amount in Kč."
                    } else {
                        val newItem = CustomExpenseItem(id = UUID.randomUUID().toString(), name = name, amount = amt)
                        dispatchSettingsPatch(settings, onUpdateSettings, onReduceSettings) { cur ->
                            val currentList = parseCustomExpenses(cur.customExpensesJson)
                            cur.copy(customExpensesJson = serializeCustomExpenses(currentList + newItem))
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_add_category_button")
            ) {
                Text("Add Category", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsAddLumpSumDialog(
    settings: SettingsEntity,
    initialItem: CustomLumpSumItem?,
    onDismiss: () -> Unit,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    var newLumpSumName by rememberSaveable(initialItem?.id, settings.baseYear) { mutableStateOf(initialItem?.name ?: "") }
    var newLumpSumYear by rememberSaveable(initialItem?.id, settings.baseYear) { mutableStateOf(initialItem?.year?.toString() ?: "${settings.baseYear + 5}") }
    var newLumpSumMonth by rememberSaveable(initialItem?.id, settings.baseYear) { mutableStateOf(initialItem?.month) }
    var newLumpSumAmount by rememberSaveable(initialItem?.id, settings.baseYear) {
        mutableStateOf(
            if (initialItem != null) fmtEditableAmount(initialItem.amount) else ""
        )
    }
    var lumpSumErrorText by rememberSaveable(initialItem?.id) { mutableStateOf<String?>(null) }

    val quickDeltas = listOf(50000, 100000, 250000, 500000, 1000000)

    val monthOptions = listOf(
        null to "Full Year",
        1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr",
        5 to "May", 6 to "Jun", 7 to "Jul", 8 to "Aug",
        9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (initialItem == null) "Add Planned Lump Sum" else "Edit Planned Lump Sum",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Bonus, inheritance, property or gift",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newLumpSumName,
                    onValueChange = {
                        newLumpSumName = it
                        lumpSumErrorText = null
                    },
                    label = { Text("Description (e.g. Work Bonus / Inheritance / Gift)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newLumpSumYear,
                    onValueChange = {
                        newLumpSumYear = it
                        lumpSumErrorText = null
                    },
                    label = { Text("Planned Year (e.g. 2026)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Timing: ${if (newLumpSumMonth == null) "Full Year (Evenly spread)" else "Month $newLumpSumMonth"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ChipWrapRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        monthOptions.forEach { (mVal, mLabel) ->
                            FilterChip(
                                selected = newLumpSumMonth == mVal,
                                onClick = { newLumpSumMonth = mVal },
                                label = {
                                    Text(
                                        text = mLabel,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        softWrap = true,
                                        fontWeight = if (newLumpSumMonth == mVal) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandTeal,
                                    selectedLabelColor = OnBrandTeal
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                // Quick Amount Increment / Preset Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "QUICK AMOUNT PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontSize = 10.sp,
                            color = BrandTeal
                        )
                    )
                    ChipWrapRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickDeltas.forEach { amtPreset ->
                            Surface(
                                onClick = {
                                    val current = newLumpSumAmount.parseUserAmountOrNull() ?: 0.0
                                    val updated = current + amtPreset
                                    newLumpSumAmount = fmtEditableAmount(updated)
                                    lumpSumErrorText = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+${amtPreset / 1000}k",
                                        maxLines = 2,
                                        softWrap = true,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = BodyFontFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newLumpSumAmount,
                    onValueChange = {
                        newLumpSumAmount = it
                        lumpSumErrorText = null
                    },
                    label = { Text("Amount") },
                    suffix = { Text("Kč") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = lumpSumErrorText != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                if (lumpSumErrorText != null) {
                    Text(
                        text = lumpSumErrorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newLumpSumName.trim()
                    val yr = newLumpSumYear.trim().toIntOrNull()
                    val amt = newLumpSumAmount.parseUserAmountOrNull()
                    if (name.isEmpty()) {
                        lumpSumErrorText = "Please enter a description."
                    } else if (yr == null || yr !in 1950..2150) {
                        lumpSumErrorText = "Please enter a valid 4-digit year."
                    } else if (amt == null || amt <= 0.0) {
                        lumpSumErrorText = "Please enter a positive amount in Kč."
                    } else {
                        dispatchSettingsPatch(settings, onUpdateSettings, onReduceSettings) { cur ->
                            val currentList = parseCustomLumpSums(cur.customLumpSumsJson)
                            val updated = if (initialItem != null) {
                                currentList.map {
                                    if (it.id == initialItem.id) {
                                        it.copy(
                                            name = name,
                                            year = yr,
                                            month = newLumpSumMonth,
                                            amount = amt
                                        )
                                    } else it
                                }
                            } else {
                                currentList + CustomLumpSumItem(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    year = yr,
                                    month = newLumpSumMonth,
                                    amount = amt,
                                    enabled = true
                                )
                            }
                            cur.copy(customLumpSumsJson = serializeCustomLumpSums(updated))
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = if (initialItem == null) "Add Lump Sum" else "Save Changes",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
