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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * A learner's steering message waiting for the running Turn's next safe boundary. Delivery saves
 * it into the history under the same id and deletes this row in one transaction: exactly once.
 */
@Entity(
    tableName = "steers",
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SteerRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val createdAt: Long,
)

/** What the learner has typed but not sent, kept across process death: [key] names the field. */
@Entity(
    tableName = "drafts",
    primaryKeys = ["sessionId", "key"],
    foreignKeys = [
        ForeignKey(
            entity = SessionRow::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DraftRow(
    val sessionId: String,
    /** `composer`, or `answer:` and the question's tool call id. */
    val key: String,
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
interface SteerDao {
    @Query("SELECT * FROM steers WHERE sessionId = :sessionId ORDER BY createdAt, rowid")
    suspend fun all(sessionId: String): List<SteerRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SteerRow)

    @Query("DELETE FROM steers WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE sessionId = :sessionId")
    suspend fun all(sessionId: String): List<DraftRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: DraftRow)

    @Query("DELETE FROM drafts WHERE sessionId = :sessionId AND `key` = :key")
    suspend fun delete(sessionId: String, key: String)
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

@Database(
    entities = [SessionRow::class, MessageRow::class, SteerRow::class, DraftRow::class, SettingRow::class],
    version = 2,
    exportSchema = false,
)
abstract class LearnHarnessDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun steers(): SteerDao
    abstract fun drafts(): DraftDao
    abstract fun settings(): SettingDao

    companion object {
        /** Version 2 adds queued steers and drafts (issue #9). */
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `steers` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_steers_sessionId` ON `steers` (`sessionId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drafts` (`sessionId` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `key`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }
    }
}
