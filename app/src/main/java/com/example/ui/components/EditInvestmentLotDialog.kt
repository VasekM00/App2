package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BrandSky
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.UserInvestmentLot
import com.example.util.parseUserAmountOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditInvestmentLotDialog(
    lot: UserInvestmentLot?, // null means creating a new lot
    onDismiss: () -> Unit,
    onSave: (UserInvestmentLot) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    templateLot: UserInvestmentLot? = null
) {
    val haptic = LocalHapticFeedback.current
    val isNew = lot == null
    val now = LocalDate.now()
    val defaultDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))

    var instrumentText by remember(lot) { mutableStateOf(lot?.instrument ?: "Portu / VWCE All-World") }
    var batchNameText by remember(lot) { mutableStateOf(lot?.batchName ?: "DCA Batch $defaultDate") }
    var purchaseDateText by remember(lot) { mutableStateOf(lot?.purchaseDateStr ?: defaultDate) }
    var acquisitionCostText by remember(lot) { mutableStateOf(lot?.acquisitionCostCzk?.toInt()?.toString() ?: "50000") }
    var currentValueText by remember(lot) { mutableStateOf(lot?.currentValueCzk?.toInt()?.toString() ?: "55000") }
    var notesText by remember(lot) { mutableStateOf(lot?.notes ?: "") }

    val parsedAcq = (acquisitionCostText.parseUserAmountOrNull() ?: 0.0).finiteOrZero().coerceAtLeast(0.0)
    val parsedCur = (currentValueText.parseUserAmountOrNull() ?: 0.0).finiteOrZero().coerceAtLeast(0.0)
    val unrealizedGain = (parsedCur - parsedAcq).finiteOrZero()
    val gainPct = if (parsedAcq > 0.0) ((unrealizedGain / parsedAcq) * 100.0).finiteOrZero() else 0.0

    val previewLot = remember(batchNameText, instrumentText, purchaseDateText, parsedAcq, parsedCur) {
        UserInvestmentLot(
            id = lot?.id ?: "new_preview",
            batchName = batchNameText.ifBlank { "Lot" },
            instrument = instrumentText.ifBlank { "ETF" },
            purchaseDateStr = purchaseDateText.ifBlank { defaultDate },
            acquisitionCostCzk = parsedAcq,
            currentValueCzk = parsedCur
        )
    }

    val monthsHeld = previewLot.monthsHeld()
    val isExempt = previewLot.isExempt()
    val monthsRemaining = previewLot.monthsRemaining()
    val progressRatio = (monthsHeld / 36f).coerceIn(0f, 1f)

    val quickInstruments = listOf("Portu ETF", "VWCE All-World", "S&P 500", "MSCI World")
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = maxDialogHeight)
                .imePadding()
                .testTag("edit_investment_lot_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Shield Icon & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GoodGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = GoodGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isNew) "Add Investment Lot" else "Edit Investment Lot",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "Hold securities for 3 years to sell 100% tax-free",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Instrument Preset Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isNew && templateLot != null) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    instrumentText = templateLot.instrument
                                    batchNameText = "DCA Batch $defaultDate"
                                    purchaseDateText = defaultDate
                                    acquisitionCostText = templateLot.acquisitionCostCzk.toInt().toString()
                                    currentValueText = templateLot.acquisitionCostCzk.toInt().toString()
                                    notesText = templateLot.notes
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = GoodGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("btn_repeat_last_lot")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Repeat last: ${templateLot.instrument}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = GoodGreen
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = fmtCZK(templateLot.acquisitionCostCzk),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = BodyFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = GoodGreen
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = "QUICK INSTRUMENT SUGGESTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 10.sp,
                                color = BrandTeal
                            )
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickInstruments.forEach { preset ->
                                val isSelected = instrumentText.contains(preset.substringBefore(" "))
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        instrumentText = preset
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BrandTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandTeal.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp,
                                                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 2,
                                            softWrap = true,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = instrumentText,
                        onValueChange = { instrumentText = it },
                        label = { Text("Instrument / Ticker") },
                        placeholder = { Text("e.g. VWCE, Portu, S&P 500") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lot_instrument_field")
                    )

                    OutlinedTextField(
                        value = batchNameText,
                        onValueChange = { batchNameText = it },
                        label = { Text("Batch / Purchase Label") },
                        placeholder = { Text("e.g. Q1 2024 DCA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lot_batch_field")
                    )

                    // Quick Date Helper Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PURCHASE DATE (YYYY-MM)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 10.sp,
                                color = BrandTeal
                            )
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    purchaseDateText = defaultDate
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "This Month",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val oneYearAgo = now.minusYears(1).format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))
                                    purchaseDateText = oneYearAgo
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "1 Year Ago",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val threeYearsAgo = now.minusYears(3).format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))
                                    purchaseDateText = threeYearsAgo
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = GoodGreen.copy(alpha = 0.12f),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "3 Years Ago (Exempt)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoodGreen
                                        ),
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = purchaseDateText,
                            onValueChange = { purchaseDateText = it },
                            label = { Text("Purchase Date (YYYY-MM)") },
                            placeholder = { Text("e.g. 2023-05") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                            modifier = Modifier.fillMaxWidth().testTag("lot_date_field")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = acquisitionCostText,
                            onValueChange = { acquisitionCostText = it },
                            label = { Text("Cost Basis") },
                            suffix = { Text(" Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                            modifier = Modifier.weight(1f).testTag("lot_acq_field")
                        )

                        OutlinedTextField(
                            value = currentValueText,
                            onValueChange = { currentValueText = it },
                            label = { Text("Current Value") },
                            suffix = { Text(" Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                            modifier = Modifier.weight(1f).testTag("lot_cur_field")
                        )
                    }

                    // Live Status & Holding Progress Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExempt) GoodGreen.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isExempt) GoodGreen.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "3-Year Time-Test Status:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (isExempt) "100% Tax-Exempt (0% Tax)" else "$monthsRemaining months remaining",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = BodyFontFamily,
                                        color = if (isExempt) GoodGreen else AmberWarning
                                    )
                                )
                            }

                            // Visual Progress Bar towards 36 months
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressRatio)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(if (isExempt) GoodGreen else BrandSky)
                                    )
                                }
                                Text(
                                    text = "$monthsHeld / 36 months",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = BodyFontFamily,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Unrealized Capital Gain:",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = "${if (unrealizedGain >= 0) "+" else ""}${fmtCZK(unrealizedGain)} (${String.format(Locale.US, "%.1f", gainPct)}%)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = BodyFontFamily,
                                        fontSize = 11.5.sp,
                                        color = if (unrealizedGain >= 0) GoodGreen else MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("e.g. Account number, broker reference") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lot != null && onDelete != null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDelete(lot.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_delete_lot")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                            Text("Delete", maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", maxLines = 2, softWrap = true)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val toSave = UserInvestmentLot(
                                id = lot?.id ?: java.util.UUID.randomUUID().toString(),
                                batchName = batchNameText.ifBlank { "Lot" },
                                instrument = instrumentText.ifBlank { "ETF" },
                                purchaseDateStr = purchaseDateText.ifBlank { defaultDate },
                                acquisitionCostCzk = parsedAcq,
                                currentValueCzk = parsedCur,
                                notes = notesText
                            )
                            onSave(toSave)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            contentColor = OnBrandTeal
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_save_lot")
                    ) {
                        Text(
                            text = if (isNew) "Add Lot" else "Save Changes",
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
