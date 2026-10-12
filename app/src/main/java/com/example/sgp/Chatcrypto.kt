package com.example.sgp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val ENC_VERSION_AES_GCM_V1 = 1

sealed class DecryptResult {
    data class Success(val text: String) : DecryptResult()
    /** No conversation key available (offline first run, fetch failed, not a participant). */
    object MissingKey : DecryptResult()
    /** GCM authentication failed: wrong key, edited ciphertext, or moved/forged message. */
    object Tampered : DecryptResult()
    /** Not valid base64 / wrong lengths. */
    object Corrupt : DecryptResult()
    /** encVersion we do not understand (newer app version wrote it). */
    object Unsupported : DecryptResult()
}

/** Pure-JVM AES-256-GCM. No Android classes, so it is unit-testable. Byte format matches functions/src/crypto.ts. */
object MessageCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private val random = SecureRandom()

    fun messageAad(conversationId: String, messageId: String, senderId: String): ByteArray =
        "msg|$conversationId|$messageId|$senderId".toByteArray(Charsets.UTF_8)

    fun previewAad(conversationId: String, lastTimestamp: Long): ByteArray =
        "preview|$conversationId|$lastTimestamp".toByteArray(Charsets.UTF_8)

    /** Returns (ciphertext||tag, iv), both base64. Production encryption happens server-side; this is used by tests. */
    fun encrypt(key: ByteArray, plaintext: String, aad: ByteArray): Pair<String, String> {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return enc.encodeToString(ct) to enc.encodeToString(iv)
    }

    fun decrypt(key: ByteArray?, encVersion: Int, ciphertextB64: String, ivB64: String, aad: ByteArray): DecryptResult {
        if (encVersion != ENC_VERSION_AES_GCM_V1) return DecryptResult.Unsupported
        if (key == null || key.size != KEY_BYTES) return DecryptResult.MissingKey
        val dec = Base64.getDecoder()
        val iv: ByteArray
        val ct: ByteArray
        try {
            iv = dec.decode(ivB64)
            ct = dec.decode(ciphertextB64)
        } catch (e: IllegalArgumentException) {
            return DecryptResult.Corrupt
        }
        if (iv.size != IV_BYTES || ct.size < TAG_BITS / 8) return DecryptResult.Corrupt
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad)
            DecryptResult.Success(String(cipher.doFinal(ct), Charsets.UTF_8))
        } catch (e: AEADBadTagException) {
            DecryptResult.Tampered
        } catch (e: GeneralSecurityException) {
            DecryptResult.Corrupt
        }
    }
}

/** Wraps small secrets with a non-exportable AES key that lives in the Android Keystore. */
class KeystoreKeyWrapper(private val alias: String = "skillswap_chat_wrap_v1") {

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** "ivB64:ctB64". The Keystore generates the IV, so it is unique per call. */
    fun wrap(raw: ByteArray, aad: ByteArray): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(raw)
        val enc = Base64.getEncoder()
        enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(ct)
    } catch (e: Exception) {
        Log.w("KeystoreKeyWrapper", "wrap failed: ${e.javaClass.simpleName}")
        null
    }

    fun unwrap(blob: String, aad: ByteArray): ByteArray? = try {
        val parts = blob.split(":")
        require(parts.size == 2)
        val dec = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, dec.decode(parts[0])))
        cipher.updateAAD(aad)
        cipher.doFinal(dec.decode(parts[1]))
    } catch (e: Exception) {
        // Includes keystore wipes after restore/reset. Caller re-fetches the key.
        Log.w("KeystoreKeyWrapper", "unwrap failed: ${e.javaClass.simpleName}")
        null
    }
}

/**
 * Caches conversation keys on disk ONLY in Keystore-wrapped form. The raw key is held in memory
 * while the process lives. Add the prefs file to your backup exclusion rules (see SETUP.md).
 */
class ConversationKeyStore(
    context: Context,
    private val wrapper: KeystoreKeyWrapper = KeystoreKeyWrapper()
) {
    private val prefs = context.applicationContext.getSharedPreferences("skillswap_chat_keys", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, ByteArray>()

    private fun id(conversationId: String, version: Int) = "k|$conversationId|$version"

    fun get(conversationId: String, version: Int): ByteArray? {
        val k = id(conversationId, version)
        memory[k]?.let { return it }
        val blob = prefs.getString(k, null) ?: return null
        val raw = wrapper.unwrap(blob, k.toByteArray())
        if (raw == null || raw.size != 32) {
            prefs.edit().remove(k).apply()
            return null
        }
        memory[k] = raw
        return raw
    }

    fun put(conversationId: String, version: Int, raw: ByteArray) {
        val k = id(conversationId, version)
        memory[k] = raw
        wrapper.wrap(raw, k.toByteArray())?.let { prefs.edit().putString(k, it).apply() }
    }

    /** Call on logout. */
    fun clearAll() {
        memory.values.forEach { it.fill(0) }
        memory.clear()
        prefs.edit().clear().apply()
    }
}

class ConversationKeyProvider(context: Context) {
    private val store = ConversationKeyStore(context)

    /** Synchronous, cache-only. */
    fun peek(conversationId: String, keyVersion: Int = 1): ByteArray? = store.get(conversationId, keyVersion)

    /** Cache first, then ask the server (which verifies the caller is a participant). */
    suspend fun getKey(conversationId: String, keyVersion: Int = 1): ByteArray? {
        store.get(conversationId, keyVersion)?.let { return it }
        return try {
            val res = ChatGateway.call("key", JSONObject().put("conversationId", conversationId))
            val j = res.json
            if (!res.ok || j == null) return null
            val raw = Base64.getDecoder().decode(j.optString("key"))
            val version = j.optInt("keyVersion", 1)
            if (raw.size != 32) return null
            store.put(conversationId, version, raw)
            raw
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log the class only: never keys, never message content.
            Log.w("ConversationKeys", "key fetch failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun clearAll() = store.clearAll()
}

object ChatKeys {
    @Volatile private var instance: ConversationKeyProvider? = null
    fun provider(context: Context): ConversationKeyProvider =
        instance ?: synchronized(this) {
            instance ?: ConversationKeyProvider(context.applicationContext).also { instance = it }
        }
}