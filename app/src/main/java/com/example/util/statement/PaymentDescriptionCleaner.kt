package com.example.util.statement

internal object PaymentDescriptionCleaner {

    // Consolidated boilerplate regex using Unicode-aware boundaries (?<![\p{L}\d]) ... (?![\p{L}\d])
    private val CONSOLIDATED_BOILERPLATE_REGEX = Regex(
        """(?<![\p{L}\d])(?:""" +
            """platba\s+kartou\s+v\s+[čc]r|""" +
            """platba\s+kartou\s+v\s+zahrani[čc][ií]|""" +
            """platba\s+kartou\s+online|""" +
            """platba\s+kartou|""" +
            """transakce\s+platebn[ií]\s+kartou|""" +
            """platba\s+na\s+internetu|""" +
            """platba\s+mobilem|""" +
            """bezhotovostn[ií]\s+platba|""" +
            """okam[zž]it[aá]\s+platba|""" +
            """p[rř][ií]choz[ií]\s+[uú]hrada|""" +
            """odchoz[ií]\s+[uú]hrada|""" +
            """p[rř][ií]choz[ií]\s+platba|""" +
            """odchoz[ií]\s+platba|""" +
            """trval[yý]\s+p[rř][ií]kaz\s+k\s+[uú]hrad[eě]:?|""" +
            """trval[yý]\s+p[rř][ií]kaz:?|""" +
            """[cč][ií]slo\s+[uú][cč]tu\s+pl[aá]tce:?|""" +
            """[cč][ií]slo\s+[uú][cč]tu\s+p[rř][ií]kazce:?|""" +
            """zpr[aá]va\s+pro\s+p[rř][ií]jemce:?|""" +
            """popis\s+transakce:?|""" +
            """detaily\s+platby:?|""" +
            """n[aá]zev\s+proti[uú][cč]tu:?|""" +
            """m[ií]sto:?|""" +
            """n[aá]kup:?|""" +
            """[cč][ií]slo\s+karty:?|""" +
            """datum\s+a\s+[cč]as:?|""" +
            """p[rř][ií]kazce:?|""" +
            """pl[aá]tce:?|""" +
            """majitel\s+[uú][cč]tu:?""" +
            """)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    private val CONSOLIDATED_TOKEN_NOISE_REGEX = Regex(
        """(?<![\p{L}\d])(?:""" +
            """\d{6}[A-Za-z0-9]\d{8,}|""" +
            """0{3,}\d*|""" +
            """\d{12,}|""" +
            """\d{1,2}:\d{2}(?::\d{2})?|""" +
            """\d{1,2}[./-]\d{1,2}(?:[./-]\d{2,4})?|""" +
            """czk|""" +
            """k[čc]""" +
            """)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    private val LEADING_DAY_MONTH_REGEX = Regex("""^\s*\d{1,2}\s+\d{1,2}\s+""")
    private val LEADING_PUNCT_REGEX = Regex("""^[\s\-:,./]+""")
    private val BLOCK_FULL_DATE_REGEX = Regex("""(?<![\p{L}\d])\d{1,2}[./-]\d{1,2}[./-]\d{4}(?![\p{L}\d])""")
    private val NUMERIC_WORD_REGEX = Regex("""^[+\-\u2212\u2013\u2014]?\d+([.,]\d+)?$""")
    private val METADATA_LINE_LABELS_REGEX = Regex(
        """(?i)(?<![\p{L}\d])(?:czk|k[čc]|variabiln[ií]\s+symbol|konstantn[ií]\s+symbol|specifick[yý]\s+symbol|zpr[aá]va\s+pro\s+p[rř][ií]jemce|popis\s+transakce|detaily\s+platby|trval[yý]\s+p[rř][ií]kaz\s+k\s+[uú]hrad[eě]|trval[yý]\s+p[rř][ií]kaz)(?![\p{L}\d])"""
    )

    fun cleanPaymentDescription(
        raw: String,
        householdNames: Collection<String> = emptyList()
    ): String {
        if (raw.isBlank()) return ""
        var text = raw
        if (householdNames.isNotEmpty()) {
            text = stripConfiguredPayerNames(text, householdNames)
        }
        text = CONSOLIDATED_BOILERPLATE_REGEX.replace(text, " ")
        text = CONSOLIDATED_TOKEN_NOISE_REGEX.replace(text, " ")
        text = LEADING_DAY_MONTH_REGEX.replace(text, " ")
        text = LEADING_PUNCT_REGEX.replace(text, " ")
        text = StatementTextDecoder.MULTI_SPACE_REGEX.replace(text, " ").trim()
        return text.ifBlank { raw.trim() }
    }

    private fun stripConfiguredPayerNames(text: String, householdNames: Collection<String>): String {
        val validNames = householdNames.map { it.trim() }.filter { it.length >= 3 }
        if (validNames.isEmpty()) return text
        var result = text
        for (name in validNames) {
            val escaped = Regex.escape(name)
            val payerPrefixPattern = Regex(
                """(?i)(?<![\p{L}\d])(?:p[rř][ií]kazce|pl[aá]tce|majitel\s+[uú][cč]tu)\s*:\s*$escaped(?![\p{L}\d])"""
            )
            result = payerPrefixPattern.replace(result, " ")
        }
        return result
    }

    fun cleanTransactionDescription(
        blockText: String,
        @Suppress("UNUSED_PARAMETER") dateStr: String,
        @Suppress("UNUSED_PARAMETER") amounts: List<Double>,
        account: String,
        vs: String,
        householdNames: Collection<String> = emptyList()
    ): String {
        var clean = BLOCK_FULL_DATE_REGEX.replace(blockText, " ")
        if (account.isNotBlank()) clean = clean.replace(account, " ")
        if (vs.isNotBlank()) clean = clean.replace(vs, " ")
        clean = METADATA_LINE_LABELS_REGEX.replace(clean, " ")
        clean = cleanPaymentDescription(clean, householdNames)

        val words = StatementTextDecoder.MULTI_SPACE_REGEX.split(clean)
            .asSequence()
            .map { it.trim() }
            .filter { w ->
                w.isNotBlank() &&
                    !NUMERIC_WORD_REGEX.matches(w) &&
                    w != "+" && w != "-" && w != "/" && w != "CZK" && w != "Kč"
            }
            .toList()
        val result = words.joinToString(" ").trim()
        val finalClean = LEADING_DAY_MONTH_REGEX.replace(result, "").trim()
        return finalClean.ifBlank {
            val folded = StatementTextDecoder.foldHeader(blockText)
            if (folded.contains("trvaly")) "Trvalý příkaz" else blockText.trim()
        }
    }
}
