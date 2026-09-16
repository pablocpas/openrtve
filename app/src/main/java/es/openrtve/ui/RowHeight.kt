package es.openrtve.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * Altura animada para una fila horizontal de tarjetas de alto variable.
 *
 * Una `LazyRow` normal mide lo que su tarjeta visible más alta y cambia de alto
 * de golpe al entrar o salir una con el título a dos líneas; ese cambio ocurre
 * en su propio remedido y ni `animateContentSize` ni `animateItem` lo suavizan.
 * Aquí la altura la fija un estado: la mayor altura natural entre las tarjetas
 * visibles, y cada cambio se anima. Las tarjetas se miden sin límite de alto
 * para que la altura fija de la fila no las aplaste.
 */
@Stable
class RowHeightState internal constructor(
    private val listState: LazyListState,
    internal val heights: MutableMap<Any, Int>,
) {
    internal val target by derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.mapNotNull { heights[it.key] }.maxOrNull() ?: 0
    }
    internal val animated = Animatable(0, Int.VectorConverter)
}

@Composable
fun rememberRowHeightState(listState: LazyListState): RowHeightState {
    val state = remember(listState) { RowHeightState(listState, mutableStateMapOf()) }
    LaunchedEffect(state, state.target) {
        val target = state.target
        if (target == 0) return@LaunchedEffect
        // La primera medición se aplica sin animar: la fila no debe crecer desde cero.
        if (state.animated.value == 0) state.animated.snapTo(target) else state.animated.animateTo(target)
    }
    return state
}

/** Para la `LazyRow`: altura animada según las tarjetas visibles. */
@Composable
fun Modifier.animatedRowHeight(state: RowHeightState): Modifier {
    val px = state.animated.value
    return if (px > 0) height(with(LocalDensity.current) { px.toDp() }) else this
}

/** Para cada tarjeta de la fila: mide su altura natural y la registra bajo su clave. */
fun Modifier.rowItemHeight(state: RowHeightState, key: Any): Modifier =
    wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { state.heights[key] = it.height }
