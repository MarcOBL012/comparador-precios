package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.DeviceContextPayload
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.HistoryRepository

/**
 * ADAPTACIÓN posterior a la búsqueda: la persona confirma si el resultado era el producto
 * correcto. "Sí" lo marca como confirmado (referencia para [HistoryRepository.findConfirmedMatch]
 * si se vuelve a escanear lo mismo). "No" guarda una corrección (nombre completo y/o link) y,
 * si dio un nombre, reintenta la búsqueda con ese texto.
 */
sealed interface ConfirmationState {
    data object Asking : ConfirmationState
    data object Confirmed : ConfirmationState
    data object CorrectionForm : ConfirmationState
    data object SearchingCorrection : ConfirmationState
    data class CorrectionResult(val tiendas: List<StoreResult>) : ConfirmationState
    data class CorrectionFailed(val message: String) : ConfirmationState
}

class ProductConfirmationViewModel(
    private val scanId: Long,
    private val identification: Identification,
    private val saveBattery: Boolean,
    private val historyRepository: HistoryRepository,
    private val scanRepository: ScanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ConfirmationState>(ConfirmationState.Asking)
    val state: StateFlow<ConfirmationState> = _state.asStateFlow()

    private val _previousConfirmedMatch = MutableStateFlow<HistoryItem?>(null)
    /** Un escaneo anterior de este mismo producto ya confirmado — útil sobre todo si esta búsqueda no dio tiendas. */
    val previousConfirmedMatch: StateFlow<HistoryItem?> = _previousConfirmedMatch.asStateFlow()

    init {
        viewModelScope.launch {
            _previousConfirmedMatch.value = historyRepository.findConfirmedMatch(identification)?.takeIf { it.id != scanId }
        }
    }

    fun confirmCorrect() {
        _state.value = ConfirmationState.Confirmed
        viewModelScope.launch { historyRepository.confirm(scanId) }
    }

    fun showCorrectionForm() {
        _state.value = ConfirmationState.CorrectionForm
    }

    fun cancelCorrection() {
        _state.value = ConfirmationState.Asking
    }

    fun submitCorrection(nombreCompleto: String, link: String) {
        val note = listOfNotNull(
            nombreCompleto.trim().takeIf { it.isNotEmpty() },
            link.trim().takeIf { it.isNotEmpty() }?.let { "Link: $it" },
        ).joinToString(" · ")
        if (note.isBlank()) return

        _state.value = ConfirmationState.SearchingCorrection
        viewModelScope.launch {
            try {
                historyRepository.saveCorrection(scanId, note)
                val texto = nombreCompleto.trim()
                if (texto.isEmpty()) {
                    // Solo dio un link, sin nombre: se guarda la corrección pero no hay con qué reintentar la búsqueda.
                    _state.value = ConfirmationState.Confirmed
                    return@launch
                }
                val corrected = identification.copy(marca = "", nombre = texto, presentacion = "")
                val response = scanRepository.prices(corrected, DeviceContextPayload(saveBattery))
                historyRepository.saveCheck(scanId, response.tiendas)
                _state.value = ConfirmationState.CorrectionResult(response.tiendas)
            } catch (e: ScanError) {
                _state.value = ConfirmationState.CorrectionFailed(e.message ?: "No se pudo buscar de nuevo.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ConfirmationState.CorrectionFailed("No se pudo buscar de nuevo.")
            }
        }
    }
}
