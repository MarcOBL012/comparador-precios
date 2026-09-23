package pe.com.comparadorprecios.pending

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Foto tomada sin conexión, a la espera de internet para identificarla y comparar precios.
 *
 * La imagen NO se guarda en la base de datos: una data URI comprimida llega a ~3 MB y SQLite en
 * Android corta las filas que superan los ~2 MB del CursorWindow. Se guarda en un archivo del
 * almacenamiento interno y aquí queda solo la ruta.
 */
@Entity(tableName = "pending_scans")
data class PendingScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Momento en que se tomó la foto, no en que se procesó: el historial conserva esta fecha. */
    val createdAt: Long,
    val imagePath: String,
    val thumbnail: ByteArray?,
    /** Categoría elegida antes de la foto, para no perder esa pista al procesar después. */
    val categoria: String?,
    /** Motivo del último intento fallido; null mientras no se haya intentado o si falló solo por red. */
    val lastError: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingScan) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
