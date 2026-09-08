package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.CzechMerchantCatalog
import com.example.util.Formatters.fmtCZK
import com.example.util.MerchantLookupService
import com.example.util.ParsedBankTransaction
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerModal(
    tx: ParsedBankTransaction,
    onSelectCategory: (BankTransactionType, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(tx.category) }
    var rememberChoice by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    var isSearchingOnline by remember { mutableStateOf(false) }
    var onlineLookupResult by remember { mutableStateOf<MerchantLookupService.LookupResult?>(null) }

    val rawTitle = if (tx.counterpartyName.isNotBlank()) tx.counterpartyName else tx.message.ifBlank { "Transaction" }
    val cleanTitle = BankStatementImporter.cleanPaymentDescription(rawTitle)
    val searchQuery = CzechMerchantCatalog.suggestMerchantSearchQuery(cleanTitle)

    val availableCategories = listOf(
        BankTransactionType.GROCERIES to "Groceries",
        BankTransactionType.DINING_RESTAURANT to "Dining & Cafes",
        BankTransactionType.SHOPPING_GOODS to "Shopping",
        BankTransactionType.HEALTH_DRUGSTORE to "Health & Pharmacy",
        BankTransactionType.TRANSPORTATION to "Transport & Fuel",
        BankTransactionType.SUBSCRIPTIONS_MEDIA to "Subscriptions",
        BankTransactionType.SERVICES_UTILITIES to "Utilities & Services",
        BankTransactionType.CHARITY_DONATION to "Charity & Donations",
        BankTransactionType.HOUSING_RENT to "Rent & Housing",
        BankTransactionType.ATM_CASH to "Cash / ATM",
        BankTransactionType.GENERAL_EXPENSE to "General Expense",
        BankTransactionType.SALARY_VACLAV to "Salary (Václav)",
        BankTransactionType.SALARY_ELEONORA to "Salary (Eleonora)",
        BankTransactionType.PARENTAL_BENEFIT to "Parental Benefit",
        BankTransactionType.OTHER_INFLOW to "Other Inflow",
        BankTransactionType.INVESTMENT_PORTU to "Portu DCA",
        BankTransactionType.INVESTMENT_DIP to "DIP Pension",
        BankTransactionType.INVESTMENT_DPS to "DPS Pension",
        BankTransactionType.INTERNAL_TRANSFER to "Internal Transfer (Netted)"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
                .testTag("category_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assign Category",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BrandTeal,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transaction Summary Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tx.date,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            if (tx.counterpartyAccount.isNotBlank()) {
                                Text(
                                    text = tx.counterpartyAccount,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }

                        Text(
                            text = (if (tx.amount > 0) "+" else "") + fmtCZK(tx.amount),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (tx.amount > 0) GoodGreen else BadRed
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Online Merchant Lookup Action
                Surface(
                    shape = RoundedCornerShape(8.dp),
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
                                    text = if (searchQuery.isNotBlank()) "Query: $searchQuery" else "Search database",
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
                                            val res = MerchantLookupService.lookupMerchant(cleanTitle)
                                            onlineLookupResult = res
                                            if (res.suggestedCategory != null) {
                                                selectedCategory = res.suggestedCategory
                                            }
                                            isSearchingOnline = false
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
                                    modifier = Modifier.height(28.dp)
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
                                        fontSize = 10.5.sp,
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (res.suggestedCategory != null) {
                                        "Found: ${res.snippet} -> ${getCategoryDisplayName(res.suggestedCategory)}"
                                    } else {
                                        "No exact match found online"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (res.suggestedCategory != null) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select Category:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Category Flow
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableCategories.forEach { (catType, label) ->
                            val isSelected = selectedCategory == catType
                            val catColor = getCategoryColorValue(catType)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) catColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .clickable { selectedCategory = catType }
                                    .testTag("category_option_${catType.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Remember for merchant checkbox
                if (searchQuery.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberChoice = !rememberChoice }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = rememberChoice,
                            onCheckedChange = { rememberChoice = it },
                            colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remember this for \"$searchQuery\"",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onSelectCategory(selectedCategory, rememberChoice)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Apply Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun getCategoryColorValue(type: BankTransactionType): Color = when (type) {
    BankTransactionType.SALARY_VACLAV,
    BankTransactionType.SALARY_ELEONORA,
    BankTransactionType.PARENTAL_BENEFIT,
    BankTransactionType.OTHER_INFLOW -> Color(0xFF16A34A)
    BankTransactionType.HOUSING_RENT -> Color(0xFFEA580C)
    BankTransactionType.GROCERIES -> Color(0xFFDC2626)
    BankTransactionType.DINING_RESTAURANT -> Color(0xFFD97706)
    BankTransactionType.TRANSPORTATION -> Color(0xFF0284C7)
    BankTransactionType.SHOPPING_GOODS -> Color(0xFF6366F1)
    BankTransactionType.HEALTH_DRUGSTORE -> Color(0xFFE11D48)
    BankTransactionType.SUBSCRIPTIONS_MEDIA -> Color(0xFF0D9488)
    BankTransactionType.SERVICES_UTILITIES -> Color(0xFF64748B)
    BankTransactionType.ATM_CASH -> Color(0xFF78716C)
    BankTransactionType.CHARITY_DONATION -> Color(0xFFEC4899)
    BankTransactionType.INVESTMENT_PORTU -> Color(0xFFD97706)
    BankTransactionType.INVESTMENT_DIP,
    BankTransactionType.INVESTMENT_DPS -> Color(0xFF0F766E)
    BankTransactionType.INTERNAL_TRANSFER -> Color(0xFF64748B)
    BankTransactionType.GENERAL_EXPENSE,
    BankTransactionType.LIFESTYLE_LIVING,
    BankTransactionType.UNCATEGORIZED -> Color(0xFF94A3B8)
}

fun getCategoryDisplayName(type: BankTransactionType): String = when (type) {
    BankTransactionType.SALARY_VACLAV -> "Salary (Václav)"
    BankTransactionType.SALARY_ELEONORA -> "Salary (Eleonora)"
    BankTransactionType.PARENTAL_BENEFIT -> "Parental Benefit"
    BankTransactionType.OTHER_INFLOW -> "Other Inflow"
    BankTransactionType.HOUSING_RENT -> "Rent & Housing"
    BankTransactionType.GROCERIES -> "Groceries"
    BankTransactionType.DINING_RESTAURANT -> "Dining & Cafes"
    BankTransactionType.TRANSPORTATION -> "Transport & Fuel"
    BankTransactionType.SHOPPING_GOODS -> "Shopping"
    BankTransactionType.HEALTH_DRUGSTORE -> "Health & Pharmacy"
    BankTransactionType.SUBSCRIPTIONS_MEDIA -> "Subscriptions"
    BankTransactionType.SERVICES_UTILITIES -> "Utilities & Services"
    BankTransactionType.ATM_CASH -> "Cash / ATM"
    BankTransactionType.CHARITY_DONATION -> "Charity & Donations"
    BankTransactionType.INVESTMENT_PORTU -> "Portu DCA"
    BankTransactionType.INVESTMENT_DIP -> "DIP Pension"
    BankTransactionType.INVESTMENT_DPS -> "DPS Pension"
    BankTransactionType.INTERNAL_TRANSFER -> "Internal Transfer (Netted)"
    BankTransactionType.GENERAL_EXPENSE -> "General Expense"
    BankTransactionType.LIFESTYLE_LIVING -> "Lifestyle"
    BankTransactionType.UNCATEGORIZED -> "General"
}
