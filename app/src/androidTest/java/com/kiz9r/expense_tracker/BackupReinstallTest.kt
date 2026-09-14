package com.kiz9r.expense_tracker

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.domain.hash
import com.kiz9r.expense_tracker.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Opt-in two-process proof. The host retains only encrypted synthetic data and comparison hashes. */
class BackupReinstallTest {
    @Test fun portableArchiveSurvivesActualReinstall()=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("backup_reinstall")
        Assume.assumeTrue(phase in listOf("prepare","verify"))
        val context=ApplicationProvider.getApplicationContext<Context>()
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        val keys=DatabaseKeys(context)
        val password=keys.databasePassword()
        val keyDigest=try {hash(password)} finally {password.fill(0)}
        val db=EncryptedLedger.open(context,keys)
        val service=BackupService(context,db,Gson())
        val archive=File(context.filesDir,"synthetic-reinstall.etbackup")
        val digest=File(context.filesDir,"synthetic-reinstall.digest")
        val keyHash=File(context.filesDir,"synthetic-reinstall.keyhash")
        try {
            if(phase=="prepare") {
                service.restore(completeBackupFixture())
                service.export(Uri.fromFile(archive),"synthetic reinstall password".toCharArray())
                digest.writeText(backupDigest(service.snapshot()));keyHash.writeText(keyDigest)
            } else {
                assertTrue(service.snapshot().accounts.isEmpty())
                assertNotEquals(keyHash.readText(),keyDigest)
                val restored=service.inspect(Uri.fromFile(archive),"synthetic reinstall password".toCharArray())
                service.restore(restored)
                assertEquals(digest.readText(),backupDigest(service.snapshot()))
                assertEquals("false",db.ledger().setting("sms"));assertEquals("false",db.ledger().setting("notifications"))
                assertFalse(context.getDatabasePath(keys.activeDatabaseName()).readBytes().take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("SQLite format"))
                archive.delete();digest.delete();keyHash.delete()
            }
        } finally {db.close()}
        Unit
    }
}
