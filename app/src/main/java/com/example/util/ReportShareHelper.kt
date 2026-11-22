package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Report export and sharing helper.
 *
 * Capabilities:
 * - CSV formula-injection neutralization and UTF-8 Byte Order Mark (BOM) encoding.
 * - Android Intent sharing utilities for text reports and cache-confined CSV files via FileProvider.
 */
object ReportShareHelper {

    const val UTF8_BOM: String = "\uFEFF"
    private const val EXPORT_CACHE_TTL_MS: Long = 10 * 60 * 1000L
    private const val MAX_EXPORT_CACHE_FILES: Int = 3
    private val SAFE_SIGNED_NUMBER_REGEX = Regex("""^[-+]?\d+([.,]\d+)?$""")
    private val UNSAFE_FILENAME_CHARS_REGEX = Regex("""[^A-Za-z0-9._-]""")
    private val FORMULA_PREFIX_CHARS = charArrayOf('=', '+', '-', '@', '|', '\t', '\r', '\n')
    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '|')

    private fun isCsvLeadingWhitespace(c: Char): Boolean =
        c.isWhitespace() || c == '\u00A0' || c == '\u202F' || c == '\uFEFF' || c == '\u000B' || c == '\u000C'

    /**
     * Escapes a CSV cell for RFC 4180 delimiters/quotes while neutralizing spreadsheet
     * formula-injection prefixes (`=`, `+`, `-`, `@`, `|`, `\t`, `\r`, `\n`), preserving signed numbers.
     */
    fun escapeCsvCell(value: String, delimiter: String = ","): String {
        val trimmedLeading = value.trimStart(::isCsvLeadingWhitespace)
        val trimmedAll = value.trim(::isCsvLeadingWhitespace)
        val isSafeNumeric = SAFE_SIGNED_NUMBER_REGEX.matches(value)
        val startsWithDangerousChar = value.isNotEmpty() && value[0] in FORMULA_PREFIX_CHARS
        val trimmedStartsWithFormula = trimmedLeading.isNotEmpty() &&
            trimmedLeading[0] in FORMULA_TRIGGER_CHARS &&
            !SAFE_SIGNED_NUMBER_REGEX.matches(trimmedAll)
        val neutralized = if (!isSafeNumeric && (startsWithDangerousChar || trimmedStartsWithFormula)) {
            "'$value"
        } else {
            value
        }
        val needsQuotes = neutralized.contains(delimiter) || neutralized.contains("\"") ||
            neutralized.contains("\n") || neutralized.contains("\r")
        return if (needsQuotes) {
            "\"${neutralized.replace("\"", "\"\"")}\""
        } else {
            neutralized
        }
    }

    /**
     * Converts a CSV string into raw UTF-8 byte array with exact Byte Order Mark (0xEF, 0xBB, 0xBF).
     */
    fun getCsvBytesWithBom(csvString: String): ByteArray {
        val clean = if (csvString.startsWith(UTF8_BOM)) csvString.substring(1) else csvString
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val contentBytes = clean.toByteArray(Charsets.UTF_8)
        return bomBytes + contentBytes
    }

    /**
     * Creates an Android Intent for sharing plain formatted report text.
     */
    fun createShareTextIntent(
        text: String,
        subject: String = "FIRE Financial Report Summary"
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    /**
     * Writes CSV bytes to the app's cache directory and returns a share Intent with content URI.
     */
    fun createShareCsvIntent(
        context: Context,
        csvBytes: ByteArray,
        filename: String = "fire_financial_export.csv",
        subject: String = "FIRE Financial Data Export"
    ): Intent {
        val safeFilename = sanitizeExportFilename(filename)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val canonicalExportDir = exportDir.canonicalFile
        val canonicalFile = File(canonicalExportDir, safeFilename).canonicalFile
        require(canonicalFile.parentFile == canonicalExportDir) {
            "Export path escapes cache/exports directory"
        }

        pruneExportCache(canonicalExportDir, keepFile = canonicalFile)
        FileOutputStream(canonicalFile).use { it.write(csvBytes) }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            canonicalFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, subject, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearExportCache(context: Context) {
        val exportDir = File(context.cacheDir, "exports")
        if (exportDir.exists() && exportDir.isDirectory) {
            exportDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    private fun sanitizeExportFilename(rawFilename: String): String {
        val leaf = rawFilename.substringAfterLast('/').substringAfterLast('\\').trimStart('.')
        val cleaned = UNSAFE_FILENAME_CHARS_REGEX.replace(leaf, "_")
        val base = cleaned.ifBlank { "fire_financial_export.csv" }
        val withExt = if (base.endsWith(".csv", ignoreCase = true)) base else "$base.csv"
        return if (withExt.length > 96) {
            val stem = withExt.substringBeforeLast('.').take(92)
            "$stem.csv"
        } else {
            withExt
        }
    }

    private fun pruneExportCache(exportDir: File, keepFile: File) {
        val now = System.currentTimeMillis()
        val existing = exportDir.listFiles()
            ?.filter { it.isFile && it.canonicalFile != keepFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        existing.forEachIndexed { index, file ->
            if (index >= MAX_EXPORT_CACHE_FILES - 1 || (now - file.lastModified()) > EXPORT_CACHE_TTL_MS) {
                runCatching { file.delete() }
            }
        }
    }
}
