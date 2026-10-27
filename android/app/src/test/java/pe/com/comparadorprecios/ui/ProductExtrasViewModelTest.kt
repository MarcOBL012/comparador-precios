package pe.com.comparadorprecios.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import pe.com.comparadorprecios.data.GeoPoint
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.InsightsResponse
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.NearbyStoresResponse
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.RouteResponse
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreRoute
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
    private val insights = ProductInsights(titular = "Calcio en cada taza", semaforo = "verde", etiquetaSemaforo = "Buena opción diaria", puntaje = 82.0)
    private val plazaVea = NearbyStore("Plaza Vea", "plazaVea - Alfonso Ugarte", "Av. Alfonso Ugarte S/N", -12.04855, -77.04271, 0.2)
    private val promart = NearbyStore("Promart", "PROMART SAN MIGUEL", "", -12.07, -77.08, 3.4)
    private val aPie = StoreRoute("pie", 0.3, 4, listOf(listOf(-12.0464, -77.0428), listOf(-12.0485, -77.0427)))
    private val yo = LatLng(-12.0464, -77.0428)

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        modeStore: InsightsModeStore = FakeModeStore(),
        chains: List<String> = listOf("Plaza Vea"),
        location: LatLng? = yo,
        profile: Flow<Set<String>> = flowOf(emptySet()),
    ) = ProductExtrasViewModel(
        identification = leche,
        chains = chains,
        repository = repository,
        modeStore = modeStore,
        locationProvider = { location },
        profileInterests = profile,
    )

    private suspend fun stubInsights() {
        whenever(repository.insights(eq(leche), any(), any())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))
    }

    @Test
    fun `carga los datos del producto con el modo guardado`() = runTest(dispatcher) {
        whenever(repository.insights(leche, InsightsMode.AUTO, emptyList())).thenReturn(InsightsResponse(InsightsMode.SALUD, insights))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(InsightsUiState.Ready(InsightsMode.SALUD, insights), vm.insights.value)
    }

    @Test
    fun `manda al backend solo los intereses del perfil que corresponden al modo`() = runTest(dispatcher) {
        stubInsights()
        val vm = viewModel(
            modeStore = FakeModeStore(InsightsMode.SALUD),
            profile = flowOf(setOf("Bajar azúcar", "Gaming")),
        )
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository).insights(leche, InsightsMode.SALUD, listOf("Bajar azúcar"))
        assertTrue(vm.insights.value is InsightsUiState.Ready)
    }

    @Test
    fun `si el perfil cambia se vuelven a generar los consejos`() = runTest(dispatcher) {
        stubInsights()
        val profile = MutableStateFlow(emptySet<String>())
        viewModel(modeStore = FakeModeStore(InsightsMode.SALUD), profile = profile)
        dispatcher.scheduler.advanceUntilIdle()

        profile.value = setOf("Vegano")
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository).insights(leche, InsightsMode.SALUD, emptyList())
        verify(repository).insights(leche, InsightsMode.SALUD, listOf("Vegano"))
    }

    @Test
    fun `cambiar de modo y volver no vuelve a llamar a la IA`() = runTest(dispatcher) {
        stubInsights()
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectMode(InsightsMode.CURIOSIDADES)
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectMode(InsightsMode.AUTO)
        dispatcher.scheduler.advanceUntilIdle()

        verify(repository, times(1)).insights(leche, InsightsMode.AUTO, emptyList())
        verify(repository, times(1)).insights(leche, InsightsMode.CURIOSIDADES, emptyList())
    }

    @Test
    fun `si la IA falla queda en Failed y reintentar vuelve a pedir`() = runTest(dispatcher) {
        whenever(repository.insights(leche, InsightsMode.AUTO, emptyList()))
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
    fun `busca la tienda mas cercana y traza la ruta a la primera`() = runTest(dispatcher) {
        stubInsights()
        whenever(repository.nearbyStores(listOf("Plaza Vea"), yo.lat, yo.lng)).thenReturn(NearbyStoresResponse(listOf(plazaVea)))
        whenever(repository.route(GeoPoint(yo.lat, yo.lng), GeoPoint(plazaVea.lat, plazaVea.lng))).thenReturn(RouteResponse(aPie))
        val vm = viewModel()

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.nearby.value as NearbyUiState.Ready
        assertEquals(yo, state.origin)
        assertEquals(plazaVea, state.selectedStore)
        assertEquals(RouteUiState.Ready(aPie), state.route)
    }

    @Test
    fun `si no hay camino o el servidor de rutas falla, el mapa sigue con las tiendas`() = runTest(dispatcher) {
        stubInsights()
        whenever(repository.nearbyStores(any(), any(), any())).thenReturn(NearbyStoresResponse(listOf(plazaVea)))
        whenever(repository.route(any(), any())).thenThrow(ScanError.Server("429"))
        val vm = viewModel()

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.nearby.value as NearbyUiState.Ready
        assertEquals(RouteUiState.Unavailable, state.route)
        assertEquals(listOf(plazaVea), state.tiendas)
    }

    @Test
    fun `elegir otra tienda recalcula la ruta y volver a la primera usa la cache`() = runTest(dispatcher) {
        stubInsights()
        whenever(repository.nearbyStores(any(), any(), any())).thenReturn(NearbyStoresResponse(listOf(plazaVea, promart)))
        whenever(repository.route(any(), any())).thenReturn(RouteResponse(aPie))
        val vm = viewModel(chains = listOf("Plaza Vea", "Promart"))
        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectStore(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(promart, (vm.nearby.value as NearbyUiState.Ready).selectedStore)

        vm.selectStore(0)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(plazaVea, (vm.nearby.value as NearbyUiState.Ready).selectedStore)

        verify(repository, times(2)).route(any(), any())
    }

    @Test
    fun `sin ubicacion disponible no consulta tiendas`() = runTest(dispatcher) {
        stubInsights()
        val vm = viewModel(location = null)

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NearbyUiState.LocationUnavailable, vm.nearby.value)
        verify(repository, never()).nearbyStores(any(), any(), any())
    }

    @Test
    fun `sin tiendas cerca lo dice en vez de mostrar una lista vacia`() = runTest(dispatcher) {
        stubInsights()
        whenever(repository.nearbyStores(any(), any(), any())).thenReturn(NearbyStoresResponse(emptyList()))
        val vm = viewModel()

        vm.findNearbyStores()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NearbyUiState.NoStoresNearby, vm.nearby.value)
    }

    @Test
    fun `permiso denegado queda registrado para mostrar como habilitarlo`() = runTest(dispatcher) {
        stubInsights()
        val vm = viewModel()

        vm.onLocationPermissionDenied()

        assertEquals(NearbyUiState.PermissionDenied, vm.nearby.value)
    }
}
