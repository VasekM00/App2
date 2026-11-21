package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.SettingsEntity
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull

@Composable
fun EditPrimaryEarnerDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit,
    onSaveTransform: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(settings.primaryName) }
    var netSalaryStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.vSalary)) }
    var salaryGrowthStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.vSalaryGrowthPct)) }
    var vouchersStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.vMealVouchersMonthly)) }
    var giftStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.vGiftsMonthly)) }
    var otherInflowsStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.vOtherInflowsMonthly)) }
    var pensionAgeStr by rememberSaveable { mutableStateOf(settings.vStatePensionAge.toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Primary Earner (${settings.primaryName})", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorText = null },
                        label = { Text("Name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = netSalaryStr,
                        onValueChange = { netSalaryStr = it; errorText = null },
                        label = { Text("Monthly Net Salary") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salaryGrowthStr,
                        onValueChange = { salaryGrowthStr = it; errorText = null },
                        label = { Text("Annual Salary Growth") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = vouchersStr,
                        onValueChange = { vouchersStr = it; errorText = null },
                        label = { Text("Meal Vouchers Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = giftStr,
                        onValueChange = { giftStr = it; errorText = null },
                        label = { Text("Tax-Exempt Family Gift Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = otherInflowsStr,
                        onValueChange = { otherInflowsStr = it; errorText = null },
                        label = { Text("Other Net Inflows Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pensionAgeStr,
                        onValueChange = { pensionAgeStr = it; errorText = null },
                        label = { Text("Target Statutory Pension Age") },
                        suffix = { Text("years") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    fun parseNonNeg(raw: String, label: String): Double? {
                        if (raw.isBlank()) return 0.0
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < 0.0) {
                            errorText = "$label must be a valid non-negative number."
                            return null
                        }
                        return p
                    }
                    fun parseGrowth(raw: String): Double? {
                        if (raw.isBlank()) return 0.0
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < -99.0 || p > 1000.0) {
                            errorText = "Annual Salary Growth must be between -99% and 1000%."
                            return null
                        }
                        return p
                    }
                    val trimmedName = name.trim().ifEmpty { settings.primaryName }
                    val salary = parseNonNeg(netSalaryStr, "Monthly Net Salary") ?: return@Button
                    val growth = parseGrowth(salaryGrowthStr) ?: return@Button
                    val vouchers = parseNonNeg(vouchersStr, "Meal Vouchers Monthly") ?: return@Button
                    val gift = parseNonNeg(giftStr, "Tax-Exempt Family Gift Monthly") ?: return@Button
                    val other = parseNonNeg(otherInflowsStr, "Other Net Inflows Monthly") ?: return@Button
                    val pensionAge = pensionAgeStr.trim().toIntOrNull()
                    if (pensionAge == null || pensionAge !in 40..90) {
                        errorText = "Target Statutory Pension Age must be between 40 and 90."
                        return@Button
                    }

                    val patch: (SettingsEntity) -> SettingsEntity = { cur ->
                        cur.copy(
                            primaryName = trimmedName,
                            vSalary = salary,
                            vSalaryGrowthPct = growth,
                            vMealVouchersMonthly = vouchers,
                            vGiftsMonthly = gift,
                            vOtherInflowsMonthly = other,
                            vStatePensionAge = pensionAge
                        )
                    }
                    if (onSaveTransform != null) {
                        onSaveTransform(patch)
                    } else {
                        onSave(patch(settings))
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditSpouseEarnerDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit,
    onSaveTransform: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(settings.spouseName) }
    var allowanceStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eParentalAllowanceMonthly)) }
    var lecturingStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eLecturingMonthly)) }
    var includeLecturing by rememberSaveable { mutableStateOf(settings.eIncludeLecturing) }
    var returnYearStr by rememberSaveable { mutableStateOf(settings.eReturnYear.toString()) }
    var returnMonthStr by rememberSaveable { mutableStateOf(settings.eReturnMonth.toString()) }
    var startingSalaryStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eStartingSalary)) }
    var salaryGrowthStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eSalaryGrowthPct)) }
    var giftStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eGiftsMonthly)) }
    var otherInflowsStr by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eOtherInflowsMonthly)) }
    var pensionAgeStr by rememberSaveable { mutableStateOf(settings.eStatePensionAge.toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Partner (${settings.spouseName.ifBlank { "Partner" }})", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorText = null },
                        label = { Text("Name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it; errorText = null },
                        label = { Text("Parental Allowance Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lecturingStr,
                        onValueChange = { lecturingStr = it; errorText = null },
                        label = { Text("Lecturing Income Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .toggleable(
                                value = includeLecturing,
                                onValueChange = { includeLecturing = it },
                                role = Role.Switch
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Include Lecturing in Cash Flow",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Switch(
                            checked = includeLecturing,
                            onCheckedChange = null
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = returnYearStr,
                            onValueChange = { returnYearStr = it; errorText = null },
                            label = { Text("Return Year") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = returnMonthStr,
                            onValueChange = { returnMonthStr = it; errorText = null },
                            label = { Text("Month (1-12)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = startingSalaryStr,
                        onValueChange = { startingSalaryStr = it; errorText = null },
                        label = { Text("Starting Net Salary upon Return") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salaryGrowthStr,
                        onValueChange = { salaryGrowthStr = it; errorText = null },
                        label = { Text("Annual Salary Growth") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = giftStr,
                        onValueChange = { giftStr = it; errorText = null },
                        label = { Text("Separate Family Gift Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = otherInflowsStr,
                        onValueChange = { otherInflowsStr = it; errorText = null },
                        label = { Text("Other Net Inflows Monthly") },
                        suffix = { Text(" Kč") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pensionAgeStr,
                        onValueChange = { pensionAgeStr = it; errorText = null },
                        label = { Text("Target Statutory Pension Age") },
                        suffix = { Text("years") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    fun parseNonNeg(raw: String, label: String): Double? {
                        if (raw.isBlank()) return 0.0
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < 0.0) {
                            errorText = "$label must be a valid non-negative number."
                            return null
                        }
                        return p
                    }
                    fun parseGrowth(raw: String): Double? {
                        if (raw.isBlank()) return 0.0
                        val p = raw.parseUserAmountOrNull()
                        if (p == null || !p.isFinite() || p < -99.0 || p > 1000.0) {
                            errorText = "Annual Salary Growth must be between -99% and 1000%."
                            return null
                        }
                        return p
                    }
                    val trimmedName = name.trim().ifEmpty { settings.spouseName }
                    val allowance = parseNonNeg(allowanceStr, "Parental Allowance Monthly") ?: return@Button
                    val lecturing = parseNonNeg(lecturingStr, "Lecturing Income Monthly") ?: return@Button
                    val returnYr = returnYearStr.trim().toIntOrNull()
                    if (returnYr == null || returnYr !in 1950..2200) {
                        errorText = "Return Year must be between 1950 and 2200."
                        return@Button
                    }
                    val returnMo = returnMonthStr.trim().toIntOrNull()
                    if (returnMo == null || returnMo !in 1..12) {
                        errorText = "Return Month must be between 1 and 12."
                        return@Button
                    }
                    val startSalary = parseNonNeg(startingSalaryStr, "Starting Net Salary") ?: return@Button
                    val growth = parseGrowth(salaryGrowthStr) ?: return@Button
                    val gift = parseNonNeg(giftStr, "Separate Family Gift Monthly") ?: return@Button
                    val other = parseNonNeg(otherInflowsStr, "Other Net Inflows Monthly") ?: return@Button
                    val pensionAge = pensionAgeStr.trim().toIntOrNull()
                    if (pensionAge == null || pensionAge !in 40..90) {
                        errorText = "Target Statutory Pension Age must be between 40 and 90."
                        return@Button
                    }

                    val patch: (SettingsEntity) -> SettingsEntity = { cur ->
                        cur.copy(
                            spouseName = trimmedName,
                            eParentalAllowanceMonthly = allowance,
                            eLecturingMonthly = lecturing,
                            eIncludeLecturing = includeLecturing,
                            eReturnYear = returnYr,
                            eReturnMonth = returnMo,
                            eStartingSalary = startSalary,
                            eSalaryGrowthPct = growth,
                            eGiftsMonthly = gift,
                            eOtherInflowsMonthly = other,
                            eStatePensionAge = pensionAge
                        )
                    }
                    if (onSaveTransform != null) {
                        onSaveTransform(patch)
                    } else {
                        onSave(patch(settings))
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
