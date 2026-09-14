package pe.com.comparadorprecios.shopping

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val addedAt: Long,
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val tipo: String,
    val quantity: Int,
    val tiendasJson: String,
    val pricesUpdatedAt: Long,
)
