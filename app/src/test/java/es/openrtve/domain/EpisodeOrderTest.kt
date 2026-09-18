package es.openrtve.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeOrderTest {
    private val base = ProgramDetail(id = "1", title = "P", description = null, imageUrl = null, emission = null, seasons = emptyList())

    @Test
    fun `episode order follows the official rules`() {
        // Serie diaria en emisión: la lista completa y la temporada en curso del final; las pasadas del principio.
        val onAir = base.copy(programTypeId = "132870", inEmission = true)
        assertEquals(EpisodeOrder.NEWEST_FIRST, onAir.episodeOrder(isFirstSeason = true))
        assertEquals(EpisodeOrder.OLDEST_FIRST, onAir.episodeOrder(isFirstSeason = false))

        // Serie terminada sin completar (Isabel): del principio.
        assertEquals(EpisodeOrder.OLDEST_FIRST, base.copy(programTypeId = "136519", inEmission = false).episodeOrder())

        // Completa: siempre del principio, aunque emita.
        assertEquals(EpisodeOrder.OLDEST_FIRST, base.copy(programTypeId = "136519", inEmission = true, isComplete = true).episodeOrder())

        // Documentales, conciertos, entrevistas y reportajes: del final aunque hayan terminado y sea una temporada pasada.
        assertEquals(EpisodeOrder.NEWEST_FIRST, base.copy(programTypeId = "137650", inEmission = false).episodeOrder(isFirstSeason = false))

        // Noticias: del final mientras emiten, en cualquier temporada; del principio si terminaron.
        assertEquals(EpisodeOrder.NEWEST_FIRST, base.copy(programTypeId = "132534", inEmission = true).episodeOrder(isFirstSeason = false))
        assertEquals(EpisodeOrder.OLDEST_FIRST, base.copy(programTypeId = "132534", inEmission = false).episodeOrder())

        // Sin tipo (programas antiguos): solo cuenta si emite.
        assertEquals(EpisodeOrder.NEWEST_FIRST, base.copy(inEmission = true).episodeOrder())
        assertEquals(EpisodeOrder.OLDEST_FIRST, base.copy(inEmission = false).episodeOrder())
    }

    @Test
    fun `seasons are sorted by orden, newest first unless the program ended incomplete, and empty ones drop`() {
        val seasons = listOf(
            ProgramSeason("t1", "Temporada 1", 8, order = 1),
            ProgramSeason("t3", "Temporada 3", 0, order = 3),
            ProgramSeason("t2", "Temporada 2", 13, order = 2),
            ProgramSeason("esp", "Especiales", null, order = 9),
        )
        val onAir = base.copy(seasons = seasons, inEmission = true)
        assertEquals(listOf("esp", "t2", "t1"), onAir.seasonsInDisplayOrder().map { it.id })
        val ended = base.copy(seasons = seasons, inEmission = false)
        assertEquals(listOf("t1", "t2", "esp"), ended.seasonsInDisplayOrder().map { it.id })
        assertEquals(listOf("esp", "t2", "t1"), ended.copy(isComplete = true).seasonsInDisplayOrder().map { it.id })
    }

    @Test
    fun `the first season is the first tab, and no selection counts as first`() {
        val detail = base.copy(seasons = listOf(ProgramSeason("t2", "T2", 3, order = 2), ProgramSeason("t1", "T1", 3, order = 1)))
        assertEquals(true, detail.isFirstSeason(null))
        assertEquals(true, detail.isFirstSeason("t2"))
        assertEquals(false, detail.isFirstSeason("t1"))
    }

    @Test
    fun `api values match the official client`() {
        assertEquals("multimedia_date_emission,desc", EpisodeOrder.NEWEST_FIRST.apiValue)
        assertEquals("multimedia_date_emission,asc", EpisodeOrder.OLDEST_FIRST.apiValue)
    }
}
