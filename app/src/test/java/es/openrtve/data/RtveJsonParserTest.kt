package es.openrtve.data

import es.openrtve.domain.ContentKind
import es.openrtve.domain.RowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtveJsonParserTest {
    private val parser = RtveJsonParser()

    @Test
    fun `home rows are sorted, get stable ids and unknown presentations are preserved`() {
        val result = parser.parseHome(
            """
            {
              "id": "16",
              "title": "Portada",
              "rows": [
                {"title":"Dos","orden":2,"moduleType":"Future","tipo":"Nuevo"},
                {"title":"Uno","orden":1,"moduleType":"Collection","urlContent":"https://api.rtve.es/api/collection/1.json"},
                {"title":"Ajena","orden":3,"urlContent":"https://evil.example.org/collection/1.json"},
                {"title":"Enlaces","orden":4,"tipo":"links","urlContent":"https://www.rtve.es/api/tematicas/1/links.json"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Uno", "Dos", "Ajena"), result.rows.map { it.title })
        assertEquals(listOf(0, 1, 2), result.rows.map { it.id })
        assertEquals("Future", result.rows[1].moduleType)
        assertEquals("Nuevo", result.rows[1].presentation)
        assertNull("las URLs fuera de la allowlist se descartan", result.rows[2].contentUrl)
    }

    @Test
    fun `row presentation maps to a layout`() {
        val result = parser.parseHome(
            """{"rows":[
              {"title":"a","orden":1,"tipo":"ColeccionSuperDestacado"},
              {"title":"b","orden":2,"tipo":"ColeccionPoster"},
              {"title":"c","orden":3,"tipo":"ColeccionCuadradoPeq"},
              {"title":"d","orden":4,"tipo":"directosTV"},
              {"title":"e","orden":5,"tipo":"AlgoNuevo"}
            ]}""",
        )

        assertEquals(
            listOf(RowLayout.HERO, RowLayout.POSTER, RowLayout.SQUARE, RowLayout.LANDSCAPE, RowLayout.LANDSCAPE),
            result.rows.map { it.layout },
        )
    }

    @Test
    fun `search results are split into programs and videos`() {
        val results = parser.parseSearch(
            """{
              "programs":{"page":{"items":[{"id":"1726","contentType":"programa","title":"Telediario Internacional"}]}},
              "videos":{"page":{"items":[{"id":"1","contentType":"video","title":"Uno"},{"id":"1","contentType":"video","title":"Uno"}]}}
            }""",
        )

        assertEquals(listOf("1726"), results.programs.map { it.id })
        assertEquals(ContentKind.PROGRAM, results.programs.single().kind)
        assertEquals(1, results.videos.size)
    }

    @Test
    fun `quick filters keep only allowed urls`() {
        val filters = parser.parseQuickFilters(
            """{"items":[
              {"nav-pag_name":"MÁS BUSCADOS","nav-pag_url":"https://recomsys.rtve.es/recommendation/tops?recoId=x"},
              {"nav-pag_name":"AJENO","nav-pag_url":"https://example.org/x"}
            ]}""",
        )

        assertEquals(listOf("MÁS BUSCADOS"), filters.map { it.title })
    }

    @Test
    fun `explore keeps public portadas from the menu and appends radio`() {
        val groups = parser.parseExplore(
            """{
              "television":{"menuBloques":[
                {"title":"Bloque de contenidos","menuItems":[
                  {"title":"DIRECTOS","tipo":"directos"},
                  {"title":"CINE","tipo":"portada","imgBackground":"http://img.rtve.es/cine.jpg","urlContent":"https://www.rtve.es/play/cine/index_apps.json","subscriptor":false},
                  {"title":"SERIES","tipo":"portada","urlContent":"https://www.rtve.es/play/playplus/series/index_apps.json","subscriptor":true},
                  {"title":"CLAN","tipo":"intentApp"}
                ]},
                {"title":"Bloque principal","menuItems":[{"title":"MI CUENTA","tipo":"micuenta"}]}
              ]},
              "radio":{"menuBloques":[{"menuItems":[{"title":"PODCAST","tipo":"portada","urlContent":"https://rtve.es/play/radio/podcasts/index_apps.json"}]}]}
            }""",
        )

        assertEquals(listOf("Bloque de contenidos", "Radio"), groups.map { it.title })
        assertEquals(listOf("CINE"), groups[0].categories.map { it.title })
        assertEquals("https://img.rtve.es/cine.jpg", groups[0].categories.single().imageUrl)
        assertEquals(listOf("Radio", "PODCAST"), groups[1].categories.map { it.title })
    }

    @Test
    fun `collection is flattened and last multimedia becomes playable`() {
        val result = parser.parseModule(
            raw = """
                {
                  "page": {
                    "items": [{
                      "id": 10,
                      "title": "Colección",
                      "collectionItems": [{
                        "id": 135930,
                        "name": "Programa",
                        "contentType": "video",
                        "requireLogged": false,
                        "imgPoster": "http://img.rtve.es/p/135930?imgProgApi=imgPoster",
                        "lastMultimedia": {
                          "id": "17222239",
                          "title": "Último episodio",
                          "contentType": "video",
                          "allowedInCountry": true,
                          "hasDRM": false
                        }
                      }]
                    }]
                  }
                }
            """.trimIndent(),
            fallbackTitle = "Destacados",
        )

        assertEquals(1, result.items.size)
        with(result.items.single()) {
            assertEquals("135930", id)
            assertEquals("17222239", playbackId)
            assertEquals("Programa", title)
            assertEquals("Último episodio", subtitle)
            assertEquals(ContentKind.PROGRAM, kind)
            assertEquals("https://img.rtve.es/p/135930?imgProgApi=imgPortada&w=960", imageUrl)
            assertEquals("https://img.rtve.es/p/135930?imgProgApi=imgPoster&w=480", posterUrl)
            assertTrue(allowedInCountry == true)
            assertFalse(loginRequired)
            assertFalse(drm)
        }
    }

    @Test
    fun `collection title fills in when the home row has none`() {
        val raw = """{"page":{"items":[{"id":1,"title":"Lo más destacado","name":"Play Portada > Destacados","collectionItems":[]}]}}"""

        assertEquals("Lo más destacado", parser.parseModule(raw, fallbackTitle = "").title)
        assertEquals("Fila", parser.parseModule(raw, fallbackTitle = "Fila").title)
    }

    @Test
    fun `program detail exposes seasons and description`() {
        val detail = parser.parseProgram(
            """
            {"page":{"items":[{
              "id":"178291","name":"La amiga estupenda","contentType":"programa",
              "description":"<p>Elena y Lila</p>","emission":"Sábado a las 22:00",
              "imgPortada":"https://img.rtve.es/imagenes/x.jpg",
              "seasons":[{"shorttitle":"T1","longTitle":"Temporada 1","id":1000013,"orden":1,"numEpisodes":8}]
            }]}}
            """.trimIndent(),
        )

        assertEquals("La amiga estupenda", detail.title)
        assertEquals("Sábado a las 22:00", detail.emission)
        assertEquals("https://img.rtve.es/imagenes/x.jpg", detail.imageUrl)
        assertEquals(listOf("1000013"), detail.seasons.map { it.id })
        assertEquals(8, detail.seasons.single().episodeCount)
    }

    @Test
    fun `video page keeps pagination and episode metadata`() {
        val page = parser.parseVideoPage(
            """
            {"page":{"number":2,"totalPages":72,"items":[{
              "id":"17222216","title":"Telediario - 21 horas","contentType":"video",
              "duration":3103400,"publicationDate":"11-09-2026 21:00:00","episode":3,"temporada":"Temporada 1",
              "requireLogged":false,"hasDRM":true,"allowedInCountry":true
            }]}}
            """.trimIndent(),
        )

        assertEquals(2, page.page)
        assertEquals(72, page.totalPages)
        assertTrue(page.hasMore)
        with(page.items.single()) {
            assertEquals("17222216", playbackId)
            assertEquals(3103400L, durationMs)
            assertEquals("11-09-2026 21:00:00", publicationDate)
            assertEquals(3, episode)
            assertEquals("Temporada 1", seasonTitle)
            assertTrue(needsAccount)
        }
    }

    @Test
    fun `container programs expose the movie inside instead of the container`() {
        val result = parser.parseModule(
            raw = """
                {"page":{"items":[{"id":1,"collectionItems":[
                  {"id":"77590","name":"Cine internacional","contentType":"video","programType":"Contenedor Películas",
                   "lastMultimedia":{"id":"16368062","title":"La favorita","contentType":"video","subType":{"name":"Película"},"previews":{"vertical":"https://img.rtve.es/v.jpg"}}},
                  {"id":"77590","name":"Cine internacional","contentType":"video","programType":"Contenedor Películas",
                   "lastMultimedia":{"id":"17189185","title":"Otra peli","contentType":"video"}}
                ]}]}}
            """.trimIndent(),
            fallbackTitle = "Cine",
        ).items

        assertEquals(listOf("16368062", "17189185"), result.map { it.id })
        with(result[0]) {
            assertEquals(ContentKind.VIDEO, kind)
            assertEquals("La favorita", title)
            assertEquals("Cine internacional", subtitle)
            assertEquals("77590", programId)
            assertEquals("https://img.rtve.es/v/16368062/vertical?w=480", posterUrl)
            assertEquals("https://img.rtve.es/v/16368062/horizontal2?w=960", imageUrl)
        }
    }

    @Test
    fun `a program repeated across a collection means each item is its media`() {
        val result = parser.parseModule(
            raw = """
                {"page":{"items":[{"id":1,"collectionItems":[
                  {"id":"1000","name":"Vuelta a España","contentType":"video","lastMultimedia":{"id":"1","title":"Etapa 1","contentType":"video"}},
                  {"id":"1000","name":"Vuelta a España","contentType":"video","lastMultimedia":{"id":"2","title":"Etapa 2","contentType":"video"}},
                  {"id":"2000","name":"Telediario","contentType":"video","lastMultimedia":{"id":"3","title":"TD 21h","contentType":"video"}},
                  {"id":"n1","title":"Una noticia","contentType":"noticia"}
                ]}]}}
            """.trimIndent(),
            fallbackTitle = "",
        ).items

        assertEquals(listOf("1", "2", "2000"), result.map { it.id })
        assertEquals(listOf(ContentKind.VIDEO, ContentKind.VIDEO, ContentKind.PROGRAM), result.map { it.kind })
        assertEquals("Etapa 1", result[0].title)
        assertEquals("Vuelta a España", result[0].subtitle)
        assertEquals("https://img.rtve.es/v/1/horizontal2?w=960", result[0].imageUrl)
        assertEquals("Telediario", result[2].title)
    }

    @Test
    fun `fast channels without title get one from description or permalink`() {
        val result = parser.parseModule(
            raw = """{"page":{"items":[
              {"id":"21007","idAsset":"1","tipo":"broadcast","contentType":"directo","descripcion":"Canal RTVE La Promesa: disfruta de capítulos"},
              {"id":"24156","idAsset":"2","tipo":"broadcast","contentType":"directo","permalink":"play-cuentame"}
            ]}}""",
            fallbackTitle = "",
        ).items

        assertEquals(listOf("Canal RTVE La Promesa", "Play Cuentame"), result.map { it.title })
    }

    @Test
    fun `non catalog rows are dropped from portadas`() {
        val rows = parser.parseHome(
            """{"rows":[{"title":"a","orden":1,"tipo":"noticias"},{"title":"b","orden":2,"tipo":"Parrilla"},{"title":"c","orden":3,"tipo":"videos"}]}""",
        ).rows

        assertEquals(listOf("c"), rows.map { it.title })
    }

    @Test
    fun `videos and programs without images fall back to the image service`() {
        val items = parser.parseModule(
            raw = """{"page":{"items":[{"id":"1","title":"v","contentType":"video"},{"id":"2","name":"p","contentType":"programa"}]}}""",
            fallbackTitle = "",
        ).items

        assertEquals("https://img.rtve.es/v/1/horizontal2?w=960", items[0].imageUrl)
        assertEquals("https://img.rtve.es/v/1/vertical?w=480", items[0].posterUrl)
        assertEquals("https://img.rtve.es/p/2?imgProgApi=imgPoster&w=480", items[1].posterUrl)
        assertEquals("https://img.rtve.es/p/2?imgProgApi=imgBackground&w=480", items[1].squareUrl)
    }

    @Test
    fun `video detail collects the movie sheet`() {
        val detail = parser.parseVideo(
            """
            {"page":{"items":[{
              "id":"16368062","title":"La favorita","contentType":"video","duration":6493100,
              "description":"<p>Reina Ana</p>","promoDesc":"Comedia negra","subType":{"id":1,"name":"Película"},
              "previews":{"horizontal":null,"horizontal2":"https://img.rtve.es/h2.jpg","vertical":"https://img.rtve.es/v.jpg"},
              "casting":"Emma Stone | Olivia Colman","director":"Yorgos Lanthimos",
              "generos":[{"generoInf":"Drama","subGeneroInf":"Drama"},{"generoInf":"Comedia"}],
              "productionDate":"2018","ageRange":"No recomendable para menores de 12 años","languageOriginal":"en",
              "expirationDate":"19-09-2026 02:40:00","htmlUrl":"https://www.rtve.es/play/videos/cine/la-favorita/",
              "programInfo":{"id":"77590","title":"Cine internacional"},"hasDRM":true,"requireLogged":true,"allowedInCountry":true
            }]}}
            """.trimIndent(),
        )

        assertEquals("La favorita", detail.item.title)
        assertEquals("16368062", detail.item.playbackId)
        assertEquals("77590", detail.item.programId)
        assertEquals("Cine internacional", detail.programTitle)
        assertEquals("https://img.rtve.es/h2.jpg", detail.backdropUrl)
        assertEquals("https://img.rtve.es/v/16368062/vertical?w=480", detail.item.posterUrl)
        assertEquals(listOf("Emma Stone", "Olivia Colman"), detail.cast)
        assertEquals(listOf("Drama", "Comedia"), detail.genres)
        assertEquals("2018", detail.year)
        assertEquals("Película", detail.subtypeName)
        assertTrue(detail.item.drm)
    }

    @Test
    fun `program without last multimedia has no playback id`() {
        val result = parser.parseModule(
            raw = """{"page":{"items":[{"id":"55","title":"Serie","contentType":"programa"}]}}""",
            fallbackTitle = "Series",
        ).items.single()

        assertEquals(ContentKind.PROGRAM, result.kind)
        assertNull(result.playbackId)
    }

    @Test
    fun `audio quality and nullable rights are retained`() {
        val result = parser.parseModule(
            raw = """
                {
                  "page": {"items": [{
                    "id": "42",
                    "title": "Episodio",
                    "contentType": "audio",
                    "qualities": [{"filePath":"https://ztnr.rtve.es/ztnr/42.mp3"}]
                  }]}
                }
            """.trimIndent(),
            fallbackTitle = "Radio",
        ).items.single()

        assertEquals(ContentKind.AUDIO, result.kind)
        assertEquals("42", result.playbackId)
        assertEquals("https://ztnr.rtve.es/ztnr/42.mp3", result.directQualityUrl)
        assertNull(result.allowedInCountry)
    }

    @Test
    fun `declared content type wins over the presence of idAsset`() {
        val result = parser.parseModule(
            raw = """
                {"page":{"items":[
                  {"id":"1","title":"VOD","contentType":"video","idAsset":"1"},
                  {"id":"2","title":"Canal","idAsset":"la1"},
                  {"id":"3","title":"Evento","contentType":"video","tipo":"broadcast","idAsset":"ev"}
                ]}}
            """.trimIndent(),
            fallbackTitle = "Mixto",
        ).items

        assertEquals(listOf(ContentKind.VIDEO, ContentKind.LIVE, ContentKind.LIVE), result.map { it.kind })
    }

    @Test
    fun `duplicated items are collapsed so list keys stay unique`() {
        val result = parser.parseModule(
            raw = """{"page":{"items":[{"id":"7","title":"x","contentType":"video"},{"id":"7","title":"x","contentType":"video"}]}}""",
            fallbackTitle = "Repetidos",
        )

        assertEquals(1, result.items.size)
    }

    @Test
    fun `images are picked by aspect ratio and foreign hosts are dropped`() {
        val result = parser.parseModule(
            raw = """
                {"page":{"items":[{
                  "id":"9","titulo":"Canal","tipo":"broadcast","idAsset":"a9",
                  "thumb":"https://img.rtve.es/thumbs/9.jpg","thumb_square":"https://img.rtve.es/thumbs/9_sq.jpg","thumb_vertical":"https://img.rtve.es/thumbs/9_v.jpg"
                },{
                  "id":"10","titulo":"Ajeno","tipo":"broadcast","idAsset":"a10",
                  "thumb":"https://cdn.example.org/10.jpg"
                }]}}
            """.trimIndent(),
            fallbackTitle = "Directos",
        ).items

        assertEquals("https://img.rtve.es/thumbs/9.jpg", result[0].imageUrl)
        assertEquals("https://img.rtve.es/thumbs/9_sq.jpg", result[0].squareUrl)
        assertEquals("https://img.rtve.es/thumbs/9_v.jpg", result[0].posterUrl)
        assertNull(result[1].imageUrl)
    }
}
