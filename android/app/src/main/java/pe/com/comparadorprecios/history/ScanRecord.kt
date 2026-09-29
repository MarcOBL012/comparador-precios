package pe.com.comparadorprecios.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val confianza: Double,
    val tiendasJson: String,
    val bestPrice: Double?,
    val thumbnail: ByteArray,
    @ColumnInfo(defaultValue = "") val tipo: String = "",
    /** La persona confirmó que este resultado sí era el producto que buscaba. */
    @ColumnInfo(defaultValue = "0") val confirmed: Boolean = false,
    /** Nombre completo o link que la persona dio cuando el resultado NO era el producto correcto. */
    val correctionNote: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
