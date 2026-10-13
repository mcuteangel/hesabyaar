package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The backup envelope must carry each account's stable ID as plaintext
 * (issue #156). The ID is not sensitive and merge matching needs it without
 * the passphrase.
 */
class BackupPayloadExporterStableIdTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun forceKotlinParser() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @Test
  fun plaintextExportWritesStableIdAndRoundTrips() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)
    val stableId = "2e5944bd-3c3b-41c9-ad1d-773d2c3bfa3a"
    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 10L,
          name = "Main",
          type = AccountType.BANK,
          stableId = stableId
        )
      )
    }

    val rootJson = runBlocking { useCase.exportBackupJson() }

    val accountsJson = rootJson.getJSONArray("accounts")
    assertEquals("accounts array size", 1, accountsJson.length())
    val accountJson = accountsJson.getJSONObject(0)
    assertEquals("stableId exported", stableId, accountJson.getString("stableId"))

    val parsed = runBlocking { useCase.parseBackupJson(rootJson.toString()) }
    assertTrue("parsed payload must not be null", parsed != null)
    assertEquals("one account parsed", 1, parsed!!.accounts.size)
    assertEquals("stableId survives round-trip", stableId, parsed.accounts.first { it.id == 10L }.stableId)
  }

  @Test
  fun encryptedExportKeepsStableIdPlaintext() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)
    val stableId = "bb1894ae-852a-4429-a9ee-3a1272376cd4"
    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 11L,
          name = "Savings",
          type = AccountType.SAVINGS_INVESTMENT,
          cardNumber = "6037991122334455",
          stableId = stableId
        )
      )
    }

    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = "test-passphrase-123") }

    assertTrue("encrypted backup must have encryption metadata", rootJson.has("sensitiveFieldsEncryption"))
    val accountJson = rootJson.getJSONArray("accounts").getJSONObject(0)
    assertEquals(
      "stableId must stay plaintext under passphrase encryption",
      stableId,
      accountJson.getString("stableId")
    )
    assertFalse(
      "card number must be encrypted, proving the encryption path ran",
      accountJson.getString("cardNumber") == "6037991122334455"
    )
  }

  @Test
  fun legacyJsonWithoutStableIdParsesToBlank() {
    val useCase = ManageBackupUseCase(FakeRepository())
    val json =
      """
      {
        "version": 2,
        "timestamp": 1710000000000,
        "appVersion": "2.0.0",
        "accounts": [{"id": 1, "name": "Main", "type": "BANK"}]
      }
      """.trimIndent()

    val parsed = runBlocking { useCase.parseBackupJson(json) }

    assertTrue("parsed payload must not be null", parsed != null)
    assertEquals("legacy account parses without stableId", "", parsed!!.accounts.first().stableId)
  }
}
