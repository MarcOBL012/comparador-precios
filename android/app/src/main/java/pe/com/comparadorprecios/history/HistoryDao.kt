package pe.com.comparadorprecios.history

import androidx.room.Dao
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
}
