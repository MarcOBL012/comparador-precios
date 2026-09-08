package pe.com.comparadorprecios.history

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
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

@RunWith(RobolectricTestRunner::class)
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
    fun `get devuelve null para id inexistente`() = runTest {
        assertNull(repo.get(9999L))
    }
}
