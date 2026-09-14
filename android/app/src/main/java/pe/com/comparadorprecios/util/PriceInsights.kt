package pe.com.comparadorprecios.util

import pe.com.comparadorprecios.data.StoreResult

data class PriceSummary(
    val bestStore: String,
    val bestPrice: Double,
    val highestPrice: Double,
    val offersCount: Int,
) {
    /** Lo que se deja de pagar frente a la opción más cara. Cero si solo hay una oferta. */
    val savings: Double get() = highestPrice - bestPrice
}

object PriceInsights {
    fun summarize(tiendas: List<StoreResult>): PriceSummary? {
        val offers = tiendas.filter { it.isFound && it.precio != null }
        val best = offers.minByOrNull { it.precio!! } ?: return null
        return PriceSummary(
            bestStore = best.tienda,
            bestPrice = best.precio!!,
            highestPrice = offers.maxOf { it.precio!! },
            offersCount = offers.size,
        )
    }

    /** Cuánto más cuesta esta oferta que la mejor; null si no tiene precio o es la mejor. */
    fun extraOverBest(result: StoreResult, summary: PriceSummary?): Double? {
        val precio = result.precio ?: return null
        if (summary == null || !result.isFound) return null
        val extra = precio - summary.bestPrice
        return if (extra > 0.005) extra else null
    }
}
