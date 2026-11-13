package es.openrtve.data

import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private val file = Files.createTempDirectory("openrtve-favorites").resolve("favorites.json").toFile()
    private var now = 1_000_000L
    private val favorites = Favorites(file, nowMillis = { now }, ioDispatcher = Dispatchers.Unconfined)

    @Test
    fun `toggle adds the newest first and removes on the second press`() {
        assertTrue(favorites.toggle(item("v1")))
        now += 10
        assertTrue(favorites.toggle(item("p1", ContentKind.PROGRAM)))
        assertEquals(listOf("p1", "v1"), favorites.items.value.map { it.id })

        assertFalse(favorites.toggle(item("v1")))
        assertFalse(favorites.contains("v1"))
        assertEquals(listOf("p1"), favorites.items.value.map { it.id })
    }

    @Test
    fun `undoing a removal puts the title back in its place`() {
        favorites.toggle(item("v1"))
        now += 10
        favorites.toggle(item("v2"))
        now += 10
        favorites.toggle(item("v3"))

        val removed = favorites.remove("v2")!!
        assertEquals(listOf("v3", "v1"), favorites.items.value.map { it.id })
        favorites.restore(removed)
        assertEquals(listOf("v3", "v2", "v1"), favorites.items.value.map { it.id })
    }

    @Test
    fun `lives are not saved`() {
        val live = item("la1", ContentKind.LIVE)
        assertFalse(favorites.canAdd(live))
        assertFalse(favorites.toggle(live))
        assertTrue(favorites.items.value.isEmpty())
    }

    @Test
    fun `the list and its order survive a restart`() {
        val program = item("p1", ContentKind.PROGRAM).copy(programTypeId = "137310", programType = "Contenedor Películas")
        favorites.toggle(item("v1"))
        now += 10
        favorites.toggle(program)

        val reloaded = Favorites(file, nowMillis = { now }, ioDispatcher = Dispatchers.Unconfined)
        assertEquals(listOf(program, item("v1")), reloaded.items.value)
    }

    private fun item(id: String, kind: ContentKind = ContentKind.VIDEO) = CatalogItem(
        id = id,
        playbackId = id.takeIf { kind == ContentKind.VIDEO },
        assetId = null,
        title = "Título $id",
        subtitle = null,
        imageUrl = "https://img.rtve.es/v/$id/horizontal2",
        kind = kind,
        allowedInCountry = null,
        loginRequired = false,
        paid = false,
        drm = false,
    )
}
