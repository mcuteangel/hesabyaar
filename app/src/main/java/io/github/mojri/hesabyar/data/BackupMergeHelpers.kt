package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
import java.util.UUID

internal suspend fun backupMergeCategories(
  categories: List<Category>,
  categoryDao: CategoryDao
): Map<Long, Long> {
  val keyToId = mutableMapOf<String, Long>()
  val idToKey = mutableMapOf<Long, String>()
  for (category in categories) {
    val existing = categoryDao.getCategoryByKey(category.key)
    val savedId =
      if (existing != null) {
        // The persisted isDefault flag is authoritative (CategoryDelegate
        // .updateCategory refuses isDefault flips for the same reason): a
        // backup carrying isDefault=false on the live default row must not
        // silently unprotect it, or the delete guard becomes bypassable.
        categoryDao.updateCategory(category.copy(id = existing.id, isDefault = existing.isDefault))
        existing.id
      } else {
        categoryDao.insertCategory(category.copy(id = 0))
      }
    keyToId[category.key] = savedId
    idToKey[category.id] = category.key
  }
  return idToKey.mapValues { keyToId[it.value] ?: it.key }
}

internal suspend fun backupMergeLoans(
  loans: List<Loan>,
  loanDao: LoanDao,
  accountIdMap: Map<Long, Long> = emptyMap(),
  resolvePersonId: (Long?, String?) -> Long?
): Map<Long, Long> =
  loans.associate { loan ->
    val mappedPersonId = resolvePersonId(loan.personId, loan.personName)
    val mappedAccountId = loan.accountId?.let { accountIdMap[it] ?: it }
    loan.id to
      loanDao.insertLoan(
        normalizeLoanForRestore(
          loan.copy(
            id = 0,
            personId = mappedPersonId,
            accountId = mappedAccountId
          )
        )
      )
  }

internal suspend fun backupMergeBankLoans(
  bankLoans: List<BankLoan>,
  bankLoanDao: BankLoanDao,
  accountIdMap: Map<Long, Long> = emptyMap()
): Map<Long, Long> =
  bankLoans.associate {
    val mappedAccountId = it.accountId?.let { id -> accountIdMap[id] ?: id }
    it.id to
      bankLoanDao.insertBankLoan(
        normalizeBankLoanForRestore(
          it.copy(
            id = 0,
            accountId = mappedAccountId
          )
        )
      )
  }

internal suspend fun backupMergeInstallments(
  installments: List<Installment>,
  installmentDao: InstallmentDao,
  bankLoanIdMap: Map<Long, Long>,
  accountIdMap: Map<Long, Long> = emptyMap()
): Map<Long, Long> {
  val idMap = mutableMapOf<Long, Long>()
  for (installment in installments) {
    val mappedBankLoanId = installment.bankLoanId?.let(bankLoanIdMap::get)
    if (installment.bankLoanId != null && mappedBankLoanId == null) {
      // Mirrors backupMergePaymentHistories: an installment whose bankLoanId
      // does not map to a merged bank loan would otherwise silently drop the
      // FK (bankLoanId?.let(map::get) yields null). Skip it with a warning
      // so a malformed backup cannot silently strand orphan installments.
      AppLogger.w(
        "HesabyarRepository",
        "mergeFromBackup: skipping installment=${installment.id} " +
          "bankLoanId=${installment.bankLoanId} because no merged bank loan matches"
      )
      continue
    }
    val mappedAccountId = installment.accountId?.let { accountIdMap[it] ?: it }
    val candidate = installment.copy(id = 0, bankLoanId = mappedBankLoanId, accountId = mappedAccountId)
    val newId = installmentDao.insertInstallment(normalizeInstallmentForRestore(candidate))
    idMap[installment.id] = newId
  }
  return idMap
}

/**
 * Identity index for backup account merge. Matches by stable ID first;
 * name matching stays as the legacy fallback for backups and rows that
 * predate stable IDs (issue #156).
 */
private class AccountMergeIndex(
  locals: List<AccountEntity>
) {
  private val byStableId = mutableMapOf<String, AccountEntity>()
  private val byName = mutableMapOf<String, AccountEntity>()

  init {
    locals.forEach { track(it) }
  }

  fun findByStableId(stableId: String): AccountEntity? = byStableId[stableId]

  fun findByName(name: String): AccountEntity? = byName[name]

  /** Records a local row; rows without a stable ID are name-indexed only. */
  fun track(
    row: AccountEntity,
    previousName: String? = null
  ) {
    if (previousName != null && previousName != row.name) byName.remove(previousName)
    byName[row.name] = row
    if (row.stableId.isNotBlank()) byStableId[row.stableId] = row
  }
}

internal suspend fun backupMergeAccounts(
  accounts: List<AccountEntity>,
  accountDao: AccountDao
): Map<Long, Long> {
  val index = AccountMergeIndex(accountDao.getAllAccountsBlocking())
  val accountIdMap = mutableMapOf<Long, Long>()
  for (account in accounts) {
    accountIdMap[account.id] = backupMergeOneAccount(account, index, accountDao)
  }
  return accountIdMap
}

private suspend fun backupMergeOneAccount(
  account: AccountEntity,
  index: AccountMergeIndex,
  accountDao: AccountDao
): Long {
  val stableId = account.stableId.takeIf { it.isNotBlank() }
  val idMatch = stableId?.let { index.findByStableId(it) }
  val nameMatch = index.findByName(account.name)
  // Same name but two different stable identities: never merge — the backup
  // row is a distinct account that happens to share the name (issue #156).
  val localId = nameMatch?.stableId?.takeIf { it.isNotBlank() }
  val isDistinctIdentity = localId != null && stableId != null && localId != stableId
  return when {
    idMatch != null -> {
      // Stable-ID hit wins outright. The row keeps its identity; the backup
      // payload refreshes the rest. Re-index under the (possibly new) name.
      val updated = account.copy(id = idMatch.id)
      accountDao.update(updated)
      index.track(updated, previousName = idMatch.name)
      idMatch.id
    }
    nameMatch != null && !isDistinctIdentity -> {
      // Legacy fallback: at least one side has no stable ID. Never overwrite
      // a non-blank local ID with a blank backup one; adopt the backup ID
      // when the local row has none, so legacy rows self-heal.
      val resolvedStableId = nameMatch.stableId.ifBlank { account.stableId }
      val updated = account.copy(id = nameMatch.id, stableId = resolvedStableId)
      accountDao.update(updated)
      index.track(updated)
      nameMatch.id
    }
    else -> {
      val freshId = stableId ?: UUID.randomUUID().toString()
      val insertedId = accountDao.insert(account.copy(id = 0, stableId = freshId))
      index.track(account.copy(id = insertedId, stableId = freshId))
      insertedId
    }
  }
}

internal suspend fun backupMergePersons(
  persons: List<Person>,
  personDao: PersonDao
): Map<String, Long> {
  val existingByKey =
    personDao
      .getAllPersonsIncludingArchived()
      .associateBy { it.normalizedName }
      .toMutableMap()
  for (person in persons) {
    backupMergeOnePerson(person, existingByKey, personDao)
  }
  return existingByKey.mapValues { it.value.id }
}

internal suspend fun backupMergeOnePerson(
  person: Person,
  existingByKey: MutableMap<String, Person>,
  personDao: PersonDao
) {
  val display = PersonNameNormalizer.displayForm(person.name)
  val key = PersonNameNormalizer.normalize(display)
  if (key.isEmpty()) return
  val existing = existingByKey[key] ?: personDao.getPersonByNormalizedName(key)
  if (existing != null) {
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

/** Source-id to local-id maps the transaction merge resolves references through. */
internal data class BackupMergeIdMaps(
  val categoryIdMap: Map<Long, Long>,
  val installmentIdMap: Map<Long, Long>,
  val accountIdMap: Map<Long, Long>,
  val loanIdMap: Map<Long, Long>
)

internal suspend fun backupMergePaymentHistories(
  paymentHistories: List<PaymentHistory>,
  loanIdMap: Map<Long, Long>,
  transactionIdMap: Map<Long, Long>,
  paymentHistoryDao: PaymentHistoryDao
) {
  for (payment in paymentHistories) {
    val mappedLoanId = loanIdMap[payment.loanId]
    if (mappedLoanId == null) {
      AppLogger.w(
        "HesabyarRepository",
        "mergeFromBackup: skipping payment=${payment.id} with unmapped loanId=${payment.loanId}"
      )
      continue
    }
    // Issue #430: re-point the repayment's ledger link to the merged row; a
    // link to a skipped or absent transaction becomes null (legacy matching).
    val mappedLinkedTransactionId = payment.linkedTransactionId?.let { transactionIdMap[it] }
    if (payment.linkedTransactionId != null && mappedLinkedTransactionId == null) {
      AppLogger.w(
        "HesabyarRepository",
        "mergeFromBackup: payment=${payment.id} loses linkedTransactionId=${payment.linkedTransactionId}: " +
          "transaction was skipped"
      )
    }
    paymentHistoryDao.insertPayment(
      payment.copy(id = 0, loanId = mappedLoanId, linkedTransactionId = mappedLinkedTransactionId)
    )
  }
}
