package es.openrtve.data

import es.openrtve.domain.RtveHostPolicy
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Sin red: un interceptor de OkHttp responde según la URL pedida. */
class SafeHttpsClientTest {
    private val requests = mutableListOf<Request>()
    private val responses = mutableMapOf<String, (Request) -> Response>()

    private val client = SafeHttpsClient(
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    responses[request.url.toString()]?.invoke(request) ?: reply(request, 404, "")
                },
            )
            .build(),
        hostPolicy = RtveHostPolicy(),
        maxPayloadBytes = 64,
    )

    @Test
    fun `a document arrives with its etag and the request identifies the app`() {
        responses[HOME] = { reply(it, 200, """{"ok":true}""", "ETag" to "W/\"v1\"") }

        val response = client.fetch(HOME)

        assertEquals("""{"ok":true}""", response.body)
        assertEquals("W/\"v1\"", response.etag)
        assertEquals(SafeHttpsClient.USER_AGENT, requests.single().header("User-Agent"))
        assertEquals("application/json", requests.single().header("Accept"))
        assertNull("sin etag no se revalida", requests.single().header("If-None-Match"))
    }

    @Test
    fun `an etag is sent as If-None-Match and a 304 comes back as not modified`() {
        responses[HOME] = { reply(it, 304, "") }

        val response = client.fetch(HOME, etag = "W/\"v1\"")

        assertTrue(response.notModified)
        assertEquals("W/\"v1\"", response.etag)
        assertEquals("W/\"v1\"", requests.single().header("If-None-Match"))
    }

    @Test
    fun `a 304 without having sent an etag is an error, not an empty document`() {
        responses[HOME] = { reply(it, 304, "") }
        try {
            client.fetch(HOME)
            fail("un 304 inesperado no puede tratarse como éxito")
        } catch (error: HttpStatusException) {
            assertEquals(304, error.statusCode)
        }
    }

    @Test
    fun `redirects are followed by hand and plain http locations are upgraded to https`() {
        responses[HOME] = { reply(it, 301, "", "Location" to "http://api.rtve.es/api/moved.json") }
        responses["https://api.rtve.es/api/moved.json"] = { reply(it, 200, "moved") }

        val response = client.fetch(HOME, etag = "W/\"v1\"")

        assertEquals("moved", response.body)
        assertEquals(listOf(HOME, "https://api.rtve.es/api/moved.json"), requests.map { it.url.toString() })
        assertNull("la revalidación solo aplica al primer salto", requests[1].header("If-None-Match"))
    }

    @Test
    fun `relative redirects resolve against the request url`() {
        responses[HOME] = { reply(it, 302, "", "Location" to "/play/other.json") }
        responses["https://www.rtve.es/play/other.json"] = { reply(it, 200, "other") }

        assertEquals("other", client.fetch(HOME).body)
    }

    @Test
    fun `a redirect outside rtve is refused before any request is made to it`() {
        responses[HOME] = { reply(it, 302, "", "Location" to "https://evil.example.org/x.json") }
        try {
            client.fetch(HOME)
            fail("no debe seguir a otro host")
        } catch (error: IOException) {
            assertEquals("Redirección a un host no permitido", error.message)
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun `redirect loops give up after a few hops`() {
        responses[HOME] = { reply(it, 302, "", "Location" to HOME) }
        try {
            client.fetch(HOME)
            fail("bucle de redirecciones")
        } catch (error: IOException) {
            assertEquals("Demasiadas redirecciones", error.message)
        }
        assertEquals(4, requests.size)
    }

    @Test
    fun `http errors carry their status code`() {
        responses[HOME] = { reply(it, 503, "down") }
        try {
            client.fetch(HOME)
            fail("503")
        } catch (error: HttpStatusException) {
            assertEquals(503, error.statusCode)
        }
    }

    @Test
    fun `oversized payloads are rejected instead of read into memory`() {
        responses[HOME] = { reply(it, 200, "x".repeat(65)) }
        try {
            client.fetch(HOME)
            fail("demasiado grande")
        } catch (error: IOException) {
            assertEquals("Respuesta demasiado grande", error.message)
        }
        responses[HOME] = { reply(it, 200, "x".repeat(64)) }
        assertEquals(64, client.fetch(HOME).body!!.length)
    }

    @Test
    fun `get unwraps the body and fails on an unexpected 304`() {
        responses[HOME] = { reply(it, 200, "body") }
        assertEquals("body", client.get(HOME))
    }

    private fun reply(request: Request, code: Int, body: String, vararg headers: Pair<String, String>): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message(code.toString())
            .body(body.toResponseBody("application/json".toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

    private companion object {
        const val HOME = "https://www.rtve.es/play/index_apps.json"
    }
}
