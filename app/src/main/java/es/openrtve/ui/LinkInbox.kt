package es.openrtve.ui

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private val URL_PATTERN = Regex("""https?://[^\s<>"]+""")

/** Enlace que trae un intent `VIEW` (la URL) o `SEND` (la primera URL del texto compartido). */
fun Intent.rtveLink(): String? = when (action) {
    Intent.ACTION_VIEW -> dataString
    Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)?.let { URL_PATTERN.find(it)?.value }
    else -> null
}

/**
 * Enlaces recibidos por una actividad, entregados una sola vez a la UI. Un
 * canal conflated basta: si llegan dos antes de que la UI escuche, vale el último.
 */
class LinkInbox {
    private val channel = Channel<String>(Channel.CONFLATED)
    val links: Flow<String> = channel.receiveAsFlow()

    fun offer(intent: Intent?) {
        intent?.rtveLink()?.let(channel::trySend)
    }
}
