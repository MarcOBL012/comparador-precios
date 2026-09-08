package pe.com.comparadorprecios.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository
    private val dispatcher = StandardTestDispatcher()

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92)
    private val tiendas = listOf(
        StoreResult("Wong", "encontrado", "Leche Evaporada Gloria 400g", 4.6, "https://www.wong.pe/p/1"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `items expone el historial ordenado`() = runTest(dispatcher) {
        repo.save(identification, tiendas, byteArrayOf(1), createdAt = 1000L)
        repo.save(identification, tiendas, byteArrayOf(2), createdAt = 2000L)

        val vm = HistoryViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        val items = vm.items.first()
        assertEquals(2, items.size)
        assertTrue(items[0].thumbnail.contentEquals(byteArrayOf(2)))
    }

    @Test
    fun `requestDelete + undo restauran el registro`() = runTest(dispatcher) {
        val id = repo.save(identification, tiendas, byteArrayOf(7))

        val vm = HistoryViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete(id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.items.first().size)
        assertTrue(vm.showUndo.first())

        vm.undo()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.items.first().size)
    }
}
