package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.CustomLumpSumItem
import com.example.domain.FullCalculationState
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLumpSums
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeCustomLumpSums
import com.example.domain.serializeDeletedCategories
import com.example.ui.components.MetricInfo
import com.example.ui.state.dispatchSettingsPatch
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

fun LazyListScope.settingsHouseholdSubTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    expandTrigger: Int,
    expandTargetState: Boolean?,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0,
    onShowAddCategory: () -> Unit,
    onShowAddLumpSum: (CustomLumpSumItem?) -> Unit,
    onShowInfo: (MetricInfo) -> Unit,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val s = state.settings
    fun patchSettings(transform: (SettingsEntity) -> SettingsEntity) {
        dispatchSettingsPatch(s, onUpdateSettings, onReduceSettings, transform)
    }
    val pName = s.primaryName.ifBlank { "Primary Earner" }
    val sName = s.spouseName.ifBlank { "Partner" }

    // 1. Earned & Side Incomes
    item {
        val eCurrentMonthly = state.currentIncome.eleonoraSalary +
            state.currentIncome.benefit +
            state.currentIncome.lecturing +
            state.currentIncome.eleonoraOther +
            s.eGiftsMonthly
        SettingsGroupCard(
            title = "Earned & Side Incomes",
            subtitle = if (s.isSingleHousehold) {
                "$pName ${fmtCZK(s.vSalary)} · +${fmtPct(s.vSalaryGrowthPct)}"
            } else {
                "$pName ${fmtCZK(s.vSalary)} · $sName ${fmtCZK(eCurrentMonthly)}"
            },
            initiallyExpanded = false,
            badgeText = fmtCZK(state.currentIncome.totalMonthly) + " monthly",
            badgeColor = GoodGreen,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "income",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            BooleanSettingField(
                label = "Single Earner Household",
                checked = s.isSingleHousehold,
                onCheckedChange = { v -> patchSettings { it.copy(isSingleHousehold = v) } }
            )
            TextSettingField(
                label = "Primary Earner Name",
                value = s.primaryName,
                onValueChange = { v -> patchSettings { it.copy(primaryName = v) } },
                fieldKey = "primary_profile"
            )
            NumberSettingField(
                label = "$pName's Current Age",
                value = s.primaryAge.toDouble(),
                minValue = 1.0,
                maxValue = 120.0,
                onValueChange = { v -> patchSettings { it.copy(primaryAge = v.toInt()) } },
                fieldKey = "primary_age"
            )
            if (!s.isSingleHousehold) {
                TextSettingField(
                    label = "Partner Name",
                    value = s.spouseName,
                    onValueChange = { v -> patchSettings { it.copy(spouseName = v) } },
                    fieldKey = "spouse_profile"
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "$pName's Incomes",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
            )
            NumberSettingField(label = "Net Salary", value = s.vSalary, onValueChange = { v -> patchSettings { it.copy(vSalary = v) } }, fieldKey = "v_income")
            NumberSettingField(label = "Annual Salary Growth (%)", value = s.vSalaryGrowthPct, onValueChange = { v -> patchSettings { it.copy(vSalaryGrowthPct = v) } }, fieldKey = "v_income")
            NumberSettingField(label = "Meal Vouchers Monthly", value = s.vMealVouchersMonthly, onValueChange = { v -> patchSettings { it.copy(vMealVouchersMonthly = v) } }, fieldKey = "v_income")
            NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.vOtherInflowsMonthly, onValueChange = { v -> patchSettings { it.copy(vOtherInflowsMonthly = v) } }, fieldKey = "v_income")
            NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.vGiftsMonthly, onValueChange = { v -> patchSettings { it.copy(vGiftsMonthly = v) } }, fieldKey = "v_income")

            if (!s.isSingleHousehold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                val monthNames = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val returnMonthLabel = "${monthNames.getOrElse(s.eReturnMonth.coerceIn(1, 12) - 1) { "M${s.eReturnMonth}" }} ${s.eReturnYear}"
                val isAlreadyEmployed = s.baseYear > s.eReturnYear || (s.baseYear == s.eReturnYear && s.eReturnMonth <= 1)

                if (isAlreadyEmployed) {
                    Text(
                        text = "$sName's Incomes (Employed)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                    )
                    NumberSettingField(label = "Net Salary", value = s.eStartingSalary, onValueChange = { v -> patchSettings { it.copy(eStartingSalary = v) } }, fieldKey = "e_income")
                    NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { v -> patchSettings { it.copy(eOtherInflowsMonthly = v) } }, fieldKey = "e_income")
                    NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.eGiftsMonthly, onValueChange = { v -> patchSettings { it.copy(eGiftsMonthly = v) } }, fieldKey = "e_income")
                    NumberSettingField(label = "Annual Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { v -> patchSettings { it.copy(eSalaryGrowthPct = v) } }, fieldKey = "e_income")
                    NumberSettingField(label = "Reinvested Share of Salary (%)", value = s.eReinvestedPct, onValueChange = { v -> patchSettings { it.copy(eReinvestedPct = v) } }, fieldKey = "e_income")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    YearMonthSettingField(
                        label = "Planned Return",
                        yearValue = s.eReturnYear,
                        monthValue = s.eReturnMonth,
                        onValueChange = { yr, mo -> patchSettings { it.copy(eReturnYear = yr, eReturnMonth = mo) } },
                        fieldKey = "e_return_employed"
                    )
                } else {
                    Text(
                        text = "$sName's Incomes (Parental Leave until $returnMonthLabel)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                    )
                    NumberSettingField(label = "Parental Allowance Monthly", value = s.eParentalAllowanceMonthly, onValueChange = { v -> patchSettings { it.copy(eParentalAllowanceMonthly = v) } }, fieldKey = "e_parental")
                    BooleanSettingField(label = "Include Lecturing Income", checked = s.eIncludeLecturing, onCheckedChange = { v -> patchSettings { it.copy(eIncludeLecturing = v) } })
                    if (s.eIncludeLecturing) {
                        NumberSettingField(label = "Lecturing Monthly", value = s.eLecturingMonthly, onValueChange = { v -> patchSettings { it.copy(eLecturingMonthly = v) } }, fieldKey = "e_parental")
                    }
                    NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { v -> patchSettings { it.copy(eOtherInflowsMonthly = v) } }, fieldKey = "e_parental")
                    NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.eGiftsMonthly, onValueChange = { v -> patchSettings { it.copy(eGiftsMonthly = v) } }, fieldKey = "e_parental")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "$sName's Future Return to Work ($returnMonthLabel+)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    YearMonthSettingField(
                        label = "Planned Return",
                        yearValue = s.eReturnYear,
                        monthValue = s.eReturnMonth,
                        onValueChange = { yr, mo -> patchSettings { it.copy(eReturnYear = yr, eReturnMonth = mo) } },
                        fieldKey = "e_return_parental"
                    )
                    NumberSettingField(label = "Future Starting Salary Net", value = s.eStartingSalary, onValueChange = { v -> patchSettings { it.copy(eStartingSalary = v) } }, fieldKey = "e_future")
                    NumberSettingField(label = "Future Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { v -> patchSettings { it.copy(eSalaryGrowthPct = v) } }, fieldKey = "e_future")
                    NumberSettingField(label = "Future Reinvested Share (%)", value = s.eReinvestedPct, onValueChange = { v -> patchSettings { it.copy(eReinvestedPct = v) } }, fieldKey = "e_future")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "Planned Lump Sum Inflows",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
            )
            Spacer(modifier = Modifier.height(4.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Primary Planned Lump Sum",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            BooleanSettingField(label = "Include Primary Lump Sum", checked = s.lumpSumInclude, onCheckedChange = { v -> patchSettings { it.copy(lumpSumInclude = v) } })
            if (s.lumpSumInclude) {
                NumberSettingField(label = "Planned Year", value = s.lumpSumYear.toDouble(), onValueChange = { v -> patchSettings { it.copy(lumpSumYear = v.toInt()) } })
                NumberSettingField(label = "Planned Amount", value = s.lumpSumAmount, onValueChange = { v -> patchSettings { it.copy(lumpSumAmount = v) } })
            }

            val customLumpSums = remember(s.customLumpSumsJson) { parseCustomLumpSums(s.customLumpSumsJson) }
            if (customLumpSums.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Additional Lump Sum Inflows",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                customLumpSums.forEach { item ->
                    key(item.id) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                    val dateLabel = item.month?.let { m ->
                                        "${monthNames.getOrElse(m - 1) { "M$m" }} ${item.year}"
                                    } ?: "${item.year}"
                                    Text(
                                        text = "${item.name} ($dateLabel)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = fmtCZK(item.amount),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = BodyFontFamily,
                                            color = GoodGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = item.enabled,
                                        onCheckedChange = { enabledState ->
                                            patchSettings { cur ->
                                                val updated = parseCustomLumpSums(cur.customLumpSumsJson).map {
                                                    if (it.id == item.id) it.copy(enabled = enabledState) else it
                                                }
                                                cur.copy(customLumpSumsJson = serializeCustomLumpSums(updated))
                                            }
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    IconButton(
                                        onClick = { onShowAddLumpSum(item) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit lump sum",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            patchSettings { cur ->
                                                val updated = parseCustomLumpSums(cur.customLumpSumsJson).filter { it.id != item.id }
                                                cur.copy(customLumpSumsJson = serializeCustomLumpSums(updated))
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete lump sum",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { onShowAddLumpSum(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Additional Lump Sum")
            }
        }
    }

    // 2. Monthly Living Expenses
    item {
        val customCategories = remember(s.customExpensesJson) { parseCustomExpenses(s.customExpensesJson) }
        val deletedSet = remember(s.deletedCategoriesJson) { parseDeletedCategories(s.deletedCategoriesJson) }

        fun deleteBuiltInKey(key: String, currentSettings: SettingsEntity): SettingsEntity {
            val newSet = parseDeletedCategories(currentSettings.deletedCategoriesJson) + key
            return currentSettings.copy(deletedCategoriesJson = serializeDeletedCategories(newSet))
        }

        SettingsGroupCard(
            title = "Living Expenses",
            subtitle = "Rent ${fmtCompact(s.rentMonthly)} · Food ${fmtCompact(s.groceriesMonthly)}",
            initiallyExpanded = false,
            badgeText = fmtCZK(state.snapshot.monthlyLivingCosts) + " monthly",
            badgeColor = BadRed,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "expenses",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            if (!deletedSet.contains("rent")) {
                NumberSettingField(label = "Rent / Housing", value = s.rentMonthly, onValueChange = { v -> patchSettings { it.copy(rentMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("rent", it.copy(rentMonthly = 0.0)) } })
                NumberSettingField(label = "Annual Rent Growth (%)", value = s.rentGrowthPct, minValue = -99.0, maxValue = 1000.0, onValueChange = { v -> patchSettings { it.copy(rentGrowthPct = v) } }, fieldKey = "rent_growth", quickStep = 0.5)
                Text(
                    text = "Indexed annually each July (Rent growth: ${fmtPct(s.rentGrowthPct, 1)} · CPI: ${fmtPct(s.cpiInflationPct, 1)})",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            if (!deletedSet.contains("groceries")) {
                NumberSettingField(label = "Groceries & Daily Living", value = s.groceriesMonthly, onValueChange = { v -> patchSettings { it.copy(groceriesMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("groceries", it.copy(groceriesMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("cafes")) {
                NumberSettingField(label = "Cafes & Restaurants", value = s.cafesMonthly, onValueChange = { v -> patchSettings { it.copy(cafesMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("cafes", it.copy(cafesMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("therapy")) {
                NumberSettingField(label = "Therapy / Physio", value = s.therapyMonthly, onValueChange = { v -> patchSettings { it.copy(therapyMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("therapy", it.copy(therapyMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("charity")) {
                NumberSettingField(label = "Charity", value = s.charityMonthly, onValueChange = { v -> patchSettings { it.copy(charityMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("charity", it.copy(charityMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("other_discretionary")) {
                NumberSettingField(label = "Other Discretionary", value = s.otherDiscretionaryMonthly, onValueChange = { v -> patchSettings { it.copy(otherDiscretionaryMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("other_discretionary", it.copy(otherDiscretionaryMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("entertainment")) {
                NumberSettingField(label = "Entertainment", value = s.entertainmentMonthly, onValueChange = { v -> patchSettings { it.copy(entertainmentMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("entertainment", it.copy(entertainmentMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("transport")) {
                NumberSettingField(label = "Transport", value = s.transportMonthly, onValueChange = { v -> patchSettings { it.copy(transportMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("transport", it.copy(transportMonthly = 0.0)) } })
            }
            if (!deletedSet.contains("subscriptions")) {
                NumberSettingField(label = "Subscriptions", value = s.subscriptionsMonthly, onValueChange = { v -> patchSettings { it.copy(subscriptionsMonthly = v) } }, onDelete = { patchSettings { deleteBuiltInKey("subscriptions", it.copy(subscriptionsMonthly = 0.0)) } })
            }

            if (customCategories.isNotEmpty()) {
                customCategories.forEach { item ->
                    key(item.id) {
                        NumberSettingField(
                            label = item.name,
                            value = item.amount,
                            onValueChange = { updatedVal ->
                                patchSettings { cur ->
                                    val updatedList = parseCustomExpenses(cur.customExpensesJson).map {
                                        if (it.id == item.id) it.copy(amount = updatedVal) else it
                                    }
                                    cur.copy(customExpensesJson = serializeCustomExpenses(updatedList))
                                }
                            },
                            onDelete = {
                                patchSettings { cur ->
                                    val updatedList = parseCustomExpenses(cur.customExpensesJson).filter { it.id != item.id }
                                    cur.copy(customExpensesJson = serializeCustomExpenses(updatedList))
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShowAddCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_custom_category_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Add Custom Category")
            }
        }
    }

    // 3. Family & Children
    item {
        SettingsGroupCard(
            title = "Family & Children",
            subtitle = "Bonus +${fmtCZK(state.taxReturnHelper.childBonus)} yearly",
            initiallyExpanded = false,
            badgeText = if (s.childExpensesEnabled) "ACTIVE" else "OFF",
            badgeColor = BrandTeal,
            info = SettingsMetricInfos.childBonus,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "family",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            BooleanSettingField(label = "Enable Family Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { v -> patchSettings { it.copy(childExpensesEnabled = v) } })
            if (s.childExpensesEnabled) {
                BooleanSettingField(
                    label = "Current Child Costs in Monthly Budget",
                    checked = s.currentChildCostsInBaseline,
                    onCheckedChange = { v -> patchSettings { it.copy(currentChildCostsInBaseline = v) } }
                )
                Text(
                    text = if (s.currentChildCostsInBaseline) {
                        "Active: Manual budget (groceries, rent) is treated as already covering current toddler costs (0 Kč added today). Projections only add the incremental delta as child grows older (+${(s.childPreschoolMonthly - s.childToddlerMonthly).toInt().coerceAtLeast(0)} Kč at preschool, +${(s.childSchoolMonthly - s.childToddlerMonthly).toInt().coerceAtLeast(0)} Kč at school), eliminating double counting."
                    } else {
                        "Off: Full child stage expense is added on top of your manual budget every year."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            BooleanSettingField(label = "Include Child 1", checked = s.child1Enabled, onCheckedChange = { v -> patchSettings { it.copy(child1Enabled = v) } })
            if (s.child1Enabled) {
                TextSettingField(label = "Child 1 Name", value = s.child1Name, onValueChange = { v -> patchSettings { it.copy(child1Name = v) } })
                NumberSettingField(label = "Child 1 Birth Year", value = s.child1BirthYear.toDouble(), onValueChange = { v -> patchSettings { it.copy(child1BirthYear = v.toInt()) } })
                NumberSettingField(label = "Child 1 Tax Bonus Annual", value = s.child1TaxBonusAnnual, onValueChange = { v -> patchSettings { it.copy(child1TaxBonusAnnual = v) } })

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Stage Expense Estimates (per Child)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                NumberSettingField(label = "Toddler Monthly", value = s.childToddlerMonthly, onValueChange = { v -> patchSettings { it.copy(childToddlerMonthly = v) } })
                NumberSettingField(label = "Preschool Monthly", value = s.childPreschoolMonthly, onValueChange = { v -> patchSettings { it.copy(childPreschoolMonthly = v) } })
                NumberSettingField(label = "School Monthly", value = s.childSchoolMonthly, onValueChange = { v -> patchSettings { it.copy(childSchoolMonthly = v) } })
                NumberSettingField(label = "Teen Monthly", value = s.childTeenMonthly, onValueChange = { v -> patchSettings { it.copy(childTeenMonthly = v) } })
                NumberSettingField(label = "University Monthly", value = s.childUniMonthly, onValueChange = { v -> patchSettings { it.copy(childUniMonthly = v) } })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BooleanSettingField(label = "Include Planned Child 2 Milestone", checked = s.child2Enabled, onCheckedChange = { v -> patchSettings { it.copy(child2Enabled = v) } })
            if (s.child2Enabled) {
                TextSettingField(label = "Child 2 Name", value = s.child2Name, onValueChange = { v -> patchSettings { it.copy(child2Name = v) } })
                NumberSettingField(label = "Child 2 Planned Birth Year", value = s.child2BirthYear.toDouble(), onValueChange = { v -> patchSettings { it.copy(child2BirthYear = v.toInt()) } })
                NumberSettingField(label = "Child 2 Tax Bonus Annual", value = s.child2TaxBonusAnnual, onValueChange = { v -> patchSettings { it.copy(child2TaxBonusAnnual = v) } })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NumberSettingField(
                label = "Child Support Until Age (Years)",
                value = s.childSupportUntilAge.toDouble(),
                minValue = 0.0,
                maxValue = 50.0,
                onValueChange = { v -> patchSettings { it.copy(childSupportUntilAge = v.toInt()) } },
                fieldKey = "child_support_age"
            )
            BooleanSettingField(
                label = "Prolong Child Support into FIRE Target",
                checked = s.prolongChildSupportInFire,
                onCheckedChange = { v -> patchSettings { it.copy(prolongChildSupportInFire = v) } },
                info = SettingsMetricInfos.prolongChildSupport,
                onShowInfo = onShowInfo
            )
            Text(
                text = if (s.prolongChildSupportInFire) {
                    "Active: FIRE target budget includes ongoing child and family support (rather than dropping to 0 Kč at early retirement)."
                } else {
                    "Off: Child expenses are phased out in the baseline early-retirement nest egg."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
    }
}
