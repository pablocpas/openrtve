package es.openrtve.ui

/**
 * Qué enseña el cuerpo de la ficha de un programa, decidido en un solo sitio y sin
 * Compose de por medio: se prueba con tests de unidad.
 *
 * Misma forma que en LibreTres. La cabecera se pinta siempre con lo que se sabe de
 * la tarjeta; el cuerpo espera a saber qué capítulos hay, para no pasar por un
 * estado intermedio ("no hay vídeos") que luego cambia.
 */
sealed interface ProgramBody {
    /** Todavía no se sabe qué capítulos hay. */
    data object Loading : ProgramBody

    /** Hay capítulos (o fragmentos, si el programa no tiene emisiones completas). */
    data object Episodes : ProgramBody

    /**
     * La ficha se ve, pero no hay capítulos que listar: [error] si la petición
     * falló, o el aviso de que el programa no tiene vídeos.
     */
    data class NoEpisodes(val error: LoadError?) : ProgramBody
}

/** Decisión del cuerpo de la ficha, a partir del estado. */
fun programBody(state: ProgramUiState): ProgramBody = when {
    state.isLoading && state.episodes.isEmpty() -> ProgramBody.Loading
    state.episodes.isNotEmpty() -> ProgramBody.Episodes
    else -> ProgramBody.NoEpisodes(state.error)
}
