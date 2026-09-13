package es.openrtve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Reloj compartido (cada 30 s) para el progreso de los directos y las etiquetas de horario. */
val LocalNowMillis = compositionLocalOf { System.currentTimeMillis() }

@Composable
fun rememberNowMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}
