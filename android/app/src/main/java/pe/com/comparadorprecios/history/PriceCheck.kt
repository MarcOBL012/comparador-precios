package pe.com.comparadorprecios.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una revisión posterior de los precios de un escaneo. Se guarda sin clave foránea a propósito:
 * al borrar un escaneo del historial se puede deshacer, y las revisiones deben volver con él.
 */
@Entity(tableName = "price_checks", indices = [Index("scanId")])
data class PriceCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scanId: Long,
    val checkedAt: Long,
    val tiendasJson: String,
    val bestPrice: Double?,
)

data class LatestBestPrice(
    val scanId: Long,
    val bestPrice: Double?,
)
