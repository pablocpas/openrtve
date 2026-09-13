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
    private val hostPolicy: RtveHostPolicy = RtveHostPolicy(),
    private val maxPayloadBytes: Long = 4L * 1024 * 1024,
) : TextHttpClient {
    // Las redirecciones se siguen a mano: algún feed de RTVE redirige a `http://`
    // y hay que elevarlo a HTTPS en lugar de aceptarlo o rechazarlo.
    private val client = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun fetch(url: String, etag: String?): TextResponse {
        var target = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            val request = Request.Builder()
                .url(target)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .apply { if (etag != null && hop == 0) header("If-None-Match", etag) }
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 && etag != null -> return TextResponse(body = null, etag = etag)
                    response.isRedirect -> {
                        if (hop == MAX_REDIRECTS) throw IOException("Demasiadas redirecciones")
                        val location = response.header("Location") ?: throw IOException("Redirección sin Location")
                        target = hostPolicy.sanitize(request.url.resolve(location)?.toString() ?: location)
                            ?: throw IOException("Redirección a un host no permitido")
                    }
                    !response.isSuccessful -> throw HttpStatusException(response.code)
                    else -> {
                        val body = response.body ?: throw IOException("Respuesta sin cuerpo")
                        val source = body.source()
                        if (source.request(maxPayloadBytes + 1)) throw IOException("Respuesta demasiado grande")
                        return TextResponse(body = source.readUtf8(), etag = response.header("ETag"))
                    }
                }
            }
        }
        throw IOException("No se pudo completar la petición")
    }

    companion object {
        const val USER_AGENT = "OpenRTVE-Android/0.2"
        private const val MAX_REDIRECTS = 3

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
