package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.pending.PendingScan
import pe.com.comparadorprecios.pending.PendingScanProcessor
import pe.com.comparadorprecios.pending.PendingScanRepository

/**
 * Dueño de la cola sin conexión. Vive a nivel de Activity (no de pantalla) para que el
 * procesamiento automático siga corriendo aunque el usuario cambie de pestaña.
 */
class PendingScansViewModel(
    private val repository: PendingScanRepository,
    private val processor: PendingScanProcessor,
) : ViewModel() {

    val pending: StateFlow<List<PendingScan>> =
        repository.pending.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    /** Guarda la foto para procesarla después: no se llama a la IA ni a las tiendas. */
    fun saveForLater(imageDataUri: String, thumbnail: ByteArray?, categoria: String?) {
        viewModelScope.launch {
            runCatching { repository.enqueue(imageDataUri, thumbnail, categoria) }
                .onSuccess {
                    _message.value = "Sin conexión: la foto quedó pendiente y se procesará sola al volver el internet."
                }
                .onFailure {
                    _message.value = "No se pudo guardar la foto en el dispositivo. Reintenta."
                }
        }
    }

    /**
     * Se invoca al detectar que volvió la conexión y también al abrir la app (por si quedaron
     * pendientes de una sesión anterior). Silencioso si no hay nada en cola.
     */
    fun processPending(saveBattery: Boolean, announceEmpty: Boolean = false) {
        if (_processing.value) return
        viewModelScope.launch {
            if (repository.current().isEmpty()) {
                if (announceEmpty) _message.value = "No hay escaneos pendientes."
                return@launch
            }
            _processing.value = true
            try {
                val result = processor.processAll(saveBattery)
                _message.value = when {
                    result.needsSignIn -> "Tu sesión expiró. Inicia sesión de nuevo para procesar los pendientes."
                    result.interruptedByNetwork && result.processed > 0 ->
                        "Se procesaron ${result.processed}, pero se cortó la conexión. El resto sigue pendiente."
                    result.interruptedByNetwork -> null // sigue sin red: no vale la pena avisar
                    result.failed > 0 && result.processed > 0 ->
                        "Se procesaron ${result.processed}. ${result.failed} no se pudieron identificar."
                    result.failed > 0 -> "${result.failed} escaneos no se pudieron identificar. Revísalos en Historial."
                    result.processed > 0 ->
                        "Listo: ${result.processed} ${if (result.processed == 1) "escaneo pendiente procesado" else "escaneos pendientes procesados"}."
                    else -> null
                }
            } finally {
                _processing.value = false
            }
        }
    }

    fun discard(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }
}
