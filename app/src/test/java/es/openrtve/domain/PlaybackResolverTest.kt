package es.openrtve.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResolverTest {
    private val resolver = PlaybackResolver()

    @Test
    fun `video resolves to dash with a license reference and a compatibility fallback`() {
        val result = resolver.resolve(item(kind = ContentKind.VIDEO)) as PlaybackDecision.Ready

        assertEquals("https://ztnr.rtve.es/ztnr/100.mpd", result.uri)
        assertEquals(DrmSpec("https://api.rtve.es/api/token/100"), result.drm)
        assertEquals("https://ztnr.rtve.es/ztnr/tv/100.mpd", result.fallbackUri)
    }

    @Test
    fun `public live resolves to hls and drm live to dash`() {
        val plain = resolver.resolve(item(kind = ContentKind.LIVE).copy(assetId = "live-1")) as PlaybackDecision.Ready
        val drm = resolver.resolve(item(kind = ContentKind.LIVE).copy(assetId = "la1", drm = true)) as PlaybackDecision.Ready

        assertEquals("https://ztnr.rtve.es/ztnr/live-1.m3u8", plain.uri)
        assertNull(plain.drm)
        assertEquals("https://ztnr.rtve.es/ztnr/la1.mpd", drm.uri)
        assertEquals(DrmSpec("https://api.rtve.es/api/token/la1"), drm.drm)
    }

    @Test
    fun `audio prefers a public quality url`() {
        val result = resolver.resolve(
            item(kind = ContentKind.AUDIO).copy(
                directQualityUrl = "https://ztnr.rtve.es/ztnr/100.mp3",
            ),
        ) as PlaybackDecision.Ready

        assertEquals("https://ztnr.rtve.es/ztnr/100.mp3", result.uri)
        assertTrue(result.isAudioOnly)
    }

    @Test
    fun `rights are checked before constructing a stream`() {
        assertEquals(
            PlaybackDecision.Blocked(BlockReason.GEO_RESTRICTED),
            resolver.resolve(item(kind = ContentKind.VIDEO).copy(allowedInCountry = false)),
        )
        assertEquals(
            PlaybackDecision.Blocked(BlockReason.SUBSCRIPTION_REQUIRED),
            resolver.resolve(item(kind = ContentKind.VIDEO).copy(paid = true)),
        )
    }

    @Test
    fun `login gate is a client policy and only applies when enforced`() {
        val gated = item(kind = ContentKind.VIDEO).copy(loginRequired = true)

        assertTrue(resolver.resolve(gated) is PlaybackDecision.Ready)
        assertEquals(
            PlaybackDecision.Blocked(BlockReason.LOGIN_REQUIRED),
            PlaybackResolver(enforceLoginGate = true).resolve(gated),
        )
    }

    @Test
    fun `program without a media id is blocked instead of guessing a stream`() {
        val result = resolver.resolve(item(kind = ContentKind.PROGRAM).copy(playbackId = null))

        assertEquals(PlaybackDecision.Blocked(BlockReason.NO_SOURCE), result)
    }

    @Test
    fun `foreign and placeholder audio urls are ignored`() {
        val foreign = resolver.resolve(
            item(kind = ContentKind.AUDIO).copy(
                directQualityUrl = "https://example.org/audio.mp3",
            ),
        ) as PlaybackDecision.Ready
        val placeholder = resolver.resolve(
            item(kind = ContentKind.AUDIO).copy(
                directQualityUrl = "https://ztnr.rtve.es/res/_TOKEN_",
            ),
        ) as PlaybackDecision.Ready

        assertEquals("https://ztnr.rtve.es/ztnr/100.mp3", foreign.uri)
        assertEquals("https://ztnr.rtve.es/ztnr/100.mp3", placeholder.uri)
    }

    private fun item(kind: ContentKind) = CatalogItem(
        id = "item-1",
        playbackId = "100",
        assetId = null,
        title = "Contenido",
        subtitle = null,
        imageUrl = null,
        kind = kind,
        directQualityUrl = null,
        allowedInCountry = true,
        loginRequired = false,
        paid = false,
        drm = false,
    )
}
