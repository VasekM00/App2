import re

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'r') as f:
    content = f.read()

# Add imports
if 'androidx.compose.material3.ScrollableTabRow' not in content:
    content = content.replace('import androidx.compose.material3.SwitchDefaults\n', 'import androidx.compose.material3.SwitchDefaults\nimport androidx.compose.material3.ScrollableTabRow\nimport androidx.compose.material3.Tab\n')
if 'androidx.compose.runtime.mutableIntStateOf' not in content:
    content = content.replace('import androidx.compose.runtime.mutableStateOf\n', 'import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.mutableIntStateOf\n')

def extract_block(name, start_comment, end_comment=None):
    if end_comment:
        pattern = re.compile(f'({start_comment}.*?){end_comment}', re.DOTALL)
        match = pattern.search(content)
        if match:
            return match.group(1).strip()
    else:
        # up to the end of the column
        pattern = re.compile(f'({start_comment}.*?)    }}\n\n    if \(showResetDialog\)', re.DOTALL)
        match = pattern.search(content)
        if match:
            return match.group(1).strip()
    return ""

tax_summary = extract_block("Tax Summary", "        // Tax Return Helper Summary Card", "        // Portfolio & General Settings")
portfolio = extract_block("Portfolio", "        // Portfolio & General Settings", "        // Vaclav Income Settings")
v_income = extract_block("Vaclav", "        // Vaclav Income Settings", "        // Eleonora Income Settings")
e_income = extract_block("Eleonora", "        // Eleonora Income Settings", "        // Gifts, Savings & Lump Sums")
gifts = extract_block("Gifts", "        // Gifts, Savings & Lump Sums", "        // Investment Balances & Contributions")
invest = extract_block("Invest", "        // Investment Balances & Contributions", "        // FIRE Target Settings")
fire = extract_block("FIRE", "        // FIRE Target Settings", "        // Tax Parameters")
tax_params = extract_block("Tax Params", "        // Tax Parameters", "        // Child Settings & Expenses")
child = extract_block("Child", "        // Child Settings & Expenses", "        // Living Costs Settings")
living = extract_block("Living", "        // Living Costs Settings", "        // Reset Card")
reset = extract_block("Reset", "        // Reset Card")


new_ui = f"""    var selectedTab by remember {{ mutableIntStateOf(0) }}
    val tabs = listOf("General", "Income", "Investments", "Taxes & Family", "Expenses", "Data")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {{
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {{
            tabs.forEachIndexed {{ index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {{ selectedTab = index }},
                    text = {{ Text(title, fontWeight = FontWeight.SemiBold) }},
                    modifier = Modifier.testTag("settings_tab_btn_$index")
                )
            }}
        }}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {{
            when (selectedTab) {{
                0 -> {{
                    {portfolio}
                    {fire}
                }}
                1 -> {{
                    {v_income}
                    {e_income}
                    {gifts}
                }}
                2 -> {{
                    {invest}
                }}
                3 -> {{
                    {tax_summary}
                    Spacer(modifier = Modifier.height(16.dp))
                    {tax_params}
                    {child}
                }}
                4 -> {{
                    {living}
                }}
                5 -> {{
                    {reset}
                }}
            }}
        }}
    }}"""

# Remove the trailing spacers from individual blocks manually if needed, or leave them.
# Replace the original Column
pattern = re.compile(r'    Column\(\n        modifier = modifier\n            \.fillMaxSize\(\)\n            \.verticalScroll\(scrollState\)\n            \.padding\(16\.dp\)\n            \.testTag\("settings_tab"\)\n    \) \{.*?    \}\n\n    if \(showResetDialog\) \{', re.DOTALL)

new_content = pattern.sub(new_ui + "\n\n    if (showResetDialog) {", content)

# Clean up double spacers or indentations slightly
with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'w') as f:
    f.write(new_content)

print("Restructured UI written")
