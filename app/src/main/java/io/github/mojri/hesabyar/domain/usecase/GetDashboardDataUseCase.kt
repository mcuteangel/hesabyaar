package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.DashboardData
import kotlinx.coroutines.flow.Flow

class GetDashboardDataUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val transactions: Flow<List<Transaction>> = repository.allTransactions
  val loans: Flow<List<Loan>> = repository.allLoans
  val installments: Flow<List<Installment>> = repository.allInstallments
  val categories: Flow<List<Category>> = repository.allCategories

  fun computeDashboardData(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>
  ): DashboardData {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeDashboardDataSync(
        transactions.map {
          io.github.mojri.hesabyar.rust.RustMappers
            .mapTransaction(it)
        },
        loans.map {
          io.github.mojri.hesabyar.rust.RustMappers
            .mapLoan(it)
        },
        installments.map {
          io.github.mojri.hesabyar.rust.RustMappers
            .mapInstallment(it)
        }
      )

    if (rustResult == null) return DashboardData()
    return io.github.mojri.hesabyar.rust.RustMappers
      .mapDashboardData(rustResult, installments)
  }
}
