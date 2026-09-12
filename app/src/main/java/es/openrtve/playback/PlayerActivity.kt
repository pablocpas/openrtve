package es.openrtve.playback

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.google.common.util.concurrent.ListenableFuture
import android.media.MediaDrm
import es.openrtve.OpenRtveApplication
import es.openrtve.R
import es.openrtve.domain.PlaybackDecision
import es.openrtve.ui.theme.OpenRtveTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var errorRes by mutableStateOf<Int?>(null)
    private var inPictureInPicture by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var showUnlock by mutableStateOf(false)
    private var feedback by mutableStateOf<GestureFeedback?>(null)
    private var title by mutableStateOf("")
    private var liveState by mutableStateOf<LiveState?>(null)
    private var request: PlaybackRequest? = null
    internal var playerView: PlayerView? = null
    private var liveTicker: Job? = null

    /** Estado del directo: en el borde en vivo o rezagado dentro de la ventana DVR. */
    internal data class LiveState(val atLiveEdge: Boolean)
    private var startJob: Job? = null
    /** Ya se probó la variante sin DRM de la petición actual; no hay más alternativas. */
    private var usedFallback = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            title = mediaMetadata.title?.toString().orEmpty()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refreshLiveState()

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = refreshLiveState()

        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshLiveState()

        override fun onPlayerError(error: PlaybackException) {
            val player = controller ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Contrato documentado: reconstruir el item y volver al borde en directo.
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            val fallback = request?.fallbackUri
            if (error.isDrmFailure() && fallback != null && !usedFallback) {
                // Igual que el cliente oficial en dispositivos sin Widevine: variante de compatibilidad.
                usedFallback = true
                play(player, request?.toMediaItem(uri = fallback, licenseUrl = null) ?: return)
                return
            }
            errorRes = error.toMessageRes()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestNotificationPermissionIfNeeded()

        request = PlaybackRequest.from(intent)
        applyPresentation()

        setContent {
            OpenRtveTheme {
                PlaybackScreen(
                    controller = controller,
                    errorRes = errorRes,
                    title = title,
                    liveState = liveState,
                    onGoLive = { controller?.seekToDefaultPosition() },
                    controlsEnabled = !inPictureInPicture && !locked,
                    locked = locked,
                    showUnlock = showUnlock && !inPictureInPicture,
                    feedback = feedback,
                    canPictureInPicture = canEnterPictureInPicture() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                    onBack = ::finish,
                    onPictureInPicture = ::enterPictureInPictureNow,
                    onLock = { locked = true; showUnlock = true },
                    onUnlock = { locked = false; showUnlock = false },
                    onLockedTap = { showUnlock = true },
                    onFeedback = { feedback = it },
                    onUnlockShown = { showUnlock = false },
                )
            }
        }
        connect()
    }

    /**
     * En TV el mando envía teclas a la vista con foco, que suele ser la raíz de
     * Compose: se reenvían al PlayerView para que muestre los controles y, una
     * vez visibles, el foco pasa a sus botones.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val view = playerView
        if (view != null && !locked && view.useController && view.dispatchKeyEvent(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun enterPictureInPictureNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canEnterPictureInPicture()) {
            enterPictureInPictureMode(pictureInPictureParams().build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = PlaybackRequest.from(intent)
        usedFallback = false
        applyPresentation()
        errorRes = null
        controller?.let(::startRequested)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            canEnterPictureInPicture() &&
            settings.current.pictureInPictureOnLeave &&
            controller?.isPlaying == true
        ) {
            enterPictureInPictureMode(pictureInPictureParams().build())
        }
    }

    /**
     * Comportamiento estándar de un reproductor de vídeo: al salir de la pantalla
     * se pausa, y al cerrarla (atrás, o cerrar la ventana PiP) se detiene. El audio
     * (radio, pódcast) sigue en segundo plano con su notificación, salvo que el
     * usuario lo desactive en ajustes.
     */
    override fun onStop() {
        super.onStop()
        val player = controller ?: return
        val keepInBackground = request?.isAudioOnly == true && settings.current.backgroundAudio
        if (keepInBackground) return
        val inPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        when {
            isFinishing -> {
                player.stop()
                player.clearMediaItems()
            }
            !inPictureInPicture -> player.pause()
        }
    }

    private fun refreshLiveState() {
        val player = controller ?: return
        liveState = if (player.isCurrentMediaItemLive) {
            LiveState(atLiveEdge = player.currentLiveOffset in 0..LIVE_EDGE_TOLERANCE_MS || player.currentLiveOffset == C.TIME_UNSET)
        } else {
            null
        }
    }

    override fun onDestroy() {
        liveTicker?.cancel()
        startJob?.cancel()
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onDestroy()
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connected ->
                        connected.addListener(playerListener)
                        title = connected.mediaMetadata.title?.toString().orEmpty()
                        applyTrackPreferences(connected)
                        controller = connected
                        startRequested(connected)
                        liveTicker = lifecycleScope.launch {
                            while (true) {
                                delay(LIVE_TICK_MS)
                                refreshLiveState()
                            }
                        }
                    }
                    .onFailure { errorRes = R.string.player_error_connect }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    /**
     * Solo carga el item si difiere del que ya tiene la sesión: al rotar, entrar en PiP
     * o volver desde la notificación se reanuda en vez de empezar de cero.
     */
    private fun startRequested(player: MediaController) {
        val request = request
        if (request == null) {
            if (player.currentMediaItem == null) {
                errorRes = R.string.player_error_nothing
            }
            return
        }
        val current = player.currentMediaItem?.mediaId
        if (current == request.uri || (request.fallbackUri != null && current == request.fallbackUri)) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            return
        }
        startJob?.cancel()
        startJob = lifecycleScope.launch {
            if (request.drmTokenUrl != null && !isWidevineAvailable() && request.fallbackUri != null) {
                usedFallback = true
                play(player, request.toMediaItem(uri = request.fallbackUri, licenseUrl = null))
                return@launch
            }
            // La licencia es temporal: se pide justo antes de reproducir y no se guarda.
            val licenseUrl = request.drmTokenUrl?.let { tokenUrl ->
                try {
                    withContext(Dispatchers.IO) {
                        (application as OpenRtveApplication).container.drmTokenClient.widevineLicenseUrl(tokenUrl)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Sin licencia solo fallará el contenido protegido; el resto reproduce igual.
                    null
                }
            }
            play(player, request.toMediaItem(uri = request.uri, licenseUrl = licenseUrl))
        }
    }

    private val settings get() = (application as OpenRtveApplication).container.settings

    private fun applyTrackPreferences(player: MediaController) {
        val prefs = settings.current
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (prefs.dataSaver) setMaxVideoSize(DATA_SAVER_MAX_WIDTH, DATA_SAVER_MAX_HEIGHT) else clearVideoSizeConstraints()
                setPreferredTextLanguages(*if (prefs.subtitlesByDefault) arrayOf("es", "spa") else emptyArray())
            }
            .build()
    }

    private fun play(player: MediaController, item: MediaItem) {
        errorRes = null
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private fun isWidevineAvailable(): Boolean =
        runCatching { MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID) }.getOrDefault(false)

    private fun PlaybackException.isDrmFailure(): Boolean =
        errorCode in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED

    private fun applyPresentation() {
        val isVideo = request?.isAudioOnly == false
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (isVideo) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                pictureInPictureParams()
                    .setAutoEnterEnabled(isVideo && canEnterPictureInPicture() && settings.current.pictureInPictureOnLeave)
                    .setSeamlessResizeEnabled(false)
                    .build(),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pictureInPictureParams(): PictureInPictureParams.Builder =
        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))

    private fun canEnterPictureInPicture(): Boolean =
        request?.isAudioOnly == false &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @StringRes
    private fun PlaybackException.toMessageRes(): Int {
        val httpStatus = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        return when {
            isDrmFailure() -> R.string.player_error_drm
            httpStatus == 403 -> R.string.player_error_geo
            httpStatus == 404 || httpStatus == 410 -> R.string.player_error_not_found
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.player_error_offline
            else -> R.string.player_error_generic
        }
    }

    private data class PlaybackRequest(
        val uri: String,
        val mimeType: String,
        val title: String,
        val drmTokenUrl: String?,
        val fallbackUri: String?,
    ) {
        val isAudioOnly: Boolean get() = mimeType.startsWith("audio/")

        fun toMediaItem(uri: String, licenseUrl: String?): MediaItem = MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .apply {
                if (licenseUrl != null) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(licenseUrl)
                            .build(),
                    )
                }
            }
            .build()

        companion object {
            fun from(intent: Intent): PlaybackRequest? {
                val uri = intent.getStringExtra(EXTRA_URI)?.takeIf(String::isNotBlank) ?: return null
                val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)?.takeIf(String::isNotBlank) ?: return null
                return PlaybackRequest(
                    uri = uri,
                    mimeType = mimeType,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    drmTokenUrl = intent.getStringExtra(EXTRA_DRM_TOKEN_URL),
                    fallbackUri = intent.getStringExtra(EXTRA_FALLBACK_URI),
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "playback_uri"
        private const val EXTRA_MIME_TYPE = "playback_mime_type"
        private const val EXTRA_TITLE = "playback_title"
        private const val EXTRA_DRM_TOKEN_URL = "playback_drm_token_url"
        private const val EXTRA_FALLBACK_URI = "playback_fallback_uri"

        fun intent(context: Context, decision: PlaybackDecision.Ready): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URI, decision.uri)
                putExtra(EXTRA_MIME_TYPE, decision.mimeType)
                putExtra(EXTRA_TITLE, decision.title)
                putExtra(EXTRA_DRM_TOKEN_URL, decision.drm?.tokenUrl)
                putExtra(EXTRA_FALLBACK_URI, decision.fallbackUri)
            }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlaybackScreen(
    controller: MediaController?,
    @StringRes errorRes: Int?,
    title: String,
    liveState: PlayerActivity.LiveState?,
    onGoLive: () -> Unit,
    controlsEnabled: Boolean,
    locked: Boolean,
    showUnlock: Boolean,
    feedback: GestureFeedback?,
    canPictureInPicture: Boolean,
    onBack: () -> Unit,
    onPictureInPicture: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onLockedTap: () -> Unit,
    onFeedback: (GestureFeedback?) -> Unit,
    onUnlockShown: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            errorRes != null -> Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(24.dp),
            )
            controller == null -> CircularProgressIndicator()
            else -> AndroidView(
                factory = { context ->
                    val view = LayoutInflater.from(context).inflate(R.layout.player_view, null) as PlayerView
                    val activity = context as PlayerActivity
                    view.findViewById<ImageButton>(R.id.player_back).setOnClickListener { onBack() }
                    view.findViewById<ImageButton>(R.id.player_lock).setOnClickListener {
                        view.hideController()
                        onLock()
                    }
                    view.findViewById<ImageButton>(R.id.player_pip).apply {
                        visibility = if (canPictureInPicture) View.VISIBLE else View.GONE
                        setOnClickListener { onPictureInPicture() }
                    }
                    view.findViewById<ImageButton>(R.id.player_aspect).setOnClickListener {
                        view.resizeMode = if (view.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                    activity.playerView = view
                    view.setOnTouchListener(
                        PlayerGestures(
                            playerView = view,
                            window = activity.window,
                            isLocked = { !view.useController },
                            onLockedTap = onLockedTap,
                            onFeedback = onFeedback,
                        ),
                    )
                    view
                },
                update = { view ->
                    view.player = controller
                    view.findViewById<TextView>(R.id.player_title).text = title
                    view.useController = controlsEnabled
                    if (!controlsEnabled) view.hideController()
                    // Directo: sin tiempos absolutos (la ventana DVR no es una duración) y con indicador en vivo.
                    val live = view.findViewById<TextView>(R.id.player_live)
                    val isLive = liveState != null
                    view.findViewById<TextView>(androidx.media3.ui.R.id.exo_position).visibility = if (isLive) View.GONE else View.VISIBLE
                    view.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration).visibility = if (isLive) View.GONE else View.VISIBLE
                    live.visibility = if (isLive) View.VISIBLE else View.GONE
                    if (liveState != null) {
                        live.setText(if (liveState.atLiveEdge) R.string.player_live else R.string.player_go_live)
                        live.alpha = if (liveState.atLiveEdge) 1f else 0.6f
                        live.setOnClickListener { if (!liveState.atLiveEdge) onGoLive() }
                    }
                },
                onRelease = { view ->
                    view.player = null
                    (view.context as? PlayerActivity)?.playerView = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        feedback?.let {
            Text(
                text = when (it) {
                    is GestureFeedback.Brightness -> stringResource(R.string.player_brightness, it.percent)
                    is GestureFeedback.Volume -> stringResource(R.string.player_volume, it.percent)
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (locked && showUnlock) {
            LaunchedEffect(showUnlock) {
                delay(UNLOCK_VISIBLE_MS)
                onUnlockShown()
            }
            IconButton(
                onClick = onUnlock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_unlock),
                    contentDescription = stringResource(R.string.player_unlock),
                    tint = Color.White,
                )
            }
        }
    }
}

private const val UNLOCK_VISIBLE_MS = 3_000L
private const val LIVE_TICK_MS = 1_000L
private const val LIVE_EDGE_TOLERANCE_MS = 20_000L
private const val DATA_SAVER_MAX_WIDTH = 1024
private const val DATA_SAVER_MAX_HEIGHT = 576
