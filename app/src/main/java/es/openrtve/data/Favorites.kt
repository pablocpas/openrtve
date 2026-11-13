package es.openrtve.data

import androidx.core.util.AtomicFile
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Un título de "Mi lista" y cuándo se añadió, para poder deshacer un quitar sin cambiar el orden. */
class FavoriteEntry(val item: CatalogItem, val addedAtMillis: Long)

/**
 * "Mi lista" local, sin cuenta: programas, películas y vídeos que el usuario
 * guarda para después. Lo último añadido va primero.
 *
 * Como [WatchHistory]: el estado en memoria cambia al instante y el fichero se
 * escribe aparte, en [ioDispatcher] y de forma atómica.
 */
class Favorites(
    file: File,
    private val json: Json = Json,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file = AtomicFile(file)
    private val entries = MutableStateFlow(load())
    private val mutableItems = MutableStateFlow(entries.value.map { it.item })
    val items: StateFlow<List<CatalogItem>> = mutableItems.asStateFlow()

    private val writer = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writes = Channel<Unit>(Channel.CONFLATED)

    init {
        writer.launch { for (ignored in writes) persist(entries.value) }
    }

    fun contains(itemId: String): Boolean = entries.value.any { it.item.id == itemId }

    /** Los directos no se guardan: lo que se emite ahora no es lo que habrá después. */
    fun canAdd(item: CatalogItem): Boolean = item.kind == ContentKind.PROGRAM || item.kind == ContentKind.VIDEO

    /** Añade o quita; devuelve si queda en la lista. */
    fun toggle(item: CatalogItem): Boolean {
        if (contains(item.id)) {
            remove(item.id)
            return false
        }
        if (!canAdd(item)) return false
        save(listOf(FavoriteEntry(item, nowMillis())) + entries.value)
        return true
    }

    /** Quita el título y lo devuelve, para poder deshacerlo con [restore]. */
    fun remove(itemId: String): FavoriteEntry? {
        val removed = entries.value.firstOrNull { it.item.id == itemId } ?: return null
        save(entries.value.filterNot { it.item.id == itemId })
        return removed
    }

    /** Vuelve a poner un título quitado en su sitio. */
    fun restore(entry: FavoriteEntry) = save(entries.value.filterNot { it.item.id == entry.item.id } + entry)

    private fun save(updated: List<FavoriteEntry>) {
        val trimmed = updated.distinctBy { it.item.id }.sortedByDescending { it.addedAtMillis }.take(MAX_ENTRIES)
        entries.value = trimmed
        mutableItems.value = trimmed.map { it.item }
        writes.trySend(Unit)
    }

    private fun persist(entries: List<FavoriteEntry>) {
        val bytes = buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    putCatalogItem(entry.item)
                    put("addedAt", entry.addedAtMillis)
                })
            }
        }.toString().toByteArray(Charsets.UTF_8)
        file.baseFile.parentFile?.mkdirs()
        val output = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
        }
    }

    private fun load(): List<FavoriteEntry> = runCatching {
        if (!file.baseFile.isFile) return emptyList()
        json.parseToJsonElement(file.readFully().toString(Charsets.UTF_8)).jsonArray
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val item = obj.toCatalogItem() ?: return@mapNotNull null
                FavoriteEntry(item, (obj["addedAt"] as? JsonPrimitive)?.longOrNull ?: 0L)
            }
            .sortedByDescending { it.addedAtMillis }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_ENTRIES = 200
    }
}
