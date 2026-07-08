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
        },
        categories.map {
          io.github.mojri.hesabyar.rust.RustMappers
            .mapCategory(it)
        }
      )

    if (rustResult == null) return AnalyticsData()
    return io.github.mojri.hesabyar.rust.RustMappers
      .mapAnalyticsData(rustResult, loans, installments)
  }
}
