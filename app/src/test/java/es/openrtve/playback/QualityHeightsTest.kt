package es.openrtve.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que RTVE sirve a la app para una película: 1080p solo en AV1 y 720/576/360 en
 * AVC, en grupos distintos. Un Mi Box 3 reproduce AVC (sin AV1 por hardware); una
 * TV con AV1, el 1080p.
 */
class QualityHeightsTest {
    @Test
    fun `without AV1 hardware the menu never offers the AV1-only 1080p`() {
        val (heights, ceiling) = qualityHeights(listOf(av1(playing = false), avc(playing = true)), userCapped = false, knownCeiling = null)
        assertEquals(listOf(720, 576, 360), heights)
        assertEquals(720, ceiling)
    }

    @Test
    fun `with AV1 playing every lower AVC height is offered too`() {
        val (heights, ceiling) = qualityHeights(listOf(av1(playing = true), avc(playing = false)), userCapped = false, knownCeiling = null)
        assertEquals(listOf(1080, 720, 576, 360), heights)
        assertEquals(1080, ceiling)
    }

    @Test
    fun `a cap keeps the ceiling measured in automatic`() {
        // En la TV con AV1 se eligió 720p: suena AVC, pero 1080p sigue en el menú para volver.
        val (heights, ceiling) = qualityHeights(listOf(av1(playing = false), avc(playing = true)), userCapped = true, knownCeiling = 1080)
        assertEquals(listOf(1080, 720, 576, 360), heights)
        assertEquals(1080, ceiling)
    }

    @Test
    fun `without a measured ceiling a cap falls back to what is playing`() {
        val (heights, _) = qualityHeights(listOf(av1(playing = false), avc(playing = true)), userCapped = true, knownCeiling = null)
        assertEquals(listOf(720, 576, 360), heights)
    }

    @Test
    fun `nothing playing yet gives no options`() {
        assertEquals(emptyList<Int>() to null, qualityHeights(listOf(av1(false), avc(false)), userCapped = false, knownCeiling = null))
    }

    private fun av1(playing: Boolean) = VideoGroup(listOf(1080), playing)
    private fun avc(playing: Boolean) = VideoGroup(listOf(720, 576, 360), playing)
}
