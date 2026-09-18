package es.openrtve.ui.mobile

import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.openrtve.OpenRtveApplication
import es.openrtve.ui.LinkInbox
import es.openrtve.ui.theme.OpenRtveTheme
import es.openrtve.ui.tv.TvActivity

class MainActivity : ComponentActivity() {
    private val links = LinkInbox()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        links.offer(intent)
    }

    private fun isTelevision(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Algunos launchers de TV abren la actividad LAUNCHER en vez de la LEANBACK_LAUNCHER;
        // el intent (con su enlace, si lo trae) se reenvía tal cual.
        if (isTelevision()) {
            startActivity(
                Intent(this, TvActivity::class.java).apply {
                    action = intent.action
                    data = intent.data
                    intent.extras?.let(::putExtras)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            finish()
            return
        }
        // Fondo siempre oscuro: iconos claros en las barras del sistema, sea cual sea el tema del móvil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as OpenRtveApplication).container
        if (savedInstanceState == null) links.offer(intent)
        setContent {
            OpenRtveTheme {
                MobileApp(container = container, incomingLinks = links.links)
            }
        }
    }
}
