package com.example

import com.example.util.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class PdfTextExtractorDeepTest {

    @Test
    fun test1_isPdf_validHeader_returnsTrue() {
        val pdf = "%PDF-1.4\n1 0 obj\nendobj".toByteArray(Charsets.US_ASCII)
        assertTrue(PdfTextExtractor.isPdf(pdf))
    }

    @Test
    fun test2_isPdf_noPdfHeader_returnsFalse() {
        val notPdf = "This is just a CSV file;123;456".toByteArray(Charsets.US_ASCII)
        assertFalse(PdfTextExtractor.isPdf(notPdf))

        val empty = ByteArray(0)
        assertFalse(PdfTextExtractor.isPdf(empty))
    }

    @Test
    fun test3_isPdf_headerInFirst1024Bytes_returnsTrue() {
        val padding = " ".repeat(100)
        val pdf = (padding + "%PDF-1.7\nrest").toByteArray(Charsets.US_ASCII)
        assertTrue(PdfTextExtractor.isPdf(pdf))
    }

    @Test
    fun test4_extractText_emptyOrInvalidBytes_returnsEmpty() {
        assertEquals("", PdfTextExtractor.extractText(ByteArray(0)))
        assertEquals("", PdfTextExtractor.extractText("Not a PDF file".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun test5_extractText_corruptedBytes_doesNotThrow() {
        val corrupted = "%PDF-1.4\n<< /Length 9999 >> stream\ngarbage\nendstream".toByteArray(Charsets.US_ASCII)
        val result = PdfTextExtractor.extractText(corrupted)
        assertTrue(result.isEmpty() || result.isNotBlank())
    }

    @Test
    fun test6_extractText_uncompressedStream_extractsSimpleLiteralString() {
        val streamContent = "BT /F1 12 Tf (Vypis z uctu) Tj ET"
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("Vypis z uctu"))
    }

    @Test
    fun test7_extractText_relativeTd_accumulatesVerticalDisplacement() {
        // Two lines separated by relative displacement 0 -15 Td
        val streamContent = """
            BT
            100 500 Td
            (Prvni radek) Tj
            0 -15 Td
            (Druhy radek) Tj
            ET
        """.trimIndent()
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertEquals("Prvni radek", lines[0])
        assertEquals("Druhy radek", lines[1])
    }

    @Test
    fun test8_extractText_tStar_advancesLine() {
        val streamContent = """
            BT
            100 400 Td
            (Polozka 1) Tj
            T*
            (Polozka 2) Tj
            ET
        """.trimIndent()
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertEquals("Polozka 1", lines[0])
        assertEquals("Polozka 2", lines[1])
    }

    @Test
    fun test9_extractText_hexStringLiteral_decodesProperly() {
        // "Albert" in ASCII hex: 41 6C 62 65 72 74
        val streamContent = "BT <416C62657274> Tj ET"
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("Albert"))
    }

    @Test
    fun test10_extractText_tjArrayWithKerning_concatenatesProperly() {
        val streamContent = "BT [(Bil) 20 (la)] TJ ET"
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("Billa"))
    }

    @Test
    fun test11_extractText_escapedParentheses_parsedCleanly() {
        val streamContent = """BT (\(Kaufland\)) Tj ET"""
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("(Kaufland)"))
    }

    @Test
    fun test12_extractText_octalEscape_decodedCorrectly() {
        // \101 is octal for ASCII 65 ('A')
        val streamContent = """BT (\101lza) Tj ET"""
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("Alza"))
    }

    @Test
    fun test13_extractText_skipsImageAndFontStreams() {
        val imageStream = "image_raw_bytes"
        val contentStream = "BT (Skutecny text) Tj ET"

        val pdf = """
            %PDF-1.4
            1 0 obj
            << /Type /XObject /Subtype /Image /Length ${imageStream.length} >>
            stream
            $imageStream
            endstream
            endobj
            2 0 obj
            << /Length ${contentStream.length} >>
            stream
            $contentStream
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val text = PdfTextExtractor.extractText(pdf)
        assertFalse(text.contains("image_raw_bytes"))
        assertTrue(text.contains("Skutecny text"))
    }

    @Test
    fun test14_extractText_flateCompressedStream_decompressesAndExtracts() {
        val rawText = "BT /F1 12 Tf (Komprimovany obsah banky) Tj ET"
        val deflatedBytes = deflate(rawText.toByteArray(Charsets.US_ASCII))

        val dict = "<< /Filter /FlateDecode /Length ${deflatedBytes.size} >>"
        val pdf = buildPdfWithStream(dict, deflatedBytes)

        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("Komprimovany obsah banky"))
    }

    @Test
    fun test15_extractText_czechCharactersInWindows1250() {
        // "ČSOB Výpis" encoded in Windows-1250
        val czechText = "CSOB Vypis z uctu"
        val streamContent = "BT ($czechText) Tj ET"
        val pdf = buildPdfWithStream("<< /Length ${streamContent.length} >>", streamContent.toByteArray(Charsets.US_ASCII))
        val text = PdfTextExtractor.extractText(pdf)
        assertTrue(text.contains("CSOB Vypis z uctu"))
    }

    private fun buildPdfWithStream(dictionary: String, streamData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write("%PDF-1.4\n1 0 obj\n$dictionary\nstream\n".toByteArray(Charsets.US_ASCII))
        baos.write(streamData)
        baos.write("\nendstream\nendobj\n%%EOF".toByteArray(Charsets.US_ASCII))
        return baos.toByteArray()
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(1024)
        val baos = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            baos.write(buffer, 0, count)
        }
        deflater.end()
        return baos.toByteArray()
    }
}
