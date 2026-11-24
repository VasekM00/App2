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
        var atFieldStart = true
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
                c == '"' && inQuotes -> {
                    inQuotes = false
                    linesInQuote = 0
                    atFieldStart = false
                    sb.append(c)
                }
                c == '"' && !inQuotes -> {
                    if (atFieldStart) {
                        inQuotes = true
                        atFieldStart = false
                    }
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
                    atFieldStart = true
                }
                c == '\n' && inQuotes -> {
                    linesInQuote++
                    if (linesInQuote > 25 || sb.length > 65_536) {
                        inQuotes = false
                        linesInQuote = 0
                        val line = sb.toString().trim()
                        if (line.isNotBlank()) {
                            if (lines.size < MAX_CSV_LINES) {
                                lines.add(line)
                            } else {
                                return lines
                            }
                        }
                        sb.setLength(0)
                        atFieldStart = true
                    } else {
                        sb.append(c)
                    }
                }
                else -> {
                    if (!inQuotes) {
                        if (c == ';' || c == ',' || c == '\t' || c == '|') {
                            atFieldStart = true
                        } else if (c != ' ') {
                            atFieldStart = false
                        }
                    }
                    sb.append(c)
                }
            }
            i++
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotBlank() && lines.size < MAX_CSV_LINES) {
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
                c == '"' && inQuotes -> inQuotes = false
                c == '"' && !inQuotes -> {
                    if (sb.isBlank()) {
                        sb.setLength(0)
                        inQuotes = true
                    } else {
                        // Literal mid-field quote (e.g. Monitor 27")
                        sb.append('"')
                    }
                }
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
        val nonBlank = lines.asSequence().filter { it.isNotBlank() }.take(40).toList()
        if (nonBlank.isEmpty()) return ';'

        val headerIdx = nonBlank.indexOfFirst { line ->
            val f = foldHeader(line.trimStart('#'))
            (f.contains("datum") || f.contains("date")) &&
                (f.contains("castka") || f.contains("objem") || f.contains("amount") ||
                    f.contains("debet") || f.contains("completed"))
        }
        val tableSample = if (headerIdx >= 0) {
            nonBlank.drop(headerIdx).take(30)
        } else {
            nonBlank.take(30)
        }
        if (tableSample.isEmpty()) return ';'

        fun countUnquoted(line: String, delim: Char): Int {
            var count = 0
            var inQuotes = false
            var atFieldStart = true
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        i++
                    } else if (inQuotes) {
                        inQuotes = false
                        atFieldStart = false
                    } else if (atFieldStart) {
                        inQuotes = true
                        atFieldStart = false
                    }
                } else if (!inQuotes) {
                    if (c == delim) {
                        count++
                    }
                    if (c == ';' || c == ',' || c == '\t' || c == '|') {
                        atFieldStart = true
                    } else if (c != ' ') {
                        atFieldStart = false
                    }
                }
                i++
            }
            return count
        }

        fun countUnquotedCzechDecimalCommas(line: String): Int {
            var hits = 0
            var inQuotes = false
            var atFieldStart = true
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        i++
                    } else if (inQuotes) {
                        inQuotes = false
                        atFieldStart = false
                    } else if (atFieldStart) {
                        inQuotes = true
                        atFieldStart = false
                    }
                } else if (!inQuotes) {
                    if (c == ',' && i > 0 && line[i - 1].isDigit() &&
                        i + 2 < line.length && line[i + 1].isDigit() && line[i + 2].isDigit() &&
                        (i + 3 >= line.length || line[i + 3] == ';' || line[i + 3] == '\t' ||
                            line[i + 3] == '|' || line[i + 3].isWhitespace())
                    ) {
                        hits++
                    }
                    if (c == ';' || c == ',' || c == '\t' || c == '|') {
                        atFieldStart = true
                    } else if (c != ' ') {
                        atFieldStart = false
                    }
                }
                i++
            }
            return hits
        }

        val semicolonCounts = tableSample.map { countUnquoted(it, ';') }
        val semicolonHighCoverage = semicolonCounts.count { it >= 2 } * 2 >= tableSample.size

        var bestDelim = ';'
        var bestScore = -1.0
        for (delim in charArrayOf(';', ',', '\t', '|')) {
            val counts = if (delim == ';') semicolonCounts else tableSample.map { countUnquoted(it, delim) }
            val positiveCounts = counts.filter { it > 0 }
            if (positiveCounts.isEmpty()) continue
            val mode = positiveCounts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val coverage = mode.value.toDouble() / tableSample.size
            var score = coverage * 1000.0 + minOf(mode.key, 20) * 15.0
            if (countUnquoted(tableSample.first(), delim) == mode.key && mode.key >= 1) {
                score += 150.0
            }
            if (delim == ',' && semicolonHighCoverage) {
                var modalTotalCommas = 0
                var modalDecimalCommas = 0
                for (idx in tableSample.indices) {
                    if (counts[idx] == mode.key) {
                        modalTotalCommas += counts[idx]
                        modalDecimalCommas += countUnquotedCzechDecimalCommas(tableSample[idx])
                    }
                }
                if (modalTotalCommas > 0 && modalDecimalCommas * 2 >= modalTotalCommas) {
                    score -= 250.0
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestDelim = delim
            }
        }
        return bestDelim
    }
}
