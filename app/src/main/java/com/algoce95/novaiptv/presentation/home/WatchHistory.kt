package com.algoce95.novaiptv.presentation.home

import com.algoce95.novaiptv.core.storage.PrefsStore

/** Guarda `movie:<id>` / `series:<id>` al frente del historial (tope 50). */
suspend fun PrefsStore.pushHistory(key: String) {
    val history = (getStringList(PrefsStore.Keys.WATCH_HISTORY) ?: emptyList()).toMutableList()
    history.remove(key)
    history.add(0, key)
    if (history.size > 50) history.subList(50, history.size).clear()
    setStringList(PrefsStore.Keys.WATCH_HISTORY, history)
}

/** Quita una entrada suelta; se usa con la pulsación larga en Historial. */
suspend fun PrefsStore.dropHistory(key: String) {
    val history = (getStringList(PrefsStore.Keys.WATCH_HISTORY) ?: emptyList())
        .filterNot { it == key }
    setStringList(PrefsStore.Keys.WATCH_HISTORY, history)
}

suspend fun PrefsStore.clearHistory() {
    setStringList(PrefsStore.Keys.WATCH_HISTORY, emptyList())
}
