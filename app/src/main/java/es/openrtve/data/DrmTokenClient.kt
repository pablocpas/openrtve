package es.openrtve.data

import java.io.IOException
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Obtiene la URL de licencia Widevine de `api/token/{id}`. La respuesta incluye
 * un token de derechos temporal dentro de la URL: no se cachea ni se registra.
 */
class DrmTokenClient(
    private val httpClient: TextHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun widevineLicenseUrl(tokenUrl: String): String {
        val root = json.parseToJsonElement(httpClient.get(tokenUrl)).jsonObject
        val url = (root["widevineURL"] as? JsonPrimitive)?.contentOrNull?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IOException("La respuesta no incluye licencia Widevine")
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw IOException("URL de licencia no admisible")
        }
        return url
    }
}
