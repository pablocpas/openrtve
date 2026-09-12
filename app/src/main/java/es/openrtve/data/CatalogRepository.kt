package es.openrtve.data

import es.openrtve.domain.CatalogLoad
import es.openrtve.domain.CatalogModule
import es.openrtve.domain.CatalogPage
import es.openrtve.domain.ExploreGroup
import es.openrtve.domain.HomeFeed
import es.openrtve.domain.HomeRow
import es.openrtve.domain.ProgramDetail
import es.openrtve.domain.QuickFilter
import es.openrtve.domain.RtveUrls
import es.openrtve.domain.SearchResults
import es.openrtve.domain.VideoDetail
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CatalogRepository {
    suspend fun loadPortada(url: String, forceRefresh: Boolean = false): CatalogLoad<HomeFeed>
    suspend fun loadModule(row: HomeRow, forceRefresh: Boolean = false): CatalogLoad<CatalogModule>
    suspend fun loadExplore(forceRefresh: Boolean = false): CatalogLoad<List<ExploreGroup>>
    suspend fun loadQuickFilters(): CatalogLoad<List<QuickFilter>>
    suspend fun loadQuickFilterItems(filter: QuickFilter): CatalogLoad<CatalogModule>
    suspend fun search(query: String): SearchResults
    suspend fun loadProgram(programId: String, forceRefresh: Boolean = false): CatalogLoad<ProgramDetail>
    suspend fun loadVideo(videoId: String, forceRefresh: Boolean = false): CatalogLoad<VideoDetail>

    /**
     * Episodios de un programa o de una temporada. `completeOnly` pide solo
     * contenidos completos (`type=39816`); sin él llegan también los fragmentos.
     */
    suspend fun loadProgramVideos(
        programId: String,
        seasonId: String? = null,
        page: Int = 1,
        completeOnly: Boolean = true,
        forceRefresh: Boolean = false,
    ): CatalogLoad<CatalogPage>
}

class DefaultCatalogRepository(
    private val httpClient: TextHttpClient,
    private val parser: RtveJsonParser,
    private val cache: RawDocumentCache,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    override suspend fun loadPortada(
        url: String,
        forceRefresh: Boolean,
    ): CatalogLoad<HomeFeed> = loadDocument(
        url = url,
        forceRefresh = forceRefresh,
        freshForMillis = HOME_FRESH_MS,
        staleForMillis = HOME_STALE_MS,
        parse = parser::parseHome,
    )

    override suspend fun loadExplore(forceRefresh: Boolean): CatalogLoad<List<ExploreGroup>> = loadDocument(
        url = RtveUrls.REMOTE_CONFIG,
        forceRefresh = forceRefresh,
        freshForMillis = CONFIG_FRESH_MS,
        staleForMillis = CONFIG_STALE_MS,
        parse = parser::parseExplore,
    )

    override suspend fun loadQuickFilters(): CatalogLoad<List<QuickFilter>> = loadDocument(
        url = RtveUrls.QUICK_SEARCHES,
        forceRefresh = false,
        freshForMillis = CONFIG_FRESH_MS,
        staleForMillis = CONFIG_STALE_MS,
        parse = parser::parseQuickFilters,
    )

    override suspend fun loadQuickFilterItems(filter: QuickFilter): CatalogLoad<CatalogModule> = loadDocument(
        url = filter.contentUrl,
        forceRefresh = false,
        freshForMillis = MODULE_FRESH_MS,
        staleForMillis = MODULE_STALE_MS,
        parse = { parser.parseModule(it, filter.title) },
    )

    // Las búsquedas no se persisten: solo interesa el resultado del momento.
    override suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val url = "${RtveUrls.SEARCH}?search=${encode(query)}&context=tve&tipology=video&type=completo"
        parser.parseSearch(httpClient.get(url))
    }

    override suspend fun loadModule(
        row: HomeRow,
        forceRefresh: Boolean,
    ): CatalogLoad<CatalogModule> {
        val url = requireNotNull(row.contentUrl) { "Este módulo no tiene una fuente remota" }
        return loadDocument(
            url = url,
            forceRefresh = forceRefresh,
            freshForMillis = MODULE_FRESH_MS,
            staleForMillis = MODULE_STALE_MS,
            parse = { parser.parseModule(it, row.title) },
        )
    }

    override suspend fun loadProgram(
        programId: String,
        forceRefresh: Boolean,
    ): CatalogLoad<ProgramDetail> = loadDocument(
        url = "$PROGRAMS_BASE/${encode(programId)}.json",
        forceRefresh = forceRefresh,
        freshForMillis = PROGRAM_FRESH_MS,
        staleForMillis = PROGRAM_STALE_MS,
        parse = parser::parseProgram,
    )

    override suspend fun loadVideo(
        videoId: String,
        forceRefresh: Boolean,
    ): CatalogLoad<VideoDetail> = loadDocument(
        url = "$VIDEOS_BASE/${encode(videoId)}.json",
        forceRefresh = forceRefresh,
        freshForMillis = PROGRAM_FRESH_MS,
        staleForMillis = PROGRAM_STALE_MS,
        parse = parser::parseVideo,
    )

    override suspend fun loadProgramVideos(
        programId: String,
        seasonId: String?,
        page: Int,
        completeOnly: Boolean,
        forceRefresh: Boolean,
    ): CatalogLoad<CatalogPage> {
        val path = buildString {
            append(PROGRAMS_BASE).append('/').append(encode(programId))
            if (seasonId != null) append("/temporadas/").append(encode(seasonId))
            append("/videos.json?page=").append(page)
            if (completeOnly) append("&type=").append(TYPE_COMPLETE)
        }
        return loadDocument(
            url = path,
            forceRefresh = forceRefresh,
            freshForMillis = PROGRAM_FRESH_MS,
            staleForMillis = PROGRAM_STALE_MS,
            parse = parser::parseVideoPage,
        )
    }

    private fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    private suspend fun <T> loadDocument(
        url: String,
        forceRefresh: Boolean,
        freshForMillis: Long,
        staleForMillis: Long,
        parse: (String) -> T,
    ): CatalogLoad<T> = withContext(Dispatchers.IO) {
        val now = nowMillis()
        val cached = cache.read(url)
        val age = cached?.let { now - it.savedAtMillis } ?: Long.MAX_VALUE
        // Solo se parsea la copia local cuando hace falta: está fresca o la red falla.
        val cachedValue: T? by lazy { cached?.let { runCatching { parse(it.raw) }.getOrNull() } }

        if (!forceRefresh && age <= freshForMillis) {
            cachedValue?.let { return@withContext CatalogLoad(it, isStale = false) }
        }

        try {
            // Con copia local se revalida por ETag: un 304 no descarga nada.
            val response = httpClient.fetch(url, cached?.etag)
            if (response.notModified && cached != null) {
                cache.touch(url, now)
                CatalogLoad(cachedValue ?: parse(cached.raw), isStale = false)
            } else {
                val raw = response.body ?: throw IOException("Respuesta vacía")
                val value = parse(raw)
                cache.write(url, raw, now, response.etag)
                CatalogLoad(value, isStale = false)
            }
        } catch (networkError: Exception) {
            val fallback = if (age <= staleForMillis) cachedValue else null
            fallback?.let { CatalogLoad(it, isStale = true) } ?: throw networkError
        }
    }

    private companion object {
        const val HOME_FRESH_MS = 2 * 60 * 1_000L
        const val HOME_STALE_MS = 24 * 60 * 60 * 1_000L
        const val MODULE_FRESH_MS = 5 * 60 * 1_000L
        const val MODULE_STALE_MS = 24 * 60 * 60 * 1_000L
        const val PROGRAM_FRESH_MS = 15 * 60 * 1_000L
        const val PROGRAM_STALE_MS = 7 * 24 * 60 * 60 * 1_000L
        const val CONFIG_FRESH_MS = 60 * 60 * 1_000L
        const val CONFIG_STALE_MS = 7 * 24 * 60 * 60 * 1_000L
        const val PROGRAMS_BASE = "https://www.rtve.es/api/programas"
        const val VIDEOS_BASE = "https://api.rtve.es/api/videos"
        const val TYPE_COMPLETE = "39816"
    }
}
