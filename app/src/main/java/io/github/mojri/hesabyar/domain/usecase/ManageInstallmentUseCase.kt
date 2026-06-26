package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import kotlinx.coroutines.flow.Flow

class ManageInstallmentUseCase(
    private val repository: HesabyarRepositoryInterface
) {
    val allInstallments: Flow<List<Installment>> = repository.allInstallments

    suspend fun addInstallment(
        title: String,
        amount: Long,
        dueDate: Long,
        reminderEnabled: Boolean,
        notes: String
    ): Long = repository.insertInstallment(
        Installment(
            title = title,
            amount = amount,
            dueDate = dueDate,
            reminderEnabled = reminderEnabled,
            notes = notes
        )
    )

    suspend fun toggleInstallmentPaid(installment: Installment) =
        repository.updateInstallment(installment.copy(isPaid = !installment.isPaid))

    suspend fun deleteInstallment(installment: Installment) =
        repository.deleteInstallment(installment)
}
