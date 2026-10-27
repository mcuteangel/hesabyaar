package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.BackupPayload
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable loan links added for issue #242 (PR #417) and carried through
 * backups since issue #430:
 *  - `transactions[].loanId` — the loan whose disbursement created the row;
 *  - `paymentHistories[].linkedTransactionId` — the ledger row a tracked
 *    repayment generated.
 *
 * Both keys are optional: older backups omit them, and absent, null, zero or
 * negative values all mean "no link" so deleteLoan falls back to its legacy
 * matching for those rows.
 */
internal object BackupStableLinks {
  private const val TAG = "BackupStableLinks"

  const val TRANSACTION_LOAN_ID = "loanId"
  const val PAYMENT_LINKED_TRANSACTION_ID = "linkedTransactionId"

  /**
   * Reads an optional positive integral id; anything else is "no link".
   * Accepts JSON numbers or positive integer strings.
   * Rejects decimals and malformed scientific notation.
   */
  fun optPositiveId(
    obj: JSONObject,
    key: String
  ): Long? {
    val raw = obj.opt(key) ?: return null
    val id =
      when (raw) {
        is Int -> raw.toLong()
        is Long -> raw
        // Doubles only arrive here from JSON decimals or scientific notation.
        // Cap at 2^53: larger whole Doubles cannot represent every integer.
        is Number ->
          raw.toDouble().takeIf { it % 1.0 == 0.0 && it <= 9.007199254740992E15 }?.toLong()
        is String ->
          raw.trim().takeIf { s -> s.isNotEmpty() && s.all { it in '0'..'9' } }?.toLongOrNull()
        else -> null
      }
    return id?.takeIf { it > 0L }
  }

  /**
   * Re-apply the links from the raw JSON, keyed by each row's own id
   * (backup ids are database primary keys, hence unique within one array).
   * Rust parsing now preserves the links, but re-applying still normalizes
   * them: absent, null, zero or negative values become "no link", and links
   * on rows sharing a duplicate id are dropped as ambiguous.
   */
  fun applyFromRawJson(
    payload: BackupPayload,
    root: JSONObject?
  ): BackupPayload {
    if (root == null) return payload
    val txLoanIds = linksById(root.optJSONArray("transactions"), TRANSACTION_LOAN_ID, "transactions")
    val paymentLinks =
      linksById(root.optJSONArray("paymentHistories"), PAYMENT_LINKED_TRANSACTION_ID, "paymentHistories")
    return if (txLoanIds.isEmpty() && paymentLinks.isEmpty()) {
      payload
    } else {
      payload.copy(
        transactions = payload.transactions.map { tx -> txLoanIds[tx.id]?.let { tx.copy(loanId = it) } ?: tx },
        paymentHistories =
          payload.paymentHistories.map { p ->
            paymentLinks[p.id]?.let { p.copy(linkedTransactionId = it) } ?: p
          }
      )
    }
  }

  private fun linksById(
    array: JSONArray?,
    key: String,
    arrayName: String
  ): Map<Long, Long> {
    if (array == null) return emptyMap()
    val links = mutableMapOf<Long, Long>()
    val seenIds = HashSet<Long>()
    val duplicateIds = HashSet<Long>()
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i)
      val id = obj?.let { optPositiveId(it, "id") }
      if (id != null) {
        if (!seenIds.add(id)) duplicateIds += id
        optPositiveId(obj, key)?.let { links[id] = it }
      }
    }
    // A repeated row id makes the link ambiguous: the Rust rows cannot be
    // told apart by id, so drop the link for every row sharing that id.
    if (duplicateIds.isNotEmpty()) {
      AppLogger.w(TAG, "restore: duplicate row ids lose their links in $arrayName: $duplicateIds")
    }
    return links - duplicateIds
  }
}
