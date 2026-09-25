package com.algoce95.novaiptv.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.algoce95.novaiptv.data.model.StreamingParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File

private val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nova_prefs",
)

/**
 * Paridad con `SharedPrefsStorage` de Flutter: mismas claves, caché en
 * ficheros `nova_iptv_<clave>.json` del directorio de soporte y migración
 * única desde las preferencias de la app Flutter (mismo applicationId).
 */
class PrefsStore private constructor(private val context: Context) {

    object Keys {
        const val FAVORITE_CHANNELS = "favorite_channels"
        const val WATCH_HISTORY = "watch_history"
        const val WATCH_PROGRESS = "watch_progress_v1"

        // Último canal abierto: Directo arranca ahí en vez del primero de la
        // categoría.
        const val LAST_LIVE_CHANNEL = "last_live_channel"
        const val ACCOUNT_EXP_DATE = "account_exp_date"
        const val AUTO_REFRESH_DAYS = "settings_auto_refresh_days"
        const val AUTO_REFRESH_MINUTES = "settings_auto_refresh_minutes"
        const val CARD_SCALE = "settings_card_scale"
        const val GRID_DENSITY = "settings_grid_density"
        const val HIGH_CONTRAST = "settings_high_contrast"
        const val STORE_VISIBLE_ONLY = "settings_store_visible_only"
        const val COLOR_ACTIONS = "settings_color_actions"
        const val LIVE_HIDDEN = "settings_live_cat_hidden"
        const val LIVE_ORDER = "settings_live_cat_order"
        const val VOD_HIDDEN = "settings_vod_cat_hidden"
        const val VOD_ORDER = "settings_vod_cat_order"
        const val SERIES_HIDDEN = "settings_series_cat_hidden"
        const val SERIES_ORDER = "settings_series_cat_order"
        const val LAST_EPISODE_PREFIX = "last_episode_"

        // Pósters de respaldo para contenido sin cartelera (iTunes/TMDB).
        const val TMDB_API_KEY = "settings_tmdb_api_key"
        const val POSTER_FALLBACK_PREFIX = "posterfb_"
        const val POSTER_FALLBACK_INDEX = "poster_fallback_index"

        // Solo lectura para migración (las credenciales las posee CredentialStore).
        const val LEGACY_USERNAME = "username"
        const val LEGACY_PASSWORD = "password"
        const val LEGACY_SERVER = "server"
        const val LEGACY_SERVER_URL = "server_url"
    }

    companion object {
        const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"

        @Volatile
        private var instance: PrefsStore? = null

        fun get(context: Context): PrefsStore =
            instance ?: synchronized(this) {
                instance ?: PrefsStore(context.applicationContext).also { instance = it }
            }
    }

    private val dataStore: DataStore<Preferences> get() = context.novaDataStore

    suspend fun getString(key: String): String? =
        dataStore.data.map { it[stringPreferencesKey(key)] }.first()

    suspend fun setString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /**
     * Las listas se guardan como JSON en una clave de texto. Con `stringSet` el
     * orden no está garantizado y los duplicados se pierden, y tanto el orden de
     * favoritos y categorías como las teclas de color dependen de la posición.
     * DataStore identifica las claves por nombre, así que una lista antigua en
     * Set convive en la misma clave: se lee según lo que haya guardado.
     */
    suspend fun getStringList(key: String): List<String>? = when (val raw = rawValue(key)) {
        is String -> runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrNull()
        is Set<*> -> raw.map { it.toString() }
        else -> null
    }

    suspend fun setStringList(key: String, value: List<String>) {
        dataStore.edit { it[stringPreferencesKey(key)] = JSONArray(value).toString() }
    }

    private suspend fun rawValue(key: String): Any? =
        dataStore.data
            .map { prefs -> prefs.asMap().entries.firstOrNull { it.key.name == key }?.value }
            .first()

    fun stringFlow(key: String): Flow<String?> =
        dataStore.data.map { prefs -> prefs[stringPreferencesKey(key)] }

    fun booleanFlow(key: String, default: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    suspend fun getBoolean(key: String): Boolean? =
        dataStore.data.map { it[booleanPreferencesKey(key)] }.first()

    suspend fun setBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        dataStore.edit {
            it.remove(stringPreferencesKey(key))
            it.remove(stringSetPreferencesKey(key))
            it.remove(booleanPreferencesKey(key))
        }
    }

    fun cacheFile(key: String): File {
        val safeKey = key.replace(Regex("[^A-Za-z0-9_]"), "_")
        return File(context.filesDir, "nova_iptv_$safeKey.json")
    }

    /**
     * Escritura en streaming (la caché puede ser grande: nunca se construye
     * el JSON entero en memoria).
     */
    suspend fun writeCacheList(key: String, items: List<JSONObject>) =
        withContext(Dispatchers.IO) {
            val file = cacheFile(key)
            file.parentFile?.mkdirs()
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                StreamingParsers.writeJsonArray(writer, items)
            }
        }

    /**
     * Lectura en streaming; null si no hay caché o es inválida
     * (equivale a pedir los datos remotos).
     */
    suspend fun <T> readCacheList(
        key: String,
        parse: (JSONObject) -> T,
    ): List<T>? = withContext(Dispatchers.IO) {
        val file = cacheFile(key)
        if (!file.exists()) return@withContext null
        try {
            file.inputStream().buffered().use { stream ->
                StreamingParsers.parseTopList(stream, parse)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copia una sola vez los ajustes no sensibles desde la app Flutter y
     * borra su fichero. Las credenciales las migra CredentialStore antes.
     * Devuelve true si migró algo.
     */
    suspend fun migrateFromFlutterPrefs(): Boolean = withContext(Dispatchers.IO) {
        val legacy = readFlutterLegacyPrefs() ?: return@withContext false
        if (legacy.isEmpty()) {
            deleteFlutterPrefs()
            return@withContext false
        }
        var migrated = false
        for ((key, value) in legacy) {
            if (key == Keys.LEGACY_USERNAME || key == Keys.LEGACY_PASSWORD ||
                key == Keys.LEGACY_SERVER || key == Keys.LEGACY_SERVER_URL
            ) {
                continue
            }
            when (value) {
                is String -> {
                    setString(key, value)
                    migrated = true
                }
                is Set<*> -> {
                    setStringList(key, value.map { it.toString() })
                    migrated = true
                }
                is Boolean -> {
                    setBoolean(key, value)
                    migrated = true
                }
                else -> Unit
            }
        }
        deleteFlutterPrefs()
        migrated
    }

    /** Lee el XML de SharedPreferences de Flutter sin migrar nada. */
    internal fun readFlutterLegacyPrefs(): Map<String, Any?>? {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$FLUTTER_PREFS_NAME.xml")
        if (!file.exists()) return null
        val result = mutableMapOf<String, Any?>()
        try {
            file.inputStream().use { stream ->
                val parser = android.util.Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var event = parser.eventType
                var textKey: String? = null
                val text = StringBuilder()
                var setKey: String? = null
                var setItems = mutableSetOf<String>()
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            // Los boolean/int/long van en atributo value.
                            "boolean" -> parser.getAttributeValue(null, "name")?.let { key ->
                                result[key] = parser.getAttributeValue(null, "value") == "true"
                            }
                            "int", "long" -> parser.getAttributeValue(null, "name")?.let { key ->
                                result[key] = parser.getAttributeValue(null, "value") ?: ""
                            }
                            "string" -> {
                                // Sin name: es un item dentro de un <set>.
                                textKey = parser.getAttributeValue(null, "name")
                                text.clear()
                            }
                            "set" -> {
                                setKey = parser.getAttributeValue(null, "name")
                                setItems = mutableSetOf()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            // El XML va indentado: se ignoran los blancos dentro de <set>.
                            if (setKey != null) {
                                if (parser.text.isNotBlank()) setItems.add(parser.text)
                            } else {
                                text.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "string" -> {
                                textKey?.let { key -> result[key] = text.toString() }
                                textKey = null
                            }
                            "set" -> {
                                setKey?.let { key -> result[key] = setItems.toSet() }
                                setKey = null
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (_: Exception) {
            return null
        }
        return result
    }

    private fun deleteFlutterPrefs() {
        try {
            context.deleteSharedPreferences(FLUTTER_PREFS_NAME)
        } catch (_: Exception) {
            // Si no se puede borrar, la próxima vez ya no habrá nada que migrar.
        }
    }
}
