package com.example.ui.components.sankey

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.theme.BadRedDark
import com.example.ui.theme.BrandBlueDarkTheme
import com.example.ui.theme.BrandGoldDarkTheme
import com.example.ui.theme.BrandTealDark
import com.example.ui.theme.GoodGreenDark

data class SankeyNode(
    val id: String,
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val amount: Double,
    val color: Color,
    val category: String,
    val description: String,
    val statutoryNote: String = ""
)

data class LayoutNode(
    val node: SankeyNode,
    val yTop: Float,
    val yBottom: Float,
    val bounds: RoundRect
)

fun buildInflowNodes(
    state: FullCalculationState,
    viewMode: Int,
    cTeal: Color = BrandTealDark,
    cGreen: Color = GoodGreenDark,
    cBlue: Color = BrandBlueDarkTheme
): List<SankeyNode> {
    val s = state.settings
    val currentInc = state.currentIncome

    return if (viewMode == 0) {
        listOf(
            SankeyNode(
                id = "in_total",
                title = "Household Net Inflow",
                shortLabel = "Inflows",
                subtitle = "All Salaries & Support",
                amount = currentInc.totalMonthly,
                color = cTeal,
                category = "Combined Take-Home Cash",
                description = "Total disposable take-home pay received into household accounts after Czech personal income taxes, social, and health deductions.",
                statutoryNote = "§ 6 ZDP · Zálohová daň 15 % / 23 % + 7,1 % SP + 4,5 % ZP"
            )
        )
    } else {
        buildList {
            if (currentInc.vaclavNet > 0.0) {
                add(
                    SankeyNode(
                        id = "in_vaclav",
                        title = "${s.primaryName}'s Net Salary",
                        shortLabel = s.primaryName,
                        subtitle = "Take-home pay",
                        amount = currentInc.vaclavNet,
                        color = cTeal,
                        category = "Earned Employment Income",
                        description = "Net monthly salary after statutory 15%/23% personal income tax, 7.1% social, and 4.5% health insurance.",
                        statutoryNote = "§ 6 ZDP · Zálohová daň 15 % / 23 % + 7,1 % SP + 4,5 % ZP"
                    )
                )
            }
            if (!s.isSingleHousehold && currentInc.eleonoraSalary > 0.0) {
                add(
                    SankeyNode(
                        id = "in_eleonora",
                        title = "${s.spouseName}'s Net Salary",
                        shortLabel = s.spouseName,
                        subtitle = "Take-home pay",
                        amount = currentInc.eleonoraSalary,
                        color = Color(0xFF0D9488),
                        category = "Earned Employment Income",
                        description = "Net monthly salary upon return to employment, accounting for progressive tax credits.",
                        statutoryNote = "§ 6 ZDP · Sleva na poplatníka 30 840 Kč/rok"
                    )
                )
            }
            if (currentInc.vouchers > 0.0) {
                add(
                    SankeyNode(
                        id = "in_vouchers",
                        title = "Meal Vouchers",
                        shortLabel = "Vouchers",
                        subtitle = "Tax-exempt allowance",
                        amount = currentInc.vouchers,
                        color = cGreen,
                        category = "Non-Taxable Allowance",
                        description = "Monetary meal allowance (§ 6(9)(b) ZDP), 100% tax-free and exempt from social and health deductions.",
                        statutoryNote = "§ 6(9)(b) ZDP · Osvobozený stravenkový paušál do zákonného limitu"
                    )
                )
            }
            val totalGifts = currentInc.gift
            if (totalGifts > 0.0) {
                add(
                    SankeyNode(
                        id = "in_gifts",
                        title = "Family Support",
                        shortLabel = "Support",
                        subtitle = "Lineage gift exemption",
                        amount = totalGifts,
                        color = Color(0xFF10B981),
                        category = "Tax-Exempt Transfer",
                        description = "Financial assistance from direct-line family members (§ 10(3)(c) ZDP), fully exempt from income tax.",
                        statutoryNote = "§ 10(3)(c) ZDP · Bezúplatné příjmy od příbuzných v linii přímé osvobozeny od daně"
                    )
                )
            }
            if (currentInc.benefit > 0.0) {
                add(
                    SankeyNode(
                        id = "in_parental",
                        title = "Parental Allowance",
                        shortLabel = "Parental",
                        subtitle = "State family benefit",
                        amount = currentInc.benefit,
                        color = cBlue,
                        category = "State Social Support",
                        description = "Monthly parental allowance (Rodičovský příspěvek, Act No. 117/1995 Coll.), fully tax-exempt.",
                        statutoryNote = "Zákon č. 117/1995 Sb. · Celkový nárok 350 000 Kč nepodléhá dani ani odvodům"
                    )
                )
            }
            if (currentInc.lecturing > 0.0) {
                add(
                    SankeyNode(
                        id = "in_lecturing",
                        title = "Lecturing & Advisory",
                        shortLabel = "Lecturing",
                        subtitle = "Academic side income",
                        amount = currentInc.lecturing,
                        color = Color(0xFF38BDF8),
                        category = "Professional Side Income",
                        description = "Academic lecturing and consulting inflows.",
                        statutoryNote = "§ 7 / § 10 ZDP · Autorské honoráře a vedlejší odborná činnost"
                    )
                )
            }
            val otherInflows = currentInc.vaclavOther + currentInc.eleonoraOther + currentInc.lumpSumMonthly
            if (otherInflows > 0.0) {
                add(
                    SankeyNode(
                        id = "in_other",
                        title = "Other Inflows",
                        shortLabel = "Other",
                        subtitle = "Lump sums & misc",
                        amount = otherInflows,
                        color = Color(0xFF818CF8),
                        category = "Supplementary Inflows",
                        description = "Other planned monthly cash inflows and annualized lump-sum deposits.",
                        statutoryNote = "Ostatní příjmy domácnosti rozpočtené do pravidelného měsíčního toku"
                    )
                )
            }
        }
    }
}

fun buildOutflowNodes(
    state: FullCalculationState,
    viewMode: Int,
    cTeal: Color = BrandTealDark,
    cGreen: Color = GoodGreenDark,
    cGold: Color = BrandGoldDarkTheme,
    cRed: Color = BadRedDark
): List<SankeyNode> {
    val s = state.settings
    val cPurple = Color(0xFF6366F1)
    val cOrange = Color(0xFFF97316)
    val cAmber = Color(0xFFF59E0B)

    val currentInc = state.currentIncome
    val totalInflows = currentInc.totalMonthly.coerceAtLeast(1.0)
    val totalExpenses = state.totalLivingCostMonthly
    val totalInvest = state.investMonthlyTotal
    val unallocatedSurplus = (totalInflows - totalExpenses - totalInvest).coerceAtLeast(0.0)

    val childCosts = if (s.childExpensesEnabled && !s.currentChildCostsInBaseline) {
        val c1 = if (s.child1Enabled) FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s) else 0.0
        val c2 = if (s.child2Enabled) FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s) else 0.0
        c1 + c2
    } else 0.0

    return if (viewMode == 0) {
        buildList {
            add(
                SankeyNode(
                    id = "out_living",
                    title = "Living Burn Rate",
                    shortLabel = "Living Burn",
                    subtitle = "Rent, groceries & lifestyle",
                    amount = totalExpenses,
                    color = cRed,
                    category = "Baseline Living Expenses",
                    description = "Baseline operational expenditures required to maintain your household standard of living.",
                    statutoryNote = "Základní spotřební koš domácnosti, bydlení a provozní výdaje"
                )
            )
            if (totalInvest > 0.0) {
                add(
                    SankeyNode(
                        id = "out_invest",
                        title = "Wealth Compounding",
                        shortLabel = "Wealth DCA",
                        subtitle = "Brokerage, DIP & DPS",
                        amount = totalInvest,
                        color = cTeal,
                        category = "Compounding Capital Formation",
                        description = "Automated monthly investments directly compounding toward your early retirement independence target.",
                        statutoryNote = "§ 4(1)(w) ZDP (3letý časový test) + § 15a ZDP (DIP) + Zákon č. 427/2011 Sb. (DPS)"
                    )
                )
            }
            if (unallocatedSurplus > 0.0) {
                add(
                    SankeyNode(
                        id = "out_buffer",
                        title = "Unallocated Cash Buffer",
                        shortLabel = "Cash Buffer",
                        subtitle = "Reserve & discretionary",
                        amount = unallocatedSurplus,
                        color = cGold,
                        category = "Liquid Capital Buffer",
                        description = "Uncommitted monthly surplus retained in high-yield cash buffers to fund emergency reserves and future lump sums.",
                        statutoryNote = "Pohotovostní likvidní rezerva chránící před sekvenčním rizikem (SORR)"
                    )
                )
            }
        }
    } else {
        buildList {
            if (s.rentMonthly > 0.0) {
                add(
                    SankeyNode(
                        id = "out_rent",
                        title = "Housing & Rent",
                        shortLabel = "Rent",
                        subtitle = "Primary residence",
                        amount = s.rentMonthly,
                        color = cRed,
                        category = "Fixed Essential Living",
                        description = "Monthly apartment lease and housing facility maintenance costs.",
                        statutoryNote = "Pevné provozní výdaje na nájemné a energie"
                    )
                )
            }
            if (s.groceriesMonthly > 0.0) {
                add(
                    SankeyNode(
                        id = "out_groceries",
                        title = "Groceries & Food",
                        shortLabel = "Groceries",
                        subtitle = "Supermarket & nutrition",
                        amount = s.groceriesMonthly,
                        color = cOrange,
                        category = "Essential Living Cost",
                        description = "Household food, cooking, and daily household supplies.",
                        statutoryNote = "Základní spotřební koš domácnosti s 12 % sníženou sazbou DPH"
                    )
                )
            }
            if (childCosts > 0.0) {
                add(
                    SankeyNode(
                        id = "out_child",
                        title = "Child Lifecycle",
                        shortLabel = "Children",
                        subtitle = "Care & development",
                        amount = childCosts,
                        color = cAmber,
                        category = "Family Living Cost",
                        description = "Age-graded education, medical, clothing, and activity costs for dependent children.",
                        statutoryNote = "Věkově odstupňované náklady na děti (0-3, 3-6, 6-15, 15-26 let)"
                    )
                )
            }
            val otherLiving = (totalExpenses - s.rentMonthly - s.groceriesMonthly - childCosts).coerceAtLeast(0.0)
            if (otherLiving > 0.0) {
                add(
                    SankeyNode(
                        id = "out_other_living",
                        title = "Lifestyle & Discretionary",
                        shortLabel = "Lifestyle",
                        subtitle = "Transport, leisure, utilities",
                        amount = otherLiving,
                        color = Color(0xFFF43F5E),
                        category = "Variable Living Expense",
                        description = "Transportation, telecommunications, travel, and discretionary living expenditures.",
                        statutoryNote = "Variabilní výdaje s možností okamžitého snížení při krizovém scénáři"
                    )
                )
            }

            val portuTotal = s.portuDcaMonthly + (if (!s.isSingleHousehold) s.ePortuDcaMonthly else 0.0)
            if (portuTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "out_portu",
                        title = "Brokerage ETF (Portu)",
                        shortLabel = "Portu ETF",
                        subtitle = "Global equity index",
                        amount = portuTotal,
                        color = cTeal,
                        category = "Liquid Wealth Compounding",
                        description = "Automated monthly dollar-cost averaging into globally diversified equity index ETFs (qualifying for the Czech 3-year time test exemption § 4(1)(w) ZDP).",
                        statutoryNote = "§ 4(1)(w) ZDP · Osvobozeno od 15% daně ze zisku po 3 letech držby"
                    )
                )
            }
            val dipTotal = s.dipContributionMonthly + (if (!s.isSingleHousehold) s.eDipContributionMonthly else 0.0)
            if (dipTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "out_dip",
                        title = "DIP Retirement",
                        shortLabel = "DIP",
                        subtitle = "Tax-sheltered account",
                        amount = dipTotal,
                        color = cGreen,
                        category = "Statutory Tax-Sheltered Account",
                        description = "Dlouhodobý investiční produkt (§ 15a ZDP) enabling index ETF compounding with pre-tax income.",
                        statutoryNote = "§ 15a ZDP · Daňový odpočet až 48 000 Kč/rok s pravidlem 120 měsíců a věkem 60 let"
                    )
                )
            }
            val dpsTotal = s.dpsOwnContributionMonthly + (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly else 0.0)
            if (dpsTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "out_dps",
                        title = "DPS Pension",
                        shortLabel = "DPS",
                        subtitle = "With state subsidies",
                        amount = dpsTotal,
                        color = cPurple,
                        category = "Supplementary State-Subsidized Pension",
                        description = "Doplňkové penzijní spoření with up to 340 Kč/mo direct state matching (Act No. 427/2011 Coll.).",
                        statutoryNote = "Zákon č. 427/2011 Sb. · Státní příspěvek 20 % z vkladu nad 500 Kč (max 340 Kč/měsíc)"
                    )
                )
            }
            if (unallocatedSurplus > 0.0) {
                add(
                    SankeyNode(
                        id = "out_buffer",
                        title = "Unallocated Buffer",
                        shortLabel = "Buffer",
                        subtitle = "Liquid reserve building",
                        amount = unallocatedSurplus,
                        color = cGold,
                        category = "Liquid Reserve Capital",
                        description = "Disposable monthly surplus uncommitted to fixed living burn or automated DCA, accumulating in cash reserves.",
                        statutoryNote = "Likvidní pohotovostní rezerva na spořicím účtu chránící před sekvenčním rizikem"
                    )
                )
            }
        }
    }
}

fun buildCentralHubNode(
    state: FullCalculationState,
    cTeal: Color = BrandTealDark
): SankeyNode {
    val currentInc = state.currentIncome
    return SankeyNode(
        id = "hub_central",
        title = "Household Clearing Pool",
        shortLabel = "Pool",
        subtitle = "Combined disposable cash",
        amount = currentInc.totalMonthly,
        color = cTeal,
        category = "Central Cash Flow Clearing Hub",
        description = "Central clearing pool representing 100% of disposable household cash generated each month.",
        statutoryNote = "Disponibilní peněžní toky domácnosti před alokací do výdajů a investic"
    )
}
