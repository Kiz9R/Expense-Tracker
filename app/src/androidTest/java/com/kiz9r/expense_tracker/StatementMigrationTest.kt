package com.kiz9r.expense_tracker

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.kiz9r.expense_tracker.data.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Test
import org.junit.Assert.*

class StatementMigrationTest {
    @Test fun encryptedVersionOneLedgerMigratesWithoutLosingImportsOrJobs()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="statement-migration-test.db"
        context.deleteDatabase(name)
        System.loadLibrary("sqlcipher")
        val key=ByteArray(32) { (it+1).toByte() }
        val schema=InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.kiz9r.expense_tracker.data.LedgerDatabase/1.json").bufferedReader().use { it.readText() }
        val definition=JsonParser.parseString(schema).asJsonObject.getAsJsonObject("database")
        val old=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object: SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    definition.getAsJsonArray("entities").forEach { entity ->
                        val item=entity.asJsonObject
                        val table=item["tableName"].asString
                        db.execSQL(item["createSql"].asString.replace("\${TABLE_NAME}",table))
                        item.getAsJsonArray("indices")?.forEach { index ->
                            db.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table))
                        }
                    }
                    definition.getAsJsonArray("setupQueries").forEach {db.execSQL(it.asString)}
                }
                override fun onUpgrade(db: SupportSQLiteDatabase,oldVersion: Int,newVersion: Int)=Unit
            }).build())
        try {
            old.writableDatabase.execSQL("INSERT INTO accounts VALUES ('account','Migration SBI','4821','Savings','SBI','INR',1,1)")
            old.writableDatabase.execSQL("INSERT INTO statement_imports VALUES ('import','account','old.pdf','file-hash','logical-hash','2026-07-01','2026-07-31',100000,50000,1,'RECONCILED','1.0.0',1)")
            old.writableDatabase.execSQL("INSERT INTO import_jobs VALUES ('job','account','pending-hash','pending.pdf','masked pending text','QUEUED',NULL,NULL,1)")
        } finally {old.close()}
        val migrated=Room.databaseBuilder(context,LedgerDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(key.copyOf())).addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals("Migration SBI",migrated.ledger().allAccounts().single().nickname)
            assertEquals(50000L,migrated.ledger().statementImport("import")!!.closingBalance)
            assertNull(migrated.ledger().statementImport("import")!!.warningsJson)
            assertEquals("masked pending text",migrated.ledger().job("job")!!.text)
            assertNull(migrated.ledger().job("job")!!.resolutionsJson)
            val header=context.getDatabasePath(name).inputStream().use { stream -> ByteArray(16).also {stream.read(it)}}
            assertFalse(header.toString(Charsets.US_ASCII).startsWith("SQLite format"))
        } finally {migrated.close();context.deleteDatabase(name);key.fill(0)}
    }
}
