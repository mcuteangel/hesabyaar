package io.github.mojri.hesabyar.domain.usecase

import android.content.Context
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import kotlinx.coroutines.flow.firstOrNull

class ExportExcelUseCase(
    private val repository: HesabyarRepositoryInterface,
    private val excelExporter: ExcelExporter
) {
    suspend fun export() = excelExporter.export(
        transactions = repository.allTransactions.firstOrNull() ?: emptyList(),
        loans = repository.allLoans.firstOrNull() ?: emptyList(),
        installments = repository.allInstallments.firstOrNull() ?: emptyList(),
        categories = repository.allCategories.firstOrNull() ?: emptyList(),
        getPaymentsForLoan = { loanId ->
            repository.getPaymentHistoryForLoan(loanId).firstOrNull() ?: emptyList()
        }
    )
}
