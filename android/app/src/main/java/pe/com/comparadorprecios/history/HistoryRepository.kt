package pe.com.comparadorprecios.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResultsJson
import pe.com.comparadorprecios.data.StoreResult

data class PriceCheckSnapshot(
    val checkedAt: Long,
    val tiendas: List<StoreResult>,
)

class HistoryRepository(private val dao: HistoryDao) {

    val all: Flow<List<HistoryItem>> =
        combine(dao.observeAll(), dao.observeLatestBestPrices()) { records, latest ->
            val latestByScan = latest.associate { it.scanId to it.bestPrice }
            records.map { it.toItem(latestBestPrice = latestByScan[it.id]) }
        }

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
            tipo = identification.tipo,
            confianza = identification.confianza,
            tiendasJson = StoreResultsJson.encode(tiendas),
            bestPrice = PriceTrend.bestPrice(tiendas),
            thumbnail = thumbnail,
        )
        return dao.upsert(record)
    }

    suspend fun get(id: Long): HistoryItem? = dao.getById(id)?.toItem(latestBestPrice = null)

    /** Borra y devuelve el registro para poder restaurarlo (deshacer). */
    suspend fun delete(id: Long): ScanRecord? {
        val record = dao.getById(id) ?: return null
        dao.deleteById(id)
        return record
    }

    suspend fun restore(record: ScanRecord) {
        dao.upsert(record)
    }

    suspend fun saveCheck(scanId: Long, tiendas: List<StoreResult>, checkedAt: Long = System.currentTimeMillis()) {
        dao.insertCheck(
            PriceCheck(scanId = scanId, checkedAt = checkedAt, tiendasJson = StoreResultsJson.encode(tiendas), bestPrice = PriceTrend.bestPrice(tiendas))
        )
    }

    /** La persona confirmó "sí, es este producto" — sirve de referencia si se repite (ver [findConfirmedMatch]). */
    suspend fun confirm(scanId: Long) = dao.markConfirmed(scanId)

    /** La persona dijo "no es este" y dio el nombre completo y/o un link para ubicarlo. */
    suspend fun saveCorrection(scanId: Long, note: String) = dao.setCorrectionNote(scanId, note)

    /** Un escaneo anterior del mismo producto exacto que ya se confirmó correcto (o null si no hay). */
    suspend fun findConfirmedMatch(identification: Identification): HistoryItem? =
        dao.findConfirmedMatch(identification.marca, identification.nombre, identification.tipo)
            ?.toItem(latestBestPrice = null)

    fun latestCheck(scanId: Long): Flow<PriceCheckSnapshot?> =
        dao.observeLatestCheck(scanId).map { check -> check?.let { PriceCheckSnapshot(it.checkedAt, StoreResultsJson.decode(it.tiendasJson)) } }

    private fun ScanRecord.toItem(latestBestPrice: Double?): HistoryItem = HistoryItem(
        id = id,
        title = "$marca $nombre $presentacion".trim(),
        dateMillis = createdAt,
        bestPrice = bestPrice,
        latestBestPrice = latestBestPrice,
        thumbnail = thumbnail,
        identification = Identification(marca, nombre, presentacion, categoria, confianza, tipo),
        tiendas = StoreResultsJson.decode(tiendasJson),
    )
}
