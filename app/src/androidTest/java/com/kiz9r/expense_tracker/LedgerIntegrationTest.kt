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
    @Test fun deleteAccountErasesOwnedRecordsAndPreservesCollidingAccount()=runBlocking {
        ledger.addAccount("Same suffix","4821","Savings")
        val other=db.ledger().allAccounts().single { it.id!=account.id }
        suspend fun manual(id: String)=ledger.saveManual(ManualInput(accountId=id,amount="450",direction=Direction.DEBIT,
            date="2026-09-09",time="14:30",merchant="Synthetic",categoryId=null,notes="Private note",tags="Shared"))
        val first=manual(account.id)
        val keep=manual(other.id)
        ledger.hide(first,true)
        val statementPreview=reconciliation.preview(account.id,"synthetic.pdf","delete-file",statement())
        reconciliation.commit(statementPreview,mapOf(0 to Resolution("match",first)))
        val ignored=statement().copy(start="2026-08-01")
        val ignoredId=reconciliation.commit(reconciliation.preview(account.id,"ignored.pdf","ignored-file",ignored),mapOf(0 to Resolution("ignore")))
        val ignoredImport=db.ledger().statementImport(ignoredId)!!
        db.ledger().saveDecision(ReviewDecisionEntity(observationKey="statement:"+ignoredImport.logicalFingerprint+":0:revision:test",action="ignore",transactionId=null))
        val credit=TransactionEntity(accountId=account.id,amountMinor=100,direction=Direction.CREDIT,date="2026-09-10",merchantOriginal="Refund",kind=EventKind.REFUND)
        db.ledger().saveTransaction(credit)
        db.ledger().saveRefund(RefundLinkEntity(first,credit.id,100))
        val mandateEvent=ledger.raw(obs(identity="mandate"))
        db.ledger().insertEvent(mandateEvent)
        db.ledger().saveMandate(MandateEntity(accountId=account.id,merchant="Synthetic",amountMinor=100,reference="M",status="ACTIVE",eventId=mandateEvent.id))
        db.ledger().saveDecision(ReviewDecisionEntity(observationKey=mandateEvent.identity,action="new",transactionId=null))
        val unknown=ledger.raw(obs(identity="unassigned")).copy(processed=true,reviewReason="Choose account")
        db.ledger().insertEvent(unknown)
        val job=ImportJobEntity(accountId=account.id,fileHash="pending",fileName="pending.pdf",text="Synthetic")
        db.ledger().saveJob(job)
        val otherJob=job.copy(id=newId(),accountId=other.id)
        db.ledger().saveJob(otherJob)
        ledger.rule(MerchantRuleEntity(matchValue="Synthetic"))
        ledger.setting("sms","true")
        val categories=db.backup().categories()
        ledger.deleteAccount(account.id)
        assertEquals(listOf(other),db.ledger().allAccounts())
        assertEquals(listOf(keep),db.backup().transactions().map { it.id })
        assertEquals(listOf(keep),db.backup().metadata().map { it.transactionId })
        assertEquals(listOf(keep),db.backup().transactionTags().map { it.transactionId })
        assertEquals(listOf(keep),db.backup().evidence().map { it.transactionId })
        assertEquals(setOf("manual:"+keep,"unassigned"),db.backup().events().map { it.identity }.toSet())
        assertTrue(db.backup().statementImports().isEmpty());assertTrue(db.backup().statementRows().isEmpty())
        assertTrue(db.backup().reviewDecisions().isEmpty());assertTrue(db.backup().mandates().isEmpty())
        assertTrue(db.backup().refundLinks().isEmpty())
        assertEquals(listOf(otherJob),db.ledger().jobs().first())
        assertEquals(categories,db.backup().categories());assertEquals(1,db.backup().tags().size)
        assertEquals(1,db.backup().merchantRules().size);assertEquals("true",db.ledger().setting("sms"))
        val jobs=StatementJobs(ApplicationProvider.getApplicationContext(),db,Gson())
        jobs.parse(job.id)
        assertNull(db.ledger().job(job.id))
        assertTrue(runCatching { jobs.enqueue(account.id,"late","text") }.isFailure)
        assertTrue(runCatching { reconciliation.commit(statementPreview,emptyMap()) }.isFailure)
        ledger.deleteAccount(other.id)
        assertTrue(db.ledger().allAccounts().isEmpty())
        assertTrue(db.backup().transactions().isEmpty())
        assertEquals(listOf(unknown),db.backup().events())
    }
    @Test fun accountDeletionRollsBackIfFinalDeleteFails()=runBlocking {
        reconciliation.commit(reconciliation.preview(account.id,"synthetic.pdf","rollback",statement()),emptyMap())
        val transactions=db.backup().transactions();val events=db.backup().events()
        val imports=db.backup().statementImports();val decisions=db.backup().reviewDecisions()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_account_delete BEFORE DELETE ON accounts BEGIN SELECT RAISE(ABORT, 'Synthetic failure'); END")
        assertTrue(runCatching { ledger.deleteAccount(account.id) }.isFailure)
        assertEquals(transactions,db.backup().transactions());assertEquals(events,db.backup().events())
        assertEquals(imports,db.backup().statementImports());assertEquals(decisions,db.backup().reviewDecisions())
        assertEquals(1,db.backup().evidence().size);assertEquals(1,db.backup().statementRows().size)
        assertEquals(listOf(account),db.ledger().allAccounts())
        assertTrue(runCatching { ledger.deleteAccount("missing") }.isFailure)
    }
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
