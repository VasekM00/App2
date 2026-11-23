package com.example.domain

/**
 * Czech Republic Statutory & Regulatory Tax / Pension Constants
 * 
 * Sources:
 * - Zákon č. 586/1992 Sb., o daních z příjmů (ZDP) v platném znění pro rok 2026
 * - Vládou schválená novela zákona o doplňkovém penzijním spoření "Lepší penzijko" (srpen 2026)
 * - Zákon č. 155/1995 Sb., o důchodovém pojištění
 * - Nařízení vlády o minimální mzdě pro rok 2026
 */
object RegulatoryConstants {

    // --- ZDP § 35ba odst. 1 písm. a) - Základní sleva na poplatníka ---
    // Roční sleva na poplatníka: 30 840 Kč (od roku 2022 dosud stabilní)
    const val STATUTORY_TAXPAYER_CREDIT_ANNUAL_2026 = 30840.0

    // --- ZDP § 35ba odst. 1 písm. b) - Sleva na manžela / manželku ---
    // Sleva 24 840 Kč ročně, podmíněna vyživovaným dítětem do 3 let věku a vlastním příjmem druhého manžela do 68 000 Kč ročně (konsolidační balíček 2024+)
    const val STATUTORY_SPOUSE_CREDIT_ANNUAL_2026 = 24840.0
    const val STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026 = 68000.0

    // --- ZDP § 35c - Daňové zvýhodnění na vyživované dítě ---
    // 1. dítě: 15 204 Kč/rok (1 267 Kč/měsíc)
    // 2. dítě: 22 320 Kč/rok (1 860 Kč/měsíc)
    // 3. a každé další dítě: 27 840 Kč/rok (2 320 Kč/měsíc)
    const val STATUTORY_CHILD_1_BONUS_ANNUAL_2026 = 15204.0
    const val STATUTORY_CHILD_2_BONUS_ANNUAL_2026 = 22320.0
    const val STATUTORY_CHILD_3_PLUS_BONUS_ANNUAL_2026 = 27840.0

    // --- ZDP § 15 odst. 5, 6 a ZDP § 15a - Odpočet na produkty spoření na stáří (DPS + DIP + ŽP) ---
    // Sloučený limit daňového odpočtu: 48 000 Kč ročně na poplatníka (konsolidační balíček 2024+)
    // DPS: odpočet platí pro částky nad měsíční příspěvek se státní podporou (nad 1 700 Kč/měsíc)
    const val STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026 = 48000.0
    const val STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026 = 1700.0

    // Zvýšená progresivní sazba: 23 % pro příjem nad 36násobek průměrné mzdy (1 762 812 Kč pro rok 2026)
    const val STATUTORY_INCOME_TAX_RATE_BASE_PCT = 15.0
    const val STATUTORY_INCOME_TAX_RATE_HIGH_PCT = 23.0
    const val STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026 = 1762812.0

    // --- STÁTNÍ PŘÍSPĚVEK DPS (zákon č. 427/2011 Sb., o doplňkovém penzijním spoření) ---
    // Státní příspěvek: 20 % z vkladu, max 340 Kč/měsíc při úložce 1 700 Kč/měsíc (min vklad 500 Kč/měsíc)
    const val LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY = 500.0
    const val LEPSI_PENZIJKO_STANDARD_SUBSIDY_RATE_PCT = 20.0
    const val LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY = 340.0
    const val LEPSI_PENZIJKO_YOUTH_AGE_LIMIT = 30
    const val LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT = 20.0
    const val LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY = 340.0
    // Statutory management fee cap for dynamic/balanced DPS funds (Act No. 427/2011 Coll. § 60(3)): 1.0% p.a.
    const val LEPSI_PENZIJKO_STATUTORY_FEE_CAP_PCT = 1.0
    const val LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE = 36
    const val LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT = 100.0 / 3.0
    const val LEPSI_PENZIJKO_EFFECTIVE_YEAR = 2027

    // --- Minimální mzda v ČR ---
    const val STATUTORY_MIN_WAGE_MONTHLY_2026 = 22400.0

    // --- Zákon č. 187/2006 Sb., § 7a - Rozhodná částka pro účast na nemocenském/sociálním pojištění u DPP ---
    // DPP podléhá odvodům SP/ZP od 12 000 Kč/měsíc (osvobozeno pouze při výdělku < 12 000 Kč/měsíc)
    const val STATUTORY_DPP_INSURANCE_THRESHOLD_MONTHLY_2026 = 12000.0

    // --- ZDP § 6 odst. 9 písm. m) - Osvobozený příspěvek zaměstnavatele na penzijní/životní spoření ---
    // Zaměstnavatelský příspěvek do výše 50 000 Kč ročně je osvobozen od daně z příjmů a pojistného
    const val STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL = 50000.0

    // --- ZDP § 35c odst. 4 - Minimální příjem pro daňový bonus na dítě ---
    // Pro uplatnění daňového bonusu musí poplatník dosáhnout alespoň 6násobku minimální mzdy za rok
    const val STATUTORY_CHILD_BONUS_MIN_WAGE_MULTIPLIER = 6.0

    // --- Rodičovský příspěvek (zákon č. 117/1995 Sb., o státní sociální podpoře) ---
    // Celková částka je určena na každé narozené dítě: 350 000 Kč
    const val PARENTAL_ALLOWANCE_TOTAL_BEFORE_CUTOFF = 350000.0
    const val PARENTAL_ALLOWANCE_TOTAL_FROM_CUTOFF = 350000.0
    const val PARENTAL_ALLOWANCE_HIGHER_TOTAL_CUTOFF_YEAR = 2027

    // --- ZDP § 15 odst. 3 & 4 - Odpočet úroků z hypotečního úvěru na bytové potřeby ---
    // Pro smlouvy uzavřené od 1. 1. 2021: max. 150 000 Kč ročně na domácnost (daňová úspora až 22 500 Kč při 15 %)
    const val STATUTORY_MORTGAGE_INTEREST_DEDUCTION_ANNUAL_CAP = 150000.0

    // --- ZDP § 4 odst. 1 písm. u) a w) - Osvobození příjmů z prodeje cenných papírů a podílů ---
    // 3letý časový test (přesáhne dobu 3 let) pro cenné papíry a ETF dle § 4 odst. 1 písm. u) ZDP
    // Roční limit úhrnu všech příjmů z prodeje cenných papírů do 100 000 Kč dle § 4 odst. 1 písm. w) ZDP
    const val STATUTORY_SECURITIES_TIME_TEST_YEARS = 3
    const val STATUTORY_SECURITIES_TIME_TEST_MONTHS = 36
    const val STATUTORY_SECURITIES_ANNUAL_EXEMPTION_LIMIT_CZK = 100000.0

    // --- Rodičovský příspěvek pro vícerčata (zákon č. 117/1995 Sb. § 30 odst. 1 ve znění od 2026) ---
    const val PARENTAL_ALLOWANCE_TWINS_MULTIPLIER_2026 = 2.0

    // --- ZDP § 38v - Oznamovací povinnost o osvobozených příjmech nad 5 000 000 Kč ---
    const val STATUTORY_EXEMPT_INCOME_REPORTING_THRESHOLD_CZK = 5_000_000.0

    // --- DPS zákonné stropy výkonnostní odměny (zákon č. 427/2011 Sb. § 60 odst. 4) ---
    const val STATUTORY_DPS_DYNAMIC_PERFORMANCE_FEE_PCT = 15.0
    const val STATUTORY_DPS_BALANCED_PERFORMANCE_FEE_PCT = 10.0

    // --- Minimální doba trvání penzijních produktů (120 měsíců = 10 let, ZDP § 15b) ---
    const val STATUTORY_PENSION_MIN_LOCK_IN_YEARS = 10

    // --- ZDP § 15 odst. 1 - Odpočet bezúplatných plnění (darů) ---
    const val STATUTORY_CHARITY_MIN_ANNUAL_CZK = 1_000.0
    const val STATUTORY_CHARITY_MAX_BASE_PCT_TEMP_2026 = 30.0
    const val STATUTORY_CHARITY_MAX_BASE_PCT_STANDARD = 15.0
}

/**
 * Shared domain numeric sanitization helpers to prevent NaN / Infinity propagation
 * and inconsistent clamping across financial and tax calculation engines.
 */
internal fun Double.finiteOr(fallback: Double = 0.0): Double =
    if (this.isFinite()) this else fallback

internal fun Double.finiteNonNegative(fallback: Double = 0.0): Double {
    val v = if (this.isFinite()) this else fallback
    return if (v > 0.0) v else 0.0
}

internal fun Double.finiteAtLeast(minValue: Double, fallback: Double = minValue): Double {
    val safeFallback = if (fallback.isFinite()) fallback else minValue
    val v = if (this.isFinite()) this else safeFallback
    return maxOf(v, minValue)
}

internal fun Double.finiteIn(minValue: Double, maxValue: Double, fallback: Double = minValue): Double {
    val lo = minOf(minValue, maxValue)
    val hi = maxOf(minValue, maxValue)
    val safeFallback = if (fallback.isFinite()) fallback else lo
    val v = if (this.isFinite()) this else safeFallback
    return v.coerceIn(lo, hi)
}

