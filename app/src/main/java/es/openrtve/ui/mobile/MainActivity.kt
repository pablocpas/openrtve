package es.openrtve.ui.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import es.openrtve.OpenRtveApplication
import es.openrtve.ui.theme.OpenRtveTheme
import es.openrtve.ui.tv.TvActivity
import androidx.compose.runtime.mutableStateOf

private val URL_PATTERN = Regex("""https?://[^\s<>"]+""")

class MainActivity : ComponentActivity() {
    /** Enlace recibido por VIEW o por SEND; la UI lo consume y lo pone a null. */
    private val pendingLink = mutableStateOf<String?>(null)

    private fun linkFrom(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { text -> URL_PATTERN.find(text)?.value }
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { pendingLink.value = it }
    }

    private fun isTelevision(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Algunos launchers de TV abren la actividad LAUNCHER en vez de la LEANBACK_LAUNCHER.
        if (isTelevision()) {
            startActivity(Intent(this, TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        // Fondo siempre oscuro: iconos claros en las barras del sistema, sea cual sea el tema del móvil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as OpenRtveApplication).container
        if (savedInstanceState == null) linkFrom(intent)?.let { pendingLink.value = it }
        setContent {
            OpenRtveTheme {
                MobileApp(
                    container = container,
                    pendingLink = pendingLink.value,
                    onLinkConsumed = { pendingLink.value = null },
                )
            }
        }
    }
}
