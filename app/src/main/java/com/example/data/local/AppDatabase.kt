package com.example.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

enum class SmsDirection(val storageValue: String) {
    INCOMING("INCOMING"),
    OUTGOING("OUTGOING"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromTelephonyType(type: Int): SmsDirection = when (type) {
            1 -> INCOMING
            2 -> OUTGOING
            else -> UNKNOWN
        }
    }
}

// -------------------------------------------------------------------------
// ENTITIES
// -------------------------------------------------------------------------

@Entity(tableName = "gateway_settings")
data class GatewaySettings(
    @PrimaryKey val id: Int = 1,
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceId: String = "android_gateway",
    val simSlotSelection: Int = -1,
    val syncIntervalSeconds: Int = 30,
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val isTestMode: Boolean = true,
    val limitSmsPerDay: Int = 500,
    val limitSmsPerHour: Int = 100,
    val limitSmsPerMin: Int = 5,
    val workingHoursStart: String = "00:00",
    val workingHoursEnd: String = "23:59",
    val isGatewayEnabled: Boolean = false,
    val autostartEnabled: Boolean = false,
    val callLogSyncEnabled: Boolean = false,
    val phoneServerPort: Int = 8080,
    val phoneServerApiKey: String = "",
    val phoneServerAllowedOrigin: String = "",
    val phoneServerLanOnly: Boolean = true
)

@Entity(tableName = "synced_sms")
data class SyncedSms(
    @PrimaryKey val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val simSlot: Int,
    val direction: String = SmsDirection.UNKNOWN.storageValue,
    val syncedAt: Long = System.currentTimeMillis()
) {
    fun getFingerprint(): String = sha256("$address\u0000$date\u0000$body")

    /** Allows old tombstones written with the pre-MVP hash to remain effective. */
    fun getLegacyFingerprint(): String = "${address}_${date}_${body.hashCode()}"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

@Entity(tableName = "tombstones", primaryKeys = ["address", "date", "fingerprint"])
data class Tombstone(
    val address: String,
    val date: Long,
    val fingerprint: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sms_queue",
    indices = [Index(value = ["requestDedupeKey"], unique = true)]
)
data class SmsQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requestId: String,
    val phoneNumber: String,
    val messageBody: String,
    val simSlot: Int,
    val status: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = 0,
    val expiresAt: Long = 0,
    val errorMessage: String? = null,
    val requestDedupeKey: String? = null
)

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String,
    val message: String,
    val tag: String
)

@Entity(tableName = "call_log_upload_queue", primaryKeys = ["deviceId", "callId"])
data class CallLogUploadItem(
    val deviceId: String,
    val callId: String,
    val number: String,
    val contactName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val type: String,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
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
    @Query("SELECT * FROM synced_sms ORDER BY date DESC, id DESC")
    fun getAllSyncedFlow(): Flow<List<SyncedSms>>

    @Query("SELECT * FROM synced_sms ORDER BY date DESC, id DESC")
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

    @Transaction
    suspend fun getOrInsert(item: SmsQueueItem): SmsQueueItem {
        val normalized = item.copy(requestDedupeKey = item.requestId)
        return getByRequestId(normalized.requestId) ?: run {
            insert(normalized)
            getByRequestId(normalized.requestId) ?: normalized
        }
    }

    @Update
    suspend fun update(item: SmsQueueItem)

    @Query("UPDATE sms_queue SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, error: String?)

    @Query("SELECT * FROM sms_queue WHERE requestId = :requestId LIMIT 1")
    suspend fun getByRequestId(requestId: String): SmsQueueItem?

    @Query("UPDATE sms_queue SET status = 'PROCESSING', errorMessage = NULL WHERE id = :id AND status = 'PENDING'")
    suspend fun claimPending(id: Int): Int

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

@Dao
interface CallLogUploadDao {
    @Query("SELECT * FROM call_log_upload_queue WHERE deviceId = :deviceId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(deviceId: String, limit: Int = 500): List<CallLogUploadItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CallLogUploadItem>)

    @Query("DELETE FROM call_log_upload_queue WHERE deviceId = :deviceId AND callId IN (:callIds)")
    suspend fun deleteUploaded(deviceId: String, callIds: List<String>)

    @Query("UPDATE call_log_upload_queue SET attemptCount = attemptCount + 1, lastError = :error WHERE deviceId = :deviceId AND callId IN (:callIds)")
    suspend fun recordFailure(deviceId: String, callIds: List<String>, error: String)

    @Query("SELECT COUNT(*) FROM call_log_upload_queue WHERE deviceId = :deviceId")
    fun pendingCountFlow(deviceId: String): Flow<Int>
}

// -------------------------------------------------------------------------
// DATABASE
// -------------------------------------------------------------------------

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN autostartEnabled INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN callLogSyncEnabled INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE synced_sms ADD COLUMN direction TEXT NOT NULL DEFAULT 'UNKNOWN'")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS call_log_upload_queue (
                deviceId TEXT NOT NULL,
                callId TEXT NOT NULL,
                number TEXT NOT NULL,
                contactName TEXT,
                timestamp INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                type TEXT NOT NULL,
                attemptCount INTEGER NOT NULL DEFAULT 0,
                lastError TEXT,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(deviceId, callId)
            )""".trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_call_log_upload_queue_deviceId_timestamp ON call_log_upload_queue(deviceId, timestamp)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN phoneServerPort INTEGER NOT NULL DEFAULT 8080")
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN phoneServerApiKey TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sms_queue ADD COLUMN requestDedupeKey TEXT")
        database.execSQL(
            "UPDATE sms_queue SET requestDedupeKey = requestId " +
                "WHERE requestId IN (SELECT requestId FROM sms_queue GROUP BY requestId HAVING COUNT(*) = 1)"
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_queue_requestDedupeKey ON sms_queue(requestDedupeKey)")
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN phoneServerAllowedOrigin TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE gateway_settings ADD COLUMN phoneServerLanOnly INTEGER NOT NULL DEFAULT 1")
    }
}

@Database(
    entities = [
        GatewaySettings::class,
        SyncedSms::class,
        Tombstone::class,
        SmsQueueItem::class,
        LogEntry::class,
        CallLogUploadItem::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): GatewaySettingsDao
    abstract fun syncedSmsDao(): SyncedSmsDao
    abstract fun tombstoneDao(): TombstoneDao
    abstract fun smsQueueDao(): SmsQueueDao
    abstract fun logDao(): LogDao
    abstract fun callLogUploadDao(): CallLogUploadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_center_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
