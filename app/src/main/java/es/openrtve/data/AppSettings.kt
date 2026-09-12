package es.openrtve.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Settings(
    /** Al salir del reproductor con un vídeo en marcha, seguir en una ventana flotante. */
    val pictureInPictureOnLeave: Boolean = true,
    /** Radio y pódcasts siguen sonando al salir del reproductor, con su notificación. */
    val backgroundAudio: Boolean = true,
    /** Limitar el vídeo a 576p, como el modo de ahorro de la app oficial. */
    val dataSaver: Boolean = false,
    /** Activar subtítulos en español cuando existan. */
    val subtitlesByDefault: Boolean = false,
)

/**
 * Preferencias del usuario en `SharedPreferences`, expuestas como `StateFlow`.
 * Son cuatro booleanos: DataStore no aporta nada todavía.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = mutableSettings

    val current: Settings get() = mutableSettings.value

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(current)
        prefs.edit()
            .putBoolean(KEY_PIP, updated.pictureInPictureOnLeave)
            .putBoolean(KEY_BACKGROUND_AUDIO, updated.backgroundAudio)
            .putBoolean(KEY_DATA_SAVER, updated.dataSaver)
            .putBoolean(KEY_SUBTITLES, updated.subtitlesByDefault)
            .apply()
        mutableSettings.update { updated }
    }

    private fun read() = Settings(
        pictureInPictureOnLeave = prefs.getBoolean(KEY_PIP, true),
        backgroundAudio = prefs.getBoolean(KEY_BACKGROUND_AUDIO, true),
        dataSaver = prefs.getBoolean(KEY_DATA_SAVER, false),
        subtitlesByDefault = prefs.getBoolean(KEY_SUBTITLES, false),
    )

    private companion object {
        const val PREFS_NAME = "openrtve_settings"
        const val KEY_PIP = "pip_on_leave"
        const val KEY_BACKGROUND_AUDIO = "background_audio"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_SUBTITLES = "subtitles_by_default"
    }
}
