package es.openrtve.data

import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class WatchEntry(
    val item: CatalogItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMillis: Long,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * "Seguir viendo" local, sin cuenta: un fichero JSON con las últimas
 * reproducciones y su posición. Lo escribe el reproductor y lo lee la portada.
 */
class WatchHistory(
    private val file: File,
    private val json: Json = Json,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutableEntries = MutableStateFlow(load())
    val entries: StateFlow<List<WatchEntry>> = mutableEntries

    fun entryFor(itemId: String): WatchEntry? = mutableEntries.value.firstOrNull { it.item.id == itemId }

    /** Registra el item antes de reproducirlo para que el progreso tenga a qué asociarse. */
    fun register(item: CatalogItem) {
        if (item.kind == ContentKind.LIVE) return
        val existing = entryFor(item.id)
        save(
            (mutableEntries.value.filterNot { it.item.id == item.id } +
                WatchEntry(item, existing?.positionMs ?: 0L, existing?.durationMs ?: 0L, nowMillis())),
        )
    }

    fun updateProgress(itemId: String, positionMs: Long, durationMs: Long) {
        val entry = entryFor(itemId) ?: return
        if (durationMs <= 0) return
        // Terminado: fuera de la lista, como en cualquier app de streaming.
        if (positionMs >= durationMs * FINISHED_FRACTION || positionMs < MIN_POSITION_MS) {
            if (positionMs >= durationMs * FINISHED_FRACTION) remove(itemId)
            return
        }
        save(
            mutableEntries.value.filterNot { it.item.id == itemId } +
                entry.copy(positionMs = positionMs, durationMs = durationMs, updatedAtMillis = nowMillis()),
        )
    }

    fun remove(itemId: String) = save(mutableEntries.value.filterNot { it.item.id == itemId })

    /** Solo lo que tiene progreso real, lo más reciente primero. */
    val resumable: List<WatchEntry>
        get() = mutableEntries.value.filter { it.positionMs >= MIN_POSITION_MS }.sortedByDescending { it.updatedAtMillis }

    private fun save(entries: List<WatchEntry>) {
        val trimmed = entries
            .filter { nowMillis() - it.updatedAtMillis < MAX_AGE_MS }
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_ENTRIES)
        mutableEntries.value = trimmed
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(buildJsonArray { trimmed.forEach { add(it.toJson()) } }.toString())
        }
    }

    private fun load(): List<WatchEntry> = runCatching {
        if (!file.isFile) return emptyList()
        json.parseToJsonElement(file.readText()).jsonArray
            .mapNotNull { (it as? JsonObject)?.toEntry() }
            .sortedByDescending { it.updatedAtMillis }
    }.getOrDefault(emptyList())

    private fun WatchEntry.toJson(): JsonObject = buildJsonObject {
        put("id", item.id)
        item.playbackId?.let { put("playbackId", it) }
        item.assetId?.let { put("assetId", it) }
        put("title", item.title)
        item.subtitle?.let { put("subtitle", it) }
        item.imageUrl?.let { put("imageUrl", it) }
        item.posterUrl?.let { put("posterUrl", it) }
        put("kind", item.kind.name)
        item.programId?.let { put("programId", it) }
        item.directQualityUrl?.let { put("directQualityUrl", it) }
        put("drm", item.drm)
        put("paid", item.paid)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("updatedAt", updatedAtMillis)
    }

    private fun JsonObject.toEntry(): WatchEntry? {
        fun text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
        fun long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
        val id = text("id") ?: return null
        val item = CatalogItem(
            id = id,
            playbackId = text("playbackId"),
            assetId = text("assetId"),
            title = text("title") ?: return null,
            subtitle = text("subtitle"),
            imageUrl = text("imageUrl"),
            kind = runCatching { ContentKind.valueOf(text("kind").orEmpty()) }.getOrDefault(ContentKind.VIDEO),
            posterUrl = text("posterUrl"),
            directQualityUrl = text("directQualityUrl"),
            allowedInCountry = null,
            loginRequired = false,
            paid = (get("paid") as? JsonPrimitive)?.booleanOrNull ?: false,
            drm = (get("drm") as? JsonPrimitive)?.booleanOrNull ?: false,
            programId = text("programId"),
        )
        return WatchEntry(item, long("positionMs") ?: 0L, long("durationMs") ?: 0L, long("updatedAt") ?: 0L)
    }

    private companion object {
        const val MAX_ENTRIES = 30
        const val MAX_AGE_MS = 60L * 24 * 60 * 60 * 1_000
        const val MIN_POSITION_MS = 30_000L
        const val FINISHED_FRACTION = 0.95
    }
}
