package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OnBrandTeal

@Composable
internal fun bankBadgeColor(bankName: String): Color {
    val isDark = LocalIsDarkTheme.current
    return when {
        bankName.contains("MONETA", ignoreCase = true) -> BrandTeal
        bankName.contains("CSOB", ignoreCase = true) || bankName.contains("ČSOB", ignoreCase = true) -> BrandGold
        else -> {
            val resolved = runCatching { com.example.util.BankType.valueOf(bankName.trim().uppercase(java.util.Locale.ROOT)) }
                .getOrElse {
                    when {
                        bankName.contains("MBANK", ignoreCase = true) -> com.example.util.BankType.MBANK
                        bankName.contains("CESKA_SPORITELNA", ignoreCase = true) -> com.example.util.BankType.CESKA_SPORITELNA
                        bankName.contains("KOMERCNI_BANKA", ignoreCase = true) -> com.example.util.BankType.KOMERCNI_BANKA
                        bankName.contains("FIO", ignoreCase = true) -> com.example.util.BankType.FIO
                        bankName.contains("RAIFFEISENBANK", ignoreCase = true) -> com.example.util.BankType.RAIFFEISENBANK
                        bankName.contains("AIR_BANK", ignoreCase = true) -> com.example.util.BankType.AIR_BANK
                        bankName.contains("UNICREDIT", ignoreCase = true) -> com.example.util.BankType.UNICREDIT
                        bankName.contains("CREDITAS", ignoreCase = true) -> com.example.util.BankType.CREDITAS
                        bankName.contains("REVOLUT", ignoreCase = true) -> com.example.util.BankType.REVOLUT
                        bankName.contains("WISE", ignoreCase = true) -> com.example.util.BankType.WISE
                        else -> com.example.util.BankType.GENERIC
                    }
                }
            if (resolved == com.example.util.BankType.GENERIC) BrandTeal
            else Color(com.example.util.statement.BankDetector.bankColorHex(resolved, isDark))
        }
    }
}

internal fun friendlyBankName(bankName: String): String =
    com.example.util.statement.BankDetector.friendlyBankName(bankName)

internal fun bankShortBadge(bankName: String): String =
    com.example.util.statement.BankDetector.bankShortBadge(bankName)

@Composable
internal fun BankStatementImportGuidanceDialog(
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandTeal.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Import Statement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoodGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "CSV / TSV Tabular (Recommended)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoodGreen
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Direct export from online banking (Moneta, ČSOB, mBank, Fio, Air Bank, Raiffeisen, etc.). Guarantees 100% deterministic precision without layout wrapping errors.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PDF E-Statement (Alternative)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Parses PDF statements via text extraction heuristics. Recommended when digital CSV exports are unavailable.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSelectFile,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_guidance_select_file")
            ) {
                Text("Select Statement File", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
internal fun LedgerAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        suffix = { Text(" Kč", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier
    )
}

@Composable
internal fun ResponsiveFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 290.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}
