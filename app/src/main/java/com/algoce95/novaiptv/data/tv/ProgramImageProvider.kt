package com.algoce95.novaiptv.data.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Sirve las tarjetas 16:9 del inicio de Android TV. El launcher (proceso
 * `com.android.tv`) no puede cargar URLs remotas ni ficheros privados: solo
 * lee `content://` expuestos por la app dueña del programa.
 */
class ProgramImageProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.algoce95.novaiptv.programimages"
        private const val CARD = 1

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "card/*", CARD)
        }

        // Nombres generados por ProgramImages: solo caracteres seguros.
        private val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,96}")
    }

    private fun resolveFile(uri: Uri): File? {
        if (MATCHER.match(uri) != CARD) return null
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (!name.matches(SAFE_NAME)) return null
        val dir = ProgramImages.dir(ctx)
        val file = File(dir, name).canonicalFile
        // Barrera contra path traversal: el fichero debe quedar dentro del dir.
        if (!file.path.startsWith(dir.canonicalFile.path + File.separator)) return null
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (MATCHER.match(uri) == CARD) "image/jpeg" else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = resolveFile(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/** Prepara los JPEG 1280x720 que consume el launcher desde [ProgramImageProvider]. */
object ProgramImages {
    private const val CARD_W = 1280
    private const val CARD_H = 720

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun dir(context: Context): File =
        File(context.cacheDir, "tv_cards").apply { mkdirs() }

    /**
     * Descarga [posterUrl], la recorta a 16:9 y la guarda como tarjeta. Devuelve
     * el `content://` para el programa o null si no hay póster o falla todo
     * (la tarjeta del launcher se muestra igualmente sin imagen).
     */
    fun prepare(context: Context, id: String, posterUrl: String?): Uri? {
        if (posterUrl.isNullOrBlank()) return null
        val name = "card_${id.hashCode().toUInt().toString(16)}_${posterUrl.hashCode().toUInt().toString(16)}.jpg"
        val file = File(dir(context), name)
        if (file.isFile && file.length() > 0) return uriFor(name)
        return runCatching {
            val request = Request.Builder().url(posterUrl).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                val card = centerCrop16x9(src)
                if (card !== src) src.recycle()
                // Escritura atómica: el launcher no debe ver un JPEG a medias.
                val tmp = File(file.parentFile, "$name.tmp-${System.nanoTime()}")
                tmp.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                card.recycle()
                if (tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) {
                    uriFor(name)
                } else {
                    tmp.delete()
                    null
                }
            }
        }.getOrNull()
    }

    fun uriFor(name: String): Uri =
        Uri.Builder().scheme("content").authority(ProgramImageProvider.AUTHORITY)
            .appendPath("card").appendPath(name).build()

    private fun centerCrop16x9(src: Bitmap): Bitmap {
        val target = CARD_W.toFloat() / CARD_H
        var cropW = src.width
        var cropH = src.height
        if (cropW.toFloat() / cropH > target) {
            cropW = max(1, (cropH * target).toInt())
        } else {
            cropH = max(1, (cropW / target).toInt())
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        return try {
            Bitmap.createScaledBitmap(cropped, CARD_W, CARD_H, true)
        } finally {
            if (cropped != src) cropped.recycle()
        }
    }
}
