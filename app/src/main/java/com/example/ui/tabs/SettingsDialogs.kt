package com.example.ui.tabs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
import com.example.ui.theme.BrandTeal
import com.example.util.BackupManager
import com.example.util.parseUserAmountOrNull
import java.util.UUID

@Composable
fun SettingsImportJsonDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onRestoreFullBackup: ((BackupManager.FullBackupPayload) -> Unit)?
) {
    val context = LocalContext.current
    var importJsonText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Settings JSON", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste your exported JSON settings snippet below to restore your configuration:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = {
                        importJsonText = it
                        importErrorMessage = null
                    },
                    label = { Text("JSON Data") },
                    minLines = 6,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                if (importErrorMessage != null) {
                    Text(
                        text = importErrorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullPayload = BackupManager.deserializeFullBackupFromJson(importJsonText, settings)
                    if (fullPayload != null && onRestoreFullBackup != null &&
                        (fullPayload.ledgerEntries.isNotEmpty() || fullPayload.actionStates.isNotEmpty() || fullPayload.importedTransactions.isNotEmpty())
                    ) {
                        onRestoreFullBackup(fullPayload)
                        onDismiss()
                        Toast.makeText(context, "Full backup successfully restored", Toast.LENGTH_SHORT).show()
                    } else {
                        val restored = fullPayload?.settings ?: BackupManager.deserializeSettingsFromJson(importJsonText, settings)
                        if (restored != null) {
                            onUpdateSettings(restored)
                            onDismiss()
                            Toast.makeText(context, "Settings successfully restored", Toast.LENGTH_SHORT).show()
                        } else {
                            importErrorMessage = "Invalid JSON format. Please check the pasted data."
                        }
                    }
                }
            ) {
                Text("Apply & Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsResetDialog(
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Settings?") },
        text = { Text("This will reset all your settings (like income, expenses, FIRE target) back to their default values. This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmReset()
                    onDismiss()
                }
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsClearDataDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear All User Data?", color = MaterialTheme.colorScheme.error) },
        text = { Text("This will PERMANENTLY delete all your ledger entries, action plan checks, and reset all your settings. Are you absolutely sure?") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmClear()
                    onDismiss()
                }
            ) {
                Text("Delete Everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsAddCategoryDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onUpdateSettings: (SettingsEntity) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryAmount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name (e.g. Pets & Vet)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_category_name_input")
                )
                OutlinedTextField(
                    value = newCategoryAmount,
                    onValueChange = { newCategoryAmount = it },
                    label = { Text("Monthly Amount", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_category_amount_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newCategoryName.ifBlank { "Expense Category" }
                    val amt = newCategoryAmount.parseUserAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val currentList = parseCustomExpenses(settings.customExpensesJson)
                    val newItem = CustomExpenseItem(id = UUID.randomUUID().toString(), name = name, amount = amt)
                    val updatedList = currentList + newItem
                    onUpdateSettings(settings.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_add_category_button")
            ) {
                Text("Add Category")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
    onUpdateSettings: (SettingsEntity) -> Unit
) {
    var newLumpSumName by remember(initialItem) { mutableStateOf(initialItem?.name ?: "") }
    var newLumpSumYear by remember(initialItem) { mutableStateOf(initialItem?.year?.toString() ?: "${settings.baseYear + 5}") }
    var newLumpSumMonth by remember(initialItem) { mutableStateOf(initialItem?.month) }
    var newLumpSumAmount by remember(initialItem) {
        mutableStateOf(
            if (initialItem != null) {
                if (initialItem.amount % 1.0 == 0.0) initialItem.amount.toLong().toString() else initialItem.amount.toString()
            } else ""
        )
    }

    val monthOptions = listOf(
        null to "Full Year",
        1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr",
        5 to "May", 6 to "Jun", 7 to "Jul", 8 to "Aug",
        9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "Add Planned Lump Sum" else "Edit Planned Lump Sum",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newLumpSumName,
                    onValueChange = { newLumpSumName = it },
                    label = { Text("Description (e.g. Work Bonus / Inheritance / Gift)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newLumpSumYear,
                    onValueChange = { newLumpSumYear = it },
                    label = { Text("Planned Year (e.g. 2026)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Timing: ${if (newLumpSumMonth == null) "Full Year (Evenly spread)" else "Month $newLumpSumMonth"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        monthOptions.forEach { (mVal, mLabel) ->
                            FilterChip(
                                selected = newLumpSumMonth == mVal,
                                onClick = { newLumpSumMonth = mVal },
                                label = {
                                    Text(
                                        text = mLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (newLumpSumMonth == mVal) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandTeal,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newLumpSumAmount,
                    onValueChange = { newLumpSumAmount = it },
                    label = { Text("Amount (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newLumpSumName.trim().ifBlank { "Lump Sum" }
                    val yr = newLumpSumYear.trim().toIntOrNull() ?: (settings.baseYear + 5)
                    val amt = newLumpSumAmount.parseUserAmountOrNull() ?: 0.0
                    if (amt > 0.0) {
                        val currentList = parseCustomLumpSums(settings.customLumpSumsJson)
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
                        onUpdateSettings(settings.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
                    }
                    onDismiss()
                }
            ) {
                Text(if (initialItem == null) "Add Lump Sum" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
