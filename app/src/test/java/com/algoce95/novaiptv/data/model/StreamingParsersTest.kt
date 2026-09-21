package com.algoce95.novaiptv.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import com.google.gson.stream.JsonReader

class StreamingParsersTest {

    private fun streamOf(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private val channelsJson = """
        [{"stream_id":1,"name":"Uno","stream_icon":"i1","category_id":"7"},
         {"stream_id":"2","name":"Dos","stream_icon":"i2","category_id":7}]
    """.trimIndent()

    @Test
    fun `array directo con ids mixtos`() {
        val channels = StreamingParsers.parseTopList(streamOf(channelsJson), Parsers::parseLiveChannel)
        assertEquals(2, channels.size)
        assertEquals(1, channels[0].channelId)
        assertEquals("7", channels[1].categoryId)
        assertEquals("Dos", channels[1].channelName)
    }

    @Test
    fun `objeto con clave conocida se expande`() {
        val wrapped = """{"live_streams":$channelsJson}"""
        val channels = StreamingParsers.parseTopList(streamOf(wrapped), Parsers::parseLiveChannel)
        assertEquals(2, channels.size)
    }

    @Test
    fun `objeto con valores sueltos se toman como elementos`() {
        val wrapped = """{"a":{"category_id":"1","category_name":"A"},"b":[1,2]}"""
        val cats = StreamingParsers.parseTopList(streamOf(wrapped), Parsers::parseLiveCategory)
        assertEquals(1, cats.size)
        assertEquals("A", cats[0].categoryName)
    }

    @Test
    fun `elementos rotos se saltan sin tumbar la lista`() {
        val broken = """[{"stream_id":1,"name":"Ok"},42,"texto",{"stream_id":3}]"""
        val channels = StreamingParsers.parseTopList(streamOf(broken), Parsers::parseLiveChannel)
        assertEquals(2, channels.size)
        assertEquals(3, channels[1].channelId)
    }

    @Test
    fun `escritura y lectura hacen roundtrip`() {
        val original = StreamingParsers.parseTopList(streamOf(channelsJson), Parsers::parseLiveChannel)
        val writer = StringWriter()
        StreamingParsers.writeJsonArray(writer, original.map { it.toCacheJson() })
        val roundtripped = StreamingParsers.parseTopList(
            ByteArrayInputStream(writer.toString().toByteArray(Charsets.UTF_8)),
            Parsers::parseLiveChannel,
        )
        assertEquals(original.map { it.channelId }, roundtripped.map { it.channelId })
        assertEquals(original.map { it.channelName }, roundtripped.map { it.channelName })
    }

    @Test
    fun `series_info conserva info y episodios`() {
        val payload = """
            {"info":{"plot":"Trama"},"episodes":{"1":[{"id":11,"title":"E1"}],"2":[]},
             "seasons":[],"extra_ignored":{"big":[1,2,3]}}
        """.trimIndent()
        val rebuilt = StreamingParsers.rebuildSeriesInfo(streamOf(payload))
        assertEquals("Trama", rebuilt.getJSONObject("info").getString("plot"))
        val episodes = rebuilt.getJSONObject("episodes")
        assertEquals(1, episodes.getJSONArray("1").length())
        assertEquals("E1", episodes.getJSONArray("1").getJSONObject(0).getString("title"))
        assertTrue(rebuilt.opt("seasons") == null)
    }

    @Test
    fun `cuerpo vacío o escalar da lista vacía`() {
        assertTrue(StreamingParsers.parseTopList(streamOf(""), Parsers::parseLiveChannel).isEmpty())
        assertTrue(
            StreamingParsers.parseTopList(streamOf("{\"ok\":true}"), Parsers::parseLiveChannel).isEmpty(),
        )
    }

    @Test(timeout = 30_000)
    fun `listado grande se parsea sin materializar el árbol`() {        val big = StringBuilder("[")
        for (i in 1..5000) {
            if (i > 1) big.append(',')
            big.append(
                """{"stream_id":$i,"name":"Canal $i con un nombre algo largo","stream_icon":"http://h/i/$i.png","category_id":"${i % 25}","epg_title":"Programa en emisión ahora mismo"}""",
            )
        }
        big.append(']')
        val channels = StreamingParsers.parseTopList(streamOf(big.toString()), Parsers::parseLiveChannel)
        assertEquals(5000, channels.size)
        assertEquals("Canal 1 con un nombre algo largo", channels[0].channelName)
        assertEquals("0", channels[4999].categoryId)
        assertEquals("Programa en emisión ahora mismo", channels[2500].currentEpgTitle)
    }

    @Test
    fun `nextString tolera tokens numéricos`() {
        val reader = JsonReader(StringReader("[1478,4.5]"))
        reader.isLenient = true
        reader.beginArray()
        assertEquals("1478", reader.nextString())
        assertEquals("4.5", reader.nextString())
        reader.endArray()
    }

    @Test
    fun `objeto realista con array anidado, nulos, escapes y doubles`() {
        val json = """[{"num":1,"name":"ES 4k- LA 1","stream_type":"live","stream_id":965558,"stream_icon":"http:\/\/x\/logos\/la1hd.png","epg_channel_id":"la1.es","added":"1696948941","is_adult":0,"category_id":"1478","category_ids":[1478],"custom_sid":null,"tv_archive":0,"direct_source":"","rating":4.5}]"""
        val channels = StreamingParsers.parseTopList(streamOf(json), Parsers::parseLiveChannel)
        assertEquals(1, channels.size)
        assertEquals(965558, channels[0].channelId)
        assertEquals("http://x/logos/la1hd.png", channels[0].channelLogo)
        assertEquals("1478", channels[0].categoryId)
    }
}
