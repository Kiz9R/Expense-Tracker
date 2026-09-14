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
    fun activeDatabaseName(): String = context.getSharedPreferences("device_keys",Context.MODE_PRIVATE)
        .getString("active_database","ledger.db")!!.also(::validateName)
    private fun validateName(name: String) {
        require(name=="ledger.db" || Regex("ledger-recovery-[a-f0-9-]{36}\\.db").matches(name))
    }
    // Inspect commit() success; the Unit-returning KTX helper cannot report a failed durable switch.
    @android.annotation.SuppressLint("UseKtx")
    @Synchronized fun activateDatabase(name: String, expectedPrevious: String) {
        validateName(name)
        check(activeDatabaseName()==expectedPrevious) { "Recovery state changed. Reopen the app." }
        check(context.getDatabasePath(name).exists())
        check(context.getSharedPreferences("device_keys",Context.MODE_PRIVATE).edit().putString("active_database",name).commit()) {
            "Could not finish recovery. Original files were retained."
        }
    }
    @Synchronized fun databasePassword(name: String = activeDatabaseName()): ByteArray {
        validateName(name)
        val prefs = context.getSharedPreferences("device_keys", Context.MODE_PRIVATE)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val suffix=if(name=="ledger.db") "" else "."+name
        val alias = "expense_tracker.database.v1"+suffix
        val preference="wrapped_database_key"+suffix
        val wrapped = prefs.getString(preference, null)
        // Missing/inaccessible key material must never silently destroy the user's ledger.
        check(wrapped != null || !context.getDatabasePath(name).exists()) { "Database key missing. Restore an encrypted backup." }
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
        check(prefs.edit().putString(preference,
            Base64.encodeToString(cipher.iv + cipher.doFinal(password), Base64.NO_WRAP)).commit()) { "Could not save encryption key." }
        return password
    }
}
