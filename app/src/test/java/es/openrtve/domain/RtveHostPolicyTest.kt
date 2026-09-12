package es.openrtve.domain

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RtveHostPolicyTest {
    private val policy = RtveHostPolicy()

    @Test
    fun `rtve subdomains over https are accepted`() {
        policy.requireAllowed(URI("https://api.rtve.es/api/videos/1.json"))
        policy.requireAllowed(URI("https://www.rtve.es/play/index_apps.json"))
        policy.requireAllowed(URI("https://rtve.es:443/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lookalike domain is rejected`() {
        policy.requireAllowed(URI("https://rtve.es.example.org/api"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cleartext is rejected`() {
        policy.requireAllowed(URI("http://api.rtve.es/api"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `embedded credentials are rejected`() {
        policy.requireAllowed(URI("https://user:secret@api.rtve.es/api"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non standard port is rejected`() {
        policy.requireAllowed(URI("https://api.rtve.es:8443/api"))
    }

    @Test
    fun `sanitize upgrades http images from rtve and drops foreign hosts`() {
        assertEquals("https://img.rtve.es/v/1", policy.sanitize("http://img.rtve.es/v/1"))
        assertNull(policy.sanitize("https://cdn.example.org/v/1"))
        assertFalse(policy.isAllowed("not a url"))
    }
}
