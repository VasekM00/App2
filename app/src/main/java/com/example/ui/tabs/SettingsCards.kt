package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.util.Formatters.fmtEditableAmount
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandTeal
import com.example.util.parseUserAmountOrNull
import kotlinx.coroutines.delay

@Composable
fun SettingsGroupCard(
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    collapsible: Boolean = true,
    badgeText: String? = null,
    badgeColor: Color = BrandTeal,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    externalExpandTrigger: Int = 0,
    externalExpandState: Boolean? = null,
    sectionKey: String? = null,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    var lastAppliedTrigger by rememberSaveable(title) { mutableIntStateOf(0) }
    var lastFocusTrigger by rememberSaveable(title) { mutableIntStateOf(0) }
    LaunchedEffect(externalExpandTrigger) {
        if (externalExpandTrigger == 0) {
            lastAppliedTrigger = 0
        } else if (externalExpandTrigger > lastAppliedTrigger && externalExpandState != null) {
            expanded = externalExpandState
            lastAppliedTrigger = externalExpandTrigger
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val stackBadgeBelow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 385 &&
                androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (stackBadgeBelow && badgeText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ColorPill(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 8.dp,
                            verticalPadding = 3.dp
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!stackBadgeBelow && badgeText != null) {
                        ColorPill(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 8.dp,
                            verticalPadding = 3.5.dp
                        )
                    }
                    if (info != null && onShowInfo != null) {
                        IconButton(
                            onClick = { onShowInfo(info) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info on $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (collapsible) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse section" else "Expand section",
                                tint = BrandTeal
                            )
                        }
                    }
                }
            }
            if (!collapsible || expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun NumberSettingField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    testTagStr: String = "",
    onDelete: (() -> Unit)? = null,
    readOnly: Boolean = false,
    minValue: Double = 0.0,
    maxValue: Double? = null,
    fieldKey: String = "",
    quickStep: Double? = null
) {
    val stateKey = if (fieldKey.isNotEmpty()) "${fieldKey}_$label" else label
    var textValue by remember(stateKey) { mutableStateOf(fmtEditableAmount(value)) }
    var isFocused by remember(stateKey) { mutableStateOf(false) }
    var showDeleteConfirm by remember(stateKey) { mutableStateOf(false) }

    fun commitCurrentText(forceClamp: Boolean = false) {
        if (readOnly) return
        val parsed = textValue.parseUserAmountOrNull()
        val safeMin = if (maxValue != null) minOf(minValue, maxValue) else minValue
        val safeMax = if (maxValue != null) maxOf(minValue, maxValue) else null
        if (parsed != null) {
            val isWithinBounds = parsed >= safeMin && (safeMax == null || parsed <= safeMax)
            if (isWithinBounds) {
                if (parsed != value) {
                    onValueChange(parsed)
                }
            } else if (forceClamp) {
                val clamped = if (safeMax != null) {
                    parsed.coerceIn(safeMin, safeMax)
                } else {
                    parsed.coerceAtLeast(safeMin)
                }
                if (clamped != value) {
                    onValueChange(clamped)
                }
                textValue = fmtEditableAmount(clamped)
            }
        } else if (forceClamp) {
            val fallback = if (textValue.isBlank()) safeMin else value
            if (fallback != value) {
                onValueChange(fallback)
            }
            textValue = fmtEditableAmount(fallback)
        }
    }

    val latestCommit by rememberUpdatedState(newValue = {
        if (isFocused) {
            commitCurrentText(forceClamp = true)
        }
    })
    DisposableEffect(stateKey) {
        onDispose {
            latestCommit()
        }
    }

    LaunchedEffect(value) {
        if (!isFocused) {
            val currentParsed = textValue.parseUserAmountOrNull()
            if (currentParsed == null || currentParsed != value) {
                textValue = fmtEditableAmount(value)
            }
        }
    }

    LaunchedEffect(textValue) {
        if (isFocused) {
            delay(500)
            commitCurrentText(forceClamp = false)
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete \"$label\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val hasQuickStep = quickStep != null && !readOnly
    val haptic = LocalHapticFeedback.current

    fun applyQuickStep(delta: Double) {
        val base = textValue.parseUserAmountOrNull() ?: value
        val rounded = kotlin.math.round((base + delta) * 100.0) / 100.0
        val safeMin = if (maxValue != null) minOf(minValue, maxValue) else minValue
        val safeMax = if (maxValue != null) maxOf(minValue, maxValue) else null
        val clamped = if (safeMax != null) {
            rounded.coerceIn(safeMin, safeMax)
        } else {
            rounded.coerceAtLeast(safeMin)
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        textValue = fmtEditableAmount(clamped)
        if (clamped != value) {
            onValueChange(clamped)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val shouldStack = maxWidth < 300.dp || fontScale >= 1.4f
        val fieldWidth = when {
            hasQuickStep && maxWidth < 280.dp -> 82.dp
            hasQuickStep -> 96.dp
            maxWidth < 280.dp -> 122.dp
            else -> 142.dp
        }
        val stepVal = quickStep
        val currentVal = textValue.parseUserAmountOrNull() ?: value

        val labelBlock: @Composable (Modifier) -> Unit = { rowMod ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowMod
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (onDelete != null) {
                                Modifier.pointerInput(stateKey) {
                                    detectTapGestures(
                                        onLongPress = {
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            } else Modifier
                        )
                )
                if (onDelete != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        val inputBlock: @Composable (Boolean) -> Unit = { expandInput ->
            Row(
                modifier = if (expandInput) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (stepVal != null && !readOnly) {
                    val canDecrease = currentVal > minValue + 1e-6
                    QuickStepButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Decrease $label",
                        enabled = canDecrease,
                        onClick = { applyQuickStep(-stepVal) }
                    )
                }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (!readOnly) {
                            textValue = input
                        }
                    },
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitCurrentText(forceClamp = true) }),
                    trailingIcon = if (!hasQuickStep && isFocused && textValue.isNotEmpty() && !readOnly) {
                        {
                            IconButton(
                                onClick = {
                                    val clearedVal = 0.0.coerceAtLeast(minValue)
                                    textValue = if (clearedVal == 0.0) "" else fmtEditableAmount(clearedVal)
                                    onValueChange(clearedVal)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily),
                    modifier = (if (expandInput) Modifier.weight(1f) else Modifier.width(fieldWidth))
                        .onFocusChanged {
                            if (isFocused && !it.isFocused) {
                                commitCurrentText(forceClamp = true)
                            }
                            isFocused = it.isFocused
                        }
                        .then(if (testTagStr.isNotEmpty()) Modifier.testTag(testTagStr) else Modifier)
                )
                if (stepVal != null && !readOnly) {
                    val canIncrease = maxValue == null || currentVal < maxValue - 1e-6
                    QuickStepButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Increase $label",
                        enabled = canIncrease,
                        onClick = { applyQuickStep(stepVal) }
                    )
                }
            }
        }

        if (shouldStack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                labelBlock(Modifier.fillMaxWidth())
                inputBlock(true)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                labelBlock(Modifier.weight(1f).padding(end = 8.dp))
                inputBlock(false)
            }
        }
    }
}

@Composable
private fun QuickStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
        modifier = Modifier.size(width = 36.dp, height = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }
}

@Composable
fun TextSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTagStr: String = "",
    fieldKey: String = ""
) {
    val stateKey = if (fieldKey.isNotEmpty()) "${fieldKey}_$label" else label
    var textValue by remember(stateKey) { mutableStateOf(value) }
    var isFocused by remember(stateKey) { mutableStateOf(false) }

    fun commitCurrentText(restoreBlank: Boolean = true) {
        val trimmed = textValue.trim()
        if (trimmed.isNotEmpty()) {
            if (trimmed != value) {
                onValueChange(trimmed)
            }
        } else if (restoreBlank) {
            textValue = value
        }
    }

    val latestCommit by rememberUpdatedState(newValue = {
        if (isFocused) {
            commitCurrentText(restoreBlank = true)
        }
    })
    DisposableEffect(stateKey) {
        onDispose {
            latestCommit()
        }
    }

    LaunchedEffect(value) {
        if (!isFocused && textValue != value) {
            textValue = value
        }
    }

    LaunchedEffect(textValue) {
        if (isFocused) {
            delay(400)
            commitCurrentText(restoreBlank = false)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val fieldWidth = if (maxWidth < 280.dp) 132.dp else 156.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitCurrentText(restoreBlank = true) }),
                singleLine = true,
                modifier = Modifier
                    .width(fieldWidth)
                    .onFocusChanged {
                        if (isFocused && !it.isFocused) {
                            commitCurrentText(restoreBlank = true)
                        }
                        isFocused = it.isFocused
                    }
                    .then(if (testTagStr.isNotEmpty()) Modifier.testTag(testTagStr) else Modifier)
            )
        }
    }
}

@Composable
fun YearMonthSettingField(
    label: String,
    yearValue: Int,
    monthValue: Int,
    onValueChange: (year: Int, month: Int) -> Unit,
    fieldKey: String = ""
) {
    val stateKey = if (fieldKey.isNotEmpty()) "${fieldKey}_$label" else label
    var yearText by remember(stateKey) { mutableStateOf(yearValue.toString()) }
    var monthText by remember(stateKey) { mutableStateOf(monthValue.toString()) }
    var isYearFocused by remember(stateKey) { mutableStateOf(false) }
    var isMonthFocused by remember(stateKey) { mutableStateOf(false) }

    LaunchedEffect(yearValue) { if (!isYearFocused) yearText = yearValue.toString() }
    LaunchedEffect(monthValue) { if (!isMonthFocused) monthText = monthValue.toString() }

    fun commit(forceValidate: Boolean = false) {
        val y = yearText.trim().toIntOrNull()
        val m = monthText.trim().toIntOrNull()
        if (forceValidate) {
            val validY = (y ?: yearValue).coerceIn(1900, 2100)
            val validM = (m ?: monthValue).coerceIn(1, 12)
            yearText = validY.toString()
            monthText = validM.toString()
            if (validY != yearValue || validM != monthValue) onValueChange(validY, validM)
        } else if (y != null && y in 1900..2100 && m != null && m in 1..12 && (y != yearValue || m != monthValue)) {
            onValueChange(y, m)
        }
    }

    val latestCommit by rememberUpdatedState(newValue = {
        if (isYearFocused || isMonthFocused) commit(forceValidate = true)
    })
    DisposableEffect(stateKey) { onDispose { latestCommit() } }
    LaunchedEffect(yearText, monthText) {
        if (isYearFocused || isMonthFocused) {
            delay(500)
            commit(forceValidate = false)
        }
    }

    val centeredStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BodyFontFamily, textAlign = TextAlign.Center)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val isNarrow = maxWidth < 280.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedTextField(
                    value = monthText,
                    onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                    placeholder = { Text("M", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { commit(forceValidate = true) }),
                    singleLine = true,
                    textStyle = centeredStyle,
                    modifier = Modifier.width(if (isNarrow) 56.dp else 62.dp).onFocusChanged {
                        if (isMonthFocused && !it.isFocused) commit(forceValidate = true)
                        isMonthFocused = it.isFocused
                    }
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                    placeholder = { Text("YYYY", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit(forceValidate = true) }),
                    singleLine = true,
                    textStyle = centeredStyle,
                    modifier = Modifier.width(if (isNarrow) 78.dp else 86.dp).onFocusChanged {
                        if (isYearFocused && !it.isFocused) commit(forceValidate = true)
                        isYearFocused = it.isFocused
                    }
                )
            }
        }
    }
}

@Composable
fun BooleanSettingField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
                .then(
                    if (info != null && onShowInfo != null) {
                        Modifier.infoTapHold(info, onShowInfo)
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (info != null && onShowInfo != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(checkedThumbColor = BrandTeal, checkedTrackColor = BrandTeal.copy(alpha = 0.5f))
        )
    }
}
