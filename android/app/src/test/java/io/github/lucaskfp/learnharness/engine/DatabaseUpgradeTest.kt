package io.github.lucaskfp.learnharness.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A device that saved Sessions with the first schema keeps them after the app updates. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersionOneDatabaseUpgradesWithItsSessionsAndTakesSteersAndDrafts() {
        val file = context.getDatabasePath("upgrade-test.db").also { it.parentFile?.mkdirs(); it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // The first schema, as Room generated it for version 1.
            db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `teachingLanguage` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `json` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_sessionId_messageId` ON `messages` (`sessionId`, `messageId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("INSERT INTO sessions VALUES ('chat_1', 'Derivatives', 'Teaching', 'anthropic', 'claude-sonnet-5-5', 'English', 1, 1)")
            db.execSQL("""INSERT INTO messages (sessionId, messageId, json) VALUES ('chat_1', 'msg_1', '{"role":"user","id":"msg_1","content":"I want to understand derivatives","timestamp":1}')""")
            db.version = 1
        }

        val database = Room.databaseBuilder(context, LearnHarnessDatabase::class.java, file.absolutePath)
            .addMigrations(LearnHarnessDatabase.Migration1To2)
            .build()
        val harness = EngineHarness(database = database)
        try {
            assertEquals(
                listOf<TranscriptEntry>(TranscriptEntry.Learner("I want to understand derivatives")),
                harness.eventually("chat_1") { true }.transcript,
            )
            runBlocking { harness.engine.saveComposerDraft("chat_1", "Still here") }
            assertEquals("Still here", runBlocking { harness.engine.drafts("chat_1") }.composer)
        } finally {
            harness.close()
            file.delete()
        }
    }
}
