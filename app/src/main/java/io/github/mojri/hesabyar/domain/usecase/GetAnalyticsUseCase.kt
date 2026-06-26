package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AnalyticsData
import io.github.mojri.hesabyar.ui.CategoryBreakdown
import io.github.mojri.hesabyar.ui.DebtSummary
import io.github.mojri.hesabyar.ui.InstallmentProgress
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.MonthlyData

class GetAnalyticsUseCase {
    fun computeAnalytics(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): AnalyticsData {
        val monthlyIncomeMap = mutableMapOf<String, Long>()
        val monthlyExpenseMap = mutableMapOf<String, Long>()
        val monthlyDataMap = mutableMapOf<String, MonthlyData>()

        transactions.forEach { t ->
            val jd = JalaliCalendarHelper.gregorianToJalali(t.date)
            val key = "${jd.year}_${jd.month}"
            val label = "${getJalaliMonthName(jd.month)} ${jd.year}"

            val existing = monthlyDataMap.getOrPut(key) {
                MonthlyData(jd.year, jd.month, label, 0L, 0L)
            }

            if (t.type == "INCOME") {
                monthlyIncomeMap[key] = (monthlyIncomeMap[key] ?: 0L) + t.amount
                monthlyDataMap[key] = existing.copy(income = existing.income + t.amount)
            } else {
                monthlyExpenseMap[key] = (monthlyExpenseMap[key] ?: 0L) + t.amount
                monthlyDataMap[key] = existing.copy(expense = existing.expense + t.amount)
            }
        }

        val sortedMonthlyData = monthlyDataMap.values.sortedBy { it.jalaliYear * 100 + it.jalaliMonth }
        val last6Months = sortedMonthlyData.takeLast(6)

        val categoryTotals = mutableMapOf<Long, Long>()
        transactions.filter { it.type == "EXPENSE" }.forEach { t ->
            categoryTotals[t.categoryId] = (categoryTotals[t.categoryId] ?: 0L) + t.amount
        }
        val totalExpense = categoryTotals.values.sum()
        val categoryBreakdown = categoryTotals.map { (catId, total) ->
            val cat = categories.find { it.id == catId }
            CategoryBreakdown(
                categoryId = catId,
                categoryName = cat?.name ?: "سایر",
                color = cat?.color ?: 0xFF757575L,
                total = total,
                percentage = if (totalExpense > 0) total.toFloat() / totalExpense else 0f
            )
        }.sortedByDescending { it.total }

        val unsettledLoans = loans.filter { !it.isSettled }
        val debtors = unsettledLoans.filter { it.type == "DEBTOR" }.map { loan ->
            DebtSummary(
                personName = loan.personName,
                originalAmount = loan.originalAmount,
                remainingAmount = loan.remainingAmount,
                type = loan.type,
                progress = if (loan.originalAmount > 0) {
                    1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
                } else 0f
            )
        }
        val creditors = unsettledLoans.filter { it.type == "CREDITOR" }.map { loan ->
            DebtSummary(
                personName = loan.personName,
                originalAmount = loan.originalAmount,
                remainingAmount = loan.remainingAmount,
                type = loan.type,
                progress = if (loan.originalAmount > 0) {
                    1f - (loan.remainingAmount.toFloat() / loan.originalAmount)
                } else 0f
            )
        }

        val installmentProgress = installments.map { inst ->
            InstallmentProgress(
                id = inst.id,
                title = inst.title,
                amount = inst.amount,
                dueDate = inst.dueDate,
                isPaid = inst.isPaid
            )
        }
        val paidCount = installments.count { it.isPaid }
        val totalCount = installments.size

        val totalDebt = debtors.sumOf { it.remainingAmount }
        val totalCredit = creditors.sumOf { it.remainingAmount }

        return AnalyticsData(
            monthlySpending = last6Months,
            monthlyIncome = last6Months,
            categoryBreakdown = categoryBreakdown,
            debtors = debtors,
            creditors = creditors,
            activeLoans = unsettledLoans,
            installmentProgress = installmentProgress,
            totalInstallments = totalCount,
            paidInstallments = paidCount,
            totalDebt = totalDebt,
            totalCredit = totalCredit
        )
    }

    private fun getJalaliMonthName(month: Int): String {
        return when (month) {
            1 -> "فروردین"
            2 -> "اردیبهشت"
            3 -> "خرداد"
            4 -> "تیر"
            5 -> "مرداد"
            6 -> "شهریور"
            7 -> "مهر"
            8 -> "آبان"
            9 -> "آذر"
            10 -> "دی"
            11 -> "بهمن"
            12 -> "اسفند"
            else -> ""
        }
    }
}
