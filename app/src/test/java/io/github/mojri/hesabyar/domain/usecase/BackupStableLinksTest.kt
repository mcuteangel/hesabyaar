package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #430: the Rust parser's records have no loan-link fields, so the
 * Rust branch of [BackupJsonParser] re-applies them from the raw JSON.
 */
class BackupStableLinksTest {
  private fun tx(id: Long) =
    Transaction(id = id, type = TransactionType.EXPENSE, categoryId = 1L, amount = 1L, description = "")

  @Test
  fun appliesLinksByRowId() {
    val root =
      JSONObject()
        .put(
          "transactions",
          JSONArray()
            .put(JSONObject().put("id", 2L).put("loanId", JSONObject.NULL))
            .put(JSONObject().put("id", 1L).put("loanId", 7L))
        ).put(
          "paymentHistories",
          JSONArray().put(JSONObject().put("id", 5L).put("linkedTransactionId", 2L))
        )
    val payload =
      BackupPayload(
        transactions = listOf(tx(1L), tx(2L)),
        paymentHistories = listOf(PaymentHistory(id = 5L, loanId = 7L, amount = 1L))
      )

    val result = BackupStableLinks.applyFromRawJson(payload, root)

    assertEquals(7L, result.transactions.single { it.id == 1L }.loanId)
    assertNull(result.transactions.single { it.id == 2L }.loanId)
    assertEquals(2L, result.paymentHistories.single().linkedTransactionId)
  }

  @Test
  fun treatsMissingNullNonPositiveFractionalAndNonNumericAsNoLink() {
    listOf<Any?>(null, JSONObject.NULL, 0L, -3L, 7.9, "7.9", "7e2", "-7", "+7", "", true).forEach { raw ->
      val obj = JSONObject().put("id", 1L)
      if (raw != null) obj.put("loanId", raw)
      assertNull("value=$raw", BackupStableLinks.optPositiveId(obj, BackupStableLinks.TRANSACTION_LOAN_ID))
    }
  }

  /**
   * Older or third-party encoders may serialize ids as strings. A string of
   * plain digits is an unambiguous positive integer, so it stays a valid link
   * instead of being silently dropped.
   */
  @Test
  fun acceptsDigitOnlyStringIds() {
    assertEquals(7L, BackupStableLinks.optPositiveId(JSONObject().put("loanId", "7"), "loanId"))
    assertEquals(7L, BackupStableLinks.optPositiveId(JSONObject().put("loanId", " 7 "), "loanId"))
    assertNull(BackupStableLinks.optPositiveId(JSONObject().put("loanId", "0"), "loanId"))
  }

  @Test
  fun leavesPayloadUntouchedForLegacyBackups() {
    val payload = BackupPayload(transactions = listOf(tx(1L)))
    val root = JSONObject().put("transactions", JSONArray().put(JSONObject().put("id", 1L)))
    assertEquals(payload, BackupStableLinks.applyFromRawJson(payload, root))
  }

  @Test
  fun acceptsWholeNumbersIncludingIntegralDoubles() {
    assertEquals(7L, BackupStableLinks.optPositiveId(JSONObject().put("loanId", 7), "loanId"))
    assertEquals(7L, BackupStableLinks.optPositiveId(JSONObject().put("loanId", 7.0), "loanId"))
  }

  @Test
  fun dropsLinksForDuplicatedRowIds() {
    val root =
      JSONObject().put(
        "transactions",
        JSONArray()
          .put(JSONObject().put("id", 1L).put("loanId", 7L))
          .put(JSONObject().put("id", 1L))
      )
    val payload = BackupPayload(transactions = listOf(tx(1L), tx(1L)))

    val result = BackupStableLinks.applyFromRawJson(payload, root)

    result.transactions.forEach { assertNull(it.loanId) }
  }

  @Test
  fun rejectsImpreciseLargeDoublesAsIds() {
    // 9e18 is a whole Double but beyond 2^53, so it cannot name an id exactly.
    assertNull(BackupStableLinks.optPositiveId(JSONObject().put("loanId", 9e18), "loanId"))
    // Small integral doubles still count.
    assertEquals(7L, BackupStableLinks.optPositiveId(JSONObject().put("loanId", 7.0), "loanId"))
  }
}
