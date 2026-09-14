package pe.com.comparadorprecios.history

import pe.com.comparadorprecios.data.StoreResult

data class PriceChange(
    val tienda: String,
    val before: Double?,
    val now: Double?,
) {
    /** Negativo = bajó. Null si falta uno de los dos precios. */
    val difference: Double? get() = if (before != null && now != null) now - before else null
}

object PriceTrend {
    fun bestPrice(tiendas: List<StoreResult>): Double? =
        tiendas.filter { it.isFound }.mapNotNull { it.precio }.minOrNull()

    /** Precio por tienda al escanear contra el de la última revisión; lo más barato de hoy primero. */
    fun compare(before: List<StoreResult>, now: List<StoreResult>): List<PriceChange> {
        fun priceIn(list: List<StoreResult>, tienda: String) =
            list.firstOrNull { it.tienda == tienda && it.isFound }?.precio

        val tiendas = (before + now).filter { it.isFound && it.precio != null }.map { it.tienda }.distinct()
        return tiendas
            .map { PriceChange(it, priceIn(before, it), priceIn(now, it)) }
            .sortedBy { it.now ?: Double.MAX_VALUE }
    }
}
