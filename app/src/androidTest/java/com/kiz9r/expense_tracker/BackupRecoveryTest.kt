package com.kiz9r.expense_tracker

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BackupRecoveryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LedgerDatabase
    private lateinit var service: BackupService
    private var onAccountDelete: (() -> Unit)?=null
    @Before fun setup(){ db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
        .setQueryCallback({ sql,_ -> if(sql=="DELETE FROM accounts")onAccountDelete?.invoke() },java.util.concurrent.Executor {it.run()}).build();service=BackupService(context,db,Gson()) }
    @After fun close(){db.close()}
    @Test fun everyRecordSurvivesEncryptedExportInspectAndReplacement()=runBlocking {
        val fixture=completeBackupFixture();validateBackup(fixture);service.restore(fixture)
        val file=File(context.cacheDir,"synthetic-all-records.etbackup")
        try {
            val password="synthetic backup password".toCharArray()
            service.export(Uri.fromFile(file),password);assertTrue(password.all {it=='\u0000'})
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Synthetic savings"))
            val loaded=service.inspect(Uri.fromFile(file),"synthetic backup password".toCharArray())
            db.ledger().saveAccount(AccountEntity(nickname="Discard on replacement",last4="1111"))
            db.ledger().saveJob(ImportJobEntity(accountId="a1",fileHash="draft",fileName="synthetic.pdf",text="synthetic"))
            service.restore(loaded)
            assertEquals(backupDigest(fixture),backupDigest(service.snapshot()))
            assertEquals("false",db.ledger().setting("sms"));assertEquals("false",db.ledger().setting("notifications"))
            assertTrue(db.ledger().jobs().first().isEmpty())
        } finally {file.delete()}
    }
    @Test fun wrongPasswordTamperingAndMalformedArchivesLeaveExistingLedgerIntact()=runBlocking {
        service.restore(completeBackupFixture());val before=backupDigest(service.snapshot())
        val file=File(context.cacheDir,"synthetic-bad-backup.etbackup")
        try {
            service.export(Uri.fromFile(file),"synthetic backup password".toCharArray())
            val wrong="wrong password".toCharArray()
            assertTrue(runCatching {service.inspect(Uri.fromFile(file),wrong)}.isFailure);assertTrue(wrong.all {it=='\u0000'})
            val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
            assertTrue(runCatching {service.inspect(Uri.fromFile(file),"synthetic backup password".toCharArray())}.isFailure)
            file.writeBytes(BackupCrypto.encrypt("{}".toByteArray(),"synthetic backup password".toCharArray()))
            assertTrue(runCatching {service.inspect(Uri.fromFile(file),"synthetic backup password".toCharArray())}.isFailure)
            assertEquals(before,backupDigest(service.snapshot()))
        } finally {file.delete()}
    }
    @Test fun failedExportClearsPasswordAndDoesNotChangeLedger()=runBlocking {
        service.restore(completeBackupFixture());val before=backupDigest(service.snapshot())
        val password="synthetic backup password".toCharArray()
        assertTrue(runCatching {service.export(Uri.fromFile(context.cacheDir),password)}.isFailure)
        assertTrue(password.all {it=='\u0000'})
        assertEquals(before,backupDigest(service.snapshot()))
    }
    @Test fun databaseWriteFailureRollsBackDeletionAndRetainsDrafts()=runBlocking {
        service.restore(completeBackupFixture());val before=backupDigest(service.snapshot())
        val job=ImportJobEntity(accountId="a1",fileHash="draft",fileName="synthetic.pdf",text="synthetic")
        db.ledger().saveJob(job)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_backup_insert BEFORE INSERT ON accounts BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        assertTrue(runCatching {service.restore(completeBackupFixture())}.isFailure)
        assertEquals(before,backupDigest(service.snapshot()));assertNotNull(db.ledger().job(job.id))
    }
    @Test fun cancellationAfterDeletionRollsBackTheWholeRestore()=runBlocking {
        service.restore(completeBackupFixture());val before=backupDigest(service.snapshot())
        val restore=launch(Dispatchers.IO,start=CoroutineStart.LAZY) {service.restore(completeBackupFixture())}
        var reached=false
        onAccountDelete={reached=true;restore.cancel()}
        restore.start();restore.join();onAccountDelete=null
        assertTrue(reached);assertTrue(restore.isCancelled)
        assertEquals(before,backupDigest(service.snapshot()))
    }
    @Test fun cancelledRestoreWaitingOnWriterChangesNothing()=runBlocking {
        service.restore(completeBackupFixture());val before=backupDigest(service.snapshot())
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO){db.withTransaction {entered.complete(Unit);release.await()}}
        entered.await()
        val restore=launch(Dispatchers.IO){service.restore(completeBackupFixture().copy(accounts=completeBackupFixture().accounts.map {it.copy(nickname="replacement")}))}
        delay(100);restore.cancel();release.complete(Unit);writer.join();restore.join()
        assertEquals(before,backupDigest(service.snapshot()))
    }
    @Test fun inconsistentFinancialRecordsAndMissingFieldsAreRejectedBeforeReplacement()=runBlocking {
        val good=completeBackupFixture();service.restore(good)
        val before=backupDigest(service.snapshot())
        val invalid=listOf(
            good.copy(evidence=good.evidence.map {it.copy(source=com.kiz9r.expense_tracker.domain.Source.MANUAL)}),
            good.copy(transactions=good.transactions.map {if(it.id=="purchase")it.copy(accountId="a2") else it}),
            good.copy(statementImports=good.statementImports.map {it.copy(closingBalance=1)}),
            good.copy(mandates=good.mandates.map {it.copy(status="UNRECOGNIZED")}),
            good.copy(merchantRules=good.merchantRules.map {it.copy(matchType="unsupported")}),
            good.copy(reviewDecisions=good.reviewDecisions.map {it.copy(observationKey="missing")}),
            good.copy(reviewDecisions=good.reviewDecisions.map {if(it.transactionId!=null)it.copy(transactionId="manual") else it}),
            good.copy(settings=listOf(SettingEntity("password","must never be restored"))),
            good.copy(refundLinks=good.refundLinks.map {it.copy(amountMinor=999999)}))
        invalid.forEach {assertTrue(runCatching {service.restore(it)}.isFailure)}
        val json=Gson().toJsonTree(good).asJsonObject
        json.getAsJsonArray("transactions")[0].asJsonObject.remove("amountMinor")
        assertTrue(runCatching {BackupArchive.decode(json.toString().toByteArray(),Gson())}.isFailure)
        assertEquals(before,backupDigest(service.snapshot()))
        assertEquals(backupDigest(good.copy(version=1)),backupDigest(BackupArchive.decode(Gson().toJson(good.copy(version=1)).toByteArray(),Gson())))
    }
    @Test fun notificationIntakeRechecksRestoredOptInInsideItsWriteTransaction()=runBlocking {
        service.restore(completeBackupFixture())
        val ledger=LedgerRepository(db,Gson())
        val rec=com.kiz9r.expense_tracker.reconciliation.ReconciliationRepository(ledger,Gson())
        val obs=com.kiz9r.expense_tracker.ingestion.PhonePeParser().parse("Rs 25 paid to SAMPLE via UPI A/c XX4821 Ref 600000009998","synthetic-notification",1_789_387_200_000L)!!
        assertFalse(rec.enqueueNotification(obs))
        ledger.setting("notifications","true")
        coroutineScope {
            launch {rec.enqueueNotification(obs)}
            launch {service.restore(completeBackupFixture())}
        }
        assertFalse(rec.enqueueNotification(obs))
        assertEquals(backupDigest(completeBackupFixture()),backupDigest(service.snapshot()))
    }
    @Test fun concurrentRestoreAndIngestionNeverResurrectOldAccountRecords()=runBlocking {
        service.restore(completeBackupFixture())
        val ledger=LedgerRepository(db,Gson())
        ledger.setting("sms","true")
        val parts=listOf(com.kiz9r.expense_tracker.ingestion.SmsPart("AD-SBIINB","Rs 25 debited from A/c XX4821 UPI Ref 600000009999",1_789_387_200_000L))
        coroutineScope {
            launch {com.kiz9r.expense_tracker.ingestion.SmsIntake(ledger).accept(parts,1_789_387_200_000L,true,true)}
            launch {service.restore(completeBackupFixture())}
        }
        assertEquals(backupDigest(completeBackupFixture()),backupDigest(service.snapshot()))
    }
}
