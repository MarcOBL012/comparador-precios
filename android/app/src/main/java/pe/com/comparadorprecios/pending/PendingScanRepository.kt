package pe.com.comparadorprecios.pending

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class PendingScanRepository(
    private val dao: PendingScanDao,
    /** Carpeta del almacenamiento interno donde viven las fotos encoladas. */
    private val imagesDir: File,
) {
    val pending: Flow<List<PendingScan>> = dao.observeAll()
    val count: Flow<Int> = dao.observeCount()

    suspend fun enqueue(
        imageDataUri: String,
        thumbnail: ByteArray?,
        categoria: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        imagesDir.mkdirs()
        val file = File(imagesDir, "pending_${createdAt}_${UUID.randomUUID()}.txt")
        file.writeText(imageDataUri)
        return dao.insert(
            PendingScan(
                createdAt = createdAt,
                imagePath = file.absolutePath,
                thumbnail = thumbnail,
                categoria = categoria,
                lastError = null,
            )
        )
    }

    suspend fun current(): List<PendingScan> = dao.getAll()

    /** null si el archivo ya no está (por ejemplo, si el sistema limpió el almacenamiento). */
    fun readImage(scan: PendingScan): String? =
        File(scan.imagePath).takeIf { it.exists() }?.readText()

    suspend fun markError(id: Long, message: String?) = dao.updateError(id, message)

    suspend fun remove(id: Long) {
        dao.getById(id)?.let { File(it.imagePath).delete() }
        dao.delete(id)
    }
}
