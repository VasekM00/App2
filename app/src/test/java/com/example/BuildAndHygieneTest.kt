package com.example

import com.example.data.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Build and source hygiene test suite.
 * Validates codebase cleanliness, TODO/FIXME hygiene, duplicate imports,
 * SettingsEntity-to-BackupManager reflection parity, UI maintainability line thresholds,
 * zero println/System.out in production, and import integrity in FinancialEngine.kt.
 */
class BuildAndHygieneTest {

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

    private fun getAllProductionKtFiles(): List<File> {
        val srcDir = getMainJavaDir()
        assertTrue("Production source directory ${srcDir.absolutePath} must exist", srcDir.exists() && srcDir.isDirectory)
        return srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * 9.1: Zero `TODO` or `FIXME` in production Kotlin code under `app/src/main/java` (excluding comments in XML).
     */
    @Test
    fun test9_1_zeroTodoOrFixmeInProductionCode() {
        val ktFiles = getAllProductionKtFiles()
        assertTrue("Should scan Kotlin source files under app/src/main/java", ktFiles.isNotEmpty())

        val todoRegex = Regex("""\b(TODO|FIXME)\b""")
        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (todoRegex.containsMatchIn(line)) {
                    violations.add("${file.name}:${index + 1}: ${line.trim()}")
                }
            }
        }

        assertTrue(
            "Found unresolved TODO or FIXME in production Kotlin code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 9.2: Zero duplicate imports in any `.kt` file under `app/src/main/java`.
     */
    @Test
    fun test9_2_zeroDuplicateImports() {
        val ktFiles = getAllProductionKtFiles()
        assertTrue("Should scan Kotlin source files under app/src/main/java", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            val importLines = lines
                .map { it.trim() }
                .filter { it.startsWith("import ") }

            val seen = mutableSetOf<String>()
            for (imp in importLines) {
                if (!seen.add(imp)) {
                    violations.add("${file.name}: duplicate '$imp'")
                }
            }
        }

        assertTrue(
            "Found duplicate import statements in production Kotlin files:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 9.3: `SettingsEntity` field count (95 fields) matches `BackupManager` serialized field count via reflection.
     */
    @Test
    fun test9_3_settingsEntityFieldCountMatchesBackupManager() {
        val declaredFields = SettingsEntity::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }

        assertEquals(
            "SettingsEntity must declare exactly 95 fields (1 id + 94 configuration parameters)",
            95,
            declaredFields.size
        )

        val backupManagerFile = File(getMainJavaDir(), "com/example/util/BackupManager.kt")
        assertTrue("BackupManager.kt must exist at ${backupManagerFile.absolutePath}", backupManagerFile.exists())

        val backupSource = backupManagerFile.readText()
        val serializeFunctionBody = backupSource
            .substringAfter("fun serializeSettingsToJson")
            .substringBefore("fun deserializeSettingsFromJson")

        val jsonPutRegex = Regex("""json\.put\(\s*"([^"]+)"""")
        val serializedFields = jsonPutRegex.findAll(serializeFunctionBody).map { it.groupValues[1] }.toSet()

        // Verify that all 94 non-id configuration fields from SettingsEntity are serialized
        val nonIdFields = declaredFields.filter { it.name != "id" }
        assertEquals(
            "BackupManager must serialize all 94 configuration fields",
            94,
            serializedFields.size
        )

        for (field in nonIdFields) {
            assertTrue(
                "BackupManager must serialize SettingsEntity field '${field.name}'",
                serializedFields.contains(field.name)
            )
        }
    }

    /**
     * 9.4: All `.kt` files under `app/src/main/java/com/example/ui` have < 900 lines (maintainability threshold).
     */
    @Test
    fun test9_4_uiFilesLineCountMaintainabilityThreshold() {
        val uiDir = File(getMainJavaDir(), "com/example/ui")
        assertTrue("UI directory must exist at ${uiDir.absolutePath}", uiDir.exists() && uiDir.isDirectory)

        val directUiFiles = uiDir.listFiles()?.filter { it.isFile && it.extension == "kt" } ?: emptyList()
        assertTrue("UI directory must contain top-level Kotlin source files", directUiFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in directUiFiles) {
            val lineCount = file.readLines().size
            if (lineCount >= 900) {
                violations.add("${file.name}: $lineCount lines (exceeds 900 lines threshold)")
            }
        }

        assertTrue(
            "Top-level UI files must not exceed 900 lines maintainability threshold:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 9.5: No `println` or `System.out` calls in production Kotlin files under `app/src/main/java`.
     */
    @Test
    fun test9_5_noPrintlnOrSystemOutInProductionCode() {
        val ktFiles = getAllProductionKtFiles()
        assertTrue("Should scan Kotlin source files under app/src/main/java", ktFiles.isNotEmpty())

        val printlnRegex = Regex("""\b(println|print)\s*\(""")
        val systemOutRegex = Regex("""System\.(out|err)\.""")

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, rawLine) in lines.withIndex()) {
                val line = rawLine.trim()
                // Ignore commented-out lines
                val codeOnly = if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) "" else line
                if (codeOnly.isNotEmpty()) {
                    if (printlnRegex.containsMatchIn(codeOnly) || systemOutRegex.containsMatchIn(codeOnly)) {
                        violations.add("${file.name}:${index + 1}: $line")
                    }
                }
            }
        }

        assertTrue(
            "Found println or System.out calls in production Kotlin code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 9.6: Check for unused imports or verify `cos` in FinancialEngine.kt.
     */
    @Test
    fun test9_6_unusedImportsAndCosVerificationInFinancialEngine() {
        val engineFile = File(getMainJavaDir(), "com/example/domain/FinancialEngine.kt")
        assertTrue("FinancialEngine.kt must exist at ${engineFile.absolutePath}", engineFile.exists())

        val engineContent = engineFile.readText()

        // Verify cos is imported and used for Box-Muller normal transform
        assertTrue(
            "FinancialEngine.kt must import kotlin.math.cos",
            engineContent.contains("import kotlin.math.cos")
        )
        assertTrue(
            "FinancialEngine.kt must use cos() for Box-Muller Gaussian simulation",
            engineContent.contains("cos(theta)") || engineContent.contains("cos(")
        )

        // Verify all imported symbols in FinancialEngine.kt are used in the codebase
        val lines = engineFile.readLines()
        val importLines = lines.filter { it.trim().startsWith("import ") }
        val codeBody = lines.filter { !it.trim().startsWith("import ") && !it.trim().startsWith("package ") }.joinToString("\n")

        for (importLine in importLines) {
            val importedSymbol = importLine.trim().substringAfterLast('.').trim()
            assertTrue(
                "Imported symbol '$importedSymbol' in FinancialEngine.kt must be referenced in the file content",
                codeBody.contains(importedSymbol)
            )
        }
    }
}
