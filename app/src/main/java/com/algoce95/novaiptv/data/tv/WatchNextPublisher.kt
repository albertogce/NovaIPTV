package com.algoce95.novaiptv.data.tv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.algoce95.novaiptv.data.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Publica el "Seguir viendo" en el inicio de Android TV (program updates,
 * tabla watch_next del proveedor de TV del sistema). Cada progreso pendiente
 * se refleja como tarjeta con su póster y un deep link `novaiptv://resume`.
 *
 * El valor de las constantes de tipo es el del `TvContract` del framework
 * (TYPE_MOVIE=0, TYPE_TV_EPISODE=3); androidx no las expone.
 */
object WatchNextPublisher {
    private const val TAG = "NovaIPTV"
    private const val MAX_PROGRAMS = 25
    private const val TYPE_MOVIE = 0
    private const val TYPE_TV_EPISODE = 3

    private val syncMutex = Mutex()

    /** La API de programas solo existe en dispositivos con perfil de TV. */
    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /**
     * Mismo filtro que "Seguir viendo" en la app (las películas acabadas caen,
     * los episodios no porque sirven para encadenar el siguiente) y tope del
     * launcher.
     */
    fun candidates(all: List<WatchProgress>): List<WatchProgress> =
        all.filter { progress ->
            progress.fraction > 0 &&
                (progress.fraction < .95 || !progress.id.startsWith("movie:"))
        }
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_PROGRAMS)

    suspend fun sync(context: Context, all: List<WatchProgress>) {
        if (!isSupported(context)) return
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val wanted = candidates(all)
                val wantedIds = wanted.mapTo(mutableSetOf()) { it.id }
                // Filas previas de esta app (el proveedor las limita al paquete
                // llamante; se filtra igualmente por si acaso).
                val existing = HashMap<String, Long>()
                runCatching {
                    resolver.query(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        arrayOf(
                            BaseColumns._ID,
                            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                            TvContractCompat.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                        val provCol = cursor.getColumnIndexOrThrow(
                            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                        )
                        val pkgCol = cursor.getColumnIndexOrThrow(
                            TvContractCompat.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                        )
                        while (cursor.moveToNext()) {
                            if (cursor.getString(pkgCol) == context.packageName) {
                                cursor.getString(provCol)?.let { existing[it] = cursor.getLong(idCol) }
                            }
                        }
                    }
                }
                var removed = 0
                existing.forEach { (providerId, rowId) ->
                    if (providerId !in wantedIds) {
                        runCatching {
                            resolver.delete(TvContractCompat.buildWatchNextProgramUri(rowId), null, null)
                        }
                        removed++
                    }
                }
                var published = 0
                for (progress in wanted) {
                    runCatching {
                        val values = programValues(context, progress)
                        val rowId = existing[progress.id]
                        if (rowId == null) {
                            resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                        } else {
                            resolver.update(
                                TvContractCompat.buildWatchNextProgramUri(rowId),
                                values,
                                null,
                                null,
                            )
                        }
                        published++
                    }
                }
                Log.i(TAG, "watchnext: $published publicadas, $removed retiradas")
            }
        }
    }

    private fun programValues(context: Context, progress: WatchProgress): ContentValues {
        val posterUri = ProgramImages.prepare(context, progress.id, progress.poster)
        val intent = Intent(Intent.ACTION_VIEW, resumeUri(progress.id)).setPackage(context.packageName)
        val program = WatchNextProgram.Builder()
            .setInternalProviderId(progress.id)
            .setTitle(progress.title)
            .setType(if (progress.id.startsWith("movie:")) TYPE_MOVIE else TYPE_TV_EPISODE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(progress.updatedAtMs)
            .setDurationMillis(progress.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .setLastPlaybackPositionMillis(progress.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .setIntent(intent)
            .setSearchable(false)
            .apply { if (posterUri != null) setPosterArtUri(posterUri) }
            .build()
        return program.toContentValues()
    }

    fun resumeUri(progressId: String): Uri =
        Uri.Builder()
            .scheme("novaiptv")
            .authority("resume")
            .appendQueryParameter("progress", progressId)
            .build()
}
