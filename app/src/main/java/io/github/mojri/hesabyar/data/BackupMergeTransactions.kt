package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.core.AppLogger

internal suspend fun backupMergeTransactions(
  transactions: List<Transaction>,
  idMaps: BackupMergeIdMaps,
  categoryDao: CategoryDao,
  accountDao: AccountDao,
  transactionDao: TransactionDao,
  resolvePersonId: (Long?, String?) -> Long?
): Map<Long, Long> {
  val otherCategoryId = categoryDao.getCategoryByKey("Other")?.id
  val localAccountIds = accountDao.getAllAccountsBlocking().map { it.id }.toSet()
  val transactionIdMap = mutableMapOf<Long, Long>()
  transactions.forEach { transaction ->
    val newId =
      backupMergeOneTransaction(
        transaction,
        idMaps,
        otherCategoryId,
        localAccountIds,
        transactionDao,
        resolvePersonId
      )
    if (newId != null && transaction.id > 0L) transactionIdMap[transaction.id] = newId
  }
  return transactionIdMap
}

private fun resolveMergeAccountIds(
  transaction: Transaction,
  idMaps: BackupMergeIdMaps,
  localAccountIds: Set<Long>
): Pair<Long, Long?>? {
  val mappedAccountId = transaction.accountId.let { idMaps.accountIdMap[it] ?: it }
  val mappedDestinationAccountId = transaction.destinationAccountId?.let { idMaps.accountIdMap[it] ?: it }
  val destinationResolved =
    mappedDestinationAccountId == null || localAccountIds.contains(mappedDestinationAccountId)
  return if (localAccountIds.contains(mappedAccountId) && destinationResolved) {
    mappedAccountId to mappedDestinationAccountId
  } else {
    null
  }
}

private fun shouldSkipTransaction(
  transaction: Transaction,
  resolvedAccounts: Pair<Long, Long?>?,
  mappedInstallmentId: Long?,
  accountIdMap: Map<Long, Long>
): Boolean {
  if (resolvedAccounts == null) {
    val mappedSource = accountIdMap[transaction.accountId] ?: transaction.accountId
    val rawDest = transaction.destinationAccountId
    val mappedDest = if (rawDest != null) accountIdMap[rawDest] ?: rawDest else null
    AppLogger.w(
      "HesabyarRepository",
      "mergeFromBackup: skipping transaction=${transaction.id} " +
        "accountId=${transaction.accountId}->$mappedSource " +
        "destinationAccountId=$rawDest->$mappedDest because no local account matches"
    )
  } else if (transaction.installmentId != null && mappedInstallmentId == null) {
    AppLogger.w(
      "HesabyarRepository",
      "mergeFromBackup: skipping transaction=${transaction.id} " +
        "installmentId=${transaction.installmentId} because referenced installment was skipped"
    )
  } else {
    return false
  }
  return true
}

private fun resolveMappedLoanId(
  transaction: Transaction,
  loanIdMap: Map<Long, Long>
): Long? {
  val originalLoanId = transaction.loanId ?: return null
  val mappedLoanId = loanIdMap[originalLoanId]
  if (mappedLoanId == null) {
    AppLogger.w(
      "HesabyarRepository",
      "mergeFromBackup: transaction=${transaction.id} loses loanId=$originalLoanId: loan was not merged"
    )
  }
  return mappedLoanId
}

private suspend fun backupMergeOneTransaction(
  transaction: Transaction,
  idMaps: BackupMergeIdMaps,
  otherCategoryId: Long?,
  localAccountIds: Set<Long>,
  transactionDao: TransactionDao,
  resolvePersonId: (Long?, String?) -> Long?
): Long? {
  val resolvedAccounts = resolveMergeAccountIds(transaction, idMaps, localAccountIds)
  val mappedInstallmentId = transaction.installmentId?.let { idMaps.installmentIdMap[it] }
  if (shouldSkipTransaction(transaction, resolvedAccounts, mappedInstallmentId, idMaps.accountIdMap)) {
    return null
  }
  val (mappedAccountId, mappedDestinationAccountId) = checkNotNull(resolvedAccounts)
  val mappedCategoryId =
    idMaps.categoryIdMap[transaction.categoryId]
      ?: otherCategoryId ?: transaction.categoryId
  val mappedPersonId = resolvePersonId(transaction.personId, transaction.personName)
  val mappedLoanId = resolveMappedLoanId(transaction, idMaps.loanIdMap)
  return transactionDao.insertTransaction(
    transaction.copy(
      id = 0,
      categoryId = mappedCategoryId,
      installmentId = mappedInstallmentId,
      accountId = mappedAccountId,
      destinationAccountId = mappedDestinationAccountId,
      personId = mappedPersonId,
      loanId = mappedLoanId
    )
  )
}
