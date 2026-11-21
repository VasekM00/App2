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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.ColorPill
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

fun LazyListScope.settingsFireSubTab(
    state: FullCalculationState,
    onUpdateSettings: (SettingsEntity) -> Unit,
    expandTrigger: Int,
    expandTargetState: Boolean?,
    focusedSectionKey: String? = null,
    focusedSectionTrigger: Int = 0,
    onShowInfo: (MetricInfo) -> Unit,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)? = null
) {
    val snap = state.snapshot
    val s = state.settings
    fun patchSettings(transform: (SettingsEntity) -> SettingsEntity) {
        dispatchSettingsPatch(s, onUpdateSettings, onReduceSettings, transform)
    }
    val pName = s.primaryName.ifBlank { "Primary Earner" }
    val sName = s.spouseName.ifBlank { "Partner" }

    // 1. Current Balances & Emergency Reserve
    item {
        SettingsGroupCard(
            title = "Balances & Cash Reserve",
            subtitle = "Liquid ${fmtCompact(snap.investedLiquidTotal)} · Pension ${fmtCompact(snap.pensionTotal)}",
            initiallyExpanded = false,
            badgeText = fmtCZK(snap.netWorthTotal),
            badgeColor = BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "balances",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(text = "$pName's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
            NumberSettingField(
                label = "Brokerage / ETF Portfolio",
                value = s.liquidPortfolioCurrent,
                onValueChange = { v -> patchSettings { it.copy(liquidPortfolioCurrent = v) } },
                testTagStr = "input_liquid_port",
                fieldKey = "v_bal"
            )
            NumberSettingField(
                label = "DIP Balance",
                value = s.dipBalanceCurrent,
                onValueChange = { v -> patchSettings { it.copy(dipBalanceCurrent = v) } },
                fieldKey = "v_bal"
            )
            NumberSettingField(
                label = "DPS Pension Balance",
                value = s.dpsBalanceCurrent,
                onValueChange = { v -> patchSettings { it.copy(dpsBalanceCurrent = v) } },
                fieldKey = "v_bal"
            )

            if (!s.isSingleHousehold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "$sName's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                NumberSettingField(
                    label = "Brokerage / ETF Portfolio",
                    value = s.eLiquidPortfolioCurrent,
                    onValueChange = { v -> patchSettings { it.copy(eLiquidPortfolioCurrent = v) } },
                    fieldKey = "e_bal"
                )
                NumberSettingField(
                    label = "DIP Balance",
                    value = s.eDipBalanceCurrent,
                    onValueChange = { v -> patchSettings { it.copy(eDipBalanceCurrent = v) } },
                    fieldKey = "e_bal"
                )
                NumberSettingField(
                    label = "DPS Pension Balance",
                    value = s.eDpsBalanceCurrent,
                    onValueChange = { v -> patchSettings { it.copy(eDpsBalanceCurrent = v) } },
                    fieldKey = "e_bal"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = "Cash & Emergency Reserve", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            NumberSettingField(
                label = "Liquid Bank & Savings Accounts",
                value = s.emergencyReserveCurrent,
                onValueChange = { v -> patchSettings { it.copy(emergencyReserveCurrent = v) } }
            )
            if (s.emergencyReserveMode == "Target") {
                NumberSettingField(
                    label = "Target Reserve Threshold (Manual)",
                    value = s.emergencyReserveTarget,
                    onValueChange = { v -> patchSettings { it.copy(emergencyReserveTarget = v) } }
                )
            } else {
                val dynamicTarget = FinancialEngine.effectiveEmergencyReserveTarget(s)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Dynamic Reserve Target (${s.emergencyReserveMode})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${s.emergencyReserveMode.removeSuffix("M")} months of baseline living costs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = fmtCZK(dynamicTarget),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Runway Calculation Mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ChipWrapRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("3M" to "3 months", "6M" to "6 months", "9M" to "9 months", "12M" to "12 months", "Target" to "Target").forEach { (mode, labelText) ->
                    FilterChip(
                        selected = s.emergencyReserveMode == mode,
                        onClick = { patchSettings { it.copy(emergencyReserveMode = mode) } },
                        label = {
                            Text(
                                text = labelText,
                                fontSize = 11.sp,
                                maxLines = 2,
                                softWrap = true,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                            selectedLabelColor = BrandTeal
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val isManualReserveMonthly = s.emergencyReserveMonthlyOverride.isFinite() && s.emergencyReserveMonthlyOverride >= 0.0
            val autoMonthlyPreview = FinancialEngine.autoEmergencyReserveContribution(s)
            val resolvedTarget = FinancialEngine.effectiveEmergencyReserveTarget(s)
            Text(
                text = "Monthly Reserve Contribution",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ChipWrapRow(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = !isManualReserveMonthly,
                    onClick = { patchSettings { it.copy(emergencyReserveMonthlyOverride = -1.0) } },
                    label = { Text("Auto (24-month gap)", fontSize = 11.sp, maxLines = 2, softWrap = true, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                        selectedLabelColor = BrandTeal
                    )
                )
                FilterChip(
                    selected = isManualReserveMonthly,
                    onClick = {
                        if (!isManualReserveMonthly) {
                            patchSettings { it.copy(emergencyReserveMonthlyOverride = kotlin.math.round(FinancialEngine.autoEmergencyReserveContribution(it))) }
                        }
                    },
                    label = { Text("Manual", fontSize = 11.sp, maxLines = 2, softWrap = true, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.16f),
                        selectedLabelColor = BrandTeal
                    )
                )
            }
            if (isManualReserveMonthly) {
                NumberSettingField(
                    label = "Monthly Emergency Reserve Contribution (Manual)",
                    value = s.emergencyReserveMonthlyOverride,
                    onValueChange = { v -> patchSettings { it.copy(emergencyReserveMonthlyOverride = v.coerceAtLeast(0.0)) } }
                )
            } else {
                Text(
                    text = if (autoMonthlyPreview > 0.0) {
                        "Auto: (${fmtCZK(resolvedTarget)} target − ${fmtCZK(s.emergencyReserveCurrent)}) ÷ 24 months = ${fmtCZK(autoMonthlyPreview)}/month"
                    } else {
                        "Auto: 0 Kč/month (${fmtCZK(resolvedTarget)} target 100% funded)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 2. Monthly Investing
    item {
        SettingsGroupCard(
            title = "Monthly Investing",
            subtitle = "ETF ${fmtCompact(s.portuDcaMonthly + if (s.isSingleHousehold) 0.0 else s.ePortuDcaMonthly)} · Pension ${fmtCompact(s.dipContributionMonthly + s.dpsOwnContributionMonthly + if (s.isSingleHousehold) 0.0 else (s.eDipContributionMonthly + s.eDpsOwnContributionMonthly))}",
            initiallyExpanded = false,
            badgeText = fmtCompact(snap.monthlyTotalInvesting) + "/month",
            badgeColor = GoodGreen,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "dca",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Text(text = "$pName's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
            NumberSettingField(
                label = "Monthly Brokerage ETF DCA",
                value = s.portuDcaMonthly,
                onValueChange = { v -> patchSettings { it.copy(portuDcaMonthly = v) } },
                fieldKey = "v_dca"
            )
            NumberSettingField(
                label = "DIP Monthly Contribution",
                value = s.dipContributionMonthly,
                onValueChange = { v -> patchSettings { it.copy(dipContributionMonthly = v) } },
                fieldKey = "v_dca"
            )
            NumberSettingField(
                label = "DPS Monthly Own Contribution",
                value = s.dpsOwnContributionMonthly,
                onValueChange = { v -> patchSettings { it.copy(dpsOwnContributionMonthly = v) } },
                fieldKey = "v_dca"
            )
            NumberSettingField(
                label = "Employer Pension Match (Kč monthly)",
                value = s.employerRetirementMonthly,
                onValueChange = { v -> patchSettings { it.copy(employerRetirementMonthly = v) } },
                fieldKey = "v_dca"
            )

            if (!s.isSingleHousehold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "$sName's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                NumberSettingField(
                    label = "Monthly Brokerage ETF DCA",
                    value = s.ePortuDcaMonthly,
                    onValueChange = { v -> patchSettings { it.copy(ePortuDcaMonthly = v) } },
                    fieldKey = "e_dca"
                )
                NumberSettingField(
                    label = "DIP Monthly Contribution",
                    value = s.eDipContributionMonthly,
                    onValueChange = { v -> patchSettings { it.copy(eDipContributionMonthly = v) } },
                    fieldKey = "e_dca"
                )
                NumberSettingField(
                    label = "DPS Monthly Contribution",
                    value = s.eDpsOwnContributionMonthly,
                    onValueChange = { v -> patchSettings { it.copy(eDpsOwnContributionMonthly = v) } },
                    fieldKey = "e_dca"
                )
                NumberSettingField(
                    label = "Employer Pension Match (Kč monthly)",
                    value = s.eEmployerRetirementMonthly,
                    onValueChange = { v -> patchSettings { it.copy(eEmployerRetirementMonthly = v) } },
                    fieldKey = "e_dca"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NumberSettingField(
                label = "Annual DCA Contribution Growth (%)",
                value = s.dcaAnnualGrowthPct,
                onValueChange = { v -> patchSettings { it.copy(dcaAnnualGrowthPct = v) } }
            )
        }
    }

    // 3. Targets & Market Assumptions
    item {
        SettingsGroupCard(
            title = "Targets & Assumptions",
            subtitle = "Return ${fmtPct(s.portfolioNominalReturnPct)} · CPI ${fmtPct(s.cpiInflationPct)} · Pension age ${s.vStatePensionAge}",
            initiallyExpanded = false,
            badgeText = "${fmtPct(s.safeWithdrawalRatePct)} draw",
            badgeColor = BrandTeal,
            info = SettingsMetricInfos.swr,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "assumptions",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            NumberSettingField(
                label = "Base Planning Year",
                value = s.baseYear.toDouble(),
                minValue = 1950.0,
                maxValue = 2200.0,
                onValueChange = { yr ->
                    val y = yr.toInt()
                    patchSettings { cur ->
                        val deltaYears = y - cur.baseYear
                        val newAge = (cur.primaryAge + deltaYears).coerceIn(1, 120)
                        cur.copy(baseYear = y, primaryAge = newAge)
                    }
                }
            )
            NumberSettingField(
                label = "Expected CPI Inflation (%)",
                value = s.cpiInflationPct,
                minValue = -99.0,
                maxValue = 1000.0,
                onValueChange = { v -> patchSettings { it.copy(cpiInflationPct = v) } },
                quickStep = 0.1
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NumberSettingField(label = "Safe Withdrawal Rate SWR (%)", value = s.safeWithdrawalRatePct, minValue = 0.1, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(safeWithdrawalRatePct = v) } }, quickStep = 0.25)
            NumberSettingField(label = "FIRE Safety Capital Buffer (%)", value = s.safetyBufferPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(safetyBufferPct = v) } }, fieldKey = "safety_buffer")
            NumberSettingField(label = "Expected Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, minValue = -99.0, maxValue = 1000.0, onValueChange = { v -> patchSettings { it.copy(portfolioNominalReturnPct = v) } }, quickStep = 0.5)
            NumberSettingField(label = "Dividend Yield (%)", value = s.dividendYieldPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(dividendYieldPct = v) } })
            NumberSettingField(label = "Dividend Tax Rate (%)", value = s.dividendTaxRatePct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(dividendTaxRatePct = v) } })
            val taxDrag = FinancialEngine.dividendTaxDragPct(s)
            Text(
                text = "Net taxable return: ${String.format(java.util.Locale.ROOT, "%.2f%%", FinancialEngine.netTaxableNominalReturnPct(s))} (${String.format(java.util.Locale.ROOT, "%.2f%%", taxDrag)} annual dividend tax drag). Capital gains exempt after 3-year time test.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { v -> patchSettings { it.copy(dpsGrossReturnPct = v) } })
            val dpsFeeCap = s.dpsStatutoryFeeCapPct.coerceAtLeast(0.0)
            val dpsFeeCapLabel = String.format(java.util.Locale.ROOT, "%.2f", dpsFeeCap).trimEnd('0').trimEnd('.')
            NumberSettingField(label = "DPS Annual Management Fee (%, cap $dpsFeeCapLabel%)", value = s.dpsAnnualFeePct, minValue = 0.0, maxValue = maxOf(dpsFeeCap, 100.0), onValueChange = { v -> patchSettings { it.copy(dpsAnnualFeePct = v) } })
            NumberSettingField(label = "Manual FIRE Target Override (0 = auto)", value = s.fireTargetOverride, onValueChange = { v -> patchSettings { it.copy(fireTargetOverride = v) } })
            NumberSettingField(label = "Lifestyle Cost at FIRE (Kč monthly)", value = s.lifestyleCostAtFireMonthly, onValueChange = { v -> patchSettings { it.copy(lifestyleCostAtFireMonthly = v) } })
            if (s.lifestyleCostAtFireMonthly <= 0.0) {
                Text(
                    text = "Linked to current living expenses: ${fmtCZK(snap.monthlyLivingCosts)} monthly",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            NumberSettingField(label = "$pName's State Pension Monthly", value = s.vStatePensionMonthly, onValueChange = { v -> patchSettings { it.copy(vStatePensionMonthly = v) } }, fieldKey = "v_pension")
            NumberSettingField(label = "$pName's State Pension Age", value = s.vStatePensionAge.toDouble(), minValue = 40.0, maxValue = 90.0, onValueChange = { v -> patchSettings { it.copy(vStatePensionAge = v.toInt()) } }, fieldKey = "v_pension", quickStep = 1.0)
            if (!s.isSingleHousehold) {
                NumberSettingField(label = "$sName's State Pension Monthly", value = s.eStatePensionMonthly, onValueChange = { v -> patchSettings { it.copy(eStatePensionMonthly = v) } }, fieldKey = "e_pension")
                NumberSettingField(label = "$sName's State Pension Age", value = s.eStatePensionAge.toDouble(), minValue = 40.0, maxValue = 90.0, onValueChange = { v -> patchSettings { it.copy(eStatePensionAge = v.toInt()) } }, fieldKey = "e_pension", quickStep = 1.0)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monte Carlo Stochastic Risk Engine",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onShowInfo(SettingsMetricInfos.monteCarloFor(s.monteCarloN, s.monteCarloVolatilityPct, s.useHistoricalBootstrap)) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Monte Carlo Methodology Details",
                        tint = BrandTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            NumberSettingField(label = "Portfolio Annual Volatility (%)", value = s.monteCarloVolatilityPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(monteCarloVolatilityPct = v) } })
            NumberSettingField(label = "Simulation Runs", value = s.monteCarloN.toDouble(), minValue = 10.0, maxValue = 10000.0, onValueChange = { v -> patchSettings { it.copy(monteCarloN = v.toInt()) } })
            NumberSettingField(label = "Simulation RNG Seed", value = s.monteCarloSeed.toDouble(), minValue = 0.0, maxValue = 9999999.0, onValueChange = { v -> patchSettings { it.copy(monteCarloSeed = v.toLong()) } }, fieldKey = "mc_seed")
            NumberSettingField(label = "Retirement Horizon (years post-FIRE)", value = s.retirementHorizonYears.toDouble(), minValue = 1.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(retirementHorizonYears = v.toInt()) } })
            BooleanSettingField(
                label = "Use Historical 1970–2025 Bootstrap",
                checked = s.useHistoricalBootstrap,
                onCheckedChange = { v -> patchSettings { it.copy(useHistoricalBootstrap = v) } },
                info = SettingsMetricInfos.historicalBootstrap,
                onShowInfo = onShowInfo
            )
            BooleanSettingField(label = "Dynamic Spending Guardrails (Guyton-Klinger)", checked = s.guardrailsEnabled, onCheckedChange = { v -> patchSettings { it.copy(guardrailsEnabled = v) } })
            if (s.guardrailsEnabled) {
                NumberSettingField(label = "Capital Preservation Upper Trigger (+%)", value = s.guardrailsUpperThresholdPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(guardrailsUpperThresholdPct = v) } })
                NumberSettingField(label = "Preservation Spending Cut (%)", value = s.guardrailsCutPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(guardrailsCutPct = v) } })
                NumberSettingField(label = "Prosperity Lower Trigger (-%)", value = s.guardrailsLowerThresholdPct, minValue = 0.0, maxValue = 95.0, onValueChange = { v -> patchSettings { it.copy(guardrailsLowerThresholdPct = v) } })
                NumberSettingField(label = "Prosperity Spending Boost (%)", value = s.guardrailsBoostPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(guardrailsBoostPct = v) } })
                NumberSettingField(label = "Essential Spending Floor (%)", value = s.guardrailsFloorPct, minValue = 0.0, maxValue = 100.0, onValueChange = { v -> patchSettings { it.copy(guardrailsFloorPct = v) } })
            }
        }
    }

    // 4. Advanced: Czech Tax Shield & Statutory Engine
    item {
        SettingsGroupCard(
            title = "Czech Tax Relief",
            subtitle = "Tax ${fmtPct(s.taxRatePct)}/${fmtPct(s.taxRateSecondPct)} · DIP +${fmtCZK(state.taxReturnHelper.dipSaving)} yearly",
            initiallyExpanded = false,
            badgeText = "ADVANCED",
            badgeColor = BrandGold,
            info = SettingsMetricInfos.partnerCredit,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState,
            sectionKey = "tax_shield",
            focusedSectionKey = focusedSectionKey,
            focusedSectionTrigger = focusedSectionTrigger
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Tax Shield Summary",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        ColorPill(
                            text = "LIVE SUMMARY",
                            color = GoodGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!s.isSingleHousehold) {
                        TaxSummaryRow(
                            label = "$sName Income",
                            status = fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                            isGood = state.taxReturnHelper.spouseOwnIncome <= s.spouseIncomeLimitAnnual
                        )
                        TaxSummaryRow(
                            label = "$sName Tax Credit Eligible",
                            status = if (state.taxReturnHelper.spouseEligible) "Yes (+${fmtCZK(state.taxReturnHelper.spouseCredit)})" else "No",
                            isGood = state.taxReturnHelper.spouseEligible
                        )
                    }
                    val c1Age = s.baseYear - s.child1BirthYear
                    val c2Age = s.baseYear - s.child2BirthYear
                    val c1ActiveNow = s.child1Enabled && c1Age in 0 until s.childSupportUntilAge
                    val c2ActiveNow = s.child2Enabled && c2Age in 0 until s.childSupportUntilAge
                    TaxSummaryRow(
                        label = "Child 1 Tax Bonus",
                        status = when {
                            !s.child1Enabled -> "Disabled"
                            c1Age < 0 -> "Planned (${s.child1BirthYear})"
                            c1ActiveNow -> "+${fmtCZK(s.child1TaxBonusAnnual)} (${fmtCZK(s.child1TaxBonusAnnual / 12)} monthly)"
                            else -> "Aged out"
                        },
                        isGood = c1ActiveNow
                    )
                    TaxSummaryRow(
                        label = "Child 2 Tax Bonus",
                        status = when {
                            !s.child2Enabled -> "Disabled"
                            c2Age < 0 -> "Planned (${s.child2BirthYear})"
                            c2ActiveNow -> {
                                val child2EffectiveBonus = if (c1ActiveNow) s.child2TaxBonusAnnual else s.child1TaxBonusAnnual
                                "+${fmtCZK(child2EffectiveBonus)} (${fmtCZK(child2EffectiveBonus / 12)} monthly)"
                            }
                            else -> "Aged out"
                        },
                        isGood = c2ActiveNow
                    )
                    TaxSummaryRow(
                        label = "Total Child Tax Bonus",
                        status = "+${fmtCZK(state.taxReturnHelper.childBonus)}",
                        isGood = true
                    )
                    TaxSummaryRow(
                        label = "DIP & DPS Annual Tax Saving",
                        status = "+${fmtCZK(state.taxReturnHelper.dipSaving)}",
                        isGood = true
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(text = "Tax Rates & Brackets", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberSettingField(label = "Base Income Tax Rate (%)", value = s.taxRatePct, onValueChange = { v -> patchSettings { it.copy(taxRatePct = v) } })
            NumberSettingField(label = "Higher Bracket Tax Rate (%)", value = s.taxRateSecondPct, onValueChange = { v -> patchSettings { it.copy(taxRateSecondPct = v) } })
            NumberSettingField(label = "Higher Bracket Threshold Annual", value = s.taxSecondBracketThresholdAnnual, onValueChange = { v -> patchSettings { it.copy(taxSecondBracketThresholdAnnual = v) } })
            NumberSettingField(label = "Basic Taxpayer Credit Annual", value = s.taxpayerCreditAnnual, onValueChange = { v -> patchSettings { it.copy(taxpayerCreditAnnual = v) } })
            NumberSettingField(label = "Retirement Deduction Ceiling Annual", value = s.taxDeductionCeilingAnnual, onValueChange = { v -> patchSettings { it.copy(taxDeductionCeilingAnnual = v) } })
            if (!s.isSingleHousehold) {
                NumberSettingField(label = "$sName Tax Credit Annual", value = s.spouseTaxCreditAnnual, onValueChange = { v -> patchSettings { it.copy(spouseTaxCreditAnnual = v) } })
                NumberSettingField(label = "$sName Income Limit Annual", value = s.spouseIncomeLimitAnnual, onValueChange = { v -> patchSettings { it.copy(spouseIncomeLimitAnnual = v) } })
                BooleanSettingField(label = "Include $sName Tax Credit", checked = s.includeSpouseCredit, onCheckedChange = { v -> patchSettings { it.copy(includeSpouseCredit = v) } })
            }
            BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { v -> patchSettings { it.copy(hasChildUnder3 = v) } })
            NumberSettingField(label = "Minimum Wage Monthly", value = s.minWageMonthly, onValueChange = { v -> patchSettings { it.copy(minWageMonthly = v) } })
            NumberSettingField(label = "Child 3+ Tax Bonus Annual", value = s.child3PlusTaxBonusAnnual, onValueChange = { v -> patchSettings { it.copy(child3PlusTaxBonusAnnual = v) } })

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(text = "Statutory DPS Pension Subsidies", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberSettingField(label = "DPS Tax Deduction Floor (Kč monthly)", value = s.dpsDeductionThresholdMonthly, onValueChange = { v -> patchSettings { it.copy(dpsDeductionThresholdMonthly = v) } })
            NumberSettingField(label = "DPS Minimum Subsidy Deposit (Kč monthly)", value = s.dpsMinDepositForSubsidy, onValueChange = { v -> patchSettings { it.copy(dpsMinDepositForSubsidy = v) } })
            NumberSettingField(label = "DPS Maximum Standard Subsidy (Kč monthly)", value = s.dpsStandardSubsidyMaxMonthly, onValueChange = { v -> patchSettings { it.copy(dpsStandardSubsidyMaxMonthly = v) } })
            NumberSettingField(label = "DPS Standard Subsidy Rate (%)", value = s.dpsSubsidyRateStandardPct, onValueChange = { v -> patchSettings { it.copy(dpsSubsidyRateStandardPct = v) } })
            NumberSettingField(label = "DPS Youth Age Limit (Years)", value = s.dpsYouthAgeLimit.toDouble(), onValueChange = { v -> patchSettings { it.copy(dpsYouthAgeLimit = v.toInt()) } })
            NumberSettingField(label = "DPS Maximum Youth Subsidy (Kč monthly)", value = s.dpsYouthSubsidyMaxMonthly, onValueChange = { v -> patchSettings { it.copy(dpsYouthSubsidyMaxMonthly = v) } })
            NumberSettingField(label = "DPS Youth Subsidy Rate (%)", value = s.dpsSubsidyRateYouthPct, onValueChange = { v -> patchSettings { it.copy(dpsSubsidyRateYouthPct = v) } })
            NumberSettingField(label = "DPS Statutory Fee Cap (%)", value = s.dpsStatutoryFeeCapPct, minValue = 0.0, onValueChange = { cap -> val safeCap = cap.coerceAtLeast(0.0); patchSettings { cur -> cur.copy(dpsStatutoryFeeCapPct = safeCap, dpsAnnualFeePct = cur.dpsAnnualFeePct.coerceAtMost(safeCap)) } })
            NumberSettingField(label = "Employer Retirement Exemption Cap (Kč yearly)", value = s.employerRetirementExemptionAnnual, onValueChange = { v -> patchSettings { it.copy(employerRetirementExemptionAnnual = v) } })
            NumberSettingField(label = "Child Bonus Minimum Wage Multiplier", value = s.childBonusMinWageMultiplier, onValueChange = { v -> patchSettings { it.copy(childBonusMinWageMultiplier = v) } })
            NumberSettingField(label = "Parental Allowance Pot (Pre-Cutoff Kč)", value = s.parentalAllowanceTotalBeforeCutoff, onValueChange = { v -> patchSettings { it.copy(parentalAllowanceTotalBeforeCutoff = v) } })
            NumberSettingField(label = "Parental Allowance Pot (Post-Cutoff Kč)", value = s.parentalAllowanceTotalFromCutoff, onValueChange = { v -> patchSettings { it.copy(parentalAllowanceTotalFromCutoff = v) } })
            NumberSettingField(label = "Parental Allowance Cutoff Year", value = s.parentalAllowanceCutoffYear.toDouble(), onValueChange = { v -> patchSettings { it.copy(parentalAllowanceCutoffYear = v.toInt()) } })
        }
    }
}

@Composable
fun TaxSummaryRow(
    label: String,
    status: String,
    isGood: Boolean,
    pillColor: androidx.compose.ui.graphics.Color? = null
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
        com.example.ui.components.ColorPill(
            text = status,
            color = pillColor ?: if (isGood) GoodGreen else BadRed,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            horizontalPadding = 7.dp,
            verticalPadding = 2.5.dp
        )
    }
}
