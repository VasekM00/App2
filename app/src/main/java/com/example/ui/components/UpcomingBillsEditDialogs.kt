package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.domain.parseCustomExpenses
import com.example.domain.serializeCustomExpenses
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.parseUserAmountOrNull

sealed class BillEditSource {
    data class BudgetField(val fieldKey: String, val currentAmount: Double) : BillEditSource()
    data class CustomBill(val itemId: String, val name: String, val amount: Double, val dayOfMonth: Int) : BillEditSource()
    data class StatementSubscription(val merchantKey: String, val displayName: String, val amount: Double, val dayOfMonth: Int) : BillEditSource()
}

@Composable
internal fun EditUpcomingBillDialog(
    bill: UpcomingBill,
    settings: SettingsEntity?,
    onDismiss: () -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?,
    onDismissSubscription: ((String) -> Unit)?,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    when (val src = bill.editSource) {
        is BillEditSource.StatementSubscription -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandTeal.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(src.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Detected from imported bank statements",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Monthly debit: ${fmtCZK(src.amount)} around day ${src.dayOfMonth}. of each month (merchant key: ${src.merchantKey}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "To recategorize individual charges or edit rules, open Monthly Ledger or Settings -> Merchant Rules. To hide this item from upcoming bills, dismiss it below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    if (onDismissSubscription != null) {
                        Button(
                            onClick = {
                                onDismissSubscription(src.merchantKey)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BadRed, contentColor = MaterialTheme.colorScheme.onError),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Dismiss Item", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Close")
                    }
                }
            )
        }

        is BillEditSource.BudgetField -> {
            if (settings == null || (onUpdateSettings == null && onReduceSettings == null)) return
            var amountText by remember(bill.id) { mutableStateOf(src.currentAmount.toLong().toString()) }
            var errorText by remember(bill.id) { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("edit_upcoming_bill_dialog"),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandTeal.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(bill.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Budget Setting · Day ${bill.dayOfMonth}. monthly",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .imePadding()
                    .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Derived from your Budget Settings (${bill.paymentType}). Updating this saves directly to your household plan and FIRE model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                errorText = null
                            },
                            label = { Text("Monthly Amount") },
                            suffix = { Text(" Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = errorText != null,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                            modifier = Modifier.fillMaxWidth().testTag("upcoming_bill_amount_input")
                        )
                        if (errorText != null) {
                            Text(errorText.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        ChipWrapRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(-1000, 1000, 5000).forEach { delta ->
                                Surface(
                                    onClick = {
                                        val curr = amountText.parseUserAmountOrNull() ?: src.currentAmount
                                        amountText = (curr + delta).coerceAtLeast(0.0).toLong().toString()
                                        errorText = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = if (delta > 0) "+$delta" else "$delta",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = BodyFontFamily)
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = {
                                    amountText = "0"
                                    errorText = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Set 0", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = amountText.parseUserAmountOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                            if (parsed == null) {
                                errorText = "Please enter a valid non-negative amount in Kč."
                            } else {
                                com.example.ui.state.dispatchSettingsPatch(
                                    settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { cur ->
                                    when (src.fieldKey) {
                                        "sett_rent" -> cur.copy(rentMonthly = parsed)
                                        "sett_portu_v" -> cur.copy(portuDcaMonthly = parsed)
                                        "sett_portu_e" -> cur.copy(ePortuDcaMonthly = parsed)
                                        "sett_dip_v" -> cur.copy(dipContributionMonthly = parsed)
                                        "sett_dip_e" -> cur.copy(eDipContributionMonthly = parsed)
                                        "sett_dps_v" -> cur.copy(dpsOwnContributionMonthly = parsed)
                                        "sett_dps_e" -> cur.copy(eDpsOwnContributionMonthly = parsed)
                                        else -> cur
                                    }
                                }
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = OnBrandTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("save_upcoming_bill_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel")
                    }
                }
            )
        }

        is BillEditSource.CustomBill -> {
            if (settings == null || (onUpdateSettings == null && onReduceSettings == null)) return
            CustomBillEditorDialog(
                title = "Edit Recurring Bill",
                initialName = src.name,
                initialAmount = src.amount,
                initialDayOfMonth = src.dayOfMonth,
                canDelete = true,
                onDismiss = onDismiss,
                onSave = { name, amount, day ->
                    com.example.ui.state.dispatchSettingsPatch(
                        settings,
                        onUpdateSettings ?: {},
                        onReduceSettings
                    ) { cur ->
                        val existing = parseCustomExpenses(cur.customExpensesJson)
                        val updatedList = existing.map {
                            if (it.id == src.itemId) it.copy(name = name, amount = amount, dayOfMonth = day) else it
                        }
                        cur.copy(customExpensesJson = serializeCustomExpenses(updatedList))
                    }
                    onDismiss()
                },
                onDelete = {
                    com.example.ui.state.dispatchSettingsPatch(
                        settings,
                        onUpdateSettings ?: {},
                        onReduceSettings
                    ) { cur ->
                        val existing = parseCustomExpenses(cur.customExpensesJson)
                        val updatedList = existing.filterNot { it.id == src.itemId }
                        cur.copy(customExpensesJson = serializeCustomExpenses(updatedList))
                    }
                    onDismiss()
                }
            )
        }

        null -> Unit
    }
}

@Composable
internal fun CustomBillEditorDialog(
    title: String,
    initialName: String,
    initialAmount: Double,
    initialDayOfMonth: Int,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, dayOfMonth: Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var nameText by remember(initialName) { mutableStateOf(initialName) }
    var amountText by remember(initialAmount) { mutableStateOf(if (initialAmount > 0.0) initialAmount.toLong().toString() else "") }
    var selectedDay by remember(initialDayOfMonth) { mutableIntStateOf(initialDayOfMonth.coerceIn(1, 28)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("custom_bill_editor_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (canDelete) Icons.Default.Edit else Icons.Default.Add, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                    }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Saved as recurring expense in your budget",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        errorText = null
                    },
                    label = { Text("Bill / Expense Name (e.g. Internet)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_bill_name_input")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("Monthly Amount") },
                    suffix = { Text(" Kč") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                    modifier = Modifier.fillMaxWidth().testTag("custom_bill_amount_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DUE DAY OF MONTH ($selectedDay.)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    ChipWrapRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1, 5, 10, 15, 20, 25, 28).forEach { day ->
                            val isSelected = selectedDay == day
                            Surface(
                                onClick = { selectedDay = day },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = "$day.",
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = BodyFontFamily,
                                            color = if (isSelected) OnBrandTeal else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (errorText != null) {
                    Text(errorText.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (canDelete && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BadRed),
                        border = BorderStroke(1.dp, BadRed.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_custom_bill_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Delete Bill", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = nameText.trim()
                    val parsedAmt = amountText.parseUserAmountOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    if (cleanName.isBlank()) {
                        errorText = "Please enter a bill name."
                    } else if (parsedAmt == null) {
                        errorText = "Please enter a valid non-negative amount."
                    } else {
                        onSave(cleanName, parsedAmt, selectedDay)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = OnBrandTeal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_custom_bill_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}
