package es.openrtve.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.core.content.edit
import es.openrtve.BuildConfig
import es.openrtve.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Una versión publicada en GitHub Releases con su APK. */
data class AppRelease(
    val version: String,
    val apkUrl: String,
    /** SHA-256 en hexadecimal que publica GitHub para el APK, si lo da. */
    val sha256: String?,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** Solo tras buscar a mano: la UI lo cuenta y vuelve a [Idle]. */
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    /** El instalador del sistema ya está pidiendo confirmación. */
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val reason: Reason) : UpdateState {
        enum class Reason { CHECK, DOWNLOAD, INSTALL }
    }
}

/**
 * Actualizaciones de la app fuera de Google Play: la versión más reciente se consulta
 * en GitHub Releases, el APK se descarga comprobando su tamaño y su SHA-256 y lo
 * instala el instalador de paquetes del sistema, que pide confirmación (y, la primera
 * vez, permiso para instalar apps de esta fuente). Como está firmado con la misma
 * clave, se instala encima conservando los datos.
 *
 * Al abrir la app se comprueba como mucho cada [CHECK_INTERVAL_MS] (se puede apagar en
 * Ajustes) y una versión aplazada con "Más tarde" no se vuelve a ofrecer durante
 * [SNOOZE_MS]. Buscar a mano desde Ajustes ignora ambas cosas.
 */
class AppUpdates(
    context: Context,
    baseClient: OkHttpClient,
    private val settings: AppSettings,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloads = appContext.cacheDir.resolve("updates")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var job: Job? = null

    /** Sin la allowlist de RTVE: solo GitHub, comprobado en cada salto (las descargas redirigen a su CDN). */
    private val client = baseClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .addNetworkInterceptor(GitHubOnly)
        .build()

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** Al abrir la app: si toca y está activado en Ajustes. */
    fun checkIfDue() {
        if (!settings.current.checkUpdates || job?.isActive == true) return
        if (nowMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        job = scope.launch {
            val release = runCatching { latestRelease() }.getOrNull() ?: return@launch
            prefs.edit { putLong(KEY_LAST_CHECK, nowMillis()) }
            if (!isNewer(release.version, currentVersion) || isSnoozed(release)) return@launch
            mutableState.value = UpdateState.Available(release)
        }
    }

    /** Desde Ajustes: siempre consulta y siempre responde. */
    fun checkNow() {
        if (job?.isActive == true) return
        mutableState.value = UpdateState.Checking
        job = scope.launch {
            mutableState.value = runCatching { latestRelease() }.fold(
                onSuccess = { release ->
                    prefs.edit { putLong(KEY_LAST_CHECK, nowMillis()) }
                    if (isNewer(release.version, currentVersion)) UpdateState.Available(release) else UpdateState.UpToDate
                },
                onFailure = { UpdateState.Failed(UpdateState.Failed.Reason.CHECK) },
            )
        }
    }

    /** "Más tarde": no se vuelve a ofrecer esta versión en unos días. */
    fun snooze(release: AppRelease) {
        prefs.edit {
            putString(KEY_SNOOZED_VERSION, release.version)
            putLong(KEY_SNOOZED_AT, nowMillis())
        }
        mutableState.value = UpdateState.Idle
    }

    /** La UI ya ha contado el resultado (al día, error). */
    fun acknowledge() {
        if (state.value is UpdateState.UpToDate || state.value is UpdateState.Failed || state.value is UpdateState.Installing) {
            mutableState.value = UpdateState.Idle
        }
    }

    fun update(release: AppRelease) {
        job?.cancel()
        mutableState.value = UpdateState.Downloading(release, progress = 0f)
        job = scope.launch {
            val apk = try {
                download(release)
            } catch (error: Exception) {
                ensureActive()
                mutableState.value = UpdateState.Failed(UpdateState.Failed.Reason.DOWNLOAD)
                return@launch
            }
            mutableState.value = try {
                install(apk)
                UpdateState.Installing(release)
            } catch (error: Exception) {
                UpdateState.Failed(UpdateState.Failed.Reason.INSTALL)
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
        mutableState.value = UpdateState.Idle
    }

    private fun isSnoozed(release: AppRelease): Boolean =
        prefs.getString(KEY_SNOOZED_VERSION, null) == release.version &&
            nowMillis() - prefs.getLong(KEY_SNOOZED_AT, 0) < SNOOZE_MS

    private suspend fun latestRelease(): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", SafeHttpsClient.USER_AGENT)
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body.string()
        }
        parseRelease(json, body) ?: throw IOException("La versión publicada no tiene APK")
    }

    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        downloads.mkdirs()
        downloads.listFiles()?.forEach { it.delete() }
        val partial = downloads.resolve("openrtve-${release.version}.apk.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", SafeHttpsClient.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            var read = 0L
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) mutableState.value = UpdateState.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (release.sizeBytes > 0 && read != release.sizeBytes) throw IOException("Tamaño inesperado")
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && !release.sha256.equals(actual, ignoreCase = true)) {
            partial.delete()
            throw IOException("El SHA-256 del APK no coincide")
        }
        partial
    }

    /** PackageInstaller: el sistema enseña su confirmación (ver [UpdateInstallReceiver]). */
    private fun install(apk: File) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("openrtve.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val status = PendingIntent.getBroadcast(appContext, sessionId, Intent(appContext, UpdateInstallReceiver::class.java), flags)
            session.commit(status.intentSender)
        }
    }

    /** Solo HTTPS a GitHub y a su CDN de descargas, en cada salto. */
    private object GitHubOnly : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            val host = url.host.lowercase()
            val allowed = url.isHttps && (host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com"))
            if (!allowed) throw IOException("Host no permitido para actualizaciones")
            return chain.proceed(chain.request())
        }
    }

    companion object {
        const val REPOSITORY = "pablocpas/openrtve"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val SNOOZE_MS = 3 * 24 * 60 * 60 * 1000L
        private const val PREFS_NAME = "openrtve_updates"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SNOOZED_VERSION = "snoozed_version"
        private const val KEY_SNOOZED_AT = "snoozed_at"

        /** `1.10.0` es más nueva que `1.9.2`; las partes que no son números cuentan como 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = versionParts(candidate)
            val b = versionParts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun versionParts(version: String): List<Int> =
            version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

        /** Respuesta de `releases/latest`: la versión y su APK, o `null` si no es publicable. */
        fun parseRelease(json: Json, body: String): AppRelease? {
            val root = json.parseToJsonElement(body).jsonObject
            if ((root["draft"] as? JsonPrimitive)?.booleanOrNull == true) return null
            if ((root["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return null
            val version = (root["tag_name"] as? JsonPrimitive)?.contentOrNull?.removePrefix("v") ?: return null
            val apk = root["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { asset ->
                (asset["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk") == true
            } ?: return null
            val url = (apk["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return null
            if (!url.startsWith("https://github.com/$REPOSITORY/releases/download/")) return null
            return AppRelease(
                version = version,
                apkUrl = url,
                sha256 = (apk["digest"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
                sizeBytes = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
    }
}

/**
 * Resultado de la instalación: si el sistema pide confirmación (lo normal), se abre su
 * diálogo; si falla, se avisa. Si sale bien, Android cierra la app para sustituirla.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
