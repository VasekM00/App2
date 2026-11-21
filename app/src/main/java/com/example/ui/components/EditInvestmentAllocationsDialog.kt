package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.OnBrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtEditableAmount
import com.example.util.parseUserAmountOrNull

@Composable
fun EditInvestmentAllocationsDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onSave: (SettingsEntity) -> Unit,
    onSaveTransform: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val isSingle = settings.isSingleHousehold

    // Monthly DCA contribution inputs
    var portuVText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.portuDcaMonthly)) }
    var portuEText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.ePortuDcaMonthly)) }
    var dipVText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.dipContributionMonthly)) }
    var dipEText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eDipContributionMonthly)) }
    var dpsVText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.dpsOwnContributionMonthly)) }
    var dpsEText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eDpsOwnContributionMonthly)) }
    var employerMatchText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.employerRetirementMonthly)) }
    var eEmployerMatchText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eEmployerRetirementMonthly)) }

    // Current portfolio & reserve balance inputs
    var liquidBalVText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.liquidPortfolioCurrent)) }
    var liquidBalEText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eLiquidPortfolioCurrent)) }
    var dipBalText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.dipBalanceCurrent)) }
    var eDipBalText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eDipBalanceCurrent)) }
    var dpsBalText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.dpsBalanceCurrent)) }
    var eDpsBalText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.eDpsBalanceCurrent)) }
    var emergencyReserveText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.emergencyReserveCurrent)) }
    var emergencyTargetText by rememberSaveable { mutableStateOf(fmtEditableAmount(settings.emergencyReserveTarget)) }
    var isReserveAuto by rememberSaveable {
        mutableStateOf(!settings.emergencyReserveMonthlyOverride.isFinite() || settings.emergencyReserveMonthlyOverride < 0.0)
    }
    var emergencyReserveMonthlyText by rememberSaveable {
        val initialVal = if (settings.emergencyReserveMonthlyOverride.isFinite() && settings.emergencyReserveMonthlyOverride >= 0.0) {
            settings.emergencyReserveMonthlyOverride
        } else {
            FinancialEngine.autoEmergencyReserveContribution(settings)
        }
        mutableStateOf(fmtEditableAmount(initialVal))
    }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    // Calculated preview totals matching Savings & Investment Allocations card
    val previewSettings = remember(
        settings, isSingle, portuVText, portuEText, dipVText, dipEText, dpsVText, dpsEText,
        employerMatchText, eEmployerMatchText, emergencyReserveText, emergencyTargetText,
        isReserveAuto, emergencyReserveMonthlyText
    ) {
        fun parsedOrZero(raw: String): Double =
            raw.parseUserAmountOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        settings.copy(
            portuDcaMonthly = parsedOrZero(portuVText),
            ePortuDcaMonthly = if (!isSingle) parsedOrZero(portuEText) else 0.0,
            dipContributionMonthly = parsedOrZero(dipVText),
            eDipContributionMonthly = if (!isSingle) parsedOrZero(dipEText) else 0.0,
            dpsOwnContributionMonthly = parsedOrZero(dpsVText),
            eDpsOwnContributionMonthly = if (!isSingle) parsedOrZero(dpsEText) else 0.0,
            employerRetirementMonthly = parsedOrZero(employerMatchText),
            eEmployerRetirementMonthly = if (!isSingle) parsedOrZero(eEmployerMatchText) else 0.0,
            emergencyReserveCurrent = parsedOrZero(emergencyReserveText),
            emergencyReserveTarget = if (settings.emergencyReserveMode == "Target") {
                parsedOrZero(emergencyTargetText)
            } else {
                settings.emergencyReserveTarget
            },
            emergencyReserveMonthlyOverride = if (isReserveAuto) -1.0 else parsedOrZero(emergencyReserveMonthlyText)
        )
    }
    val previewReserveContrib = FinancialEngine.monthlyEmergencyReserveContribution(previewSettings)
    val totalMonthlyInvest = FinancialEngine.monthlySavingsAndInvestmentTotal(previewSettings)

    val maxBodyHeight = 460.dp

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("edit_investments_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.15f),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Column {
                    Text(
                        text = "Edit Investment Allocations",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Monthly DCA targets and baseline portfolio balances",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight)
                        .imePadding()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                // Live summary header banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandTeal.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (previewReserveContrib > 0.0) {
                                    "Total Savings & Invested (+${fmtCZK(previewReserveContrib)} reserve)"
                                } else {
                                    "Total Monthly Savings & Invested"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "${fmtCZK(totalMonthlyInvest.finiteOrZero())} monthly",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoodGreen,
                                    fontFamily = BodyFontFamily,
                                    fontSize = 15.sp
                                )
                            )
                        }
                        ColorPill(
                            text = "AUTOMATED DCA",
                            color = BrandTeal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 7.dp,
                            verticalPadding = 3.dp
                        )
                    }
                }

                // Section 1: Liquid Brokerage / Portu ETF
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. Liquid ETF Portfolio (Portu / Brokerage)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Core pillar of the Pre-60 Liquid Bridge. Capital gains 100% tax-exempt after 3-year holding period.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = portuVText,
                            onValueChange = { portuVText = it; errorText = null },
                            label = { Text("${settings.primaryName} · Portu ETF Monthly") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_portu_v")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = portuEText,
                                onValueChange = { portuEText = it; errorText = null },
                                label = { Text("${settings.spouseName} · Portu ETF Monthly") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_portu_e")
                            )
                        }
                    }
                }

                // Section 2: DIP (Long-Term Investment Product)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. DIP (Long-Term Investment Product)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoodGreen,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Annual income tax deduction up to 48\u00A0000\u00A0Kč. Generates 7\u00A0200\u00A0Kč to 11\u00A0040\u00A0Kč in direct tax savings.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = dipVText,
                            onValueChange = { dipVText = it; errorText = null },
                            label = { Text("${settings.primaryName} · DIP Monthly") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_dip_v")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = dipEText,
                                onValueChange = { dipEText = it; errorText = null },
                                label = { Text("${settings.spouseName} · DIP Monthly") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_dip_e")
                            )
                        }
                    }
                }

                // Section 3: DPS (Supplementary Pension Scheme)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "3. DPS (Supplementary Pension Scheme)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Direct 20% state subsidy on monthly deposits from 500 to 1\u00A0700\u00A0Kč (up to 340\u00A0Kč/month state match).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = dpsVText,
                            onValueChange = { dpsVText = it; errorText = null },
                            label = { Text("${settings.primaryName} · DPS Monthly") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_dps_v")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = dpsEText,
                                onValueChange = { dpsEText = it; errorText = null },
                                label = { Text("${settings.spouseName} · DPS Monthly") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_dps_e")
                            )
                        }

                        OutlinedTextField(
                            value = employerMatchText,
                            onValueChange = { employerMatchText = it; errorText = null },
                            label = { Text("${settings.primaryName} · Employer Pension Match") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_employer_match")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = eEmployerMatchText,
                                onValueChange = { eEmployerMatchText = it; errorText = null },
                                label = { Text("${settings.spouseName} · Employer Pension Match") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_employer_match_e")
                            )
                        }
                    }
                }

                // Section 4: Emergency Reserve & Current Account Balances
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "4. Emergency Reserve & Starting Balances",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        )

                        OutlinedTextField(
                            value = emergencyReserveText,
                            onValueChange = { emergencyReserveText = it; errorText = null },
                            label = { Text("Emergency Cash Reserve (Current)") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_emergency_reserve")
                        )

                        if (settings.emergencyReserveMode == "Target") {
                            OutlinedTextField(
                                value = emergencyTargetText,
                                onValueChange = { emergencyTargetText = it; errorText = null },
                                label = { Text("Emergency Reserve Target") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_emergency_target")
                            )
                        }

                        val effectiveTarget = FinancialEngine.effectiveEmergencyReserveTarget(previewSettings)
                        val autoContribPreview = FinancialEngine.autoEmergencyReserveContribution(previewSettings)

                        Text(
                            text = "Monthly Reserve Contribution",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                        ChipWrapRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalSpacing = 8.dp
                        ) {
                            FilterChip(
                                selected = isReserveAuto,
                                onClick = { isReserveAuto = true },
                                label = { Text("Auto (24-month gap)", fontSize = 11.sp, maxLines = 2, softWrap = true) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_reserve_auto"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                                    selectedLabelColor = BrandTeal
                                )
                            )
                            FilterChip(
                                selected = !isReserveAuto,
                                onClick = { isReserveAuto = false },
                                label = { Text("Manual (Kč/month)", fontSize = 11.sp, maxLines = 2, softWrap = true) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_reserve_manual"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                                    selectedLabelColor = BrandTeal
                                )
                            )
                        }
                        if (isReserveAuto) {
                            val currentVal = previewSettings.emergencyReserveCurrent
                            Text(
                                text = if (autoContribPreview > 0.0) {
                                    "Auto (${settings.emergencyReserveMode}): (${fmtCZK(effectiveTarget)} target − ${fmtCZK(currentVal)}) ÷ 24 months = ${fmtCZK(autoContribPreview)}/month"
                                } else {
                                    "Target (${settings.emergencyReserveMode}): ${fmtCZK(effectiveTarget)} (100% funded → 0 Kč/month)"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        } else {
                            OutlinedTextField(
                                value = emergencyReserveMonthlyText,
                                onValueChange = { emergencyReserveMonthlyText = it; errorText = null },
                                label = { Text("Manual Monthly Reserve Contribution") },
                                suffix = { Text("Kč") },
                                supportingText = { Text("Enter 0 to exclude reserve build from monthly allocations", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_emergency_reserve_monthly")
                            )
                        }

                        OutlinedTextField(
                            value = liquidBalVText,
                            onValueChange = { liquidBalVText = it; errorText = null },
                            label = { Text("${settings.primaryName} · Liquid ETF / Portu Balance") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_bal_liquid_v")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = liquidBalEText,
                                onValueChange = { liquidBalEText = it; errorText = null },
                                label = { Text("${settings.spouseName} · Liquid ETF / Portu Balance") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_bal_liquid_e")
                            )
                        }

                        OutlinedTextField(
                            value = dipBalText,
                            onValueChange = { dipBalText = it; errorText = null },
                            label = { Text("${settings.primaryName} · DIP Balance") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_bal_dip")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = eDipBalText,
                                onValueChange = { eDipBalText = it; errorText = null },
                                label = { Text("${settings.spouseName} · DIP Balance") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_bal_dip_e")
                            )
                        }

                        OutlinedTextField(
                            value = dpsBalText,
                            onValueChange = { dpsBalText = it; errorText = null },
                            label = { Text("${settings.primaryName} · DPS Balance") },
                            suffix = { Text("Kč") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (isSingle) ImeAction.Done else ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_bal_dps")
                        )

                        if (!isSingle) {
                            OutlinedTextField(
                                value = eDpsBalText,
                                onValueChange = { eDpsBalText = it; errorText = null },
                                label = { Text("${settings.spouseName} · DPS Balance") },
                                suffix = { Text("Kč") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_bal_dps_e")
                            )
                        }
                    }
                }
                }
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("investments_amount_error")
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

                    val portuV = parseNonNeg(portuVText, "${settings.primaryName} Portu ETF Monthly") ?: return@Button
                    val portuE = if (!isSingle) (parseNonNeg(portuEText, "${settings.spouseName} Portu ETF Monthly") ?: return@Button) else null
                    val dipV = parseNonNeg(dipVText, "${settings.primaryName} DIP Monthly") ?: return@Button
                    val dipE = if (!isSingle) (parseNonNeg(dipEText, "${settings.spouseName} DIP Monthly") ?: return@Button) else null
                    val dpsV = parseNonNeg(dpsVText, "${settings.primaryName} DPS Monthly") ?: return@Button
                    val dpsE = if (!isSingle) (parseNonNeg(dpsEText, "${settings.spouseName} DPS Monthly") ?: return@Button) else null
                    val empV = parseNonNeg(employerMatchText, "${settings.primaryName} Employer Match") ?: return@Button
                    val empE = if (!isSingle) (parseNonNeg(eEmployerMatchText, "${settings.spouseName} Employer Match") ?: return@Button) else null
                    val reserveCur = parseNonNeg(emergencyReserveText, "Emergency Cash Reserve") ?: return@Button
                    val reserveTarget = if (settings.emergencyReserveMode == "Target") {
                        parseNonNeg(emergencyTargetText, "Emergency Reserve Target") ?: return@Button
                    } else null
                    val reserveOverride = if (isReserveAuto) {
                        -1.0
                    } else {
                        parseNonNeg(emergencyReserveMonthlyText, "Manual Monthly Reserve Contribution") ?: return@Button
                    }
                    val liqV = parseNonNeg(liquidBalVText, "${settings.primaryName} Liquid Balance") ?: return@Button
                    val liqE = if (!isSingle) (parseNonNeg(liquidBalEText, "${settings.spouseName} Liquid Balance") ?: return@Button) else null
                    val dipBalV = parseNonNeg(dipBalText, "${settings.primaryName} DIP Balance") ?: return@Button
                    val dipBalE = if (!isSingle) (parseNonNeg(eDipBalText, "${settings.spouseName} DIP Balance") ?: return@Button) else null
                    val dpsBalV = parseNonNeg(dpsBalText, "${settings.primaryName} DPS Balance") ?: return@Button
                    val dpsBalE = if (!isSingle) (parseNonNeg(eDpsBalText, "${settings.spouseName} DPS Balance") ?: return@Button) else null

                    errorText = null
                    val patch: (SettingsEntity) -> SettingsEntity = { cur ->
                        cur.copy(
                            portuDcaMonthly = portuV,
                            ePortuDcaMonthly = portuE ?: cur.ePortuDcaMonthly,
                            dipContributionMonthly = dipV,
                            eDipContributionMonthly = dipE ?: cur.eDipContributionMonthly,
                            dpsOwnContributionMonthly = dpsV,
                            eDpsOwnContributionMonthly = dpsE ?: cur.eDpsOwnContributionMonthly,
                            employerRetirementMonthly = empV,
                            eEmployerRetirementMonthly = empE ?: cur.eEmployerRetirementMonthly,
                            emergencyReserveCurrent = reserveCur,
                            emergencyReserveTarget = reserveTarget ?: cur.emergencyReserveTarget,
                            emergencyReserveMonthlyOverride = reserveOverride,
                            liquidPortfolioCurrent = liqV,
                            eLiquidPortfolioCurrent = liqE ?: cur.eLiquidPortfolioCurrent,
                            dipBalanceCurrent = dipBalV,
                            eDipBalanceCurrent = dipBalE ?: cur.eDipBalanceCurrent,
                            dpsBalanceCurrent = dpsBalV,
                            eDpsBalanceCurrent = dpsBalE ?: cur.eDpsBalanceCurrent
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_investments_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
