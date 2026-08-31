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
    const val STATUTORY_TAXPAYER_CREDIT_LAW_REF = "ZDP § 35ba odst. 1 písm. a) (30 840 Kč/rok)"

    // --- ZDP § 35ba odst. 1 písm. b) - Sleva na manžela / manželku ---
    // Sleva 24 840 Kč ročně, podmíněna vyživovaným dítětem do 3 let věku a vlastním příjmem druhého manžela do 68 000 Kč ročně (konsolidační balíček 2024+)
    const val STATUTORY_SPOUSE_CREDIT_ANNUAL_2026 = 24840.0
    const val STATUTORY_SPOUSE_INCOME_LIMIT_ANNUAL_2026 = 68000.0
    const val STATUTORY_SPOUSE_CREDIT_LAW_REF = "ZDP § 35ba odst. 1 písm. b) (24 840 Kč/rok při příjmu ≤ 68 000 Kč a dítěti < 3 roky)"

    // --- ZDP § 35c - Daňové zvýhodnění na vyživované dítě ---
    // 1. dítě: 15 204 Kč/rok (1 267 Kč/měsíc)
    // 2. dítě: 22 320 Kč/rok (1 860 Kč/měsíc)
    // 3. a každé další dítě: 27 840 Kč/rok (2 320 Kč/měsíc)
    const val STATUTORY_CHILD_1_BONUS_ANNUAL_2026 = 15204.0
    const val STATUTORY_CHILD_2_BONUS_ANNUAL_2026 = 22320.0
    const val STATUTORY_CHILD_3_PLUS_BONUS_ANNUAL_2026 = 27840.0
    const val STATUTORY_CHILD_BONUS_LAW_REF = "ZDP § 35c (1. dítě 15 204 Kč, 2. dítě 22 320 Kč, 3.+ dítě 27 840 Kč/rok)"

    // --- ZDP § 15 odst. 5, 6 a ZDP § 15a - Odpočet na produkty spoření na stáří (DPS + DIP + ŽP) ---
    // Sloučený limit daňového odpočtu: 48 000 Kč ročně na poplatníka (konsolidační balíček 2024+)
    // DPS: odpočet platí pro částky nad měsíční příspěvek se státní podporou (nad 1 700 Kč/měsíc)
    const val STATUTORY_RETIREMENT_DEDUCTION_CEILING_ANNUAL_2026 = 48000.0
    const val STATUTORY_DPS_DEDUCTION_THRESHOLD_MONTHLY_2026 = 1700.0
    const val STATUTORY_RETIREMENT_DEDUCTION_LAW_REF = "ZDP § 15 a § 15a (sdružený strop 48 000 Kč/rok pro DPS a DIP)"

    // --- ZDP § 16 - Sazba daně z příjmů fyzických osob ---
    // Základní sazba: 15 %
    // Zvýšená progresivní sazba: 23 % pro příjem nad 36násobek průměrné mzdy (cca 1 582 812 Kč pro rok 2026)
    const val STATUTORY_INCOME_TAX_RATE_BASE_PCT = 15.0
    const val STATUTORY_INCOME_TAX_RATE_HIGH_PCT = 23.0
    const val STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026 = 1582812.0
    const val STATUTORY_INCOME_TAX_LAW_REF = "ZDP § 16 (15 % základní pásmo, 23 % nad 36× průměrné mzdy)"

    // --- REFORMA DPS "LEPŠÍ PENZIJKO" (novela zákona o doplňkovém penzijním spoření, schváleno vládou 8/2026) ---
    // 1. Státní příspěvek pro mladé (do 30. narozenin): 40 % z vkladu, max 680 Kč/měsíc při úložce 1 700 Kč/měsíc
    // 2. Standardní státní příspěvek (≥ 30 let): 20 % z vkladu, max 340 Kč/měsíc při úložce 1 700 Kč/měsíc (min vklad 500 Kč/měsíc)
    // 3. Zákonný poplatkový strop pro správu fondů: 0.5 % p.a. (ruší se výkonnostní poplatek)
    // 4. Možnost jednorázového výběru až jedné TŘETINY vlastních vkladů včetně jejich zhodnocení
    //    do 36. narozenin (po minimálně 10 letech spoření, bez sankce; výběr není vázán na účel)
    const val LEPSI_PENZIJKO_MIN_DEPOSIT_MONTHLY = 500.0
    const val LEPSI_PENZIJKO_STANDARD_SUBSIDY_RATE_PCT = 20.0
    const val LEPSI_PENZIJKO_STANDARD_MAX_SUBSIDY_MONTHLY = 340.0
    const val LEPSI_PENZIJKO_YOUTH_AGE_LIMIT = 30
    const val LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT = 40.0
    const val LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY = 680.0
    const val LEPSI_PENZIJKO_STATUTORY_FEE_CAP_PCT = 0.5
    const val LEPSI_PENZIJKO_EARLY_WITHDRAWAL_AGE = 36
    const val LEPSI_PENZIJKO_EARLY_WITHDRAWAL_SHARE_PCT = 100.0 / 3.0
    const val LEPSI_PENZIJKO_EFFECTIVE_YEAR = 2027
    const val LEPSI_PENZIJKO_LAW_REF = "Novela zákona o doplňkovém penzijním spoření (Lepší penzijko, účinnost od 1. 1. 2027)"

    // --- Minimální mzda v ČR ---
    const val STATUTORY_MIN_WAGE_MONTHLY_2026 = 22400.0
    const val STATUTORY_MIN_WAGE_LAW_REF = "Nařízení vlády o minimální mzdě 2026 (22 400 Kč/měsíc)"

    // --- ZDP § 6 odst. 9 písm. m) - Osvobozený příspěvek zaměstnavatele na penzijní/životní spoření ---
    // Zaměstnavatelský příspěvek do výše 50 000 Kč ročně je osvobozen od daně z příjmů a pojistného
    const val STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL = 50000.0
    const val STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_LAW_REF = "ZDP § 6 odst. 9 písm. m) (50 000 Kč/rok osvobozeno)"

    // --- ZDP § 35c odst. 4 - Minimální příjem pro daňový bonus na dítě ---
    // Pro uplatnění daňového bonusu musí poplatník dosáhnout alespoň 6násobku minimální mzdy za rok
    const val STATUTORY_CHILD_BONUS_MIN_WAGE_MULTIPLIER = 6.0
    const val STATUTORY_CHILD_BONUS_MIN_WAGE_LAW_REF = "ZDP § 35c odst. 4 (min. příjem 6× minimální mzda ročně)"

    // --- ČSÚ Live CPI Benchmark (default for fresh installs; overwritten by Live Sync) ---
    // Meziroční inflace dle ČSÚ pro rok 2025/2026 (aktualizováno při Live Sync)
    const val DEFAULT_CPI_BENCHMARK_PCT = 2.8

    // --- Rodičovský příspěvek (zákon č. 117/1995 Sb., o státní sociální podpoře) ---
    // Celková částka je určena na každé narozené dítě podle data narození:
    // - narozené před 1. 1. 2027: 350 000 Kč
    // - narozené od 1. 1. 2027: 400 000 Kč
    const val PARENTAL_ALLOWANCE_TOTAL_BEFORE_CUTOFF = 350000.0
    const val PARENTAL_ALLOWANCE_TOTAL_FROM_CUTOFF = 400000.0
    const val PARENTAL_ALLOWANCE_HIGHER_TOTAL_CUTOFF_YEAR = 2027
}
