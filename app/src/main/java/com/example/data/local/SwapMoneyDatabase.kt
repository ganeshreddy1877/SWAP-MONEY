package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_sync_queue")
data class PendingSyncEntity(
    @PrimaryKey val id: String,
    val idempotencyKey: String,
    val actionLabel: String,
    val payloadSummary: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "cached_history")
data class CachedHistoryEntity(
    @PrimaryKey val id: String,
    val swapCode: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val amountInr: Int?,
    val exchangePairLabel: String?,
    val timestampLabel: String,
    val statusName: String?,
    val partnerName: String?,
    val createdAtEpochMs: Long
)

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync_queue ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<PendingSyncEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync_queue WHERE id = :id")
    suspend fun removeById(id: String)

    @Query("DELETE FROM pending_sync_queue")
    suspend fun clearAll()
}

@Dao
interface CachedHistoryDao {
    @Query("SELECT * FROM cached_history ORDER BY createdAtEpochMs DESC")
    fun observeHistory(): Flow<List<CachedHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedHistoryEntity>)
}

@Database(
    entities = [PendingSyncEntity::class, CachedHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SwapMoneyDatabase : RoomDatabase() {
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun cachedHistoryDao(): CachedHistoryDao

    companion object {
        @Volatile
        private var instance: SwapMoneyDatabase? = null

        fun getInstance(context: Context): SwapMoneyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SwapMoneyDatabase::class.java,
                    "swap_money_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
