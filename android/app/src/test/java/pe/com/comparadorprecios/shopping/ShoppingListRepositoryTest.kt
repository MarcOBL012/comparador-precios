package pe.com.comparadorprecios.shopping

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.AppDatabase

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShoppingListRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ShoppingListRepository

    private val leche = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.9, tipo = "leche evaporada")

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ShoppingListRepository(db.shoppingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `agregar el mismo producto suma una unidad y guarda los precios mas nuevos`() = runTest {
        repo.add(leche, listOf(StoreResult("Wong", "encontrado", "Leche", 4.6, null)), now = 1L)
        repo.add(leche, listOf(StoreResult("Wong", "encontrado", "Leche", 4.2, null)), now = 2L)

        val line = repo.lines.first().single()
        assertEquals(2, line.quantity)
        assertEquals(4.2, line.tiendas.single().precio!!, 0.0)
        assertEquals("leche evaporada", line.identification.tipo)
    }

    @Test
    fun `bajar la cantidad a cero quita el producto`() = runTest {
        repo.add(leche, emptyList())
        val id = repo.lines.first().single().id

        repo.setQuantity(id, 0)

        assertTrue(repo.lines.first().isEmpty())
    }
}
