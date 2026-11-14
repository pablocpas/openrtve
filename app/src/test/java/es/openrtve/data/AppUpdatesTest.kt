package es.openrtve.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `versions compare by number, not as text`() {
        assertTrue(AppUpdates.isNewer("0.7.1", "0.7.0"))
        assertTrue(AppUpdates.isNewer("0.10.0", "0.9.9"))
        assertTrue(AppUpdates.isNewer("v1.0.0", "0.9.0"))
        assertFalse(AppUpdates.isNewer("0.7.0", "0.7.0"))
        assertFalse(AppUpdates.isNewer("1.0", "1.0.0"))
        assertFalse(AppUpdates.isNewer("0.6.0", "0.7.0"))
        assertFalse(AppUpdates.isNewer("0.7.0-beta", "0.7.0"))
    }

    @Test
    fun `the latest release gives its version, APK and SHA-256`() {
        val release = AppUpdates.parseRelease(json, response())!!
        assertEquals("0.7.1", release.version)
        assertEquals("https://github.com/pablocpas/openrtve/releases/download/v0.7.1/app-release.apk", release.apkUrl)
        assertEquals("892029b5ec8db08f741871673d81fbd0ca8c291db1e8e64fc57d63a22872635a", release.sha256)
        assertEquals(3834195L, release.sizeBytes)
    }

    @Test
    fun `drafts, pre-releases, releases without APK and foreign downloads are ignored`() {
        assertNull(AppUpdates.parseRelease(json, response(draft = true)))
        assertNull(AppUpdates.parseRelease(json, response(prerelease = true)))
        assertNull(AppUpdates.parseRelease(json, response(assetName = "notas.txt")))
        assertNull(AppUpdates.parseRelease(json, response(url = "https://example.com/app-release.apk")))
    }

    @Test
    fun `a release without digest still parses, without SHA-256`() {
        assertNull(AppUpdates.parseRelease(json, response(digest = null))!!.sha256)
    }

    /** Lo que devuelve `GET /repos/pablocpas/openrtve/releases/latest` (recortado). */
    private fun response(
        draft: Boolean = false,
        prerelease: Boolean = false,
        assetName: String = "app-release.apk",
        url: String = "https://github.com/pablocpas/openrtve/releases/download/v0.7.1/app-release.apk",
        digest: String? = "sha256:892029b5ec8db08f741871673d81fbd0ca8c291db1e8e64fc57d63a22872635a",
    ) = """
        {
          "tag_name": "v0.7.1",
          "name": "OpenRTVE 0.7.1",
          "draft": $draft,
          "prerelease": $prerelease,
          "html_url": "https://github.com/pablocpas/openrtve/releases/tag/v0.7.1",
          "assets": [
            {
              "name": "$assetName",
              "size": 3834195,
              ${if (digest != null) "\"digest\": \"$digest\"," else ""}
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "$url"
            }
          ]
        }
    """.trimIndent()
}
