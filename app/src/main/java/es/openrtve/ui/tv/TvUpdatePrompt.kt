package es.openrtve.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import es.openrtve.R
import es.openrtve.data.AppUpdates
import es.openrtve.data.UpdateState
import es.openrtve.ui.rememberUpdateState

/** Aviso de versión nueva y progreso de la descarga (TV), con el mando. */
@Composable
fun TvUpdatePrompt(updates: AppUpdates, onMessage: (String) -> Unit) {
    when (val state = rememberUpdateState(updates, onMessage)) {
        is UpdateState.Available -> TvOptionsDialog(
            title = stringResource(R.string.update_available_title, state.release.version),
            options = listOf(
                TvOption(stringResource(R.string.update_now)) { updates.update(state.release) },
                TvOption(stringResource(R.string.update_later)) { updates.snooze(state.release) },
            ),
            onDismiss = { updates.snooze(state.release) },
        )
        is UpdateState.Downloading -> Dialog(onDismissRequest = updates::cancelDownload) {
            ProvideScreenInitialFocus {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, TvCardShape)
                        .padding(24.dp)
                        .width(420.dp),
                ) {
                    Text(stringResource(R.string.update_downloading, state.release.version), style = MaterialTheme.typography.titleLarge)
                    ProgressBar(state.progress ?: 0f)
                    Button(onClick = updates::cancelDownload, modifier = Modifier.initialFocus()) {
                        Text(stringResource(R.string.update_cancel))
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
