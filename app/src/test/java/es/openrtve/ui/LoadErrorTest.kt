package es.openrtve.ui

import es.openrtve.data.HttpStatusException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadErrorTest {
    @Test
    fun `network exceptions map to user facing categories`() {
        assertEquals(LoadError.Offline, UnknownHostException("api.rtve.es").toLoadError())
        assertEquals(LoadError.Timeout, SocketTimeoutException().toLoadError())
        assertEquals(LoadError.Http(503), HttpStatusException(503).toLoadError())
        assertEquals(LoadError.Unknown, IOException("otro").toLoadError())
        assertEquals(LoadError.Unknown, IllegalStateException().toLoadError())
    }
}
