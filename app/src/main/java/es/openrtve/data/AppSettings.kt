package es.openrtve.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Settings(
    /** Al salir del reproductor con un vídeo en marcha, seguir en una ventana flotante. */
    val pictureInPictureOnLeave: Boolean = true,
    /** Radio y pódcasts siguen sonando al salir del reproductor, con su notificación. */
    val backgroundAudio: Boolean = true,
    /** Altura máxima de vídeo (0 = automática). 576 equivale al ahorro de datos de la app oficial. */
    val maxVideoHeight: Int = 0,
    /** Activar subtítulos en español cuando existan. */
    val subtitlesByDefault: Boolean = false,
    /** Encadenar el siguiente episodio al terminar. */
    val autoplayNext: Boolean = true,
)

/**
 * Preferencias del usuario en `SharedPreferences`, expuestas como `StateFlow`.
 * Son cuatro booleanos: DataStore no aporta nada todavía.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = mutableSettings.asStateFlow()

    val current: Settings get() = mutableSettings.value

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(current)
        prefs.edit {
            putBoolean(KEY_PIP, updated.pictureInPictureOnLeave)
            putBoolean(KEY_BACKGROUND_AUDIO, updated.backgroundAudio)
            putInt(KEY_MAX_HEIGHT, updated.maxVideoHeight)
            putBoolean(KEY_SUBTITLES, updated.subtitlesByDefault)
            putBoolean(KEY_AUTOPLAY, updated.autoplayNext)
        }
        mutableSettings.update { updated }
    }

    private fun read() = Settings(
        pictureInPictureOnLeave = prefs.getBoolean(KEY_PIP, true),
        backgroundAudio = prefs.getBoolean(KEY_BACKGROUND_AUDIO, true),
        maxVideoHeight = prefs.getInt(KEY_MAX_HEIGHT, 0),
        subtitlesByDefault = prefs.getBoolean(KEY_SUBTITLES, false),
        autoplayNext = prefs.getBoolean(KEY_AUTOPLAY, true),
    )

    private companion object {
        const val PREFS_NAME = "openrtve_settings"
        const val KEY_PIP = "pip_on_leave"
        const val KEY_BACKGROUND_AUDIO = "background_audio"
        const val KEY_MAX_HEIGHT = "max_video_height"
        const val KEY_AUTOPLAY = "autoplay_next"
        const val KEY_SUBTITLES = "subtitles_by_default"
    }
}
