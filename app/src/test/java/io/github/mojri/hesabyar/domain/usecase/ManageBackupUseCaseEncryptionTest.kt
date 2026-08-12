package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.auth.BackupCipher
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BackupPayload
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the PBKDF2 iteration count in the backup encryption metadata:
 * the value written on export must be read back and used on restore, validated
 * against a security floor, and defaulted when absent.
 */
class ManageBackupUseCaseEncryptionTest {
  private val useCase = ManageBackupUseCase(FakeRepository())

  /**
   * Builds a raw backup JSON whose sensitive account fields are encrypted with a key
   * derived at [iterations], plus a matching parsed [BackupPayload] with the same
   * account ids. Models a backup produced by an app version that used [iterations]
   * as its PBKDF2 work factor.
   */
  private fun encryptedBackupFixture(
    passphrase: String,
    iterations: Int,
    account: AccountEntity
  ): Pair<JSONObject, BackupPayload> {
    val salt = BackupCipher.generateSalt()
    val key = BackupCipher.deriveKey(passphrase, salt, iterations)
    val rootJson =
      JSONObject().apply {
        put(
          "sensitiveFieldsEncryption",
          JSONObject().apply {
            put("salt", salt)
            put("iterations", iterations)
          }
        )
        put(
          "accounts",
          JSONArray().put(
            JSONObject().apply {
              put("id", account.id)
              put("name", account.name)
              put(
                "cardNumber",
                BackupCipher.encryptOrNull(
                  account.cardNumber,
                  key,
                  BackupCipher.accountFieldAad(account.id, "cardNumber")
                )
              )
              put(
                "accountNumber",
                BackupCipher.encryptOrNull(
                  account.accountNumber,
                  key,
                  BackupCipher.accountFieldAad(account.id, "accountNumber")
                )
              )
              put(
                "iban",
                BackupCipher.encryptOrNull(
                  account.iban,
                  key,
                  BackupCipher.accountFieldAad(account.id, "iban")
                )
              )
            }
          )
        )
      }
    return rootJson to BackupPayload(accounts = listOf(account))
  }

  @Test
  fun decryptHonorsDeclaredIterationCountNotHardCodedDefault() {
    val passphrase = "my-secret-passphrase"
    val declaredIterations = 700_000
    val realCard = "621986101234567890123456"
    val realIban = "IR123456789012345678901234"
    val account =
      AccountEntity(
        id = 1,
        name = "حساب اصلی",
        type = AccountType.BANK,
        cardNumber = realCard,
        iban = realIban
      )
    // Fields encrypted under a key derived at 700k. Decrypting with the hard-coded
    // 600k default derives a different key and fails the GCM tag, so recovering the
    // fields proves the declared count was read from the backup and actually used.
    val (rootJson, parsed) = encryptedBackupFixture(passphrase, declaredIterations, account)

    val decrypted =
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
      }

    assertEquals(
      "cardNumber must be recovered via the declared iteration count",
      realCard,
      decrypted.accounts[0].cardNumber
    )
    assertEquals(
      "iban must be recovered via the declared iteration count",
      realIban,
      decrypted.accounts[0].iban
    )
  }

  @Test
  fun decryptRejectsIterationCountBelowMinimumFloor() {
    val passphrase = "my-secret-passphrase"
    val tamperedIterations = 1_000
    val account =
      AccountEntity(
        id = 1,
        name = "حساب اصلی",
        type = AccountType.BANK,
        cardNumber = "6219861012345678",
        iban = "IR12345"
      )
    // A tampered backup declaring a tiny work factor must be rejected before key
    // derivation, so the passphrase can never be brute-forced against a weak key.
    val (rootJson, parsed) = encryptedBackupFixture(passphrase, tamperedIterations, account)

    val e =
      org.junit.Assert.assertThrows(
        "Below-floor iteration count must be rejected",
        IllegalArgumentException::class.java
      ) {
        runBlocking {
          useCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
        }
      }
    assertTrue(
      "Error message must mention the offending count, got: ${e.message}",
      e.message!!.contains("1000")
    )
  }

  @Test
  fun decryptFallsBackToDefaultIterationsWhenFieldMissing() {
    val passphrase = "my-secret-passphrase"
    val realCard = "6219861012345678"
    val realIban = "IR12345"
    val account =
      AccountEntity(
        id = 1,
        name = "حساب اصلی",
        type = AccountType.BANK,
        cardNumber = realCard,
        iban = realIban
      )
    // Fields encrypted under the current default work factor, metadata present but
    // without the iterations field — restore must fall back to the constant.
    val (rootJson, parsed) =
      encryptedBackupFixture(passphrase, BackupCipher.PBKDF2_ITERATIONS, account)
    rootJson.getJSONObject("sensitiveFieldsEncryption").remove("iterations")

    val decrypted =
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
      }

    assertEquals(
      "cardNumber must be recovered via the default iteration fallback",
      realCard,
      decrypted.accounts[0].cardNumber
    )
    assertEquals(
      "iban must be recovered via the default iteration fallback",
      realIban,
      decrypted.accounts[0].iban
    )
  }

  @Test
  fun getEncryptionIterationsFallsBackToDefaultWhenMetadataAbsent() {
    assertEquals(
      "No encryption metadata must fall back to the current work factor",
      BackupCipher.PBKDF2_ITERATIONS,
      ManageBackupUseCase.getEncryptionIterations(JSONObject())
    )
  }

  @Test
  fun getEncryptionSaltReturnsNullWhenMetadataPresentButSaltKeyAbsent() {
    // ENCRYPTION_KEY object is present but has no "salt" member — org.json's
    // optString(name) falls back to "" for an absent key, so without the empty check
    // this would return "" and the `?:` guard at the decrypt call site would never fire.
    val rootJson =
      JSONObject().apply {
        put(
          "sensitiveFieldsEncryption",
          JSONObject().apply { put("iterations", BackupCipher.PBKDF2_ITERATIONS) }
        )
      }

    assertNull(
      "Encryption object present but salt key absent must yield null, not empty string",
      ManageBackupUseCase.getEncryptionSalt(rootJson)
    )
  }

  @Test
  fun decryptRejectsEncryptedBackupWithoutSaltKey() {
    val passphrase = "my-secret-passphrase"
    val account =
      AccountEntity(
        id = 1,
        name = "حساب اصلی",
        type = AccountType.BANK,
        cardNumber = "6219861012345678",
        iban = "IR12345"
      )
    // A backup declaring encryption metadata but no salt can never derive a key —
    // the salt guard must fire with the metadata error instead of failing deep in
    // the PBKDF2 provider (empty-salt PBEKeySpec throws InvalidKeySpecException).
    val (rootJson, parsed) =
      encryptedBackupFixture(passphrase, BackupCipher.PBKDF2_ITERATIONS, account)
    rootJson.getJSONObject("sensitiveFieldsEncryption").remove("salt")

    val e =
      org.junit.Assert.assertThrows(
        "Encrypted backup without a salt must be rejected by the metadata guard",
        IllegalArgumentException::class.java
      ) {
        runBlocking {
          useCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase)
        }
      }
    assertTrue(
      "Error must identify the missing metadata, got: ${e.message}",
      e.message!!.contains("does not contain encryption metadata")
    )
  }
}
