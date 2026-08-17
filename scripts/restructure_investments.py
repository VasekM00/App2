import re

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'r') as f:
    content = f.read()

old_tab_0_start = """                0 -> {
                    // Portfolio & Base Settings
                    SettingsGroupCard(title = "Portfolio & Base Settings") {
                        NumberSettingField(
                            label = "Liquid Portfolio Current (CZK)",
                            value = s.liquidPortfolioCurrent,
                            onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                            testTagStr = "input_liquid_port"
                        )
                        NumberSettingField(
                            label = "Emergency Reserve Current (CZK)",
                            value = s.emergencyReserveCurrent,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Emergency Reserve Target (CZK)",
                            value = s.emergencyReserveTarget,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveTarget = it)) }
                        )
                        NumberSettingField(
                            label = "Base Year",
                            value = s.baseYear.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(baseYear = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "Primary Age",
                            value = s.primaryAge.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(primaryAge = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "CPI Inflation (%)",
                            value = s.cpiInflationPct,
                            onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                        )
                    }"""

new_tab_0 = """                0 -> {
                    // Base Settings
                    SettingsGroupCard(title = "General Settings") {
                        NumberSettingField(
                            label = "Base Year",
                            value = s.baseYear.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(baseYear = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "Primary Age",
                            value = s.primaryAge.toDouble(),
                            onValueChange = { onUpdateSettings(s.copy(primaryAge = it.toInt())) }
                        )
                        NumberSettingField(
                            label = "CPI Inflation (%)",
                            value = s.cpiInflationPct,
                            onValueChange = { onUpdateSettings(s.copy(cpiInflationPct = it)) }
                        )
                    }"""

content = content.replace(old_tab_0_start, new_tab_0)

old_tab_2 = """                2 -> {
                    // Investment Balances & Contributions
                    SettingsGroupCard(title = "Investment Balances & Setup") {
                        NumberSettingField(label = "DPS Balance Current (CZK)", value = s.dpsBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) })
                        NumberSettingField(label = "DIP Balance Current (CZK)", value = s.dipBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) })
                        NumberSettingField(label = "Portu Monthly DCA (CZK)", value = s.portuDcaMonthly, onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) })
                        NumberSettingField(label = "Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
                        NumberSettingField(label = "DPS Own Contribution (CZK)", value = s.dpsOwnContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) })
                        NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
                        NumberSettingField(label = "DPS Annual Fee (%)", value = s.dpsAnnualFeePct, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
                        NumberSettingField(label = "DIP Monthly Contribution (CZK)", value = s.dipContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) })
                        NumberSettingField(label = "Employer Retirement Annual (CZK)", value = s.employerRetirementAnnual, onValueChange = { onUpdateSettings(s.copy(employerRetirementAnnual = it)) })
                    }
                }"""

new_tab_2 = """                2 -> {
                    // Personal Portfolio & Cash
                    SettingsGroupCard(title = "Personal Portfolio & Cash") {
                        NumberSettingField(
                            label = "Investment Portfolio Balance (CZK)",
                            value = s.liquidPortfolioCurrent,
                            onValueChange = { onUpdateSettings(s.copy(liquidPortfolioCurrent = it)) },
                            testTagStr = "input_liquid_port"
                        )
                        NumberSettingField(
                            label = "Savings & Bank Accounts (CZK)",
                            value = s.emergencyReserveCurrent,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveCurrent = it)) }
                        )
                        NumberSettingField(
                            label = "Savings / Reserve Target (CZK)",
                            value = s.emergencyReserveTarget,
                            onValueChange = { onUpdateSettings(s.copy(emergencyReserveTarget = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Retirement Accounts
                    SettingsGroupCard(title = "Retirement Accounts (DPS & DIP)") {
                        NumberSettingField(label = "DPS Balance Current (CZK)", value = s.dpsBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dpsBalanceCurrent = it)) })
                        NumberSettingField(label = "DIP Balance Current (CZK)", value = s.dipBalanceCurrent, onValueChange = { onUpdateSettings(s.copy(dipBalanceCurrent = it)) })
                        NumberSettingField(label = "DPS Own Contribution (CZK)", value = s.dpsOwnContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dpsOwnContributionMonthly = it)) })
                        NumberSettingField(label = "DIP Monthly Contribution (CZK)", value = s.dipContributionMonthly, onValueChange = { onUpdateSettings(s.copy(dipContributionMonthly = it)) })
                        NumberSettingField(label = "Employer Retirement Annual (CZK)", value = s.employerRetirementAnnual, onValueChange = { onUpdateSettings(s.copy(employerRetirementAnnual = it)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Investment Parameters
                    SettingsGroupCard(title = "Investment Parameters") {
                        NumberSettingField(label = "Portu / ETF Monthly DCA (CZK)", value = s.portuDcaMonthly, onValueChange = { onUpdateSettings(s.copy(portuDcaMonthly = it)) })
                        NumberSettingField(label = "Portfolio Nominal Return (%)", value = s.portfolioNominalReturnPct, onValueChange = { onUpdateSettings(s.copy(portfolioNominalReturnPct = it)) })
                        NumberSettingField(label = "DPS Gross Return (%)", value = s.dpsGrossReturnPct, onValueChange = { onUpdateSettings(s.copy(dpsGrossReturnPct = it)) })
                        NumberSettingField(label = "DPS Annual Fee (%)", value = s.dpsAnnualFeePct, onValueChange = { onUpdateSettings(s.copy(dpsAnnualFeePct = it)) })
                    }
                }"""

content = content.replace(old_tab_2, new_tab_2)

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'w') as f:
    f.write(content)

