package com.avainfinity.android

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted SharedPreferences facade for AVA's local operational stores.
 * Values are encrypted with an AES-GCM key held by Android Keystore.
 * Legacy plaintext primitive values are migrated on first read.
 */
class AvaSecurePrefs(context: Context, fileName: String) {
    private val backing: SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val alias = "ava_infinity_" + fileName.replace(Regex("[^A-Za-z0-9_]"), "_") + "_aes_v1"
    private val lock = Any()

    val allKeys: Set<String>
        get() = backing.all.keys.toSet()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return "v1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value.removePrefix("v1:"), Base64.NO_WRAP)
        require(combined.size > 12) { "Encrypted AVA value is truncated." }
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun getString(key: String, defaultValue: String? = null): String? = synchronized(lock) {
        val raw = backing.all[key] ?: return@synchronized defaultValue
        val value = when (raw) {
            is String -> raw
            is Boolean, is Int, is Long, is Float -> raw.toString()
            else -> throw IllegalStateException("Unsupported legacy AVA preference type.")
        }
        if (value.startsWith("v1:")) {
            try {
                decrypt(value)
            } catch (error: Exception) {
                throw IllegalStateException("Encrypted AVA data could not be opened. Data was left unchanged.", error)
            }
        } else {
            // One-time migration from legacy plaintext SharedPreferences.
            backing.edit().putString(key, encrypt(value)).commit()
            value
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        getString(key, null)?.equals("true", ignoreCase = true) ?: defaultValue

    fun getInt(key: String, defaultValue: Int = 0): Int =
        getString(key, null)?.toIntOrNull() ?: defaultValue

    fun edit(): Editor = Editor(this, backing.edit())

    class Editor internal constructor(
        private val owner: AvaSecurePrefs,
        private val editor: SharedPreferences.Editor
    ) {
        fun putString(key: String, value: String?): Editor {
            if (value == null) editor.remove(key) else editor.putString(key, owner.encrypt(value))
            return this
        }

        fun putBoolean(key: String, value: Boolean): Editor =
            putString(key, value.toString())

        fun putInt(key: String, value: Int): Editor =
            putString(key, value.toString())

        fun remove(key: String): Editor {
            editor.remove(key)
            return this
        }

        fun apply() {
            editor.apply()
        }

        fun commit(): Boolean = editor.commit()
    }
}
