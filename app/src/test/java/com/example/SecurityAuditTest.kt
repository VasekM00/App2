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
     * Asserts only INTERNET and ACCESS_NETWORK_STATE permissions are declared.
     */
    @Test
    fun test4_1_manifestPermissionsAudit() {
        val manifestFile = File(getAppSrcMain(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())
        val lines = manifestFile.readLines()
        val permissionLines = lines.filter { it.contains("<uses-permission") }

        val allowedPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
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
        assertEquals("Manifest should contain exactly 2 permissions", 2, foundPermissions.size)
        assertTrue("Manifest must contain INTERNET permission", foundPermissions.contains("android.permission.INTERNET"))
        assertTrue("Manifest must contain ACCESS_NETWORK_STATE permission", foundPermissions.contains("android.permission.ACCESS_NETWORK_STATE"))
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
     * 4.3: allowBackup
     * Checks AndroidManifest.xml for allowBackup="true" and issues a WARNING.
     */
    @Test
    fun test4_3_allowBackupWarning() {
        val manifestFile = File(getAppSrcMain(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()
        if (content.contains("android:allowBackup=\"true\"")) {
            println("WARNING: AndroidManifest.xml contains android:allowBackup=\"true\". Ensure sensitive storage is protected.")
        }
        assertTrue("allowBackup check completed", true)
    }

    /**
     * 4.4: data_extraction_rules.xml
     * Reads data_extraction_rules.xml and warns if it contains template TODO markers.
     */
    @Test
    fun test4_4_dataExtractionRules() {
        val rulesFile = File(getAppSrcMain(), "res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml must exist at ${rulesFile.absolutePath}", rulesFile.exists())
        val content = rulesFile.readText()
        if (content.contains("TODO")) {
            println("WARNING: data_extraction_rules.xml contains template 'TODO' comments. Custom backup inclusion/exclusion rules recommended.")
        }
        assertTrue("data_extraction_rules.xml check completed", true)
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
     * Scans all .kt files for Android Log calls containing sensitive financial variables.
     */
    @Test
    fun test4_6_noPiiLogging() {
        val ktFiles = getAllSourceKtFiles()
        assertTrue("Should scan Kotlin source files", ktFiles.isNotEmpty())

        val piiKeywords = listOf("salary", "portfolio", "balance", "income")
        val logRegex = Regex("""Log\.[deiwv]\s*\([^)]*\)""", RegexOption.DOT_MATCHES_ALL)

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
        }

        assertTrue(
            "Found PII/financial variables in Log statements:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * 4.7: SharedPreferences MODE_PRIVATE
     * Asserts EmergencyReserveWidget.kt specifies MODE_PRIVATE.
     */
    @Test
    fun test4_7_sharedPreferencesModePrivate() {
        val widgetFile = File(getAppSrcMain(), "java/com/example/ui/components/EmergencyReserveWidget.kt")
        assertTrue("EmergencyReserveWidget.kt must exist at ${widgetFile.absolutePath}", widgetFile.exists())
        val content = widgetFile.readText()
        assertTrue("EmergencyReserveWidget.kt must use MODE_PRIVATE for SharedPreferences", content.contains("MODE_PRIVATE"))
    }

    /**
     * 4.8: CSV injection protection
     * Verifies MainViewModel.kt parses CSV numerical cells with toDoubleOrNull() to prevent formula injection.
     */
    @Test
    fun test4_8_csvInjectionProtection() {
        val viewModelFile = File(getAppSrcMain(), "java/com/example/ui/MainViewModel.kt")
        assertTrue("MainViewModel.kt must exist at ${viewModelFile.absolutePath}", viewModelFile.exists())
        val content = viewModelFile.readText()

        assertTrue("MainViewModel must implement importCsvData", content.contains("fun importCsvData"))
        assertTrue(
            "MainViewModel must sanitize CSV numerical inputs using toDoubleOrNull() to mitigate CSV formula injection",
            content.contains("toDoubleOrNull()")
        )
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
     * Scans all .kt files for @Database annotations and ensures exportSchema = false is set.
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
                    "Database annotation in ${file.name} must specify 'exportSchema = false'",
                    args.contains("exportSchema = false") || args.contains("exportSchema=false")
                )
            }
        }

        assertTrue("At least one @Database annotation must exist and be audited", databaseAnnotationFound)
    }
}
