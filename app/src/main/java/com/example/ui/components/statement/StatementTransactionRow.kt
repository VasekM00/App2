package com.example.ui.components.statement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.components.getCategoryColorValue
import com.example.ui.components.getCategoryDisplayName
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.GoodGreen
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.ParsedBankTransaction

@Composable
fun StatementTransactionRowItem(
    tx: ParsedBankTransaction,
    onClickCategory: (() -> Unit)? = null,
    onToggleExclude: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val safeAmount = tx.amount.finiteOrZero()
    val isPositive = safeAmount > 0
    val amountColor = if (isPositive) GoodGreen else BadRed

    val categoryColor = getCategoryColorValue(tx.category)
    val categoryLabel = getCategoryDisplayName(tx.category)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (tx.isExcluded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (tx.isExcluded) BadRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClickCategory != null) Modifier.clickable { onClickCategory() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.date,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.45f)),
                        modifier = Modifier.then(if (onClickCategory != null) Modifier.clickable { onClickCategory() } else Modifier)
                    ) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                    if (tx.isExcluded) {
                        ColorPill(
                            text = "EXCLUDED",
                            color = BadRed,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 4.dp,
                            verticalPadding = 1.dp
                        )
                    }
                }

                val rawTitle = if (tx.counterpartyName.isNotBlank()) tx.counterpartyName else tx.message.ifBlank { "Transaction" }
                val cleanTitle = BankStatementImporter.cleanPaymentDescription(rawTitle)

                val cleanMessage = BankStatementImporter.cleanPaymentDescription(tx.message)
                val distinctMessage = if (cleanMessage.isNotBlank() && !cleanMessage.equals(cleanTitle, ignoreCase = true)) cleanMessage else ""
                val subtitle = when {
                    tx.counterpartyAccount.isNotBlank() && distinctMessage.isNotBlank() -> "${tx.counterpartyAccount} · $distinctMessage"
                    tx.counterpartyAccount.isNotBlank() -> tx.counterpartyAccount
                    distinctMessage.isNotBlank() -> distinctMessage
                    else -> ""
                }

                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (tx.isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (tx.isExcluded) TextDecoration.LineThrough else null
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (tx.isExcluded) 0.4f else 0.8f)
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tx.category == BankTransactionType.INTERNAL_TRANSFER && !tx.isExcluded) {
                    Text(
                        text = "NETTED -> Excluded from income & spending",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoodGreen
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = (if (isPositive) "+" else "") + fmtCZK(safeAmount),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.5.sp,
                        color = if (tx.isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else amountColor,
                        textDecoration = if (tx.isExcluded) TextDecoration.LineThrough else null
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                if (onToggleExclude != null) {
                    IconButton(
                        onClick = onToggleExclude,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (tx.isExcluded) Icons.Default.AddCircleOutline else Icons.Default.RemoveCircleOutline,
                            contentDescription = if (tx.isExcluded) "Include transaction" else "Exclude transaction",
                            tint = if (tx.isExcluded) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
