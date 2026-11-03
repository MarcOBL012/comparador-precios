package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.AlertView
import pe.com.comparadorprecios.data.ApiError
import pe.com.comparadorprecios.data.HistoryResponse
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.ProductPayload
import pe.com.comparadorprecios.data.Watch
import pe.com.comparadorprecios.data.WatchRepository
import pe.com.comparadorprecios.data.WatchStatus

data class HistoryDialog(val watch: Watch, val loading: Boolean = true, val history: HistoryResponse? = null, val error: String? = null)

data class WatchesUiState(
    val loading: Boolean = true,
    val watches: List<Watch> = emptyList(),
    val alerts: List<AlertView> = emptyList(),
    val unreadAlerts: Int = 0,
    /** Error de carga que deja la pantalla sin datos (con "Reintentar"). */
    val loadError: String? = null,
    /** Seguimientos con una acción en curso: se desactivan sus botones y se muestra progreso. */
    val busy: Set<String> = emptySet(),
    /** Aviso corto de la última acción (éxito o error) para un Snackbar. */
    val message: String? = null,
    val history: HistoryDialog? = null,
)

sealed interface FollowUiState {
    data object Idle : FollowUiState
    data object Saving : FollowUiState
    data class Done(val watch: Watch) : FollowUiState
    data class Failed(val message: String, val alreadyFollowing: Boolean = false) : FollowUiState
}

/** Texto para la persona según el tipo de error: el servidor ya responde en español; la red lo pone la app. */
fun Throwable.userMessage(): String = when (this) {
    is ApiError.Validation -> detalles.firstOrNull()?.let { "${it.mensaje}" } ?: message.orEmpty()
    is ApiError -> message.orEmpty()
    else -> "No se pudo completar la acción. Reintenta."
}

class WatchesViewModel(private val repository: WatchRepository) : ViewModel() {

    private val _state = MutableStateFlow(WatchesUiState())
    val state: StateFlow<WatchesUiState> = _state.asStateFlow()

    private val _follow = MutableStateFlow<FollowUiState>(FollowUiState.Idle)
    val follow: StateFlow<FollowUiState> = _follow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val watches = repository.list().items
                val alerts = repository.alerts()
                _state.update {
                    it.copy(loading = false, watches = watches, alerts = alerts.items, unreadAlerts = alerts.sinLeer)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.userMessage()) }
            }
        }
    }

    /** Ejecuta una acción sobre un seguimiento marcándolo ocupado; el resultado reemplaza al seguimiento en la lista. */
    private fun act(id: String, successMessage: String?, block: suspend () -> Watch) {
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            try {
                val updated = block()
                _state.update { s ->
                    s.copy(
                        watches = s.watches.map { if (it.id == updated.id) updated else it },
                        message = successMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    /** "Revisar ahora": consulta las tiendas, guarda los precios y avisa si saltó una alerta. */
    fun check(id: String) {
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            try {
                val result = repository.check(id)
                _state.update { s ->
                    s.copy(
                        watches = s.watches.map { if (it.id == id) result.seguimiento else it },
                        alerts = result.alertasNuevas + s.alerts,
                        unreadAlerts = s.unreadAlerts + result.alertasNuevas.size,
                        message = when {
                            result.alertasNuevas.isNotEmpty() -> result.alertasNuevas.first().mensaje
                            result.ofertasGuardadas == 0 -> "Hoy no encontramos precios para este producto."
                            else -> "Precios actualizados."
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    fun togglePause(watch: Watch) {
        val target = if (watch.isPaused) WatchStatus.ACTIVA else WatchStatus.PAUSADA
        act(watch.id, if (watch.isPaused) "Seguimiento reactivado." else "Seguimiento en pausa.") {
            repository.setStatus(watch.id, target)
        }
    }

    /** Un seguimiento que ya alcanzó su objetivo se reactiva con un objetivo nuevo, más bajo que el precio actual. */
    fun reactivate(watch: Watch, newTarget: Double) =
        act(watch.id, "Seguimiento reactivado con un nuevo objetivo.") {
            repository.setStatus(watch.id, WatchStatus.ACTIVA, newTarget)
        }

    fun setTarget(watch: Watch, target: Double?) =
        act(watch.id, if (target == null) "Objetivo quitado: solo te avisaremos de mínimos." else "Objetivo actualizado.") {
            repository.setTarget(watch.id, target)
        }

    fun delete(watch: Watch) {
        if (watch.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + watch.id) }
        viewModelScope.launch {
            try {
                repository.delete(watch.id)
                _state.update { s ->
                    s.copy(
                        watches = s.watches.filterNot { it.id == watch.id },
                        alerts = s.alerts.filterNot { it.seguimientoId == watch.id },
                        message = "Seguimiento eliminado.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = it.busy - watch.id) }
            }
        }
    }

    fun openHistory(watch: Watch) {
        _state.update { it.copy(history = HistoryDialog(watch)) }
        viewModelScope.launch {
            val next = try {
                // Se vuelve a leer el seguimiento (GET) junto con su historial: el dato mostrado es el del servidor.
                val fresh = repository.get(watch.id)
                HistoryDialog(fresh, loading = false, history = repository.history(watch.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HistoryDialog(watch, loading = false, error = e.userMessage())
            }
            _state.update { s -> if (s.history?.watch?.id == watch.id) s.copy(history = next) else s }
        }
    }

    fun closeHistory() = _state.update { it.copy(history = null) }

    fun markAlert(alert: AlertView, read: Boolean) {
        viewModelScope.launch {
            try {
                val updated = repository.markAlert(alert.id, read)
                _state.update { s ->
                    s.copy(
                        alerts = s.alerts.map { if (it.id == updated.id) updated else it },
                        unreadAlerts = (s.unreadAlerts + if (read) -1 else 1).coerceAtLeast(0),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // ── Seguir el producto que se acaba de escanear ──

    fun follow(identification: Identification, target: Double?) {
        if (_follow.value == FollowUiState.Saving) return
        _follow.value = FollowUiState.Saving
        viewModelScope.launch {
            _follow.value = try {
                val watch = repository.create(
                    ProductPayload(
                        marca = identification.marca,
                        nombre = identification.nombre,
                        presentacion = identification.presentacion,
                        categoria = identification.categoria,
                        tipo = identification.tipo,
                    ),
                    target,
                )
                _state.update { it.copy(watches = listOf(watch) + it.watches) }
                FollowUiState.Done(watch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FollowUiState.Failed(e.userMessage(), alreadyFollowing = (e as? ApiError.Conflict)?.codigo == "ya_seguido")
            }
        }
    }

    fun resetFollow() {
        _follow.value = FollowUiState.Idle
    }
}
