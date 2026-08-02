package io.github.mojri.hesabyar.auth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for sensitive banking fields in backup exports.
 *
 * Uses PBKDF2WithHmacSHA256 (600k iterations) for key derivation — same parameters
 * as [PinStorage] — and AES-GCM for authenticated encryption.
 *
 * Encrypted value format: `base64(12-byte-IV || ciphertext || 16-byte-GCM-tag)`
 *
 * The IV is prepended to the ciphertext (standard pattern — IV is not secret, just unique).
 * Each [encrypt] call generates a fresh random IV, so the same plaintext encrypted twice
 * produces different ciphertext.
 */
object BackupCipher {
  private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val PBKDF2_ITERATIONS = 600_000
  private const val PBKDF2_KEY_LENGTH_BITS = 256
  private const val SALT_LENGTH_BYTES = 16
  private const val IV_LENGTH_BYTES = 12
  private const val GCM_TAG_LENGTH_BITS = 128
  private const val AES_ALGORITHM = "AES/GCM/NoPadding"
  private const val HEX_RADIX = 16

  /**
   * Derives a 256-bit AES key from [passphrase] and [saltHex] using PBKDF2.
   *
   * @param passphrase the user-supplied passphrase (any length)
   * @param saltHex hex-encoded salt string (32 hex chars = 16 bytes), as produced by [generateSalt]
   * @return a [SecretKey] suitable for AES-GCM encryption
   */
  fun deriveKey(
    passphrase: String,
    saltHex: String
  ): SecretKey {
    val saltBytes = hexToBytes(saltHex)
    val spec =
      PBEKeySpec(
        passphrase.toCharArray(),
        saltBytes,
        PBKDF2_ITERATIONS,
        PBKDF2_KEY_LENGTH_BITS
      )
    val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
  }

  /**
   * Generates a cryptographically secure random salt as a hex string.
   * @return 32-character lowercase hex string (16 bytes)
   */
  fun generateSalt(): String {
    val bytes = ByteArray(SALT_LENGTH_BYTES)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  /**
   * Encrypts [plaintext] using AES-GCM with a random IV.
   *
   * @param plaintext the string to encrypt
   * @param key the AES key (derived via [deriveKey])
   * @return base64-encoded string: IV (12 bytes) + ciphertext + GCM tag (16 bytes)
   */
  fun encrypt(
    plaintext: String,
    key: SecretKey
  ): String {
    val cipher = Cipher.getInstance(AES_ALGORITHM)
    val iv = ByteArray(IV_LENGTH_BYTES)
    SecureRandom().nextBytes(iv)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
    cipher.init(Cipher.ENCRYPT_MODE, key, spec)
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val combined = iv + ciphertext
    return Base64.getEncoder().encodeToString(combined)
  }

  /**
   * Decrypts a base64-encoded encrypted string produced by [encrypt].
   *
   * @param encryptedBase64 base64-encoded IV + ciphertext + GCM tag
   * @param key the AES key (derived via [deriveKey])
   * @return the original plaintext string
   * @throws javax.crypto.AEADBadTagException if the passphrase is wrong or ciphertext is tampered
   * @throws IllegalArgumentException if the ciphertext is too short or malformed
   */
  fun decrypt(
    encryptedBase64: String,
    key: SecretKey
  ): String {
    val combined = Base64.getDecoder().decode(encryptedBase64)
    // Minimum: IV (12) + 1 byte ciphertext + GCM tag (16) = 29 bytes
    val minLen = IV_LENGTH_BYTES + 1
    require(combined.size >= minLen) {
      "Encrypted data too short: ${combined.size} bytes (minimum $minLen)"
    }
    val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
    val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
    val cipher = Cipher.getInstance(AES_ALGORITHM)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
    cipher.init(Cipher.DECRYPT_MODE, key, spec)
    val plaintext = cipher.doFinal(ciphertext)
    return String(plaintext, Charsets.UTF_8)
  }

  /**
   * Encrypts [value] if non-null, returns [org.json.JSONObject.NULL] otherwise.
   * Convenience wrapper for backup account field serialization.
   */
  fun encryptOrNull(
    value: String?,
    key: SecretKey
  ): Any = if (value != null) encrypt(value, key) else org.json.JSONObject.NULL

  /**
   * Decrypts [value] if it is a non-null string, returns null otherwise.
   * Convenience wrapper for backup account field deserialization.
   *
   * @throws javax.crypto.AEADBadTagException if the passphrase is wrong or ciphertext is tampered
   * @throws IllegalArgumentException if the ciphertext is malformed
   */
  fun decryptOrNull(
    value: Any?,
    key: SecretKey
  ): String? = if (value is String && value.isNotEmpty()) decrypt(value, key) else null

  private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    val bytes = ByteArray(hex.length / 2)
    for (i in bytes.indices) {
      bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(HEX_RADIX).toByte()
    }
    return bytes
  }
}
