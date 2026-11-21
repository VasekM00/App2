package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.domain.parseAccountListJson
import com.example.domain.serializeAccountListJson
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import java.util.Locale

private val CZECH_ACCOUNT_REGEX = Regex("""^(?:\d{1,6}-)?\d{2,10}/\d{4}$""")
private val CZ_IBAN_REGEX = Regex("""^CZ\d{22}$""")

internal fun normalizeAndValidateCzechAccount(rawInput: String): String? {
    val compact = rawInput.trim().replace(" ", "").uppercase(Locale.ROOT)
    if (compact.isBlank()) return null
    return when {
        CZECH_ACCOUNT_REGEX.matches(compact) -> compact
        CZ_IBAN_REGEX.matches(compact) -> compact
        else -> null
    }
}

private enum class AccountTargetList {
    OWN_HOUSEHOLD,
    PARENTAL_GIFT
}

@Composable
fun BankAccountsReconciliationCard(
    knownFamilyAccountsJson: String,
    knownParentalGiftAccountsJson: String,
    onUpdateAccounts: (newFamilyJson: String, newParentalGiftJson: String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    externalExpandTrigger: Int = 0,
    externalExpandState: Boolean? = null,
    sectionKey: String? = null,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var lastExpandTrigger by remember { mutableIntStateOf(externalExpandTrigger) }
    var lastFocusTrigger by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(externalExpandTrigger) {
        if (externalExpandTrigger != lastExpandTrigger) {
            lastExpandTrigger = externalExpandTrigger
            if (externalExpandState != null) {
                expanded = externalExpandState
            }
        }
    }

    LaunchedEffect(focusedSectionTrigger, focusedSectionKey) {
        if (focusedSectionTrigger > lastFocusTrigger &&
            sectionKey != null &&
            focusedSectionKey == sectionKey
        ) {
            expanded = true
            lastFocusTrigger = focusedSectionTrigger
        }
    }

    val familyAccounts = remember(knownFamilyAccountsJson) {
        parseAccountListJson(knownFamilyAccountsJson)
    }
    val giftAccounts = remember(knownParentalGiftAccountsJson) {
        parseAccountListJson(knownParentalGiftAccountsJson)
    }

    var dialogTarget by remember { mutableStateOf<AccountTargetList?>(null) }
    var accountInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val totalCount = familyAccounts.size + giftAccounts.size

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
                    .heightIn(min = 48.dp)
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
                        text = "Bank Accounts & Reconciliation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (totalCount == 0) {
                            "Configure own transfer & parental gift accounts"
                        } else {
                            "${familyAccounts.size} own · ${giftAccounts.size} parental gift accounts"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ColorPill(
                        text = if (totalCount > 0) "$totalCount SAVED" else "RECONCILE",
                        color = BrandTeal,
                        fontFamily = BodyFontFamily
                    )
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))

                // Section A: Own Household Accounts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Own Household Accounts",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    ColorPill(
                        text = "SELF-NET",
                        color = BrandGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Transfers to or from your own accounts are auto-netted during import so internal moves do not inflate income or spending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (familyAccounts.isEmpty()) {
                    Text(
                        text = "No own accounts added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        familyAccounts.forEach { acct ->
                            AccountEntryRow(
                                account = acct,
                                onDelete = {
                                    val nextFamily = serializeAccountListJson(familyAccounts.filterNot { it == acct })
                                    onUpdateAccounts(nextFamily, knownParentalGiftAccountsJson)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        accountInput = ""
                        inputError = null
                        dialogTarget = AccountTargetList.OWN_HOUSEHOLD
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("add_family_account_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Own Household Account", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section B: Tax-Exempt Parental / Family Gift Accounts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tax-Exempt Parental / Family Gift Accounts",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    ColorPill(
                        text = "TAX-EXEMPT",
                        color = GoodGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Incoming credits from parents or close relatives (ZDP § 4a & § 10(3)(c)) are categorized as 100% tax-exempt gifts and never netted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (giftAccounts.isEmpty()) {
                    Text(
                        text = "No parental / family gift accounts added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        giftAccounts.forEach { acct ->
                            AccountEntryRow(
                                account = acct,
                                onDelete = {
                                    val nextGift = serializeAccountListJson(giftAccounts.filterNot { it == acct })
                                    onUpdateAccounts(knownFamilyAccountsJson, nextGift)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        accountInput = ""
                        inputError = null
                        dialogTarget = AccountTargetList.PARENTAL_GIFT
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("add_gift_account_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Parental / Family Gift Account", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    val currentTarget = dialogTarget
    if (currentTarget != null) {
        val isOwn = currentTarget == AccountTargetList.OWN_HOUSEHOLD
        AlertDialog(
            onDismissRequest = { dialogTarget = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandTeal.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isOwn) "Add Own Household Account" else "Add Parental Gift Account",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isOwn) {
                            "Enter a Czech account number (e.g. 123456-9876543210/0800) or Czech IBAN (CZ + 22 digits)."
                        } else {
                            "Enter the sender's Czech account number or CZ IBAN for tax-exempt family gifts."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = {
                            accountInput = it
                            inputError = null
                        },
                        label = { Text("Account Number or CZ IBAN") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bank_account_input")
                    )
                    if (inputError != null) {
                        Text(
                            text = inputError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalized = normalizeAndValidateCzechAccount(accountInput)
                        if (normalized == null) {
                            inputError = "Enter valid Czech format (e.g. 123456789/0800) or CZ IBAN (24 chars)."
                            return@Button
                        }
                        if (isOwn) {
                            if (giftAccounts.any { it.equals(normalized, ignoreCase = true) }) {
                                inputError = "Already saved as a Parental Gift account. Remove it there first."
                                return@Button
                            }
                            val nextFamily = serializeAccountListJson(familyAccounts + normalized)
                            onUpdateAccounts(nextFamily, knownParentalGiftAccountsJson)
                        } else {
                            if (familyAccounts.any { it.equals(normalized, ignoreCase = true) }) {
                                inputError = "Already saved as an Own Household account. Remove it there first."
                                return@Button
                            }
                            val nextGift = serializeAccountListJson(giftAccounts + normalized)
                            onUpdateAccounts(knownFamilyAccountsJson, nextGift)
                        }
                        dialogTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = OnBrandTeal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Save Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dialogTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AccountEntryRow(
    account: String,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = account,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = BodyFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove account $account",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
