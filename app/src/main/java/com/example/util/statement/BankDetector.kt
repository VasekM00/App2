package com.example.util.statement

import com.example.util.BankType
import java.util.Locale

internal object BankDetector {

    private val FIO_WORD_REGEX = Regex("""\bfio\b""")
    private val STRANA_PAGE_REGEX = Regex("""\bstrana\b(?!\s*:)""", RegexOption.IGNORE_CASE)
    private val DATE_BLOCKS_START_REGEX = Regex("""^\s*(\d{1,4})[./-]\s*(\d{1,2})(?:[./-]\s*(\d{2,4}))?(?![\p{L}\d])""")

    private val ACCOUNT_CODE_PATTERNS = listOf(
        Regex("""/\s*0100\b""") to BankType.KOMERCNI_BANKA,
        Regex("""/\s*0300\b""") to BankType.CSOB,
        Regex("""/\s*0600\b""") to BankType.MONETA,
        Regex("""/\s*0800\b""") to BankType.CESKA_SPORITELNA,
        Regex("""/\s*2010\b""") to BankType.FIO,
        Regex("""/\s*2250\b""") to BankType.CREDITAS,
        Regex("""/\s*2700\b""") to BankType.UNICREDIT,
        Regex("""/\s*3030\b""") to BankType.AIR_BANK,
        Regex("""/\s*4000\b""") to BankType.MAX_BANKA,
        Regex("""/\s*5500\b""") to BankType.RAIFFEISENBANK,
        Regex("""/\s*5800\b""") to BankType.JT,
        Regex("""/\s*6000\b""") to BankType.PPF,
        Regex("""/\s*6100\b""") to BankType.EQUA,
        Regex("""/\s*6210\b""") to BankType.MBANK,
        Regex("""/\s*8040\b""") to BankType.OBERBANK
    )

    private val NAME_SIGNATURES = listOf(
        "moneta money bank" to BankType.MONETA,
        "moneta" to BankType.MONETA,
        "ceskoslovenska obchodni banka" to BankType.CSOB,
        "csob" to BankType.CSOB,
        "mbank" to BankType.MBANK,
        "ceska sporitelna" to BankType.CESKA_SPORITELNA,
        "sporitelna" to BankType.CESKA_SPORITELNA,
        "komercni banka" to BankType.KOMERCNI_BANKA,
        "fio banka" to BankType.FIO,
        "raiffeisenbank" to BankType.RAIFFEISENBANK,
        "raiffeisen" to BankType.RAIFFEISENBANK,
        "air bank" to BankType.AIR_BANK,
        "unicredit" to BankType.UNICREDIT,
        "creditas" to BankType.CREDITAS,
        "max banka" to BankType.MAX_BANKA,
        "expobank" to BankType.MAX_BANKA,
        "partners banka" to BankType.PARTNERS,
        "ppf banka" to BankType.PPF,
        "j&t banka" to BankType.JT,
        "jt banka" to BankType.JT,
        "equa bank" to BankType.EQUA,
        "ing bank" to BankType.ING,
        "oberbank" to BankType.OBERBANK,
        "revolut" to BankType.REVOLUT,
        "wise europe" to BankType.WISE,
        "wise payments" to BankType.WISE
    )

    fun isIgnoredDateContext(line: String): Boolean {
        val folded = StatementTextDecoder.foldHeader(line)
        if (folded.contains("protistrana") || folded.contains("protiucet")) {
            return false
        }
        val isPageStrana = STRANA_PAGE_REGEX.containsMatchIn(folded) && !folded.contains("protistrana")
        return folded.contains("obdobi") ||
            folded.contains("vystaveni") ||
            folded.contains("vypis ze dne") ||
            folded.contains("datum tisku") ||
            folded.contains("platnost do") ||
            folded.contains("zustatek") ||
            folded.contains("konecny stav") ||
            folded.contains("pocatecni") ||
            folded.contains("vyhotoveno") ||
            isPageStrana ||
            folded.contains("obchodni misto banky") ||
            folded.contains("souhrnne zuctovani")
    }

    fun detectBankType(lines: List<String>): BankType {
        return detectBankFromText(StatementTextDecoder.foldHeader(lines.take(30).joinToString(" \n ")), lines)
    }

    fun detectPdfBankType(fullText: String, lines: List<String>): BankType {
        val headerSample = lines.take(40).joinToString("\n").ifBlank { fullText.take(3000) }
        return detectBankFromText(StatementTextDecoder.foldHeader(headerSample), lines.take(40))
    }

    fun detectBankFromText(folded: String, lines: List<String>): BankType {
        val headerIdx = lines.take(15).indexOfFirst { line ->
            val f = StatementTextDecoder.foldHeader(line)
            (f.contains("datum") || f.contains("date")) &&
                (f.contains("castka") || f.contains("objem") || f.contains("amount"))
        }
        val headerAndPreambleLines = if (headerIdx >= 0) {
            lines.take(headerIdx + 1)
        } else {
            val preTx = lines.take(15).takeWhile { line ->
                !DATE_BLOCKS_START_REGEX.containsMatchIn(line) ||
                    CzechValueParsers.extractAllAmounts(line).isEmpty() ||
                    isIgnoredDateContext(line)
            }
            preTx.ifEmpty { lines.take(8) }
        }
        val nameScanText = StatementTextDecoder.foldHeader(headerAndPreambleLines.joinToString(" \n ")).ifBlank { folded }

        for ((keyword, bank) in NAME_SIGNATURES) {
            if (nameScanText.contains(keyword)) return bank
        }
        if (FIO_WORD_REGEX.containsMatchIn(nameScanText)) return BankType.FIO

        val headerText = StatementTextDecoder.foldHeader(lines.take(30).joinToString(" | "))
        if (headerText.contains("#datum operace") || headerText.contains("#popis transakce")) return BankType.MBANK
        if (headerText.contains("cislo protiuctu") || headerText.contains("banka protiuctu") || headerText.contains("nazev protiuctu")) {
            return BankType.MONETA
        }
        if (headerText.contains("cislo uctu protistrany") || headerText.contains("nazev protistrany")) return BankType.CSOB
        if (headerText.contains("cislo uctu") && headerText.contains("smer")) return BankType.CESKA_SPORITELNA
        if (headerText.contains("objem") && headerText.contains("protiucet")) return BankType.FIO
        if (headerText.contains("started date") && headerText.contains("completed date") && headerText.contains("state") && headerText.contains("currency")) {
            return BankType.REVOLUT
        }

        val preambleLines = lines.take(15).takeWhile { line ->
            val f = StatementTextDecoder.foldHeader(line)
            !(f.contains("datum") && (f.contains("castka") || f.contains("objem")))
        }
        val preambleText = StatementTextDecoder.foldHeader(preambleLines.joinToString(" "))
        for ((pattern, bank) in ACCOUNT_CODE_PATTERNS) {
            if (pattern.containsMatchIn(preambleText)) return bank
        }

        val delim = StatementTextDecoder.detectCsvDelimiter(lines)
        val counterpartyColIndices = if (headerIdx >= 0) {
            StatementTextDecoder.splitLine(lines[headerIdx], delim).mapIndexedNotNull { idx, col ->
                val cf = StatementTextDecoder.foldHeader(col)
                if (cf.contains("protiucet") || cf.contains("protistran")) idx else null
            }.toSet()
        } else emptySet()

        val filteredLines = lines.take(40).mapIndexed { idx, line ->
            if (idx > headerIdx && counterpartyColIndices.isNotEmpty()) {
                StatementTextDecoder.splitLine(line, delim)
                    .filterIndexed { cIdx, _ -> cIdx !in counterpartyColIndices }
                    .joinToString(" ")
            } else {
                line
            }
        }
        val codeText = StatementTextDecoder.foldHeader(filteredLines.joinToString(" "))
        for ((pattern, bank) in ACCOUNT_CODE_PATTERNS) {
            if (pattern.containsMatchIn(codeText)) return bank
        }
        return BankType.GENERIC
    }

    fun bankFromAccountCode(code: String?): BankType? = when (code?.trim()) {
        "0100" -> BankType.KOMERCNI_BANKA
        "0300" -> BankType.CSOB
        "0600" -> BankType.MONETA
        "0800" -> BankType.CESKA_SPORITELNA
        "2010" -> BankType.FIO
        "2250" -> BankType.CREDITAS
        "2700" -> BankType.UNICREDIT
        "3030" -> BankType.AIR_BANK
        "4000" -> BankType.MAX_BANKA
        "5500" -> BankType.RAIFFEISENBANK
        "5800" -> BankType.JT
        "6000" -> BankType.PPF
        "6100" -> BankType.EQUA
        "6210" -> BankType.MBANK
        "8040" -> BankType.OBERBANK
        else -> null
    }

    fun friendlyBankName(bank: BankType): String = when (bank) {
        BankType.MONETA -> "Moneta"
        BankType.CSOB -> "ČSOB"
        BankType.MBANK -> "mBank"
        BankType.CESKA_SPORITELNA -> "Česká spořitelna"
        BankType.KOMERCNI_BANKA -> "Komerční banka"
        BankType.FIO -> "Fio banka"
        BankType.RAIFFEISENBANK -> "Raiffeisenbank"
        BankType.AIR_BANK -> "Air Bank"
        BankType.UNICREDIT -> "UniCredit"
        BankType.CREDITAS -> "Banka Creditas"
        BankType.MAX_BANKA -> "Max banka"
        BankType.PARTNERS -> "Partners Banka"
        BankType.PPF -> "PPF banka"
        BankType.JT -> "J&T Banka"
        BankType.EQUA -> "Equa bank"
        BankType.ING -> "ING Bank"
        BankType.OBERBANK -> "Oberbank"
        BankType.REVOLUT -> "Revolut"
        BankType.WISE -> "Wise"
        BankType.GENERIC -> "Bank"
    }

    fun friendlyBankName(bankName: String): String {
        val norm = bankName.trim().uppercase(Locale.ROOT)
        return runCatching { BankType.valueOf(norm) }.getOrNull()?.let {
            if (it == BankType.GENERIC) bankName else friendlyBankName(it)
        } ?: when {
            norm.contains("MONETA") -> "Moneta"
            norm.contains("CSOB") || bankName.contains("ČSOB", ignoreCase = true) -> "ČSOB"
            norm.contains("MBANK") -> "mBank"
            norm.contains("CESKA_SPORITELNA") -> "Česká spořitelna"
            norm.contains("KOMERCNI_BANKA") -> "Komerční banka"
            norm.contains("FIO") -> "Fio banka"
            norm.contains("RAIFFEISENBANK") -> "Raiffeisenbank"
            norm.contains("AIR_BANK") -> "Air Bank"
            norm.contains("UNICREDIT") -> "UniCredit"
            norm.contains("CREDITAS") -> "Banka Creditas"
            norm.contains("REVOLUT") -> "Revolut"
            norm.contains("WISE") -> "Wise"
            else -> bankName
        }
    }

    fun bankShortBadge(bankName: String): String {
        val norm = bankName.trim().uppercase(Locale.ROOT)
        return when {
            norm.contains("MONETA") -> "MON"
            norm.contains("CSOB") || bankName.contains("ČSOB", ignoreCase = true) -> "ČSOB"
            norm.contains("MBANK") -> "mB"
            norm.contains("CESKA_SPORITELNA") -> "ČS"
            norm.contains("KOMERCNI_BANKA") -> "KB"
            norm.contains("FIO") -> "Fio"
            norm.contains("RAIFFEISENBANK") -> "RB"
            norm.contains("AIR_BANK") -> "Air"
            norm.contains("UNICREDIT") -> "UC"
            norm.contains("CREDITAS") -> "CR"
            norm.contains("MAX_BANKA") -> "MAX"
            norm.contains("PARTNERS") -> "PB"
            norm.contains("PPF") -> "PPF"
            norm.contains("JT") -> "J&T"
            norm.contains("EQUA") -> "EQ"
            norm.contains("ING") -> "ING"
            norm.contains("OBERBANK") -> "OB"
            norm.contains("REVOLUT") -> "Rev"
            norm.contains("WISE") -> "Wise"
            else -> bankName.take(3).uppercase(Locale.ROOT)
        }
    }

    fun bankColorHex(bank: BankType, isDark: Boolean): Long = when (bank) {
        BankType.MONETA -> 0xFF0F766EL // BrandTeal fallback
        BankType.CSOB -> 0xFFD97706L // BrandGold fallback
        BankType.MBANK -> if (isDark) 0xFFFB7185L else 0xFFBE123CL
        BankType.CESKA_SPORITELNA -> if (isDark) 0xFF60A5FAL else 0xFF1D4ED8L
        BankType.KOMERCNI_BANKA -> if (isDark) 0xFFF87171L else 0xFFB91C1CL
        BankType.FIO -> if (isDark) 0xFF4ADE80L else 0xFF15803DL
        BankType.RAIFFEISENBANK -> if (isDark) 0xFFFACC15L else 0xFFA16207L
        BankType.AIR_BANK -> if (isDark) 0xFFA3E635L else 0xFF4D7C0FL
        BankType.UNICREDIT -> if (isDark) 0xFFC084FCL else 0xFF7E22CEL
        BankType.CREDITAS -> if (isDark) 0xFF2DD4BFL else 0xFF0F766EL
        BankType.MAX_BANKA -> if (isDark) 0xFFC084FCL else 0xFF7E22CEL
        BankType.PARTNERS -> if (isDark) 0xFF22D3EEL else 0xFF00838FL
        BankType.PPF -> if (isDark) 0xFF38BDF8L else 0xFF0284C7L
        BankType.JT -> if (isDark) 0xFFD6B8A8L else 0xFF795548L
        BankType.EQUA -> if (isDark) 0xFFFB923CL else 0xFFEF6C00L
        BankType.ING -> if (isDark) 0xFFFB923CL else 0xFFE65100L
        BankType.OBERBANK -> if (isDark) 0xFF2DD4BFL else 0xFF0D9488L
        BankType.REVOLUT -> if (isDark) 0xFF38BDF8L else 0xFF0369A1L
        BankType.WISE -> if (isDark) 0xFF4ADE80L else 0xFF15803DL
        BankType.GENERIC -> 0xFF0F766EL
    }
}
