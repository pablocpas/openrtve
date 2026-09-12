package es.openrtve.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DrmTokenClientTest {
    private fun fixed(body: String) = object : TextHttpClient {
        override fun fetch(url: String, etag: String?) = TextResponse(body = body, etag = null)
    }

    @Test
    fun `widevine url is extracted from the token document`() {
        val client = DrmTokenClient(fixed("""{"token":"x","widevineURL":"https://example.axprod.net/AcquireLicense?AxDrmMessage=abc","fairplayURL":"https://f"}"""))

        assertEquals(
            "https://example.axprod.net/AcquireLicense?AxDrmMessage=abc",
            client.widevineLicenseUrl("https://api.rtve.es/api/token/1"),
        )
    }

    @Test
    fun `missing or cleartext license urls are rejected`() {
        listOf("""{"token":"x"}""", """{"widevineURL":"http://example.axprod.net/l"}""").forEach { body ->
            try {
                DrmTokenClient(fixed(body)).widevineLicenseUrl("https://api.rtve.es/api/token/1")
                fail("expected rejection for $body")
            } catch (_: IOException) {
            }
        }
    }
}
