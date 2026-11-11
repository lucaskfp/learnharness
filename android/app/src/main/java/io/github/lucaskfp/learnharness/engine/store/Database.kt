package io.github.lucaskfp.learnharness.engine.store

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A Session's record: what upstream stores as a `ChatRecord`, minus its messages. */
@Entity(tableName = "sessions")
data class SessionRow(
    @PrimaryKey val id: String,
    /** The goal the learner stated, trimmed for display. */
    val title: String,
    /** `teaching` or `practice`. */
    val kind: String,
    val provider: String,
    val model: String,
    val teachingLanguage: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One message of a Session's history, as upstream-shaped JSON. [seq] is the history order. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "messageId"], unique = true)],
)
data class MessageRow(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val sessionId: String,
    val messageId: String,
    val json: String,
)

@Entity(tableName = "settings")
data class SettingRow(@PrimaryKey val key: String, val value: String)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun all(): List<SessionRow>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SessionRow)

    @Query("UPDATE sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY seq")
    suspend fun all(sessionId: String): List<MessageRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: MessageRow)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingRow>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SettingRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<SettingRow>)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Database(entities = [SessionRow::class, MessageRow::class, SettingRow::class], version = 1, exportSchema = false)
abstract class LearnHarnessDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun settings(): SettingDao
}
