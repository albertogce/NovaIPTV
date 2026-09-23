package com.algoce95.novaiptv.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.algoce95.novaiptv.data.model.LiveChannel

/**
 * Reproductor de previsualización de canales en directo, compartido entre el
 * navegador y la pantalla completa.
 *
 * Vive fuera de la composición porque al navegar, el destino HOME se destruye:
 * si el navegador creara su propio ExoPlayer, ampliar a pantalla completa
 * reiniciaría la reproducción y al volver el preview quedaría parado. Aquí la
 * pantalla completa toma este mismo reproductor y lo devuelve al salir.
 */
@OptIn(UnstableApi::class)
object LivePreview {
    private var player: ExoPlayer? = null

    /** El navegador avisó de que va a abrir la pantalla completa. */
    private var handoffArmed = false
    private var ownedByFullscreen = false
    private var restorePending = false
    private var resumeOnReturn = false

    /** Canal de la previsualización; sobrevive a HOME -> PLAYER -> HOME. */
    var channel: LiveChannel? = null
        private set

    /**
     * Canal cargado, que al zapear desde la pantalla completa puede ser otro al
     * que el navegador solo sabe poner nombre buscándolo en su lista.
     */
    var channelId: Int? = null
        private set

    fun url(): String? =
        player?.currentMediaItem?.localConfiguration?.uri?.toString()

    /** true si [candidate] es el reproductor de esta preview. */
    fun holds(candidate: ExoPlayer?): Boolean = candidate != null && candidate === player

    /** El navegador pide el reproductor para montarlo en su `PlayerView`. */
    fun player(context: Context): ExoPlayer {
        player?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTV-Flutter/1.0")
            .setAllowCrossProtocolRedirects(true)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
            .also { player = it }
    }

    /** Reproduce el canal en la preview. Idempotente: la misma URL no reinicia. */
    fun play(context: Context, channel: LiveChannel, streamUrl: String) {
        val exo = player(context)
        this.channel = channel
        this.channelId = channel.channelId
        ownedByFullscreen = false
        handoffArmed = false
        if (url() != streamUrl) exo.setMediaItem(MediaItem.fromUri(streamUrl))
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.playWhenReady = true
    }

    /** Apaga la preview (se elige otra categoría). */
    fun stop() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        channel = null
        channelId = null
        restorePending = false
    }

    /**
     * El toque que abre la pantalla completa: autoriza a [claim] a quedarse con
     * este reproductor. Sin armar, el navegador lo libera al irse.
     */
    fun armHandoff() {
        handoffArmed = true
    }

    /**
     * La pantalla completa reclama el reproductor si ya está sonando ESA url;
     * si no, no hay nada que reutilizar y se libera el preview.
     */
    fun claim(streamUrl: String?): ExoPlayer? {
        if (!handoffArmed) return null
        handoffArmed = false
        val exo = player ?: return null
        if (url() != streamUrl || exo.playbackState == Player.STATE_IDLE) {
            dispose()
            return null
        }
        ownedByFullscreen = true
        restorePending = true
        return exo
    }

    /** La pantalla completa se cierra: se pausa y el navegador lo retoma. */
    fun handBack(loadedChannelId: Int?) {
        if (!ownedByFullscreen) return
        ownedByFullscreen = false
        channelId = loadedChannelId ?: channelId
        channel = channel?.takeIf { it.channelId == channelId }
        player?.playWhenReady = false
        restorePending = true
    }

    /** true la primera vez que el navegador se monta tras un handBack. */
    fun consumeRestore(): Boolean = restorePending.also { restorePending = false }

    /** Una sola conexión: en segundo plano se corta la red del preview. */
    fun onBackground() {
        val exo = player ?: return
        resumeOnReturn = exo.isPlaying
        exo.playWhenReady = false
        exo.stop()
    }

    fun onForeground() {
        val exo = player ?: return
        if (!resumeOnReturn) return
        resumeOnReturn = false
        exo.prepare()
        exo.playWhenReady = true
    }

    /**
     * El navegador sale de composición: libera su reproductor salvo que la
     * pantalla completa lo haya reclamado, porque entonces lo está usando ella.
     */
    fun releaseIfUnused(exo: ExoPlayer) {
        if (player !== exo) return
        if (handoffArmed || ownedByFullscreen) return
        exo.release()
        player = null
        channel = null
        channelId = null
        handoffArmed = false
        ownedByFullscreen = false
        restorePending = false
        resumeOnReturn = false
    }

    /** Libera de verdad, sin comprobaciones. */
    fun dispose() {
        player?.release()
        player = null
        channel = null
        channelId = null
        handoffArmed = false
        ownedByFullscreen = false
        restorePending = false
        resumeOnReturn = false
    }
}

/**
 * Al pasar el reproductor de una superficie a otra, la vista vieja destruye su
 * `SurfaceView` y con eso limpia la superficie del reproductor compartido, que
 * es un cambio asíncrono al desmontaje de la vista. Si la nueva se había ligado
 * antes, se quedaría en negro: se vuelve a ligar con un margen corto, cuando el
 * árbol anterior ya se ha ido.
 */
@OptIn(UnstableApi::class)
fun PlayerView.bindSharedSurface(player: Player) {
    this.player = player
    postDelayed(
        {
            if (isAttachedToWindow && this.player === player) {
                this.player = null
                this.player = player
            }
        },
        SURFACE_REBIND_MS,
    )
}

private const val SURFACE_REBIND_MS = 80L
