package es.openrtve.data

import es.openrtve.domain.RtveHostPolicy
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Documento de texto con su validador de caché, o `body == null` si el servidor respondió 304. */
data class TextResponse(val body: String?, val etag: String?) {
    val notModified: Boolean get() = body == null
}

interface TextHttpClient {
    /** Si se pasa [etag], se envía `If-None-Match` y un 304 devuelve `body == null`. */
    fun fetch(url: String, etag: String? = null): TextResponse

    fun get(url: String): String = fetch(url).body ?: throw IOException("Respuesta vacía")
}

/**
 * Cliente HTTPS sobre un [OkHttpClient] compartido con Coil: una sola
 * conexión HTTP/2 para todas las peticiones, gzip y revalidación por ETag.
 * La allowlist se comprueba en cada salto de red, redirecciones incluidas.
 */
class SafeHttpsClient(
    okHttpClient: OkHttpClient,
    private val maxPayloadBytes: Long = 4L * 1024 * 1024,
) : TextHttpClient {
    private val client = okHttpClient.newBuilder()
        .followSslRedirects(false)
        .build()

    override fun fetch(url: String, etag: String?): TextResponse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 && etag != null -> return TextResponse(body = null, etag = etag)
                !response.isSuccessful -> throw HttpStatusException(response.code)
            }
            val body = response.body ?: throw IOException("Respuesta sin cuerpo")
            val source = body.source()
            if (source.request(maxPayloadBytes + 1)) throw IOException("Respuesta demasiado grande")
            return TextResponse(body = source.readUtf8(), etag = response.header("ETag"))
        }
    }

    companion object {
        const val USER_AGENT = "OpenRTVE-Android/0.2"

        /** Cliente base de la app: timeouts y allowlist en cada salto. Lo comparten catálogo e imágenes. */
        fun buildOkHttpClient(hostPolicy: RtveHostPolicy): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addNetworkInterceptor(HostPolicyInterceptor(hostPolicy))
            .build()
    }

    private class HostPolicyInterceptor(private val hostPolicy: RtveHostPolicy) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            try {
                hostPolicy.requireAllowed(URI(chain.request().url.toString()))
            } catch (error: IllegalArgumentException) {
                throw IOException(error.message, error)
            }
            return chain.proceed(chain.request())
        }
    }
}

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")
