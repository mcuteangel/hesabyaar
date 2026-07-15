package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ManageBankLoanUseCase(
  private val repository: HesabyarRepositoryInterface,
  private val manageInstallmentUseCase: ManageInstallmentUseCase
) {
  val allBankLoans: Flow<List<BankLoan>> = repository.allBankLoans

  /**
   * Quick entry: only received amount, monthly installment, and count are entered;
   * total repayable and interest are computed.
   */
  suspend fun addBankLoan(
    bankName: String,
    loanName: String,
    receivedAmount: Long,
    monthlyInstallmentAmount: Long,
    numberOfInstallments: Int,
    startDate: Long,
    description: String
  ): Long {
    val count = if (numberOfInstallments > 0) numberOfInstallments else 1
    val totalRepayable = monthlyInstallmentAmount * count
    val totalInterest = totalRepayable - receivedAmount
    val id =
      repository.insertBankLoan(
        BankLoan(
          bankName = bankName,
          loanName = loanName,
          receivedAmount = receivedAmount,
          monthlyInstallmentAmount = monthlyInstallmentAmount,
          numberOfInstallments = count,
          totalRepayableAmount = totalRepayable,
          totalInterest = totalInterest,
          startDate = startDate,
          description = description,
          isSettled = false
        )
      )
    val dayMs = 24L * 60 * 60 * 1000
    for (i in 1..count) {
      manageInstallmentUseCase.addInstallmentForBankLoan(
        bankLoanId = id,
        title = "قسط $i از $count - $loanName",
        amount = monthlyInstallmentAmount,
        dueDate = startDate + (i - 1) * 30L * dayMs,
        reminderEnabled = true,
        notes = ""
      )
    }
    return id
  }

  suspend fun updateBankLoan(bankLoan: BankLoan) = repository.updateBankLoan(bankLoan)

  suspend fun deleteBankLoan(bankLoan: BankLoan) = repository.deleteBankLoan(bankLoan)

  suspend fun toggleSettled(id: Long) {
    val loan = repository.getBankLoanById(id) ?: return
    repository.updateBankLoan(loan.copy(isSettled = !loan.isSettled))
  }

  fun installmentsByBankLoan(bankLoanId: Long): Flow<List<Installment>> =
    flow { emit(repository.getInstallmentsByBankLoanId(bankLoanId)) }
}
