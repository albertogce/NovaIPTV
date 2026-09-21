package com.algoce95.novaiptv.data.model

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Writer

/**
 * Parseo en streaming para respuestas grandes (el catálogo revienta el heap
 * con `org.json` porque cada objeto es un mapa con todo boxeado).
 *
 * Solo se retiene lo necesario (modelos u objetos pequeños); nunca el árbol
 * completo. Réplica de la tolerancia de `_asList`/`fromJson` de Dart.
 */
object StreamingParsers {

    /** Array o objeto Xtream → modelos, sin materializar el cuerpo entero. */
    fun <T> parseTopList(stream: InputStream, parse: (JSONObject) -> T): List<T> {
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val json = JsonReader(reader).apply { isLenient = true }
            // Cuerpo vacío: lista vacía (no es error). Un corte a mitad de
            // documento lanza EOFException y SÍ es error (respuesta truncada).
            val first = try {
                json.peek()
            } catch (_: EOFException) {
                return emptyList()
            }
            return when (first) {
                JsonToken.BEGIN_ARRAY -> readModelArray(json, parse)
                JsonToken.BEGIN_OBJECT -> readModelObject(json, parse)
                else -> {
                    json.skipValue()
                    emptyList()
                }
            }
        }
    }

    private fun <T> readModelArray(reader: JsonReader, parse: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                try {
                    out.add(parse(readObject(reader)))
                } catch (e: Exception) {
                    // Elemento roto: se registra y se salta sin tumbar el listado.
                    android.util.Log.w("NovaIPTV", "elemento descartado: $e")
                }
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        return out
    }

    /**
     * Objeto Xtream: se expanden arrays y se toman objetos (equivale a las
     * claves conocidas + valores de `_asList`, en una sola pasada).
     */
    private fun <T> readModelObject(reader: JsonReader, parse: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>()
        reader.beginObject()
        while (reader.hasNext()) {
            reader.nextName()
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            try {
                                out.add(parse(readObject(reader)))
                            } catch (e: Exception) {
                                android.util.Log.w("NovaIPTV", "elemento anidado descartado: $e")
                            }
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    try {
                        out.add(parse(readObject(reader)))
                    } catch (e: Exception) {
                        android.util.Log.w("NovaIPTV", "objeto descartado: $e")
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return out
    }

    /** Un objeto JSON pequeño (nunca el documento entero). */
    fun readObject(reader: JsonReader): JSONObject {
        val obj = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.STRING -> obj.put(name, reader.nextString())
                JsonToken.NUMBER -> {
                    val raw = reader.nextString()
                    obj.put(name, raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw)
                }
                JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                JsonToken.NULL -> {
                    reader.nextNull()
                    obj.put(name, JSONObject.NULL)
                }
                JsonToken.BEGIN_OBJECT -> obj.put(name, readObject(reader))
                JsonToken.BEGIN_ARRAY -> obj.put(name, readRawArray(reader))
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return obj
    }

    private fun readRawArray(reader: JsonReader): JSONArray {
        val array = JSONArray()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> array.put(readObject(reader))
                JsonToken.STRING -> array.put(reader.nextString())
                JsonToken.NUMBER -> {
                    val raw = reader.nextString()
                    array.put(raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw)
                }
                JsonToken.BOOLEAN -> array.put(reader.nextBoolean())
                JsonToken.NULL -> {
                    reader.nextNull()
                    array.put(JSONObject.NULL)
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return array
    }

    /**
     * `get_series_info` puede ser grande: se reconstruye solo `info` y
     * `episodes` (lo único que usa la pantalla) sin el cuerpo en memoria.
     */
    fun rebuildSeriesInfo(stream: InputStream): JSONObject {
        val out = JSONObject()
        try {
            InputStreamReader(stream, Charsets.UTF_8).use { reader -> readSeriesInfoInto(reader, out) }
        } catch (_: Exception) {
            // Respuesta rota: se devuelve lo acumulado (posiblemente vacío).
        }
        return out
    }

    private fun readSeriesInfoInto(reader: InputStreamReader, out: JSONObject) {
        val json = JsonReader(reader).apply { isLenient = true }
        if (json.peek() != JsonToken.BEGIN_OBJECT) return
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "info" -> {
                    out.put(
                        "info",
                        if (json.peek() == JsonToken.BEGIN_OBJECT) {
                            readObject(json)
                        } else {
                            json.skipValue()
                            JSONObject()
                        },
                    )
                }
                "episodes" -> out.put("episodes", rebuildEpisodes(json))
                else -> json.skipValue()
            }
        }
        json.endObject()
    }

    private fun rebuildEpisodes(reader: JsonReader): JSONObject {
        val episodes = JSONObject()
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return episodes
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val season = reader.nextName()
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue()
                continue
            }
            val array = JSONArray()
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    try {
                        array.put(readObject(reader))
                    } catch (_: Exception) {
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endArray()
            episodes.put(season, array)
        }
        reader.endObject()
        return episodes
    }

    /** Escritura en streaming (la caché también puede ser grande). */
    fun writeJsonArray(writer: Writer, items: List<JSONObject>) {
        val jsonWriter = JsonWriter(writer)
        jsonWriter.beginArray()
        for (item in items) {
            jsonWriter.jsonValue(item.toString())
        }
        jsonWriter.endArray()
        jsonWriter.flush()
    }
}
