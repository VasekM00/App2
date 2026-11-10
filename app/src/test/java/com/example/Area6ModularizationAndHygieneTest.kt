package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Area6ModularizationAndHygieneTest {

    private fun getMainJavaDir(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(userDir, "app/src/main/java"),
            File(userDir, "src/main/java"),
            File("app/src/main/java"),
            File("src/main/java")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isDirectory) return candidate
        }
        var curr: File? = userDir
        while (curr != null) {
            val f = File(curr, "app/src/main/java")
            if (f.exists() && f.isDirectory) return f
            curr = curr.parentFile
        }
        return File(userDir, "app/src/main/java")
    }

    @Test
    fun testPlanAndSettingsTabDecomposedUnderThreshold() {
        val planTabFile = File(getMainJavaDir(), "com/example/ui/tabs/PlanTab.kt")
        assertTrue("PlanTab.kt must exist", planTabFile.exists())
        val planTabLines = planTabFile.readLines().size
        assertTrue("PlanTab.kt must be a concise orchestrator (< 150 lines, was $planTabLines)", planTabLines < 150)

        val settingsTabFile = File(getMainJavaDir(), "com/example/ui/tabs/SettingsTab.kt")
        assertTrue("SettingsTab.kt must exist", settingsTabFile.exists())
        val settingsTabLines = settingsTabFile.readLines().size
        assertTrue("SettingsTab.kt must be a concise orchestrator (< 500 lines, was $settingsTabLines)", settingsTabLines < 500)

        val budgetTabFile = File(getMainJavaDir(), "com/example/ui/tabs/BudgetAndIncomesSubTab.kt")
        assertTrue("BudgetAndIncomesSubTab.kt must exist", budgetTabFile.exists())
        val budgetTabLines = budgetTabFile.readLines().size
        assertTrue("BudgetAndIncomesSubTab.kt must be a concise orchestrator (< 200 lines, was $budgetTabLines)", budgetTabLines < 200)
    }

    @Test
    fun testAllExtractedPlanAndSettingsComponentsUnderMaintainabilityLimit() {
        val requiredExtractedFiles = listOf(
            "com/example/ui/tabs/PlanTab.kt",
            "com/example/ui/tabs/PlanMetricInfos.kt",
            "com/example/ui/tabs/LifeGoalsSimulatorCard.kt",
            "com/example/ui/tabs/FireMilestonesComparisonCard.kt",
            "com/example/ui/tabs/EarnerOptimizationCard.kt",
            "com/example/ui/tabs/PensionSubTab.kt",
            "com/example/ui/tabs/FireRoadmapSubTab.kt",
            "com/example/ui/tabs/SettingsTab.kt",
            "com/example/ui/tabs/SettingsCards.kt",
            "com/example/ui/tabs/SettingsHouseholdSubTab.kt",
            "com/example/ui/tabs/SettingsFireSubTab.kt",
            "com/example/ui/tabs/SettingsDataSubTab.kt",
            "com/example/ui/tabs/SettingsDialogs.kt",
            "com/example/ui/tabs/BudgetAndIncomesSubTab.kt",
            "com/example/ui/tabs/BudgetOverviewCard.kt",
            "com/example/ui/tabs/IncomeSourcesCard.kt",
            "com/example/ui/tabs/ExpenseCategoriesCard.kt"
        )

        val violations = mutableListOf<String>()
        for (relPath in requiredExtractedFiles) {
            val file = File(getMainJavaDir(), relPath)
            assertTrue("Extracted file $relPath must exist", file.exists())
            val lineCount = file.readLines().size
            assertTrue("Extracted file $relPath must have content (> 50 lines, was $lineCount)", lineCount > 50)
            if (lineCount >= 750) {
                violations.add("$relPath: $lineCount lines (exceeds 750 lines maintainability threshold)")
            }
        }

        assertTrue(
            "All modularized Plan and Settings tab files must remain well under 750 lines:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testZeroTodoOrFixmeInExtractedFiles() {
        val requiredExtractedFiles = listOf(
            "com/example/ui/tabs/PlanTab.kt",
            "com/example/ui/tabs/PlanMetricInfos.kt",
            "com/example/ui/tabs/LifeGoalsSimulatorCard.kt",
            "com/example/ui/tabs/FireMilestonesComparisonCard.kt",
            "com/example/ui/tabs/EarnerOptimizationCard.kt",
            "com/example/ui/tabs/PensionSubTab.kt",
            "com/example/ui/tabs/FireRoadmapSubTab.kt",
            "com/example/ui/tabs/SettingsTab.kt",
            "com/example/ui/tabs/SettingsCards.kt",
            "com/example/ui/tabs/SettingsHouseholdSubTab.kt",
            "com/example/ui/tabs/SettingsFireSubTab.kt",
            "com/example/ui/tabs/SettingsDataSubTab.kt",
            "com/example/ui/tabs/SettingsDialogs.kt",
            "com/example/ui/tabs/BudgetAndIncomesSubTab.kt",
            "com/example/ui/tabs/BudgetOverviewCard.kt",
            "com/example/ui/tabs/IncomeSourcesCard.kt",
            "com/example/ui/tabs/ExpenseCategoriesCard.kt"
        )

        val todoRegex = Regex("""\b(TODO|FIXME)\b""")
        val violations = mutableListOf<String>()
        for (relPath in requiredExtractedFiles) {
            val file = File(getMainJavaDir(), relPath)
            val lines = file.readLines()
            for ((idx, line) in lines.withIndex()) {
                if (todoRegex.containsMatchIn(line)) {
                    violations.add("${file.name}:${idx + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            "Found unresolved TODO or FIXME in modularized files:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
