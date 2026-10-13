package pe.com.comparadorprecios.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.InsightsResponse
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.NearbyStoresResponse
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.location.LatLng

private class FakeModeStore(initial: String = InsightsMode.AUTO) : InsightsModeStore {
    override val mode = MutableStateFlow(initial)
    override suspend fun setMode(mode: String) {
        this.mode.value = mode
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ProductExtrasViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @Mock
    lateinit var repository: ScanRepository

    private val dispatcher = StandardTestDispatcher()
    private val leche = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.9, tipo = "leche evaporada")
    private val insights = ProductInsights(titular = "Calcio en cada taza", semaforo = "verde", etiquetaSemaforo = "Buena opción diaria")
    private val plazaVea = NearbyStore("Plaza Vea", "plazaVea - Alfonso Ugarte", "Av. Alfonso Ugarte S/N", -12.04855, -77.04271, 0.2)

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        modeStore: InsightsModeStore = FakeModeStore(),
        chains: List<String> = listOf("Plaza Vea"),
        location: LatLng? = LatLng(-12.0464, -77.0428),
    ) = ProductExtrasViewModel(leche, chains, repository, modeStore) { location }

    @Test
    fun `carga los datos del producto con el modo guardado`() = runTest(dispatcher) {
        whenever(repository.insights(leche, InsightsMode.AUTO)).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(InsightsUiState.Ready(InsightsMode.SALUD, insights), vm.insights.value)
    }

    @Test
    fun `cambiar de modo y volver no vuelve a llamar a la IA`() = runTest(dispatcher) {
        whenever(repository.insights(eq(leche), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectMode(InsightsMode.CURIOSIDADES)
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectMode(InsightsMode.AUTO)
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository, times(1)).insights(leche, InsightsMode.AUTO)
        verify(repository, times(1)).insights(leche, InsightsMode.CURIOSIDADES)
    }

    @Test
    fun `si la IA falla queda en Failed y reintentar vuelve a pedir`() = runTest(dispatcher) {
        whenever(repository.insights(leche, InsightsMode.AUTO))
            .thenThrow(ScanError.Server("500"))
            .thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(InsightsUiState.Failed, vm.insights.value)

        vm.retryInsights()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.insights.value is InsightsUiState.Ready)
    }

    @Test
    fun `busca la tienda mas cercana con la ubicacion del celular`() = runTest(dispatcher) {
        whenever(repository.insights(eq(leche), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        whenever(repository.nearbyStores(listOf("Plaza Vea"), -12.0464, -77.0428)).thenReturn(NearbyStoresResponse(listOf(plazaVea)))
        val vm = viewModel()

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NearbyUiState.Ready(listOf(plazaVea)), vm.nearby.value)
    }

    @Test
    fun `sin ubicacion disponible no consulta tiendas`() = runTest(dispatcher) {
        whenever(repository.insights(eq(leche), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        val vm = viewModel(location = null)

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NearbyUiState.LocationUnavailable, vm.nearby.value)
        verify(repository, never()).nearbyStores(any(), any(), any())
    }

    @Test
    fun `sin tiendas cerca lo dice en vez de mostrar una lista vacia`() = runTest(dispatcher) {
        whenever(repository.insights(eq(leche), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        whenever(repository.nearbyStores(any(), any(), any())).thenReturn(NearbyStoresResponse(emptyList()))
        val vm = viewModel()

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NearbyUiState.NoStoresNearby, vm.nearby.value)
    }

    @Test
    fun `permiso denegado queda registrado para mostrar como habilitarlo`() = runTest(dispatcher) {
        whenever(repository.insights(eq(leche), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
        val vm = viewModel()

        vm.onLocationPermissionDenied()

        assertEquals(NearbyUiState.PermissionDenied, vm.nearby.value)
    }
}
