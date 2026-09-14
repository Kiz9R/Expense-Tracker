package com.kiz9r.expense_tracker.security

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.newId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject

object EncryptedLedger {
    fun open(context: Context,keys: DatabaseKeys,name: String = keys.activeDatabaseName()): LedgerDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context,LedgerDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(keys.databasePassword(name)))
            .addMigrations(MIGRATION_1_2).build()
    }
}

/** No dependency on the inaccessible database: authenticate first, stage separately, then activate. */
class LedgerRecovery @Inject constructor(@ApplicationContext private val context: Context,
    private val keys: DatabaseKeys, private val gson: Gson) {
    suspend fun checkAccessible() = withContext(Dispatchers.IO) {
        val db=EncryptedLedger.open(context,keys)
        try { db.openHelper.writableDatabase } finally { db.close() }
        Unit
    }
    suspend fun restore(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        validateBackup(snapshot)
        val previous=keys.activeDatabaseName()
        // Recovery must never become an alternate unconfirmed replacement path for a healthy ledger.
        val accessible=runCatching { checkAccessible() }.isSuccess
        currentCoroutineContext().ensureActive()
        check(!accessible) { "The ledger is accessible again. Reopen the app and use normal backup restore." }
        val replacement="ledger-recovery-"+newId()+".db"
        var activated=false
        var activationAttempted=false
        try {
            val db=EncryptedLedger.open(context,keys,replacement)
            try {
                val service=BackupService(context,db,gson)
                service.restore(snapshot)
                validateBackup(service.snapshot())
            } finally { db.close() }
            // Verify the new encrypted file can be reopened before changing the durable pointer.
            val reopened=EncryptedLedger.open(context,keys,replacement)
            try { reopened.openHelper.writableDatabase } finally { reopened.close() }
            currentCoroutineContext().ensureActive()
            activationAttempted=true
            keys.activateDatabase(replacement,previous)
            activated=true
        } finally {
            // Only the unique staging database from this attempt is eligible for cleanup.
            if(!activated && !activationAttempted) context.deleteDatabase(replacement)
        }
    }
}
