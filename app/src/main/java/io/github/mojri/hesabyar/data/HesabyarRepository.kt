package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.core.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HesabyarRepository(
  private val transactionDao: TransactionDao,
  private val loanDao: LoanDao,
  private val installmentDao: InstallmentDao,
  private val paymentHistoryDao: PaymentHistoryDao,
  private val categoryDao: CategoryDao,
  private val bankLoanDao: BankLoanDao,
  private val accountDao: AccountDao,
  private val database: AppDatabase
) : HesabyarRepositoryInterface {
  override val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
  override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
  override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()
  override val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
  override val allBankLoans: Flow<List<BankLoan>> = bankLoanDao.getAllBankLoans()
  override val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

  override suspend fun getActiveAccounts(): List<AccountEntity> = accountDao.getActiveAccounts().first()

  override suspend fun getAllAccounts(): List<AccountEntity> = accountDao.getAllAccountsBlocking()

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

  // Account CRUD
  override suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getById(id)

  override suspend fun insertAccount(account: AccountEntity): Long = accountDao.insert(account)

  override suspend fun updateAccount(account: AccountEntity) = accountDao.update(account)

  override suspend fun deleteAccount(account: AccountEntity) =
    database.withTransaction {
      val allAccounts = accountDao.getAllAccountsBlocking()
      if (allAccounts.size == 1 && allAccounts[0].id == account.id) {
        throw IllegalStateException(
          "Account ${account.id} is the last remaining account and cannot be deleted"
        )
      }
      val count = accountDao.getTransactionCountForAccount(account.id)
      if (count > 0) {
        throw IllegalStateException("Account ${account.id} has $count transactions and cannot be deleted")
      }
      accountDao.delete(account)
    }

  override suspend fun getTransactionCountForAccount(accountId: Long): Int =
    accountDao.getTransactionCountForAccount(accountId)

  override suspend fun getMaxDisplayOrder(): Int = accountDao.getMaxDisplayOrder()

  override suspend fun replaceAllFromBackup(backup: BackupPayload) =
    database.withTransaction {
      transactionDao.deleteAllTransactions()
      loanDao.deleteAllLoans()
      installmentDao.deleteAllInstallments()
      paymentHistoryDao.deleteAllPaymentHistory()
      bankLoanDao.deleteAllBankLoans()
      accountDao.deleteAllAccounts()

      backup.categories.forEach { categoryDao.insertCategory(it) }
      backup.transactions.forEach { transactionDao.insertTransaction(it) }
      backup.loans.forEach { loanDao.insertLoan(it) }
      backup.installments.forEach { installmentDao.insertInstallment(it) }
      backup.paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
      backup.bankLoans.forEach { bankLoanDao.insertBankLoan(it) }
      backup.accounts.forEach { accountDao.insert(it) }
    }

  /**
   * Merges backup data into the database while preserving relationships through foreign-key remapping.
   *
   * @param backup The backup payload containing categories and related records to merge.
   */
  override suspend fun mergeFromBackup(backup: BackupPayload) =
    database.withTransaction {
      val categoryIdMap = mergeCategories(backup.categories)
      val loanIdMap = mergeLoans(backup.loans)
      val bankLoanIdMap = mergeBankLoans(backup.bankLoans)
      val installmentIdMap = mergeInstallments(backup.installments, bankLoanIdMap)
      val accountIdMap = mergeAccounts(backup.accounts)
      mergeTransactions(backup.transactions, categoryIdMap, installmentIdMap, accountIdMap)
      mergePaymentHistories(backup.paymentHistories, loanIdMap)
    }

  private suspend fun mergeCategories(categories: List<Category>): Map<Long, Long> {
    val keyToId = mutableMapOf<String, Long>()
    val idToKey = mutableMapOf<Long, String>()
    for (category in categories) {
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
    return idToKey.mapValues { keyToId[it.value] ?: it.key }
  }

  private suspend fun mergeLoans(loans: List<Loan>): Map<Long, Long> =
    loans.associate { it.id to loanDao.insertLoan(it.copy(id = 0)) }

  private suspend fun mergeBankLoans(bankLoans: List<BankLoan>): Map<Long, Long> =
    bankLoans.associate { it.id to bankLoanDao.insertBankLoan(it.copy(id = 0)) }

  private suspend fun mergeInstallments(
    installments: List<Installment>,
    bankLoanIdMap: Map<Long, Long>
  ): Map<Long, Long> =
    installments.associate { installment ->
      val newId =
        installmentDao.insertInstallment(
          installment.copy(id = 0, bankLoanId = installment.bankLoanId?.let(bankLoanIdMap::get))
        )
      installment.id to newId
    }

  private suspend fun mergeAccounts(accounts: List<AccountEntity>): Map<Long, Long> {
    // Name-to-id lookup seeded from the DB snapshot and updated after every
    // insert, so two backup entries sharing a name map to the SAME local row
    // (mirrors mergeCategories' keyed dedup). A snapshot captured only once
    // would miss accounts inserted earlier in this same loop and silently
    // create duplicate rows (no unique index on accounts.name).
    val accountIdsByName =
      accountDao.getAllAccountsBlocking().associateTo(mutableMapOf()) { it.name to it.id }
    val accountIdMap = mutableMapOf<Long, Long>()
    for (account in accounts) {
      val existingId = accountIdsByName[account.name]
      if (existingId != null) {
        accountDao.update(account.copy(id = existingId))
        accountIdMap[account.id] = existingId
      } else {
        val newId = accountDao.insert(account.copy(id = 0))
        accountIdsByName[account.name] = newId
        accountIdMap[account.id] = newId
      }
    }
    return accountIdMap
  }

  private suspend fun mergeTransactions(
    transactions: List<Transaction>,
    categoryIdMap: Map<Long, Long>,
    installmentIdMap: Map<Long, Long>,
    accountIdMap: Map<Long, Long>
  ) {
    val otherCategoryId = categoryDao.getCategoryByKey("Other")?.id
    // Accounts were merged before this call, so the local table reflects the
    // full target state. Transactions whose source/destination account
    // resolves to no local account (orphaned foreign ID from a malformed or
    // tampered backup) are skipped — writing them would create dangling
    // references that never surface in account dashboards. Legacy backups
    // without an accounts list keep DEFAULT_ACCOUNT_ID (1) via the
    // `?: it` fallback; the existence check below still guards that ID.
    val localAccountIds = accountDao.getAllAccountsBlocking().map { it.id }.toSet()
    for (transaction in transactions) {
      val mappedAccountId = transaction.accountId.let { accountIdMap[it] ?: it }
      val mappedDestinationAccountId =
        transaction.destinationAccountId?.let { accountIdMap[it] ?: it }
      val destinationResolved =
        when (mappedDestinationAccountId) {
          null -> transaction.destinationAccountId == null
          else -> localAccountIds.contains(mappedDestinationAccountId)
        }
      if (!localAccountIds.contains(mappedAccountId) || !destinationResolved) {
        AppLogger.w(
          "HesabyarRepository",
          "mergeFromBackup: skipping transaction=${transaction.id} " +
            "accountId=${transaction.accountId}->$mappedAccountId " +
            "destinationAccountId=${transaction.destinationAccountId}->$mappedDestinationAccountId " +
            "because no local account matches"
        )
        continue
      }
      val mappedCategoryId =
        categoryIdMap[transaction.categoryId]
          ?: otherCategoryId
          ?: transaction.categoryId
      val mappedInstallmentId = transaction.installmentId?.let { installmentIdMap[it] }
      transactionDao.insertTransaction(
        transaction.copy(
          id = 0,
          categoryId = mappedCategoryId,
          installmentId = mappedInstallmentId,
          accountId = mappedAccountId,
          destinationAccountId = mappedDestinationAccountId
        )
      )
    }
  }

  private suspend fun mergePaymentHistories(
    paymentHistories: List<PaymentHistory>,
    loanIdMap: Map<Long, Long>
  ) {
    for (payment in paymentHistories) {
      val mappedLoanId = loanIdMap[payment.loanId]
      if (mappedLoanId == null) {
        AppLogger.w("HesabyarRepository", "mergeFromBackup: skipping payment with unmapped loanId=${payment.loanId}")
        continue
      }
      paymentHistoryDao.insertPayment(payment.copy(id = 0, loanId = mappedLoanId))
    }
  }
}
