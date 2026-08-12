package io.github.mojri.hesabyar.domain.usecase

import android.content.Context
import io.github.mojri.hesabyar.auth.BackupCipher
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.RestoreMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.GeneralSecurityException

/**
 * Facade over the backup domain. Parsing, validation, export serialization and
 * summary strings live in [BackupJsonParser], [BackupJsonValidator] and
 * [BackupPayloadExporter]; this class keeps the public API stable for callers
 * (BackupViewModel, tests) while delegating to those collaborators.
 */
class ManageBackupUseCase(
  private val repository: HesabyarRepositoryInterface,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val application: Context? = null
) {
  companion object {
    private const val TAG = "ManageBackupUseCase"

    /**
     * Returns true if the backup JSON indicates that sensitive banking fields
     * (cardNumber, accountNumber, iban) are encrypted with a passphrase.
     */
    fun isEncryptedBackup(rootJson: JSONObject): Boolean = rootJson.has(ENCRYPTION_KEY)

    /**
     * Extracts the PBKDF2 salt from the encryption metadata in the backup JSON.
     * @return the hex-encoded salt string, or null if no encryption metadata is
     *   present or the salt field is absent/empty — org.json's `optString` falls
     *   back to "" for an absent key, so an explicit empty check is needed for
     *   the `?:` guard at the decrypt call site to fire
     */
    fun getEncryptionSalt(rootJson: JSONObject): String? =
      rootJson
        .optJSONObject(ENCRYPTION_KEY)
        ?.optString(SALT_KEY)
        ?.takeIf { it.isNotEmpty() }

    /**
     * Extracts the PBKDF2 iteration count from the encryption metadata in the backup JSON.
     *
     * @return the declared iteration count, or [BackupCipher.PBKDF2_ITERATIONS] when the
     *   field is absent (defensive fallback — every encrypted backup written by this app
     *   includes it, but a foreign or hand-edited backup may not)
     * @throws IllegalArgumentException if the declared count is below [MIN_ITERATIONS_FLOOR]
     *   or above [MAX_ITERATIONS_CEILING], so a tampered backup cannot force a weak
     *   key derivation on the low end or a hang (DoS/ANR) on the high end
     */
    fun getEncryptionIterations(rootJson: JSONObject): Int {
      val iterations =
        rootJson
          .optJSONObject(ENCRYPTION_KEY)
          ?.optInt(ITERATIONS_KEY, BackupCipher.PBKDF2_ITERATIONS)
          ?: BackupCipher.PBKDF2_ITERATIONS
      require(iterations >= MIN_ITERATIONS_FLOOR) {
        "Backup declares PBKDF2 iteration count $iterations, below the minimum allowed floor $MIN_ITERATIONS_FLOOR"
      }
      require(iterations <= MAX_ITERATIONS_CEILING) {
        "Backup declares PBKDF2 iteration count $iterations, above the maximum allowed ceiling $MAX_ITERATIONS_CEILING"
      }
      return iterations
    }
  }

  private val parser = BackupJsonParser(dispatcher)
  private val validator = BackupJsonValidator(dispatcher, application)
  private val exporter = BackupPayloadExporter(repository)

  /**
   * Decrypts the sensitive banking fields (cardNumber, accountNumber, iban) in all
   * accounts of a parsed [BackupPayload], using the raw JSON to re-read the encrypted
   * values and [passphrase] to derive the decryption key.
   *
   * This method uses the same parsing path (Rust or Kotlin) that [parseBackupJson]
   * originally used — the parsed [backup] was produced by [parseBackupJson] and the
   * raw JSON is only re-read to obtain the encrypted field values, rather than
   * introducing a second independent parser.
   *
   * Raw JSON accounts are matched to parsed accounts by their stable `id` field
   * (present in both the serialized JSON and [io.github.mojri.hesabyar.data.AccountEntity]),
   * NOT by positional index. Index-based matching could attach ciphertext to the
   * wrong account if a raw entry is missing or the array is reordered; id matching
   * makes that impossible. Any malformed raw entry, duplicate id, or parsed account
   * with no raw counterpart fails loudly instead of returning misaligned financial data.
   *
   * @throws GeneralSecurityException if the passphrase is wrong or the ciphertext is tampered
   * @throws IllegalArgumentException if the encrypted data is malformed
   * @throws IllegalStateException if the raw accounts array cannot be matched 1:1 with
   *   the parsed accounts by id (malformed entry, duplicate id, or missing counterpart)
   */
  suspend fun decryptBackupWithPassphrase(
    backup: BackupPayload,
    rootJson: JSONObject,
    passphrase: String,
    dispatcher: CoroutineDispatcher = this.dispatcher
  ): BackupPayload =
    withContext(dispatcher) {
      val salt =
        getEncryptionSalt(rootJson)
          ?: throw IllegalArgumentException("Backup does not contain encryption metadata")
      val key = BackupCipher.deriveKey(passphrase, salt, getEncryptionIterations(rootJson))
      val accountsArray = rootJson.optJSONArray("accounts") ?: return@withContext backup

      // Index raw JSON accounts by stable account id. A raw entry that is not an
      // object, lacks an id, or duplicates another id would make id-based matching
      // ambiguous — reject instead of guessing.
      val encryptedById = HashMap<Long, JSONObject>(accountsArray.length() * 2)
      for (i in 0 until accountsArray.length()) {
        val o =
          accountsArray.optJSONObject(i)
            ?: throw IllegalStateException(
              "Account entry #$i in encrypted backup is not a JSON object"
            )
        if (!o.has("id")) {
          throw IllegalStateException("Account entry #$i in encrypted backup has no id field")
        }
        val id = o.optLong("id", -1L)
        if (id <= 0L) {
          throw IllegalStateException(
            "Account entry #$i in encrypted backup has invalid id: $id"
          )
        }
        if (encryptedById.containsKey(id)) {
          throw IllegalStateException("Duplicate account id $id in encrypted backup")
        }
        encryptedById[id] = o
      }

      // Each parsed account must have exactly one raw counterpart to decrypt.
      // A missing counterpart means the parsed payload and raw JSON diverged —
      // failing beats silently preserving the wrong account's ciphertext.
      val decryptedAccounts =
        backup.accounts.map { account ->
          val raw =
            encryptedById[account.id]
              ?: throw IllegalStateException(
                "Parsed account ${account.id} has no counterpart in encrypted backup"
              )
          account.copy(
            cardNumber =
              BackupCipher.decryptOrNull(
                raw.opt("cardNumber"),
                key,
                BackupCipher.accountFieldAad(account.id, "cardNumber")
              ),
            accountNumber =
              BackupCipher.decryptOrNull(
                raw.opt("accountNumber"),
                key,
                BackupCipher.accountFieldAad(account.id, "accountNumber")
              ),
            iban =
              BackupCipher.decryptOrNull(
                raw.opt("iban"),
                key,
                BackupCipher.accountFieldAad(account.id, "iban")
              )
          )
        }
      backup.copy(accounts = decryptedAccounts)
    }

  suspend fun parseBackupJson(jsonString: String): BackupPayload? = parser.parseBackupJson(jsonString)

  suspend fun validateBackup(backup: BackupPayload): BackupValidationResult = validator.validateBackup(backup)

  suspend fun executeRestore(
    backup: BackupPayload,
    mode: RestoreMode
  ) {
    when (mode) {
      RestoreMode.REPLACE -> repository.replaceAllFromBackup(backup)
      RestoreMode.MERGE -> repository.mergeFromBackup(backup)
    }
  }

  suspend fun exportBackupJson(
    isDarkMode: Boolean = true,
    passphrase: String? = null
  ): JSONObject = exporter.exportBackupJson(isDarkMode, passphrase)
}
