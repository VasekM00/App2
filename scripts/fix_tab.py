import re

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'r') as f:
    content = f.read()

def get_block(start_marker, end_marker):
    idx1 = content.find(start_marker)
    if idx1 == -1: return ""
    if end_marker:
        idx2 = content.find(end_marker, idx1)
        if idx2 == -1: return ""
        return content[idx1:idx2].strip()
    else:
        idx2 = content.find('    }', idx1)
        return content[idx1:idx2].strip()

# find blocks
tax_summary = get_block('        // Tax Return Helper Summary Card', '        // Portfolio & Base Settings')
portfolio = get_block('        // Portfolio & Base Settings', '        // Vaclav Income Settings')
v_income = get_block('        // Vaclav Income Settings', '        // Eleonora Income Settings')
e_income = get_block('        // Eleonora Income Settings', '        // Gifts, Savings & Lump Sums')
gifts = get_block('        // Gifts, Savings & Lump Sums', '        // Investment Balances & Setup')
invest = get_block('        // Investment Balances & Setup', '        // FIRE Target & Parameters')
fire = get_block('        // FIRE Target & Parameters', '        // Tax Parameters')
tax = get_block('        // Tax Parameters', '        // Child Settings & Expenses')
child = get_block('        // Child Settings & Expenses', '        // Monthly Living Expenses (CZK)')
living = get_block('        // Monthly Living Expenses (CZK)', '        // Reset Card')
reset = get_block('        // Reset Card', '        if (showResetDialog) {')

# The closing of the Column is before `if (showResetDialog)`
# Let's remove the last `}` from reset
if reset.endswith('    }'):
    reset = reset[:-5].strip()
    
# Remove everything between the start of Column and `if (showResetDialog)`
start_idx = content.find('    Column(\n        modifier = modifier')
end_idx = content.find('    if (showResetDialog) {')

new_column = f"""    var selectedTab by remember {{ mutableIntStateOf(0) }}
    val tabs = listOf("General", "Income", "Investments", "Taxes & Family", "Expenses", "Data")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_tab")
    ) {{
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.background
        ) {{
            tabs.forEachIndexed {{ index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {{ selectedTab = index }},
                    text = {{ Text(title, fontWeight = FontWeight.SemiBold) }}
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
                    {tax}
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
    }}
    
"""

content = content[:start_idx] + new_column + content[end_idx:]

with open('app/src/main/java/com/example/ui/tabs/SettingsTab.kt', 'w') as f:
    f.write(content)
