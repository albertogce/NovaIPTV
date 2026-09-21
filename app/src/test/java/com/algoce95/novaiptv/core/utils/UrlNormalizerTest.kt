package com.algoce95.novaiptv.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `normalizeUrl deja vacío el vacío`() {
        assertEquals("", UrlNormalizer.normalizeUrl("   "))
    }

    @Test
    fun `normalizeUrl añade esquema y quita barras`() {
        assertEquals("http://example.com:8080", UrlNormalizer.normalizeUrl("example.com:8080///"))
        assertEquals("https://x.com/a", UrlNormalizer.normalizeUrl("https://x.com/a//"))
        assertEquals("http://x.com", UrlNormalizer.normalizeUrl("  http://x.com/ "))
    }

    @Test
    fun `sanitizeStreamUrl oculta usuario y contraseña en rutas directas`() {
        assertEquals(
            "http://h:8080/live/***/***/123.ts",
            UrlNormalizer.sanitizeStreamUrl("http://h:8080/live/user/pass/123.ts"),
        )
        assertEquals(
            "http://h/movie/***/***/456.mp4",
            UrlNormalizer.sanitizeStreamUrl("http://h/movie/user/pass/456.mp4"),
        )
    }

    @Test
    fun `sanitizeStreamUrl oculta parámetros de consulta`() {
        assertEquals(
            "http://h/player_api.php?username=***&password=***&action=x",
            UrlNormalizer.sanitizeStreamUrl(
                "http://h/player_api.php?username=u&password=p&action=x",
            ),
        )
    }

    @Test
    fun `sanitizeStreamUrl deja intacto lo demás`() {
        assertEquals(
            "sin credenciales aquí",
            UrlNormalizer.sanitizeStreamUrl("sin credenciales aquí"),
        )
    }
}
