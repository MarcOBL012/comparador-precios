package pe.com.comparadorprecios.history

import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

data class HistoryItem(
    val id: Long,
    val title: String,
    val dateMillis: Long,
    val bestPrice: Double?,
    /** Mejor precio en la última revisión; null si nunca se revisó. */
    val latestBestPrice: Double?,
    val thumbnail: ByteArray,
    val identification: Identification,
    val tiendas: List<StoreResult>,
) {
    val priceDropped: Boolean
        get() = bestPrice != null && latestBestPrice != null && latestBestPrice < bestPrice
}
