package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageBackupUseCaseTest {
  private val useCase = ManageBackupUseCase(FakeRepository())

  private fun buildBackupJson(block: JSONObject.() -> Unit): String = JSONObject().apply(block).toString()

  // --- success: transactions ---

  @Test
  fun `parseBackupJson fallback parses transaction fields`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().put(
            JSONObject().apply {
              put("id", 7L)
              put("type", "INCOME")
              put("categoryId", 3L)
              put("amount", 1_250_000L)
              put("description", "salary")
              put("personName", "Boss")
              put("date", 1_700_000_000_000L)
              put("dueDate", 1_800_000_000_000L)
              put("installmentId", 5L)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertTrue(result != null)
    assertEquals(1, result!!.transactions.size)
    val tx = result.transactions[0]
    assertEquals(7L, tx.id)
    assertEquals(TransactionType.INCOME, tx.type)
    assertEquals(3L, tx.categoryId)
    assertEquals(1_250_000L, tx.amount)
    assertEquals("salary", tx.description)
    assertEquals("Boss", tx.personName)
    assertEquals(1_700_000_000_000L, tx.date)
    assertEquals(1_800_000_000_000L, tx.dueDate)
    assertEquals(5L, tx.installmentId)
  }

  @Test
  fun `parseBackupJson fallback defaults transaction type when omitted`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().put(
            JSONObject().apply {
              put("id", 1L)
              put("amount", 500L)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.transactions.size)
    assertEquals(TransactionType.EXPENSE, result.transactions[0].type)
  }

  @Test
  fun `parseBackupJson fallback uses default type for invalid type`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().apply {
            put(
              JSONObject().apply {
                put("id", 1L)
                put("type", "BOGUS")
                put("amount", 500L)
              }
            )
            put(
              JSONObject().apply {
                put("id", 2L)
                put("type", "INCOME")
                put("amount", 800L)
              }
            )
          }
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(2, result!!.transactions.size)
    assertEquals(1L, result.transactions[0].id)
    assertEquals(TransactionType.EXPENSE, result.transactions[0].type)
    assertEquals(2L, result.transactions[1].id)
    assertEquals(TransactionType.INCOME, result.transactions[1].type)
  }

  // --- success: loans ---

  @Test
  fun `parseBackupJson fallback parses loan fields`() {
    val json =
      buildBackupJson {
        put(
          "loans",
          JSONArray().put(
            JSONObject().apply {
              put("id", 11L)
              put("personName", "Reza")
              put("type", "CREDITOR")
              put("originalAmount", 5_000_000L)
              put("remainingAmount", 2_000_000L)
              put("description", "lent")
              put("date", 1_600_000_000_000L)
              put("isSettled", true)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.loans.size)
    val loan = result.loans[0]
    assertEquals(11L, loan.id)
    assertEquals("Reza", loan.personName)
    assertEquals(LoanType.CREDITOR, loan.type)
    assertEquals(5_000_000L, loan.originalAmount)
    assertEquals(2_000_000L, loan.remainingAmount)
    assertEquals(true, loan.isSettled)
  }

  // --- success: installments ---

  @Test
  fun `parseBackupJson fallback parses installment fields`() {
    val json =
      buildBackupJson {
        put(
          "installments",
          JSONArray().put(
            JSONObject().apply {
              put("id", 21L)
              put("title", "Car")
              put("amount", 900_000L)
              put("dueDate", 1_500_000_000_000L)
              put("isPaid", true)
              put("reminderEnabled", false)
              put("notes", "n")
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.installments.size)
    val inst = result.installments[0]
    assertEquals(21L, inst.id)
    assertEquals("Car", inst.title)
    assertEquals(900_000L, inst.amount)
    assertEquals(true, inst.isPaid)
    assertEquals(false, inst.reminderEnabled)
  }

  // --- success: categories ---

  @Test
  fun `parseBackupJson fallback parses category fields`() {
    val json =
      buildBackupJson {
        put(
          "categories",
          JSONArray().put(
            JSONObject().apply {
              put("id", 31L)
              put("name", "Food")
              put("key", "food")
              put("icon", "ic_food")
              put("color", 0xFF0000L)
              put("type", "EXPENSE")
              put("isDefault", true)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.categories.size)
    val cat = result.categories[0]
    assertEquals(31L, cat.id)
    assertEquals("Food", cat.name)
    assertEquals(CategoryType.EXPENSE, cat.type)
    assertEquals(true, cat.isDefault)
  }

  // --- success: paymentHistories ---

  @Test
  fun `parseBackupJson fallback parses paymentHistories`() {
    val json =
      buildBackupJson {
        put(
          "paymentHistories",
          JSONArray().put(
            JSONObject().apply {
              put("id", 41L)
              put("loanId", 11L)
              put("amount", 100_000L)
              put("date", 1_400_000_000_000L)
              put("notes", "partial")
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.paymentHistories.size)
    val ph = result.paymentHistories[0]
    assertEquals(41L, ph.id)
    assertEquals(11L, ph.loanId)
    assertEquals(100_000L, ph.amount)
  }

  // --- success: settings ---

  @Test
  fun `parseBackupJson fallback parses settings darkMode false`() {
    val json =
      buildBackupJson {
        put(
          "settings",
          JSONObject().apply {
            put("darkMode", false)
          }
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(false, result!!.settings.darkMode)
  }

  @Test
  fun `parseBackupJson fallback defaults settings when missing`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertEquals(true, result!!.settings.darkMode)
  }

  // --- success: top-level metadata + defaults ---

  @Test
  fun `parseBackupJson fallback uses explicit version and appVersion`() {
    val json =
      buildBackupJson {
        put("version", 3)
        put("appVersion", "2.4")
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(3, result!!.version)
    assertEquals("2.4", result.appVersion)
  }

  @Test
  fun `parseBackupJson fallback returns defaults for empty object`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertTrue(result != null)
    assertEquals(BuildConfig.BACKUP_SCHEMA_VERSION, result!!.version)
    assertEquals(BuildConfig.VERSION_NAME, result.appVersion)
    assertTrue(result.transactions.isEmpty())
    assertTrue(result.loans.isEmpty())
    assertTrue(result.installments.isEmpty())
    assertTrue(result.categories.isEmpty())
    assertTrue(result.paymentHistories.isEmpty())
    assertTrue(result.timestamp > 0)
  }

  // --- failure paths ---

  @Test
  fun `parseBackupJson fallback returns null on malformed JSON`() {
    val result = runBlocking { useCase.parseBackupJson("this is not json {{{") }
    assertNull(result)
  }

  @Test
  fun `parseBackupJson fallback returns null on non-object JSON`() {
    val result = runBlocking { useCase.parseBackupJson("[1,2,3]") }
    assertNull(result)
  }

  @Test
  fun `parseBackupJson skips invalid paymentHistories item and restores rest`() {
    // A non-object entry in paymentHistories is skipped (not a fatal crash),
    // so the rest of the backup still parses and the payload is non-null.
    val json =
      buildBackupJson {
        put(
          "paymentHistories",
          JSONArray().put(JSONObject.NULL)
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertTrue(result != null)
    assertTrue(result!!.paymentHistories.isEmpty())
  }

  @Test
  fun `parseBackupJson fallback tolerates missing arrays`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertTrue(result!!.transactions.isEmpty())
    assertTrue(result.loans.isEmpty())
    assertTrue(result.installments.isEmpty())
    assertTrue(result.categories.isEmpty())
    assertFalse(result.toString().isEmpty())
  }

  // --- round-trip: accountId / destinationAccountId preservation ---

  @Test
  fun `exportBackupJson and parseBackupJson round-trip preserves accountId and destinationAccountId`() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    val nonDefaultAccountId = 5L
    val destAccountId = 3L

    runBlocking {
      repo.insertTransaction(
        Transaction(
          id = 1L,
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 100_000L,
          description = "transfer out",
          accountId = nonDefaultAccountId,
          destinationAccountId = destAccountId
        )
      )
    }

    val json = runBlocking { useCase.exportBackupJson() }
    val result = runBlocking { useCase.parseBackupJson(json.toString()) }

    assertTrue(result != null)
    assertEquals(1, result!!.transactions.size)
    val tx = result.transactions[0]
    assertEquals(nonDefaultAccountId, tx.accountId)
    assertEquals(destAccountId, tx.destinationAccountId)
  }
}
