package com.algoce95.novaiptv.presentation.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.playback.LivePreview
import com.algoce95.novaiptv.core.playback.bindSharedSurface
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.utils.UrlNormalizer
import com.algoce95.novaiptv.data.model.EpgProgram
import com.algoce95.novaiptv.data.model.WatchProgress
import com.algoce95.novaiptv.presentation.tv.hideSystemBars
import com.algoce95.novaiptv.presentation.tv.keepScreenOn
import com.algoce95.novaiptv.presentation.tv.lockLandscape
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.requestFocusReady
import com.algoce95.novaiptv.presentation.tv.tryRequestFocus
import com.algoce95.novaiptv.presentation.tv.tvPress
import com.algoce95.novaiptv.presentation.tv.unlockOrientation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private const val SEEK_STEP_S = 10L
private const val SEEK_STEP_LARGE_S = 30L
private const val SEEK_BAR_S = 60L
private const val SEEK_BAR_LARGE_S = 120L
private const val CONTROLS_HIDE_MS = 5000L
private const val RESUME_PROMPT_TIMEOUT_MS = 12_000L

/** Cuentra atrás antes de saltar al siguiente episodio al terminar el actual. */
private const val AUTO_ADVANCE_S = 8

private enum class FitMode { CONTAIN, COVER, STRETCH, ZOOM }

/** Paridad con `PlayerScreen` de Flutter (cola, zapping, resume, OSD, errores). */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val session = remember { AppContainer.playerSession }
    if (session == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // Directo ya sonando en la preview: se reutiliza ese reproductor para que
    // ampliar a pantalla completa no reinicie la reproducción (LivePreview).
    val exo = remember(session) {
        val preview = if (session.isLive) {
            LivePreview.claim(session.items.getOrNull(session.index)?.streamUrl)
        } else {
            null
        }
        preview ?: run {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent("IPTV-Flutter/1.0")
                .setAllowCrossProtocolRedirects(true)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
                .build()
        }
    }
    val fromPreview = LivePreview.holds(exo)
    var skipInitialLoad by remember(session) { mutableStateOf(fromPreview) }

    var queueIndex by remember(session) {
        mutableIntStateOf(session.index.coerceIn(0, (session.items.size - 1).coerceAtLeast(0)))
    }
    var title by remember(session) { mutableStateOf(session.title) }
    var episodeId by remember(session) { mutableStateOf(session.items.getOrNull(session.index)?.episodeId) }
    var initialized by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUrl by remember { mutableStateOf("") }
    var showControls by remember { mutableStateOf(true) }
    var treatAsLive by remember(session) { mutableStateOf(session.isLive) }
    var fitMode by remember { mutableStateOf(FitMode.CONTAIN) }
    var volume by remember { mutableFloatStateOf(1f) }
    var osdHint by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }
    var advancing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    // Segundo plano (una sola conexión de streaming): al ocultar la app se
    // detiene la red; al volver se reanuda solo si estaba sonando.
    var inBackground by remember { mutableStateOf(false) }
    var resumeOnReturn by remember { mutableStateOf(false) }

    // OSD de reanudación: si hay progreso guardado, se ofrece elegir en vez de
    // reanudar a ciegas. Non-null = posición (ms) que se ofrecerá continuar.
    var resumePromptMs by remember { mutableStateOf<Long?>(null) }

    // Pistas de audio y subtítulo. Sin elección explícita el panel apilaba dos
    // pistas de texto y se solapaban en pantalla.
    var showTrackSheet by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf(listOf<TrackChoice>()) }
    var subtitleTracks by remember { mutableStateOf(listOf<TrackChoice>()) }

    // Programa en emisión del canal (solo directo) y cuenta atrás del siguiente
    // episodio al terminar el actual.
    var nowProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var nextProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var autoAdvanceIn by remember { mutableIntStateOf(0) }

    val canSeek = initialized && !treatAsLive && durationMs > 0
    val hasPrevious = session.items.isNotEmpty() && queueIndex > 0
    val hasNext = session.items.isNotEmpty() && queueIndex < session.items.lastIndex

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val resumeContinueFocus = remember { FocusRequester() }
    val trackSheetFocus = remember { FocusRequester() }
    val nextEpisodeFocus = remember { FocusRequester() }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var osdJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleHide() {
        hideJob?.cancel()
        if (!isPlaying) return
        hideJob = scope.launch {
            delay(CONTROLS_HIDE_MS)
            showControls = false
        }
    }

    fun showOsd(message: String, millis: Long = 2000) {
        osdJob?.cancel()
        osdHint = message
        osdJob = scope.launch {
            delay(millis)
            osdHint = null
        }
    }

    fun revealControls() {
        showControls = true
        scope.launch { playFocus.tryRequestFocus() }
        scheduleHide()
    }

    fun hideControls() {
        hideJob?.cancel()
        showControls = false
        rootFocus.tryRequestFocus()
    }

    suspend fun saveProgress() {
        val pid = session.progressId ?: return
        if (treatAsLive || !initialized) return
        val dur = exo.duration
        val pos = exo.currentPosition
        if (dur < 1000 || pos < 2000) return
        AppContainer.watchProgress.save(
            WatchProgress(
                id = pid,
                title = title,
                positionMs = pos,
                durationMs = dur,
                updatedAtMs = System.currentTimeMillis(),
                episodeId = episodeId,
                poster = session.poster,
            ),
        )
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            try {
                saveProgress()
            } catch (_: Exception) {
            }
            onClose()
        }
    }

    fun handleBackground() {
        if (closing) return
        resumeOnReturn = exo.isPlaying && initialized
        inBackground = true
        scope.launch {
            try {
                saveProgress()
            } catch (_: Exception) {
            }
        }
        try {
            exo.playWhenReady = false
            exo.stop()
        } catch (_: Exception) {
        }
    }

    fun handleForeground() {
        inBackground = false
        if (closing) return
        if (resumeOnReturn) {
            resumeOnReturn = false
            retryTick++
        } else if (!initialized && !hasError) {
            // Se interrumpió una carga: reintentar al volver.
            retryTick++
        }
    }

    fun onBackPressed() {
        when {
            showTrackSheet -> showTrackSheet = false
            autoAdvanceIn > 0 -> autoAdvanceIn = 0
            showControls -> hideControls()
            else -> close()
        }
    }

    fun play() {
        if (exo.playbackState == Player.STATE_IDLE) {
            // Tras una parada en segundo plano hay que preparar de nuevo.
            retryTick++
            return
        }
        exo.play()
        scheduleHide()
    }

    fun pause() {
        exo.pause()
        hideJob?.cancel()
        if (!showControls) revealControls()
    }

    fun togglePlayPause() {
        if (exo.isPlaying) pause() else play()
    }

    fun seekBy(offsetMs: Long) {
        if (!canSeek) {
            if (treatAsLive) showOsd("Emisión en vivo")
            return
        }
        // Base en la posición real del reproductor (no en positionMs, que en
        // pausa se queda obsoleta) y refrescar para que pulsaciones repetidas
        // acumulen y la barra se mueva también en pausa.
        val target = (exo.currentPosition + offsetMs).coerceIn(0, durationMs)
        exo.seekTo(target)
        positionMs = target
        val secs = kotlin.math.abs(offsetMs / 1000)
        showOsd("${if (offsetMs < 0) "−" else "+"}${secs}s  ${formatDuration(target)}")
        scheduleHide()
    }

    fun seekToFraction(fraction: Float) {
        if (!canSeek) return
        val target = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        exo.seekTo(target)
        positionMs = target
        scheduleHide()
    }

    fun changeQueueItem(offset: Int) {
        if (session.items.isEmpty()) return
        val next = queueIndex + offset
        if (next < 0 || next > session.items.lastIndex) return
        // Mover a mano cancela la cuenta atrás del siguiente episodio.
        autoAdvanceIn = 0
        scope.launch {
            try {
                saveProgress()
            } catch (_: Exception) {
            }
            queueIndex = next
            session.onQueueIndexChanged?.invoke(next)
        }
    }

    fun restart() {
        if (!initialized) return
        if (treatAsLive) {
            retryTick++
            return
        }
        exo.seekTo(0)
        exo.play()
        showOsd("Desde el inicio")
        scheduleHide()
    }

    // El usuario eligió continuar desde el punto guardado en el OSD de reanudación.
    fun confirmResume() {
        val pos = resumePromptMs
        resumePromptMs = null
        if (pos != null) {
            exo.seekTo(pos)
            positionMs = pos
        }
        exo.playWhenReady = true
        exo.play()
        showControls = true
        playFocus.tryRequestFocus()
        scheduleHide()
    }

    // El usuario eligió empezar desde el principio en el OSD de reanudación.
    fun restartFromBeginning() {
        resumePromptMs = null
        exo.seekTo(0)
        positionMs = 0
        exo.playWhenReady = true
        exo.play()
        showControls = true
        playFocus.tryRequestFocus()
        scheduleHide()
    }

    fun cycleFit() {
        fitMode = FitMode.entries[(fitMode.ordinal + 1) % FitMode.entries.size]
        showOsd(
            when (fitMode) {
                FitMode.CONTAIN -> "Ajustar"
                FitMode.COVER -> "Rellenar"
                FitMode.STRETCH -> "Estirar"
                FitMode.ZOOM -> "Zoom"
            },
        )
        scheduleHide()
    }

    fun adjustVolume(delta: Float) {
        volume = (volume + delta).coerceIn(0f, 1f)
        exo.volume = volume
        showOsd("Volumen ${(volume * 100).toInt()}%")
    }

    // --- Pistas de audio y subtítulo -----------------------------------------

    fun refreshTrackChoices() {
        audioTracks = exo.currentTracks.buildChoices(C.TRACK_TYPE_AUDIO)
        subtitleTracks = exo.currentTracks.buildChoices(C.TRACK_TYPE_TEXT)
    }

    /** Con [choice] nulo se vuelve a "Automático" (o se apagan, en subtítulos). */
    fun selectTrack(type: Int, choice: TrackChoice?) {
        val builder = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT && choice == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
            if (choice != null) builder.setOverrideForType(choice.override)
        }
        exo.trackSelectionParameters = builder.build()
        refreshTrackChoices()
        showOsd(
            if (type == C.TRACK_TYPE_TEXT) {
                "Subtítulos: ${choice?.label ?: "apagados"}"
            } else {
                "Audio: ${choice?.label ?: "automático"}"
            },
        )
    }

    fun openTrackSheet() {
        hideJob?.cancel()
        refreshTrackChoices()
        showTrackSheet = true
        scope.launch { trackSheetFocus.requestFocusReady() }
    }

    /**
     * Único punto donde se registra el último canal sintonizado: el navegador,
     * la guía y los resultados de búsqueda montan su propia sesión. Se anota al
     * sintonizar, no al sonar, porque un canal ilegible sigue siendo el canal
     * donde te quedaste.
     */
    fun rememberLiveChannel() {
        val id = session.items.getOrNull(queueIndex)?.channelId ?: return
        scope.launch {
            runCatching {
                AppContainer.prefs.setString(PrefsStore.Keys.LAST_LIVE_CHANNEL, id.toString())
            }
        }
    }

    // --- Ciclo de vida: sistema, wakelock, liberación (réplica de init/dispose).
    DisposableEffect(Unit) {
        lockLandscape(activity)
        hideSystemBars(activity)
        keepScreenOn(activity, true)
        val bgHook = { handleBackground() }
        val fgHook = { handleForeground() }
        AppContainer.onAppBackgrounded = bgHook
        AppContainer.onAppForegrounded = fgHook
        onDispose {
            if (AppContainer.onAppBackgrounded === bgHook) AppContainer.onAppBackgrounded = null
            if (AppContainer.onAppForegrounded === fgHook) AppContainer.onAppForegrounded = null
            unlockOrientation(activity)
            hideSystemBars(activity)
            keepScreenOn(activity, false)
            val pid = session.progressId
            if (pid != null && !treatAsLive && initialized) {
                val dur = exo.duration
                val pos = exo.currentPosition
                if (dur >= 1000 && pos >= 2000) {
                    val progress = WatchProgress(
                        id = pid,
                        title = title,
                        positionMs = pos,
                        durationMs = dur,
                        updatedAtMs = System.currentTimeMillis(),
                        episodeId = episodeId,
                        poster = session.poster,
                    )
                    // Fuera del hilo principal: serializar y escribir prefs en
                    // onDispose congelaba la UI (y aportaba al ANR) al salir.
                    // Con force: es la posición final de la sesión, la tarjeta
                    // debe actualizarse aunque el refresco periódico vaya throttled.
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { AppContainer.watchProgress.save(progress, force = true) }
                    }
                }
            }
            if (fromPreview) {
                // Era el reproductor de la preview: se devuelve al navegador,
                // que lo reanuda por el canal en el que se haya quedado.
                LivePreview.handBack(session.items.getOrNull(queueIndex)?.channelId)
            } else {
                exo.pause()
                exo.release()
            }
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    // Aviso de "ya se está viendo": bajo demanda y con imagen
                    // real en pantalla, no al pulsar el botón.
                    if (!treatAsLive) {
                        session.items.getOrNull(queueIndex)?.let { item ->
                            session.onPlaybackStarted?.invoke(item)
                        }
                    }
                    scheduleHide()
                } else {
                    hideJob?.cancel()
                }
            }

            override fun onIsLoadingChanged(loading: Boolean) {
                isBuffering = loading
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTrackChoices()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val d = exo.duration
                    if (d > 0) durationMs = d
                    refreshTrackChoices()
                }
                if (state == Player.STATE_ENDED && !advancing && autoAdvanceIn == 0 &&
                    session.items.isNotEmpty() && !treatAsLive &&
                    queueIndex < session.items.lastIndex
                ) {
                    // Antes saltaba de golpe y no había forma de quedarse
                    // viendo los créditos; ahora cuenta atrás y opción de parar.
                    autoAdvanceIn = AUTO_ADVANCE_S
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                errorMessage = friendlyPlaybackError(error)
                scope.launch { retryFocus.tryRequestFocus() }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // --- Inicialización por item (resume incluido).
    LaunchedEffect(queueIndex, retryTick) {
        if (inBackground) return@LaunchedEffect
        val item = session.items.getOrNull(queueIndex)
        if (item == null) {
            hasError = true
            errorMessage = "No se pudo reproducir el contenido."
            return@LaunchedEffect
        }
        if (skipInitialLoad && queueIndex == session.index) {
            // El preview ya trae este canal cargando: solo se toma el control.
            skipInitialLoad = false
            lastUrl = item.streamUrl
            initialized = true
            treatAsLive = true
            isPlaying = exo.isPlaying
            rememberLiveChannel()
            showControls = true
            playFocus.tryRequestFocus()
            scheduleHide()
            return@LaunchedEffect
        }
        val myAttempt = ++attempt
        lastUrl = item.streamUrl
        episodeId = item.episodeId
        title = item.title
        exo.volume = volume
        initialized = false
        hasError = false
        errorMessage = ""
        showControls = true
        try {
            exo.setMediaItem(MediaItem.fromUri(item.streamUrl))
            exo.prepare()
            exo.playWhenReady = true
            if (session.isLive) {
                // Directo conocido: la duración nunca llega, sin espera.
                treatAsLive = true
            } else {
                var waited = 0
                while (exo.duration == C.TIME_UNSET && waited < 15_000 && !inBackground) {
                    delay(250)
                    waited += 250
                }
                if (inBackground) return@LaunchedEffect
                val dur = exo.duration
                val looksLive = dur <= 0
                treatAsLive = looksLive
                if (!looksLive) {
                    val pid = session.progressId
                    if (pid != null) {
                        val saved = AppContainer.watchProgress.get(pid)
                        if (saved != null && saved.episodeId == item.episodeId &&
                            saved.positionMs > 3_000 && saved.positionMs < dur - 5_000
                        ) {
                            // Pausa y ofrece elegir: continuar desde el punto
                            // guardado o empezar desde el principio.
                            exo.playWhenReady = false
                            resumePromptMs = saved.positionMs
                        }
                    }
                }
            }
            initialized = true
            if (treatAsLive) rememberLiveChannel()
            showControls = true
            if (resumePromptMs != null) {
                resumeContinueFocus.tryRequestFocus()
            } else {
                playFocus.tryRequestFocus()
            }
            scheduleHide()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (session.items.size <= 1 && myAttempt == 1 &&
                (item.streamUrl.endsWith(".ts") || item.streamUrl.endsWith(".m3u8"))
            ) {
                retryTick++
                return@LaunchedEffect
            }
            hasError = true
            errorMessage = friendlyPlaybackError(null) + "\n${UrlNormalizer.sanitizeStreamUrl(e.toString())}"
            retryFocus.tryRequestFocus()
        }
    }

    // --- Posición en directo mientras reproduce (tope 250 ms).
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exo.currentPosition
            val d = exo.duration
            if (d > 0) durationMs = d
            delay(250)
        }
    }

    // --- Guardado periódico de progreso (solo VOD).
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            try {
                saveProgress()
            } catch (_: Exception) {
            }
        }
    }

    // Cuenta atrás del siguiente episodio: 0 = apagado.
    LaunchedEffect(autoAdvanceIn) {
        if (autoAdvanceIn <= 0) return@LaunchedEffect
        delay(1000)
        if (autoAdvanceIn <= 1) {
            autoAdvanceIn = 0
            advancing = true
            changeQueueItem(1)
            advancing = false
        } else {
            autoAdvanceIn--
        }
    }

    // Programa en emisión del canal. Solo en directo, y se vuelve a pedir justo
    // cuando termina el que se está mostrando.
    LaunchedEffect(queueIndex, treatAsLive) {
        nowProgram = null
        nextProgram = null
        val channelId = session.items.getOrNull(queueIndex)?.channelId
        val client = AppContainer.api
        if (channelId == null || client == null || !treatAsLive) return@LaunchedEffect
        while (true) {
            val listings = runCatching { client.getShortEpgPrograms(channelId) }
                .getOrDefault(emptyList())
            val nowMs = System.currentTimeMillis()
            val at = listings.indexOfFirst { nowMs in it.startMs until it.endMs }
            nowProgram = listings.getOrNull(at)
            nextProgram = if (at >= 0) listings.getOrNull(at + 1) else listings.firstOrNull()
            val endsIn = nowProgram?.let { it.endMs - nowMs } ?: 0L
            delay(if (endsIn > 0) endsIn + 1_000 else 60_000)
        }
    }

    LaunchedEffect(showControls) {
        if (!showControls) rootFocus.tryRequestFocus()
    }

    // Si nadie elige en 12 s, continuar desde el punto guardado por defecto
    // (nunca dejar la reproducción parada indefinidamente).
    LaunchedEffect(resumePromptMs) {
        if (resumePromptMs != null) {
            delay(RESUME_PROMPT_TIMEOUT_MS)
            if (resumePromptMs != null) confirmResume()
        }
    }

    BackHandler(enabled = true) {
        onBackPressed()
    }

    fun handleHardwareKey(code: Int, repeat: Boolean): Boolean {
        // Códigos Android directos (KEYCODE_*): no dependen del mapeo de Compose.
        when (code) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                revealControls()
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                play()
                revealControls()
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pause()
                revealControls()
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                close()
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy((if (repeat) SEEK_STEP_LARGE_S else SEEK_STEP_S) * 1000)
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBy(-(if (repeat) SEEK_STEP_LARGE_S else SEEK_STEP_S) * 1000)
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                changeQueueItem(1)
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                changeQueueItem(-1)
                return true
            }
            android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (treatAsLive) {
                    changeQueueItem(1)
                    revealControls()
                    return true
                }
                return false
            }
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (treatAsLive) {
                    changeQueueItem(-1)
                    revealControls()
                    return true
                }
                return false
            }
        }
        return false
    }

    fun onRootKey(code: Int, isDown: Boolean, repeat: Boolean): Boolean {
        if (handleHardwareKey(code, repeat)) return true
        // ATRÁS lo gestiona en exclusiva el BackHandler de abajo: si también se
        // atiende aquí, la misma pulsación oculta el OSD y acto seguido cierra
        // el reproductor.
        if (code == android.view.KeyEvent.KEYCODE_BACK) return false
        if (code == android.view.KeyEvent.KEYCODE_ESCAPE) {
            if (isDown) onBackPressed()
            return true
        }
        if (!showControls && autoAdvanceIn == 0) {
            // Con la cuenta atrás activa las teclas van al aviso, no a revelar
            // el OSD: si no, "Cancelar" quedaba inalcanzable.
            if (!isDown) return true
            when (code) {
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_SPACE -> revealControls()
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> seekBy(
                    -(if (repeat) SEEK_STEP_LARGE_S else SEEK_STEP_S) * 1000,
                )
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> seekBy(
                    (if (repeat) SEEK_STEP_LARGE_S else SEEK_STEP_S) * 1000,
                )
                android.view.KeyEvent.KEYCODE_DPAD_UP -> adjustVolume(0.1f)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> adjustVolume(-0.1f)
                else -> revealControls()
            }
            return true
        }
        return false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when (event.type) {
                    KeyEventType.KeyDown -> onRootKey(
                        code = code,
                        isDown = true,
                        repeat = event.nativeKeyEvent.repeatCount > 0,
                    )
                    KeyEventType.KeyUp -> {
                        // Escape ya se tragó en KeyDown; Atrás se deja pasar al
                        // BackHandler, que es quien lo atiende.
                        code == android.view.KeyEvent.KEYCODE_ESCAPE
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (showControls) hideControls() else revealControls()
                },
                onDoubleClick = { togglePlayPause() },
            ),
    ) {
        // La superficie existe desde el principio: si se monta al terminar el
        // init, el vídeo no tiene dónde renderizar y solo se oye el audio.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Reutilizado de la preview: hay que religar la superficie
                    // cuando la vista del navegador se haya ido.
                    if (fromPreview) bindSharedSurface(exo) else player = exo
                }
            },
            onRelease = { view -> view.player = null },
            update = { view ->
                view.player = exo
                view.resizeMode = when (fitMode) {
                    FitMode.CONTAIN -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    FitMode.COVER -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    FitMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    FitMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                val zoom = if (fitMode == FitMode.ZOOM) 1.18f else 1f
                view.scaleX = zoom
                view.scaleY = zoom
            },
            modifier = Modifier.fillMaxSize(),
        )
        // En streams en directo ExoPlayer puede permanecer en estado loading
        // mientras sigue entregando frames. No mostrar la capa en ese caso:
        // además de ser engañosa, intercepta la interacción con los controles.
        val showLoadingIndicator = !hasError &&
            (!initialized || (isBuffering && !isPlaying))
        if (showLoadingIndicator) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent)
            }
        }
        if (!initialized && !hasError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Conectando...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 88.dp),
                )
            }
        }
        if (hasError) {
            PlayerErrorView(
                message = errorMessage,
                url = lastUrl,
                retryFocus = retryFocus,
                onBack = ::close,
                onRetry = { retryTick++ },
            )
        }
        if (!hasError && osdHint != null) {
            OsdBanner(text = osdHint!!)
        }
        if (showControls && !hasError) {
            PlayerControls(
                title = title,
                queuePosition = if (session.items.isNotEmpty()) {
                    "${queueIndex + 1} / ${session.items.size}"
                } else {
                    null
                },
                isLiveBadge = treatAsLive,
                isPlaying = isPlaying,
                canSeek = canSeek,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                progress = if (canSeek && durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                positionLabel = if (treatAsLive) "En vivo" else formatDuration(positionMs),
                durationLabel = if (treatAsLive) "" else formatDuration(durationMs),
                fitLabel = when (fitMode) {
                    FitMode.CONTAIN -> "Ajustar"
                    FitMode.COVER -> "Rellenar"
                    FitMode.STRETCH -> "Estirar"
                    FitMode.ZOOM -> "Zoom"
                },
                restartLabel = if (treatAsLive) "Recargar" else "Reiniciar",
                nowPlaying = nowProgram?.title,
                nowRange = nowProgram?.let {
                    "${clockLabel(it.startMs)} – ${clockLabel(it.endMs)}"
                },
                nextPlaying = nextProgram?.let { "${clockLabel(it.startMs)}  ${it.title}" },
                showTracksChip = audioTracks.size + subtitleTracks.size > 1,
                playFocus = playFocus,
                onBack = ::close,
                onTogglePlay = ::togglePlayPause,
                onSeek = ::seekBy,
                onSeekFraction = ::seekToFraction,
                onPrevious = { changeQueueItem(-1) },
                onNext = { changeQueueItem(1) },
                onCycleFit = ::cycleFit,
                onRestart = ::restart,
                onOpenTracks = ::openTrackSheet,
            )
        }
        if (!hasError && resumePromptMs != null) {
            ResumePromptOverlay(
                positionLabel = formatDuration(resumePromptMs!!),
                continueFocus = resumeContinueFocus,
                onContinue = ::confirmResume,
                onRestart = ::restartFromBeginning,
            )
        }
        if (showTrackSheet) {
            TrackSheet(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                firstFocus = trackSheetFocus,
                onAudio = { selectTrack(C.TRACK_TYPE_AUDIO, it) },
                onSubtitle = { selectTrack(C.TRACK_TYPE_TEXT, it) },
                onClose = { showTrackSheet = false },
            )
        }
        if (!hasError && autoAdvanceIn > 0) {
            NextEpisodeOverlay(
                seconds = autoAdvanceIn,
                nextTitle = session.items.getOrNull(queueIndex + 1)?.title.orEmpty(),
                actionFocus = nextEpisodeFocus,
                onPlayNow = {
                    autoAdvanceIn = 0
                    changeQueueItem(1)
                },
                onCancel = { autoAdvanceIn = 0 },
            )
        }
    }
}

/** Una pista elegible del reproductor, con la etiqueta ya legible. */
private data class TrackChoice(
    val override: TrackSelectionOverride,
    val label: String,
    val selected: Boolean,
)

/**
 * Pistas de un tipo. El `TrackSelectionOverride` se monta sobre el
 * `MediaTrackGroup`, no sobre el índice del grupo: los índices cambian entre
 * actualizaciones de `Tracks` y la elección se perdía al cambiar de canal.
 */
private fun Tracks.buildChoices(type: Int): List<TrackChoice> = groups
    .filter { it.type == type && it.isSupported }
    .flatMap { group ->
        (0 until group.length).map { index ->
            TrackChoice(
                override = TrackSelectionOverride(group.mediaTrackGroup, index),
                label = trackLabel(group.getTrackFormat(index), type, index),
                selected = group.isTrackSelected(index),
            )
        }
    }

private fun trackLabel(format: Format, type: Int, index: Int): String {
    val language = format.language?.takeIf { it.isNotBlank() }?.let { tag ->
        // El panel manda códigos de 2 o 3 letras; si no salen, se muestran crudos.
        java.util.Locale.forLanguageTag(tag).displayLanguage.ifBlank { tag.uppercase() }
    }
    val parts = listOfNotNull(
        language,
        format.label?.takeIf { it.isNotBlank() },
        if (type == C.TRACK_TYPE_TEXT) {
            null
        } else {
            format.sampleMimeType?.substringAfterLast('/')?.uppercase()
        },
    )
    return parts.ifEmpty { listOf("Pista ${index + 1}") }.joinToString(" · ")
}

private fun clockLabel(ms: Long): String {
    val at = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(at.hour, at.minute)
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600 / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minutes:$seconds" else "$minutes:$seconds"
}

/** Mensaje accionable ante fallos de códec en vez del volcado del reproductor. */
private fun friendlyPlaybackError(e: PlaybackException?): String {
    val raw = e?.message ?: e.toString()
    val s = UrlNormalizer.sanitizeStreamUrl(raw)
    val isCodec = raw.contains("MediaCodec") ||
        e?.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        e?.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
        e?.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
    return if (isCodec) {
        "Tu dispositivo no puede decodificar este vídeo (códec no soportado, p. ej. HEVC en MKV).\n" +
            "Prueba con otro capítulo o en otro dispositivo.\n$s"
    } else {
        "No se pudo reproducir el contenido.\n$s"
    }
}

@Composable
private fun OsdBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}

/** OSD modal para elegir reanudar desde el punto guardado o empezar de cero. */
@Composable
private fun ResumePromptOverlay(
    positionLabel: String,
    continueFocus: FocusRequester,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.panel)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "¿Continuar desde $positionLabel?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.W700,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ResumePromptButton(
                    label = "Continuar",
                    accent = AppColors.accent,
                    focusRequester = continueFocus,
                    autofocus = true,
                    onClick = onContinue,
                )
                ResumePromptButton(
                    label = "Desde el principio",
                    accent = AppColors.mint,
                    onClick = onRestart,
                )
            }
        }
    }
}

@Composable
private fun ResumePromptButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) accent else Color.White.copy(alpha = 0.08f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, autofocus = autofocus, onTap = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        LaunchedEffect(autofocus) {
            if (autofocus) focusRequester?.tryRequestFocus()
        }
        Text(
            text = label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.W600,
        )
    }
}

/**
 * Panel de pistas. Con [firstFocus] en la primera opción de la primera sección
 * que exista; si no hay ninguna, el panel no tiene sentido y no se abre.
 */
@Composable
private fun TrackSheet(
    audioTracks: List<TrackChoice>,
    subtitleTracks: List<TrackChoice>,
    firstFocus: FocusRequester,
    onAudio: (TrackChoice?) -> Unit,
    onSubtitle: (TrackChoice?) -> Unit,
    onClose: () -> Unit,
) {
    val showSubtitles = subtitleTracks.isNotEmpty()
    val showAudio = audioTracks.size > 1
    // Sin subtítulos, el audio es la primera sección que hay y la que recibe el
    // foco al abrir.
    val audioFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusReady() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.panel)
                .padding(horizontal = 26.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Idiomas",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    modifier = Modifier.weight(1f),
                )
                TvCircleButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    iconSize = 22,
                    onClick = onClose,
                )
            }
            if (showSubtitles) {
                Spacer(Modifier.height(12.dp))
                TrackSection(
                    title = "Subtítulos",
                    options = listOf(
                        "Apagados" to subtitleTracks.none { it.selected },
                    ) + subtitleTracks.map { it.label to it.selected },
                    onSelect = { index ->
                        onSubtitle(if (index == 0) null else subtitleTracks[index - 1])
                    },
                    focusRequester = firstFocus,
                )
            }
            if (showAudio) {
                Spacer(Modifier.height(16.dp))
                TrackSection(
                    title = "Audio",
                    options = listOf(
                        "Automático" to audioTracks.none { it.selected },
                    ) + audioTracks.map { it.label to it.selected },
                    onSelect = { index ->
                        onAudio(if (index == 0) null else audioTracks[index - 1])
                    },
                    // Sin subtítulos, el audio es la primera sección que hay.
                    focusRequester = if (showSubtitles) audioFocus else firstFocus,
                )
            }
        }
    }
}

@Composable
private fun TrackSection(
    title: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    focusRequester: FocusRequester,
) {
    Column {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        options.forEachIndexed { index, (label, selected) ->
            val focus = rememberTvFocus()
            val focused = focus.focused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            focused -> AppColors.accent.copy(alpha = 0.26f)
                            selected -> AppColors.mint.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) AppColors.mint else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    .focusable(interactionSource = focus.interaction)
                    .tvPress(fireOnDown = true, onTap = { onSelect(index) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Text(
                        text = "Elegida",
                        color = AppColors.mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
        }
    }
}

/** Aviso modal del siguiente episodio: deja cancelar el salto. */
@Composable
private fun NextEpisodeOverlay(
    seconds: Int,
    nextTitle: String,
    actionFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(Unit) { actionFocus.requestFocusReady() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.panel)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Siguiente episodio en $seconds s",
                color = AppColors.mint,
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = nextTitle,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.W700,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ResumePromptButton(
                    label = "Reproducir ahora",
                    accent = AppColors.accent,
                    focusRequester = actionFocus,
                    autofocus = true,
                    onClick = onPlayNow,
                )
                ResumePromptButton(label = "Cancelar", accent = AppColors.mint, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    queuePosition: String?,
    isLiveBadge: Boolean,
    isPlaying: Boolean,
    canSeek: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    progress: Float,
    positionLabel: String,
    durationLabel: String,
    fitLabel: String,
    restartLabel: String,
    nowPlaying: String?,
    nowRange: String?,
    nextPlaying: String?,
    showTracksChip: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleFit: () -> Unit,
    onRestart: () -> Unit,
    onOpenTracks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.scrimStrong,
                        AppColors.scrimFaint,
                        AppColors.scrimFaint,
                        AppColors.scrimBottom,
                    ),
                ),
            )
            .padding(horizontal = 36.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (queuePosition != null) {
                Text(
                    text = queuePosition,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            if (isLiveBadge) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accent)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "EN VIVO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (canSeek) {
                TvCircleButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = "Retroceder 10 segundos",
                    onClick = { onSeek(-SEEK_STEP_S * 1000) },
                )
            }
            if (hasPrevious) {
                TvCircleButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    iconSize = 40,
                    onClick = onPrevious,
                )
            }
            TvCircleButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                iconSize = 48,
                primary = true,
                focusRequester = playFocus,
                autofocus = true,
                onClick = onTogglePlay,
            )
            if (hasNext) {
                TvCircleButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    iconSize = 40,
                    onClick = onNext,
                )
            }
            if (canSeek) {
                TvCircleButton(
                    icon = Icons.Filled.Forward10,
                    contentDescription = "Avanzar 10 segundos",
                    onClick = { onSeek(SEEK_STEP_S * 1000) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TvSeekBar(
            enabled = canSeek,
            progress = progress,
            positionLabel = positionLabel,
            durationLabel = durationLabel,
            onSeekStep = onSeek,
            onSeekFraction = onSeekFraction,
        )
        Spacer(Modifier.height(12.dp))
        // Qué se está emitiendo. Va dentro del OSD, así que se oculta con él.
        if (nowPlaying != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.mint.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "AHORA",
                        color = AppColors.mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = nowPlaying,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (nowRange != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = nowRange,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
            }
            if (nextPlaying != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A continuación  $nextPlaying",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvChipButton(
                label = fitLabel,
                icon = Icons.Filled.FitScreen,
                onClick = onCycleFit,
            )
            TvChipButton(
                label = restartLabel,
                icon = Icons.Filled.Replay,
                onClick = onRestart,
            )
            if (showTracksChip) {
                TvChipButton(
                    label = "Idiomas",
                    icon = Icons.Filled.Subtitles,
                    onClick = onOpenTracks,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "OK play/pausa  ·  barra ← → 1 min  ·  ↑↓ volumen  ·  Atrás oculta",
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun TvCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Int = 30,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val diameter = if (primary) 84.dp else 56.dp
    LaunchedEffect(autofocus) {
        if (autofocus) focusRequester?.tryRequestFocus()
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(diameter)
            .clip(CircleShape)
            .background(
                when {
                    primary && focused -> AppColors.accent
                    primary -> Color.White
                    focused -> AppColors.accent.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.1f)
                },
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused && primary -> Color.White
                    focused -> AppColors.accent
                    else -> Color.White.copy(alpha = 0.18f)
                },
                shape = CircleShape,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (primary && !focused) AppColors.playerInk else Color.White,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

@Composable
private fun TvChipButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) AppColors.accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.accent else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun TvSeekBar(
    enabled: Boolean,
    progress: Float,
    positionLabel: String,
    durationLabel: String,
    onSeekStep: (Long) -> Unit,
    onSeekFraction: (Float) -> Unit,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusable(enabled = enabled, interactionSource = focus.interaction)
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent false
                }
                // Solo al pulsar (el repeat llega como KeyDown con repeatCount).
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val repeat = event.nativeKeyEvent.repeatCount > 0
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekStep(-(if (repeat) SEEK_BAR_LARGE_S else SEEK_BAR_S) * 1000)
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekStep((if (repeat) SEEK_BAR_LARGE_S else SEEK_BAR_S) * 1000)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Text(
            text = positionLabel,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.width(72.dp),
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val widthPx = constraints.maxWidth.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (focused) 18.dp else 10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .border(
                        width = 2.dp,
                        color = if (focused) AppColors.accent else Color.Transparent,
                        shape = RoundedCornerShape(99.dp),
                    )
                    .pointerInput(enabled) {
                        detectTapGestures { offset ->
                            if (enabled && widthPx > 0) onSeekFraction(offset.x / widthPx)
                        }
                    }
                    .pointerInput(enabled) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (enabled && widthPx > 0) {
                                onSeekFraction((change.position.x / widthPx).coerceIn(0f, 1f))
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                LinearProgressIndicator(
                    progress = { if (enabled) progress else 1f },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (enabled) AppColors.accent else AppColors.expired,
                    trackColor = Color.White.copy(alpha = 0.14f),
                )
            }
        }
        Text(
            text = durationLabel,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun PlayerErrorView(
    message: String,
    url: String,
    retryFocus: FocusRequester,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    LaunchedEffect(Unit) { retryFocus.tryRequestFocus() }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp),
        ) {
            Icon(
                Icons.Filled.SignalWifiOff,
                contentDescription = null,
                tint = AppColors.expired,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Contenido no disponible",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = UrlNormalizer.sanitizeStreamUrl(url),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row {
                TvChipButton(label = "Reintentar", icon = Icons.Filled.Refresh, onClick = onRetry)
                TvChipButton(label = "Volver", icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            }
        }
    }
}
