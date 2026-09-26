package com.algoce95.novaiptv.data.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import com.algoce95.novaiptv.R
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.data.model.WatchProgress

/**
 * Segundo camino de "Seguir viendo": las notificaciones de recomendación que
 * consume el launcher clásico de Android TV (la fila propia de la app). Es el
 * mecanismo que usa SmartTube; complementa (no sustituye) a los program
 * updates de `watch_next`, que solo lee el launcher de Google TV.
 */
object WatchNextNotifications {
    private const val TAG = "NovaIPTV"
    private const val CHANNEL_ID = "watch_next"
    private const val KEYS = "watch_next_notif_keys"

    fun activeCount(context: Context): Int =
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications?.count { it.packageName == context.packageName } ?: -1

    suspend fun publish(context: Context, candidates: List<WatchProgress>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Seguir viendo", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setShowBadge(false) },
        )
        val prefs = AppContainer.prefs
        val posted = (prefs.getStringList(KEYS) ?: emptyList()).toSet()
        val keys = candidates.mapTo(mutableSetOf()) { it.id }
        (posted - keys).forEach { manager.cancel(it.hashCode()) }
        for (progress in candidates) {
            val notification = buildNotification(context, progress) ?: continue
            manager.notify(progress.id.hashCode(), notification)
        }
        prefs.setStringList(KEYS, keys.toList())
        Log.i(TAG, "recomendaciones: ${keys.size} notificaciones")
    }

    private fun buildNotification(context: Context, progress: WatchProgress): Notification? {
        val poster = ProgramImages.prepare(context, progress.id, progress.poster)
            ?.let { decodeCard(context, it.lastPathSegment) }
        val intent = Intent(Intent.ACTION_VIEW, WatchNextPublisher.resumeUri(progress.id))
            .setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context,
            progress.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remainingMin = ((progress.durationMs - progress.positionMs) / 60_000L)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(progress.title)
            .setContentText(
                if (remainingMin > 0) "Quedan ~$remainingMin min" else "Sin terminar",
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setLocalOnly(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setColor(0xFF1E88E5.toInt())
            .setSmallIcon(R.drawable.app_logo)
            .setContentIntent(contentIntent)
        if (poster != null) builder.setLargeIcon(poster)
        return runCatching {
            NotificationCompat.BigPictureStyle(builder).build()
        }.onFailure { Log.w(TAG, "recomendación ${progress.id}: $it") }.getOrNull()
    }

    private fun decodeCard(context: Context, name: String?): android.graphics.Bitmap? {
        if (name == null) return null
        return runCatching {
            val file = java.io.File(ProgramImages.dir(context), name)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()
    }
}
