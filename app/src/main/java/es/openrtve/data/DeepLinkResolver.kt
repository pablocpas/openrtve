package es.openrtve.data

import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.DeepLink
import es.openrtve.domain.HomeRow
import es.openrtve.domain.RowLayout
import es.openrtve.domain.parseDeepLink

/** Convierte un enlace en el item del catálogo al que apunta, con la red que haga falta. */
class DeepLinkResolver(
    private val repository: CatalogRepository,
) {
    suspend fun resolve(url: String): CatalogItem? {
        val link = parseDeepLink(url) ?: return null
        return when (link) {
            is DeepLink.Video -> repository.loadVideo(link.id).value.item
            is DeepLink.Audio -> repository.loadAudio(link.id).value.item
            is DeepLink.Program -> repository.loadProgram(link.id).value.let { detail ->
                programItem(detail.id, detail.title, detail.imageUrl)
            }
            is DeepLink.Live -> repository.loadModule(liveRow("$LIVES_BASE/${link.assetId}.json")).value.items
                .firstOrNull { it.kind == ContentKind.LIVE }
            is DeepLink.ProgramPermalink -> {
                val marker = "/play/${if (link.isAudio) "audios" else "videos"}/${link.permalink}/"
                repository.search(link.permalink.replace('-', ' ')).programs
                    .firstOrNull { it.webUrl?.endsWith(marker) == true }
            }
            is DeepLink.LivePermalink -> repository.loadModule(liveRow("$LIVES_BASE/agr-directos/1/directos.json")).value.items
                .firstOrNull { it.webUrl?.trimEnd('/')?.endsWith("/directo/${link.permalink}") == true }
        }
    }

    private fun liveRow(url: String) = HomeRow(
        id = -1,
        title = "",
        order = 0,
        moduleType = "livesCollection",
        presentation = "directosTV",
        contentUrl = url,
        layout = RowLayout.LANDSCAPE,
    )

    private fun programItem(id: String, title: String, imageUrl: String?) = CatalogItem(
        id = id,
        playbackId = null,
        assetId = null,
        title = title,
        subtitle = null,
        imageUrl = imageUrl,
        kind = ContentKind.PROGRAM,
        directQualityUrl = null,
        allowedInCountry = null,
        loginRequired = false,
        paid = false,
        drm = false,
        programId = id,
    )

    private companion object {
        const val LIVES_BASE = "https://api.rtve.es/api/lives"
    }
}
