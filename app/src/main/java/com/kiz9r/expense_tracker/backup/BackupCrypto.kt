package com.kiz9r.expense_tracker.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable authenticated envelope. No device-bound key is needed to restore this file. */
object BackupCrypto {
    private val magic = "ETBACK01".toByteArray(Charsets.US_ASCII)
    private const val iterations = 600_000
    private const val headerSize = 8+4+16+12
    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password,salt,iterations,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
        finally { spec.clearPassword() }
    }
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use a backup password of at least 12 characters." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(headerSize).put(magic).putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in (headerSize+16)..(64*1024*1024)) { "Invalid backup size." }
        val buffer = ByteBuffer.wrap(bytes)
        val prefix = ByteArray(8).also { buffer.get(it) }
        require(prefix.contentEquals(magic) && buffer.int==iterations) { "Unsupported backup format." }
        val salt = ByteArray(16).also { buffer.get(it) }; val nonce = ByteArray(12).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce))
        cipher.updateAAD(bytes.copyOfRange(0,headerSize))
        return cipher.doFinal(bytes.copyOfRange(headerSize,bytes.size))
    }
}
