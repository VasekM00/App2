package com.example.ui.components.sankey

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.parseDeletedCategories
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BadRedDark
import com.example.ui.theme.BrandBlueDarkTheme
import com.example.ui.theme.BrandGoldDarkTheme
import com.example.ui.theme.BrandTealDark
import com.example.ui.theme.GoodGreenDark
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct

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
    cBlue: Color = BrandBlueDarkTheme,
    isDark: Boolean = true
): List<SankeyNode> {
    val s = state.settings
    val currentInc = state.currentIncome
    val cSpouse = if (isDark) Color(0xFF14B8A6) else Color(0xFF0F766E)
    val cGift = cGreen
    val cLecture = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val cOther = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

    val safeTotalMonthly = currentInc.totalMonthly.finiteOrZero().coerceAtLeast(0.0)
    val safeVaclavNet = currentInc.vaclavNet.finiteOrZero().coerceAtLeast(0.0)
    val safeSpouseNet = currentInc.eleonoraSalary.finiteOrZero().coerceAtLeast(0.0)
    val safeVouchers = currentInc.vouchers.finiteOrZero().coerceAtLeast(0.0)
    val safeGifts = currentInc.gift.finiteOrZero().coerceAtLeast(0.0)
    val safeBenefit = currentInc.benefit.finiteOrZero().coerceAtLeast(0.0)
    val safeLecturing = currentInc.lecturing.finiteOrZero().coerceAtLeast(0.0)
    val safeOtherInflows = (
        currentInc.vaclavOther.finiteOrZero() +
            currentInc.eleonoraOther.finiteOrZero() +
            currentInc.lumpSumMonthly.finiteOrZero()
        ).coerceAtLeast(0.0)
    val empMatchTotal = FinancialEngine.baseEmployerMatchMonthly(s)
    val cViolet = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)

    return if (viewMode == 0) {
        listOf(
            SankeyNode(
                id = "in_total",
                title = "Household Net Inflow",
                shortLabel = "Inflows",
                subtitle = "All Salaries & Support",
                amount = safeTotalMonthly,
                color = cTeal,
                category = "Combined Take-Home Cash",
                description = "Total disposable take-home pay received into household accounts after Czech personal income taxes, social, and health deductions.",
                statutoryNote = "Progressive income tax (15% / 23%) + 7.1% social + 4.5% health insurance"
            )
        )
    } else {
        buildList {
            if (safeVaclavNet > 0.0) {
                add(
                    SankeyNode(
                        id = "in_vaclav",
                        title = "${s.primaryName}'s Net Salary",
                        shortLabel = s.primaryName,
                        subtitle = "Take-home pay",
                        amount = safeVaclavNet,
                        color = cTeal,
                        category = "Earned Employment Income",
                        description = "Net monthly salary after 15%/23% personal income tax, 7.1% social, and 4.5% health insurance.",
                        statutoryNote = "Progressive income tax (15% / 23%) + 7.1% social + 4.5% health insurance"
                    )
                )
            }
            if (!s.isSingleHousehold && safeSpouseNet > 0.0) {
                add(
                    SankeyNode(
                        id = "in_eleonora",
                        title = "${s.spouseName}'s Net Salary",
                        shortLabel = s.spouseName,
                        subtitle = "Take-home pay",
                        amount = safeSpouseNet,
                        color = cSpouse,
                        category = "Earned Employment Income",
                        description = "Net monthly salary upon return to employment, accounting for progressive tax credits.",
                        statutoryNote = "Includes annual taxpayer credit (${fmtCZK(s.taxpayerCreditAnnual)}/year)"
                    )
                )
            }
            if (safeVouchers > 0.0) {
                add(
                    SankeyNode(
                        id = "in_vouchers",
                        title = "Meal Vouchers",
                        shortLabel = "Vouchers",
                        subtitle = "Tax-exempt allowance",
                        amount = safeVouchers,
                        color = cGreen,
                        category = "Non-Taxable Allowance",
                        description = "Monetary meal allowance, 100% tax-free and exempt from social and health insurance deductions.",
                        statutoryNote = "Monetary meal voucher flat rate up to the legal statutory daily limit"
                    )
                )
            }
            if (safeGifts > 0.0) {
                add(
                    SankeyNode(
                        id = "in_gifts",
                        title = "Family Support",
                        shortLabel = "Support",
                        subtitle = "Lineage gift exemption",
                        amount = safeGifts,
                        color = cGift,
                        category = "Tax-Exempt Transfer",
                        description = "Financial assistance from direct-line family members, fully exempt from personal income tax.",
                        statutoryNote = "Gratuitous financial transfers between direct-line relatives are 100% tax-exempt"
                    )
                )
            }
            if (safeBenefit > 0.0) {
                add(
                    SankeyNode(
                        id = "in_parental",
                        title = "Parental Allowance",
                        shortLabel = "Parental",
                        subtitle = "State family benefit",
                        amount = safeBenefit,
                        color = cBlue,
                        category = "State Social Support",
                        description = "Monthly parental allowance, fully tax-exempt.",
                        statutoryNote = "Total entitlement (${fmtCZK(s.parentalAllowanceTotalBeforeCutoff)}) is 100% exempt from income taxes and social levies"
                    )
                )
            }
            if (safeLecturing > 0.0) {
                add(
                    SankeyNode(
                        id = "in_lecturing",
                        title = "Lecturing & Advisory",
                        shortLabel = "Lecturing",
                        subtitle = "Academic side income",
                        amount = safeLecturing,
                        color = cLecture,
                        category = "Professional Side Income",
                        description = "Academic lecturing and consulting inflows.",
                        statutoryNote = "Author royalties and independent consulting income"
                    )
                )
            }
            if (safeOtherInflows > 0.0) {
                add(
                    SankeyNode(
                        id = "in_other",
                        title = "Other Inflows",
                        shortLabel = "Other",
                        subtitle = "Lump sums & miscellaneous",
                        amount = safeOtherInflows,
                        color = cOther,
                        category = "Supplementary Inflows",
                        description = "Other planned monthly cash inflows and annualized lump-sum deposits.",
                        statutoryNote = "Other household revenues prorated into steady monthly flow"
                    )
                )
            }
            if (empMatchTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "in_employer_match",
                        title = "Employer Pension Match",
                        shortLabel = "Employer Match",
                        subtitle = "Direct employer benefit",
                        amount = empMatchTotal,
                        color = cViolet,
                        category = "Tax-Exempt Employer Benefit",
                        description = "Direct employer contribution paid into DPS/DIP retirement accounts outside take-home salary.",
                        statutoryNote = "Exempt from employee income tax and social/health levies up to ${fmtCZK(s.employerRetirementExemptionAnnual)}/year"
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
    cRed: Color = BadRedDark,
    isDark: Boolean = true
): List<SankeyNode> {
    val s = state.settings
    val cPurple = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val cOrange = if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val cAmber = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val cRose = if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    val cViolet = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)

    val currentInc = state.currentIncome
    val totalInflows = currentInc.totalMonthly.finiteOrZero().coerceAtLeast(0.0)
    val totalExpenses = state.totalLivingCostMonthly.finiteOrZero().coerceAtLeast(0.0)
    val ownInvestMonthly = FinancialEngine.baseOwnInvestMonthly(s).finiteOrZero().coerceAtLeast(0.0)
    val empMatchTotal = FinancialEngine.baseEmployerMatchMonthly(s).finiteOrZero().coerceAtLeast(0.0)
    val unallocatedSurplus = (totalInflows - totalExpenses - ownInvestMonthly).finiteOrZero().coerceAtLeast(0.0)

    val deletedSet = parseDeletedCategories(s.deletedCategoriesJson)
    val effectiveRent = if ("rent" in deletedSet) 0.0 else s.rentMonthly.finiteOrZero().coerceAtLeast(0.0)
    val effectiveGroceries = if ("groceries" in deletedSet) 0.0 else s.groceriesMonthly.finiteOrZero().coerceAtLeast(0.0)

    val rawChildCosts = if (s.childExpensesEnabled && !s.currentChildCostsInBaseline) {
        val c1 = if (s.child1Enabled) FinancialEngine.childMonthlyExpense(s.child1BirthYear, s.baseYear, s).finiteOrZero().coerceAtLeast(0.0) else 0.0
        val c2 = if (s.child2Enabled) FinancialEngine.childMonthlyExpense(s.child2BirthYear, s.baseYear, s).finiteOrZero().coerceAtLeast(0.0) else 0.0
        c1 + c2
    } else 0.0
    val childCosts = rawChildCosts.coerceIn(0.0, totalExpenses)

    return if (viewMode == 0) {
        buildList {
            add(
                SankeyNode(
                    id = "out_living",
                    title = "Living Costs",
                    shortLabel = "Living Costs",
                    subtitle = "Rent, groceries & lifestyle",
                    amount = totalExpenses,
                    color = cRed,
                    category = "Baseline Living Expenses",
                    description = "Baseline operational expenditures required to maintain your household standard of living.",
                    statutoryNote = "Core household consumption basket, housing, and operating expenses"
                )
            )
            if (ownInvestMonthly > 0.0) {
                add(
                    SankeyNode(
                        id = "out_invest",
                        title = "Auto-Investing",
                        shortLabel = "Investing",
                        subtitle = if (empMatchTotal > 0.0) "Own brokerage, DIP & DPS (plus employer match)" else "Brokerage, DIP & DPS",
                        amount = ownInvestMonthly,
                        color = cTeal,
                        category = "Compounding Capital Formation",
                        description = "Automated monthly investments funded from household take-home pay toward your early retirement target.",
                        statutoryNote = "3-year capital gains exemption + DIP tax deduction + State-subsidized DPS"
                    )
                )
            }
            if (unallocatedSurplus > 0.0) {
                add(
                    SankeyNode(
                        id = "out_buffer",
                        title = "Unallocated Cash",
                        shortLabel = "Cash Reserve",
                        subtitle = "Reserve & discretionary",
                        amount = unallocatedSurplus,
                        color = cGold,
                        category = "Liquid Capital Buffer",
                        description = "Uncommitted monthly surplus retained in high-yield cash buffers to fund emergency reserves and future lump sums.",
                        statutoryNote = "Emergency liquid reserve protecting against sequence-of-returns risk"
                    )
                )
            }
        }
    } else {
        buildList {
            if (effectiveRent > 0.0) {
                add(
                    SankeyNode(
                        id = "out_rent",
                        title = "Housing & Rent",
                        shortLabel = "Rent",
                        subtitle = "Primary residence",
                        amount = effectiveRent,
                        color = cRed,
                        category = "Fixed Essential Living",
                        description = "Monthly apartment lease and housing facility maintenance costs.",
                        statutoryNote = "Fixed recurring operating expenses for housing lease and utilities"
                    )
                )
            }
            if (effectiveGroceries > 0.0) {
                add(
                    SankeyNode(
                        id = "out_groceries",
                        title = "Groceries & Food",
                        shortLabel = "Groceries",
                        subtitle = "Supermarket & nutrition",
                        amount = effectiveGroceries,
                        color = cOrange,
                        category = "Essential Living Cost",
                        description = "Household food, cooking, and daily household supplies.",
                        statutoryNote = "Essential household food basket subject to the 12% reduced VAT rate"
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
                        statutoryNote = "Age-tiered child development costs across stages (0-3, 3-6, 6-15, 15-26 years)"
                    )
                )
            }
            val otherLiving = (totalExpenses - effectiveRent - effectiveGroceries - childCosts).coerceAtLeast(0.0)
            if (otherLiving > 0.0) {
                add(
                    SankeyNode(
                        id = "out_other_living",
                        title = "Lifestyle & Discretionary",
                        shortLabel = "Lifestyle",
                        subtitle = "Transport, leisure, utilities",
                        amount = otherLiving,
                        color = cRose,
                        category = "Variable Living Expense",
                        description = "Transportation, telecommunications, travel, and discretionary living expenditures.",
                        statutoryNote = "Discretionary variable expenses adjustable during market stress regimes"
                    )
                )
            }

            val portuTotal = (s.portuDcaMonthly.finiteOrZero().coerceAtLeast(0.0) +
                (if (!s.isSingleHousehold) s.ePortuDcaMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0))
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
                        description = "Automated monthly dollar-cost averaging into globally diversified equity index ETFs with 0% tax after 3 years.",
                        statutoryNote = "100% exempt from personal capital gains tax after 3 continuous years of holding"
                    )
                )
            }
            val dipTotal = (s.dipContributionMonthly.finiteOrZero().coerceAtLeast(0.0) +
                (if (!s.isSingleHousehold) s.eDipContributionMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0))
            if (dipTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "out_dip",
                        title = "DIP Retirement",
                        shortLabel = "DIP",
                        subtitle = "Tax-sheltered account",
                        amount = dipTotal,
                        color = cGreen,
                        category = "Tax-Sheltered Pension (DIP)",
                        description = "DIP retirement account enabling index ETF compounding with pre-tax income.",
                        statutoryNote = "Up to ${fmtCZK(s.taxDeductionCeilingAnnual)} annual deduction with 120-month holding rule and age 60 requirement"
                    )
                )
            }
            val dpsTotal = (s.dpsOwnContributionMonthly.finiteOrZero().coerceAtLeast(0.0) +
                (if (!s.isSingleHousehold) s.eDpsOwnContributionMonthly.finiteOrZero().coerceAtLeast(0.0) else 0.0))
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
                        description = "DPS pension account with up to ${fmtCZK(s.dpsStandardSubsidyMaxMonthly)} monthly direct state matching.",
                        statutoryNote = "State contribution of ${fmtPct(s.dpsSubsidyRateStandardPct, 0)} on monthly deposits from ${fmtCZK(s.dpsMinDepositForSubsidy)} to ${fmtCZK(s.dpsDeductionThresholdMonthly)} (up to ${fmtCZK(s.dpsStandardSubsidyMaxMonthly)}/month)"
                    )
                )
            }
            if (empMatchTotal > 0.0) {
                add(
                    SankeyNode(
                        id = "out_employer_match",
                        title = "Employer Pension Match",
                        shortLabel = "Employer Match",
                        subtitle = "Tax-exempt employer benefit",
                        amount = empMatchTotal,
                        color = cViolet,
                        category = "Employer Retirement Contribution",
                        description = "Tax-advantaged employer contributions to DPS/DIP retirement accounts within the annual exemption cap.",
                        statutoryNote = "Exempt from employee income tax and social/health levies up to ${fmtCZK(s.employerRetirementExemptionAnnual)}/year"
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
                        statutoryNote = "Liquid emergency reserve in yield accounts protecting against sequence risk"
                    )
                )
            }
        }
    }
}

fun buildCentralHubNode(
    state: FullCalculationState,
    cTeal: Color = BrandTealDark,
    viewMode: Int = 1
): SankeyNode {
    val currentInc = state.currentIncome
    val empMatch = if (viewMode == 0) 0.0 else FinancialEngine.baseEmployerMatchMonthly(state.settings).finiteOrZero().coerceAtLeast(0.0)
    return SankeyNode(
        id = "hub_central",
        title = "Household Clearing Pool",
        shortLabel = "Pool",
        subtitle = if (empMatch > 0.0) "Combined disposable cash & match" else "Combined disposable cash",
        amount = currentInc.totalMonthly.finiteOrZero().coerceAtLeast(0.0) + empMatch,
        color = cTeal,
        category = "Central Cash Flow Clearing Hub",
        description = if (empMatch > 0.0) {
            "Central clearing pool representing 100% of disposable household cash and employer retirement match flowing each month."
        } else {
            "Central clearing pool representing 100% of disposable household take-home cash flowing each month."
        },
        statutoryNote = "Disposable monthly household cash flow before expense and investment allocation"
    )
}
