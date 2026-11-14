package es.openrtve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.openrtve.R
import es.openrtve.data.AppUpdates
import es.openrtve.data.UpdateState

/**
 * Lo común a móvil y TV: comprobar si toca al abrir la app y al volver a ella (como
 * mucho cada pocas horas, ver [AppUpdates]) y contar con [onMessage] los resultados
 * que no necesitan diálogo. Devuelve el estado para que cada interfaz pinte el suyo.
 */
@Composable
fun rememberUpdateState(updates: AppUpdates, onMessage: (String) -> Unit): UpdateState {
    val state by updates.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(updates) {
        updates.checkIfDue()
        onPauseOrDispose {}
    }
    val upToDate = stringResource(R.string.update_up_to_date)
    val checking = stringResource(R.string.update_checking)
    val checkFailed = stringResource(R.string.update_check_failed)
    val downloadFailed = stringResource(R.string.update_download_failed)
    val installFailed = stringResource(R.string.update_install_failed)
    LaunchedEffect(state) {
        when (val current = state) {
            UpdateState.Checking -> onMessage(checking)
            UpdateState.UpToDate -> onMessage(upToDate)
            is UpdateState.Failed -> onMessage(
                when (current.reason) {
                    UpdateState.Failed.Reason.CHECK -> checkFailed
                    UpdateState.Failed.Reason.DOWNLOAD -> downloadFailed
                    UpdateState.Failed.Reason.INSTALL -> installFailed
                },
            )
            else -> Unit
        }
        // Lo que ya se ha contado (o lo que pasa a manos del instalador del sistema) no se repite.
        if (state is UpdateState.UpToDate || state is UpdateState.Failed || state is UpdateState.Installing) updates.acknowledge()
    }
    return state
}
