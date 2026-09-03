package com.xiaogong.ntfy.im.util

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    /**
     * Encrypts plaintext using AES-256-GCM with the given password.
     * Returns Base64-encoded string containing: [salt][iv][ciphertext+tag]
     */
    fun encrypt(plaintext: String, password: String): String {
        // Generate random salt and IV
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().apply {
            nextBytes(salt)
            nextBytes(iv)
        }

        // Derive key from password using PBKDF2
        val key = deriveKey(password, salt)

        // Encrypt using AES-GCM
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)

        // Combine salt + iv + ciphertext and encode to Base64
        val combined = ByteBuffer.allocate(SALT_LENGTH + GCM_IV_LENGTH + ciphertext.size)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64-encoded encrypted data using AES-256-GCM with the given password.
     * Expected format: [salt][iv][ciphertext+tag]
     * Returns decrypted plaintext string.
     * @throws Exception if decryption fails (wrong password, corrupted data, etc.)
     */
    fun decrypt(encryptedBase64: String, password: String): String {
        // Decode from Base64
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        // Extract salt, IV, and ciphertext
        val buffer = ByteBuffer.wrap(combined)
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(salt)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        // Derive key from password using PBKDF2
        val key = deriveKey(password, salt)

        // Decrypt using AES-GCM
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Derives an AES key from password and salt using PBKDF2.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }
}
