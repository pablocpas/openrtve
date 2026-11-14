package es.openrtve.ui.mobile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import es.openrtve.BuildConfig
import es.openrtve.R
import es.openrtve.data.AppUpdates
import es.openrtve.data.UpdateState
import es.openrtve.ui.rememberUpdateState

/** Aviso de versión nueva y progreso de la descarga (móvil). */
@Composable
fun UpdatePrompt(updates: AppUpdates, onMessage: (String) -> Unit) {
    when (val state = rememberUpdateState(updates, onMessage)) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { updates.snooze(state.release) },
            title = { Text(stringResource(R.string.update_available_title, state.release.version)) },
            text = { Text(stringResource(R.string.update_available_text, BuildConfig.VERSION_NAME)) },
            confirmButton = { TextButton(onClick = { updates.update(state.release) }) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = { updates.snooze(state.release) }) { Text(stringResource(R.string.update_later)) } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.update_downloading, state.release.version)) },
            text = {
                val progress = state.progress
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = updates::cancelDownload) { Text(stringResource(R.string.update_cancel)) } },
        )
        else -> Unit
    }
}
