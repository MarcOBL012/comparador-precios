package pe.com.comparadorprecios.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import pe.com.comparadorprecios.data.AlertPage
import pe.com.comparadorprecios.data.AlertType
import pe.com.comparadorprecios.data.AlertView
import pe.com.comparadorprecios.data.ApiError
import pe.com.comparadorprecios.data.CheckResponse
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.Watch
import pe.com.comparadorprecios.data.WatchPage
import pe.com.comparadorprecios.data.WatchProduct
import pe.com.comparadorprecios.data.WatchRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class WatchesViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @Mock
    lateinit var repository: WatchRepository

    private val dispatcher = StandardTestDispatcher()
    private val watch = Watch(
        id = "w1",
        producto = WatchProduct("p1", "Free Tea", "Frutos Rojos", "500ml", "bebidas", "té"),
        precioObjetivo = 2.5,
        estado = "activa",
        creadoEn = "2026-11-03T00:00:00Z",
        actualizadoEn = "2026-11-03T00:00:00Z",
    )
    private val alert = AlertView(7, AlertType("objetivo_alcanzado", "Objetivo alcanzado"), "Free Tea bajó a S/ 2.00", "w1", "Free Tea", "Plaza Vea", 2.0, "2026-11-03", false)
    private val identification = Identification("Free Tea", "Frutos Rojos", "500ml", "bebidas", 0.9, tipo = "té")

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun loaded(): WatchesViewModel {
        whenever(repository.list(anyOrNull(), any(), any())).thenReturn(WatchPage(listOf(watch), 1))
        whenever(repository.alerts(any(), any(), any())).thenReturn(AlertPage(listOf(alert), 1, 1))
        return WatchesViewModel(repository)
    }

    @Test
    fun `al abrir carga los seguimientos y las alertas sin leer`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf(watch), s.watches)
        assertEquals(1, s.unreadAlerts)
    }

    @Test
    fun `si la carga falla sin datos queda el error para reintentar`() = runTest(dispatcher) {
        whenever(repository.list(anyOrNull(), any(), any())).thenThrow(ApiError.Network("Sin conexión."))
        val vm = WatchesViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sin conexión.", vm.state.value.loadError)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `revisar ahora actualiza el seguimiento, suma las alertas nuevas y avisa`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        val reached = watch.copy(estado = "alcanzada")
        whenever(repository.check("w1")).thenReturn(CheckResponse(reached, 2, 0, listOf(alert.copy(id = 8))))

        vm.check("w1")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("alcanzada", s.watches.single().estado)
        assertEquals(2, s.unreadAlerts)
        assertEquals(alert.mensaje, s.message)
        assertTrue(s.busy.isEmpty())
    }

    @Test
    fun `el limite de revisiones (429) se muestra como aviso y libera el boton`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.check("w1")).thenThrow(ApiError.RateLimited("Vuelve a intentarlo en 14 min.", 840))

        vm.check("w1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Vuelve a intentarlo en 14 min.", vm.state.value.message)
        assertTrue(vm.state.value.busy.isEmpty())
        assertEquals(listOf(watch), vm.state.value.watches)
    }

    @Test
    fun `eliminar quita el seguimiento y sus alertas de la lista`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()

        vm.delete(watch)
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository).delete("w1")
        assertTrue(vm.state.value.watches.isEmpty())
        assertTrue(vm.state.value.alerts.isEmpty())
    }

    @Test
    fun `si borrar falla con 404 el seguimiento no se quita y se explica`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.delete("w1")).thenThrow(ApiError.NotFound("No encontramos ese seguimiento."))

        vm.delete(watch)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.watches.size)
        assertEquals("No encontramos ese seguimiento.", vm.state.value.message)
    }

    @Test
    fun `pausar envia el estado contrario y reemplaza el seguimiento`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.setStatus(eq("w1"), eq("pausada"), anyOrNull())).thenReturn(watch.copy(estado = "pausada"))

        vm.togglePause(watch)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.watches.single().isPaused)
    }

    @Test
    fun `marcar una alerta como leida baja el contador`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.markAlert(7, true)).thenReturn(alert.copy(leida = true))

        vm.markAlert(alert, true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.unreadAlerts)
        assertTrue(vm.state.value.alerts.single().leida)
    }

    @Test
    fun `seguir un producto lo agrega arriba de la lista`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        val nuevo = watch.copy(id = "w2")
        whenever(repository.create(any(), eq(2.0))).thenReturn(nuevo)

        vm.follow(identification, 2.0)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FollowUiState.Done(nuevo), vm.follow.value)
        assertEquals("w2", vm.state.value.watches.first().id)
    }

    @Test
    fun `seguir dos veces el mismo producto (409) se reporta como ya seguido`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.create(any(), anyOrNull())).thenThrow(ApiError.Conflict("Ya sigues este producto.", "ya_seguido"))

        vm.follow(identification, null)
        dispatcher.scheduler.advanceUntilIdle()

        val f = vm.follow.value as FollowUiState.Failed
        assertTrue(f.alreadyFollowing)
        assertEquals("Ya sigues este producto.", f.message)
        assertEquals(1, vm.state.value.watches.size)
    }

    @Test
    fun `el historial lee el seguimiento y su historial del servidor`() = runTest(dispatcher) {
        val vm = loaded()
        dispatcher.scheduler.advanceUntilIdle()
        whenever(repository.get("w1")).thenReturn(watch)
        whenever(repository.history("w1")).thenReturn(pe.com.comparadorprecios.data.HistoryResponse(30))

        vm.openHistory(watch)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.history!!.loading)
        assertNull(vm.state.value.history!!.error)
        vm.closeHistory()
        assertNull(vm.state.value.history)
    }

    @Test
    fun `parsePrice acepta coma y punto y rechaza cero, negativos y texto`() {
        assertEquals(2.5, parsePrice("2,5")!!, 0.0)
        assertEquals(2.5, parsePrice(" 2.5 ")!!, 0.0)
        assertNull(parsePrice("0"))
        assertNull(parsePrice("-3"))
        assertNull(parsePrice("abc"))
        assertNull(parsePrice(""))
    }
}
