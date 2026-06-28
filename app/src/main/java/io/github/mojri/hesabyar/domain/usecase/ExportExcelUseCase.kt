package io.github.mojri.hesabyar.domain.usecase

import android.content.Context
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class ExportExcelUseCase(
    private val repository: HesabyarRepositoryInterface,
    private val excelExporter: ExcelExporter
) {
    suspend fun export() = withContext(Dispatchers.IO) {
        excelExporter.export(
            transactions = repository.allTransactions.firstOrNull() ?: emptyList(),
            loans = repository.allLoans.firstOrNull() ?: emptyList(),
            installments = repository.allInstallments.firstOrNull() ?: emptyList(),
            categories = repository.allCategories.firstOrNull() ?: emptyList(),
            getPaymentsForLoan = { loanId ->
                repository.getPaymentHistoryForLoan(loanId).firstOrNull() ?: emptyList()
            }
        )
    }
}
