package com.algoce95.novaiptv.core.utils

/** Paridad con `lib/core/utils/url_normalizer.dart` de Flutter. */
object UrlNormalizer {
    fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (normalized.isEmpty()) return ""
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        return normalized.trimEnd('/')
    }

    private val queryCredentials =
        Regex("([?&](?:username|password)=)[^&\\s'\"]+", RegexOption.IGNORE_CASE)
    private val pathCredentials =
        Regex("((?:live|movie|series|timeshift)/)[^/]+/[^/]+(/[^'\"\\s]*)")

    /** Oculta usuario y contraseña antes de mostrar texto o registrarlo. */
    fun sanitizeStreamUrl(text: String): String {
        val withoutQuery = queryCredentials.replace(text) { "${it.groupValues[1]}***" }
        return pathCredentials.replace(withoutQuery) {
            "${it.groupValues[1]}***/***${it.groupValues[2]}"
        }
    }
}
