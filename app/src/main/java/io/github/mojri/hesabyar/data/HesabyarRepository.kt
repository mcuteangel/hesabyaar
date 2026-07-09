package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class HesabyarRepository(
  private val transactionDao: TransactionDao,
  private val loanDao: LoanDao,
  private val installmentDao: InstallmentDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : HesabyarRepositoryInterface {
  override val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
  override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
  override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()
  override val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

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
    val loan = loanDao.getLoanById(loanId) ?: return false
    val loansCategory = getCategoryByKey("Loans") ?: return false
    val newRemaining = (loan.remainingAmount - amount).coerceAtLeast(0L)
    val isSettled = newRemaining <= 0L
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
        amount = amount,
        description = desc,
        personName = loan.personName,
        date = date
      )
    val payment = PaymentHistory(loanId = loanId, amount = amount, notes = notes, date = date)

    database.withTransaction {
      loanDao.updateLoan(updatedLoan)
      paymentHistoryDao.insertPayment(payment)
      transactionDao.insertTransaction(tx)
    }
    return true
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

  // Backup & Restore structure
  override suspend fun importBackup(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    paymentHistories: List<PaymentHistory>
  ) = database.withTransaction {
    transactionDao.deleteAllTransactions()
    loanDao.deleteAllLoans()
    installmentDao.deleteAllInstallments()
    paymentHistoryDao.deleteAllPaymentHistory()

    transactions.forEach { transactionDao.insertTransaction(it) }
    loans.forEach { loanDao.insertLoan(it) }
    installments.forEach { installmentDao.insertInstallment(it) }
    paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
  }

  override suspend fun getAllPaymentHistories(): List<PaymentHistory> = paymentHistoryDao.getAllPaymentHistories()

  override suspend fun replaceAllFromBackup(backup: BackupPayload) =
    database.withTransaction {
      transactionDao.deleteAllTransactions()
      loanDao.deleteAllLoans()
      installmentDao.deleteAllInstallments()
      paymentHistoryDao.deleteAllPaymentHistory()

      backup.categories.forEach { categoryDao.insertCategory(it) }
      backup.transactions.forEach { transactionDao.insertTransaction(it) }
      backup.loans.forEach { loanDao.insertLoan(it) }
      backup.installments.forEach { installmentDao.insertInstallment(it) }
      backup.paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
    }

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
            categoryDao.insertCategory(category)
          }
        keyToId[category.key] = savedId
        idToKey[category.id] = category.key
      }

      for (transaction in backup.transactions) {
        val mappedId =
          idToKey[transaction.categoryId]?.let { keyToId[it] }
            ?: categoryDao.getCategoryByKey("Other")?.id
            ?: transaction.categoryId
        transactionDao.insertTransaction(transaction.copy(categoryId = mappedId))
      }

      for (loan in backup.loans) {
        loanDao.insertLoan(loan)
      }

      for (installment in backup.installments) {
        installmentDao.insertInstallment(installment)
      }

      for (payment in backup.paymentHistories) {
        paymentHistoryDao.insertPayment(payment)
      }
    }
}
