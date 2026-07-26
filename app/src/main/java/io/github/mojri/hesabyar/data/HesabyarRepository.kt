package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HesabyarRepository(
  private val transactionDao: TransactionDao,
  private val loanDao: LoanDao,
  private val installmentDao: InstallmentDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val categoryDao: CategoryDao,
  private val bankLoanDao: BankLoanDao,
  private val database: AppDatabase
) : HesabyarRepositoryInterface {
  override val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
  override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
  override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()
  override val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
  override val allBankLoans: Flow<List<BankLoan>> = bankLoanDao.getAllBankLoans()

  override fun getTransactionsInRange(
    start: Long,
    end: Long
  ): Flow<List<Transaction>> = transactionDao.getTransactionsInRange(start, end)

  override fun getCategoriesByType(type: String): Flow<List<Category>> = categoryDao.getCategoriesByType(type)

  override suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)

  override suspend fun getCategoryByKey(key: String): Category? = categoryDao.getCategoryByKey(key)

  override suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)

  override suspend fun updateCategory(category: Category) {
    categoryDao.updateCategory(category)
  }

  override suspend fun deleteCategory(category: Category) {
    categoryDao.deleteCategory(category)
  }

  override suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)

  override suspend fun deleteTransaction(transaction: Transaction) {
    transactionDao.deleteTransaction(transaction)
  }

  override suspend fun updateTransaction(transaction: Transaction) {
    transactionDao.updateTransaction(transaction)
  }

  // Loans and Payments logic combined
  override suspend fun insertLoan(loan: Loan): Long = loanDao.insertLoan(loan)

  override suspend fun updateLoan(loan: Loan) {
    loanDao.updateLoan(loan)
  }

  override suspend fun deleteLoan(loan: Loan) {
    loanDao.deleteLoan(loan)
  }

  override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> =
    paymentHistoryDao.getPaymentHistoryForLoan(loanId)

  override suspend fun addPaymentToLoan(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long?
  ): Boolean {
    if (amount <= 0L) return false

    return database.withTransaction {
      val loan = loanDao.getLoanById(loanId) ?: return@withTransaction false
      val loansCategory = getCategoryByKey("Loans") ?: return@withTransaction false
      if (loan.remainingAmount <= 0L) return@withTransaction false
      val newRemaining = (loan.remainingAmount - amount).coerceAtLeast(0L)
      val isSettled = newRemaining == 0L
      val effectiveAmount = loan.remainingAmount - newRemaining
      val date = customDate ?: System.currentTimeMillis()

      val updatedLoan = loan.copy(remainingAmount = newRemaining, isSettled = isSettled)
      val desc =
        if (loan.type == LoanType.CREDITOR) {
          "بازپرداخت بدهی به ${loan.personName} - $notes"
        } else {
          "دریافت بازپرداخت از ${loan.personName} - $notes"
        }
      val tx =
        Transaction(
          type = if (loan.type == LoanType.CREDITOR) TransactionType.EXPENSE else TransactionType.INCOME,
          categoryId = loansCategory.id,
          amount = effectiveAmount,
          description = desc,
          personName = loan.personName,
          date = date
        )
      val payment = PaymentHistory(loanId = loanId, amount = effectiveAmount, notes = notes, date = date)

      loanDao.updateLoan(updatedLoan)
      paymentHistoryDao.insertPayment(payment)
      transactionDao.insertTransaction(tx)
      true
    }
  }

  // Installments
  override suspend fun insertInstallment(installment: Installment): Long = installmentDao.insertInstallment(installment)

  override suspend fun updateInstallment(installment: Installment) {
    database.withTransaction {
      val existing = installmentDao.getInstallmentById(installment.id)
      installmentDao.updateInstallment(installment)
      val justPaid = installment.isPaid && (existing == null || !existing.isPaid)
      if (justPaid) {
        val installmentsCategory = getCategoryByKey("Installments")
        if (installmentsCategory != null) {
          transactionDao.insertTransaction(
            Transaction(
              type = TransactionType.EXPENSE,
              categoryId = installmentsCategory.id,
              amount = installment.amount,
              description = "پرداخت قسط: ${installment.title} - ${installment.notes}"
            )
          )
        }
      }
    }
  }

  override suspend fun deleteInstallment(installment: Installment) {
    installmentDao.deleteInstallment(installment)
  }

  override suspend fun getBankLoanById(id: Long): BankLoan? = bankLoanDao.getBankLoanById(id)

  override suspend fun insertBankLoan(bankLoan: BankLoan): Long = bankLoanDao.insertBankLoan(bankLoan)

  override suspend fun updateBankLoan(bankLoan: BankLoan) {
    bankLoanDao.updateBankLoan(bankLoan)
  }

  override suspend fun deleteBankLoan(bankLoan: BankLoan) {
    database.withTransaction {
      // Remove generated installments linked to this loan before deleting it,
      // so they don't linger as orphaned reminders/expenses.
      installmentDao.deleteInstallmentsByBankLoanId(bankLoan.id)
      bankLoanDao.deleteBankLoan(bankLoan)
    }
  }

  override suspend fun addBankLoanWithInstallments(
    bankLoan: BankLoan,
    installments: List<Installment>
  ) = database.withTransaction {
    val loanId = bankLoanDao.insertBankLoan(bankLoan)
    installments.forEach { installmentDao.insertInstallment(it.copy(bankLoanId = loanId)) }
    loanId
  }

  // Backup & Restore structure
  override suspend fun getInstallmentsByBankLoanId(bankLoanId: Long): List<Installment> =
    bankLoanDao.getInstallmentsByBankLoanId(bankLoanId).first()

  override suspend fun importBackup(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    paymentHistories: List<PaymentHistory>,
    bankLoans: List<BankLoan>
  ) = database.withTransaction {
    transactionDao.deleteAllTransactions()
    loanDao.deleteAllLoans()
    installmentDao.deleteAllInstallments()
    paymentHistoryDao.deleteAllPaymentHistory()
    bankLoanDao.deleteAllBankLoans()

    transactions.forEach { transactionDao.insertTransaction(it) }
    loans.forEach { loanDao.insertLoan(it) }
    installments.forEach { installmentDao.insertInstallment(it) }
    paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
    bankLoans.forEach { bankLoanDao.insertBankLoan(it) }
  }

  override suspend fun getAllPaymentHistories(): List<PaymentHistory> = paymentHistoryDao.getAllPaymentHistories()

  override suspend fun replaceAllFromBackup(backup: BackupPayload) =
    database.withTransaction {
      transactionDao.deleteAllTransactions()
      loanDao.deleteAllLoans()
      installmentDao.deleteAllInstallments()
      paymentHistoryDao.deleteAllPaymentHistory()
      bankLoanDao.deleteAllBankLoans()

      backup.categories.forEach { categoryDao.insertCategory(it) }
      backup.transactions.forEach { transactionDao.insertTransaction(it) }
      backup.loans.forEach { loanDao.insertLoan(it) }
      backup.installments.forEach { installmentDao.insertInstallment(it) }
      backup.paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
      backup.bankLoans.forEach { bankLoanDao.insertBankLoan(it) }
    }

  /**
   * Merges backup data into the database while preserving relationships through foreign-key remapping.
   *
   * @param backup The backup payload containing categories and related records to merge.
   */
  override suspend fun mergeFromBackup(backup: BackupPayload) =
    database.withTransaction {
      val keyToId = mutableMapOf<String, Long>()
      val idToKey = mutableMapOf<Long, String>()
      for (category in backup.categories) {
        val existing = categoryDao.getCategoryByKey(category.key)
        val savedId =
          if (existing != null) {
            categoryDao.updateCategory(category.copy(id = existing.id))
            existing.id
          } else {
            categoryDao.insertCategory(category.copy(id = 0))
          }
        keyToId[category.key] = savedId
        idToKey[category.id] = category.key
      }

      // Insert loans first and capture old→new ID map so that
      // paymentHistories.loanId can be remapped correctly.
      val loanIdMap = backup.loans.associate { it.id to loanDao.insertLoan(it.copy(id = 0)) }

      // Map old bank-loan IDs → freshly assigned IDs so installments
      // that reference them stay linked after the merge.
      val bankLoanIdMap = backup.bankLoans.associate { it.id to bankLoanDao.insertBankLoan(it.copy(id = 0)) }

      // Insert installments (with remapped bankLoanId) and capture
      // old→new ID map so that transactions.installmentId can be remapped.
      val installmentIdMap =
        backup.installments.associate { installment ->
          val newId =
            installmentDao.insertInstallment(
              installment.copy(id = 0, bankLoanId = installment.bankLoanId?.let(bankLoanIdMap::get))
            )
          installment.id to newId
        }

      for (transaction in backup.transactions) {
        val mappedCategoryId =
          idToKey[transaction.categoryId]?.let { keyToId[it] }
            ?: categoryDao.getCategoryByKey("Other")?.id
            ?: transaction.categoryId
        val mappedInstallmentId = transaction.installmentId?.let { installmentIdMap[it] }
        transactionDao.insertTransaction(
          transaction.copy(id = 0, categoryId = mappedCategoryId, installmentId = mappedInstallmentId)
        )
      }

      for (payment in backup.paymentHistories) {
        val mappedLoanId = loanIdMap[payment.loanId] ?: continue
        paymentHistoryDao.insertPayment(payment.copy(id = 0, loanId = mappedLoanId))
      }
    }
}
