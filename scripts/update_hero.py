import re

with open('app/src/main/java/com/example/ui/components/HeroHeader.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.example.ui.theme.BrandTealDark\n', '')
content = content.replace('import com.example.ui.theme.BrandTeal\n', '')
content = content.replace('import com.example.ui.theme.BrandGold\n', '')
content = content.replace('import com.example.ui.theme.BrandGoldDarkTheme\n', '')
content = content.replace('import com.example.ui.theme.BrandLavenderDarkTheme\n', '')

content = content.replace('import com.example.util.Formatters.fmtCompact\n', 'import com.example.util.Formatters.fmtCompact\nimport com.example.ui.theme.BrandGoldDarkTheme\nimport com.example.ui.theme.BrandLavenderDarkTheme\n')

with open('app/src/main/java/com/example/ui/components/HeroHeader.kt', 'w') as f:
    f.write(content)
