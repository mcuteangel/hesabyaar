package io.github.mojri.hesabyar.domain.usecase

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
  const val TRANSACTION_LOAN_ID = "loanId"
  const val PAYMENT_LINKED_TRANSACTION_ID = "linkedTransactionId"

  /** Reads an optional positive id; anything else is "no link". */
  fun optPositiveId(
    obj: JSONObject,
    key: String
  ): Long? {
    if (!obj.has(key) || obj.isNull(key)) return null
    return obj.optLong(key, 0L).takeIf { it > 0L }
  }

  /**
   * The Rust record types predate the links and drop them while parsing, so
   * re-apply them from the raw JSON, keyed by each row's own id (backup ids
   * are database primary keys, hence unique within one array).
   */
  fun applyFromRawJson(
    payload: BackupPayload,
    root: JSONObject?
  ): BackupPayload {
    if (root == null) return payload
    val txLoanIds = linksById(root.optJSONArray("transactions"), TRANSACTION_LOAN_ID)
    val paymentLinks = linksById(root.optJSONArray("paymentHistories"), PAYMENT_LINKED_TRANSACTION_ID)
    if (txLoanIds.isEmpty() && paymentLinks.isEmpty()) return payload
    return payload.copy(
      transactions = payload.transactions.map { tx -> txLoanIds[tx.id]?.let { tx.copy(loanId = it) } ?: tx },
      paymentHistories =
        payload.paymentHistories.map { p ->
          paymentLinks[p.id]?.let { p.copy(linkedTransactionId = it) } ?: p
        }
    )
  }

  private fun linksById(
    array: JSONArray?,
    key: String
  ): Map<Long, Long> {
    if (array == null) return emptyMap()
    val links = mutableMapOf<Long, Long>()
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      val id = obj.optLong("id", 0L).takeIf { it > 0L } ?: continue
      optPositiveId(obj, key)?.let { links[id] = it }
    }
    return links
  }
}
