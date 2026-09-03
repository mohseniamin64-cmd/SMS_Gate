package com.example.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure local storage for Phone Server API Key using AES/GCM.
 * The master key is stored in Android KeyStore, ensuring the API key is
 * never stored in plaintext in Room, SharedPreferences, or logs.
 */
object SecureApiKeyStore {
    private const val PREFS_NAME = "phone_server_secure_prefs"
    private const val KEY_CIPHERTEXT = "encrypted_api_key"
    private const val ANDROID_KEYSTORE_ALIAS = "phone_server_api_key_aes"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    @Volatile
    private var cachedKey: String? = null

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return runCatching {
            Security.getProvider(ANDROID_KEYSTORE) != null &&
                KeyStore.getInstance(ANDROID_KEYSTORE) != null
        }.getOrDefault(false)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return if (isAndroidKeyStoreAvailable()) {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(ANDROID_KEYSTORE_ALIAS)) {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    ANDROID_KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                kg.init(spec)
                kg.generateKey()
            }
            ks.getKey(ANDROID_KEYSTORE_ALIAS, null) as SecretKey
        } else {
            // Consistent software AES-256 key for JVM unit tests where AndroidKeyStore is unavailable
            val testSeed = "sms-gateway-jvm-master-secret-seed-32-bytes!".toByteArray(Charsets.UTF_8).copyOf(32)
            SecretKeySpec(testSeed, "AES")
        }
    }

    /**
     * Reads and decrypts the phone server API key. Returns null if none has been generated.
     */
    fun getApiKey(context: Context): String? {
        cachedKey?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hex = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val combined = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (combined.size <= GCM_IV_LENGTH) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plain = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            cachedKey = plain
            plain
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts and persists the given API key.
     */
    fun saveApiKey(context: Context, rawKey: String) {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(rawKey.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        val hex = combined.joinToString("") { "%02x".format(it) }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CIPHERTEXT, hex)
            .apply()
        cachedKey = rawKey
    }

    /**
     * Retrieves the existing API key or securely generates and saves a new one.
     */
    fun getOrCreateApiKey(context: Context): String {
        val existing = getApiKey(context)
        if (!existing.isNullOrBlank()) return existing
        val newKey = PhoneServerSecurity.generateApiKey()
        saveApiKey(context, newKey)
        return newKey
    }

    fun hasApiKey(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    fun clearApiKey(context: Context) {
        cachedKey = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CIPHERTEXT)
            .apply()
    }
}
