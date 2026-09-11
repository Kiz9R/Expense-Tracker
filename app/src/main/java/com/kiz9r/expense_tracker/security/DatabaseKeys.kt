package com.kiz9r.expense_tracker.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseKeys @Inject constructor(@ApplicationContext private val context: Context) {
    @Synchronized fun databasePassword(): ByteArray {
        val prefs = context.getSharedPreferences("device_keys", Context.MODE_PRIVATE)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "expense_tracker.database.v1"
        val wrapped = prefs.getString("wrapped_database_key", null)
        // Missing/inaccessible key material must never silently destroy the user's ledger.
        check(wrapped != null || !context.getDatabasePath("ledger.db").exists()) { "Database key missing. Restore an encrypted backup on a fresh installation." }
        check(wrapped == null || store.containsAlias(alias)) { "Device key unavailable. Restore your encrypted backup." }
        val key = if (store.containsAlias(alias)) store.getKey(alias, null) as SecretKey else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (wrapped != null) {
            val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        }
        val password = ByteArray(32).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(prefs.edit().putString("wrapped_database_key",
            Base64.encodeToString(cipher.iv + cipher.doFinal(password), Base64.NO_WRAP)).commit()) { "Could not save encryption key." }
        return password
    }
}
