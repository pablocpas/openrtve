package es.openrtve.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.openrtve.AppContainer
import es.openrtve.R
import es.openrtve.ui.SettingsViewModel
import es.openrtve.ui.formatBytes

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.documentCache, context.applicationContext) }
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val cleared = stringResource(R.string.settings_cache_cleared)
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel(stringResource(R.string.settings_playback))
            SwitchRow(
                title = stringResource(R.string.settings_pip),
                summary = stringResource(R.string.settings_pip_summary),
                checked = settings.pictureInPictureOnLeave,
            ) { value -> viewModel.update { it.copy(pictureInPictureOnLeave = value) } }
            SwitchRow(
                title = stringResource(R.string.settings_background_audio),
                summary = stringResource(R.string.settings_background_audio_summary),
                checked = settings.backgroundAudio,
            ) { value -> viewModel.update { it.copy(backgroundAudio = value) } }
            SwitchRow(
                title = stringResource(R.string.settings_data_saver),
                summary = stringResource(R.string.settings_data_saver_summary),
                checked = settings.dataSaver,
            ) { value -> viewModel.update { it.copy(dataSaver = value) } }
            SwitchRow(
                title = stringResource(R.string.settings_subtitles),
                summary = stringResource(R.string.settings_subtitles_summary),
                checked = settings.subtitlesByDefault,
            ) { value -> viewModel.update { it.copy(subtitlesByDefault = value) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel(stringResource(R.string.settings_storage))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_cache_size, cacheBytes?.let(::formatBytes) ?: "…"))
                },
                modifier = Modifier.clickable {
                    viewModel.clearCache()
                    onMessage(cleared)
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, versionName)) },
                supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

