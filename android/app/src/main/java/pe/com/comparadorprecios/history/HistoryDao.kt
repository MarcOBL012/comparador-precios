package pe.com.comparadorprecios.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(record: ScanRecord): Long

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanRecord?

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertCheck(check: PriceCheck): Long

    @Query("SELECT * FROM price_checks WHERE scanId = :scanId ORDER BY checkedAt DESC LIMIT 1")
    fun observeLatestCheck(scanId: Long): Flow<PriceCheck?>

    @Query(
        "SELECT scanId, bestPrice FROM price_checks AS c " +
            "WHERE checkedAt = (SELECT MAX(checkedAt) FROM price_checks WHERE scanId = c.scanId)"
    )
    fun observeLatestBestPrices(): Flow<List<LatestBestPrice>>
}
