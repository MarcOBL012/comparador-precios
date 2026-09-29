package pe.com.comparadorprecios.history

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

// application = Application::class evita que Robolectric instancie nuestra App real (que
// llama a Clerk.initialize(...) en onCreate) — este test solo ejercita Room y no necesita
// (ni debe depender de) que Clerk esté inicializado con una clave válida.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92)
    private val tiendas = listOf(
        StoreResult("Wong", "encontrado", "Leche Evaporada Gloria 400g", 4.6, "https://www.wong.pe/p/1"),
        StoreResult("Plaza Vea", "encontrado", "Leche Evaporada Gloria 400g", 4.5, "https://www.plazavea.com.pe/p/1"),
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save guarda y all lo devuelve con mejor precio`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1, 2, 3))

        val all = repo.all.first()

        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
        assertEquals("Gloria Leche evaporada 400g", all[0].title)
        assertEquals(4.5, all[0].bestPrice!!, 0.0)
        assertTrue(all[0].thumbnail.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `all ordena por recientes primero`() = runTest {
        repo.save(identification, tiendas, byteArrayOf(1), createdAt = 1000L)
        repo.save(identification, tiendas, byteArrayOf(2), createdAt = 2000L)

        val all = repo.all.first()

        assertEquals(2, all.size)
        assertTrue(all[0].thumbnail.contentEquals(byteArrayOf(2)))
    }

    @Test
    fun `delete y restore implementan deshacer`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(9))

        val deleted = repo.delete(id)
        assertEquals(0, repo.all.first().size)

        repo.restore(deleted!!)
        val all = repo.all.first()
        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
    }

    @Test
    fun `una revision mas barata marca el escaneo como bajado de precio`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1))

        repo.saveCheck(id, listOf(StoreResult("Wong", "encontrado", "Leche", 3.9, "https://www.wong.pe/p/1")), checkedAt = 1L)

        val item = repo.all.first().single()
        assertEquals(3.9, item.latestBestPrice!!, 0.0)
        assertTrue(item.priceDropped)
        assertEquals(3.9, repo.latestCheck(id).first()!!.tiendas.single().precio!!, 0.0)
    }

    @Test
    fun `el historial usa la revision mas reciente, no la primera`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1))

        repo.saveCheck(id, listOf(StoreResult("Wong", "encontrado", "Leche", 3.9, null)), checkedAt = 1L)
        repo.saveCheck(id, listOf(StoreResult("Wong", "encontrado", "Leche", 4.8, null)), checkedAt = 2L)

        val item = repo.all.first().single()
        assertEquals(4.8, item.latestBestPrice!!, 0.0)
        assertTrue(!item.priceDropped)
    }

    @Test
    fun `get devuelve null para id inexistente`() = runTest {
        assertNull(repo.get(9999L))
    }

    @Test
    fun `findConfirmedMatch es null hasta que se confirma el mismo producto`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1))

        assertNull(repo.findConfirmedMatch(identification))

        repo.confirm(id)

        val match = repo.findConfirmedMatch(identification)
        assertEquals(id, match?.id)
    }

    @Test
    fun `findConfirmedMatch exige marca, nombre y tipo iguales, no solo parecidos`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1))
        repo.confirm(id)

        val otraPresentacion = identification.copy(presentacion = "1L")
        assertEquals(id, repo.findConfirmedMatch(otraPresentacion)?.id)

        val otroProducto = identification.copy(nombre = "Leche UHT")
        assertNull(repo.findConfirmedMatch(otroProducto))
    }

    @Test
    fun `saveCorrection guarda la nota sin afectar la confirmacion`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1))

        repo.saveCorrection(id, "Era Gloria Leche UHT, no evaporada · Link: https://ejemplo.pe/producto")

        assertNull(repo.findConfirmedMatch(identification))
        repo.confirm(id)
        assertEquals(id, repo.findConfirmedMatch(identification)?.id)
    }

    @Test
    fun `save con precio null deja bestPrice en null`() = runTest {
        repo.save(
            identification,
            listOf(StoreResult("Wong", "encontrado", "Prod", null, null)),
            byteArrayOf(1, 2, 3),
        )

        val all = repo.all.first()

        assertEquals(1, all.size)
        assertNull(all[0].bestPrice)
    }
}
