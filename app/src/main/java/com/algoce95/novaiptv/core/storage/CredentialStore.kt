package com.algoce95.novaiptv.core.storage

import android.security.keystore.KeyProperties
import android.util.Base64
import com.algoce95.novaiptv.core.utils.UrlNormalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Paridad con `CredentialStore` de Flutter: credenciales en el almacén
 * seguro del sistema (Android Keystore, AES/GCM) con copia en memoria.
 *
 * La contraseña anterior en claro NO se migra nunca: el usuario la
 * reintroduce una sola vez. Servidor y usuario sí se precargan.
 */
class CredentialStore(private val prefs: PrefsStore) {

    companion object {
        private const val KEY_ALIAS = "nova_credentials"
        private const val K_USERNAME = "secure_username"
        private const val K_PASSWORD = "secure_password"
        private const val K_SERVER = "secure_server"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }

    var username: String = ""
        private set
    var password: String = ""
        private set
    var server: String = ""
        private set

    private val writeMutex = Mutex()

    suspend fun init() {
        try {
            username = prefs.getString(K_USERNAME)?.let(::decrypt).orEmpty()
            password = prefs.getString(K_PASSWORD)?.let(::decrypt).orEmpty()
            server = prefs.getString(K_SERVER)?.let(::decrypt).orEmpty()
        } catch (_: Exception) {
            // Sin almacenamiento seguro disponible: se arranca sin sesión.
            username = ""
            password = ""
            server = ""
        }
        migrateLegacyServerUsername()
    }

    suspend fun save(username: String, password: String, server: String) {
        writeMutex.withLock {
            val normalizedServer = UrlNormalizer.normalizeUrl(server)
            this.username = username
            this.password = password
            this.server = normalizedServer
            prefs.setString(K_USERNAME, encrypt(username))
            prefs.setString(K_PASSWORD, encrypt(password))
            prefs.setString(K_SERVER, encrypt(normalizedServer))
        }
    }

    suspend fun clear() {
        writeMutex.withLock {
            username = ""
            password = ""
            server = ""
            prefs.remove(K_USERNAME)
            prefs.remove(K_PASSWORD)
            prefs.remove(K_SERVER)
        }
    }

    /** Precarga servidor y usuario desde la app Flutter (nunca la contraseña). */
    private suspend fun migrateLegacyServerUsername() {
        if (username.isNotEmpty() || server.isNotEmpty()) return
        val legacy = try {
            prefs.readFlutterLegacyPrefs()
        } catch (_: Exception) {
            null
        } ?: return
        val legacyServer = (legacy[PrefsStore.Keys.LEGACY_SERVER] as? String)
            ?: (legacy[PrefsStore.Keys.LEGACY_SERVER_URL] as? String)
            ?: ""
        val legacyUsername = legacy[PrefsStore.Keys.LEGACY_USERNAME] as? String ?: ""
        writeMutex.withLock {
            if (server.isEmpty() && legacyServer.isNotEmpty()) {
                server = UrlNormalizer.normalizeUrl(legacyServer)
            }
            if (username.isEmpty() && legacyUsername.isNotEmpty()) {
                username = legacyUsername
            }
        }
        // Se persiste lo precargado para no depender del fichero heredado.
        if (server.isNotEmpty()) prefs.setString(K_SERVER, encrypt(server))
        if (username.isNotEmpty()) prefs.setString(K_USERNAME, encrypt(username))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val parts = blob.split(":")
        require(parts.size == 2) { "formato no válido" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        require(iv.size == GCM_IV_BYTES) { "IV no válido" }
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
