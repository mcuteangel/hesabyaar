package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.domain.exception.CannotDeleteLastActiveAccountException
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
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
  private val personDao: PersonDao,
  private val database: AppDatabase
) : HesabyarRepositoryInterface,
  PersonRepositoryInterface {
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
    // Default categories (e.g. key="Loans") are infrastructure: loan
    // repayments and KPI exclusion resolve them by key. The UI hides the
    // delete affordance for them; this guard closes non-UI paths so the
    // category cannot silently disappear and degrade those lookups.
    //
    // We re-read `isDefault` from the persisted row rather than trusting
    // the caller's parameter. A caller holding a stale or hand-built
    // `Category(id=…, isDefault=false)` could otherwise delete the
    // default row by primary key.
    val persisted = categoryDao.getCategoryById(category.id)
    if (persisted == null) {
      AppLogger.w(
        "HesabyarRepository",
        "deleteCategory: category id=${category.id} not found; nothing to delete"
      )
      return
    }
    if (persisted.isDefault) {
      AppLogger.w(
        "HesabyarRepository",
        "deleteCategory: refusing to delete default category id=${persisted.id} key=${persisted.key} " +
          "(caller-provided isDefault=${category.isDefault})"
      )
      return
    }
    categoryDao.deleteCategory(persisted)
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
    database.withTransaction {
      paymentHistoryDao.deletePaymentHistoryForLoan(loan.id)
      loanDao.deleteLoan(loan)
    }
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
      // Overpayments are rejected (not clamped) so the caller can warn the user
      // instead of silently recording less money than they handed over.
      if (amount > loan.remainingAmount) return@withTransaction false
      val newRemaining = loan.remainingAmount - amount
      val isSettled = newRemaining == 0L
      val effectiveAmount = amount
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
      // Only the last ACTIVE (non-archived) account is protected: deleting it
      // would leave zero usable accounts even though archived ones exist.
      // Deleting an archived account is always allowed — the active count
      // stays unchanged.
      val activeAccountCount = allAccounts.count { !it.isArchived }
      if (activeAccountCount == 1 && allAccounts.any { it.id == account.id && !it.isArchived }) {
        throw CannotDeleteLastActiveAccountException(account.id)
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

  // Person CRUD
  override val allPersons: Flow<List<Person>> = personDao.getAllPersons()

  override suspend fun getAllPersonsIncludingArchived(): List<Person> =
    personDao.getAllPersonsIncludingArchivedBlocking()

  override suspend fun getPersonById(id: Long): Person? = personDao.getPersonById(id)

  override suspend fun upsertPerson(person: Person): Person {
    val display = PersonNameNormalizer.displayForm(person.name)
    require(display.isNotEmpty()) { "Person name is blank" }
    val key = PersonNameNormalizer.normalize(display)
    require(key.isNotEmpty()) { "Person name normalizes to empty" }
    val existing = personDao.getPersonByNormalizedName(key)
    if (existing != null) {
      val merged =
        existing.copy(
          phone = person.phone ?: existing.phone,
          notes = person.notes ?: existing.notes
        )
      personDao.updatePerson(merged)
      return merged
    }
    val candidate =
      person.copy(
        id = 0,
        name = display,
        normalizedName = key,
        createdAt = person.createdAt.takeIf { it != 0L } ?: System.currentTimeMillis()
      )
    val id = personDao.insertPerson(candidate)
    return if (id != -1L) candidate.copy(id = id) else requireNotNull(personDao.getPersonByNormalizedName(key))
  }

  override suspend fun renamePerson(
    personId: Long,
    newName: String
  ): Boolean {
    val display = PersonNameNormalizer.displayForm(newName)
    if (display.isEmpty()) return false
    return database.withTransaction {
      val person = personDao.getPersonById(personId) ?: return@withTransaction false
      val key = PersonNameNormalizer.normalize(display)
      if (key.isEmpty()) return@withTransaction false
      val clash = personDao.getPersonByNormalizedName(key)
      if (clash != null && clash.id != personId) return@withTransaction false
      personDao.updatePerson(person.copy(name = display, normalizedName = key))
      loanDao.syncLoanPersonNames(personId, display)
      transactionDao.syncTransactionPersonNames(personId, display)
      true
    }
  }

  override suspend fun deletePerson(person: Person) {
    // Loans/transactions keep their denormalized personName and a dangling
    // personId; display never joins Person (D3), so no data is lost. A
    // merge/reassign flow is deliberately out of scope for Phase 1.
    database.withTransaction { personDao.deletePerson(person) }
  }

  override suspend fun replaceAllFromBackup(backup: BackupPayload) =
    database.withTransaction {
      clearAllTablesForReplace()
      reseedDefaultAccountIfNeeded(backup.accounts.isEmpty())
      backup.categories.forEach { categoryDao.insertCategory(it) }
      val personMaps = insertPersonsForReplace(backup.persons)
      insertLoansWithPersonRemap(backup.loans, personMaps)
      insertTransactionsWithPersonRemap(backup.transactions, personMaps)
      backup.installments.forEach { installmentDao.insertInstallment(it) }
      backup.paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
      backup.bankLoans.forEach { bankLoanDao.insertBankLoan(it) }
      backup.accounts.forEach { accountDao.insert(it) }
    }

  private suspend fun clearAllTablesForReplace() {
    transactionDao.deleteAllTransactions()
    loanDao.deleteAllLoans()
    installmentDao.deleteAllInstallments()
    paymentHistoryDao.deleteAllPaymentHistory()
    bankLoanDao.deleteAllBankLoans()
    accountDao.deleteAllAccounts()
    personDao.deleteAllPersons()
  }

  private suspend fun reseedDefaultAccountIfNeeded(isEmpty: Boolean) {
    if (isEmpty) accountDao.insert(AccountEntity.DEFAULT_ACCOUNT)
  }

  private suspend fun insertPersonsForReplace(persons: List<Person>): PersonKeyMaps {
    val sourceIdToKey =
      persons.associate {
        it.id to
          PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it.name))
      }
    val keyToLocalId = mutableMapOf<String, Long>()
    for (raw in persons) {
      insertOnePersonForReplace(raw, keyToLocalId)
    }
    return PersonKeyMaps(sourceIdToKey, keyToLocalId)
  }

  private suspend fun insertOnePersonForReplace(
    raw: Person,
    keyToLocalId: MutableMap<String, Long>
  ) {
    val display = PersonNameNormalizer.displayForm(raw.name)
    val key = PersonNameNormalizer.normalize(display)
    if (key.isEmpty() || keyToLocalId.containsKey(key)) return
    val newId = personDao.insertPerson(raw.copy(name = display, normalizedName = key, id = 0))
    val storedId =
      if (newId != -1L) {
        newId
      } else {
        personDao.getPersonByNormalizedName(key)?.id ?: return
      }
    keyToLocalId[key] = storedId
  }

  private suspend fun insertLoansWithPersonRemap(
    loans: List<Loan>,
    maps: PersonKeyMaps
  ) {
    for (loan in loans) {
      val mappedPersonId = resolvePersonId(loan.personId, loan.personName, maps)
      loanDao.insertLoan(loan.copy(personId = mappedPersonId))
    }
  }

  private suspend fun insertTransactionsWithPersonRemap(
    transactions: List<Transaction>,
    maps: PersonKeyMaps
  ) {
    for (tx in transactions) {
      val mappedPersonId = resolvePersonId(tx.personId, tx.personName, maps)
      transactionDao.insertTransaction(tx.copy(personId = mappedPersonId))
    }
  }

  private data class PersonKeyMaps(
    val sourceIdToKey: Map<Long, String>,
    val keyToLocalId: Map<String, Long>
  )

  private fun resolvePersonId(
    sourcePersonId: Long?,
    fallbackName: String?,
    maps: PersonKeyMaps
  ): Long? {
    val fromSource =
      sourcePersonId
        ?.let { maps.sourceIdToKey[it] }
        ?.takeIf { it.isNotEmpty() }
        ?.let { maps.keyToLocalId[it] }
    val fallbackKey =
      fallbackName
        ?.let { PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it)) }
        ?.takeIf { it.isNotEmpty() }
    return fromSource ?: fallbackKey?.let { maps.keyToLocalId[it] }
  }

  /**
   * Merges backup data into the database while preserving relationships through foreign-key remapping.
   *
   * @param backup The backup payload containing categories and related records to merge.
   */
  override suspend fun mergeFromBackup(backup: BackupPayload) =
    database.withTransaction {
      val categoryIdMap = mergeCategories(backup.categories)
      val personKeyToId = mergePersons(backup.persons)
      val personMaps =
        PersonKeyMaps(
          sourceIdToKey =
            backup.persons.associate {
              it.id to
                PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it.name))
            },
          keyToLocalId = personKeyToId
        )

      fun resolveForMerge(
        sourcePersonId: Long?,
        fallbackName: String?
      ): Long? = resolvePersonId(sourcePersonId, fallbackName, personMaps)

      val loanIdMap = mergeLoans(backup.loans, ::resolveForMerge)
      val bankLoanIdMap = mergeBankLoans(backup.bankLoans)
      val installmentIdMap = mergeInstallments(backup.installments, bankLoanIdMap)
      val accountIdMap = mergeAccounts(backup.accounts)
      mergeTransactions(backup.transactions, categoryIdMap, installmentIdMap, accountIdMap, ::resolveForMerge)
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

  private suspend fun mergeLoans(
    loans: List<Loan>,
    resolvePersonId: (Long?, String?) -> Long? = { _, _ -> null }
  ): Map<Long, Long> =
    loans.associate { loan ->
      val mappedPersonId = resolvePersonId(loan.personId, loan.personName)
      loan.id to loanDao.insertLoan(loan.copy(id = 0, personId = mappedPersonId))
    }

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

  private suspend fun mergePersons(persons: List<Person>): Map<String, Long> {
    // Dedup by re-derived normalizedName (do not trust backup payload).
    // Preload existing keys so the loop avoids N queries when the backup
    // carries many persons (mirrors mergeAccounts' name→id map).
    val existingByKey =
      personDao
        .getAllPersonsIncludingArchivedBlocking()
        .associateBy { it.normalizedName }
        .toMutableMap()
    for (person in persons) {
      mergeOnePerson(person, existingByKey)
    }
    return existingByKey.mapValues { it.value.id }
  }

  private suspend fun mergeOnePerson(
    person: Person,
    existingByKey: MutableMap<String, Person>
  ) {
    val display = PersonNameNormalizer.displayForm(person.name)
    val key = PersonNameNormalizer.normalize(display)
    if (key.isEmpty()) return
    val existing = existingByKey[key] ?: personDao.getPersonByNormalizedName(key)
    if (existing != null) {
      // Merge (do not let the backup overwrite local identity fields): keep the
      // local name, createdAt and isArchived. Only fill blank phone/notes from
      // the backup so a local edit is never clobbered by a stale backup value.
      val merged =
        existing.copy(
          normalizedName = key,
          phone = if (existing.phone.isNullOrBlank()) person.phone else existing.phone,
          notes = if (existing.notes.isNullOrBlank()) person.notes else existing.notes
        )
      personDao.updatePerson(merged)
      existingByKey[key] = merged
    } else {
      val candidate = person.copy(id = 0, name = display, normalizedName = key)
      val insertedId = personDao.insertPerson(candidate)
      val stored =
        if (insertedId != -1L) {
          candidate.copy(id = insertedId)
        } else {
          personDao.getPersonByNormalizedName(key) ?: return
        }
      existingByKey[key] = stored
    }
  }

  private suspend fun mergeTransactions(
    transactions: List<Transaction>,
    categoryIdMap: Map<Long, Long>,
    installmentIdMap: Map<Long, Long>,
    accountIdMap: Map<Long, Long>,
    resolvePersonId: (Long?, String?) -> Long? = { _, _ -> null }
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
      val mappedPersonId = resolvePersonId(transaction.personId, transaction.personName)
      transactionDao.insertTransaction(
        transaction.copy(
          id = 0,
          categoryId = mappedCategoryId,
          installmentId = mappedInstallmentId,
          accountId = mappedAccountId,
          destinationAccountId = mappedDestinationAccountId,
          personId = mappedPersonId
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
