package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.ui.AnalyticsData

class GetAnalyticsUseCase {
  fun computeAnalytics(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): AnalyticsData {
    val rustResult =
      io.github.mojri.hesabyar.rust.RustBridge.computeAnalyticsSync(
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
        },
        categories.map {
          io.github.mojri.hesabyar.rust.Category(
            id = it.id,
            name = it.name,
            key = it.key,
            icon = it.icon,
            color = it.color,
            categoryType = it.type,
            isDefault = it.isDefault
          )
        }
      )

    if (rustResult == null) return AnalyticsData()
    return io.github.mojri.hesabyar.rust.RustMappers
      .mapAnalyticsData(rustResult, loans, installments)
  }
}
