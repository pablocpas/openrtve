package es.openrtve.data

import es.openrtve.domain.CatalogItem
import es.openrtve.domain.CatalogModule
import es.openrtve.domain.CatalogPage
import es.openrtve.domain.ExploreCategory
import es.openrtve.domain.ExploreGroup
import es.openrtve.domain.QuickFilter
import es.openrtve.domain.RowLayout
import es.openrtve.domain.RtveUrls
import es.openrtve.domain.SearchResults
import es.openrtve.domain.VideoDetail
import es.openrtve.domain.ContentKind
import es.openrtve.domain.HomeFeed
import es.openrtve.domain.HomeRow
import es.openrtve.domain.LiveInfo
import es.openrtve.domain.PreviewSprite
import es.openrtve.domain.SpriteCue
import es.openrtve.domain.ProgramDetail
import es.openrtve.domain.ProgramSeason
import es.openrtve.domain.RtveHostPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RtveJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val hostPolicy: RtveHostPolicy = RtveHostPolicy(),
) {
    fun parseHome(raw: String): HomeFeed {
        val root = json.parseToJsonElement(raw).jsonObject
        val rows = root.array("rows")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { row ->
                val presentation = row.text("tipo")
                HomeRow(
                    id = -1,
                    title = row.text("title", "name").orEmpty(),
                    order = row.number("orden", "order") ?: Int.MAX_VALUE,
                    moduleType = row.text("moduleType"),
                    presentation = presentation,
                    contentUrl = row.text("urlContent")?.takeIf(hostPolicy::isAllowed),
                    layout = rowLayout(presentation),
                )
            }
            // Enlaces y noticias no son reproducibles; la parrilla tiene otra estructura.
            .filterNot { it.presentation?.lowercase() in NON_CATALOG_ROWS }
            .sortedBy(HomeRow::order)
            .mapIndexed { index, row -> row.copy(id = index) }

        return HomeFeed(
            id = root.text("id"),
            title = root.text("title") ?: "RTVE Play",
            rows = rows,
        )
    }

    /**
     * Muchas filas de portada llegan sin título; la propia colección sí lo trae
     * (`title`, no `name`, que es la ruta editorial interna).
     */
    fun parseModule(raw: String, fallbackTitle: String): CatalogModule {
        val root = json.parseToJsonElement(raw).jsonObject
        val pageItems = root.obj("page")?.array("items")
            ?: root.array("items")
            ?: JsonArray(emptyList())
        val collection = (pageItems.singleOrNull() as? JsonObject)
            ?.takeIf { it.array("collectionItems") != null }
        val items = toCatalogItems((collection?.array("collectionItems") ?: pageItems).mapNotNull { it as? JsonObject })

        return CatalogModule(
            title = fallbackTitle.ifBlank { collection?.text("title") ?: fallbackTitle },
            items = items,
        )
    }

    fun parseSearch(raw: String): SearchResults {
        val root = json.parseToJsonElement(raw).jsonObject
        fun block(key: String) = toCatalogItems(
            root.obj(key)?.obj("page")?.array("items").orEmpty().mapNotNull { it as? JsonObject },
        )
        return SearchResults(programs = block("programs"), videos = block("videos"))
    }

    fun parseQuickFilters(raw: String): List<QuickFilter> =
        json.parseToJsonElement(raw).jsonObject.array("items")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { item ->
                val url = item.text("nav-pag_url")?.takeIf(hostPolicy::isAllowed) ?: return@mapNotNull null
                QuickFilter(title = item.text("nav-pag_name") ?: return@mapNotNull null, contentUrl = url)
            }

    /**
     * Categorías del menú de la app oficial (`menuBloques`), solo las portadas
     * públicas. La radio se añade como un grupo más.
     */
    fun parseExplore(raw: String): List<ExploreGroup> {
        val root = json.parseToJsonElement(raw).jsonObject
        val groups = root.obj("television")?.array("menuBloques")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { block ->
                val categories = block.array("menuItems")
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { it.text("tipo") == "portada" && it.flag("subscriptor") != true }
                    .mapNotNull { item ->
                        ExploreCategory(
                            title = item.text("title") ?: return@mapNotNull null,
                            imageUrl = item.text("imgBackground")?.let(hostPolicy::sanitize),
                            portadaUrl = item.text("urlContent")?.takeIf(hostPolicy::isAllowed)
                                ?: return@mapNotNull null,
                        )
                    }
                    .distinctBy(ExploreCategory::portadaUrl)
                if (categories.isEmpty()) return@mapNotNull null
                ExploreGroup(title = block.text("title").orEmpty(), categories = categories)
            }
        val radio = root.obj("radio")?.array("menuBloques")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { it.array("menuItems").orEmpty() }
            .mapNotNull { it as? JsonObject }
            .filter { it.text("tipo") == "portada" }
            .mapNotNull { item ->
                ExploreCategory(
                    title = item.text("title") ?: return@mapNotNull null,
                    imageUrl = item.text("imgBackground")?.let(hostPolicy::sanitize),
                    portadaUrl = item.text("urlContent")?.let(hostPolicy::sanitize) ?: return@mapNotNull null,
                )
            }
        val radioGroup = ExploreGroup(
            title = "Radio",
            categories = listOf(ExploreCategory("Radio", null, RtveUrls.RADIO_HOME)) + radio,
        )
        return groups + radioGroup
    }

    private fun rowLayout(presentation: String?): RowLayout = when (presentation?.lowercase()) {
        "coleccionsuperdestacado", "colecciondestacado" -> RowLayout.HERO
        "coleccionposter", "videoposter", "storiesposter" -> RowLayout.POSTER
        "coleccioncuadrado", "coleccioncuadradopeq" -> RowLayout.SQUARE
        else -> RowLayout.LANDSCAPE
    }

    fun parseProgram(raw: String): ProgramDetail {
        val root = json.parseToJsonElement(raw).jsonObject
        val program = root.obj("page")?.array("items")?.firstOrNull() as? JsonObject
            ?: throw IllegalArgumentException("El programa no tiene ficha")
        val seasons = program.array("seasons")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { season ->
                val id = season.text("id") ?: return@mapNotNull null
                ProgramSeason(
                    id = id,
                    title = season.text("longTitle", "shorttitle", "shortTitle", "title") ?: id,
                    episodeCount = season.number("numEpisodes"),
                )
            }
        return ProgramDetail(
            id = program.text("id") ?: throw IllegalArgumentException("Programa sin id"),
            title = program.text("name", "title", "longTitle") ?: "",
            description = program.text("description", "longDescription", "promoDesc"),
            imageUrl = firstAllowed(program.text("imgPortada", "imgPortada2"))
                ?: derivedImage("p", program.text("id").orEmpty(), "imgPortada", BACKDROP_WIDTH),
            emission = program.text("emission"),
            seasons = seasons,
        )
    }

    fun parseVideoPage(raw: String): CatalogPage {
        val root = json.parseToJsonElement(raw).jsonObject
        val page = root.obj("page")
        val items = toCatalogItems(page?.array("items").orEmpty().mapNotNull { it as? JsonObject })
        return CatalogPage(
            items = items,
            page = page?.number("number") ?: 1,
            totalPages = page?.number("totalPages") ?: 1,
        )
    }

    /**
     * Una lista de "programa + lastMultimedia" puede ser una lista de programas
     * (cada uno con su último episodio) o una lista de medios que repite el
     * programa en cada item (etapas, resúmenes, invitados...). Se deduce de los
     * datos: si el programa se repite, cada item es su medio.
     */
    private fun toCatalogItems(objects: List<JsonObject>): List<CatalogItem> {
        val programOccurrences = objects
            .filter { it.obj("lastMultimedia") != null }
            .groupingBy { it.text("id", "uid") }
            .eachCount()
        return objects
            .mapNotNull { item ->
                val repeated = (programOccurrences[item.text("id", "uid")] ?: 0) > 1
                toCatalogItem(item, mediaFirst = repeated)
            }
            // Noticias, enlaces o tipos que no conocemos no se pueden abrir ni reproducir.
            .filter { it.kind != ContentKind.UNKNOWN }
            // Los feeds repiten items; Compose exige claves únicas en las listas.
            .distinctBy(CatalogItem::id)
    }

    private fun toCatalogItem(item: JsonObject, mediaFirst: Boolean = false): CatalogItem? {
        val nestedMedia = item.obj("lastMultimedia")
        if (nestedMedia != null && (mediaFirst || isContainer(item, nestedMedia))) {
            val mediaItem = toCatalogItem(nestedMedia) ?: return null
            // Las previews del medio anidado repiten la imagen del programa; el
            // servicio de imágenes por id sí devuelve la propia del medio.
            return mediaItem.copy(
                subtitle = mediaItem.subtitle ?: item.text("name", "title"),
                programId = mediaItem.programId ?: item.text("id"),
            )
        }
        val media = nestedMedia ?: item
        val id = item.text("id", "uid") ?: media.text("id", "uid") ?: return null
        val title = item.text("title", "name", "titulo", "longTitle", "shortTitle", "metaTitle")
            ?: media.text("title", "name", "longTitle")
            ?: fallbackTitle(item)
            ?: return null
        val mediaTitle = media.text("title", "longTitle")
            ?.takeUnless { it == title }
        val contentType = media.text("contentType")
            ?: item.text("contentType")
        val editorialType = media.text("tipo") ?: item.text("tipo")
        val assetId = media.text("idAsset") ?: item.text("idAsset")
        val kind = contentKind(contentType, editorialType, nestedMedia != null, assetId)
        val live = if (kind == ContentKind.LIVE) liveInfo(item) else null
        val subtitle = when {
            mediaTitle != null -> mediaTitle
            kind == ContentKind.VIDEO -> item.obj("programInfo")?.text("title")
            kind == ContentKind.LIVE -> live?.category
            else -> null
        }

        return CatalogItem(
            id = id,
            playbackId = playbackId(kind, nestedMedia, id),
            assetId = assetId,
            title = title,
            subtitle = subtitle,
            imageUrl = landscapeImage(kind, id, media, item),
            kind = kind,
            posterUrl = posterImage(kind, id, media, item),
            squareUrl = squareImage(kind, id, media, item),
            programId = if (kind == ContentKind.PROGRAM) id else item.obj("programInfo")?.text("id"),
            directQualityUrl = media.array("qualities")
                ?.mapNotNull { it as? JsonObject }
                ?.firstNotNullOfOrNull { it.text("filePath") },
            allowedInCountry = media.flag("allowedInCountry")
                ?: item.flag("allowedInCountry"),
            loginRequired = media.flag("requireLogged")
                ?: item.flag("requireLogged")
                ?: false,
            paid = media.flag("paidContent")
                ?: item.flag("paidContent")
                ?: false,
            drm = media.flag("hasDRM")
                ?: item.flag("hasDRM")
                ?: false,
            durationMs = media.long("duration"),
            publicationDate = media.text("publicationDate", "dateOfEmission"),
            episode = media.number("episode")?.takeIf { it > 0 },
            seasonTitle = media.text("temporada", "temporadaShortTitle"),
            live = live,
        )
    }

    private fun liveInfo(item: JsonObject): LiveInfo = LiveInfo(
        isOnAir = item.flag("live") ?: true,
        startsAtMillis = item.text("inicio")?.let(::parseMadridTime),
        durationMinutes = item.number("duracion")?.takeIf { it > 0 },
        progressPercent = item.number("porcentaje"),
        channelLogoUrl = item.text("logo")?.let(hostPolicy::sanitize),
        category = item.text("antetitulo")?.let(::titleCase),
    )

    /** El feed escribe las horas en local de Madrid, sin zona. */
    private fun parseMadridTime(value: String): Long? = runCatching {
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Madrid") }
            .parse(value)?.time
    }.getOrNull()

    /** "LA VUELTA 2026" -> "La Vuelta 2026"; los antetítulos llegan en mayúsculas. */
    private fun titleCase(value: String): String =
        if (value != value.uppercase()) value
        else value.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** Respuesta de `tiivii-previews/api/sprite`: URLs del sprite y de su VTT, o `null` si no está disponible. */
    fun parseSpriteInfo(raw: String): Pair<String, String>? {
        val root = json.parseToJsonElement(raw).jsonObject
        if (root.text("state") != "AVAILABLE") return null
        val sprite = root.text("sprite_url")?.let(hostPolicy::sanitize) ?: return null
        val vtt = root.text("vtt_url")?.let(hostPolicy::sanitize) ?: return null
        return sprite to vtt
    }

    /** VTT de regiones: `00:00:10 --> 00:00:20` y una URL con `#xywh=x,y,w,h`. */
    fun parseSpriteVtt(vtt: String, spriteUrl: String): PreviewSprite {
        val cues = mutableListOf<SpriteCue>()
        val lines = vtt.lines()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            val arrow = line.indexOf("-->")
            if (arrow > 0) {
                val start = parseVttTime(line.substring(0, arrow).trim())
                val end = parseVttTime(line.substring(arrow + 3).trim())
                val region = lines.getOrNull(index + 1)?.substringAfter("#xywh=", "")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                if (start != null && end != null && region != null && region.size == 4) {
                    cues += SpriteCue(start, end, region[0], region[1], region[2], region[3])
                }
                index += 2
            } else {
                index++
            }
        }
        return PreviewSprite(spriteUrl, cues)
    }

    private fun parseVttTime(value: String): Long? {
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong())
    }

    private fun isContainer(program: JsonObject, media: JsonObject): Boolean =
        program.text("programType")?.contains("contenedor", ignoreCase = true) == true ||
            media.obj("subType")?.text("name")?.equals("Película", ignoreCase = true) == true

    fun parseVideo(raw: String): VideoDetail {
        val root = json.parseToJsonElement(raw).jsonObject
        val video = root.obj("page")?.array("items")?.firstOrNull() as? JsonObject
            ?: throw IllegalArgumentException("El vídeo no tiene ficha")
        val item = toCatalogItem(video) ?: throw IllegalArgumentException("Vídeo sin id")
        val previews = video.obj("previews")
        return VideoDetail(
            item = item,
            backdropUrl = firstAllowed(previews?.text("horizontal2", "horizontal"), video.text("thumbnail", "imageSEO"))
                ?: derivedImage("v", item.id, "horizontal2", BACKDROP_WIDTH),
            description = video.text("description", "longDescription", "shortDescription"),
            promo = video.text("promoDesc"),
            subtypeName = video.obj("subType")?.text("name"),
            programTitle = video.obj("programInfo")?.text("title"),
            year = video.text("productionDate")?.take(4),
            ageRating = video.text("ageRange"),
            genres = video.array("generos")
                .orEmpty()
                .mapNotNull { (it as? JsonObject)?.text("subGeneroInf", "generoInf") }
                .distinct(),
            director = video.text("director")?.split('|')?.map(String::trim)?.filter(String::isNotEmpty)?.joinToString(", "),
            cast = video.text("casting")?.split('|')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
            originalLanguage = video.text("languageOriginal"),
            expirationDate = video.text("expirationDate"),
            webUrl = video.text("htmlUrl")?.let(hostPolicy::sanitize),
        )
    }

    /**
     * Un programa solo es reproducible a través de su último medio; su propio ID
     * no identifica ningún stream.
     */
    private fun playbackId(kind: ContentKind, nestedMedia: JsonObject?, itemId: String): String? =
        when {
            nestedMedia != null -> nestedMedia.text("id")
            kind == ContentKind.PROGRAM || kind == ContentKind.UNKNOWN -> null
            else -> itemId
        }

    private fun contentKind(
        contentType: String?,
        editorialType: String?,
        isProgramWithMedia: Boolean,
        assetId: String?,
    ): ContentKind {
        if (editorialType?.lowercase() in LIVE_EDITORIAL_TYPES) {
            return ContentKind.LIVE
        }
        return when (contentType?.lowercase()) {
            "audio" -> ContentKind.AUDIO
            "video" -> if (isProgramWithMedia) ContentKind.PROGRAM else ContentKind.VIDEO
            "program", "programa" -> ContentKind.PROGRAM
            "directo", "live", "broadcast" -> ContentKind.LIVE
            // Sin tipo declarado, un idAsset es la única pista de que se trata de un directo.
            null -> if (assetId != null) ContentKind.LIVE else ContentKind.UNKNOWN
            else -> ContentKind.UNKNOWN
        }
    }

    /*
     * Imágenes por proporción, medidas el 12-09-2026: `imgPortada`, `thumb` y
     * `previews.horizontal*` son 16:9; `imgPoster`, `thumb_vertical` y
     * `previews.vertical*` 2:3; `imgBackground`, `thumb_square` y `previews.square*`
     * 1:1; `imgCol` 1:2 e `imgBanner` 4:1. Para vídeos, programas y audios el
     * servicio por id devuelve siempre la variante pedida y admite `w=`.
     */
    private fun landscapeImage(kind: ContentKind, id: String, media: JsonObject, item: JsonObject): String? = when (kind) {
        ContentKind.VIDEO -> derivedImage("v", id, "horizontal2", LANDSCAPE_WIDTH)
        ContentKind.PROGRAM -> derivedImage("p", id, "imgPortada", LANDSCAPE_WIDTH)
        ContentKind.AUDIO -> squareImage(kind, id, media, item)
        else -> firstAllowed(
            item.text("thumb", "imagen", "imgPortada", "imgPortada2", "thumbnail"),
            media.obj("previews")?.text("horizontal2", "horizontal"),
            item.text("imageSEO", "imgBackground"),
        )
    }

    private fun posterImage(kind: ContentKind, id: String, media: JsonObject, item: JsonObject): String? = when (kind) {
        ContentKind.VIDEO -> derivedImage("v", id, "vertical", POSTER_WIDTH)
        ContentKind.PROGRAM -> derivedImage("p", id, "imgPoster", POSTER_WIDTH)
        ContentKind.AUDIO -> null
        else -> firstAllowed(
            item.text("thumb_vertical", "imgPoster", "imgPoster2"),
            media.obj("previews")?.text("vertical2", "vertical"),
        )
    }

    private fun squareImage(kind: ContentKind, id: String, media: JsonObject, item: JsonObject): String? = when (kind) {
        ContentKind.PROGRAM -> derivedImage("p", id, "imgBackground", SQUARE_WIDTH)
        ContentKind.AUDIO -> firstAllowed(
            media.obj("previews")?.text("square", "square2"),
            item.text("imgBackground", "imagePodcast"),
        ) ?: derivedImage("a", id, null, SQUARE_WIDTH)
        ContentKind.VIDEO -> null
        else -> firstAllowed(item.text("thumb_square", "imgBackground"), media.obj("previews")?.text("square", "square2"))
    }

    private fun firstAllowed(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { it?.let(hostPolicy::sanitize) }

    private fun derivedImage(type: String, id: String, variant: String?, width: Int): String = when (type) {
        "p" -> "$IMAGE_SERVICE/p/$id?imgProgApi=$variant&w=$width"
        "v" -> "$IMAGE_SERVICE/v/$id/$variant?w=$width"
        else -> "$IMAGE_SERVICE/$type/$id?w=$width"
    }

    /**
     * Los canales FAST llegan sin `titulo`; su descripción empieza por el nombre
     * ("Canal RTVE La Promesa: ...") y, si no, queda el permalink ("play-promesa").
     */
    private fun fallbackTitle(item: JsonObject): String? {
        val description = item.text("descripcion", "description")
            ?.substringBefore(':')
            ?.substringBefore('.')
            ?.trim()
            ?.takeIf { it.length in 3..60 }
        return description ?: item.text("permalink")
            ?.split('-')
            ?.joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (get(key) as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun JsonObject.number(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        (get(key) as? JsonPrimitive)?.intOrNull
    }

    private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull

    private fun JsonObject.flag(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

    private companion object {
        val LIVE_EDITORIAL_TYPES = setOf("broadcast", "peticion", "directo")
        val NON_CATALOG_ROWS = setOf("links", "noticias", "parrilla")
        const val IMAGE_SERVICE = "https://img.rtve.es"
        const val LANDSCAPE_WIDTH = 960
        const val POSTER_WIDTH = 480
        const val SQUARE_WIDTH = 480
        const val BACKDROP_WIDTH = 1600
    }
}
