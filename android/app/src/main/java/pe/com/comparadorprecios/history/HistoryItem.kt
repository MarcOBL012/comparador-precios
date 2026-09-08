package pe.com.comparadorprecios.history

import pe.com.comparadorprecios.data.StoreResult

data class HistoryItem(
    val id: Long,
    val title: String,
    val dateMillis: Long,
    val bestPrice: Double?,
    val thumbnail: ByteArray,
    val identification: pe.com.comparadorprecios.data.Identification,
    val tiendas: List<StoreResult>,
)
