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
