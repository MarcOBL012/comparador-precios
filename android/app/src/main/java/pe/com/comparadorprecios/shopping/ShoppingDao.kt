package pe.com.comparadorprecios.shopping

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Query("SELECT * FROM shopping_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items ORDER BY addedAt DESC")
    suspend fun getAll(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE marca = :marca AND nombre = :nombre AND presentacion = :presentacion LIMIT 1")
    suspend fun findSame(marca: String, nombre: String, presentacion: String): ShoppingItem?

    @Query("UPDATE shopping_items SET quantity = :quantity WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Int)

    @Query("UPDATE shopping_items SET tiendasJson = :tiendasJson, pricesUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePrices(id: Long, tiendasJson: String, updatedAt: Long)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)
}
