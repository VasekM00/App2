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

    fun normalizeSimple(rawText: String): String {
        if (rawText.isBlank()) return ""
        val withoutAccents = Normalizer.normalize(rawText, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
        return withoutAccents.replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
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

    private const val MIN_OVERRIDE_PATTERN_LENGTH = 3

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
        val simpleNorm = normalizeSimple(rawDescription)
        if (norm.isBlank() && simpleNorm.isBlank()) return null

        val normTokens = norm.split(" ").filter { it.isNotBlank() }
        val simpleTokens = simpleNorm.split(" ").filter { it.isNotBlank() }

        // 1. Check user custom overrides first (allow 2-letter tokens like "o2", "dm", "cd", "dp"
        //    when matched as isolated whole words, and 3+ char patterns via substring).
        for ((pattern, category) in userOverrides) {
            val normPattern = normalize(pattern)
            val simplePattern = normalizeSimple(pattern)
            val tokenMatch = (normPattern.length >= 2 && normTokens.contains(normPattern)) ||
                    (simplePattern.length >= 2 && simpleTokens.contains(simplePattern))
            val normSubstringMatch = normPattern.length >= MIN_OVERRIDE_PATTERN_LENGTH && norm.contains(normPattern)
            val simpleSubstringMatch = simplePattern.length >= MIN_OVERRIDE_PATTERN_LENGTH && simpleNorm.contains(simplePattern)
            if (tokenMatch || normSubstringMatch || simpleSubstringMatch) {
                return category
            }
        }

        // 2. Check built-in merchant rules
        for (rule in CATALOG_RULES) {
            if (rule.matches(norm, simpleNorm)) {
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
        fun matches(normalizedText: String, simpleText: String = normalizedText): Boolean {
            val tokens = normalizedText.split(" ").filter { it.isNotBlank() }
            val simpleTokens = simpleText.split(" ").filter { it.isNotBlank() }
            val padded = " $normalizedText "
            val simplePadded = " $simpleText "
            return keywords.any { kw ->
                val normKw = normalizeSimple(kw)
                if (kw.endsWith("*")) {
                    val prefix = kw.removeSuffix("*")
                    tokens.any { it.startsWith(prefix) } || simpleTokens.any { it.startsWith(prefix) }
                } else if (normKw.contains(" ")) {
                    padded.contains(" $normKw ") || simplePadded.contains(" $normKw ")
                } else {
                    tokens.contains(kw) || simpleTokens.contains(kw)
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
            listOf(
                "portu", "wood company", "wood & company", "wood retail", "wood retail investments",
                "76788295", "76788295 2010",
                "7678876788", "7678876788 2010",
                "2038012508", "2038012508 2600",
                "2555410109", "2555410109 2600",
                "518746050", "518746050 2700",
                "xtb", "trade brokers", "xtb sa",
                "degiro", "interactive brokers", "trading 212"
            )
        ),
        MerchantRule(
            BankTransactionType.INVESTMENT_DIP,
            listOf("patria dip", "fio dip", "dip investice", "dip patria", "dip")
        ),
        MerchantRule(
            BankTransactionType.INVESTMENT_DPS,
            listOf(
                "5005004433", "5005004433 0800", "5005004433/0800",
                "nn penzijni spolecnost", "nn penzijni", "nn penze",
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

        // Groceries, Bakeries, Fresh Markets & Butchers
        MerchantRule(
            BankTransactionType.GROCERIES,
            listOf(
                // Major Supermarket & Hypermarket Chains
                "albert", "billa", "lidl", "tesco", "penny", "penny market", "kaufland",
                "globus", "jip", "makro", "tamda", "tamda foods", "norma", "enapo", "flop", "hruska",
                "coop", "zabka", "delmart", "rohlik", "kosik", "itesco",
                // Brno & Regional Artisanal Bakeries
                "william thomas", "wt bakery", "william thomas artisan", "william thomas bakery",
                "carlini", "pekarstvi carlini", "makovec", "pekarstvi makovec",
                "krizak", "pekarstvi krizak", "klasterni pekarna",
                "krupka", "pekarstvi krupka", "kulhanek", "pekarstvi kulhanek",
                "karlova pekarna", "racek", "pekarna racek", "svoboda pekarstvi", "chleba se soli",
                "antoninovo pekarstvi", "merhautovo pekarstvi", "kabat", "pekarstvi", "pekarna", "bakery",
                // Brno Farmers Markets, Organic & Zero-Waste
                "zelny trh", "zelnak", "trhy na zelnaku", "farmarske", "farmarske trhy",
                "sklizeno", "sklizeno josefska", "sklizeno campus",
                "brana ke zdravi", "nasyp", "bezobalu", "bio svetylko", "svetylko",
                "country life", "svet bedynek", "nakup domu", "potraviny", "vecerka", "minipotraviny",
                // Brno & Czech Artisanal Butchers, Cheese, Wine & Fish
                "mikrofarma", "u krejcara", "reznictvi u krejcara", "ksandr", "reznictvi ksandr",
                "steinhauser", "kostelecke uzeniny", "nase maso", "reznictvi", "butcher", "uzeniny", "lahudky",
                "pan syr", "cheesy", "syry od certic", "la formaggeria", "gran moravia",
                "rybarstvi pohorelice", "rybarstvi", "zelinastvi", "ovoce a zelenina", "ovoce zelenina",
                "mlekarna", "syrarna", "moravska banka vin", "vinna galerie", "u zizniveho mnicha",
                "vinoteka", "vinarstvi"
            )
        ),

        // Transportation, Public Transit, Fuel & Ride Hailing
        MerchantRule(
            BankTransactionType.TRANSPORTATION,
            listOf(
                // Railways & Intercity
                "ceske drahy", "drahy", "cd", "cd cz", "cd eshop", "cd vnitro", "regiojet", "student agency",
                "leo express", "arriva", "gw train", "flixbus", "idos",
                // Brno & JMK Public Transit (DPMB, IDS JMK, KORDIS)
                "dpmb", "pipni a jed", "beep go", "beep and go", "brnopas",
                "ids jmk", "kordis jmk", "kordis", "salina",
                "dpp", "litacka", "pid litacka", "dpo", "pmdp", "dopravni podnik",
                "jizdenka", "idsok", "odis", "idsk", "iredo", "duk", "korid",
                // Micro-Mobility & Ride Hailing
                "bolt ride", "bolt scooter", "bolt", "uber", "liftago", "rekola", "nextbike",
                "lime", "dott", "hoppygo", "anytime", "car4way",
                // Parking (Brno Resident Zones & Garages)
                "parkovani brno", "modre zony", "b parking", "domini park", "pinki park",
                "parkovani", "parkoviste", "easypark", "mpla", "zaparkuj",
                // Fuel & EV Charging
                "orlen", "benzina", "mol", "shell", "omv", "eurooil", "tank ono", "ono",
                "robin oil", "km prona", "avia", "pap oil", "f1 gas", "prim", "cerpaci stanice",
                "cerpaci", "benzin", "nafta", "lpg", "cng", "tesla supercharger", "pre odber",
                "cez drive", "eon drive", "ionity",
                // Highway Tolls, Vignette & Garages
                "dalnicni znamka", "edalnice", "myto", "czechtoll",
                "stk", "autoservis", "pneuservis", "automycka", "bestdrive", "auto kelly", "elit", "barum",
                // Airlines & Travel
                "ryanair", "wizz air", "smartwings", "csa", "lufthansa", "easyjet", "eurowings",
                "british airways", "klm", "air france", "emirates", "kiwi com", "booking com", "airbnb",
                "letiste turany"
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

        // Dining, Cafes, Specialty Coffee, Bistros, Delivery & Pubs
        MerchantRule(
            BankTransactionType.DINING_RESTAURANT,
            listOf(
                // Food Delivery
                "wolt", "foodora", "dame jidlo", "bolt food",
                // Brno Specialty Coffee, Roasters & Cafes
                "rebelbean", "rebel bean", "vlnena", "jantarova",
                "industra", "industra coffee",
                "monogram", "monogram espresso",
                "kafe mitte", "hostel mitte", "mitte",
                "punkt", "punkt cafe", "kafe punkt",
                "skog", "skog urban",
                "podnik cafe", "podnik cafe bar", "podnik",
                "kafec", "kafec orli", "kafec uvoz",
                "buchta caffe", "buchta cafe",
                "pelisek", "kocici kavarna",
                "mezzanine", "mezzanine cafe",
                "cafe atlas", "kavarna atlas",
                "cafe falk", "kavarna falk",
                "kavarna spolek", "spolek",
                "cafe tungsram", "tungsram",
                "cafe pilat", "pilat",
                "cafe momenta", "momenta",
                "gills coffee", "gills", "gill s",
                "rusty nails", "rusty nails coffee",
                "motmot", "motmot coffee",
                "dot cafe", "kafe v rozkvetu",
                "coffee trail", "piknik box",
                "the roses", "roses beer coffee",
                "kofikofi", "kofi kofi",
                "chleba maslo", "chleba a maslo",
                "kafarna", "podnebi", "kavarna podnebi",
                "kavarna trojka", "kavarna pole", "caffe del saggio", "kavarna lucerna",
                "anoda", "anoda cafe", "kavarna pohodicka", "pohodicka",
                // Brno Pastry, Cakes, Donuts & Artisan Ice Cream
                "sorry peceme jinak", "sorry peceme", "mlsna holka",
                "kobliha", "kobliharna",
                "martinak", "cukrarstvi martinak",
                "kolbaba", "cukrarna kolbaba",
                "bozsky kopecek",
                "cukrarna vetrnik", "vetrnik",
                "tutti frutti", "cukrarna blaha", "cukrarna aida",
                "zmrzlinove kralovstvi",
                // Other Czech Specialty Coffee & Chains
                "costa coffee", "costa", "starbucks", "crosscafe", "tchibo",
                "mamacoffee", "kofarna", "doubleshot", "dos mundos", "coffee source", "vnitroblock",
                "miners", "typika", "ema espresso", "onesip", "acid coffee",
                "kavarn*", "kafe", "cafe", "coffee", "espresso",
                // Brno Bars, Cocktails & Nightlife (Lidi z Baru & Iconic Spots)
                "bar ktery neexistuje", "super panda circus", "panda circus",
                "4pokoje", "4 pokoje", "ctyri pokoje", "slast", "bar slast",
                "whiskey bar", "zazrak bar", "atelier bar", "atelier bistro",
                "element bar", "element bistro", "element restaurant",
                "hangar bar", "two faces", "charlies square", "charlies mill",
                // Brno Bistros, Burgers, Casual & Fine Dining
                "bucheck", "burger inn", "fryends",
                "eggo truck", "eggo bistro", "eggo",
                "buns", "buns burger", "forkys", "forky s", "forky",
                "bistro franz", "bistro soul", "bistro bastardo", "bastardo",
                "die kuche", "traubka", "jakoby",
                "u dreveneho orla", "dreveny orel", "u dreveneho vlka", "dreveny vlk",
                "u trech certu", "trech certu",
                "monte bu", "pavillon", "kohout na vine", "borgo agnese",
                "castellana trattoria", "castellana",
                // Brno Breweries, Pubs & Taprooms
                "vycep na stojaka", "na stojaka", "stojak",
                "pivni burza", "pivovar pegas", "pegas",
                "stopkova plzenska", "stopkova",
                "lokal u caipla", "u caipla",
                "ochutnavkova pivnice", "ochutnavkova",
                "malt worm", "zelena kocka", "mamut pub", "mamut",
                "u seminaru", "hladinka snyt", "hladinka a snyt",
                "poupata", "pivovarska poupata",
                "u poutnika", "hostinec u poutnika",
                "starobrno", "pivovarska starobrno",
                "moravska chalupa", "pivnice u capa", "u capa",
                "stredoveka krcma", "axman", "flek", "u fleku",
                // Asian, Ethnic & Vegetarian in Brno & CZ
                "ramen", "yamachan", "ca phe co", "viet sen",
                "cao fat boys", "fat boys", "pho eden", "pho bo", "bun bo nam bo",
                "koishi", "koishi fish", "sushi clock", "manna",
                "padagali", "annapurna", "namaskar",
                "aarav", "aarav brno", "aarav brnostredcern", "indicka restaurace aarav", "taj mahal",
                "restaurace gopal", "gopal", "dhaba beas", "loving hut",
                "doner kebab", "kebab point", "gashi",
                // Czech Pub Chains & Breweries
                "prazdroj", "kozlovna", "kolkovna", "potrefena husa", "lokal",
                "pivovar", "pivovarska", "plzenska", "bernard pub", "budvarka", "staropramen",
                "cerna hora", "svijany", "zubr", "radegastovna", "dalesice", "restaurace dalesice",
                "hostinec", "vycep", "hospoda", "pivnice",
                // Fast Food & Pizzerias
                "mcdonalds", "mcdonald", "kfc", "burger king", "subway", "bageterie boulevard",
                "bageterie", "popeyes", "five guys", "burrito loco", "amici", "pizza hut", "dominos",
                "ugo freshbar", "fruitisimo", "freshbox", "pizzeria", "pizza", "burger", "sushi",
                "pho", "nudle", "vietnam", "coloseum", "grosseto", "l osteria", "bistro", "restaurac*",
                // Gelato, Pastry & Sweets
                "cukrarn*", "zmrzlina", "gelato", "ovocny svetozor", "cukrar skala", "puro gelato",
                "angelato", "creme de la creme", "zmrzlinar", "trdelnik", "bar", "pub"
            )
        ),

        // Health, Pharmacy, Wellness, Pools & Drugstores
        MerchantRule(
            BankTransactionType.HEALTH_DRUGSTORE,
            listOf(
                // Drugstores
                "dm drogerie", "dm markt", "dm", "rossmann", "teta drogerie", "teta",
                // Pharmacies (including Brno's Chytrá lékárna)
                "chytra lekarna", "u cerveneho raka", "u bileho orla",
                "dr max", "drmax", "benu", "pilulka", "lekarna", "moje lekarna", "magistra", "gigalekarna",
                // Brno Sports, Municipal Baths & STAREZ
                "starez", "starez arna", "kravi hora", "riviera", "koupaliste riviera",
                "aquapark kohoutovice", "bazen luzanky", "luzanky",
                // Wellness, Saunas & Fitness in Brno & CZ
                "infinit maximus", "maximus resort", "infinit", "saunia", "aquapalace",
                "big one fitness", "big1fitness", "zone4you",
                "form factory", "fitinn", "next move", "john reed", "fitness", "posilovna", "sauna", "masaze",
                // Medical, Dental, Labs & Hospitals
                "fn", "fnusa", "bohunice", "u svate anny", "nemocnice", "poliklinika", "ordinace",
                "zubni", "stomatol*", "ortodoncie", "dentalni hygiena", "fyzio",
                "synlab", "aeskulab", "euc klinika",
                // Optics & Cosmetics
                "optik", "grand optical", "fokus optik", "eiffel optic", "lentiamo", "alensa",
                "sephora", "notino", "douglas", "marionnaud", "manufaktura",
                "yves rocher", "lush", "l occitane", "havlikova"
            )
        ),

        // Shopping, Electronics, Furniture, Fashion, Books & Outdoor
        MerchantRule(
            BankTransactionType.SHOPPING_GOODS,
            listOf(
                // Brno Shopping Centers & Malls
                "vankovka", "galerie vankovka", "olympia", "avion", "avion shopping park",
                "kralovo pole", "nc kralovo pole", "krpole", "spalicek", "velky spalicek",
                "letmo", "oc letmo", "palac omega",
                // Brno Local Boutiques, Concept Stores & Design
                "place store", "restart shop", "ty identity", "pokojovky", "kytky od pepy",
                "kvetinarstvi", "kvetiny", "kvetiny huszarova", "florist", "kytky",
                // Books & Stationery (including Brno's Barvič a Novotný and Ševčík)
                "barvic a novotny", "barvic", "sevcik knihy", "papirnictvi sevcik",
                "knihy dobrovsky", "dobrovsky", "neoluxor", "luxor", "kosmas", "megaknihy", "martinus", "palmknihy",
                "koh i noor", "sevt", "papirnictvi", "mcpen",
                // Electronics & Computers
                "alza", "datart", "czc", "smarty", "istyle", "m zone", "apple store", "electroworld",
                "electro world", "mall cz", "mall", "space", "planeo", "ts bohemia", "mironet", "mp cz",
                "conrad", "okay elektro",
                // Furniture, Home & DIY
                "ikea", "jysk", "mobelix", "xxxlutz", "asko", "sconto", "bonami", "biano", "favi",
                "butlers", "kave home", "zara home", "bauhaus", "hornbach", "obi", "baumax",
                "mountfield", "unihobby", "stavebniny dek", "prodoma", "tescoma", "orion",
                // Sports & Outdoor
                "4camping", "decathlon", "sportisimo", "intersport", "hervis",
                "hanibal", "rock point", "hudy", "husky", "endorphin", "sanasport",
                "gymbeam", "aktin", "vilgain", "brainmarket", "ronnie", "bikero", "koloshop",
                // Fashion, Footwear & Apparel
                "zalando", "about you", "answear", "h m", "zara", "reserved", "mohito",
                "cropp", "house", "sinsay", "c a", "new yorker", "mango", "peek cloppenburg",
                "marks spencer", "bershka", "pull bear", "stradivarius", "massimo dutti",
                "cos", "levis", "gant", "tommy hilfiger", "calvin klein", "deichmann", "ccc",
                "eobuv", "humanic", "foot locker", "snipes", "sizeer", "bata", "rieker",
                "vivobarefoot", "barefoot", "vasky",
                // Toys & Kids
                "bambule", "sparkys", "dracik", "lego", "pompo", "hamleys", "feedo", "funbaby",
                // General Variety, Discount & Marketplaces
                "shoptet", "baktoma", "action", "pepco", "kik", "flying tiger", "tedi",
                "primark", "halfprice", "tk maxx", "allegro", "amazon", "temu", "aliexpress", "vinted", "ebay"
            )
        ),

        // Services, Utilities, Municipal Accounts & Culture
        MerchantRule(
            BankTransactionType.SERVICES_UTILITIES,
            listOf(
                // Brno Municipal Services & Utilities
                "teplarny", "bvk", "brnenske vodarny", "sako", "poplatek za odpad",
                // Brno Theatres, Culture, Sights & Concert Halls
                "tic", "spilberk", "vila tugendhat", "tugendhat", "hvezdarna", "planetarium",
                "narodni divadlo", "ndb", "janackovo divadlo", "mahenovo divadlo", "divadlo reduta",
                "mestske divadlo", "mdb", "husa na provazku", "provazek", "hadivadlo",
                "divadlo polarka", "divadlo radost",
                "kino scala", "scala", "kino art",
                "filharmonie", "besedni dum", "moravska galerie", "zoo",
                "fleda", "sono centrum", "metro music bar",
                // Telecommunications & Internet
                "vodafone", "o2", "t mobile", "nordic telecom", "poda", "nej cz", "cetin",
                // National Energy & Water Utilities
                "cez", "pre", "e on", "innogy", "mnd", "prazska plynarenska", "centropol",
                "epet", "veolia", "pvk", "vodovody",
                // Insurance (Health & Commercial)
                "zivotni pojisteni", "pojist*", "kooperativa", "generali", "ceska pojistovna",
                "allianz", "uniqa", "pillow", "direct", "slavia pojistovna",
                "pvzp", "vzp", "ozp", "vozp", "cpzp", "rbp",
                // Banking Fees
                "poplatek za vedeni", "vedeni uctu", "bankovni poplatek", "poplatek za kartu",
                // Personal Care, Hair, Laundry & Trades
                "salon", "salon galapa", "kadernictvi", "barber", "cistirna", "pradelna",
                "zamecnik", "hodinovy manzel",
                // Postal & Courier Services
                "posta", "ceska posta", "balikovna", "zasilkovna", "packeta", "dpd", "ppl", "gls", "dhl", "fedex", "ups"
            )
        )
    )
}
