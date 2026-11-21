package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull

internal data class EditCashFlowFieldData(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color,
    val currentValue: Double,
    val onConfirm: (Double) -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashFlowFieldEditDialog(
    data: EditCashFlowFieldData,
    onDismiss: () -> Unit
) {
    var editFieldText by rememberSaveable(data.key) { mutableStateOf(fmtEditableAmount(data.currentValue)) }
    var errorText by rememberSaveable(data.key) { mutableStateOf<String?>(null) }

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
                    color = data.accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, data.accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = data.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = data.subtitle,
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
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = editFieldText,
                    onValueChange = {
                        editFieldText = it
                        errorText = null
                    },
                    label = { Text("Monthly Amount") },
                    suffix = { Text("Kč") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorText != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = BodyFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-1000, 1000, 5000).forEach { delta ->
                        Surface(
                            onClick = {
                                val current = editFieldText.parseUserAmountOrNull() ?: data.currentValue
                                val updated = (current + delta).coerceAtLeast(0.0)
                                editFieldText = fmtEditableAmount(updated)
                                errorText = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (delta > 0) "+$delta" else "$delta",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = BodyFontFamily,
                                        fontFeatureSettings = "tnum",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            editFieldText = fmtEditableAmount(data.currentValue)
                            errorText = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = editFieldText.parseUserAmountOrNull()
                    if (amt == null || amt < 0.0) {
                        errorText = "Please enter a valid non-negative amount in Kč."
                    } else {
                        data.onConfirm(amt)
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
                Text("Save Changes", fontWeight = FontWeight.Bold)
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
internal fun EditableCashFlowRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    badgeText: String? = null,
    badgeColor: Color = GoodGreen,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .then(
            if (onEdit != null) Modifier.clickable(role = Role.Button, onClick = onEdit)
            else if (info != null && onShowInfo != null) Modifier.infoTapHold(info, onShowInfo)
            else Modifier
        )
        .padding(vertical = 6.dp, horizontal = 2.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
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
                    cornerRadius = 6.dp
                )
            }
            if (info != null && onShowInfo != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info on $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .infoTapHold(info, onShowInfo)
                        .padding(1.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (onEdit != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit $label",
                    tint = BrandTeal,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
internal fun ExpenseCategoriesCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList()
) {
    val scrollState = rememberScrollState()
    val s = state.settings
    val deletedSet = remember(s.deletedCategoriesJson) {
        parseDeletedCategories(s.deletedCategoriesJson)
    }
    val customCategories = remember(s.customExpensesJson) {
        parseCustomExpenses(s.customExpensesJson)
    }

    var activeEditKey by rememberSaveable { mutableStateOf<String?>(null) }

    val currentYm = remember { java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).toString() }
    val hasImportedCurrentMonth = remember(allImportedTransactions, currentYm) {
        allImportedTransactions.any { it.yearMonth == currentYm }
    }
    val activeLedger = remember(ledgerEntries, currentYm, hasImportedCurrentMonth) {
        if (hasImportedCurrentMonth) {
            null
        } else {
            ledgerEntries.find {
                it.yearMonth == currentYm &&
                    !it.notes.startsWith("Imported from", ignoreCase = true) &&
                    !it.notes.startsWith("Direct Bank Statement", ignoreCase = true)
            }
        }
    }

    val canEdit = onUpdateSettings != null || onReduceSettings != null
    val patchSettings: ((SettingsEntity) -> SettingsEntity) -> Unit = { transform ->
        com.example.ui.state.dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings, transform)
    }
    val activeDialogData: EditCashFlowFieldData? = if (canEdit) {
        when {
            activeEditKey == "rent" -> EditCashFlowFieldData(
                key = "rent",
                title = "Edit Rent / Housing",
                subtitle = "Monthly Household Living Expense",
                description = "Baseline monthly housing rent. Indexed annually to rent growth (${s.rentGrowthPct}%) / Czech CPI (${s.cpiInflationPct}%).",
                accentColor = BadRed,
                currentValue = s.rentMonthly,
                onConfirm = { amount -> patchSettings { it.copy(rentMonthly = amount) } }
            )
            activeEditKey == "groceries" -> EditCashFlowFieldData(
                key = "groceries",
                title = "Edit Groceries & Daily Living",
                subtitle = "Monthly Household Living Expense",
                description = "Monthly spending for food, supermarket purchases, and household consumables.",
                accentColor = BadRed,
                currentValue = s.groceriesMonthly,
                onConfirm = { amount -> patchSettings { it.copy(groceriesMonthly = amount) } }
            )
            activeEditKey == "transport" -> EditCashFlowFieldData(
                key = "transport",
                title = "Edit Transport",
                subtitle = "Monthly Household Living Expense",
                description = "Monthly public transit passes, fuel, and vehicle expenses.",
                accentColor = BadRed,
                currentValue = s.transportMonthly,
                onConfirm = { amount -> patchSettings { it.copy(transportMonthly = amount) } }
            )
            activeEditKey == "cafes" -> EditCashFlowFieldData(
                key = "cafes",
                title = "Edit Cafes & Restaurants",
                subtitle = "Monthly Household Living Expense",
                description = "Monthly dining out, specialty coffee, and social dining expenses.",
                accentColor = BadRed,
                currentValue = s.cafesMonthly,
                onConfirm = { amount -> patchSettings { it.copy(cafesMonthly = amount) } }
            )
            activeEditKey == "entertainment" -> EditCashFlowFieldData(
                key = "entertainment",
                title = "Edit Entertainment",
                subtitle = "Monthly Household Living Expense",
                description = "Cinema, events, gaming, books, and cultural activities.",
                accentColor = BadRed,
                currentValue = s.entertainmentMonthly,
                onConfirm = { amount -> patchSettings { it.copy(entertainmentMonthly = amount) } }
            )
            activeEditKey == "therapy" -> EditCashFlowFieldData(
                key = "therapy",
                title = "Edit Therapy / Physio",
                subtitle = "Monthly Household Living Expense",
                description = "Healthcare, mental health, physio, and wellness sessions.",
                accentColor = BadRed,
                currentValue = s.therapyMonthly,
                onConfirm = { amount -> patchSettings { it.copy(therapyMonthly = amount) } }
            )
            activeEditKey == "subscriptions" -> EditCashFlowFieldData(
                key = "subscriptions",
                title = "Edit Subscriptions",
                subtitle = "Monthly Household Living Expense",
                description = "Recurring streaming, SaaS, cloud storage, and membership services.",
                accentColor = BadRed,
                currentValue = s.subscriptionsMonthly,
                onConfirm = { amount -> patchSettings { it.copy(subscriptionsMonthly = amount) } }
            )
            activeEditKey == "charity" -> EditCashFlowFieldData(
                key = "charity",
                title = "Edit Charity",
                subtitle = "Monthly Household Living Expense",
                description = "Regular philanthropic gifts and donations.",
                accentColor = BadRed,
                currentValue = s.charityMonthly,
                onConfirm = { amount -> patchSettings { it.copy(charityMonthly = amount) } }
            )
            activeEditKey == "other_discretionary" -> EditCashFlowFieldData(
                key = "other_discretionary",
                title = "Edit Other Discretionary",
                subtitle = "Monthly Household Living Expense",
                description = "Unplanned personal shopping, gifts, hobbies, or miscellaneous.",
                accentColor = BadRed,
                currentValue = s.otherDiscretionaryMonthly,
                onConfirm = { amount -> patchSettings { it.copy(otherDiscretionaryMonthly = amount) } }
            )
            activeEditKey?.startsWith("custom:") == true -> {
                val id = activeEditKey?.removePrefix("custom:")
                customCategories.find { it.id == id }?.let { item ->
                    EditCashFlowFieldData(
                        key = "custom:${item.id}",
                        title = "Edit ${item.name}",
                        subtitle = "Monthly Household Living Expense",
                        description = "Custom monthly living expense category.",
                        accentColor = BadRed,
                        currentValue = item.amount,
                        onConfirm = { updatedVal ->
                            patchSettings { current ->
                                val currentList = parseCustomExpenses(current.customExpensesJson)
                                val updatedList = currentList.map {
                                    if (it.id == item.id) it.copy(amount = updatedVal) else it
                                }
                                current.copy(customExpensesJson = serializeCustomExpenses(updatedList))
                            }
                        }
                    )
                }
            }
            else -> null
        }
    } else null

    activeDialogData?.let { dialog ->
        CashFlowFieldEditDialog(
            data = dialog,
            onDismiss = { activeEditKey = null }
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
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                CardHeaderPill(
                    title = "Monthly Expenses",
                    subtitle = "Tap any row to edit amount",
                    badgeText = null,
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

                Spacer(modifier = Modifier.height(8.dp))

                // Section 1: Housing & Essentials
                Text(
                    text = "HOUSING & ESSENTIALS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))

                if (!deletedSet.contains("rent")) {
                    EditableCashFlowRow(
                        label = "Rent / Housing",
                        value = fmtCZK(s.rentMonthly),
                        badgeText = "INDEXED",
                        badgeColor = BrandTeal,
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "rent" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("groceries")) {
                    EditableCashFlowRow(
                        label = "Groceries & Daily Living",
                        value = fmtCZK(s.groceriesMonthly),
                        info = CashFlowMetricInfos.livingExpenses,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "groceries" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("transport")) {
                    EditableCashFlowRow(
                        label = "Transport",
                        value = fmtCZK(s.transportMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "transport" } } else null
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))

                // Section 2: Lifestyle & Discretionary
                Text(
                    text = "LIFESTYLE & DISCRETIONARY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandGold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))

                if (!deletedSet.contains("cafes")) {
                    EditableCashFlowRow(
                        label = "Cafes & Restaurants",
                        value = fmtCZK(s.cafesMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "cafes" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("entertainment")) {
                    EditableCashFlowRow(
                        label = "Entertainment",
                        value = fmtCZK(s.entertainmentMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "entertainment" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("therapy")) {
                    EditableCashFlowRow(
                        label = "Therapy / Physio",
                        value = fmtCZK(s.therapyMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "therapy" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("subscriptions")) {
                    EditableCashFlowRow(
                        label = "Subscriptions",
                        value = fmtCZK(s.subscriptionsMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "subscriptions" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("charity")) {
                    EditableCashFlowRow(
                        label = "Charity",
                        value = fmtCZK(s.charityMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "charity" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (!deletedSet.contains("other_discretionary")) {
                    EditableCashFlowRow(
                        label = "Other Discretionary",
                        value = fmtCZK(s.otherDiscretionaryMonthly),
                        onEdit = if (canEdit) { { activeEditKey = "other_discretionary" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }

                if (customCategories.isNotEmpty()) {
                    customCategories.forEach { item ->
                        EditableCashFlowRow(
                            label = item.name,
                            value = fmtCZK(item.amount),
                            onEdit = if (canEdit) { { activeEditKey = "custom:${item.id}" } } else null
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }

                if (s.childExpensesEnabled) {
                    val child1Label = s.child1Name.ifBlank { "Child 1" }
                    val child2Label = s.child2Name.ifBlank { "Child 2" }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 3: Children & Family
                    Text(
                        text = "CHILDREN & FAMILY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = BrandTeal
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (s.child1Enabled) {
                        val isEmbedded1 = s.currentChildCostsInBaseline && s.child1BirthYear <= s.baseYear
                        val c1 = FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s)
                        if (c1 > 0 || isEmbedded1) {
                            EditableCashFlowRow(
                                label = if (isEmbedded1) "$child1Label (in groceries & rent)" else "$child1Label Expenses (Age ${s.baseYear - s.child1BirthYear})",
                                value = if (isEmbedded1) fmtCZK(0.0) else fmtCZK(c1),
                                badgeText = if (isEmbedded1) "IN BASELINE" else null,
                                badgeColor = BrandGold,
                                info = CashFlowMetricInfos.childExpenses,
                                onShowInfo = onShowInfo
                            )
                            if (s.child2Enabled) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }
                    }

                    if (s.child2Enabled) {
                        val isEmbedded2 = s.currentChildCostsInBaseline && s.child2BirthYear <= s.baseYear
                        val c2 = FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s)
                        if (c2 > 0 || isEmbedded2) {
                            EditableCashFlowRow(
                                label = if (isEmbedded2) "$child2Label (in groceries & rent)" else "$child2Label Expenses (Age ${s.baseYear - s.child2BirthYear})",
                                value = if (isEmbedded2) fmtCZK(0.0) else fmtCZK(c2),
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
                    label = "Total Monthly Expenses",
                    value = fmtCZK(state.totalLivingCostMonthly),
                    isBold = true,
                    info = CashFlowMetricInfos.livingExpenses,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                val surplus = state.currentIncome.totalMonthly - state.totalLivingCostMonthly
                IncomeRow(
                    label = "Net Monthly Leftover",
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
