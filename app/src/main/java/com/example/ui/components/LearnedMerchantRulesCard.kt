package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.BankTransactionType
import com.example.util.MerchantCategoryManager

fun formatCategoryLabel(
    cat: BankTransactionType,
    primaryName: String? = null,
    spouseName: String? = null
): String = when (cat) {
    BankTransactionType.SALARY_VACLAV -> primaryName?.takeIf { it.isNotBlank() }?.let { "Salary ($it)" } ?: "Primary Salary"
    BankTransactionType.SALARY_ELEONORA -> spouseName?.takeIf { it.isNotBlank() }?.let { "Salary ($it)" } ?: "Partner Salary"
    BankTransactionType.PARENTAL_BENEFIT -> "Parental Benefit"
    BankTransactionType.FAMILY_GIFT -> "Family Gift (Tax-Exempt)"
    BankTransactionType.OTHER_INFLOW -> "Other Inflow"
    BankTransactionType.HOUSING_RENT -> "Housing & Rent"
    BankTransactionType.GROCERIES -> "Groceries"
    BankTransactionType.DINING_RESTAURANT -> "Dining & Restaurant"
    BankTransactionType.TRANSPORTATION -> "Transportation"
    BankTransactionType.SHOPPING_GOODS -> "Shopping & Goods"
    BankTransactionType.HEALTH_DRUGSTORE -> "Health & Drugstore"
    BankTransactionType.SUBSCRIPTIONS_MEDIA -> "Subscriptions & Media"
    BankTransactionType.SERVICES_UTILITIES -> "Services & Utilities"
    BankTransactionType.ATM_CASH -> "ATM & Cash"
    BankTransactionType.CHARITY_DONATION -> "Charity & Donation"
    BankTransactionType.GENERAL_EXPENSE -> "General Expense"
    BankTransactionType.LIFESTYLE_LIVING -> "Lifestyle & Living"
    BankTransactionType.INVESTMENT_PORTU -> "Portu Investment"
    BankTransactionType.INVESTMENT_DIP -> "DIP Investment"
    BankTransactionType.INVESTMENT_DPS -> "DPS Investment"
    BankTransactionType.INTERNAL_TRANSFER -> "Internal Transfer"
    BankTransactionType.UNCATEGORIZED -> "Uncategorized"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedMerchantRulesCard(
    rulesJson: String,
    onUpdateRulesJson: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    sectionKey: String? = null,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var lastFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(focusedSectionTrigger, focusedSectionKey) {
        if (focusedSectionTrigger > lastFocusTrigger &&
            sectionKey != null &&
            focusedSectionKey == sectionKey
        ) {
            expanded = true
            lastFocusTrigger = focusedSectionTrigger
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(BankTransactionType.GROCERIES) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val rulesMap = remember(rulesJson) {
        MerchantCategoryManager.parseRulesFromJson(rulesJson)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Learned Merchant Rules",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${rulesMap.size} custom rules configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ColorPill(
                        text = "${rulesMap.size} RULES",
                        color = BrandTeal,
                        fontFamily = BodyFontFamily
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Auto-categorization rules learned from statement imports or configured manually. These rules are automatically applied whenever bank statements are parsed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        newKeyword = ""
                        selectedCategory = BankTransactionType.GROCERIES
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("add_merchant_rule_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Merchant Rule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (rulesMap.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No custom merchant rules yet. Categorize a transaction during statement import with 'Remember for future imports' checked, or add a rule above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rulesMap.entries.sortedBy { it.key }.forEach { (keyword, category) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = keyword.uppercase(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = BodyFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatCategoryLabel(category),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandTeal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val updated = rulesMap.toMutableMap().apply { remove(keyword) }
                                            MerchantCategoryManager.removeOverride(context, keyword)
                                            MerchantCategoryManager.removeOverride(context, keyword.lowercase())
                                            onUpdateRulesJson(MerchantCategoryManager.serializeRulesToJson(updated))
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Rule",
                                            tint = BadRed,
                                            modifier = Modifier.size(18.dp)
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

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Add Merchant Rule",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("Merchant Keyword (e.g. LIDL, NETFLIX)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merchant_rule_keyword_input")
                    )

                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = formatCategoryLabel(selectedCategory),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            BankTransactionType.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(formatCategoryLabel(cat)) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newKeyword.trim().lowercase()
                        if (clean.isNotBlank()) {
                            val updated = rulesMap.toMutableMap().apply { put(clean, selectedCategory) }
                            MerchantCategoryManager.saveOverride(context, clean, selectedCategory)
                            onUpdateRulesJson(MerchantCategoryManager.serializeRulesToJson(updated))
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_add_merchant_rule_button")
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
