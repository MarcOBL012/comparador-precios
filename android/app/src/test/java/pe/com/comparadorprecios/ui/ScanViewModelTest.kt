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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.ScanResponse
import pe.com.comparadorprecios.data.StoreResult

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ScanViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @Mock
    lateinit var repository: ScanRepository

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92)

    @Test
    fun `exito ordena tiendas por precio`() = runTest(dispatcher) {
        whenever(repository.scan("uri")).thenReturn(
            ScanResponse(
                identification,
                listOf(
                    StoreResult("Wong", "encontrado", "Leche Gloria 400g", 4.6, "https://wong.pe/1"),
                    StoreResult("Plaza Vea", "encontrado", "Leche Gloria 400g", 4.5, "https://plazavea.com.pe/1"),
                )
            )
        )
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is ScanUiState.Success)
        assertEquals("Plaza Vea", (state as ScanUiState.Success).tiendas[0].tienda)
    }

    @Test
    fun `confianza baja produce LowConfidence`() = runTest(dispatcher) {
        whenever(repository.scan("uri")).thenReturn(
            ScanResponse(identification.copy(confianza = 0.2), emptyList())
        )
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is ScanUiState.LowConfidence)
    }

    @Test
    fun `error 400 produce mensaje de validacion`() = runTest(dispatcher) {
        whenever(repository.scan("uri")).thenThrow(ScanError.Validation("La imagen no es válida."))
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is ScanUiState.Error)
    }

    @Test
    fun `error 502 produce mensaje de identificacion`() = runTest(dispatcher) {
        whenever(repository.scan("uri")).thenThrow(ScanError.IdentificationFailed("No se pudo identificar."))
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is ScanUiState.Error)
    }

    @Test
    fun `error 401 produce Unauthorized con mensaje`() = runTest(dispatcher) {
        whenever(repository.scan("uri")).thenThrow(
            ScanError.Unauthorized("Tu sesión expiró. Inicia sesión de nuevo.")
        )
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is ScanUiState.Unauthorized)
        assertEquals(
            "Tu sesión expiró. Inicia sesión de nuevo.",
            (state as ScanUiState.Unauthorized).message,
        )
    }

    @Test
    fun `reset limpia un estado Unauthorized de vuelta a Idle`() = runTest(dispatcher) {
        // Regresión: MainActivity re-usa el mismo ScanViewModel (Activity-scoped) al
        // salir/re-entrar de la rama Scanning tras un signOut forzado por 401. Si reset()
        // no limpiara Unauthorized, el usuario quedaría en un bucle permanente de
        // signOut al volver a iniciar sesión.
        whenever(repository.scan("uri")).thenThrow(
            ScanError.Unauthorized("Tu sesión expiró. Inicia sesión de nuevo.")
        )
        val vm = ScanViewModel(repository)
        vm.scan("uri")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is ScanUiState.Unauthorized)

        vm.reset()

        assertEquals(ScanUiState.Idle, vm.state.value)
    }
}
