package es.openrtve.ui.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.openrtve.OpenRtveApplication
import es.openrtve.ui.LinkInbox
import es.openrtve.ui.theme.OpenRtveTvTheme

class TvActivity : ComponentActivity() {
    private val links = LinkInbox()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        links.offer(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as OpenRtveApplication).container
        if (savedInstanceState == null) links.offer(intent)
        setContent {
            OpenRtveTvTheme {
                TvApp(container, incomingLinks = links.links)
            }
        }
    }
}
