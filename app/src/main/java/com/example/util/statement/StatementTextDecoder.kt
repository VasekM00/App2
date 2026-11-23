package com.example.util.statement

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

internal object StatementTextDecoder {

    /** Hard upper bound for statement imports to avoid OOM on pathological files. */
    const val MAX_STATEMENT_BYTES: Int = 25 * 1024 * 1024

    /** Max CSV rows retained from one statement; bounds token/object allocation on hostile files. */
    const val MAX_CSV_LINES: Int = 200_000

    val COMBINING_MARKS_REGEX = Regex("""\p{M}+""")
    val MULTI_SPACE_REGEX = Regex("""\s+""")
    val YEAR_MONTH_REGEX = Regex("""\d{4}-\d{2}""")

    private val MOJIBAKE_MARKERS = listOf(
        "Ã¡", "Ã©", "Ã­", "Ã½", "Ã³", "Ãº", "Ä›", "Å¡",
        "ÄŤ", "Å™", "Å¾", "Å¯", "Ä ", "Ã ", "Å¤", "Å¥"
    )
    private const val CZECH_ACCENT_CHARS = "ěščřžýáíéúůťďňĚŠČŘŽÝÁÍÉÚŮŤĎŇ"

    fun isWithinSizeLimit(byteCount: Int): Boolean = byteCount in 1..MAX_STATEMENT_BYTES

    fun readStreamBounded(inputStream: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16384)
        var total = 0
        while (true) {
            val count = inputStream.read(chunk)
            if (count <= 0) break
            total += count
            if (total > MAX_STATEMENT_BYTES) return null
            buffer.write(chunk, 0, count)
        }
        val result = buffer.toByteArray()
        return if (isWithinSizeLimit(result.size)) result else null
    }

    fun readUriBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        return try {
            val pfd = try { contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            val reportedSize = pfd?.use { it.statSize } ?: -1L
            if (reportedSize > MAX_STATEMENT_BYTES) return null

            val inputStream = contentResolver.openInputStream(uri) ?: return null
            inputStream.use { stream -> readStreamBounded(stream) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes statement bytes into text, handling BOMs and the encodings Czech banks actually use.
     */
    fun decodeStatementText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        val cp1250Charset = try { Charset.forName("windows-1250") } catch (_: Exception) { null }
        val cp1250Text = if (cp1250Charset != null) {
            try { String(bytes, cp1250Charset) } catch (_: Exception) { "" }
        } else ""

        val strictUtf8: String? = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }

        if (strictUtf8 != null) {
            val mojibakeCount = countMojibakeSequences(strictUtf8)
            val czechCharCount = countCzechAccents(cp1250Text)
            if (mojibakeCount >= 2 && czechCharCount > mojibakeCount) {
                return cp1250Text
            }
            return strictUtf8
        }

        val isoCharset = try { Charset.forName("ISO-8859-2") } catch (_: Exception) { null }
        val isoText = if (isoCharset != null) {
            try { String(bytes, isoCharset) } catch (_: Exception) { "" }
        } else ""

        val cpValid = cp1250Text.isNotBlank() && !cp1250Text.contains('\uFFFD')
        val isoValid = isoText.isNotBlank() && !isoText.contains('\uFFFD')
        if (cpValid && isoValid) {
            val cpScore = countCzechAccents(cp1250Text) - cp1250Text.count { it in "©«®»" } * 2
            val isoScore = countCzechAccents(isoText)
            return if (isoScore > cpScore) isoText else cp1250Text
        }
        if (cpValid) return cp1250Text
        if (isoValid) return isoText

        return String(bytes, Charsets.UTF_8)
    }

    fun countMojibakeSequences(text: String): Int {
        var count = 0
        for (m in MOJIBAKE_MARKERS) {
            var idx = 0
            while (true) {
                idx = text.indexOf(m, idx)
                if (idx == -1) break
                count++
                idx += m.length
            }
        }
        return count
    }

    fun countCzechAccents(text: String): Int = text.count { it in CZECH_ACCENT_CHARS }

    /** Drops diacritics and collapses whitespace so Czech/English headers match reliably. */
    fun foldHeader(value: String): String {
        val decomposed = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(decomposed, "")
            .replace('\u00A0', ' ')
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    fun splitCsvLines(content: String): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var linesInQuote = 0
        var i = 0
        val len = content.length
        while (i < len) {
            val c = content[i]
            when {
                c == '"' && inQuotes && i + 1 < len && content[i + 1] == '"' -> {
                    sb.append("\"\"")
                    i++
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    if (!inQuotes) linesInQuote = 0
                    sb.append(c)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && content[i + 1] == '\n') {
                        i++
                    }
                    val line = sb.toString().trim()
                    if (line.isNotBlank()) {
                        if (lines.size < MAX_CSV_LINES) {
                            lines.add(line)
                        } else {
                            return lines // row cap reached; ignore the rest of the file
                        }
                    }
                    sb.setLength(0)
                }
                c == '\n' && inQuotes -> {
                    linesInQuote++
                    if (linesInQuote > 25 || sb.length > 65_536) {
                        inQuotes = false
                        linesInQuote = 0
                        val line = sb.toString().trim()
                        if (line.isNotBlank()) {
                            lines.add(line)
                        }
                        sb.setLength(0)
                    } else {
                        sb.append(c)
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotBlank()) {
            lines.add(remaining)
        }
        return lines
    }

    fun splitLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    fun detectCsvDelimiter(lines: List<String>): Char {
        val sample = lines.take(30).filter { it.isNotBlank() }
        if (sample.isEmpty()) return ';'

        fun countUnquoted(line: String, delim: Char): Int {
            var count = 0
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (c == delim && !inQuotes) {
                    count++
                }
                i++
            }
            return count
        }

        var bestDelim = ';'
        var bestScore = -1.0
        for (delim in charArrayOf(';', ',', '\t', '|')) {
            val counts = sample.map { countUnquoted(it, delim) }.filter { it > 0 }
            if (counts.isEmpty()) continue
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val consistency = mode.value.toDouble() / counts.size
            val score = consistency * 1000.0 + mode.key
            if (score > bestScore) {
                bestScore = score
                bestDelim = delim
            }
        }
        return bestDelim
    }
}
