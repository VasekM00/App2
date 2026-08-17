import re

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'r') as f:
    content = f.read()

portfolio_settings = """
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vaclav Income Settings
"""

content = content.replace("        // Vaclav Income Settings\n", portfolio_settings)

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'w') as f:
    f.write(content)
