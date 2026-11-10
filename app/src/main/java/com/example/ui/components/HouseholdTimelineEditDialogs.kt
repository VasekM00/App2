package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.SettingsEntity
import com.example.domain.HouseholdMember

@Composable
fun EditPrimaryEarnerDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(settings.primaryName) }
    var netSalaryStr by rememberSaveable { mutableStateOf(settings.vSalary.toInt().toString()) }
    var salaryGrowthStr by rememberSaveable { mutableStateOf(settings.vSalaryGrowthPct.toString()) }
    var vouchersStr by rememberSaveable { mutableStateOf(settings.vMealVouchersMonthly.toInt().toString()) }
    var giftStr by rememberSaveable { mutableStateOf(settings.vGiftsMonthly.toInt().toString()) }
    var otherInflowsStr by rememberSaveable { mutableStateOf(settings.vOtherInflowsMonthly.toInt().toString()) }
    var pensionAgeStr by rememberSaveable { mutableStateOf(settings.vStatePensionAge.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Primary Earner (${settings.primaryName})", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = netSalaryStr,
                    onValueChange = { netSalaryStr = it },
                    label = { Text("Monthly Net Salary (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = salaryGrowthStr,
                    onValueChange = { salaryGrowthStr = it },
                    label = { Text("Annual Salary Growth (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vouchersStr,
                    onValueChange = { vouchersStr = it },
                    label = { Text("Meal Vouchers Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = giftStr,
                    onValueChange = { giftStr = it },
                    label = { Text("Tax-Exempt Family Gift Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = otherInflowsStr,
                    onValueChange = { otherInflowsStr = it },
                    label = { Text("Other Net Inflows Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pensionAgeStr,
                    onValueChange = { pensionAgeStr = it },
                    label = { Text("Target Statutory Pension Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim().ifEmpty { settings.primaryName }
                    val salary = netSalaryStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.vSalary
                    val growth = salaryGrowthStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.vSalaryGrowthPct
                    val vouchers = vouchersStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.vMealVouchersMonthly
                    val gift = giftStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.vGiftsMonthly
                    val other = otherInflowsStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.vOtherInflowsMonthly
                    val pensionAge = pensionAgeStr.trim().toIntOrNull() ?: settings.vStatePensionAge

                    onSave(
                        settings.copy(
                            primaryName = trimmedName,
                            vSalary = salary.coerceAtLeast(0.0),
                            vSalaryGrowthPct = growth.coerceAtLeast(0.0),
                            vMealVouchersMonthly = vouchers.coerceAtLeast(0.0),
                            vGiftsMonthly = gift.coerceAtLeast(0.0),
                            familyGiftMonthly = gift.coerceAtLeast(0.0) + settings.eGiftsMonthly,
                            vOtherInflowsMonthly = other.coerceAtLeast(0.0),
                            vStatePensionAge = pensionAge.coerceIn(55, 75)
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditSpouseEarnerDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(settings.spouseName) }
    var allowanceStr by rememberSaveable { mutableStateOf(settings.eParentalAllowanceMonthly.toInt().toString()) }
    var lecturingStr by rememberSaveable { mutableStateOf(settings.eLecturingMonthly.toInt().toString()) }
    var includeLecturing by rememberSaveable { mutableStateOf(settings.eIncludeLecturing) }
    var returnYearStr by rememberSaveable { mutableStateOf(settings.eReturnYear.toString()) }
    var returnMonthStr by rememberSaveable { mutableStateOf(settings.eReturnMonth.toString()) }
    var startingSalaryStr by rememberSaveable { mutableStateOf(settings.eStartingSalary.toInt().toString()) }
    var salaryGrowthStr by rememberSaveable { mutableStateOf(settings.eSalaryGrowthPct.toString()) }
    var giftStr by rememberSaveable { mutableStateOf(settings.eGiftsMonthly.toInt().toString()) }
    var otherInflowsStr by rememberSaveable { mutableStateOf(settings.eOtherInflowsMonthly.toInt().toString()) }
    var pensionAgeStr by rememberSaveable { mutableStateOf(settings.eStatePensionAge.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Partner (${settings.spouseName.ifBlank { "Eleonora" }})", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = allowanceStr,
                    onValueChange = { allowanceStr = it },
                    label = { Text("Parental Allowance Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lecturingStr,
                    onValueChange = { lecturingStr = it },
                    label = { Text("Lecturing Income Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Include Lecturing in Cash Flow",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = includeLecturing,
                        onCheckedChange = { includeLecturing = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = returnYearStr,
                        onValueChange = { returnYearStr = it },
                        label = { Text("Return Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = returnMonthStr,
                        onValueChange = { returnMonthStr = it },
                        label = { Text("Month (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = startingSalaryStr,
                    onValueChange = { startingSalaryStr = it },
                    label = { Text("Starting Net Salary upon Return (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = salaryGrowthStr,
                    onValueChange = { salaryGrowthStr = it },
                    label = { Text("Annual Salary Growth (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = giftStr,
                    onValueChange = { giftStr = it },
                    label = { Text("Separate Family Gift Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = otherInflowsStr,
                    onValueChange = { otherInflowsStr = it },
                    label = { Text("Other Net Inflows Monthly (CZK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pensionAgeStr,
                    onValueChange = { pensionAgeStr = it },
                    label = { Text("Target Statutory Pension Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim().ifEmpty { settings.spouseName }
                    val allowance = allowanceStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eParentalAllowanceMonthly
                    val lecturing = lecturingStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eLecturingMonthly
                    val returnYr = returnYearStr.trim().toIntOrNull() ?: settings.eReturnYear
                    val returnMo = (returnMonthStr.trim().toIntOrNull() ?: settings.eReturnMonth).coerceIn(1, 12)
                    val startSalary = startingSalaryStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eStartingSalary
                    val growth = salaryGrowthStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eSalaryGrowthPct
                    val gift = giftStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eGiftsMonthly
                    val other = otherInflowsStr.replace(',', '.').trim().toDoubleOrNull() ?: settings.eOtherInflowsMonthly
                    val pensionAge = pensionAgeStr.trim().toIntOrNull() ?: settings.eStatePensionAge

                    onSave(
                        settings.copy(
                            spouseName = trimmedName,
                            eParentalAllowanceMonthly = allowance.coerceAtLeast(0.0),
                            eLecturingMonthly = lecturing.coerceAtLeast(0.0),
                            eIncludeLecturing = includeLecturing,
                            eReturnYear = returnYr,
                            eReturnMonth = returnMo,
                            eStartingSalary = startSalary.coerceAtLeast(0.0),
                            eSalaryGrowthPct = growth.coerceAtLeast(0.0),
                            eGiftsMonthly = gift.coerceAtLeast(0.0),
                            familyGiftMonthly = settings.vGiftsMonthly + gift.coerceAtLeast(0.0),
                            eOtherInflowsMonthly = other.coerceAtLeast(0.0),
                            eStatePensionAge = pensionAge.coerceIn(55, 75)
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
