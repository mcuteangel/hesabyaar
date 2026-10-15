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
   * Runs [block] inside a single database transaction, so a set of reads
   * observes a consistent snapshot even when writers run concurrently.
   */
  suspend fun <R> withTransaction(block: suspend () -> R): R
}
