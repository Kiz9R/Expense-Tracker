package com.kiz9r.expense_tracker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class DeviceKeyRecoveryTest {
    @Test fun missingWrappedKeyPreservesOldFileAndActivatesValidatedEncryptedReplacement()=runBlocking {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val prefix="synthetic-key-recovery-"+newId()+"-"
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext(): Context=this
            override fun getSharedPreferences(name: String,mode: Int)=base.getSharedPreferences(prefix+name,mode)
            override fun getDatabasePath(name: String): File=base.getDatabasePath(prefix+name)
            override fun deleteDatabase(name: String)=base.deleteDatabase(prefix+name)
        }
        val keys=DatabaseKeys(context);val recovery=LedgerRecovery(context,keys,Gson())
        val original=EncryptedLedger.open(context,keys)
        try {BackupService(context,original,Gson()).restore(completeBackupFixture())} finally {original.close()}
        val path=context.getDatabasePath("ledger.db");assertTrue(path.exists())
        val before=hash(path.readBytes())
        context.getSharedPreferences("device_keys",Context.MODE_PRIVATE).edit().remove("wrapped_database_key").commit()
        assertTrue(runCatching {recovery.checkAccessible()}.isFailure)
        assertEquals(before,hash(path.readBytes()))
        assertTrue(runCatching {recovery.restore(completeBackupFixture().copy(accounts=emptyList()))}.isFailure)
        assertEquals("ledger.db",keys.activeDatabaseName())
        recovery.restore(completeBackupFixture())
        assertNotEquals("ledger.db",keys.activeDatabaseName());assertEquals(before,hash(path.readBytes()))
        recovery.checkAccessible()
        val restored=EncryptedLedger.open(context,keys)
        try {assertEquals(backupDigest(completeBackupFixture()),backupDigest(BackupService(context,restored,Gson()).snapshot()))}
        finally {restored.close()}
        assertFalse(context.getDatabasePath(keys.activeDatabaseName()).readBytes().take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("SQLite format"))
        assertTrue(runCatching {recovery.restore(completeBackupFixture())}.isFailure)
        context.deleteDatabase("ledger.db");context.deleteDatabase(keys.activeDatabaseName())
        context.getSharedPreferences("device_keys",Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
}
