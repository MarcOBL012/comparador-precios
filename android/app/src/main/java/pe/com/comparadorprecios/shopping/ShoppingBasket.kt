package pe.com.comparadorprecios.shopping

import pe.com.comparadorprecios.data.StoreResult

data class BasketLine(
    val title: String,
    val quantity: Int,
    val tiendas: List<StoreResult>,
)

data class StoreBasket(
    val tienda: String,
    val total: Double,
    val foundCount: Int,
    val missing: List<String>,
    val fromWeb: Boolean,
)

object ShoppingBasket {
    /**
     * Cuánto cuesta la lista completa en cada tienda. Primero las que tienen más productos
     * (una tienda barata a la que le falta la mitad no resuelve la compra), luego la más barata.
     */
    fun compare(lines: List<BasketLine>): List<StoreBasket> {
        val stores = lines.flatMap { line -> line.tiendas.filter { it.isFound && it.precio != null } }
            .groupBy { it.tienda }

        return stores.map { (tienda, offers) ->
            val priced = lines.mapNotNull { line ->
                line.tiendas.firstOrNull { it.tienda == tienda && it.isFound && it.precio != null }
                    ?.let { line to it.precio!! }
            }
            StoreBasket(
                tienda = tienda,
                total = priced.sumOf { (line, precio) -> precio * line.quantity },
                foundCount = priced.size,
                missing = lines.filter { line -> priced.none { it.first === line } }.map { it.title },
                fromWeb = offers.all { it.isFromWeb },
            )
        }.sortedWith(compareByDescending<StoreBasket> { it.foundCount }.thenBy { it.total })
    }
}
