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
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.PricesResponse
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.HistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ProductConfirmationViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @Mock
    lateinit var historyRepository: HistoryRepository

    @Mock
    lateinit var scanRepository: ScanRepository

    private val dispatcher = StandardTestDispatcher()
    private val identification = Identification("AJE", "Free Tea Frutos Rojos", "500ml", "bebidas", 0.9, tipo = "te helado")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ProductConfirmationViewModel {
        return ProductConfirmationViewModel(
            scanId = 1L,
            identification = identification,
            saveBattery = false,
            historyRepository = historyRepository,
            scanRepository = scanRepository,
        )
    }

    @Test
    fun `empieza pidiendo confirmacion`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConfirmationState.Asking, vm.state.value)
        assertNull(vm.previousConfirmedMatch.value)
    }

    @Test
    fun `confirmCorrect marca el escaneo como confirmado`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmCorrect()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConfirmationState.Confirmed, vm.state.value)
        verify(historyRepository).confirm(1L)
    }

    @Test
    fun `no excluye un match confirmado anterior de otro escaneo`() = runTest(dispatcher) {
        val previo = HistoryItem(
            id = 42L,
            title = "AJE Free Tea Frutos Rojos 500ml",
            dateMillis = 1000L,
            bestPrice = 3.5,
            latestBestPrice = null,
            thumbnail = byteArrayOf(),
            identification = identification,
            tiendas = emptyList(),
        )
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(previo)

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(42L, vm.previousConfirmedMatch.value?.id)
    }

    @Test
    fun `submitCorrection sin texto no hace nada`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.showCorrectionForm()

        vm.submitCorrection("", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConfirmationState.CorrectionForm, vm.state.value)
    }

    @Test
    fun `submitCorrection solo con link guarda la nota y no busca de nuevo`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.showCorrectionForm()

        vm.submitCorrection("", "https://tienda.pe/producto")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConfirmationState.Confirmed, vm.state.value)
        verify(historyRepository).saveCorrection(eq(1L), eq("Link: https://tienda.pe/producto"))
        verify(scanRepository, org.mockito.kotlin.never()).prices(any(), anyOrNull())
    }

    @Test
    fun `submitCorrection con nombre reintenta la busqueda y actualiza el historial`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val nuevosResultados = listOf(StoreResult("Tottus", "encontrado", "Free Tea Frutos Rojos 500ml", 3.5, "https://tottus.pe/1"))
        whenever(scanRepository.prices(any(), anyOrNull())).thenReturn(PricesResponse(nuevosResultados))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.showCorrectionForm()

        vm.submitCorrection("Free Tea Frutos Rojos 500ml AJE", "")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is ConfirmationState.CorrectionResult)
        assertEquals(nuevosResultados, (state as ConfirmationState.CorrectionResult).tiendas)
        verify(historyRepository).saveCorrection(eq(1L), eq("Free Tea Frutos Rojos 500ml AJE"))
        verify(historyRepository).saveCheck(eq(1L), eq(nuevosResultados), any())
    }

    @Test
    fun `submitCorrection con error de busqueda muestra el mensaje`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        whenever(scanRepository.prices(any(), anyOrNull())).thenThrow(ScanError.Network("Sin conexión."))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.showCorrectionForm()

        vm.submitCorrection("Nombre completo", "")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is ConfirmationState.CorrectionFailed)
        assertEquals("Sin conexión.", (state as ConfirmationState.CorrectionFailed).message)
    }

    @Test
    fun `cancelCorrection vuelve a Asking`() = runTest(dispatcher) {
        whenever(historyRepository.findConfirmedMatch(any())).thenReturn(null)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.showCorrectionForm()

        vm.cancelCorrection()

        assertEquals(ConfirmationState.Asking, vm.state.value)
    }
}
