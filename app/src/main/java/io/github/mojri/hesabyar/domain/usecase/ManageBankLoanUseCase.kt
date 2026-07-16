package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ManageBankLoanUseCase(
  private val repository: HesabyarRepositoryInterface
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
    require(bankName.isNotBlank()) { "bankName must not be blank" }
    require(receivedAmount > 0) { "receivedAmount must be positive" }
    require(monthlyInstallmentAmount > 0) { "monthlyInstallmentAmount must be positive" }
    require(numberOfInstallments >= 0) { "numberOfInstallments must not be negative" }
    require(startDate > 0) { "startDate must be positive" }

    // A count of 0 is an interest-free single-payment loan; fall back to one installment.
    val count = if (numberOfInstallments > 0) numberOfInstallments else 1
    // Checked arithmetic so an overflowed repayable/interest amount can never be persisted.
    val totalRepayable = Math.multiplyExact(monthlyInstallmentAmount, count.toLong())
    val totalInterest = Math.subtractExact(totalRepayable, receivedAmount)
    val bankLoan =
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

    val jStart = JalaliCalendarHelper.gregorianToJalali(startDate)
    val monthsPerYear = 12
    val installments =
      (1..count).map { i ->
        var jYear = jStart.year
        var jMonth = jStart.month + (i - 1)
        while (jMonth > monthsPerYear) {
          jMonth -= monthsPerYear
          jYear += 1
        }
        val dueDay =
          minOf(
            jStart.day,
            JalaliCalendarHelper.getDaysInMonth(jYear, jMonth)
          )
        val dueDate =
          requireNotNull(
            JalaliCalendarHelper.jalaliToGregorian(jYear, jMonth, dueDay)
          ).timeInMillis
        Installment(
          title = "قسط $i از $count - $loanName",
          amount = monthlyInstallmentAmount,
          dueDate = dueDate,
          reminderEnabled = true,
          notes = "",
          bankLoanId = null
        )
      }

    // Insert loan + its installments atomically so a failure can't leave
    // orphaned installments referencing a missing loan.
    return repository.addBankLoanWithInstallments(bankLoan, installments)
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
