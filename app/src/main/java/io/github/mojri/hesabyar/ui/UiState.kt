package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import java.text.NumberFormat
import java.util.Locale

data class DashboardData(
    val currentBalance: Long = 0L,
    val monthlyExpenses: Long = 0L,
    val monthlyIncome: Long = 0L,
    val debtorsTotal: Long = 0L,
    val creditorsTotal: Long = 0L,
    val upcomingInstallments: List<Installment> = emptyList()
)

sealed interface ParserUIState {
    object Idle : ParserUIState
    object Loading : ParserUIState
    data class Success(val result: ParsedResult) : ParserUIState
    data class Error(val message: String) : ParserUIState
    data class Confirming(val result: ParsedResult) : ParserUIState
}

sealed interface AdvisorUIState {
    object Idle : AdvisorUIState
    object Loading : AdvisorUIState
    data class Success(val advice: String) : AdvisorUIState
    data class Error(val message: String) : AdvisorUIState
}

sealed interface ForecastUIState {
    object Idle : ForecastUIState
    object Loading : ForecastUIState
    data class Success(val forecast: String) : ForecastUIState
    data class Error(val message: String) : ForecastUIState
}

sealed interface ModelFetchState {
    object Idle : ModelFetchState
    object Loading : ModelFetchState
    data class Success(val models: List<String>) : ModelFetchState
    data class Error(val message: String) : ModelFetchState
}

fun formatToman(value: Long): String {
    val tomanValue = value / 10
    val formatter = NumberFormat.getNumberInstance(Locale("fa", "IR"))
    return "${formatter.format(tomanValue)} تومان"
}

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

sealed interface BackupOperationState {
    object Idle : BackupOperationState
    object Importing : BackupOperationState
    object Exporting : BackupOperationState
    data class ImportSuccess(val message: String) : BackupOperationState
    data class ExportSuccess(val message: String) : BackupOperationState
    data class Error(val message: String) : BackupOperationState
    data class ValidationFailed(val errors: List<String>) : BackupOperationState
}

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val summary: String) : ExportState
    data class Error(val message: String) : ExportState
}
