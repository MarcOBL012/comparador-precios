package pe.com.comparadorprecios.ui

import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

/** Estados de la pantalla de escaneo → resultados (spec + HANDOFF). */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Loading : ScanUiState

    /** El mensaje viene de ScanError.Unauthorized (ScanRepository) para mostrarlo antes de cerrar sesión. */
    data class Unauthorized(
        val message: String = "Tu sesión expiró. Inicia sesión de nuevo.",
    ) : ScanUiState

    /** confianza < 0.5 → el backend devolvió tiendas: []. Reintentar o ingreso manual. */
    data class LowConfidence(val identification: Identification) : ScanUiState

    /** Comparación lista. `tiendas` ya viene ordenada para mostrar (ver PriceSorting). */
    data class Success(
        val identification: Identification,
        val tiendas: List<StoreResult>,
    ) : ScanUiState

    data class Error(val message: String) : ScanUiState
}
