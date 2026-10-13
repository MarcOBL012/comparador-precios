package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.location.UserLocationProvider

sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    /** `modo` es el que realmente usó el backend (con "auto" puede ser salud, tecnología…). */
    data class Ready(val modo: String, val insights: ProductInsights) : InsightsUiState
    data object Failed : InsightsUiState
}

sealed interface NearbyUiState {
    /** Aún no se pidió: se muestra el porqué antes de pedir permiso (aumenta la aceptación). */
    data object Idle : NearbyUiState
    data object Locating : NearbyUiState
    data class Ready(val tiendas: List<NearbyStore>) : NearbyUiState
    data object NoStoresNearby : NearbyUiState
    data object LocationUnavailable : NearbyUiState
    data object PermissionDenied : NearbyUiState
    data object Failed : NearbyUiState
}

/**
 * Lo que se muestra debajo de la comparación de precios: datos/consejos del producto según el
 * modo elegido, y la tienda física más cercana de las cadenas donde se encontró.
 * Ambos se cargan aparte de la comparación: si fallan, el precio — lo principal — no se afecta.
 */
class ProductExtrasViewModel(
    private val identification: Identification,
    /** Cadenas con tienda física donde el producto se encontró (ver StoreLocations.chainsToLocate). */
    private val chains: List<String>,
    private val repository: ScanRepository,
    private val modeStore: InsightsModeStore,
    private val locationProvider: UserLocationProvider,
) : ViewModel() {

    val selectedMode: StateFlow<String> =
        modeStore.mode.stateIn(viewModelScope, SharingStarted.Eagerly, InsightsMode.AUTO)

    private val _insights = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
    val insights: StateFlow<InsightsUiState> = _insights.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val nearby: StateFlow<NearbyUiState> = _nearby.asStateFlow()

    val hasChainsToLocate: Boolean get() = chains.isNotEmpty()

    /** Cambiar de modo y volver no debe gastar otra llamada a la IA. */
    private val cache = mutableMapOf<String, InsightsUiState.Ready>()
    private var insightsJob: Job? = null

    init {
        viewModelScope.launch {
            modeStore.mode.distinctUntilChanged().collect { load(it) }
        }
    }

    fun selectMode(mode: String) {
        viewModelScope.launch { modeStore.setMode(mode) }
    }

    fun retryInsights() = load(selectedMode.value, force = true)

    private fun load(mode: String, force: Boolean = false) {
        cache[mode]?.takeUnless { force }?.let {
            _insights.value = it
            return
        }
        insightsJob?.cancel()
        _insights.value = InsightsUiState.Loading
        insightsJob = viewModelScope.launch {
            _insights.value = try {
                val response = repository.insights(identification, mode)
                InsightsUiState.Ready(response.modo, response.insights).also { cache[mode] = it }
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
                    if (tiendas.isEmpty()) NearbyUiState.NoStoresNearby else NearbyUiState.Ready(tiendas)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NearbyUiState.Failed
            }
        }
    }
}
