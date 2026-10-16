package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan

interface HesabyarRepositoryInterface :
  PersonRepositoryInterface,
  AccountOps,
  TransactionOps,
  CategoryOps,
  LoanOps,
  InstallmentOps,
  BankLoanOps,
  BackupOps {
  /**
   * Runs [block] inside a single database transaction. The production
   * implementation ([HesabyarRepository]) delegates to Room's `withTransaction`,
   * so a set of reads observes a consistent snapshot even when writers run
   * concurrently. This interface default just invokes [block] directly. Test
   * fakes rely on it because they do not need real atomicity. Every production
   * implementation that touches more than one table must override it.
   */
  suspend fun <R> withTransaction(block: suspend () -> R): R = block()
}
