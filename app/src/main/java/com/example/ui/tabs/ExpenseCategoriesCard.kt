package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomExpenses
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

internal data class EditCashFlowFieldData(
    val title: String,
    val description: String,
    val currentValue: Double,
    val onConfirm: (Double) -> Unit
)

@Composable
internal fun EditableCashFlowRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    badgeText: String? = null,
    badgeColor: Color = GoodGreen,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
            .padding(vertical = 1.dp)
    } else Modifier.padding(vertical = 1.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(clickModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                ColorPill(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 5.dp,
                    verticalPadding = 1.5.dp,
                    cornerRadius = 4.dp
                )
            }
            if (info != null && onShowInfo != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info on $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (onEdit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit $label",
                        tint = BrandTeal,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpenseCategoriesCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val deletedSet = remember(s.deletedCategoriesJson) {
        parseDeletedCategories(s.deletedCategoriesJson)
    }
    val customCategories = remember(s.customExpensesJson) {
        parseCustomExpenses(s.customExpensesJson)
    }

    var editFieldData by remember { mutableStateOf<EditCashFlowFieldData?>(null) }
    var editFieldText by remember { mutableStateOf("") }

    val activeYear = s.baseYear
    val activeMonth = remember { java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 }
    val currentYm = String.format(java.util.Locale.ROOT, "%04d-%02d", activeYear, activeMonth)
    val activeLedger = remember(ledgerEntries, currentYm) {
        ledgerEntries.find { it.yearMonth == currentYm } ?: ledgerEntries.maxByOrNull { it.yearMonth }
    }

    editFieldData?.let { dialog ->
        AlertDialog(
            onDismissRequest = { editFieldData = null },
            title = { Text(dialog.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dialog.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editFieldText,
                        onValueChange = { editFieldText = it },
                        label = { Text("Monthly Amount (CZK)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = editFieldText.replace(',', '.').trim()
                        val amt = sanitized.toDoubleOrNull() ?: dialog.currentValue
                        if (amt >= 0.0) {
                            dialog.onConfirm(amt)
                        }
                        editFieldData = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editFieldData = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                CardHeaderPill(
                    title = "Monthly Expense Breakdown",
                    subtitle = "Combined household living costs · Tap row to edit or info",
                    badgeText = "EXPENSES",
                    accentColor = BadRed
                )

                if (activeLedger != null && (activeLedger.expRent > 0.0 || activeLedger.expGroceries > 0.0 || activeLedger.expOther > 0.0)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ColorPill(
                        text = "SYNCED WITH LEDGER (${activeLedger.yearMonth})",
                        color = BrandTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 1.5.dp,
                        cornerRadius = 6.dp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Section 1: Housing & Essentials
                Text(
                    text = "HOUSING & ESSENTIALS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                if (!deletedSet.contains("rent")) {
                    EditableCashFlowRow(
                        label = "Rent / Housing",
                        value = fmtCZK(s.rentMonthly),
                        badgeText = "INDEXED",
                        badgeColor = BrandTeal,
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.rentMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Rent / Housing",
                                    description = "Baseline monthly housing rent. Indexed annually to Czech CPI inflation (${s.cpiInflationPct}%).",
                                    currentValue = s.rentMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(rentMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("groceries")) {
                    EditableCashFlowRow(
                        label = "Groceries & Daily Living",
                        value = fmtCZK(s.groceriesMonthly),
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.groceriesMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Groceries & Daily Living",
                                    description = "Monthly spending for food, supermarket purchases, and household consumables.",
                                    currentValue = s.groceriesMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(groceriesMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("transport")) {
                    EditableCashFlowRow(
                        label = "Transport",
                        value = fmtCZK(s.transportMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.transportMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Transport",
                                    description = "Monthly public transit passes (Lítačka), fuel, and vehicle expenses.",
                                    currentValue = s.transportMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(transportMonthly = it)) }
                                )
                            }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(5.dp))

                // Section 2: Lifestyle & Discretionary
                Text(
                    text = "LIFESTYLE & DISCRETIONARY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandGold
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                if (!deletedSet.contains("cafes")) {
                    EditableCashFlowRow(
                        label = "Cafes & Restaurants",
                        value = fmtCZK(s.cafesMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.cafesMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Cafes & Restaurants",
                                    description = "Monthly dining out, specialty coffee, and social dining expenses.",
                                    currentValue = s.cafesMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(cafesMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("entertainment")) {
                    EditableCashFlowRow(
                        label = "Entertainment",
                        value = fmtCZK(s.entertainmentMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.entertainmentMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Entertainment",
                                    description = "Cinema, events, gaming, books, and cultural activities.",
                                    currentValue = s.entertainmentMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(entertainmentMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("therapy")) {
                    EditableCashFlowRow(
                        label = "Therapy / Physio",
                        value = fmtCZK(s.therapyMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.therapyMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Therapy / Physio",
                                    description = "Healthcare, mental health, physio, and wellness sessions.",
                                    currentValue = s.therapyMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(therapyMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("subscriptions")) {
                    EditableCashFlowRow(
                        label = "Subscriptions",
                        value = fmtCZK(s.subscriptionsMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.subscriptionsMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Subscriptions",
                                    description = "Recurring streaming, SaaS, cloud storage, and membership services.",
                                    currentValue = s.subscriptionsMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(subscriptionsMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("charity")) {
                    EditableCashFlowRow(
                        label = "Charity",
                        value = fmtCZK(s.charityMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.charityMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Charity",
                                    description = "Regular philanthropic gifts and donations.",
                                    currentValue = s.charityMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(charityMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                    EditableCashFlowRow(
                        label = "Other Discretionary",
                        value = fmtCZK(s.otherDiscretionaryMonthly),
                        onEdit = if (onUpdateSettings != null) {
                            {
                                editFieldText = s.otherDiscretionaryMonthly.toInt().toString()
                                editFieldData = EditCashFlowFieldData(
                                    title = "Edit Other Discretionary",
                                    description = "Unplanned personal shopping, gifts, hobbies, or miscellaneous.",
                                    currentValue = s.otherDiscretionaryMonthly,
                                    onConfirm = { onUpdateSettings(s.copy(otherDiscretionaryMonthly = it)) }
                                )
                            }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                }

                if (customCategories.isNotEmpty()) {
                    customCategories.forEach { item ->
                        EditableCashFlowRow(
                            label = item.name,
                            value = fmtCZK(item.amount),
                            onEdit = if (onUpdateSettings != null) {
                                {
                                    editFieldText = item.amount.toInt().toString()
                                    editFieldData = EditCashFlowFieldData(
                                        title = "Edit ${item.name}",
                                        description = "Custom monthly living expense category.",
                                        currentValue = item.amount,
                                        onConfirm = { updatedVal ->
                                            val updatedList = customCategories.map {
                                                if (it.id == item.id) it.copy(amount = updatedVal) else it
                                            }
                                            onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                                        }
                                    )
                                }
                            } else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                    }
                }

                if (s.childExpensesEnabled) {
                    val child1Label = s.child1Name.ifBlank { "Child 1" }
                    val child2Label = s.child2Name.ifBlank { "Child 2" }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(5.dp))

                    // Section 3: Children & Family
                    Text(
                        text = "CHILDREN & FAMILY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = BrandTeal
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))

                    if (s.child1Enabled) {
                        val isEmbedded1 = s.currentChildCostsInBaseline && s.child1BirthYear <= s.baseYear
                        val c1 = FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s)
                        if (c1 > 0 || isEmbedded1) {
                            EditableCashFlowRow(
                                label = if (isEmbedded1) "$child1Label (in groceries & rent)" else "$child1Label Expenses (Age ${s.baseYear - s.child1BirthYear})",
                                value = if (isEmbedded1) "0 Kč" else fmtCZK(c1),
                                badgeText = if (isEmbedded1) "IN BASELINE" else null,
                                badgeColor = BrandGold,
                                info = CashFlowMetricInfos.childExpenses,
                                onShowInfo = onShowInfo
                            )
                            if (s.child2Enabled) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                            }
                        }
                    }

                    if (s.child2Enabled) {
                        val isEmbedded2 = s.currentChildCostsInBaseline && s.child2BirthYear <= s.baseYear
                        val c2 = FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s)
                        if (c2 > 0 || isEmbedded2) {
                            EditableCashFlowRow(
                                label = if (isEmbedded2) "$child2Label (in groceries & rent)" else "$child2Label Expenses (Age ${s.baseYear - s.child2BirthYear})",
                                value = if (isEmbedded2) "0 Kč" else fmtCZK(c2),
                                badgeText = if (isEmbedded2) "IN BASELINE" else null,
                                badgeColor = BrandGold,
                                info = CashFlowMetricInfos.childExpenses,
                                onShowInfo = onShowInfo
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 1.5.dp,
                    color = BadRed
                )

                IncomeRow(
                    label = "TOTAL MONTHLY EXPENSES",
                    value = fmtCZK(state.totalLivingCostMonthly),
                    isBold = true,
                    info = CashFlowMetricInfos.livingExpenses,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.5.dp))
                val surplus = state.currentIncome.totalMonthly - state.totalLivingCostMonthly
                IncomeRow(
                    label = "NET MONTHLY SURPLUS",
                    value = fmtCZK(surplus),
                    isBold = true,
                    valueColor = if (surplus >= 0.0) GoodGreen else BadRed,
                    info = CashFlowMetricInfos.surplus,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
