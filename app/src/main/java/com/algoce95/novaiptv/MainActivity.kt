package com.algoce95.novaiptv

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.theme.NovaTheme
import com.algoce95.novaiptv.presentation.nav.AppNav
import com.algoce95.novaiptv.presentation.tv.PosterClient

class MainActivity : ComponentActivity() {

    companion object {
        private val splashUntil = SystemClock.elapsedRealtime() + 450
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode in 183..186 &&
            AppContainer.onColorKey?.invoke(event.keyCode) == true
        ) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        handleResumeIntent(intent)
        // Breve permanencia mínima: evita el parpadeo del tema antiguo y deja
        // ver el logo; el sistema anima la salida.
        splash.setKeepOnScreenCondition { SystemClock.elapsedRealtime() < splashUntil }
        enableEdgeToEdge()
        setContent {
            NovaTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleResumeIntent(intent)
    }

    /** Tarjeta "Seguir viendo" pulsada en el inicio de Android TV. */
    private fun handleResumeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "novaiptv" || data.host != "resume") return
        data.getQueryParameter("progress")
            ?.takeIf { it.isNotBlank() }
            ?.let { AppContainer.pendingResume.value = it }
    }

    override fun onStart() {
        super.onStart()
        AppContainer.inForeground = true
        try {
            AppContainer.onAppForegrounded?.invoke()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        AppContainer.inForeground = false
        // Una sola conexión de streaming: al ir a segundo plano se detiene la
        // reproducción y se cierran las conexiones pooled ociosas para que el
        // panel no vea actividad desde este dispositivo.
        try {
            AppContainer.onAppBackgrounded?.invoke()
        } catch (_: Exception) {
        }
        try {
            AppContainer.api?.evictIdleConnections()
        } catch (_: Exception) {
        }
        try {
            PosterClient.evictIdleConnections()
        } catch (_: Exception) {
        }
        super.onStop()
    }
}
