package pe.com.comparadorprecios.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.StoreResult

class HistoryRepository(private val dao: HistoryDao) {

    val all: Flow<List<HistoryItem>> =
        dao.observeAll().map { records -> records.map { it.toItem() } }

    suspend fun save(
        identification: Identification,
        tiendas: List<StoreResult>,
        thumbnail: ByteArray,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        val record = ScanRecord(
            createdAt = createdAt,
            marca = identification.marca,
            nombre = identification.nombre,
            presentacion = identification.presentacion,
            categoria = identification.categoria,
            confianza = identification.confianza,
            tiendasJson = RetrofitProvider.json.encodeToString(ListSerializer(StoreResult.serializer()), tiendas),
            bestPrice = tiendas.filter { it.estado == "encontrado" }.minOfOrNull { it.precio ?: Double.MAX_VALUE },
            thumbnail = thumbnail,
        )
        return dao.upsert(record)
    }

    suspend fun get(id: Long): HistoryItem? = dao.getById(id)?.toItem()

    /** Borra y devuelve el registro para poder restaurarlo (deshacer). */
    suspend fun delete(id: Long): ScanRecord? {
        val record = dao.getById(id) ?: return null
        dao.deleteById(id)
        return record
    }

    suspend fun restore(record: ScanRecord) {
        dao.upsert(record)
    }

    private fun ScanRecord.toItem(): HistoryItem {
        val tiendas = RetrofitProvider.json.decodeFromString(ListSerializer(StoreResult.serializer()), tiendasJson)
        return HistoryItem(
            id = id,
            title = "$marca $nombre $presentacion".trim(),
            dateMillis = createdAt,
            bestPrice = bestPrice,
            thumbnail = thumbnail,
            identification = Identification(marca, nombre, presentacion, categoria, confianza),
            tiendas = tiendas,
        )
    }
}
