package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.GeoPoint
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreRoute
import pe.com.comparadorprecios.location.LatLng
import pe.com.comparadorprecios.location.UserLocationProvider
import pe.com.comparadorprecios.profile.ProfileCatalog

sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    /** `modo` es el que realmente usó el backend (con "auto" puede ser salud, tecnología…). */
    data class Ready(val modo: String, val insights: ProductInsights) : InsightsUiState
    data object Failed : InsightsUiState
}

sealed interface RouteUiState {
    data object Loading : RouteUiState
    data class Ready(val route: StoreRoute) : RouteUiState
    /** Sin camino o el servidor de rutas no respondió: el mapa igual muestra ambos puntos. */
    data object Unavailable : RouteUiState
}

sealed interface NearbyUiState {
    /** Aún no se pidió: se muestra el porqué antes de pedir permiso (aumenta la aceptación). */
    data object Idle : NearbyUiState
    data object Locating : NearbyUiState
    data class Ready(
        val origin: LatLng,
        val tiendas: List<NearbyStore>,
        val selected: Int = 0,
        val route: RouteUiState = RouteUiState.Loading,
    ) : NearbyUiState {
        val selectedStore: NearbyStore get() = tiendas[selected]
    }
    data object NoStoresNearby : NearbyUiState
    data object LocationUnavailable : NearbyUiState
    data object PermissionDenied : NearbyUiState
    data object Failed : NearbyUiState
}

/**
 * Lo que acompaña a la comparación de precios: datos/consejos del producto según el modo y el
 * perfil de la persona, y la tienda física más cercana con su ruta en el mapa.
 * Todo se carga aparte de la comparación: si falla, el precio — lo principal — no se afecta.
 */
class ProductExtrasViewModel(
    private val identification: Identification,
    /** Cadenas con tienda física donde el producto se encontró (ver StoreLocations.chainsToLocate). */
    private val chains: List<String>,
    private val repository: ScanRepository,
    private val modeStore: InsightsModeStore,
    private val locationProvider: UserLocationProvider,
    /** Intereses del perfil de la cuenta activa; vacío si no hay sesión o no configuró nada. */
    profileInterests: Flow<Set<String>> = flowOf(emptySet()),
) : ViewModel() {

    val selectedMode: StateFlow<String> =
        modeStore.mode.stateIn(viewModelScope, SharingStarted.Eagerly, InsightsMode.AUTO)

    val profile: StateFlow<Set<String>> =
        profileInterests.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _insights = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
    val insights: StateFlow<InsightsUiState> = _insights.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val nearby: StateFlow<NearbyUiState> = _nearby.asStateFlow()

    val hasChainsToLocate: Boolean get() = chains.isNotEmpty()

    /** Cambiar de modo y volver no debe gastar otra llamada a la IA (la clave incluye el perfil). */
    private val cache = mutableMapOf<Pair<String, List<String>>, InsightsUiState.Ready>()
    private var insightsJob: Job? = null
    private val routeCache = mutableMapOf<Int, RouteUiState>()
    private var routeJob: Job? = null

    init {
        viewModelScope.launch {
            combine(modeStore.mode, profileInterests) { mode, interests ->
                mode to ProfileCatalog.interestsFor(mode, interests)
            }.distinctUntilChanged().collect { (mode, perfil) -> load(mode, perfil) }
        }
    }

    fun selectMode(mode: String) {
        viewModelScope.launch { modeStore.setMode(mode) }
    }

    fun retryInsights() {
        val mode = selectedMode.value
        load(mode, ProfileCatalog.interestsFor(mode, profile.value), force = true)
    }

    private fun load(mode: String, perfil: List<String>, force: Boolean = false) {
        val key = mode to perfil
        cache[key]?.takeUnless { force }?.let {
            _insights.value = it
            return
        }
        insightsJob?.cancel()
        _insights.value = InsightsUiState.Loading
        insightsJob = viewModelScope.launch {
            _insights.value = try {
                val response = repository.insights(identification, mode, perfil)
                InsightsUiState.Ready(response.modo, response.insights).also { cache[key] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InsightsUiState.Failed
            }
        }
    }

    fun onLocationPermissionDenied() {
        _nearby.value = NearbyUiState.PermissionDenied
    }

    fun findNearbyStores() {
        if (chains.isEmpty() || _nearby.value == NearbyUiState.Locating) return
        _nearby.value = NearbyUiState.Locating
        viewModelScope.launch {
            _nearby.value = try {
                val location = locationProvider.current()
                if (location == null) {
                    NearbyUiState.LocationUnavailable
                } else {
                    val tiendas = repository.nearbyStores(chains, location.lat, location.lng).tiendas
                    if (tiendas.isEmpty()) NearbyUiState.NoStoresNearby else NearbyUiState.Ready(location, tiendas)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NearbyUiState.Failed
            }
            if (_nearby.value is NearbyUiState.Ready) loadRoute(0)
        }
    }

    /** Elegir otra tienda en el mapa recalcula la ruta (o la toma de caché si ya se pidió). */
    fun selectStore(index: Int) {
        val current = _nearby.value as? NearbyUiState.Ready ?: return
        if (index !in current.tiendas.indices || index == current.selected) return
        _nearby.value = current.copy(selected = index, route = routeCache[index] ?: RouteUiState.Loading)
        if (routeCache[index] == null) loadRoute(index)
    }

    private fun loadRoute(index: Int) {
        val ready = _nearby.value as? NearbyUiState.Ready ?: return
        val store = ready.tiendas[index]
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val route = try {
                repository.route(GeoPoint(ready.origin.lat, ready.origin.lng), GeoPoint(store.lat, store.lng)).ruta
                    ?.let { RouteUiState.Ready(it) } ?: RouteUiState.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RouteUiState.Unavailable
            }
            routeCache[index] = route
            _nearby.update { state ->
                if (state is NearbyUiState.Ready && state.selected == index) state.copy(route = route) else state
            }
        }
    }
}
