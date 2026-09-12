package es.openrtve.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults

private val Accent = Color(0xFFFF6A47)
private val OnAccent = Color(0xFF240900)
private val Background = Color(0xFF090A0F)
private val OnBackground = Color(0xFFF2F2F5)
private val SurfaceColor = Color(0xFF141620)
private val SurfaceVariant = Color(0xFF232636)

private val MobileColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceColor,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnBackground.copy(alpha = 0.8f),
)

private val TvColors = androidx.tv.material3.darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceColor,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnBackground.copy(alpha = 0.8f),
)

@Composable
fun OpenRtveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors, content = content)
}

/**
 * Tema de TV. Envuelve el contenido en una `Surface` de tv-material para que
 * `LocalContentColor` sea claro (sin ella el texto sale negro) y aplica también el
 * tema de Material 3 móvil por si algún componente de esa librería se usa en TV.
 */
@Composable
fun OpenRtveTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors) {
        androidx.tv.material3.MaterialTheme(colorScheme = TvColors) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(containerColor = Background, contentColor = OnBackground),
            ) {
                content()
            }
        }
    }
}
