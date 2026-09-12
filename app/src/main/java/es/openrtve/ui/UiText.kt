package es.openrtve.ui

import android.content.Context
import es.openrtve.R
import es.openrtve.domain.BlockReason
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.VideoDetail

fun LoadError.text(context: Context): String = when (this) {
    LoadError.Offline -> context.getString(R.string.error_offline)
    LoadError.Timeout -> context.getString(R.string.error_timeout)
    is LoadError.Http -> context.getString(R.string.error_http, statusCode)
    LoadError.Unknown -> context.getString(R.string.error_unknown)
}

fun BlockReason.text(context: Context): String = when (this) {
    BlockReason.GEO_RESTRICTED -> context.getString(R.string.blocked_geo)
    BlockReason.LOGIN_REQUIRED -> context.getString(R.string.blocked_login)
    BlockReason.SUBSCRIPTION_REQUIRED -> context.getString(R.string.blocked_paid)
    BlockReason.NO_SOURCE -> context.getString(R.string.blocked_no_source)
}

fun UiMessage.text(context: Context): String = when (this) {
    is UiMessage.Error -> error.text(context)
    is UiMessage.Blocked -> reason.text(context)
    is UiMessage.Text -> text
}

/** "Temporada 1 · E3 · 12/09/2026 · 52 min", omitiendo lo que falte. */
fun CatalogItem.metaLine(context: Context): String? = listOfNotNull(
    seasonTitle,
    episode?.let { context.getString(R.string.meta_episode, it) },
    publicationDate?.feedDateToDisplay(),
    durationMs?.takeIf { it > 0 }?.let { context.getString(R.string.meta_minutes, (it / 60_000L).toInt()) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Los bloques del menú oficial tienen nombres internos ("Bloque de contenidos"). */
fun exploreGroupTitle(context: Context, raw: String): String = when {
    raw.contains("infantil", ignoreCase = true) -> context.getString(R.string.explore_kids)
    raw.equals("Radio", ignoreCase = true) -> context.getString(R.string.explore_radio)
    else -> context.getString(R.string.explore_topics)
}

/** "2018 · 108 min · No recomendable para menores de 12 años", omitiendo lo que falte. */
fun VideoDetail.metaLine(context: Context): String? = listOfNotNull(
    year,
    item.durationMs?.takeIf { it > 0 }?.let { context.getString(R.string.meta_minutes, (it / 60_000L).toInt()) },
    ageRating,
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Fecha del feed (`dd-MM-yyyy HH:mm:ss`) como `dd/MM/yyyy`. */
fun String.feedDateToDisplay(): String = take(10).replace('-', '/')

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1L shl 10 -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}
