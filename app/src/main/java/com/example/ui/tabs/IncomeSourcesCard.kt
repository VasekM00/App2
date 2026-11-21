package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK

@Composable
internal fun EditableIncomeRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    badgeText: String? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) = EditableCashFlowRow(
    label = label,
    value = value,
    info = info,
    badgeText = badgeText,
    badgeColor = GoodGreen,
    onShowInfo = onShowInfo,
    onEdit = onEdit
)

@Composable
internal fun IncomeRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color? = null,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
            .padding(vertical = if (isBold) 1.5.dp else 1.dp)
    } else Modifier.padding(vertical = if (isBold) 1.5.dp else 1.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
            if (info != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info on $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall.copy(
                fontFamily = BodyFontFamily,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodyMedium.copy(
                fontFamily = BodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
internal fun IncomeSourcesCard(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    onUpdateSettings: ((SettingsEntity) -> Unit)? = null,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null,
    allImportedTransactions: List<com.example.data.ImportedBankTransactionEntity> = emptyList()
) {
    val scrollState = rememberScrollState()
    val inc = state.currentIncome
    val s = state.settings

    var activeEditKey by rememberSaveable { mutableStateOf<String?>(null) }

    val primaryName = s.primaryName.ifBlank { "Primary Earner" }
    val spouseName = s.spouseName.ifBlank { "Partner" }

    val nowPrague = remember { java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")) }
    val activeYear = nowPrague.year
    val activeMonth = nowPrague.monthValue
    val currentYm = nowPrague.toString()
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
    val scheduledLumpSums = remember(s.customLumpSumsJson, activeYear, activeMonth) {
        FinancialEngine.lumpSumsForMonth(activeYear, activeMonth, s)
    }
    val scheduledLumpSumTotal = scheduledLumpSums.sumOf { it.amount }
    val effectiveScheduledLumpTotal = minOf(inc.lumpSumMonthly, scheduledLumpSumTotal)
    val unforeseenLump = (inc.lumpSumMonthly - scheduledLumpSumTotal).coerceAtLeast(0.0)

    val canEdit = onUpdateSettings != null || onReduceSettings != null
    val patchSettings: ((SettingsEntity) -> SettingsEntity) -> Unit = { transform ->
        com.example.ui.state.dispatchSettingsPatch(s, onUpdateSettings ?: {}, onReduceSettings, transform)
    }
    val activeDialogData: EditCashFlowFieldData? = if (canEdit) {
        when (activeEditKey) {
            "vSalary" -> EditCashFlowFieldData(
                key = "vSalary",
                title = "Edit $primaryName's Net Salary",
                subtitle = "Monthly Household Cash Flow",
                description = "Update baseline monthly net take-home salary for $primaryName. Recalculates projections, savings rate, and cash flows across the app.",
                accentColor = GoodGreen,
                currentValue = s.vSalary,
                onConfirm = { amount -> patchSettings { it.copy(vSalary = amount) } }
            )
            "vOther" -> EditCashFlowFieldData(
                key = "vOther",
                title = "Edit $primaryName's Other Inflows",
                subtitle = "Monthly Household Cash Flow",
                description = "Update monthly side-hustle, freelance, or other recurring personal inflows for $primaryName.",
                accentColor = GoodGreen,
                currentValue = s.vOtherInflowsMonthly,
                onConfirm = { amount -> patchSettings { it.copy(vOtherInflowsMonthly = amount) } }
            )
            "vGifts" -> EditCashFlowFieldData(
                key = "vGifts",
                title = "Edit $primaryName's Gifts",
                subtitle = "Monthly Household Cash Flow",
                description = "Update monthly tax-exempt family gifts received by $primaryName. Completely tax-free and exempt from income taxes.",
                accentColor = GoodGreen,
                currentValue = s.vGiftsMonthly,
                onConfirm = { amount -> patchSettings { it.copy(vGiftsMonthly = amount) } }
            )
            "eSalary" -> EditCashFlowFieldData(
                key = "eSalary",
                title = "Edit $spouseName's Net Salary",
                subtitle = if (s.baseYear < s.eReturnYear) "Activates in ${s.eReturnYear} upon return to work" else "Monthly Household Cash Flow",
                description = if (s.baseYear < s.eReturnYear) {
                    "Update future starting monthly net salary for $spouseName. Applies starting in ${s.eReturnYear} (currently on parental leave)."
                } else {
                    "Update baseline monthly net take-home salary for $spouseName."
                },
                accentColor = GoodGreen,
                currentValue = s.eStartingSalary,
                onConfirm = { amount -> patchSettings { it.copy(eStartingSalary = amount) } }
            )
            "eParental" -> EditCashFlowFieldData(
                key = "eParental",
                title = "Edit $spouseName's Parental Allowance",
                subtitle = "Monthly Household Cash Flow",
                description = "Update state parental allowance. Tax-exempt and excluded from the 68,000 Kč partner income test.",
                accentColor = GoodGreen,
                currentValue = s.eParentalAllowanceMonthly,
                onConfirm = { amount -> patchSettings { it.copy(eParentalAllowanceMonthly = amount) } }
            )
            "eLecturing" -> EditCashFlowFieldData(
                key = "eLecturing",
                title = "Edit $spouseName's Lecturing Income",
                subtitle = "Monthly Household Cash Flow",
                description = "Update monthly part-time academic lecturing income.",
                accentColor = GoodGreen,
                currentValue = if (s.eIncludeLecturing) s.eLecturingMonthly else 0.0,
                onConfirm = { amount ->
                    patchSettings {
                        it.copy(
                            eLecturingMonthly = amount,
                            eIncludeLecturing = amount > 0.0
                        )
                    }
                }
            )
            "eOther" -> EditCashFlowFieldData(
                key = "eOther",
                title = "Edit $spouseName's Other Inflows",
                subtitle = "Monthly Household Cash Flow",
                description = "Update monthly side-hustle or other recurring inflows for $spouseName.",
                accentColor = GoodGreen,
                currentValue = s.eOtherInflowsMonthly,
                onConfirm = { amount -> patchSettings { it.copy(eOtherInflowsMonthly = amount) } }
            )
            "eGifts" -> EditCashFlowFieldData(
                key = "eGifts",
                title = "Edit $spouseName's Gifts",
                subtitle = "Monthly Household Cash Flow",
                description = "Update monthly tax-exempt family gifts received by $spouseName. 100% tax-free and excluded from partner income ceiling.",
                accentColor = GoodGreen,
                currentValue = s.eGiftsMonthly,
                onConfirm = { amount -> patchSettings { it.copy(eGiftsMonthly = amount) } }
            )
            "vMealVouchers" -> EditCashFlowFieldData(
                key = "vMealVouchers",
                title = "Edit Meal Vouchers ($primaryName)",
                subtitle = "Monthly Household Cash Flow",
                description = "Update tax-exempt monetary meal voucher cash allowance.",
                accentColor = GoodGreen,
                currentValue = s.vMealVouchersMonthly,
                onConfirm = { amount -> patchSettings { it.copy(vMealVouchersMonthly = amount) } }
            )
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
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                CardHeaderPill(
                    title = "Monthly Income",
                    subtitle = "Tap any row to edit amount",
                    badgeText = null,
                    accentColor = GoodGreen
                )

                if (activeLedger != null && (activeLedger.incVaclav > 0.0 || activeLedger.incEleonora > 0.0 || activeLedger.incUnforeseen > 0.0)) {
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

                Spacer(modifier = Modifier.height(6.dp))

                // Section 1: Primary Earner
                Text(
                    text = "${primaryName.uppercase(java.util.Locale.ROOT)}'S INFLOWS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                EditableIncomeRow(
                    label = "$primaryName's Net Salary",
                    value = fmtCZK(inc.vaclavNet),
                    info = CashFlowMetricInfos.vaclavSalary,
                    onShowInfo = onShowInfo,
                    onEdit = if (canEdit) { { activeEditKey = "vSalary" } } else null
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                EditableIncomeRow(
                    label = "$primaryName's Other Inflows",
                    value = fmtCZK(inc.vaclavOther),
                    onEdit = if (canEdit) { { activeEditKey = "vOther" } } else null
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                EditableIncomeRow(
                    label = "$primaryName's Gifts",
                    value = fmtCZK(inc.vaclavGifts),
                    badgeText = "EXEMPT",
                    info = CashFlowMetricInfos.vaclavGifts,
                    onShowInfo = onShowInfo,
                    onEdit = if (canEdit) { { activeEditKey = "vGifts" } } else null
                )

                if (!s.isSingleHousehold) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(5.dp))

                    // Section 2: Partner
                    Text(
                        text = "${spouseName.uppercase(java.util.Locale.ROOT)}'S INFLOWS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = BrandGold
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))

                    EditableIncomeRow(
                        label = if (s.baseYear < s.eReturnYear) "$spouseName's Net Salary (from ${s.eReturnYear})" else "$spouseName's Net Salary",
                        value = fmtCZK(inc.eleonoraSalary),
                        info = CashFlowMetricInfos.eleonoraSalary,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "eSalary" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    EditableIncomeRow(
                        label = "$spouseName's Parental Allowance",
                        value = fmtCZK(inc.benefit),
                        badgeText = "EXEMPT",
                        info = CashFlowMetricInfos.parentalBenefit,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "eParental" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    EditableIncomeRow(
                        label = "$spouseName's Lecturing",
                        value = fmtCZK(inc.lecturing),
                        info = CashFlowMetricInfos.lecturing,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "eLecturing" } } else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    EditableIncomeRow(
                        label = "$spouseName's Other Inflows",
                        value = fmtCZK(inc.eleonoraOther),
                        onEdit = if (canEdit) { { activeEditKey = "eOther" } } else null
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    EditableIncomeRow(
                        label = "$spouseName's Gifts",
                        value = fmtCZK(inc.eleonoraGifts),
                        badgeText = "EXEMPT",
                        info = CashFlowMetricInfos.eleonoraGifts,
                        onShowInfo = onShowInfo,
                        onEdit = if (canEdit) { { activeEditKey = "eGifts" } } else null
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(5.dp))

                // Section 3: Benefits & Bonuses
                Text(
                    text = "BENEFITS & BONUSES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = BrandTeal
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))

                EditableIncomeRow(
                    label = "Meal Vouchers ($primaryName)",
                    value = fmtCZK(inc.vouchers),
                    badgeText = "EXEMPT",
                    info = CashFlowMetricInfos.mealVouchers,
                    onShowInfo = onShowInfo,
                    onEdit = if (canEdit) { { activeEditKey = "vMealVouchers" } } else null
                )

                if (scheduledLumpSums.isNotEmpty() && effectiveScheduledLumpTotal > 0.0) {
                    for (item in scheduledLumpSums) {
                        val displayAmt = if (scheduledLumpSumTotal > 0.0) {
                            item.amount * (effectiveScheduledLumpTotal / scheduledLumpSumTotal)
                        } else item.amount
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        IncomeRow(
                            label = "${item.name} (${activeMonth}/$activeYear Bonus)",
                            value = fmtCZK(displayAmt),
                            isBold = false
                        )
                    }
                }

                if (unforeseenLump > 0.0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    IncomeRow(
                        label = "Unforeseen Inflow (${activeMonth}/$activeYear Ledger)",
                        value = fmtCZK(unforeseenLump),
                        isBold = false
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 1.5.dp,
                    color = BrandTeal
                )

                IncomeRow(
                    label = "Total Monthly Income",
                    value = fmtCZK(inc.totalMonthly),
                    isBold = true,
                    info = CashFlowMetricInfos.totalIncome,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                val surplus = inc.totalMonthly - state.totalLivingCostMonthly
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
