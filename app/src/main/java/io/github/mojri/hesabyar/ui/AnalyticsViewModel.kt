package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class MonthlyData(
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val label: String,
    val income: Long,
    val expense: Long
)

data class CategoryBreakdown(
    val categoryId: Long,
    val categoryName: String,
    val color: Long,
    val total: Long,
    val percentage: Float
)

data class DebtSummary(
    val personName: String,
    val originalAmount: Long,
    val remainingAmount: Long,
    val type: String,
    val progress: Float
)

data class InstallmentProgress(
    val id: Long,
    val title: String,
    val amount: Long,
    val dueDate: Long,
    val isPaid: Boolean
)

data class AnalyticsData(
    val monthlySpending: List<MonthlyData> = emptyList(),
    val monthlyIncome: List<MonthlyData> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val debtors: List<DebtSummary> = emptyList(),
    val creditors: List<DebtSummary> = emptyList(),
    val activeLoans: List<Loan> = emptyList(),
    val installmentProgress: List<InstallmentProgress> = emptyList(),
    val totalInstallments: Int = 0,
    val paidInstallments: Int = 0,
    val totalDebt: Long = 0L,
    val totalCredit: Long = 0L
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    private val _selectedJalaliMonth = MutableStateFlow<Pair<Int, Int>?>(null)
    val selectedJalaliMonth: StateFlow<Pair<Int, Int>?> = _selectedJalaliMonth.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analyticsData: StateFlow<AnalyticsData> = combine(
        transactions, loans, installments, categories
    ) { trans, loanList, instList, catList ->
        computeAnalytics(trans, loanList, instList, catList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsData())

    fun setSelectedJalaliMonth(year: Int?, month: Int?) {
        _selectedJalaliMonth.value = if (year != null && month != null) year to month else null
    }

    private fun computeAnalytics(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>
    ): AnalyticsData {
        val jalaliHelper = io.github.mojri.hesabyar.ui.JalaliCalendarHelper

        // Group transactions by Jalali month
        val monthlyIncomeMap = mutableMapOf<String, Long>()
        val monthlyExpenseMap = mutableMapOf<String, Long>()
        val monthlyDataMap = mutableMapOf<String, MonthlyData>()

        transactions.forEach { t ->
            val jd = jalaliHelper.gregorianToJalali(t.date)
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

        // Category breakdown (expenses only)
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

        // Debt/Credit summary
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

        // Installment progress
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

        // Totals
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
