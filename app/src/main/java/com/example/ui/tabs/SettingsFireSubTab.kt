package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.data.VACLAV_BIRTH_YEAR
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
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
    onShowInfo: (MetricInfo) -> Unit
) {
    val s = state.settings

    // 1. Current Balances & Emergency Reserve
    item {
        SettingsGroupCard(
            title = "Current Asset Balances & Cash Reserve",
            subtitle = "Liquid ${fmtCompact(s.liquidPortfolioCurrent + if (s.isSingleHousehold) 0.0 else s.eLiquidPortfolioCurrent)} · Pension ${fmtCompact(s.dipBalanceCurrent + s.dpsBalanceCurrent + if (s.isSingleHousehold) 0.0 else (s.eDipBalanceCurrent + s.eDpsBalanceCurrent))} · Cash ${fmtCompact(s.emergencyReserveCurrent)}",
            initiallyExpanded = false,
            badgeText = fmtCZK(state.netWorthTotal),
            badgeColor = BrandTeal,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            Text(text = "Václav's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
            NumberSettingField(
                label = "Brokerage / ETF Portfolio",
                value = s.liquidPortfolioCurrent,
                onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                testTagStr = "input_liquid_port"
            )
            NumberSettingField(
                label = "DIP Balance",
                value = s.dipBalanceCurrent,
                onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) }
            )
            NumberSettingField(
                label = "DPS Pension Balance",
                value = s.dpsBalanceCurrent,
                onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) }
            )

            if (!s.isSingleHousehold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Eleonora's Balances", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                NumberSettingField(
                    label = "Brokerage / ETF Portfolio",
                    value = s.eLiquidPortfolioCurrent,
                    onValueChange = { onUpdateSettings(s.copy(eLiquidPortfolioCurrent = it)) }
                )
                NumberSettingField(
                    label = "DIP Balance",
                    value = s.eDipBalanceCurrent,
                    onValueChange = { onUpdateSettings(s.copy(eDipBalanceCurrent = it)) }
                )
                NumberSettingField(
                    label = "DPS Pension Balance",
                    value = s.eDpsBalanceCurrent,
                    onValueChange = { onUpdateSettings(s.copy(eDpsBalanceCurrent = it)) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = "Cash & Emergency Reserve", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            NumberSettingField(
                label = "Liquid Bank & Savings Accounts",
                value = s.emergencyReserveCurrent,
                onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
            )
            if (s.emergencyReserveMode == "Target") {
                NumberSettingField(
                    label = "Target Reserve Threshold (Manual)",
                    value = s.emergencyReserveTarget,
                    onValueChange = { onUpdateSettings(s.copy(emergencyReserveTarget = it)) }
                )
            } else {
                val dynamicTarget = FinancialEngine.effectiveEmergencyReserveTarget(s)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Dynamic Reserve Target (${s.emergencyReserveMode})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "${s.emergencyReserveMode.removeSuffix("M")} months of baseline living costs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = fmtCZK(dynamicTarget),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("3M", "6M", "9M", "12M", "Target").forEach { mode ->
                    FilterChip(
                        selected = s.emergencyReserveMode == mode,
                        onClick = { onUpdateSettings(s.copy(emergencyReserveMode = mode)) },
                        label = {
                            Text(
                                text = mode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

    // 2. Monthly Investment Flows (DCA)
    item {
        SettingsGroupCard(
            title = "Monthly Investment Flows (DCA)",
            subtitle = "ETF ${fmtCZK(s.portuDcaMonthly + if (s.isSingleHousehold) 0.0 else s.ePortuDcaMonthly)} · DIP/DPS ${fmtCZK(s.dipContributionMonthly + s.dpsOwnContributionMonthly + if (s.isSingleHousehold) 0.0 else (s.eDipContributionMonthly + s.eDpsOwnContributionMonthly))} · Growth ${fmtPct(s.dcaAnnualGrowthPct)}/yr",
            initiallyExpanded = false,
            badgeText = fmtCZK(state.investMonthlyTotal) + "/mo",
            badgeColor = GoodGreen,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            Text(text = "Václav's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandTeal))
            NumberSettingField(
                label = "Monthly Brokerage ETF DCA",
                value = s.portuDcaMonthly,
                onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) }
            )
            NumberSettingField(
                label = "DIP Monthly Contribution",
                value = s.dipContributionMonthly,
                onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) }
            )
            NumberSettingField(
                label = "DPS Monthly Own Contribution",
                value = s.dpsOwnContributionMonthly,
                onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) }
            )
            NumberSettingField(
                label = "Employer Pension Match (CZK/mo)",
                value = s.employerRetirementMonthly,
                onValueChange = { onUpdateSettings(s.copy(employerRetirementMonthly = it)) }
            )

            if (!s.isSingleHousehold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Eleonora's DCA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BrandGold))
                NumberSettingField(
                    label = "Monthly Brokerage ETF DCA",
                    value = s.ePortuDcaMonthly,
                    onValueChange = { onUpdateSettings(s.copy(ePortuDcaMonthly = it)) }
                )
                NumberSettingField(
                    label = "DIP Monthly Contribution",
                    value = s.eDipContributionMonthly,
                    onValueChange = { onUpdateSettings(s.copy(eDipContributionMonthly = it)) }
                )
                NumberSettingField(
                    label = "DPS Monthly Contribution",
                    value = s.eDpsOwnContributionMonthly,
                    onValueChange = { onUpdateSettings(s.copy(eDpsOwnContributionMonthly = it)) }
                )
                NumberSettingField(
                    label = "Employer Pension Match (CZK/mo)",
                    value = s.eEmployerRetirementMonthly,
                    onValueChange = { onUpdateSettings(s.copy(eEmployerRetirementMonthly = it)) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NumberSettingField(
                label = "Annual DCA Contribution Growth (%)",
                value = s.dcaAnnualGrowthPct,
                onValueChange = { onUpdateSettings(s.copy(dcaAnnualGrowthPct = it)) }
            )
        }
    }

    // 3. FIRE Targets & Market Assumptions
    item {
        SettingsGroupCard(
            title = "Planning Assumptions & FIRE Targets",
            subtitle = "Return ${fmtPct(s.portfolioNominalReturnPct)} · CPI ${fmtPct(s.cpiInflationPct)} · SWR ${fmtPct(s.safeWithdrawalRatePct)} · Pension age ${s.vStatePensionAge}",
            initiallyExpanded = false,
            badgeText = "${fmtPct(s.safeWithdrawalRatePct)} SWR",
            badgeColor = BrandTeal,
            info = SettingsMetricInfos.swr,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            NumberSettingField(
                label = "Base Planning Year",
                value = s.baseYear.toDouble(),
                minValue = 2000.0,
                maxValue = 2200.0,
                onValueChange = { yr ->
                    val y = yr.toInt()
                    val newAge = (y - VACLAV_BIRTH_YEAR).coerceIn(15, 80)
                    onUpdateSettings(s.copy(baseYear = y, primaryAge = newAge))
                }
            )
            NumberSettingField(
                label = "Expected CPI Inflation (%)",
                value = s.cpiInflationPct,
                minValue = 0.0,
                maxValue = 20.0,
                onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NumberSettingField(label = "Safe Withdrawal Rate SWR (%)", value = s.safeWithdrawalRatePct, minValue = 0.5, maxValue = 15.0, onValueChange = { onUpdateSettings(s.copy(safeWithdrawalRatePct = it)) })
            NumberSettingField(label = "Expected Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, minValue = -20.0, maxValue = 30.0, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
            NumberSettingField(label = "Dividend Yield (%)", value = s.dividendYieldPct, minValue = 0.0, maxValue = 15.0, onValueChange = { onUpdateSettings(s.copy(dividendYieldPct = it)) })
            NumberSettingField(label = "Dividend Tax Rate (%)", value = s.dividendTaxRatePct, minValue = 0.0, maxValue = 35.0, onValueChange = { onUpdateSettings(s.copy(dividendTaxRatePct = it)) })
            val taxDrag = FinancialEngine.dividendTaxDragPct(s)
            Text(
                text = "Net taxable return: ${String.format(java.util.Locale.getDefault(), "%.2f%%", FinancialEngine.netTaxableNominalReturnPct(s))} (${String.format(java.util.Locale.getDefault(), "%.2f%%", taxDrag)}/yr dividend drag). Capital gains 0% after 3-year time test.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
            NumberSettingField(label = "DPS Annual Management Fee (%, cap 0.5%)", value = s.dpsAnnualFeePct, minValue = 0.0, maxValue = 5.0, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
            NumberSettingField(label = "Manual FIRE Target Override (0 = auto)", value = s.fireTargetOverride, onValueChange = { onUpdateSettings(s.copy(fireTargetOverride = it)) })
            NumberSettingField(label = "Lifestyle Cost at FIRE (CZK/mo)", value = s.lifestyleCostAtFireMonthly, onValueChange = { onUpdateSettings(s.copy(lifestyleCostAtFireMonthly = it)) })
            if (s.lifestyleCostAtFireMonthly <= 0.0) {
                Text(
                    text = "Linked to current living expenses: ${fmtCZK(state.totalLivingCostMonthly)}/mo",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            NumberSettingField(label = "Václav's State Pension Monthly", value = s.vStatePensionMonthly, onValueChange = { onUpdateSettings(s.copy(vStatePensionMonthly = it)) })
            NumberSettingField(label = "Václav's State Pension Age", value = s.vStatePensionAge.toDouble(), minValue = 55.0, maxValue = 75.0, onValueChange = { onUpdateSettings(s.copy(vStatePensionAge = it.toInt())) })
            if (!s.isSingleHousehold) {
                NumberSettingField(label = "Eleonora's State Pension Monthly", value = s.eStatePensionMonthly, onValueChange = { onUpdateSettings(s.copy(eStatePensionMonthly = it)) })
                NumberSettingField(label = "Eleonora's State Pension Age", value = s.eStatePensionAge.toDouble(), minValue = 55.0, maxValue = 75.0, onValueChange = { onUpdateSettings(s.copy(eStatePensionAge = it.toInt())) })
            }
        }
    }

    // 4. Advanced: Czech Tax Shield & Statutory Engine
    item {
        SettingsGroupCard(
            title = "Czech Tax Shield & Statutory Engine",
            subtitle = "Tax ${fmtPct(s.taxRatePct)}/${fmtPct(s.taxRateSecondPct)} · DIP Shield +${fmtCZK(state.taxReturnHelper.dipSaving)}/yr · Monte Carlo ${s.monteCarloN} runs",
            initiallyExpanded = false,
            badgeText = "ADVANCED",
            badgeColor = BrandGold,
            info = SettingsMetricInfos.partnerCredit,
            onShowInfo = onShowInfo,
            externalExpandTrigger = expandTrigger,
            externalExpandState = expandTargetState
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
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
                            label = "Eleonora Income",
                            status = fmtCZK(state.taxReturnHelper.spouseOwnIncome),
                            isGood = state.taxReturnHelper.spouseOwnIncome <= s.spouseIncomeLimitAnnual
                        )
                        TaxSummaryRow(
                            label = "Eleonora Tax Credit Eligible",
                            status = if (state.taxReturnHelper.spouseEligible) "Yes (+${fmtCZK(state.taxReturnHelper.spouseCredit)})" else "No",
                            isGood = state.taxReturnHelper.spouseEligible
                        )
                    }
                    TaxSummaryRow(
                        label = "Child 1 Tax Bonus",
                        status = if (s.child1Enabled) "+${fmtCZK(s.child1TaxBonusAnnual)} (${fmtCZK(s.child1TaxBonusAnnual / 12)} monthly)" else "Disabled",
                        isGood = s.child1Enabled
                    )
                    TaxSummaryRow(
                        label = "Child 2 Tax Bonus",
                        status = if (s.child2Enabled) "+${fmtCZK(s.child2TaxBonusAnnual)} (${fmtCZK(s.child2TaxBonusAnnual / 12)} monthly)" else "Disabled",
                        isGood = s.child2Enabled
                    )
                    TaxSummaryRow(
                        label = "Total Child Tax Bonus",
                        status = "+${fmtCZK(state.taxReturnHelper.childBonus)}",
                        isGood = true
                    )
                    TaxSummaryRow(
                        label = "DIP Annual Tax Saving",
                        status = "+${fmtCZK(state.taxReturnHelper.dipSaving)}",
                        isGood = true
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(text = "Tax Rates & Brackets", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberSettingField(label = "Base Income Tax Rate (%)", value = s.taxRatePct, onValueChange = { onUpdateSettings(s.copy(taxRatePct = it)) })
            NumberSettingField(label = "Higher Bracket Tax Rate (%)", value = s.taxRateSecondPct, onValueChange = { onUpdateSettings(s.copy(taxRateSecondPct = it)) })
            NumberSettingField(label = "Higher Bracket Threshold Annual", value = s.taxSecondBracketThresholdAnnual, onValueChange = { onUpdateSettings(s.copy(taxSecondBracketThresholdAnnual = it)) })
            NumberSettingField(label = "Basic Taxpayer Credit Annual", value = s.taxpayerCreditAnnual, onValueChange = { onUpdateSettings(s.copy(taxpayerCreditAnnual = it)) })
            NumberSettingField(label = "Retirement Deduction Ceiling Annual", value = s.taxDeductionCeilingAnnual, onValueChange = { onUpdateSettings(s.copy(taxDeductionCeilingAnnual = it)) })
            if (!s.isSingleHousehold) {
                NumberSettingField(label = "Eleonora Tax Credit Annual", value = s.spouseTaxCreditAnnual, onValueChange = { onUpdateSettings(s.copy(spouseTaxCreditAnnual = it)) })
                NumberSettingField(label = "Eleonora Income Limit Annual", value = s.spouseIncomeLimitAnnual, onValueChange = { onUpdateSettings(s.copy(spouseIncomeLimitAnnual = it)) })
                BooleanSettingField(label = "Include Eleonora Tax Credit", checked = s.includeSpouseCredit, onCheckedChange = { onUpdateSettings(s.copy(includeSpouseCredit = it)) })
            }
            BooleanSettingField(label = "Has Child Under 3", checked = s.hasChildUnder3, onCheckedChange = { onUpdateSettings(s.copy(hasChildUnder3 = it)) })
            NumberSettingField(label = "Min Wage Monthly", value = s.minWageMonthly, onValueChange = { onUpdateSettings(s.copy(minWageMonthly = it)) })
            NumberSettingField(label = "Child 3+ Tax Bonus Annual", value = s.child3PlusTaxBonusAnnual, onValueChange = { onUpdateSettings(s.copy(child3PlusTaxBonusAnnual = it)) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(text = "Statutory DPS Pension Subsidies", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberSettingField(label = "DPS Tax Deduction Floor (CZK/mo)", value = s.dpsDeductionThresholdMonthly, onValueChange = { onUpdateSettings(s.copy(dpsDeductionThresholdMonthly = it)) })
            NumberSettingField(label = "DPS Min Subsidy Deposit (CZK/mo)", value = s.dpsMinDepositForSubsidy, onValueChange = { onUpdateSettings(s.copy(dpsMinDepositForSubsidy = it)) })
            NumberSettingField(label = "DPS Max Standard Subsidy (CZK/mo)", value = s.dpsStandardSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsStandardSubsidyMaxMonthly = it)) })
            NumberSettingField(label = "DPS Standard Subsidy Rate (%)", value = s.dpsSubsidyRateStandardPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateStandardPct = it)) })
            NumberSettingField(label = "DPS Youth Age Limit (Years)", value = s.dpsYouthAgeLimit.toDouble(), onValueChange = { onUpdateSettings(s.copy(dpsYouthAgeLimit = it.toInt())) })
            NumberSettingField(label = "DPS Max Youth Subsidy (CZK/mo)", value = s.dpsYouthSubsidyMaxMonthly, onValueChange = { onUpdateSettings(s.copy(dpsYouthSubsidyMaxMonthly = it)) })
            NumberSettingField(label = "DPS Youth Subsidy Rate (%)", value = s.dpsSubsidyRateYouthPct, onValueChange = { onUpdateSettings(s.copy(dpsSubsidyRateYouthPct = it)) })
            NumberSettingField(label = "DPS Statutory Fee Cap (%)", value = s.dpsStatutoryFeeCapPct, onValueChange = { onUpdateSettings(s.copy(dpsStatutoryFeeCapPct = it)) })
            NumberSettingField(label = "Employer Retirement Exemption Cap (CZK/yr)", value = s.employerRetirementExemptionAnnual, onValueChange = { onUpdateSettings(s.copy(employerRetirementExemptionAnnual = it)) })
            NumberSettingField(label = "Child Bonus Min Wage Multiplier", value = s.childBonusMinWageMultiplier, onValueChange = { onUpdateSettings(s.copy(childBonusMinWageMultiplier = it)) })
            NumberSettingField(label = "Parental Allowance Pot (Pre-Cutoff CZK)", value = s.parentalAllowanceTotalBeforeCutoff, onValueChange = { onUpdateSettings(s.copy(parentalAllowanceTotalBeforeCutoff = it)) })
            NumberSettingField(label = "Parental Allowance Pot (Post-Cutoff CZK)", value = s.parentalAllowanceTotalFromCutoff, onValueChange = { onUpdateSettings(s.copy(parentalAllowanceTotalFromCutoff = it)) })
            NumberSettingField(label = "Parental Allowance Cutoff Year", value = s.parentalAllowanceCutoffYear.toDouble(), onValueChange = { onUpdateSettings(s.copy(parentalAllowanceCutoffYear = it.toInt())) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(text = "Monte Carlo Stochastic Risk Engine", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberSettingField(label = "Portfolio Annual Volatility (%)", value = s.monteCarloVolatilityPct, onValueChange = { onUpdateSettings(s.copy(monteCarloVolatilityPct = it)) })
            NumberSettingField(label = "Simulation Runs", value = s.monteCarloN.toDouble(), minValue = 100.0, maxValue = 400.0, onValueChange = { onUpdateSettings(s.copy(monteCarloN = it.toInt())) })
            NumberSettingField(label = "Retirement Horizon (years post-FIRE)", value = s.retirementHorizonYears.toDouble(), minValue = 10.0, maxValue = 60.0, onValueChange = { onUpdateSettings(s.copy(retirementHorizonYears = it.toInt())) })
            BooleanSettingField(
                label = "Use Historical 1970–2025 Bootstrap",
                checked = s.useHistoricalBootstrap,
                onCheckedChange = { onUpdateSettings(s.copy(useHistoricalBootstrap = it)) },
                info = SettingsMetricInfos.historicalBootstrap,
                onShowInfo = onShowInfo
            )
            BooleanSettingField(label = "Dynamic Spending Guardrails (Guyton-Klinger)", checked = s.guardrailsEnabled, onCheckedChange = { onUpdateSettings(s.copy(guardrailsEnabled = it)) })
            if (s.guardrailsEnabled) {
                NumberSettingField(label = "Capital Preservation Upper Trigger (+%)", value = s.guardrailsUpperThresholdPct, minValue = 5.0, maxValue = 50.0, onValueChange = { onUpdateSettings(s.copy(guardrailsUpperThresholdPct = it)) })
                NumberSettingField(label = "Preservation Spending Cut (%)", value = s.guardrailsCutPct, minValue = 5.0, maxValue = 30.0, onValueChange = { onUpdateSettings(s.copy(guardrailsCutPct = it)) })
                NumberSettingField(label = "Prosperity Lower Trigger (-%)", value = s.guardrailsLowerThresholdPct, minValue = 5.0, maxValue = 50.0, onValueChange = { onUpdateSettings(s.copy(guardrailsLowerThresholdPct = it)) })
                NumberSettingField(label = "Prosperity Spending Boost (%)", value = s.guardrailsBoostPct, minValue = 5.0, maxValue = 30.0, onValueChange = { onUpdateSettings(s.copy(guardrailsBoostPct = it)) })
                NumberSettingField(label = "Essential Spending Floor (%)", value = s.guardrailsFloorPct, minValue = 40.0, maxValue = 90.0, onValueChange = { onUpdateSettings(s.copy(guardrailsFloorPct = it)) })
            }
        }
    }
}
