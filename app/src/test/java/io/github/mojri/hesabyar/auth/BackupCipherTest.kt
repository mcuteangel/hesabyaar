package io.github.mojri.hesabyar.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupCipherTest {
  private val salt = BackupCipher.generateSalt()
  private val passphrase = "test-passphrase-123"
  private val key = BackupCipher.deriveKey(passphrase, salt)

  @Test
  fun encryptThenDecryptRecoversOriginalValue() {
    val plaintext = "621986101234567890123456"
    val encrypted = BackupCipher.encrypt(plaintext, key)
    val decrypted = BackupCipher.decrypt(encrypted, key)
    assertEquals("Decrypted value must match original", plaintext, decrypted)
  }

  @Test(expected = AEADBadTagException::class)
  fun wrongPassphraseThrowsAEADBadTagException() {
    val plaintext = "621986101234567890123456"
    val encrypted = BackupCipher.encrypt(plaintext, key)
    val wrongKey = BackupCipher.deriveKey("wrong-passphrase", salt)
    BackupCipher.decrypt(encrypted, wrongKey)
  }

  @Test
  fun differentSaltsProduceDifferentCiphertext() {
    val plaintext = "IR12 3456 7890 1234 5678 9012 34"
    val salt2 = BackupCipher.generateSalt()
    val key2 = BackupCipher.deriveKey(passphrase, salt2)
    val encrypted1 = BackupCipher.encrypt(plaintext, key)
    val encrypted2 = BackupCipher.encrypt(plaintext, key2)
    assertNotEquals("Different salts must produce different ciphertext", encrypted1, encrypted2)
    // Both must still decrypt correctly
    assertEquals(plaintext, BackupCipher.decrypt(encrypted1, key))
    assertEquals(plaintext, BackupCipher.decrypt(encrypted2, key2))
  }

  @Test
  fun ivIsRandomPerEncryption() {
    val plaintext = "4567890123456789"
    val encrypted1 = BackupCipher.encrypt(plaintext, key)
    val encrypted2 = BackupCipher.encrypt(plaintext, key)
    assertNotEquals("Same plaintext encrypted twice must differ (different IVs)", encrypted1, encrypted2)
    // Both must decrypt to the same plaintext
    assertEquals(plaintext, BackupCipher.decrypt(encrypted1, key))
    assertEquals(plaintext, BackupCipher.decrypt(encrypted2, key))
  }

  @Test(expected = IllegalArgumentException::class)
  fun decryptingTruncatedCiphertextThrowsIllegalArgumentException() {
    // Base64 of just 5 bytes — shorter than IV_LENGTH_BYTES + 1
    val tooShort =
      java.util.Base64
        .getEncoder()
        .encodeToString(byteArrayOf(1, 2, 3, 4, 5))
    BackupCipher.decrypt(tooShort, key)
  }

  @Test(expected = AEADBadTagException::class)
  fun decryptingTamperedButWellFormedCiphertextThrowsAEADBadTagException() {
    val plaintext = "98765432109876543210"
    val encrypted = BackupCipher.encrypt(plaintext, key)
    // Tamper: flip a character in the base64 to change a byte in the ciphertext
    // The encrypted string is long enough that changing one char alters a byte
    // in the ciphertext portion (after the IV), invalidating the GCM tag.
    val tampered =
      encrypted.substring(0, encrypted.length - 1) +
        if (encrypted.last() == 'A') "B" else "A"
    BackupCipher.decrypt(tampered, key)
  }

  @Test
  fun encryptOrNullReturnsNullForNullInput() {
    val result = BackupCipher.encryptOrNull(null, key)
    assertTrue("Null input must return JSONObject.NULL", result === org.json.JSONObject.NULL)
  }

  @Test
  fun decryptOrNullReturnsNullForNullInput() {
    val result = BackupCipher.decryptOrNull(org.json.JSONObject.NULL, key)
    assertEquals("JSONObject.NULL input must return null", null, result)
  }

  @Test
  fun roundTripWithNullFieldsInAccountContext() {
    val realCard = "6219861012345678"
    val realIban = "IR123456789012345678901234"

    // Simulate account export with some null fields
    val cardEncrypted = BackupCipher.encryptOrNull(realCard, key)
    val accountNumEncrypted = BackupCipher.encryptOrNull(null, key)
    val ibanEncrypted = BackupCipher.encryptOrNull(realIban, key)

    // Encrypted values are strings, null stays JSONObject.NULL
    assertTrue("Card should be encrypted string", cardEncrypted is String)
    assertTrue("Account number should be JSONObject.NULL", accountNumEncrypted === org.json.JSONObject.NULL)
    assertTrue("IBAN should be encrypted string", ibanEncrypted is String)

    // Decrypt back
    assertEquals("Card must recover", realCard, BackupCipher.decryptOrNull(cardEncrypted, key))
    assertEquals("Null account number must stay null", null, BackupCipher.decryptOrNull(accountNumEncrypted, key))
    assertEquals("IBAN must recover", realIban, BackupCipher.decryptOrNull(ibanEncrypted, key))
  }
}
