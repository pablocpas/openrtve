package es.openrtve.domain

import java.net.URI

/**
 * Única definición de qué hosts remotos acepta la app. La usan el cliente HTTPS,
 * el parser (imágenes) y el resolver de reproducción.
 */
class RtveHostPolicy(private val rootDomain: String = "rtve.es") {
    fun requireAllowed(uri: URI) {
        val host = uri.host?.lowercase()
        require(uri.scheme.equals("https", ignoreCase = true)) { "Solo se permite HTTPS" }
        require(uri.rawUserInfo == null) { "La URL no puede contener credenciales" }
        require(uri.port == -1 || uri.port == 443) { "Puerto no permitido" }
        require(host == rootDomain || host?.endsWith(".$rootDomain") == true) {
            "Host no permitido"
        }
    }

    fun isAllowed(value: String): Boolean = runCatching {
        requireAllowed(URI(value))
        true
    }.getOrDefault(false)

    /**
     * Devuelve la URL lista para usarse o `null` si no es admisible. Las URLs de
     * imagen de los feeds llegan a veces en `http://`; se elevan a HTTPS antes de
     * validar porque los hosts de RTVE lo sirven.
     */
    fun sanitize(value: String): String? {
        val upgraded = value.trim().replaceFirst(HTTP_SCHEME, "https://")
        return upgraded.takeIf(::isAllowed)
    }

    private companion object {
        val HTTP_SCHEME = Regex("^http://", RegexOption.IGNORE_CASE)
    }
}
