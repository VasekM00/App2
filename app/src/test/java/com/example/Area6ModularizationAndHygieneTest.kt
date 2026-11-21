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
    fun testPlanAndSettingsTabModularFilesExist() {
        val projectionsTabFile = File(getMainJavaDir(), "com/example/ui/tabs/ProjectionsTab.kt")
        assertTrue("ProjectionsTab.kt must exist", projectionsTabFile.exists())
        assertTrue("ProjectionsTab.kt must be non-empty", projectionsTabFile.readLines().isNotEmpty())

        val settingsTabFile = File(getMainJavaDir(), "com/example/ui/tabs/SettingsTab.kt")
        assertTrue("SettingsTab.kt must exist", settingsTabFile.exists())
        assertTrue("SettingsTab.kt must be non-empty", settingsTabFile.readLines().isNotEmpty())

        val budgetTabFile = File(getMainJavaDir(), "com/example/ui/tabs/BudgetAndIncomesSubTab.kt")
        assertTrue("BudgetAndIncomesSubTab.kt must exist", budgetTabFile.exists())
        assertTrue("BudgetAndIncomesSubTab.kt must be non-empty", budgetTabFile.readLines().isNotEmpty())
    }

    @Test
    fun testAllExtractedPlanAndSettingsComponentsExistAndNonEmpty() {
        val requiredExtractedFiles = listOf(
            "com/example/ui/tabs/ProjectionsTab.kt",
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

        for (relPath in requiredExtractedFiles) {
            val file = File(getMainJavaDir(), relPath)
            assertTrue("Extracted file $relPath must exist", file.exists())
            val lineCount = file.readLines().size
            assertTrue("Extracted file $relPath must have content (> 50 lines, was $lineCount)", lineCount > 50)
        }
    }

    @Test
    fun testZeroTodoOrFixmeInExtractedFiles() {
        val requiredExtractedFiles = listOf(
            "com/example/ui/tabs/ProjectionsTab.kt",
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
