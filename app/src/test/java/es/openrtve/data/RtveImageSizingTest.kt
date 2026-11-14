package es.openrtve.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Las mismas variantes que pide la web oficial: casi siempre ya están en la caché de RTVE. */
class RtveImageSizingTest {
    @Test
    fun `videos use 400 wide on cards and 1600 on headers, keeping the editorial artwork`() {
        assertEquals("https://img.rtve.es/v/9/?w=400", RtveImageSizing.sized("https://img.rtve.es/v/9/?w=960", widthPx = 560))
        assertEquals("https://img.rtve.es/v/9/?w=400", RtveImageSizing.sized("https://img.rtve.es/v/9/horizontal?w=960", widthPx = 300))
        assertEquals("https://img.rtve.es/v/9/horizontal2?w=400", RtveImageSizing.sized("https://img.rtve.es/v/9/horizontal2?w=960", widthPx = 560))
        assertEquals("https://img.rtve.es/v/9/horizontal2?w=1600", RtveImageSizing.sized("https://img.rtve.es/v/9/horizontal2?w=1600", widthPx = 1080))
        assertEquals("https://img.rtve.es/v/9/?w=1600", RtveImageSizing.sized("https://img.rtve.es/v/9/?w=1600", widthPx = 1920))
    }

    @Test
    fun `posters and squares use 400 high`() {
        assertEquals("https://img.rtve.es/v/9/vertical/?h=400", RtveImageSizing.sized("https://img.rtve.es/v/9/vertical?w=480", 360))
        assertEquals("https://img.rtve.es/v/9/vertical2/?h=400", RtveImageSizing.sized("https://img.rtve.es/v/9/vertical2?w=480", 360))
        assertEquals("https://img.rtve.es/p/123/imgposter/?h=400", RtveImageSizing.sized("https://img.rtve.es/p/123?imgProgApi=imgPoster&w=480", 360))
        assertEquals("https://img.rtve.es/p/123/imgbackground/?h=400", RtveImageSizing.sized("https://img.rtve.es/p/123?imgProgApi=imgBackground&w=480", 360))
    }

    @Test
    fun `programs use the official cover sizes and logo`() {
        assertEquals("https://img.rtve.es/p/123/imgportada/?w=400", RtveImageSizing.sized("https://img.rtve.es/p/123?imgProgApi=imgPortada&w=960", 560))
        assertEquals("https://img.rtve.es/p/123/imgportada/?w=1600", RtveImageSizing.sized("https://img.rtve.es/p/123?imgProgApi=imgPortada&w=1600", 1080))
        assertEquals("https://img.rtve.es/p/123/?w=320&imgProgApi=logo", RtveImageSizing.sized("https://img.rtve.es/p/123?imgProgApi=logo", 200))
    }

    @Test
    fun `originals go through the web resizer at the same two widths`() {
        assertEquals(
            "https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fimagenes%2Fcine-de-siempre%2F1768405234614.jpg",
            RtveImageSizing.sized("https://img.rtve.es/imagenes/cine-de-siempre/1768405234614.jpg", widthPx = 300),
        )
        assertEquals(
            "las categorías de Explorar",
            "https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fcss%2Fmoviles%2Fi%2Frtveplay%2Fdispositivos%2Ftematica-cine.jpg",
            RtveImageSizing.sized("https://img.rtve.es/css/moviles/i/rtveplay/dispositivos/tematica-cine.jpg", widthPx = 480),
        )
        assertEquals(
            "una URL del redimensionador solo cambia de tamaño",
            "https://img.rtve.es/i/?w=1600&i=1665049167787.jpg",
            RtveImageSizing.sized("https://img.rtve.es/i/?w=400&i=1665049167787.jpg", widthPx = 1080),
        )
    }

    @Test
    fun `static files, other hosts and named program files are left alone`() {
        assertNull(RtveImageSizing.sized("https://img.rtve.es/resources/x/1.jpg", 300))
        assertNull(RtveImageSizing.sized("https://img2.rtve.es/p/1?w=1", 300))
        // RTVE responde 503 a `?w=` en estos y 404 desde su redimensionador.
        assertNull(RtveImageSizing.sized("https://img.rtve.es/p/bluey_1028792.png", 300))
        assertNull(RtveImageSizing.sized("https://img.rtve.es/p/1?w=1", 0))
    }
}
