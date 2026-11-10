package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.parseUserAmountOrNull
import kotlinx.coroutines.delay

object SettingsMetricInfos {
    val swr = MetricInfo(
        title = "Safe Withdrawal Rate (SWR)",
        category = "Actuarial Science",
        formulaOrRule = "Initial Annual Spend / Starting Portfolio Size",
        explanation = "The percentage of your investment portfolio withdrawn in year 1 of retirement, subsequently adjusted annually for CPI inflation. Based on the Trinity Study (Bengen, 1994). A 3.5% SWR historically provides a 99%+ 40-year portfolio survival rate.",
        statutoryReference = "Trinity Study & Modern Portfolio Theory",
        practicalImplication = "Dropping SWR from 4.0% to 3.5% provides extra safety margin against sequence-of-returns risk.",
        accentColor = Color(0xFF0F766E)
    )

    val safetyBuffer = MetricInfo(
        title = "FIRE Safety Capital Buffer",
        category = "Risk Management",
        formulaOrRule = "Target Base Portfolio * (1 + Safety Buffer %)",
        explanation = "An additional discretionary capital cushion added on top of your baseline FIRE nest egg. Shields against healthcare shocks, extended deep recessions, or unforeseen property maintenance.",
        practicalImplication = "A 10% buffer on a 10M CZK target adds 1,000,000 CZK in extra margin of safety.",
        accentColor = Color(0xFFD97706)
    )

    val partnerCredit = MetricInfo(
        title = "Partner Tax Credit",
        category = "Czech Tax Code",
        formulaOrRule = "24,840 CZK/yr deduction if partner's own income <= 68,000 CZK",
        explanation = "Annual tax credit claimed when caring for a child under 3 years old and partner's own annual gross income does not exceed 68,000 CZK. Crucially, state parental allowance (rodičovský příspěvek) is legally excluded from this income ceiling.",
        statutoryReference = "§ 35ba odst. 1 písm. b) Act No. 586/1992 Coll.",
        practicalImplication = "Directly reduces personal income tax liability by up to 24,840 CZK per year (2,070 CZK/month).",
        accentColor = Color(0xFF16A34A)
    )

    val childBonus = MetricInfo(
        title = "Child Tax Credit & Bonus",
        category = "Czech Tax Code",
        formulaOrRule = "1st child: 15,204 CZK/yr · 2nd child: 22,320 CZK/yr · 3rd+: 27,840 CZK/yr",
        explanation = "Progressive tax allowance per dependent child. If your tax liability reaches zero, the unused portion is paid out by the state directly to you as a cash tax bonus (daňový bonus).",
        statutoryReference = "§ 35c Act No. 586/1992 Coll.",
        practicalImplication = "Provides direct cash rebates when tax liability is low.",
        accentColor = Color(0xFF0F766E)
    )

    val monteCarlo = MetricInfo(
        title = "Monte Carlo Volatility Modeling",
        category = "Statistical Simulation",
        formulaOrRule = "Geometric Brownian Motion · 400 Iterations",
        explanation = "Simulates 400 randomized market return paths using historical asset volatility (16% std dev) and expected inflation. Renders 5th percentile (bear worst case), 50th percentile (median), and 95th percentile (bull optimal) wealth trajectories.",
        practicalImplication = "Ensures financial planning accounts for unpredictable market sequences rather than unrealistic linear returns.",
        accentColor = Color(0xFF0F766E)
    )

    val cpiInflation = MetricInfo(
        title = "CPI Inflation Compounding",
        category = "Macroeconomic Model",
        formulaOrRule = "Future Cost = Present Cost * (1 + CPI)^Years",
        explanation = "Annual consumer price inflation rate used to compound future living expenses, child costs, and lifestyle goals.",
        statutoryReference = "Czech Statistical Office (ČSÚ)",
        practicalImplication = "At 2.5% inflation, living costs double approximately every 28 years.",
        accentColor = Color(0xFFDC2626)
    )

    val statePensionAge = MetricInfo(
        title = "Statutory State Pension Age",
        category = "Czech Pension System",
        formulaOrRule = "Act No. 155/1995 Coll. · Statutory retirement age",
        explanation = "The legal retirement age when state pillar 1 pension benefits begin. In the FIRE model, your private investment portfolio bridges living expenses from early retirement until this age.",
        statutoryReference = "Act No. 155/1995 Coll. on Pension Insurance",
        practicalImplication = "Earlier personal retirement requires a larger private bridge fund to cover living costs before state pensions kick in.",
        accentColor = Color(0xFF0F766E)
    )

    val historicalBootstrap = MetricInfo(
        title = "Historical 1970–2025 Bootstrap",
        category = "Statistical Simulation",
        formulaOrRule = "Empirical Resampling · 56-Year Real Market Returns",
        explanation = "Samples real annual total return blocks from 1970 to 2025 (MSCI World / S&P 500 with dividends reinvested). Unlike synthetic Gaussian curves, historical bootstrapping preserves real-world fat tails, severe multi-year drawdowns (1973–74 stagflation, 2000–02 dot-com bust, 2008 GFC), and return autocorrelation.",
        practicalImplication = "Provides a stress-tested reality check against real market history instead of idealized theoretical distributions.",
        accentColor = Color(0xFF0F766E)
    )

    val prolongChildSupport = MetricInfo(
        title = "Prolong Child Support in FIRE",
        category = "Family Budgeting",
        formulaOrRule = "Preserve child expenses in post-FIRE lifestyle target",
        explanation = "When enabled, your target financial nest egg accounts for ongoing child allowances, education costs, and university support instead of assuming child expenses drop to zero immediately upon early retirement.",
        practicalImplication = "Essential if you plan to achieve FIRE while your children are teenagers or attending university.",
        accentColor = Color(0xFFD97706)
    )
}

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
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(externalExpandTrigger) {
        if (externalExpandTrigger > 0 && externalExpandState != null) {
            expanded = externalExpandState
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (info != null && onShowInfo != null) {
                        IconButton(
                            onClick = { onShowInfo(info) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info on $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (badgeText != null) {
                        ColorPill(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 8.dp,
                            verticalPadding = 3.5.dp
                        )
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
    maxValue: Double? = null
) {
    fun formatVal(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        return if (v % 1.0 == 0.0) {
            v.toLong().toString()
        } else {
            val rounded = kotlin.math.round(v * 100.0) / 100.0
            if (rounded % 1.0 == 0.0) {
                rounded.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
            }
        }
    }

    var textValue by remember { mutableStateOf(formatVal(value)) }
    var isFocused by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun commitCurrentText(forceClamp: Boolean = false) {
        if (readOnly) return
        val parsed = textValue.parseUserAmountOrNull()
        if (parsed != null) {
            val isWithinBounds = parsed >= minValue && (maxValue == null || parsed <= maxValue)
            if (isWithinBounds) {
                if (parsed != value) {
                    onValueChange(parsed)
                }
            } else if (forceClamp) {
                val clamped = if (maxValue != null) {
                    parsed.coerceIn(minValue, maxValue)
                } else {
                    parsed.coerceAtLeast(minValue)
                }
                if (clamped != value) {
                    onValueChange(clamped)
                }
                textValue = formatVal(clamped)
            }
        } else if (forceClamp) {
            val fallback = if (textValue.isBlank()) minValue else value
            if (fallback != value) {
                onValueChange(fallback)
            }
            textValue = formatVal(fallback)
        }
    }

    LaunchedEffect(value) {
        if (!isFocused) {
            val currentParsed = textValue.parseUserAmountOrNull()
            if (currentParsed == null || currentParsed != value) {
                textValue = formatVal(value)
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
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onDelete != null) {
                        Modifier.pointerInput(label) {
                            detectTapGestures(
                                onLongPress = {
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    } else Modifier
                )
        )
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
            trailingIcon = if (isFocused && textValue.isNotEmpty() && !readOnly) {
                {
                    IconButton(
                        onClick = {
                            val clearedVal = 0.0.coerceAtLeast(minValue)
                            textValue = if (clearedVal == 0.0) "" else formatVal(clearedVal)
                            onValueChange(clearedVal)
                        },
                        modifier = Modifier.size(24.dp)
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
            modifier = Modifier
                .width(140.dp)
                .onFocusChanged {
                    if (isFocused && !it.isFocused) {
                        commitCurrentText(forceClamp = true)
                    }
                    isFocused = it.isFocused
                }
                .then(if (testTagStr.isNotEmpty()) Modifier.testTag(testTagStr) else Modifier)
        )
    }
}

@Composable
fun TextSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTagStr: String = ""
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    fun commitCurrentText() {
        val trimmed = textValue.trim()
        if (trimmed.isNotEmpty()) {
            onValueChange(trimmed)
        } else {
            textValue = value
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitCurrentText() }),
            singleLine = true,
            modifier = Modifier
                .width(160.dp)
                .onFocusChanged {
                    if (isFocused && !it.isFocused) {
                        commitCurrentText()
                    }
                    isFocused = it.isFocused
                }
                .then(if (testTagStr.isNotEmpty()) Modifier.testTag(testTagStr) else Modifier)
        )
    }
}

@Composable
fun YearMonthSettingField(
    label: String,
    yearValue: Int,
    monthValue: Int,
    onValueChange: (year: Int, month: Int) -> Unit
) {
    var yearText by remember { mutableStateOf(yearValue.toString()) }
    var monthText by remember { mutableStateOf(monthValue.toString()) }
    var isYearFocused by remember { mutableStateOf(false) }
    var isMonthFocused by remember { mutableStateOf(false) }

    LaunchedEffect(yearValue) {
        if (!isYearFocused) {
            yearText = yearValue.toString()
        }
    }
    LaunchedEffect(monthValue) {
        if (!isMonthFocused) {
            monthText = monthValue.toString()
        }
    }

    fun commit(forceValidate: Boolean = false) {
        val y = yearText.trim().toIntOrNull()
        val m = monthText.trim().toIntOrNull()
        if (forceValidate) {
            val validY = (y ?: yearValue).coerceIn(1900, 2100)
            val validM = (m ?: monthValue).coerceIn(1, 12)
            yearText = validY.toString()
            monthText = validM.toString()
            onValueChange(validY, validM)
        } else {
            if (y != null && y in 1900..2100 && m != null && m in 1..12) {
                onValueChange(y, m)
            }
        }
    }

    LaunchedEffect(yearText, monthText) {
        if (isYearFocused || isMonthFocused) {
            delay(500)
            commit(forceValidate = false)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = monthText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(2)
                    monthText = filtered
                },
                placeholder = { Text("M", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { commit(forceValidate = true) }),
                singleLine = true,
                modifier = Modifier
                    .width(56.dp)
                    .onFocusChanged {
                        if (isMonthFocused && !it.isFocused) commit(forceValidate = true)
                        isMonthFocused = it.isFocused
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(4)
                    yearText = filtered
                },
                placeholder = { Text("YYYY", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(forceValidate = true) }),
                singleLine = true,
                modifier = Modifier
                    .width(78.dp)
                    .onFocusChanged {
                        if (isYearFocused && !it.isFocused) commit(forceValidate = true)
                        isYearFocused = it.isFocused
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
            )
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

@Composable
fun TaxSummaryRow(
    label: String,
    status: String,
    isGood: Boolean,
    pillColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ColorPill(
            text = status,
            color = pillColor ?: if (isGood) GoodGreen else BadRed,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            horizontalPadding = 7.dp,
            verticalPadding = 2.5.dp
        )
    }
}

fun liveDataSubtitle(data: com.example.domain.CzechRegulatoryData?): String {
    return if (data != null) {
        "ČSÚ CPI: ${data.csuCpiInflationPct}% · EUR: ${String.format(java.util.Locale.getDefault(), "%.2f", data.eurCzkRate)} · USD: ${String.format(java.util.Locale.getDefault(), "%.2f", data.usdCzkRate)}"
    } else {
        "Check live ČSÚ CPI, ČNB rates, and statutory tax credits"
    }
}
