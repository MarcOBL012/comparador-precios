package pe.com.comparadorprecios.data

import kotlinx.serialization.Serializable

/** Contrato de la API REST propia de seguimientos (ver docs/api/openapi.yaml en el backend). */

object WatchStatus {
    const val ACTIVA = "activa"
    const val PAUSADA = "pausada"
    const val ALCANZADA = "alcanzada"
}

@Serializable
data class ProductPayload(
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val tipo: String,
)

@Serializable
data class CreateWatchRequest(
    val producto: ProductPayload,
    val precioObjetivo: Double? = null,
)

@Serializable
data class WatchProduct(
    val id: String,
    val marca: String = "",
    val nombre: String,
    val presentacion: String = "",
    val categoria: String,
    val tipo: String = "",
) {
    val title: String get() = listOf(marca, nombre, presentacion).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class PriceSummary(
    val mejorPrecio: Double? = null,
    val tienda: String? = null,
    val minimoHistorico: Double? = null,
    val observaciones: Int = 0,
)

@Serializable
data class Watch(
    val id: String,
    val producto: WatchProduct,
    val precioObjetivo: Double? = null,
    val estado: String,
    val ultimaRevision: String? = null,
    val creadoEn: String,
    val actualizadoEn: String,
    val resumenPrecios: PriceSummary = PriceSummary(),
) {
    val isActive: Boolean get() = estado == WatchStatus.ACTIVA
    val isPaused: Boolean get() = estado == WatchStatus.PAUSADA
    val reachedTarget: Boolean get() = estado == WatchStatus.ALCANZADA
}

@Serializable
data class WatchPage(
    val items: List<Watch> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
)

@Serializable
data class AlertType(val codigo: String, val nombre: String)

@Serializable
data class AlertView(
    val id: Long,
    val tipo: AlertType,
    val mensaje: String,
    val seguimientoId: String,
    val producto: String,
    val tienda: String,
    val precio: Double,
    val creadaEn: String,
    val leida: Boolean,
)

@Serializable
data class AlertPage(
    val items: List<AlertView> = emptyList(),
    val total: Int = 0,
    val sinLeer: Int = 0,
)

@Serializable
data class MarkAlertRequest(val leida: Boolean)

@Serializable
data class CheckResponse(
    val seguimiento: Watch,
    val ofertasGuardadas: Int = 0,
    val ofertasDescartadas: Int = 0,
    val alertasNuevas: List<AlertView> = emptyList(),
)

@Serializable
data class HistoryPoint(val observadoEn: String, val tienda: String, val precio: Double)

@Serializable
data class HistoryStats(
    val minimo: Double? = null,
    val maximo: Double? = null,
    val promedio: Double? = null,
    val cantidad: Int = 0,
    /** "baja" | "sube" | "estable" | "sin_datos". */
    val tendencia: String = "sin_datos",
)

@Serializable
data class HistoryResponse(
    val dias: Int,
    val puntos: List<HistoryPoint> = emptyList(),
    val estadisticas: HistoryStats = HistoryStats(),
)

@Serializable
data class FieldIssue(val campo: String, val mensaje: String)

/** Cuerpo de error de la API: `codigo` es estable (para decidir qué hacer), `error` es para mostrar. */
@Serializable
data class ApiErrorBody(
    val error: String = "",
    val codigo: String = "",
    val detalles: List<FieldIssue> = emptyList(),
)
