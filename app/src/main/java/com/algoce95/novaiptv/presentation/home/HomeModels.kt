package com.algoce95.novaiptv.presentation.home

/** Vistas de la pantalla de inicio (paridad con `ActiveView` de Flutter). */
enum class ActiveView {
    HOME,
    LIVE,
    MOVIES,
    SERIES,
    FAVORITES,
    HISTORY,
    CONTINUE_WATCHING,
}

/** Categoría sintética que agrupa los canales favoritos en Directos. */
const val FAVORITES_CATEGORY_ID = "__favorites__"

data class HistoryEntry(
    val type: String,
    val id: String,
    val title: String,
    val logo: String,
)

/** Elemento de la fila "Añadido recientemente" del inicio. */
data class RecentItem(
    val type: String, // "movie" | "series"
    val id: String,
    val title: String,
    val logo: String,
)
