package com.kiz9r.expense_tracker

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.*
import com.kiz9r.expense_tracker.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.time.YearMonth

class LedgerIntegrationTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var reconciliation: ReconciliationRepository
    private lateinit var account: AccountEntity
    @Before fun setup() = runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase::class.java).build()
        ledger=LedgerRepository(db,Gson());reconciliation=ReconciliationRepository(ledger,Gson())
        ledger.initialize();ledger.addAccount("Primary","4821","Savings")
        account=db.ledger().allAccounts().single()
    }
    @After fun close() {db.close()}
    private fun obs(reference: String="625189208332",identity: String="sms",source: Source=Source.SBI_SMS,amount: Long=45000,
        direction: Direction=Direction.DEBIT,kind: EventKind=EventKind.DEBIT) =
        Observation(source,identity,1_788_940_800_000,date="2026-09-09",accountLast4="4821",amountMinor=amount,
            direction=direction,merchant="Swiggy",reference=reference,channel=Channel.UPI,kind=kind,content="Synthetic financial event")
    private fun statement(reference: String="625189208332",amount: Long=45000)=ParsedStatement("4821","2026-09-01","2026-09-30",
        100000,100000-amount,listOf(ParsedRow(0,"2026-09-09","2026-09-09","UPI/$reference/SWIGGY",reference,amount,Direction.DEBIT,100000-amount)),emptyList())
    @Test fun smsNotificationStatementProduceOneVerifiedTransaction()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.enqueue(obs())
        reconciliation.enqueue(obs(identity="notification",source=Source.PHONEPE_NOTIFICATION))
        reconciliation.processPending()
        assertEquals(1,db.backup().transactions().size)
        val preview=reconciliation.preview(account.id,"test.pdf","hash-one",statement())
        reconciliation.commit(preview,emptyMap())
        val tx=db.backup().transactions().single()
        assertEquals(Verification.VERIFIED,tx.verification)
        assertEquals(3,db.backup().evidence().size)
    }
    @Test fun exactAndLogicalDuplicateImportsCreateNothing()=runBlocking {
        reconciliation.commit(reconciliation.preview(account.id,"one.pdf","first",statement()),emptyMap())
        assertTrue(reconciliation.preview(account.id,"one.pdf","first",statement()).duplicate)
        assertTrue(reconciliation.preview(account.id,"new-metadata.pdf","different",statement()).duplicate)
        assertEquals(1,db.backup().transactions().size)
    }
    @Test fun overlappingStatementUsesExistingEvidenceAndKeepsNewRows()=runBlocking {
        reconciliation.commit(reconciliation.preview(account.id,"one.pdf","one",statement()),emptyMap())
        val prior=statement()
        val larger=prior.copy(closing=50000,rows=prior.rows+ParsedRow(1,"2026-09-10",null,"UPI/625189208333/UBER","625189208333",5000,Direction.DEBIT,50000))
        reconciliation.commit(reconciliation.preview(account.id,"two.pdf","two",larger),emptyMap())
        assertEquals(2,db.backup().transactions().size)
        assertEquals(3,db.backup().statementRows().size)
    }
    @Test fun manualMetadataSurvivesVerificationAndCannotBeDeleted()=runBlocking {
        val id=ledger.saveManual(ManualInput(accountId=account.id,amount="450",direction=Direction.DEBIT,date="2026-09-09",
            time="14:30",merchant="My lunch",categoryId=null,notes="Keep me",tags="Work"))
        val preview=reconciliation.preview(account.id,"test.pdf","one",statement())
        reconciliation.commit(preview,mapOf(0 to Resolution("match",id)))
        assertEquals("My lunch",db.ledger().metadata(id)!!.merchantDisplay)
        assertEquals("Keep me",db.ledger().metadata(id)!!.notes)
        assertEquals(1,db.backup().transactionTags().size)
        assertTrue(runCatching{ledger.delete(id)}.isFailure)
        ledger.hide(id,true)
        assertEquals(1,db.backup().statementRows().size)
        assertTrue(ledger.history(HistoryFilter()).first().isEmpty())
    }
    @Test fun repeatedPurchasesNeedReviewAndCanRemainDistinct()=runBlocking {
        reconciliation.enqueue(obs(reference="FIRST123"));reconciliation.processPending()
        reconciliation.enqueue(obs(reference="SECOND123",identity="second"));reconciliation.processPending()
        assertEquals(1,db.backup().transactions().size)
        val review=db.ledger().reviews().first().single()
        reconciliation.resolveEvent(review.id,account.id,Resolution("new"))
        assertEquals(2,db.backup().transactions().size)
    }
    @Test fun failedPaymentsAndMandatesDoNotIncreaseSpending()=runBlocking {
        reconciliation.enqueue(obs(kind=EventKind.FAILED))
        reconciliation.enqueue(obs(identity="mandate",reference="MANDATE1",kind=EventKind.MANDATE_CREATED))
        reconciliation.processPending()
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().spend)
        assertEquals(1,db.backup().mandates().size)
    }
    @Test fun fullAndPartialRefundsNetExactlyOnce()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.processPending()
        reconciliation.enqueue(obs(identity="refund",direction=Direction.CREDIT,kind=EventKind.REFUND,amount=15000))
        reconciliation.processPending()
        // An opposite-direction same reference is deliberately reviewed before linking.
        val review=db.ledger().reviews().first().single()
        reconciliation.resolveEvent(review.id,account.id,Resolution("new"))
        assertEquals(Outcome.PARTIALLY_REFUNDED,db.backup().transactions().first{it.direction==Direction.DEBIT}.outcome)
        assertEquals(30000L,ledger.totals(YearMonth.of(2026,9),null).first().netSpend)
        reconciliation.enqueue(obs(identity="refund2",direction=Direction.CREDIT,kind=EventKind.REVERSAL,amount=30000))
        reconciliation.processPending()
        reconciliation.resolveEvent(db.ledger().reviews().first().single().id,account.id,Resolution("new"))
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().netSpend)
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().income)
    }
    @Test fun multipleMaskedAccountsRequireAssignment()=runBlocking {
        ledger.addAccount("Second same suffix","4821","Savings")
        reconciliation.enqueue(obs());reconciliation.processPending()
        assertTrue(db.backup().transactions().isEmpty())
        assertEquals(1,db.ledger().reviews().first().size)
    }
    @Test fun verifiedStatementCanConfirmPreviouslyFailedPayment()=runBlocking {
        reconciliation.enqueue(obs(kind=EventKind.FAILED));reconciliation.processPending()
        reconciliation.commit(reconciliation.preview(account.id,"confirmed.pdf","confirmed",statement()),emptyMap())
        val tx=db.backup().transactions().single()
        assertEquals(Outcome.POSTED,tx.outcome)
        assertEquals(Verification.VERIFIED,tx.verification)
        assertEquals(45000L,ledger.totals(YearMonth.of(2026,9),null).first().spend)
    }
    @Test fun linkedRefundSurvivesGenericStatementCredit()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.processPending()
        reconciliation.enqueue(obs(identity="refund",direction=Direction.CREDIT,kind=EventKind.REFUND))
        reconciliation.processPending()
        reconciliation.resolveEvent(db.ledger().reviews().first().single().id,account.id,Resolution("new"))
        val credit=db.backup().transactions().single{it.direction==Direction.CREDIT}
        val s=ParsedStatement("4821","2026-09-01","2026-09-30",100000,145000,
            listOf(ParsedRow(0,"2026-09-09",null,"UPI/625189208332/SWIGGY","625189208332",45000,Direction.CREDIT,145000)),emptyList())
        val preview=reconciliation.preview(account.id,"refund.pdf","refund",s)
        reconciliation.commit(preview,mapOf(0 to Resolution("match",credit.id)))
        assertEquals(EventKind.REFUND,db.ledger().transaction(credit.id)!!.kind)
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().netSpend)
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().income)
    }
    @Test fun stalePreviewRollsBackAllChanges()=runBlocking {
        val preview=reconciliation.preview(account.id,"one.pdf","one",statement())
        reconciliation.enqueue(obs());reconciliation.processPending()
        assertTrue(runCatching {reconciliation.commit(preview,emptyMap())}.isFailure)
        assertTrue(db.backup().statementImports().isEmpty())
        assertEquals(1,db.backup().transactions().size)
    }
    @Test fun conflictingRowAbortsWholeImport()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.processPending()
        val tx=db.backup().transactions().single()
        val preview=reconciliation.preview(account.id,"test.pdf","hash",statement(amount=50000))
        assertTrue(runCatching{reconciliation.commit(preview,mapOf(0 to Resolution("match",tx.id)))}.isFailure)
        assertTrue(db.backup().statementImports().isEmpty())
        assertEquals(Verification.PROVISIONAL,db.backup().transactions().single().verification)
    }
    @Test fun restoreSnapshotRecoversEvidenceAndRejectsInvalidData()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.processPending()
        val backup=BackupService(ApplicationProvider.getApplicationContext(),db,Gson())
        val snapshot=backup.snapshot()
        validateBackup(snapshot)
        ledger.addAccount("Temporary","9999","Savings")
        backup.restore(snapshot)
        assertEquals(snapshot.transactions,db.backup().transactions())
        assertEquals(snapshot.evidence,db.backup().evidence())
        val bad=snapshot.copy(accounts=emptyList())
        assertTrue(runCatching{backup.restore(bad)}.isFailure)
        assertEquals(1,db.backup().accounts().size)
    }
    @Test fun concurrentDuplicateIngestionIsIdempotent()=runBlocking {
        coroutineScope {repeat(10) {launch(Dispatchers.IO){reconciliation.enqueue(obs())}}}
        reconciliation.processPending()
        assertEquals(1,db.backup().events().size);assertEquals(1,db.backup().transactions().size)
    }
    @Test fun encryptedFileRestoresIntoAnIndependentFreshDatabase()=runBlocking {
        reconciliation.enqueue(obs());reconciliation.processPending()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=java.io.File(context.cacheDir,"portable-backup-test.etbackup")
        val fresh=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        try {
            val source=BackupService(context,db,Gson())
            source.export(android.net.Uri.fromFile(file),"portable long password".toCharArray())
            val target=BackupService(context,fresh,Gson())
            val snapshot=target.inspect(android.net.Uri.fromFile(file),"portable long password".toCharArray())
            target.restore(snapshot)
            assertEquals(db.backup().transactions(),fresh.backup().transactions())
            assertEquals(db.backup().evidence(),fresh.backup().evidence())
            assertTrue(runCatching{target.inspect(android.net.Uri.fromFile(file),"wrong".toCharArray())}.isFailure)
            assertEquals(1,fresh.backup().transactions().size)
        } finally {fresh.close();file.delete()}
    }
}
