package es.openrtve.data

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.pxOrElse
import java.net.URLEncoder

/**
 * Imágenes del servicio de RTVE (`img.rtve.es`) en la forma y el tamaño exactos que
 * pide la web oficial de RTVE Play (comprobado el 14-11-2026).
 *
 * El servicio redimensiona al vuelo: una variante que nadie ha pedido tarda de medio
 * segundo a seis (y a veces responde 503); una ya pedida sale de su caché en ~50 ms.
 * Pedir "el tamaño justo" de cada tarjeta generaba variantes que solo pedía esta app
 * y casi siempre tocaba esperar. La web oficial pide siempre las mismas pocas, que ya
 * están hechas para todo lo que se ve:
 *
 * - vídeo apaisado: `v/{id}/?w=400` (o `v/{id}/horizontal2?w=400`, la carátula
 *   editorial de películas), y `?w=1600` para destacados y cabeceras;
 * - vídeo vertical: `v/{id}/vertical/?h=400` y `v/{id}/vertical2/?h=400`;
 * - programa: `p/{id}/imgportada/?w=400` (o `?w=1600`), `p/{id}/imgposter/?h=400`,
 *   `p/{id}/imgbackground/?h=400`… y el logo, `p/{id}/?w=320&imgProgApi=logo`.
 *
 * Sin `h=` en las apaisadas (el servicio recortaría una variante nueva): la imagen
 * llega en su proporción y la tarjeta la recorta.
 *
 * Los originales de `/imagenes/` y `/css/` (0,2-1,5 MB) pasan por el redimensionador
 * del mismo host, `img.rtve.es/i/?w=&i=`, con los mismos dos anchos. Los `/p/` con
 * nombre de fichero (`p/bluey_1028792.png`) no se redimensionan por ninguna vía.
 */
object RtveImageSizing {
    private const val HOST = "https://img.rtve.es/"
    /** Ancho de las tarjetas en la web oficial. */
    const val CARD_PX = 400
    /** Ancho de los destacados y cabeceras en la web oficial. */
    const val LARGE_PX = 1600
    /** Desde este ancho en píxeles de pantalla (destacados, cabeceras) se pide la versión grande. */
    private const val LARGE_FROM_PX = 800

    private val videoPath = Regex("""^v/(\d+)(?:/([A-Za-z0-9]+))?/?$""")
    private val programPath = Regex("""^p/(\d+)/?$""")

    /** URL a pedir para una imagen pintada a [widthPx] de ancho, o `null` si se pide tal cual. */
    fun sized(url: String, widthPx: Int): String? {
        if (!url.startsWith(HOST) || widthPx <= 0) return null
        val rest = url.removePrefix(HOST)
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val width = if (widthPx >= LARGE_FROM_PX) LARGE_PX else CARD_PX

        videoPath.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            val variant = match.groupValues[2]
            return when {
                variant.equals("vertical", true) || variant.equals("vertical2", true) -> "${HOST}v/$id/${variant.lowercase()}/?h=$CARD_PX"
                variant.equals("horizontal2", true) -> "${HOST}v/$id/horizontal2?w=$width"
                variant.isEmpty() || variant.equals("horizontal", true) -> "${HOST}v/$id/?w=$width"
                else -> "${HOST}v/$id/$variant/?w=$CARD_PX"
            }
        }
        programPath.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            val variant = (query["imgProgApi"] ?: "imgPortada").lowercase()
            return when {
                variant == "logo" -> "${HOST}p/$id/?w=320&imgProgApi=logo"
                variant.startsWith("imgportada") -> "${HOST}p/$id/$variant/?w=$width"
                else -> "${HOST}p/$id/$variant/?h=$CARD_PX"
            }
        }
        if (path.startsWith("p/")) return null
        val original = when {
            path.startsWith("i/") -> query["i"]?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            path.startsWith("imagenes/") || path.startsWith("css/") -> HOST + path
            else -> null
        } ?: return null
        return "${HOST}i/?w=$width&i=${URLEncoder.encode(original, "UTF-8")}"
    }
}

/**
 * Aplica [RtveImageSizing] con el tamaño que Coil ha resuelto para cada petición. Si
 * la versión redimensionada fallase, se pide el original: el tamaño es una mejora,
 * nunca el motivo de una tarjeta vacía.
 */
class RtveImageSizeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed()
        val sized = RtveImageSizing.sized(url = url, widthPx = chain.size.width.pxOrElse { -1 }) ?: return chain.proceed()
        val result = chain.withRequest(chain.request.newBuilder().data(sized).build()).proceed()
        return if (result is ErrorResult) chain.proceed() else result
    }
}
