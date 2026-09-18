package es.openrtve.ui

import android.content.Context
import android.content.Intent

/** "Compartir" de una ficha: título como asunto y su página web como texto. */
fun Context.shareLink(title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(intent, title))
}
