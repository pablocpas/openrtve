package es.openrtve.ui.tv

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.openrtve.R
import es.openrtve.data.WatchHistory
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.CatalogModule
import es.openrtve.domain.HomeFeed
import es.openrtve.domain.HomeRow
import es.openrtve.testing.FakeCatalogRepository
import es.openrtve.testing.catalogItem
import es.openrtve.testing.loaded
import es.openrtve.ui.theme.OpenRtveTvTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPortadaScreenTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /**
     * El menú de "Seguir viendo" se abre manteniendo OK. Al soltar, el KEY_UP llega
     * al primer botón del menú, que tiene el foco: no debe elegirlo. Las teclas se
     * inyectan de verdad (no en el nodo) para que Android las entregue a la ventana
     * enfocada, como con un mando.
     */
    @Test
    fun releasingTheLongPressKeepsTheContinueWatchingMenuOpen() {
        val film = catalogItem("braveheart", title = "Braveheart")
        val history = WatchHistory(File(context.cacheDir, "tv-portada-test-history-${System.nanoTime()}.json")).apply {
            register(film)
            updateProgress(film.id, positionMs = 20 * 60_000L, durationMs = 178 * 60_000L)
        }
        val repository = FakeCatalogRepository().apply {
            portada = { _, _, cachedOnly ->
                if (cachedOnly) throw es.openrtve.data.NotCachedException()
                loaded(HomeFeed(null, "Portada", listOf(row())))
            }
            module = { _, _, _ -> loaded(CatalogModule("", listOf(catalogItem("otra", title = "Otra película")))) }
        }
        val resumed = mutableListOf<CatalogItem>()
        val restarted = mutableListOf<CatalogItem>()
        compose.setContent {
            OpenRtveTvTheme {
                TvPortadaScreen(
                    repository = repository,
                    url = "https://www.rtve.es/play/index_apps.json",
                    title = "",
                    onOpenItem = {},
                    onOpenRow = { _, _ -> },
                    history = history,
                    onResume = { resumed += it },
                    onRestart = { restarted += it },
                )
            }
        }
        val card = compose.onNode(hasClickAction() and hasAnySibling(hasText("Braveheart")))
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        card.assertIsFocused()

        // Mantener OK: pulsación, repeticiones del mando y, al final, soltar.
        val downTime = SystemClock.uptimeMillis()
        repeat(6) { repeatCount -> sendCenter(KeyEvent.ACTION_DOWN, downTime, repeatCount) }
        sendCenter(KeyEvent.ACTION_UP, downTime, 0)
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.continue_remove)).assertIsDisplayed()
        assertEquals(emptyList<CatalogItem>(), resumed)

        // Una pulsación nueva sí elige: bajar a "Desde el inicio" y pulsar OK.
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(listOf("braveheart"), restarted.map { it.id })
        assertEquals(emptyList<CatalogItem>(), resumed)
    }

    private fun sendCenter(action: Int, downTime: Long, repeatCount: Int) = send(KeyEvent.KEYCODE_DPAD_CENTER, action, downTime, repeatCount)

    private fun sendKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        send(keyCode, KeyEvent.ACTION_DOWN, downTime)
        send(keyCode, KeyEvent.ACTION_UP, downTime)
    }

    private fun send(keyCode: Int, action: Int, downTime: Long, repeatCount: Int = 0) {
        instrumentation.sendKeySync(KeyEvent(downTime, SystemClock.uptimeMillis(), action, keyCode, repeatCount))
        compose.waitForIdle()
    }

    private fun row() = HomeRow(
        id = 0, title = "Cine", order = 0, moduleType = null, presentation = "ColeccionApaisado",
        contentUrl = "https://api.rtve.es/api/collection/0.json",
    )
}
