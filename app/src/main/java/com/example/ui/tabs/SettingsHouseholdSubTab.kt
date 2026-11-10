package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct

fun LazyListScope.settingsHouseholdSubTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    expandTrigger: Int,
    expandTargetState: Boolean?,
    onShowAddCategory: () -> Unit,
    onShowAddLumpSum: (CustomLumpSumItem?) -> Unit,
    onShowInfo: (MetricInfo) -> Unit
) {
    val s = state.settings

    // 1. Earned & Side Incomes
    item {
        SettingsGroupCard(
            title = "Earned & Side Incomes",
            subtitle = if (s.isSingleHousehold) {
                "Václav ${fmtCZK(s.vSalary)}/mo · Growth ${fmtPct(s.vSalaryGrowthPct)}/yr · Single household mode"
            } else {
                "Václav ${fmtCZK(s.vSalary)}/mo · Eleonora ${fmtCZK(s.eStartingSalary)}/mo · Return ${s.eReturnMonth}/${s.eReturnYear}"
            },
            initiallyExpanded = false,
            badgeText = fmtCZK(state.currentIncome.totalMonthly) + "/mo",
            badgeColor = GoodGreen,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            BooleanSettingField(
                label = "Single Earner Household",
                checked = s.isSingleHousehold,
                onCheckedChange = { onUpdateSettings(s.copy(isSingleHousehold = it)) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Václav's Incomes",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
            )
            NumberSettingField(label = "Net Salary", value = s.vSalary, onValueChange = { onUpdateSettings(s.copy(vSalary = it)) })
            NumberSettingField(label = "Annual Salary Growth (%)", value = s.vSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(vSalaryGrowthPct = it)) })
            NumberSettingField(label = "Meal Vouchers Monthly", value = s.vMealVouchersMonthly, onValueChange = { onUpdateSettings(s.copy(vMealVouchersMonthly = it)) })
            NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.vOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(vOtherInflowsMonthly = it)) })
            NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.vGiftsMonthly, onValueChange = { onUpdateSettings(s.copy(vGiftsMonthly = it, familyGiftMonthly = it + s.eGiftsMonthly)) })

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
                        text = "Eleonora's Incomes (Employed)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                    )
                    NumberSettingField(label = "Net Salary", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                    NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) })
                    NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.eGiftsMonthly, onValueChange = { onUpdateSettings(s.copy(eGiftsMonthly = it, familyGiftMonthly = s.vGiftsMonthly + it)) })
                    NumberSettingField(label = "Annual Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                    NumberSettingField(label = "Reinvested Share of Salary (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    YearMonthSettingField(
                        label = "Planned Return",
                        yearValue = s.eReturnYear,
                        monthValue = s.eReturnMonth,
                        onValueChange = { yr, mo -> onUpdateSettings(s.copy(eReturnYear = yr, eReturnMonth = mo)) }
                    )
                } else {
                    Text(
                        text = "Eleonora's Incomes (Parental Leave until $returnMonthLabel)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                    )
                    NumberSettingField(label = "Parental Allowance Monthly", value = s.eParentalAllowanceMonthly, onValueChange = { onUpdateSettings(s.copy(eParentalAllowanceMonthly = it)) })
                    NumberSettingField(label = "Lecturing Monthly", value = s.eLecturingMonthly, onValueChange = { onUpdateSettings(s.copy(eLecturingMonthly = it)) })
                    NumberSettingField(label = "Other Monthly Inflows / Side Income", value = s.eOtherInflowsMonthly, onValueChange = { onUpdateSettings(s.copy(eOtherInflowsMonthly = it)) })
                    NumberSettingField(label = "Tax-Exempt Gifts Monthly", value = s.eGiftsMonthly, onValueChange = { onUpdateSettings(s.copy(eGiftsMonthly = it, familyGiftMonthly = s.vGiftsMonthly + it)) })

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Eleonora's Future Return to Work ($returnMonthLabel+)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    YearMonthSettingField(
                        label = "Planned Return",
                        yearValue = s.eReturnYear,
                        monthValue = s.eReturnMonth,
                        onValueChange = { yr, mo -> onUpdateSettings(s.copy(eReturnYear = yr, eReturnMonth = mo)) }
                    )
                    NumberSettingField(label = "Future Starting Salary Net", value = s.eStartingSalary, onValueChange = { onUpdateSettings(s.copy(eStartingSalary = it)) })
                    NumberSettingField(label = "Future Salary Growth (%)", value = s.eSalaryGrowthPct, onValueChange = { onUpdateSettings(s.copy(eSalaryGrowthPct = it)) })
                    NumberSettingField(label = "Future Reinvested Share (%)", value = s.eReinvestedPct, onValueChange = { onUpdateSettings(s.copy(eReinvestedPct = it)) })
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
            BooleanSettingField(label = "Include Primary Lump Sum", checked = s.lumpSumInclude, onCheckedChange = { onUpdateSettings(s.copy(lumpSumInclude = it)) })
            if (s.lumpSumInclude) {
                NumberSettingField(label = "Planned Year", value = s.lumpSumYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(lumpSumYear = it.toInt())) })
                NumberSettingField(label = "Planned Amount", value = s.lumpSumAmount, onValueChange = { onUpdateSettings(s.copy(lumpSumAmount = it)) })
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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
                                        style = MaterialTheme.typography.bodySmall.copy(color = GoodGreen, fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = item.enabled,
                                        onCheckedChange = { enabledState ->
                                            val updated = customLumpSums.map {
                                                if (it.id == item.id) it.copy(enabled = enabledState) else it
                                            }
                                            onUpdateSettings(s.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
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
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = customLumpSums.filter { it.id != item.id }
                                            onUpdateSettings(s.copy(customLumpSumsJson = serializeCustomLumpSums(updated)))
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete lump sum",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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
                modifier = Modifier.fillMaxWidth(),
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

        fun deleteBuiltInKey(key: String, newSettings: SettingsEntity): SettingsEntity {
            val newSet = deletedSet + key
            return newSettings.copy(deletedCategoriesJson = serializeDeletedCategories(newSet))
        }

        SettingsGroupCard(
            title = "Monthly Living Expenses",
            subtitle = "Rent ${fmtCZK(s.rentMonthly)} · Groceries ${fmtCZK(s.groceriesMonthly)} · Cafes ${fmtCZK(s.cafesMonthly)}",
            initiallyExpanded = false,
            badgeText = fmtCZK(state.totalLivingCostMonthly) + "/mo",
            badgeColor = BadRed,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            if (!deletedSet.contains("rent")) {
                NumberSettingField(label = "Rent / Housing", value = s.rentMonthly, onValueChange = { onUpdateSettings(s.copy(rentMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("rent", s.copy(rentMonthly = 0.0))) })
                Text(
                    text = "Indexed annually each July to ČSÚ CPI inflation (${s.cpiInflationPct}%)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            if (!deletedSet.contains("groceries")) {
                NumberSettingField(label = "Groceries & Daily Living", value = s.groceriesMonthly, onValueChange = { onUpdateSettings(s.copy(groceriesMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("groceries", s.copy(groceriesMonthly = 0.0))) })
            }
            if (!deletedSet.contains("cafes")) {
                NumberSettingField(label = "Cafes & Restaurants", value = s.cafesMonthly, onValueChange = { onUpdateSettings(s.copy(cafesMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("cafes", s.copy(cafesMonthly = 0.0))) })
            }
            if (!deletedSet.contains("therapy")) {
                NumberSettingField(label = "Therapy / Physio", value = s.therapyMonthly, onValueChange = { onUpdateSettings(s.copy(therapyMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("therapy", s.copy(therapyMonthly = 0.0))) })
            }
            if (!deletedSet.contains("charity")) {
                NumberSettingField(label = "Charity", value = s.charityMonthly, onValueChange = { onUpdateSettings(s.copy(charityMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("charity", s.copy(charityMonthly = 0.0))) })
            }
            if (!deletedSet.contains("other_discretionary") && s.otherDiscretionaryMonthly > 0.0) {
                NumberSettingField(label = "Other Discretionary", value = s.otherDiscretionaryMonthly, onValueChange = { onUpdateSettings(s.copy(otherDiscretionaryMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("other_discretionary", s.copy(otherDiscretionaryMonthly = 0.0))) })
            }
            if (!deletedSet.contains("entertainment")) {
                NumberSettingField(label = "Entertainment", value = s.entertainmentMonthly, onValueChange = { onUpdateSettings(s.copy(entertainmentMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("entertainment", s.copy(entertainmentMonthly = 0.0))) })
            }
            if (!deletedSet.contains("transport")) {
                NumberSettingField(label = "Transport", value = s.transportMonthly, onValueChange = { onUpdateSettings(s.copy(transportMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("transport", s.copy(transportMonthly = 0.0))) })
            }
            if (!deletedSet.contains("subscriptions")) {
                NumberSettingField(label = "Subscriptions", value = s.subscriptionsMonthly, onValueChange = { onUpdateSettings(s.copy(subscriptionsMonthly = it)) }, onDelete = { onUpdateSettings(deleteBuiltInKey("subscriptions", s.copy(subscriptionsMonthly = 0.0))) })
            }

            if (customCategories.isNotEmpty()) {
                customCategories.forEach { item ->
                    key(item.id) {
                        NumberSettingField(
                            label = item.name,
                            value = item.amount,
                            onValueChange = { updatedVal ->
                                val updatedList = customCategories.map {
                                    if (it.id == item.id) it.copy(amount = updatedVal) else it
                                }
                                onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
                            },
                            onDelete = {
                                val updatedList = customCategories.filter { it.id != item.id }
                                onUpdateSettings(s.copy(customExpensesJson = serializeCustomExpenses(updatedList)))
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
            subtitle = "${if (s.child1Enabled) "${s.child1Name} (${s.child1BirthYear})" else "No Child 1"}${if (s.child2Enabled) " · ${s.child2Name} (${s.child2BirthYear})" else ""} · Bonus +${fmtCZK(state.taxReturnHelper.childBonus)}/yr",
            initiallyExpanded = false,
            badgeText = if (s.childExpensesEnabled) "ACTIVE" else "OFF",
            badgeColor = BrandTeal,
            info = SettingsMetricInfos.childBonus,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            BooleanSettingField(label = "Enable Family Child Expenses", checked = s.childExpensesEnabled, onCheckedChange = { onUpdateSettings(s.copy(childExpensesEnabled = it)) })
            if (s.childExpensesEnabled) {
                BooleanSettingField(
                    label = "Current Child Costs in Monthly Budget",
                    checked = s.currentChildCostsInBaseline,
                    onCheckedChange = { onUpdateSettings(s.copy(currentChildCostsInBaseline = it)) }
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

            BooleanSettingField(label = "Include Child 1", checked = s.child1Enabled, onCheckedChange = { onUpdateSettings(s.copy(child1Enabled = it)) })
            if (s.child1Enabled) {
                TextSettingField(label = "Child 1 Name", value = s.child1Name, onValueChange = { onUpdateSettings(s.copy(child1Name = it)) })
                NumberSettingField(label = "Child 1 Birth Year", value = s.child1BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child1BirthYear = it.toInt())) })
                NumberSettingField(label = "Child 1 Tax Bonus Annual", value = s.child1TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child1TaxBonusAnnual = it)) })

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Stage Expense Estimates (per Child)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                NumberSettingField(label = "Toddler Monthly", value = s.childToddlerMonthly, onValueChange = { onUpdateSettings(s.copy(childToddlerMonthly = it)) })
                NumberSettingField(label = "Preschool Monthly", value = s.childPreschoolMonthly, onValueChange = { onUpdateSettings(s.copy(childPreschoolMonthly = it)) })
                NumberSettingField(label = "School Monthly", value = s.childSchoolMonthly, onValueChange = { onUpdateSettings(s.copy(childSchoolMonthly = it)) })
                NumberSettingField(label = "Teen Monthly", value = s.childTeenMonthly, onValueChange = { onUpdateSettings(s.copy(childTeenMonthly = it)) })
                NumberSettingField(label = "Uni Monthly", value = s.childUniMonthly, onValueChange = { onUpdateSettings(s.copy(childUniMonthly = it)) })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BooleanSettingField(label = "Include Planned Child 2 Milestone", checked = s.child2Enabled, onCheckedChange = { onUpdateSettings(s.copy(child2Enabled = it)) })
            if (s.child2Enabled) {
                TextSettingField(label = "Child 2 Name", value = s.child2Name, onValueChange = { onUpdateSettings(s.copy(child2Name = it)) })
                NumberSettingField(label = "Child 2 Planned Birth Year", value = s.child2BirthYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(child2BirthYear = it.toInt())) })
                NumberSettingField(label = "Child 2 Tax Bonus Annual", value = s.child2TaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child2TaxBonusAnnual = it)) })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BooleanSettingField(
                label = "Prolong Child Support into FIRE Target",
                checked = s.prolongChildSupportInFire,
                onCheckedChange = { onUpdateSettings(s.copy(prolongChildSupportInFire = it)) },
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
