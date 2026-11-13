package es.openrtve.data

import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * Un item del catálogo guardado en local ("Seguir viendo", "Mi lista"): lo justo
 * para pintar su tarjeta, abrir su ficha y reproducirlo sin volver a pedirlo.
 * Las claves son las de siempre del historial: cambiarlas lo vaciaría.
 */

internal fun JsonObjectBuilder.putCatalogItem(item: CatalogItem) {
    put("id", item.id)
    item.playbackId?.let { put("playbackId", it) }
    item.assetId?.let { put("assetId", it) }
    put("title", item.title)
    item.subtitle?.let { put("subtitle", it) }
    item.imageUrl?.let { put("imageUrl", it) }
    item.posterUrl?.let { put("posterUrl", it) }
    put("kind", item.kind.name)
    item.programId?.let { put("programId", it) }
    item.programTypeId?.let { put("programTypeId", it) }
    item.programType?.let { put("programType", it) }
    item.mediaTypeId?.let { put("mediaTypeId", it) }
    item.mediaTypeName?.let { put("mediaTypeName", it) }
    item.mediaSubtypeId?.let { put("mediaSubtypeId", it) }
    item.mediaSubtypeName?.let { put("mediaSubtypeName", it) }
    item.seasonId?.let { put("seasonId", it) }
    item.seasonTitle?.let { put("seasonTitle", it) }
    item.episode?.let { put("episode", it) }
    put("drm", item.drm)
    put("paid", item.paid)
}

internal fun JsonObject.toCatalogItem(): CatalogItem? {
    fun text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    fun long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    val id = text("id") ?: return null
    return CatalogItem(
        id = id,
        playbackId = text("playbackId"),
        assetId = text("assetId"),
        title = text("title") ?: return null,
        subtitle = text("subtitle"),
        imageUrl = text("imageUrl"),
        kind = runCatching { ContentKind.valueOf(text("kind").orEmpty()) }.getOrDefault(ContentKind.VIDEO),
        posterUrl = text("posterUrl"),
        allowedInCountry = null,
        loginRequired = false,
        paid = (get("paid") as? JsonPrimitive)?.booleanOrNull ?: false,
        drm = (get("drm") as? JsonPrimitive)?.booleanOrNull ?: false,
        programId = text("programId"),
        programTypeId = text("programTypeId"),
        programType = text("programType"),
        mediaTypeId = text("mediaTypeId"),
        mediaTypeName = text("mediaTypeName"),
        mediaSubtypeId = text("mediaSubtypeId"),
        mediaSubtypeName = text("mediaSubtypeName"),
        seasonId = text("seasonId"),
        seasonTitle = text("seasonTitle"),
        episode = long("episode")?.toInt(),
    )
}
