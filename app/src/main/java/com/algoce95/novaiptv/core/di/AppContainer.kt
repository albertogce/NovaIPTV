package com.algoce95.novaiptv.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.algoce95.novaiptv.core.storage.CredentialStore
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.data.api.XtreamApiClient
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.data.model.VodMovie
import com.algoce95.novaiptv.data.playback.WatchProgressStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cola de reproducción compartida (equivale a pasar `queue` al PlayerScreen). */
data class QueueItem(
    val streamUrl: String,
    val title: String,
    val episodeId: String? = null,
)

data class PlayerSession(
    val items: List<QueueItem>,
    val index: Int,
    val title: String,
    val progressId: String?,
    val isLive: Boolean,
    val poster: String? = null,
    val onQueueIndexChanged: ((Int) -> Unit)? = null,
)

data class SearchSnapshot(
    val channels: List<LiveChannel> = emptyList(),
    val movies: List<VodMovie> = emptyList(),
    val series: List<Series> = emptyList(),
)

/** DI manual: singletons + estado compartido entre pantallas. */
object AppContainer {
    var onColorKey: ((Int) -> Boolean)? = null
    lateinit var prefs: PrefsStore
        private set
    lateinit var credentials: CredentialStore
        private set
    lateinit var watchProgress: WatchProgressStore
        private set

    var api: XtreamApiClient? = null
        private set

    /** Canales con los que se abrió la guía EPG (ya filtrados por categoría). */
    var epgChannels: List<LiveChannel> = emptyList()

    /** Resultado global de búsqueda para la pantalla de resultados. */
    var searchSnapshot: SearchSnapshot = SearchSnapshot()

    /** Sesión activa del reproductor. */
    var playerSession: PlayerSession? = null

    /**
     * Garantía de una sola conexión de streaming: MainActivity avisa al pasar a
     * segundo plano y el reproductor detiene la red; al volver reanuda si estaba
     * sonando. El refresco periódico también se salta en segundo plano.
     */
    var inForeground: Boolean = true
    var onAppBackgrounded: (() -> Unit)? = null
    var onAppForegrounded: (() -> Unit)? = null

    private val initMutex = Mutex()
    private var initialized = false

    /**
     * Carga credenciales, migra datos de Flutter y crea el cliente.
     * Devuelve true si hay sesión válida (equivale a `_canSkipLogin`).
     */
    /** Contexto de aplicación para consultas que lo requieren (p.ej. conectividad). */
    var appContext: Context? = null
        private set

    suspend fun ensureInitialized(context: Context): Boolean {
        if (initialized) return isAuthenticated()
        initMutex.withLock {
            if (initialized) return isAuthenticated()
            val appContext = context.applicationContext
            this.appContext = appContext
            prefs = PrefsStore.get(appContext)
            credentials = CredentialStore(prefs)
            credentials.init()
            // La migración lee el fichero heredado DESPUÉS del precargado
            // de servidor/usuario, y luego lo borra.
            prefs.migrateFromFlutterPrefs()
            watchProgress = WatchProgressStore(prefs)
            refreshApi()
            initialized = true
            return isAuthenticated()
        }
    }

    fun isAuthenticated(): Boolean {
        if (!::credentials.isInitialized) return false
        return credentials.username.isNotEmpty() &&
            credentials.password.isNotEmpty() &&
            credentials.server.isNotEmpty()
    }

    fun refreshApi() {
        val previous = api
        val next = if (isAuthenticated()) {
            XtreamApiClient.create(
                credentials.server,
                credentials.username,
                credentials.password,
            )
        } else {
            null
        }
        api = next
        // Libera el cliente anterior (dispatcher + conexiones pooled) al sustituirlo,
        // para que el panel no vea conexiones abiertas más allá de la activa.
        if (previous !== next) {
            previous?.shutdown()
        }
    }
}

/** Factoría concisa para ViewModels con parámetros. */
inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
