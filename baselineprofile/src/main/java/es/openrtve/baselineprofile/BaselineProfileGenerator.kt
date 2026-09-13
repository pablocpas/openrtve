package es.openrtve.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorre los flujos principales para que ART precompile sus clases y métodos:
 * portada, scroll, Explorar, una categoría, una ficha y el reproductor.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
        maxIterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("Explorar")), 15_000)
        device.waitForIdle()

        // Portada: recorrer filas.
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
            device.waitForIdle()
        }
        // Explorar y una categoría.
        device.findObject(By.text("Explorar"))?.click()
        device.wait(Until.hasObject(By.textContains("Temáticas")), 10_000)
        device.findObject(By.textContains("CINE"))?.click()
        device.wait(Until.hasObject(By.textContains("Directos")), 15_000)
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
        device.waitForIdle()
        // Buscar y filtros rápidos.
        device.findObject(By.text("Buscar"))?.click()
        device.wait(Until.hasObject(By.textContains("SERIES")), 10_000)
        device.findObject(By.textContains("SERIES"))?.click()
        device.waitForIdle()
        // Reproductor: primer resultado -> ficha -> reproducir.
        device.findObjects(By.clickable(true)).drop(4).firstOrNull()?.click()
        device.wait(Until.hasObject(By.textContains("Reproducir")), 15_000)
        device.findObject(By.textContains("Reproducir"))?.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "player_settings")), 15_000)
        Thread.sleep(6_000)
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "es.openrtve"
    }
}
