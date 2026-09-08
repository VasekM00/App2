package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * High-performance offline catalog and intelligence engine for Czech and international merchants.
 * Performs accent-insensitive, tokenized pattern matching across 250+ brands, chains, and service providers.
 */
object CzechMerchantCatalog {

    /**
     * Normalizes a merchant or payment description string:
     * - Removes Czech/Slovak diacritics (á -> a, č -> c, etc.)
     * - Converts to lowercase
     * - Strips non-alphanumeric noise, city names, and business entity abbreviations (s.r.o., a.s.)
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""
        val withoutAccents = Normalizer.normalize(rawText, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)

        // Replace non-alphanumeric punctuation with spaces (including * from gateways like GOPAY *...)
        val alphanumeric = withoutAccents.replace(Regex("""[^a-z0-9\s]"""), " ")

        // Remove common Czech city names, corporate suffixes, and terminal noise
        val cleanTokens = alphanumeric.split(Regex("""\s+""")).filter { token ->
            token !in CITY_NOISE && token !in CORPORATE_SUFFIXES && token.length > 1
        }

        return cleanTokens.joinToString(" ").trim()
    }

    private val CITY_NOISE = setOf(
        "praha", "prague", "brno", "ostrava", "plzen", "pilsen", "olomouc", "liberec",
        "hradec", "kralove", "budejovice", "zlin", "pardubice", "kladno",
        "most", "karvina", "opava", "jihlava", "teplice", "decin", "vary", "karlovy"
    )

    private val CORPORATE_SUFFIXES = setOf(
        "sro", "as", "spol", "gmbh", "ltd", "corp", "inc", "od", "cz", "com", "eu",
        "eshop", "shop", "cr", "czk", "mist", "misto", "terminal", "republika"
    )

    /**
     * Matches a transaction description against user custom overrides first,
     * then against the built-in merchant intelligence catalog.
     *
     * @param rawDescription The raw description, counterparty name, or message.
     * @param userOverrides User-defined overrides mapping merchant keywords/patterns to categories.
     * @return The identified BankTransactionType, or null if no catalog match is found.
     */
    fun matchCategory(
        rawDescription: String,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): BankTransactionType? {
        val norm = normalize(rawDescription)
        if (norm.isBlank()) return null

        // 1. Check user custom overrides first
        for ((pattern, category) in userOverrides) {
            val normPattern = normalize(pattern)
            if (normPattern.isNotBlank() && norm.contains(normPattern)) {
                return category
            }
        }

        // 2. Check built-in merchant rules
        for (rule in CATALOG_RULES) {
            if (rule.matches(norm)) {
                return rule.category
            }
        }

        return null
    }

    /**
     * Clean a raw transaction description to extract a concise search query for online lookup.
     */
    fun suggestMerchantSearchQuery(rawText: String): String {
        val clean = BankStatementImporter.cleanPaymentDescription(rawText)
        return clean.replace(Regex("""\b(od|cz|com|s\.r\.o\.|a\.s\.|brno|praha)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private data class MerchantRule(
        val category: BankTransactionType,
        val keywords: List<String>
    ) {
        fun matches(normalizedText: String): Boolean {
            val tokens = normalizedText.split(" ").filter { it.isNotBlank() }
            val padded = " $normalizedText "
            return keywords.any { kw ->
                if (kw.endsWith("*")) {
                    val prefix = kw.removeSuffix("*")
                    tokens.any { it.startsWith(prefix) }
                } else if (kw.contains(" ")) {
                    padded.contains(" $kw ")
                } else {
                    tokens.contains(kw)
                }
            }
        }
    }

    private val CATALOG_RULES = listOf(
        // ATM & Cash Withdrawals
        MerchantRule(
            BankTransactionType.ATM_CASH,
            listOf(
                "vyber bankomat", "vyber z bankomatu", "bankomat*", "atm", "cash withdrawal",
                "vyber hotovosti", "csob bankomat", "moneta bankomat", "kb bankomat",
                "airbank bankomat", "rb bankomat", "sporitelna bankomat", "fio bankomat", "euronet"
            )
        ),

        // Housing & Rent
        MerchantRule(
            BankTransactionType.HOUSING_RENT,
            listOf(
                "najem", "najemne", "cinze", "fond oprav", "svj", "sprava domu",
                "bydleni", "platba najmu", "najemce", "bytove druzstvo"
            )
        ),

        // Investments
        MerchantRule(
            BankTransactionType.INVESTMENT_PORTU,
            listOf("portu", "wood company", "wood & company", "degiro", "xtb", "interactive brokers", "trading 212")
        ),
        MerchantRule(
            BankTransactionType.INVESTMENT_DIP,
            listOf("patria dip", "fio dip", "dip investice", "dip patria", "dip")
        ),
        MerchantRule(
            BankTransactionType.INVESTMENT_DPS,
            listOf(
                "penzij*", "dps", "conseq", "generali penzijni", "allianz penzijni",
                "nn penzij*", "ceska sporitelna penzijni", "kb penzijni", "csob penzijni"
            )
        ),

        // Internal Accounts, Goals & Savings Transfers
        MerchantRule(
            BankTransactionType.INTERNAL_TRANSFER,
            listOf(
                "prevod z cile", "prevod cile", "prevod na msporeni", "prevod z msporeni",
                "prevod msporeni", "prevod na cil", "msporeni", "vlastni prevod",
                "prevod mezi ucty", "sporic* ucet", "prevod na sporeni", "prevod ze sporeni"
            )
        ),

        // Charity, Foundations & Public Benefit Donations
        MerchantRule(
            BankTransactionType.CHARITY_DONATION,
            listOf(
                "nadace sirius", "sirius", "clovek v tisni", "clovek tisni", "tisni", "clovekvtisni", "people in need",
                "skutecny darek", "dobry andel", "dobryandel", "pamet naroda", "post bellum",
                "svetluska", "paraple", "centrum paraple", "konto bariery", "nadace charty 77",
                "adra", "unicef", "kapka nadeje", "tereza maxova", "terezy maxove",
                "nadace via", "ceska katolicka charita", "charita ceska republika", "trikralova sbirka",
                "vybor dobre vule", "olgy havlove", "olga havlova", "krasa pomoci", "nase dite",
                "cesta domu", "pomocne tlapky", "lekari bez hranic", "medecins sans frontieres",
                "nadace partnerstvi", "diakonie", "sue ryder", "nadace neuron",
                "donio", "darujme", "chces me", "utulek", "nadac*", "charit*", "darovac*"
            )
        ),

        // Groceries & Food Markets
        MerchantRule(
            BankTransactionType.GROCERIES,
            listOf(
                "albert", "billa", "lidl", "tesco", "penny", "penny market", "kaufland",
                "globus", "jip", "makro", "tamda", "norma", "enapo", "flop", "hruska",
                "coop", "zabka", "sklizeno", "delmart", "rohlik", "kosik", "itesco",
                "country life", "svet bedynek", "nakup domu", "potraviny", "vecerka",
                "minipotraviny", "pekarstvi", "pekarna", "bakery", "antoninovo pekarstvi",
                "merhautovo pekarstvi", "kabat", "karlova pekarna", "reznictvi", "butcher",
                "uzeniny", "lahudky", "nase maso", "steinhauser", "kostelecke uzeniny",
                "farmarske", "rybarstvi", "zelinastvi", "ovoce a zelenina", "ovoce zelenina",
                "mlekarna", "syrarna"
            )
        ),

        // Transportation, Public Transit, Fuel & Ride Hailing
        MerchantRule(
            BankTransactionType.TRANSPORTATION,
            listOf(
                "ceske drahy", "drahy", "cd", "cd cz", "cd eshop", "cd vnitro", "regiojet", "student agency",
                "leo express", "arriva", "gw train", "dpp", "dpmb", "dpo", "pmdp", "dopravni podnik",
                "litacka", "pid litacka", "jizdenka", "idos", "flixbus", "idsok", "odis", "idsk",
                "ids jmk", "iredo", "duk", "korid", "bolt ride", "bolt scooter", "bolt", "uber",
                "liftago", "rekola", "nextbike", "lime", "dott", "hoppygo", "anytime", "car4way",
                "orlen", "benzina", "mol", "shell", "omv", "eurooil", "tank ono", "ono",
                "robin oil", "km prona", "avia", "pap oil", "f1 gas", "prim", "cerpaci stanice",
                "cerpaci", "benzin", "nafta", "lpg", "cng", "tesla supercharger", "pre odber",
                "cez drive", "eon drive", "ionity", "parkovani", "parkoviste", "easypark",
                "mpla", "zaparkuj", "dalnicni znamka", "edalnice", "myto", "czechtoll",
                "stk", "autoservis", "pneuservis", "automycka", "bestdrive", "auto kelly", "elit", "barum",
                "ryanair", "wizz air", "smartwings", "csa", "lufthansa", "easyjet", "eurowings",
                "british airways", "klm", "air france", "emirates", "kiwi com", "booking com", "airbnb"
            )
        ),

        // Subscriptions & Digital Media & Gaming & AI
        MerchantRule(
            BankTransactionType.SUBSCRIPTIONS_MEDIA,
            listOf(
                "spotify", "netflix", "youtube", "google play", "google apps", "google storage",
                "apple com bill", "itunes", "disney", "disney plus", "hbo max", "hbo",
                "deezer", "tidal", "voyo", "prima plus", "canal plus", "skyshowtime",
                "dafilms", "aerovod", "ivysilani", "kuki", "sledovanitv", "lepsi tv", "skylink",
                "audible", "audioteka", "patreon", "forendors", "herohero", "pickey", "substack",
                "medium", "denik n", "respekt", "hospodarske noviny", "seznam zpravy",
                "steam", "steampowered", "valve", "playstation", "sony interactive",
                "xbox", "microsoft", "nintendo", "epic games", "blizzard", "ea play", "ubisoft",
                "twitch", "discord", "openai", "chatgpt", "anthropic", "claude", "midjourney",
                "perplexity", "jetbrains", "github", "adobe", "notion", "canva", "figma",
                "icloud", "dropbox", "1password", "bitwarden", "proton", "nordvpn", "surfshark", "deepl"
            )
        ),

        // Dining, Cafes, Fast Food, Delivery & Pubs
        MerchantRule(
            BankTransactionType.DINING_RESTAURANT,
            listOf(
                "wolt", "foodora", "dame jidlo", "bolt food", "rebelbean", "anoda",
                "anoda cafe", "kavarn*", "kafe", "cafe", "coffee", "costa coffee", "costa",
                "starbucks", "crosscafe", "espresso", "bistro", "pohodicka", "kavarna pohodicka",
                "monogram", "industra", "buchta", "kafec", "skog", "mezzanine", "tchibo",
                "mamacoffee", "kofarna", "doubleshot", "dos mundos", "coffee source", "vnitroblock",
                "miners", "typika", "ema espresso", "onesip", "acid coffee",
                "mcdonalds", "mcdonald", "kfc", "burger king", "subway", "bageterie boulevard",
                "bageterie", "popeyes", "five guys", "burrito loco", "amici", "pizza hut", "dominos",
                "ugo freshbar", "fruitisimo", "dhaba beas", "loving hut", "restaurac*", "dalesice",
                "restaurace dalesice", "gopal", "restaurace gopal", "pivni burza", "hospoda",
                "pivnice", "prazdroj", "kozlovna", "kolkovna", "potrefena husa", "lokal",
                "pivovar", "pivovarska", "plzenska", "bernard pub", "budvarka", "staropramen",
                "cerna hora", "svijany", "zubr", "radegastovna", "hostinec", "vycep",
                "doner kebab", "kebab", "gashi", "kebab point", "freshbox", "pizzeria", "pizza",
                "burger", "sushi", "pho", "ramen", "nudle", "vietnam", "coloseum", "grosseto", "l osteria",
                "cukrarn*", "zmrzlina", "gelato", "ovocny svetozor", "cukrar skala", "puro gelato",
                "angelato", "creme de la creme", "zmrzlinar", "trdelnik", "vinoteka", "vinarstvi", "bar", "pub"
            )
        ),

        // Health, Pharmacy, Wellness & Drugstores
        MerchantRule(
            BankTransactionType.HEALTH_DRUGSTORE,
            listOf(
                "dm drogerie", "dm markt", "dm", "rossmann", "teta drogerie", "teta",
                "dr max", "drmax", "benu", "pilulka", "lekarna", "moje lekarna", "magistra",
                "gigalekarna", "nemocnice", "poliklinika", "ordinace", "zubni", "stomatol*",
                "ortodoncie", "dentalni hygiena", "optik", "grand optical", "fokus optik",
                "eiffel optic", "lentiamo", "alensa", "fyzio", "fitness", "posilovna",
                "form factory", "fitinn", "next move", "john reed", "sauna", "saunia", "infinit",
                "aquapalace", "masaze", "sephora", "notino", "douglas", "marionnaud", "manufaktura",
                "yves rocher", "lush", "l occitane", "havlikova", "synlab", "aeskulab"
            )
        ),

        // Shopping, Electronics, Furniture, Fashion & Outdoor
        MerchantRule(
            BankTransactionType.SHOPPING_GOODS,
            listOf(
                "alza", "datart", "czc", "smarty", "istyle", "m zone", "apple store", "electroworld",
                "electro world", "mall cz", "mall", "space", "planeo", "ts bohemia", "mironet", "mp cz",
                "conrad", "okay elektro", "ikea", "jysk", "mobelix", "xxxlutz", "asko", "sconto",
                "bonami", "biano", "favi", "butlers", "kave home", "zara home", "bauhaus",
                "hornbach", "obi", "baumax", "mountfield", "unihobby", "stavebniny dek", "prodoma",
                "tescoma", "orion", "4camping", "decathlon", "sportisimo", "intersport", "hervis",
                "hanibal", "rock point", "hudy", "husky", "endorphin", "sanasport", "gymbeam",
                "aktin", "vilgain", "brainmarket", "ronnie", "bikero", "koloshop",
                "zalando", "about you", "answear", "h m", "zara", "reserved", "mohito",
                "cropp", "house", "sinsay", "c a", "new yorker", "mango", "peek cloppenburg",
                "marks spencer", "bershka", "pull bear", "stradivarius", "massimo dutti",
                "cos", "levis", "gant", "tommy hilfiger", "calvin klein", "deichmann", "ccc",
                "eobuv", "humanic", "foot locker", "snipes", "sizeer", "bata", "rieker",
                "vivobarefoot", "barefoot", "vasky", "knihy dobrovsky", "dobrovsky", "neoluxor",
                "luxor", "kosmas", "megaknihy", "martinus", "palmknihy", "bambule", "sparkys",
                "dracik", "lego", "pompo", "hamleys", "feedo", "funbaby", "koh i noor", "sevt",
                "papirnictvi", "mcpen", "shoptet", "baktoma", "action", "pepco", "kik",
                "flying tiger", "tedi", "primark", "halfprice", "tk maxx", "allegro", "amazon",
                "temu", "aliexpress", "vinted", "ebay"
            )
        ),

        // Services & Utilities
        MerchantRule(
            BankTransactionType.SERVICES_UTILITIES,
            listOf(
                "vodafone", "o2", "t mobile", "nordic telecom", "poda", "nej cz", "cetin",
                "cez", "pre", "e on", "innogy", "mnd", "prazska plynarenska", "centropol",
                "epet", "veolia", "pvk", "vodovody", "zivotni pojisteni", "pojist*",
                "kooperativa", "generali", "ceska pojistovna", "allianz", "uniqa", "pillow",
                "direct", "slavia pojistovna", "pvzp", "vzp", "ozp", "vozp", "cpzp", "rbp",
                "poplatek za vedeni", "vedeni uctu", "bankovni poplatek", "poplatek za kartu",
                "salon", "salon galapa", "kadernictvi", "barber", "cistirna", "pradelna",
                "kvetinarstvi", "kvetiny", "zamecnik", "hodinovy manzel", "posta", "ceska posta",
                "balikovna", "zasilkovna", "packeta", "dpd", "ppl", "gls", "dhl", "fedex", "ups"
            )
        )
    )
}
