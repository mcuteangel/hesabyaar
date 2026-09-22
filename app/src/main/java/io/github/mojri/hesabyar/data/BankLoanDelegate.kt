package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal class BankLoanDelegate(
  private val bankLoanDao: BankLoanDao,
  private val installmentDao: InstallmentDao,
  private val transactionLinkDao: TransactionLinkDao,
  private val transactionDao: TransactionDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : BankLoanOps {
  override val allBankLoans: Flow<List<BankLoan>> = bankLoanDao.getAllBankLoans()

  override suspend fun getBankLoanById(id: Long): BankLoan? = bankLoanDao.getBankLoanById(id)

  override suspend fun insertBankLoan(bankLoan: BankLoan): Long = bankLoanDao.insertBankLoan(bankLoan)

  override suspend fun updateBankLoan(bankLoan: BankLoan) {
    bankLoanDao.updateBankLoan(bankLoan)
  }

  override suspend fun deleteBankLoan(bankLoan: BankLoan) {
    database.withTransaction {
      // Decide from the persisted row when available: the caller may hold a
      // stale snapshot whose tracked flag no longer matches the database.
      val existing = bankLoanDao.getBankLoanById(bankLoan.id) ?: bankLoan
      // A paid installment's linked expense must die with its row, exactly
      // like InstallmentDelegate.deleteInstallment — deleting the row through
      // the bank-loan cascade alone would strand the money behind dead
      // installments (reports keep counting it).
      installmentDao
        .getInstallmentsByBankLoanIdSync(bankLoan.id)
        .filter { it.isPaid }
        .forEach { transactionLinkDao.deleteTransactionsForInstallment(it.id) }
      // addBankLoanWithInstallmentsAndInitial posted a tracked disbursement
      // income with no link row to find it by; delete it with the same
      // field-match strategy the creator's fields allow, or it keeps counting
      // in reports behind a dead bank loan. Untracked loans never posted one.
      if (existing.tracked) {
        val loansCategoryId = categoryDao.getCategoryByKey("Loans")?.id
        if (loansCategoryId != null) {
          transactionLinkDao.deleteBankLoanDisbursementTransaction(
            categoryId = loansCategoryId,
            amount = existing.receivedAmount,
            date = existing.startDate
          )
        }
      }
      installmentDao.deleteInstallmentsByBankLoanId(bankLoan.id)
      bankLoanDao.deleteBankLoan(bankLoan)
    }
  }

  override suspend fun getInstallmentsByBankLoanId(bankLoanId: Long): List<Installment> =
    bankLoanDao.getInstallmentsByBankLoanId(bankLoanId).first()

  override suspend fun addBankLoanWithInstallments(
    bankLoan: BankLoan,
    installments: List<Installment>
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(bankLoan.tracked, bankLoan.accountId)
    val normalizedBankLoan =
      bankLoan.copy(
        accountId = TrackedLedgerHelper.normalizeAccountId(bankLoan.tracked, bankLoan.accountId)
      )
    return database.withTransaction {
      val loanId = bankLoanDao.insertBankLoan(normalizedBankLoan)
      installments.forEach {
        installmentDao.insertInstallment(
          it.copy(bankLoanId = loanId, tracked = false, accountId = null)
        )
      }
      loanId
    }
  }

  override suspend fun addBankLoanWithInstallmentsAndInitial(
    bankLoan: BankLoan,
    installments: List<Installment>,
    recordInitial: Boolean
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(bankLoan.tracked, bankLoan.accountId)
    val normalizedBankLoan =
      bankLoan.copy(
        accountId = TrackedLedgerHelper.normalizeAccountId(bankLoan.tracked, bankLoan.accountId)
      )
    return database.withTransaction {
      val loanId = bankLoanDao.insertBankLoan(normalizedBankLoan)
      // Bank-loan installments strictly inherit ledger-only defaults (enforced);
      // their own tracked flag governs future paid toggles (see InstallmentDelegate).
      // The parent tracked flag governs only the one-time disbursement leg below.
      installments.forEach {
        installmentDao.insertInstallment(
          it.copy(bankLoanId = loanId, tracked = false, accountId = null)
        )
      }
      if (recordInitial && normalizedBankLoan.tracked) {
        val loansCategory =
          categoryDao.getCategoryByKey(LoansCategoryExclusion.CATEGORY_KEY)
            ?: throw IllegalStateException(
              "Loans category is missing; cannot record the bank loan disbursement transaction"
            )
        transactionDao.insertTransaction(
          Transaction(
            type = TransactionType.INCOME,
            categoryId = loansCategory.id,
            amount = normalizedBankLoan.receivedAmount,
            description = "دریافت وام ${normalizedBankLoan.loanName} از ${normalizedBankLoan.bankName}",
            date = normalizedBankLoan.startDate,
            accountId = TrackedLedgerHelper.resolveAccountId(normalizedBankLoan.accountId)
          )
        )
      }
      loanId
    }
  }
}
