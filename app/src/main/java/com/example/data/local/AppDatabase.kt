package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// -------------------------------------------------------------------------
// ENTITIES
// -------------------------------------------------------------------------

@Entity(tableName = "gateway_settings")
data class GatewaySettings(
    @PrimaryKey val id: Int = 1, // Only one settings row
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceId: String = "android_gateway",
    val simSlotSelection: Int = -1, // -1: default, 0: SIM1, 1: SIM2
    val syncIntervalSeconds: Int = 30,
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val isTestMode: Boolean = true, // Default to true for safety
    val limitSmsPerDay: Int = 500,
    val limitSmsPerHour: Int = 100,
    val limitSmsPerMin: Int = 5,
    val workingHoursStart: String = "00:00", // "HH:mm"
    val workingHoursEnd: String = "23:59",
    val isGatewayEnabled: Boolean = false
)

@Entity(tableName = "synced_sms")
data class SyncedSms(
    @PrimaryKey val id: Long, // Matches the Android SMS _id
    val address: String,
    val body: String,
    val date: Long,
    val simSlot: Int,
    val syncedAt: Long = System.currentTimeMillis()
) {
    // Generate simple content fingerprint to prevent duplicates or identify deletion tombstones
    fun getFingerprint(): String {
        return "${address}_${date}_${body.hashCode()}"
    }
}

@Entity(tableName = "tombstones", primaryKeys = ["address", "date", "fingerprint"])
data class Tombstone(
    val address: String,
    val date: Long,
    val fingerprint: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_queue")
data class SmsQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requestId: String, // Prevent double processing
    val phoneNumber: String,
    val messageBody: String,
    val simSlot: Int, // 0 for SIM1, 1 for SIM2, -1 for default
    val status: String, // PENDING, PROCESSING, SENT, DELIVERED, FAILED, CANCELLED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = 0,
    val expiresAt: Long = 0,
    val errorMessage: String? = null
)

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // INFO, WARN, ERROR
    val message: String,
    val tag: String
)

// -------------------------------------------------------------------------
// DAOS
// -------------------------------------------------------------------------

@Dao
interface GatewaySettingsDao {
    @Query("SELECT * FROM gateway_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<GatewaySettings?>

    @Query("SELECT * FROM gateway_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): GatewaySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: GatewaySettings)
}

@Dao
interface SyncedSmsDao {
    @Query("SELECT * FROM synced_sms")
    fun getAllSyncedFlow(): Flow<List<SyncedSms>>

    @Query("SELECT * FROM synced_sms")
    suspend fun getAllSynced(): List<SyncedSms>

    @Query("SELECT id FROM synced_sms")
    suspend fun getAllSyncedIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: SyncedSms)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SyncedSms>)

    @Query("DELETE FROM synced_sms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM synced_sms WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones ORDER BY deletedAt DESC")
    fun getAllTombstonesFlow(): Flow<List<Tombstone>>

    @Query("SELECT * FROM tombstones")
    suspend fun getAllTombstones(): List<Tombstone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: Tombstone)

    @Query("SELECT COUNT(*) FROM tombstones WHERE address = :address AND date = :date AND fingerprint = :fingerprint")
    suspend fun isTombstoned(address: String, date: Long, fingerprint: String): Int
}

@Dao
interface SmsQueueDao {
    @Query("SELECT * FROM sms_queue ORDER BY createdAt DESC")
    fun getAllQueueFlow(): Flow<List<SmsQueueItem>>

    @Query("SELECT * FROM sms_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getQueueByStatus(status: String): List<SmsQueueItem>

    @Query("SELECT * FROM sms_queue WHERE status = 'PENDING' AND (scheduledAt == 0 OR scheduledAt <= :now) ORDER BY createdAt ASC")
    suspend fun getPendingToProcess(now: Long = System.currentTimeMillis()): List<SmsQueueItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SmsQueueItem): Long

    @Update
    suspend fun update(item: SmsQueueItem)

    @Query("UPDATE sms_queue SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, error: String?)

    @Query("SELECT * FROM sms_queue WHERE requestId = :requestId LIMIT 1")
    suspend fun getByRequestId(requestId: String): SmsQueueItem?

    @Query("DELETE FROM sms_queue WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM sms_queue WHERE status IN ('SENT', 'DELIVERED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT 300")
    fun getLogsFlow(): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(log: LogEntry)

    @Query("DELETE FROM log_entries")
    suspend fun clearLogs()
}

// -------------------------------------------------------------------------
// DATABASE
// -------------------------------------------------------------------------

@Database(
    entities = [
        GatewaySettings::class,
        SyncedSms::class,
        Tombstone::class,
        SmsQueueItem::class,
        LogEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): GatewaySettingsDao
    abstract fun syncedSmsDao(): SyncedSmsDao
    abstract fun tombstoneDao(): TombstoneDao
    abstract fun smsQueueDao(): SmsQueueDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_center_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
