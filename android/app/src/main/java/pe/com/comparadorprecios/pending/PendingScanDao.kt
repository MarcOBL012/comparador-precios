package pe.com.comparadorprecios.pending

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingScanDao {
    @Insert
    suspend fun insert(scan: PendingScan): Long

    /** Más antiguos primero: se procesan en el orden en que se tomaron. */
    @Query("SELECT * FROM pending_scans ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingScan>>

    @Query("SELECT * FROM pending_scans ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingScan>

    @Query("SELECT * FROM pending_scans WHERE id = :id")
    suspend fun getById(id: Long): PendingScan?

    @Query("SELECT COUNT(*) FROM pending_scans")
    fun observeCount(): Flow<Int>

    @Query("UPDATE pending_scans SET lastError = :message WHERE id = :id")
    suspend fun updateError(id: Long, message: String?)

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun delete(id: Long)
}
