package com.algoce95.novaiptv.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun `canal con ids numéricos y favorito por lista`() {
        val channel = Parsers.parseLiveChannel(
            JSONObject(
                """{"stream_id":12,"name":"Canal","stream_icon":"i","category_id":"3",
                   |"epg_title":"Ahora"}""".trimMargin(),
            ),
            favoriteIds = setOf("12"),
        )
        assertEquals(12, channel.channelId)
        assertEquals("Canal", channel.channelName)
        assertEquals("3", channel.categoryId)
        assertEquals("Ahora", channel.currentEpgTitle)
        assertTrue(channel.isFavorite)
    }

    @Test
    fun `canal tolera ids en texto y claves alternativas`() {
        val channel = Parsers.parseLiveChannel(
            JSONObject("""{"channel_id":"7","channel_name":"X","category_id":9}"""),
        )
        assertEquals(7, channel.channelId)
        assertEquals("X", channel.channelName)
        assertEquals("9", channel.categoryId)
        assertFalse(channel.isFavorite)
    }

    @Test
    fun `título EPG decodifica base64 y respeta texto plano`() {
        assertEquals("Hello", Parsers.cleanEpgTitle("SGVsbG8="))
        assertEquals("Ya en claro", Parsers.cleanEpgTitle("Ya en claro"))
        assertEquals("", Parsers.cleanEpgTitle(null))
    }

    @Test
    fun `tiempos EPG aceptan segundos, milis y texto ISO`() {
        assertEquals(1_700_000_000_000L, Parsers.parseEpgTime(1_700_000_000))
        assertEquals(1_700_000_000_000L, Parsers.parseEpgTime(1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, Parsers.parseEpgTime("1700000000"))
    }

    @Test
    fun `temporadas se ordenan numéricamente y el resto alfabético`() {
        assertEquals(
            listOf("1", "2", "10", "x"),
            Parsers.sortSeasonKeys(listOf("10", "x", "2", "1")),
        )
    }

    @Test
    fun `progreso recorta la fracción a 0-1`() {
        assertEquals(0.0, Parsers.parseWatchProgress(JSONObject("{}")).fraction, 0.0)
        val full = WatchProgress("a", "t", 10, 10, 0)
        assertEquals(1.0, full.fraction, 0.0)
    }

    @Test
    fun `película y serie parsean año en texto`() {
        val movie = Parsers.parseVodMovie(
            JSONObject("""{"stream_id":"5","name":"M","year":"2020"}"""),
        )
        assertEquals(5, movie.movieId)
        assertEquals(2020, movie.year)
        val series = Parsers.parseSeries(
            JSONObject("""{"series_id":9,"name":"S","cover":"c"}"""),
        )
        assertEquals(9, series.seriesId)
        assertEquals("c", series.logo)
    }
}
