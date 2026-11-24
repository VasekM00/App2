package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ImportedBankTransactionEntity
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.CzechMerchantCatalog
import com.example.util.Formatters.fmtExactCZK
import com.example.util.MerchantLookupService
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditImportedTransactionDialog(
    tx: ImportedBankTransactionEntity,
    matchingCountInMonth: Int = 1,
    onConfirmCategoryChange: (newCategory: BankTransactionType, rememberMerchant: Boolean, applyToAllMatching: Boolean) -> Unit,
    onToggleExcluded: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialCat = remember(tx.id, tx.category) {
        try {
            BankTransactionType.valueOf(tx.category)
        } catch (_: Exception) {
            BankTransactionType.UNCATEGORIZED
        }
    }

    var selectedCategory by rememberSaveable(tx.id, tx.category, stateSaver = bankTransactionTypeSaver(initialCat)) {
        mutableStateOf(initialCat)
    }
    var rememberMerchant by rememberSaveable(tx.id) { mutableStateOf(true) }
    var applyToAllMatching by rememberSaveable(tx.id, matchingCountInMonth) { mutableStateOf(matchingCountInMonth > 1) }
    val coroutineScope = rememberCoroutineScope()
    var isSearchingOnline by remember(tx.id) { mutableStateOf(false) }
    var onlineLookupResult by remember(tx.id) { mutableStateOf<MerchantLookupService.LookupResult?>(null) }

    val rawTitle = if (tx.counterpartyName.isNotBlank()) tx.counterpartyName else tx.message.ifBlank { "Transaction #${tx.id}" }
    val cleanTitle = BankStatementImporter.cleanPaymentDescription(rawTitle)
    val searchQuery = CzechMerchantCatalog.suggestMerchantSearchQuery(cleanTitle)
    val safeAmount = tx.amount.finiteOrZero()
    val isCredit = safeAmount > 0
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    val availableCategories = remember {
        listOf(
            BankTransactionType.GROCERIES,
            BankTransactionType.DINING_RESTAURANT,
            BankTransactionType.HOUSING_RENT,
            BankTransactionType.SHOPPING_GOODS,
            BankTransactionType.HEALTH_DRUGSTORE,
            BankTransactionType.TRANSPORTATION,
            BankTransactionType.SUBSCRIPTIONS_MEDIA,
            BankTransactionType.SERVICES_UTILITIES,
            BankTransactionType.LIFESTYLE_LIVING,
            BankTransactionType.GENERAL_EXPENSE,
            BankTransactionType.ATM_CASH,
            BankTransactionType.CHARITY_DONATION,
            BankTransactionType.INVESTMENT_PORTU,
            BankTransactionType.INVESTMENT_DIP,
            BankTransactionType.INVESTMENT_DPS,
            BankTransactionType.SALARY_VACLAV,
            BankTransactionType.SALARY_ELEONORA,
            BankTransactionType.PARENTAL_BENEFIT,
            BankTransactionType.FAMILY_GIFT,
            BankTransactionType.OTHER_INFLOW,
            BankTransactionType.INTERNAL_TRANSFER
        ).map { it to formatCategoryLabel(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = maxDialogHeight)
                .imePadding()
                .padding(vertical = 16.dp)
                .testTag("edit_imported_tx_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Edit Transaction",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${tx.bankName} • ${tx.date}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Transaction Detail Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rawTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (tx.isNetted) TextDecoration.LineThrough else null
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = (if (isCredit) "+" else "") + fmtExactCZK(safeAmount),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = BodyFontFamily,
                                        color = if (tx.isNetted) MaterialTheme.colorScheme.onSurfaceVariant else if (isCredit) GoodGreen else BadRed,
                                        textDecoration = if (tx.isNetted) TextDecoration.LineThrough else null
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (tx.message.isNotBlank() && tx.counterpartyName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tx.message,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (tx.counterpartyAccount.isNotBlank() || tx.variableSymbol.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (tx.counterpartyAccount.isNotBlank()) {
                                        Text(
                                            text = "Account: ${tx.counterpartyAccount}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = BodyFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        )
                                    }
                                    if (tx.variableSymbol.isNotBlank()) {
                                        Text(
                                            text = "VS: ${tx.variableSymbol}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = BodyFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        )
                                    }
                                }
                            }

                            if (tx.isNetted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BadRed.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (tx.nettingReason.isNotBlank()) "Excluded: ${tx.nettingReason}" else "Excluded from ledger totals",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = BadRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Online Merchant Lookup Action
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandTeal.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Merchant Lookup",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = BrandTeal
                                        )
                                    )
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Query: $searchQuery" else "Search offline Czech merchant catalog",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSearchingOnline) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = BrandTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            isSearchingOnline = true
                                            coroutineScope.launch {
                                                try {
                                                    val res = MerchantLookupService.lookupMerchant(cleanTitle)
                                                    onlineLookupResult = res
                                                    if (res.suggestedCategory != null) {
                                                        selectedCategory = res.suggestedCategory
                                                    }
                                                } finally {
                                                    isSearchingOnline = false
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_edit_tx_merchant_lookup")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = BrandTeal,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Search",
                                            fontSize = 11.sp,
                                            color = BrandTeal,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            onlineLookupResult?.let { res ->
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = BrandTeal.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (res.suggestedCategory != null) {
                                        "Found: ${res.snippet} -> ${formatCategoryLabel(res.suggestedCategory)}"
                                    } else {
                                        "No exact match found in offline catalog"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (res.suggestedCategory != null) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Chips Selector
                    Text(
                        text = "ASSIGN CATEGORY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableCategories.forEach { (type, label) ->
                            val isSelected = selectedCategory == type
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BrandTeal.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .heightIn(min = 48.dp)
                                    .clickable { selectedCategory = type }
                                    .testTag("cat_chip_${type.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = BrandTeal,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Options: Remember Merchant
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .toggleable(
                                value = rememberMerchant,
                                onValueChange = { rememberMerchant = it },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMerchant,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remember for future imports",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Auto-assigns this category when this merchant appears again",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Options: Apply to matching transactions in month
                    if (matchingCountInMonth > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .toggleable(
                                    value = applyToAllMatching,
                                    onValueChange = { applyToAllMatching = it },
                                    role = Role.Checkbox
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyToAllMatching,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Apply to all $matchingCountInMonth transactions this month",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Updates all matching instances in this statement period",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exclude / Restore Button
                    OutlinedButton(
                        onClick = {
                            onToggleExcluded()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (tx.isNetted) BrandTeal else BadRed
                        ),
                        border = BorderStroke(1.dp, if (tx.isNetted) BrandTeal.copy(alpha = 0.5f) else BadRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (tx.isNetted) Icons.Default.RestartAlt else Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (tx.isNetted) "Restore" else "Exclude",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Confirm Save Button
                    Button(
                        onClick = {
                            onConfirmCategoryChange(selectedCategory, rememberMerchant, applyToAllMatching)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            contentColor = OnBrandTeal
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_save_tx_category")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save Category",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
