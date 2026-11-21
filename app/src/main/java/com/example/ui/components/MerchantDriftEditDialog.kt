package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.parseCustomExpenses
import com.example.domain.serializeCustomExpenses
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull

sealed class MerchantEditTarget {
    data class StatementMerchant(
        val merchantKey: String,
        val displayName: String,
        val currentCategory: BankTransactionType,
        val representativeTxIdsByMonth: List<Long>,
        val totalSpend: Double
    ) : MerchantEditTarget()

    data class BudgetCategory(
        val settingKey: String,
        val displayName: String,
        val monthlyAmount: Double
    ) : MerchantEditTarget()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditMerchantSpendTargetDialog(
    target: MerchantEditTarget,
    settings: SettingsEntity?,
    onDismiss: () -> Unit,
    onUpdateSettings: ((SettingsEntity) -> Unit)?,
    onUpdateImportedTransactionCategory: ((Long, BankTransactionType, Boolean, Boolean) -> Unit)?,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    when (target) {
        is MerchantEditTarget.StatementMerchant -> {
            if (onUpdateImportedTransactionCategory == null) return
            var selectedCategory by remember(target.merchantKey) { mutableStateOf(target.currentCategory) }
            var menuExpanded by remember { mutableStateOf(false) }
            var rememberRule by remember(target.merchantKey) { mutableStateOf(true) }

            val editableCategories = remember {
                listOf(
                    BankTransactionType.HOUSING_RENT,
                    BankTransactionType.GROCERIES,
                    BankTransactionType.DINING_RESTAURANT,
                    BankTransactionType.TRANSPORTATION,
                    BankTransactionType.SHOPPING_GOODS,
                    BankTransactionType.HEALTH_DRUGSTORE,
                    BankTransactionType.SUBSCRIPTIONS_MEDIA,
                    BankTransactionType.SERVICES_UTILITIES,
                    BankTransactionType.ATM_CASH,
                    BankTransactionType.CHARITY_DONATION,
                    BankTransactionType.LIFESTYLE_LIVING,
                    BankTransactionType.GENERAL_EXPENSE,
                    BankTransactionType.INVESTMENT_PORTU,
                    BankTransactionType.INVESTMENT_DIP,
                    BankTransactionType.INVESTMENT_DPS,
                    BankTransactionType.INTERNAL_TRANSFER
                )
            }

            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("edit_merchant_drift_dialog"),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandTeal.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(target.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Period Spend: ${fmtCZK(target.totalSpend)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Reassigning the category updates all matching transactions in the period and recalculates monthly budgets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = !menuExpanded }
                        ) {
                            OutlinedTextField(
                                value = formatCategoryLabel(selectedCategory),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            ExposedDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                editableCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(formatCategoryLabel(cat)) },
                                        onClick = {
                                            selectedCategory = cat
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .toggleable(
                                    value = rememberRule,
                                    onValueChange = { rememberRule = it },
                                    role = Role.Checkbox
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = rememberRule,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = BrandTeal)
                            )
                            Text(
                                text = "Remember rule for future statement imports",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            target.representativeTxIdsByMonth.forEachIndexed { idx, txId ->
                                onUpdateImportedTransactionCategory(
                                    txId,
                                    selectedCategory,
                                    rememberRule && idx == 0,
                                    true
                                )
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = OnBrandTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("save_merchant_category_button")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel")
                    }
                }
            )
        }

        is MerchantEditTarget.BudgetCategory -> {
            if (settings == null || (onUpdateSettings == null && onReduceSettings == null)) return
            var amountText by rememberSaveable(target.settingKey) { mutableStateOf(fmtEditableAmount(target.monthlyAmount)) }
            var errorText by rememberSaveable(target.settingKey) { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("edit_budget_drift_dialog"),
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
                            Text("Edit ${target.displayName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Budget Expenses Baseline (Monthly)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No bank statement transactions imported yet, so this ranking comes from your Budget Expenses. Updating this updates your monthly baseline and FIRE model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                errorText = null
                            },
                            label = { Text("Monthly Budget Amount") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = errorText != null,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                            modifier = Modifier.fillMaxWidth().testTag("budget_drift_amount_input")
                        )
                        if (errorText != null) {
                            Text(errorText.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-500, 500, 1000).forEach { delta ->
                                Surface(
                                    onClick = {
                                        val curr = amountText.parseUserAmountOrNull() ?: target.monthlyAmount
                                        amountText = fmtEditableAmount((curr + delta).coerceAtLeast(0.0))
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
                                dispatchSettingsPatch(
                                    settings,
                                    onUpdateSettings ?: {},
                                    onReduceSettings
                                ) { cur ->
                                    when {
                                        target.settingKey == "rent" -> cur.copy(rentMonthly = parsed)
                                        target.settingKey == "groceries" -> cur.copy(groceriesMonthly = parsed)
                                        target.settingKey == "cafes" -> cur.copy(cafesMonthly = parsed)
                                        target.settingKey == "therapy" -> cur.copy(therapyMonthly = parsed)
                                        target.settingKey == "charity" -> cur.copy(charityMonthly = parsed)
                                        target.settingKey == "entertainment" -> cur.copy(entertainmentMonthly = parsed)
                                        target.settingKey == "transport" -> cur.copy(transportMonthly = parsed)
                                        target.settingKey == "subscriptions" -> cur.copy(subscriptionsMonthly = parsed)
                                        target.settingKey == "other_discretionary" -> cur.copy(otherDiscretionaryMonthly = parsed)
                                        target.settingKey.startsWith("custom:") -> {
                                            val customId = target.settingKey.removePrefix("custom:")
                                            val items = parseCustomExpenses(cur.customExpensesJson).map {
                                                if (it.id == customId) it.copy(amount = parsed) else it
                                            }
                                            cur.copy(customExpensesJson = serializeCustomExpenses(items))
                                        }
                                        else -> cur
                                    }
                                }
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = OnBrandTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("save_budget_drift_button")
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
    }
}
