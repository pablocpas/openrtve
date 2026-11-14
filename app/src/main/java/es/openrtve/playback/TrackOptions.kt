package es.openrtve.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import es.openrtve.R
import java.util.Locale

/** Una opción de los menús del reproductor. [apply] la activa sobre el player. */
data class PlayerOption(
    val label: String,
    val selected: Boolean,
    val apply: (Player) -> Unit,
)

data class PlayerMenu(
    val qualities: List<PlayerOption>,
    val speeds: List<PlayerOption>,
    val audios: List<PlayerOption>,
    val subtitles: List<PlayerOption>,
    /** La calidad más alta que el reproductor eligió por sí mismo para este vídeo (ver [qualityOptions]). */
    val qualityCeiling: Int? = null,
)

/**
 * Construye los menús a partir de las pistas reales del stream, como hace la app
 * oficial: calidades por altura, audios e idiomas de subtítulos con nombre legible.
 *
 * [defaultMaxHeight] es el tope de Ajustes (0 = sin tope), al que vuelve "Automática";
 * [knownCeiling], el techo de calidad que devolvió el menú anterior de este vídeo.
 */
fun buildPlayerMenu(context: Context, player: Player, defaultMaxHeight: Int, knownCeiling: Int?): PlayerMenu {
    val tracks = player.currentTracks
    val params = player.trackSelectionParameters
    val quality = qualityOptions(context, tracks, player, defaultMaxHeight, knownCeiling)
    return PlayerMenu(
        qualityCeiling = quality.second,
        qualities = quality.first,
        speeds = SPEEDS.map { speed ->
            PlayerOption(
                label = if (speed == 1f) context.getString(R.string.player_speed_normal) else "${speed}x".replace(".0x", "x"),
                selected = player.playbackParameters.speed == speed,
                apply = { it.setPlaybackSpeed(speed) },
            )
        },
        audios = trackOptions(context, tracks, C.TRACK_TYPE_AUDIO, player),
        subtitles = listOf(
            PlayerOption(
                label = context.getString(R.string.player_subtitles_off),
                selected = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                    tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
                apply = { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
            ),
        ) + trackOptions(context, tracks, C.TRACK_TYPE_TEXT, player),
    )
}

/**
 * Calidades como tope de altura ("hasta 720p"), no como pista forzada: el reproductor
 * sigue eligiendo el códec, como en automático. RTVE publica a veces la máxima calidad
 * en AV1 y el resto en AVC; forzar la pista AV1 en un aparato sin AV1 por hardware
 * (un Mi Box 3) dejaba la pantalla en negro con sonido.
 *
 * Por la misma razón solo se ofrecen alturas hasta la que el reproductor eligió por sí
 * mismo (el techo): por encima solo hay pistas que este aparato no reproduce bien. El
 * techo se mide en automático y se recuerda mientras haya un tope puesto, porque
 * entonces lo que suena ya no es lo que eligió el reproductor.
 */
private fun qualityOptions(
    context: Context,
    tracks: Tracks,
    player: Player,
    defaultMaxHeight: Int,
    knownCeiling: Int?,
): Pair<List<PlayerOption>, Int?> {
    val defaultCap = if (defaultMaxHeight > 0) defaultMaxHeight else Int.MAX_VALUE
    val cap = player.trackSelectionParameters.maxVideoHeight
    val userCapped = cap != defaultCap
    val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.map { VideoGroup(supportedHeights(it), it.isSelected) }
    val (heights, ceiling) = qualityHeights(video, userCapped, knownCeiling)
    if (heights.size < 2) return emptyList<PlayerOption>() to ceiling
    val auto = PlayerOption(
        label = context.getString(R.string.player_quality_auto),
        selected = !userCapped,
        apply = { p -> p.trackSelectionParameters = p.trackSelectionParameters.withVideoCap(defaultMaxHeight) },
    )
    return (listOf(auto) + heights.map { height ->
        PlayerOption(
            label = "${height}p",
            selected = userCapped && cap == height,
            apply = { p -> p.trackSelectionParameters = p.trackSelectionParameters.withVideoCap(height) },
        )
    }) to ceiling
}

/**
 * Alturas que ofrece el menú de calidad y su techo: hasta la altura más alta del grupo
 * que el reproductor eligió por sí mismo, o hasta [knownCeiling] si hay un tope puesto
 * (entonces lo que suena ya no es lo que eligió). Las de otros grupos (otro códec) por
 * debajo del techo también valen: al poner un tope, el reproductor elige su pista.
 */
internal fun qualityHeights(video: List<VideoGroup>, userCapped: Boolean, knownCeiling: Int?): Pair<List<Int>, Int?> {
    val playing = video.firstOrNull { it.playing } ?: return emptyList<Int>() to knownCeiling
    val ceiling = (if (userCapped) knownCeiling else null) ?: playing.heights.maxOrNull() ?: return emptyList<Int>() to knownCeiling
    return video.flatMap { it.heights }.filter { it <= ceiling }.distinct().sortedDescending() to ceiling
}

/** Un grupo de pistas de vídeo (un códec): las alturas que este aparato admite y si es el que suena. */
internal data class VideoGroup(val heights: List<Int>, val playing: Boolean)

private fun supportedHeights(group: Tracks.Group): List<Int> =
    (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it).height }.filter { it > 0 }

/** Tope de altura (0 = ninguno), sin pistas forzadas de versiones anteriores del menú. */
private fun TrackSelectionParameters.withVideoCap(maxHeight: Int): TrackSelectionParameters =
    buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .apply { if (maxHeight > 0) setMaxVideoSize(Int.MAX_VALUE, maxHeight) else clearVideoSizeConstraints() }
        .build()

private fun trackOptions(context: Context, tracks: Tracks, type: Int, player: Player): List<PlayerOption> {
    val options = mutableListOf<PlayerOption>()
    val seen = mutableSetOf<String>()
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            val label = trackLabel(context, format)
            if (!seen.add(label)) continue
            options += PlayerOption(
                label = label,
                selected = group.isTrackSelected(index),
                apply = { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                        .build()
                },
            )
        }
    }
    return options
}

/** RTVE etiqueta la versión original como `qaa` y la audiodescripción como `ads`. */
private fun trackLabel(context: Context, format: Format): String {
    val language = format.language?.lowercase()
    val base = when {
        language == "qaa" -> context.getString(R.string.player_track_original)
        language == "ads" || format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 -> context.getString(R.string.player_track_audio_description)
        language == null || language == "und" -> format.label ?: context.getString(R.string.player_track_unknown)
        else -> Locale.forLanguageTag(language).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }
    val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
    return if (forced) "$base (${context.getString(R.string.player_track_forced)})" else base
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
