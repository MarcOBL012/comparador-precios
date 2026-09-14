package pe.com.comparadorprecios.shopping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResultsJson
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.data.title

data class ShoppingLine(
    val id: Long,
    val identification: Identification,
    val quantity: Int,
    val tiendas: List<StoreResult>,
    val pricesUpdatedAt: Long,
) {
    val title: String get() = identification.title
}

class ShoppingListRepository(private val dao: ShoppingDao) {

    val lines: Flow<List<ShoppingLine>> = dao.observeAll().map { items -> items.map { it.toLine() } }

    /** Si el producto ya está en la lista suma una unidad y se queda con los precios más recientes. */
    suspend fun add(identification: Identification, tiendas: List<StoreResult>, now: Long = System.currentTimeMillis()) {
        val existing = dao.findSame(identification.marca, identification.nombre, identification.presentacion)
        if (existing != null) {
            dao.setQuantity(existing.id, existing.quantity + 1)
            dao.updatePrices(existing.id, StoreResultsJson.encode(tiendas), now)
            return
        }
        dao.insert(
            ShoppingItem(
                addedAt = now,
                marca = identification.marca,
                nombre = identification.nombre,
                presentacion = identification.presentacion,
                categoria = identification.categoria,
                tipo = identification.tipo,
                quantity = 1,
                tiendasJson = StoreResultsJson.encode(tiendas),
                pricesUpdatedAt = now,
            )
        )
    }

    suspend fun setQuantity(id: Long, quantity: Int) {
        if (quantity <= 0) dao.delete(id) else dao.setQuantity(id, quantity)
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun current(): List<ShoppingLine> = dao.getAll().map { it.toLine() }

    suspend fun updatePrices(id: Long, tiendas: List<StoreResult>, now: Long = System.currentTimeMillis()) =
        dao.updatePrices(id, StoreResultsJson.encode(tiendas), now)

    private fun ShoppingItem.toLine() = ShoppingLine(
        id = id,
        identification = Identification(marca, nombre, presentacion, categoria, confianza = 1.0, tipo = tipo),
        quantity = quantity,
        tiendas = StoreResultsJson.decode(tiendasJson),
        pricesUpdatedAt = pricesUpdatedAt,
    )
}
