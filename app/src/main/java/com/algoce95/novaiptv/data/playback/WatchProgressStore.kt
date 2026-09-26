package com.algoce95.novaiptv.data.playback

import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.WatchProgress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** Paridad con `WatchProgressStore` de Flutter (tope 100, escritura seriada). */
class WatchProgressStore(private val prefs: PrefsStore) {

    private val writeMutex = Mutex()

    /** Aviso tras cada cambio (publicación de tarjetas en el inicio de TV). */
    var onChanged: (suspend (List<WatchProgress>) -> Unit)? = null

    suspend fun getAll(): List<WatchProgress> {
        val raw = prefs.getString(PrefsStore.Keys.WATCH_PROGRESS) ?: return emptyList()
        return try {
            // Parseo tolerante: un elemento corrupto se descarta sin arrastrar
            // al resto (antes un solo fallo devolvía lista vacía y el siguiente
            // save() sobrescribía todo el historial).
            Parsers.objectList(JSONArray(raw))
                .mapNotNull { runCatching { Parsers.parseWatchProgress(it) }.getOrNull() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun get(id: String): WatchProgress? = getAll().firstOrNull { it.id == id }

    suspend fun save(item: WatchProgress) {
        writeMutex.withLock {
            val items = getAll().filterNot { it.id == item.id }.toMutableList()
            items.add(0, item)
            if (items.size > 100) items.subList(100, items.size).clear()
            prefs.setString(
                PrefsStore.Keys.WATCH_PROGRESS,
                JSONArray(items.map { it.toJson() }).toString(),
            )
        }
        notifyChanged()
    }

    suspend fun remove(id: String) {
        writeMutex.withLock {
            val items = getAll().filterNot { it.id == id }
            prefs.setString(
                PrefsStore.Keys.WATCH_PROGRESS,
                JSONArray(items.map { it.toJson() }).toString(),
            )
        }
        notifyChanged()
    }

    private suspend fun notifyChanged() {
        val callback = onChanged ?: return
        runCatching { callback(getAll()) }
    }
}
