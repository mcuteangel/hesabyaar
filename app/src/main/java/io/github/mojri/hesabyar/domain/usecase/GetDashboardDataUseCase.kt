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
          io.github.mojri.hesabyar.rust.Transaction(
            id = it.id,
            txType =
              io.github.mojri.hesabyar.rust.TransactionType
                .valueOf(it.type),
            categoryId = it.categoryId,
            amount = it.amount,
            description = it.description,
            personName = it.personName,
            date = it.date,
            dueDate = it.dueDate,
            installmentId = it.installmentId
          )
        },
        loans.map {
          io.github.mojri.hesabyar.rust.Loan(
            id = it.id,
            personName = it.personName,
            loanType = it.type,
            originalAmount = it.originalAmount,
            remainingAmount = it.remainingAmount,
            description = it.description,
            date = it.date,
            isSettled = it.isSettled
          )
        },
        installments.map {
          io.github.mojri.hesabyar.rust.Installment(
            id = it.id,
            title = it.title,
            amount = it.amount,
            dueDate = it.dueDate,
            isPaid = it.isPaid,
            reminderEnabled = it.reminderEnabled,
            notes = it.notes
          )
        }
      )

    if (rustResult == null) return DashboardData()
    return io.github.mojri.hesabyar.rust.RustMappers
      .mapDashboardData(rustResult, installments)
  }
}
