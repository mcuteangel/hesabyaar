package io.github.mojri.hesabyar.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object PinStorage {
    private const val PREFS_NAME = "hesabyar_auth_prefs"
    private const val PIN_HASH_KEY = "pin_hash"
    private const val PIN_SALT_KEY = "pin_salt"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(context: Context): Boolean = getPrefs(context).contains(PIN_HASH_KEY)

    fun setPin(context: Context, pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        getPrefs(context).edit()
            .putString(PIN_HASH_KEY, hash)
            .putString(PIN_SALT_KEY, salt)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(PIN_HASH_KEY, null) ?: return false
        val salt = prefs.getString(PIN_SALT_KEY, null) ?: return false

        // Try new PBKDF2 format first
        if (hashPin(pin, salt) == storedHash) {
            return true
        }

        // Fall back to legacy SHA-256 format and migrate if successful
        val legacyHash = MessageDigest.getInstance("SHA-256")
            .digest((pin + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }

        if (legacyHash == storedHash) {
            // Migrate to new PBKDF2 format
            setPin(context, pin)
            return true
        }

        return false
    }

    fun clearPin(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), 10000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }
}
