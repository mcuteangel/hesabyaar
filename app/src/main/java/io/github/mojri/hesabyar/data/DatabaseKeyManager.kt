package io.github.mojri.hesabyar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object DatabaseKeyManager {
    private const val PREFS_FILE = "hesabyar_db_key_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreateKey(context: Context): ByteArray {
        val prefs = getEncryptedPrefs(context)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.DEFAULT)
        }
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        prefs.edit().putString(KEY_DB_PASSPHRASE, android.util.Base64.encodeToString(key, android.util.Base64.DEFAULT)).apply()
        return key
    }
}
