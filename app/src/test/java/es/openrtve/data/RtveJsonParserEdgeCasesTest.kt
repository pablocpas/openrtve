package es.openrtve.data

import es.openrtve.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RtveJsonParserEdgeCasesTest {
    private val parser = RtveJsonParser()

    @Test
    fun `a portada without rows or title still parses`() {
        val feed = parser.parseHome("""{"id":"1"}""")
        assertEquals("RTVE Play", feed.title)
        assertTrue(feed.rows.isEmpty())
    }

    @Test
    fun `rows without order go last and keep their relative position`() {
        val feed = parser.parseHome(
            """{"rows":[
              {"title":"sin orden A","urlContent":"https://api.rtve.es/a.json"},
              {"title":"dos","orden":2,"urlContent":"https://api.rtve.es/b.json"},
              {"title":"sin orden B","urlContent":"https://api.rtve.es/c.json"},
              {"title":"uno","orden":1,"urlContent":"https://api.rtve.es/d.json"}
            ]}""",
        )
        assertEquals(listOf("uno", "dos", "sin orden A", "sin orden B"), feed.rows.map { it.title })
    }

    @Test
    fun `modules accept items at the root, under page, or nested in a single collection`() {
        val root = parser.parseModule("""{"items":[{"id":"1","contentType":"video","title":"a"}]}""", "Fila")
        val paged = parser.parseModule("""{"page":{"items":[{"id":"2","contentType":"video","title":"b"}]}}""", "Fila")
        val empty = parser.parseModule("""{"page":{}}""", "Fila")

        assertEquals(listOf("1"), root.items.map { it.id })
        assertEquals(listOf("2"), paged.items.map { it.id })
        assertTrue(empty.items.isEmpty())
        assertEquals("Fila", empty.title)
    }

    @Test
    fun `unusable documents raise instead of producing half a sheet`() {
        for (raw in listOf("""{"page":{"items":[]}}""", """{}""")) {
            try {
                parser.parseProgram(raw)
                fail("programa sin ficha")
            } catch (_: IllegalArgumentException) {
            }
            try {
                parser.parseVideo(raw)
                fail("vídeo sin ficha")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `sprite vtt accepts minutes-only timestamps and skips malformed cues`() {
        val sprite = parser.parseSpriteVtt(
            """
            WEBVTT

            00:00.000 --> 00:10.000
            sprite.jpg#xywh=0,0,160,90

            00:10.000 --> 01:00:00.500
            sprite.jpg#xywh=160,0,160,90

            garbage --> 00:20.000
            sprite.jpg#xywh=1,2,3,4

            00:20.000 --> 00:30.000
            sprite.jpg
            """.trimIndent(),
            "https://img.rtve.es/sprite.jpg",
        )

        assertEquals(2, sprite.cues.size)
        assertEquals(0L, sprite.cues[0].startMs)
        assertEquals(10_000L, sprite.cues[0].endMs)
        assertEquals(3_600_500L, sprite.cues[1].endMs)
    }

    @Test
    fun `sprite info is only used when available and from rtve`() {
        assertNull(parser.parseSpriteInfo("""{"state":"PROCESSING","sprite_url":"https://videopreviews.rtve.es/s.jpg","vtt_url":"https://videopreviews.rtve.es/s.vtt"}"""))
        assertNull(parser.parseSpriteInfo("""{"state":"AVAILABLE","sprite_url":"https://evil.example.org/s.jpg","vtt_url":"https://videopreviews.rtve.es/s.vtt"}"""))
        assertEquals(
            "https://videopreviews.rtve.es/s.jpg" to "https://videopreviews.rtve.es/s.vtt",
            parser.parseSpriteInfo("""{"state":"AVAILABLE","sprite_url":"http://videopreviews.rtve.es/s.jpg","vtt_url":"https://videopreviews.rtve.es/s.vtt"}"""),
        )
    }

    @Test
    fun `live category is title cased only when it arrives shouting`() {
        val module = parser.parseModule(
            """{"items":[
              {"id":"1","idAsset":"a1","tipo":"broadcast","titulo":"Uno","antetitulo":"LA VUELTA 2026"},
              {"id":"2","idAsset":"a2","tipo":"broadcast","titulo":"Dos","antetitulo":"Telediario 2"}
            ]}""",
            "",
        )
        assertEquals(ContentKind.LIVE, module.items[0].kind)
        assertEquals("La Vuelta 2026", module.items[0].live!!.category)
        assertEquals("Telediario 2", module.items[1].live!!.category)
    }

    @Test
    fun `items without any usable id or title are skipped rather than crashing the row`() {
        val module = parser.parseModule(
            """{"items":[
              {"contentType":"video","title":"sin id"},
              {"id":"3","contentType":"video"},
              {"id":"4","contentType":"video","title":"ok"},
              "no es un objeto"
            ]}""",
            "",
        )
        assertEquals(listOf("4"), module.items.map { it.id })
    }

    @Test
    fun `search tolerates missing blocks`() {
        val results = parser.parseSearch("""{"programs":{}}""")
        assertTrue(results.isEmpty)
    }
}
