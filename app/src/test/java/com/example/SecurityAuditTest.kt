package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static analysis and security audit test suite.
 * Validates manifest permissions, network security, backups, secrets, PII logging,
 * SharedPreferences isolation, CSV injection mitigation, emoji hygiene, and Room configuration.
 */
class SecurityAuditTest {

    private fun getProjectRoot(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        if (File(userDir, "app/src/main").exists()) return userDir
        if (File(userDir, "src/main").exists()) return userDir.parentFile ?: userDir
        var curr: File? = userDir
        while (curr != null) {
            if (File(curr, "app/src/main").exists()) return curr
            curr = curr.parentFile
        }
        return userDir
    }

    private fun getAppSrcMain(): File {
        val root = getProjectRoot()
        val inApp = File(root, "app/src/main")
        if (inApp.exists()) return inApp
        val direct = File(root, "src/main")
        if (direct.exists()) return direct
        return File(File(".").canonicalPath, "app/src/main")
    }

    private fun getAllSourceKtFiles(): List<File> {
        val srcDir = File(getAppSrcMain(), "java")
        assertTrue("Source directory ${srcDir.absolutePath} must exist", srcDir.exists())
        return srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * 4.1: AndroidManifest.xml permission audit
     * Asserts only INTERNET and USE_BIOMETRIC permissions are declared.
     */
    @Test
    fun test4_1_manifestPermissionsAudit() {
        val manifestFile = File(getAppSrcMain(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())
        val lines = manifestFile.readLines()
        val permissionLines = lines.filter { it.contains("<uses-permission") }

        val allowedPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.USE_BIOMETRIC"
        )

        val foundPermissions = mutableListOf<String>()
        val permRegex = Regex("""android:name="([^"]+)"""")
        for (line in permissionLines) {
            val match = permRegex.find(line)
            if (match != null) {
                foundPermissions.add(match.groupValues[1])
            }
        }

        for (perm in foundPermissions) {
            assertTrue("Unexpected permission found: $perm", allowedPermissions.contains(perm))
        }
        assertEquals("Manifest should contain only INTERNET and USE_BIOMETRIC permissions", 2, foundPermissions.size)
        assertTrue("Manifest must contain INTERNET permission", foundPermissions.contains("android.permission.INTERNET"))
        assertTrue("Manifest must contain USE_BIOMETRIC permission", foundPermissions.contains("android.permission.USE_BIOMETRIC"))
    }

    /**
     * 4.2: network_security_config.xml
     * Checks that network_security_config.xml does not exist, or if it does exist, cleartext traffic is disabled.
     */
    @Test
    fun test4_2_networkSecurityConfig() {
        val netSecFile = File(getAppSrcMain(), "res/xml/network_security_config.xml")
        if (!netSecFile.exists()) {
            println("INFO: network_security_config.xml does not exist (default platform cleartext security applies).")
            assertTrue("network_security_config.xml does not exist", true)
        } else {
            val content = netSecFile.readText()
            assertTrue(
                "network_security_config.xml must contain cleartextTrafficPermitted=\"false\"",
                content.contains("cleartextTrafficPermitted=\"false\"")
            )
        }
    }

    /**
     * 4.3: backup rules
     * Asserts allowBackup="false" and backup_rules.xml excludes Room DB (+ WAL/SHM) and all 7 SharedPreferences.
     */
    @Test
    fun test4_3_backupRulesExcludeDatabase() {
        val manifestFile = File(getAppSrcMain(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must set android:allowBackup=\"false\"",
            manifestContent.contains("""android:allowBackup="false"""")
        )

        val rulesFile = File(getAppSrcMain(), "res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml must exist at ${rulesFile.absolutePath}", rulesFile.exists())
        val content = rulesFile.readText()
        val requiredExclusions = listOf(
            "martinu_financials_db",
            "martinu_financials_db-wal",
            "martinu_financials_db-shm",
            "statement_import_prefs.xml",
            "subscription_auditor_prefs.xml",
            "czech_sync_prefs.xml",
            "investment_lots_prefs.xml",
            "merchant_category_overrides.xml",
            "app_preferences.xml",
            "app_theme_prefs.xml",
            "app_user_prefs.xml"
        )
        for (item in requiredExclusions) {
            assertTrue(
                "backup_rules.xml must exclude '$item' from cloud backups",
                content.contains("<exclude") && content.contains(item)
            )
        }
    }

    /**
     * 4.4: data_extraction_rules.xml
     * Asserts data_extraction_rules.xml excludes the Room database (+ WAL/SHM) and all 7 SharedPreferences
     * from cloud backup and contains no template TODOs.
     */
    @Test
    fun test4_4_dataExtractionRules() {
        val rulesFile = File(getAppSrcMain(), "res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml must exist at ${rulesFile.absolutePath}", rulesFile.exists())
        val content = rulesFile.readText()
        assertTrue(
            "data_extraction_rules.xml must not contain template 'TODO' comments",
            !content.contains("TODO")
        )
        assertTrue("data_extraction_rules.xml must contain <cloud-backup>", content.contains("<cloud-backup>"))
        val cloudBackupSection = content.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        assertTrue(
            "data_extraction_rules.xml <cloud-backup> must wildcard-exclude all databases",
            cloudBackupSection.contains("""<exclude domain="database" path="." />""") ||
                cloudBackupSection.contains("""<exclude domain="database" path="."/>""")
        )
        assertTrue(
            "data_extraction_rules.xml <cloud-backup> must wildcard-exclude all sharedprefs",
            cloudBackupSection.contains("""<exclude domain="sharedpref" path="." />""") ||
                cloudBackupSection.contains("""<exclude domain="sharedpref" path="."/>""")
        )
    }

    /**
     * 4.5: No hardcoded secrets
     * Scans all .kt files under app/src/main/java for sensitive patterns (excluding comments).
     */
    @Test
    fun test4_5_noHardcodedSecrets() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val secretPatterns = listOf(
            Regex("""\bapiKey\b""", RegexOption.IGNORE_CASE),
            Regex("""\bapiSecret\b""", RegexOption.IGNORE_CASE),
            Regex("""\bBearer\s+[A-Za-z0-9_\-\.]{10,}"""),
            Regex("""\bpassword\s*=""", RegexOption.IGNORE_CASE),
            Regex("""\bsecret\s*=""", RegexOption.IGNORE_CASE)
        )

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, rawLine) in lines.withIndex()) {
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    continue
                }
                val codeOnly = if (trimmed.contains("//")) trimmed.substringBefore("//").trim() else trimmed

                for (pattern in secretPatterns) {
                    if (pattern.containsMatchIn(codeOnly)) {
                        violations.add("${file.name}:${index + 1}: $trimmed (Matched: ${pattern.pattern})")
                    }
                }
            }
        }

        assertTrue(
            "Found potential hardcoded secrets in source files:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 4.6: No PII logging
     * Scans all .kt files for Android Log calls containing sensitive financial variables and forbids printStackTrace().
     */
    @Test
    fun test4_6_noPiiLogging() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val piiKeywords = listOf(
            "salary", "portfolio", "balance", "income", "czk", "tax",
            "pension", "counterparty", "account", "expense", "wealth", "dividend"
        )
        val logRegex = Regex("""Log\.[deiwv]\s*\([^)]*\)""", RegexOption.DOT_MATCHES_ALL)
        val stackTraceRegex = Regex("""\.printStackTrace\s*\(""")

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val content = file.readText()
            val matches = logRegex.findAll(content)
            for (match in matches) {
                val logCall = match.value.lowercase()
                for (kw in piiKeywords) {
                    if (logCall.contains(kw)) {
                        violations.add("${file.name}: Log statement logs potential PII/financial data ($kw): ${match.value}")
                    }
                }
            }
            file.readLines().forEachIndexed { idx, rawLine ->
                val trimmed = rawLine.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                    if (stackTraceRegex.containsMatchIn(trimmed)) {
                        violations.add("${file.name}:${idx + 1}: Forbidden printStackTrace() call: $trimmed")
                    }
                }
            }
        }

        assertTrue(
            "Found PII/financial variables in Log statements or printStackTrace() calls:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 4.7: SharedPreferences MODE_PRIVATE
     * Asserts every SharedPreferences creation across all production .kt files specifies MODE_PRIVATE.
     */
    @Test
    fun test4_7_sharedPreferencesModePrivate() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val prefCallRegex = Regex("""getSharedPreferences\s*\([^)]*\)""", RegexOption.DOT_MATCHES_ALL)
        var callsFound = 0
        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val content = file.readText()
            for (match in prefCallRegex.findAll(content)) {
                callsFound++
                if (!match.value.contains("MODE_PRIVATE")) {
                    violations.add("${file.name}: SharedPreferences call without MODE_PRIVATE: ${match.value}")
                }
            }
        }

        assertTrue("Expected SharedPreferences usages in production source", callsFound >= 1)
        assertTrue(
            "All SharedPreferences calls must use MODE_PRIVATE:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 4.8: CSV injection protection & FileProvider sharing
     * Verifies MainViewModel.kt / state controllers sanitize CSV numerical cells and ReportShareHelper/ExportReportDialog
     * neutralize leading formula characters (=, +, -, @, \t, \r) and share via FileProvider.
     */
    @Test
    fun test4_8_csvInjectionProtection() {
        val viewModelFile = File(getAppSrcMain(), "java/com/example/ui/MainViewModel.kt")
        assertTrue("MainViewModel.kt must exist at ${viewModelFile.absolutePath}", viewModelFile.exists())
        val content = viewModelFile.readText()
        val stateDir = File(getAppSrcMain(), "java/com/example/ui/state")
        val stateCombined = (listOf(viewModelFile) + (stateDir.listFiles()?.toList() ?: emptyList()))
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue("MainViewModel must implement importCsvData", content.contains("fun importCsvData"))
        assertTrue(
            "MainViewModel / state controllers must sanitize CSV numerical inputs using toDoubleOrNull() to mitigate CSV formula injection",
            stateCombined.contains("toDoubleOrNull()")
        )

        val shareHelperFile = File(getAppSrcMain(), "java/com/example/util/ReportShareHelper.kt")
        assertTrue("ReportShareHelper.kt must exist", shareHelperFile.exists())
        val shareContent = shareHelperFile.readText()
        assertTrue(
            "ReportShareHelper must neutralize CSV formula prefix characters",
            shareContent.contains("FORMULA_PREFIX_CHARS") && shareContent.contains("escapeCsvCell")
        )
        assertTrue(
            "ReportShareHelper must use FileProvider and never fall back to Uri.fromFile",
            shareContent.contains("FileProvider.getUriForFile") && !shareContent.contains("Uri.fromFile")
        )

        val manifestContent = File(getAppSrcMain(), "AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml must register androidx.core.content.FileProvider",
            manifestContent.contains("androidx.core.content.FileProvider") &&
                manifestContent.contains("file_paths")
        )
        val filePathsFile = File(getAppSrcMain(), "res/xml/file_paths.xml")
        assertTrue("res/xml/file_paths.xml must exist", filePathsFile.exists())

        // Direct behavioral check of ExportReportDialog CSV injection sanitizer (including \t / \r prefix bypass)
        assertTrue("Formula '=1+1' must be neutralized", com.example.ui.components.escapeExportCsvCell("=1+1").startsWith("'"))
        assertTrue("Tab-prefixed formula must be neutralized", com.example.ui.components.escapeExportCsvCell("\t=cmd|'/C calc'!A0").startsWith("'"))
        assertTrue("CR-prefixed formula must be neutralized", com.example.ui.components.escapeExportCsvCell("\r+1+1").contains("'\r+1+1"))
        assertEquals("Safe signed number must remain untouched", "-123.45", com.example.ui.components.escapeExportCsvCell("-123.45"))
    }

    /**
     * 4.9: Zero emoji
     * Scans all .kt files under app/src/main/java for Unicode emoji / non-standard symbol ranges.
     */
    @Test
    fun test4_9_zeroEmojiInSourceFiles() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val emojiRegex = Regex(
            "[\uD83C-\uDBFF][\uDC00-\uDFFF]|" +
            "[\u2600-\u27BF]|" +
            "[\u2B00-\u2BFF]|" +
            "[\uE000-\uF8FF]|" +
            "[\uFE00-\uFE0F]"
        )

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val match = emojiRegex.find(line)
                if (match != null) {
                    violations.add("${file.name}:${index + 1}: Contains emoji/unsupported Unicode symbol: '${match.value}'")
                }
            }
        }

        assertTrue(
            "Found emoji or non-standard symbol characters in Kotlin source files:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 4.10: Room exportSchema
     * Scans all .kt files for @Database annotations and ensures exportSchema = true is set
     * (schema JSON export is required to validate migrations).
     */
    @Test
    fun test4_10_roomDatabaseExportSchema() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        var databaseAnnotationFound = false
        val dbAnnotationRegex = Regex("""@Database\s*\(([^)]+)\)""", RegexOption.DOT_MATCHES_ALL)

        for (file in ktFiles) {
            val content = file.readText()
            val matches = dbAnnotationRegex.findAll(content)
            for (match in matches) {
                databaseAnnotationFound = true
                val args = match.groupValues[1]
                assertTrue(
                    "Database annotation in ${file.name} must specify 'exportSchema = true'",
                    args.contains("exportSchema = true") || args.contains("exportSchema=true")
                )
            }
        }

        assertTrue("At least one @Database annotation must exist and be audited", databaseAnnotationFound)
    }

    /**
     * 4.11: Clipboard sensitivity & no raw file:// URI exposure
     * Verifies all ClipboardManager copies mark EXTRA_IS_SENSITIVE and zero Uri.fromFile calls exist.
     */
    @Test
    fun test4_11_clipboardCopiesMarkSensitiveAndNoUriFromFile() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val clipboardViolations = mutableListOf<String>()
        val uriFromFileViolations = mutableListOf<String>()

        for (file in ktFiles) {
            val content = file.readText()
            if (content.contains("setPrimaryClip(") && !content.contains("EXTRA_IS_SENSITIVE")) {
                clipboardViolations.add(file.name)
            }
            if (content.contains("Uri.fromFile(")) {
                uriFromFileViolations.add(file.name)
            }
        }

        assertTrue(
            "Clipboard copies must mark ClipDescription.EXTRA_IS_SENSITIVE = true: $clipboardViolations",
            clipboardViolations.isEmpty()
        )
        assertTrue(
            "Production code must never expose raw file:// URIs via Uri.fromFile(): $uriFromFileViolations",
            uriFromFileViolations.isEmpty()
        )
    }
}
