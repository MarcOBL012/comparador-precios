package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.isLowConfidence
import pe.com.comparadorprecios.util.PriceSorting

class ScanViewModel(
    private val repository: ScanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = ScanUiState.Idle
    }

    /**
     * Cierra sesión desde el estado Unauthorized usando viewModelScope (Activity-scoped,
     * NO scoped al composable) para que la corrutina de signOut() sobreviva aunque la rama
     * Unauthorized se recomponga/dispose (p.ej. si algo más resetea el estado mientras tanto).
     * Colocar el reset a Idle en un `finally` alrededor de una llamada suspend garantiza que
     * signOut() corra hasta completarse (o falle) antes de limpiar el estado, en vez de que un
     * reset síncrono cancele la corrutina en su primer punto de suspensión (ver MainActivity).
     */
    fun signOutAfterUnauthorized(signOut: suspend () -> Unit) {
        if (_state.value !is ScanUiState.Unauthorized) return
        viewModelScope.launch {
            try {
                signOut()
            } finally {
                _state.value = ScanUiState.Idle
            }
        }
    }

    fun scan(imageDataUri: String) {
        if (_state.value is ScanUiState.Loading) return
        _state.value = ScanUiState.Loading
        viewModelScope.launch {
            try {
                val response = repository.scan(imageDataUri)
                _state.value = if (response.identification.isLowConfidence()) {
                    ScanUiState.LowConfidence(response.identification)
                } else {
                    ScanUiState.Success(
                        identification = response.identification,
                        tiendas = PriceSorting.sortForDisplay(response.tiendas),
                    )
                }
            } catch (e: ScanError.Unauthorized) {
                _state.value = ScanUiState.Unauthorized(
                    e.message ?: "Tu sesión expiró. Inicia sesión de nuevo."
                )
            } catch (e: ScanError.Validation) {
                _state.value = ScanUiState.Error(
                    e.message ?: "La imagen no es válida. Toma otra foto."
                )
            } catch (e: ScanError.IdentificationFailed) {
                _state.value = ScanUiState.Error(
                    e.message ?: "No se pudo identificar el producto."
                )
            } catch (e: ScanError) {
                _state.value = ScanUiState.Error(e.message ?: "Ocurrió un error. Reintenta.")
            } catch (e: Exception) {
                _state.value = ScanUiState.Error("Error inesperado. Reintenta.")
            }
        }
    }
}
