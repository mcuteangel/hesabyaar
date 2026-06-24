package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.Installment

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
