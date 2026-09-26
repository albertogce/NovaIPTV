package com.algoce95.novaiptv.data.tv

import com.algoce95.novaiptv.data.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextPublisherTest {

    private fun progress(
        id: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long = 0L,
    ) = WatchProgress(
        id = id,
        title = "t-$id",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtMs = updatedAtMs,
    )

    @Test
    fun `pelicula acabada no se publica pero episodio si`() {
        val movie = progress("movie:1", 9_900, 10_000)
        val episode = progress("series:2", 9_900, 10_000)
        val ids = WatchNextPublisher.candidates(listOf(movie, episode)).map { it.id }
        assertFalse("movie:1" in ids)
        assertTrue("series:2" in ids)
    }

    @Test
    fun `sin progreso y a medias se filtran y se conservan segun umbral`() {
        val notStarted = progress("movie:3", 0, 10_000)
        val started = progress("movie:4", 5_000, 10_000)
        val ids = WatchNextPublisher.candidates(listOf(notStarted, started)).map { it.id }
        assertEquals(listOf("movie:4"), ids)
    }

    @Test
    fun `orden por ultimo uso y tope del launcher`() {
        val items = (1..40).map { progress("movie:$it", 5_000, 10_000, updatedAtMs = it.toLong()) }
        val result = WatchNextPublisher.candidates(items.shuffled())
        assertEquals(25, result.size)
        assertEquals("movie:40", result.first().id)
        assertEquals("movie:16", result.last().id)
    }
}
