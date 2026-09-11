package com.kiz9r.expense_tracker

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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

class StatementReconciliationTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var rec: ReconciliationRepository
    private lateinit var account: AccountEntity
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        ledger=LedgerRepository(db,Gson());rec=ReconciliationRepository(ledger,Gson())
        ledger.initialize();ledger.addAccount("Test SBI","4821","Savings");account=db.ledger().allAccounts().single()
    }
    @After fun close() {db.close()}
    private fun statement()=ParsedStatement("4821","2026-07-01","2026-07-31",100000,0,
        listOf(ParsedRow(0,"2026-07-02",null,"UPI/DR/700000000001/SHOP","700000000001",50000,Direction.DEBIT,50000),
            ParsedRow(1,"2026-07-02",null,"UPI/DR/700000000002/SHOP","700000000002",50000,Direction.DEBIT,0)),emptyList())
    private suspend fun preview(s: ParsedStatement=statement(),hash: String="hash")=rec.preview(account.id,"Synthetic.pdf",hash,s)

    @Test fun statementOnlyRowsAreVerifiedAndSameValuePurchasesRemainDistinct()=runBlocking {
        val id=rec.commit(preview(),emptyMap())
        val records=db.backup().transactions()
        assertEquals(2,records.size);assertTrue(records.all {it.verification==Verification.VERIFIED})
        assertEquals(2,db.backup().evidence().size)
        assertTrue(db.backup().evidence().all {it.source==Source.SBI_STATEMENT && it.verified})
        assertEquals(100000L,rec.report(id).summary.linkedDebit)
        assertEquals(2,rec.report(id).summary.added)
        assertTrue(rec.preview(account.id,"renamed.pdf","different-bytes",statement()).duplicate)
    }
    @Test fun ignoredRowResolutionPreservesDecisionHistoryAndDoesNotRewriteBalances()=runBlocking {
        val id=rec.commit(preview(),mapOf(1 to Resolution("ignore")))
        val before=rec.report(id)
        assertEquals("EXCEPTIONS",before.imported.status)
        assertEquals(100000L,before.summary.statementDebit);assertEquals(50000L,before.summary.linkedDebit)
        val row=before.rows.last()
        rec.resolveIgnoredRow(row.id,Resolution("new"))
        val after=rec.report(id)
        assertEquals("RECONCILED",after.imported.status);assertEquals(100000L,after.summary.linkedDebit)
        assertEquals(0,after.summary.ignored)
        assertEquals(1,after.decisions.count {it.action=="ignore"});assertEquals(3,after.decisions.size)
        assertEquals(row.balance,after.rows.last().balance)
        assertTrue(runCatching {rec.resolveIgnoredRow(row.id,Resolution("new"))}.isFailure)
        assertEquals(2,db.backup().transactions().size)
    }
    @Test fun ignoredRowCannotReuseAnotherRowsTransaction()=runBlocking {
        val s=statement().let {it.copy(rows=it.rows.map {r->r.copy(reference="")})}
        val id=rec.commit(preview(s),mapOf(1 to Resolution("ignore")))
        val rows=db.ledger().importRows(id)
        assertTrue(runCatching {rec.resolveIgnoredRow(rows.last().id,Resolution("match",rows.first().transactionId))}.isFailure)
        assertEquals("EXCEPTIONS",rec.report(id).imported.status);assertEquals(1,db.backup().transactions().size)
    }
    @Test fun balanceWarningsSurviveReloadHidingAndBackup()=runBlocking {
        val id=rec.commit(preview(statement().copy(closing=100)),emptyMap())
        val first=rec.report(id)
        assertEquals("EXCEPTIONS",first.imported.status);assertTrue(first.summary.warnings.isNotEmpty())
        db.backup().transactions().forEach {ledger.hide(it.id,true)}
        val another=ReconciliationRepository(ledger,Gson()).report(id)
        assertEquals(first.summary.statementDebit,another.summary.statementDebit)
        assertEquals(first.summary.linkedDebit,another.summary.linkedDebit)
        val service=BackupService(context,db,Gson())
        val snapshot=service.snapshot()
        service.restore(Gson().fromJson(Gson().toJson(snapshot),BackupSnapshot::class.java))
        assertEquals(first.summary.warnings,rec.report(id).summary.warnings)
        assertEquals("EXCEPTIONS",rec.report(id).imported.status)
    }
    @Test fun repeatedIdenticalRowsRetainMultiplicityAndOneToOneAssignments()=runBlocking {
        val row=statement().rows.first().copy(reference="")
        val s=statement().copy(closing=0,rows=listOf(row,row.copy(sequence=1)))
        val id=rec.commit(preview(s),emptyMap())
        assertEquals(2,db.backup().transactions().size)
        assertEquals(2,db.ledger().importRows(id).mapNotNull {it.transactionId}.distinct().size)
        assertEquals("EXCEPTIONS",rec.report(id).imported.status)
        val p=preview(s.copy(end="2026-08-01"),"overlap")
        val targets=db.backup().transactions().map {it.id}
        assertTrue(runCatching {rec.commit(p,mapOf(0 to Resolution("match",targets[0]),1 to Resolution("match",targets[0])))}.isFailure)
        rec.commit(p,mapOf(0 to Resolution("match",targets[0]),1 to Resolution("match",targets[1])))
        assertEquals(2,db.backup().transactions().size);assertEquals(4,db.backup().statementRows().size)
    }
    @Test fun previouslyIgnoredOverlapRequiresExplicitReview()=runBlocking {
        rec.commit(preview(),mapOf(1 to Resolution("ignore")))
        val p=preview(statement().copy(end="2026-08-01"),"overlap")
        assertTrue(p.rows.last().decision is MatchDecision.Review)
        assertTrue(runCatching {rec.commit(p,emptyMap())}.isFailure)
        rec.commit(p,mapOf(1 to Resolution("new")))
        assertEquals(2,db.backup().transactions().size)
    }
    @Test fun draftsSurviveServiceRecreationAndStaleDraftsAreCleared()=runBlocking {
        val p=preview()
        val job=ImportJobEntity(accountId=account.id,fileHash="hash",fileName="chosen.pdf",text="",
            status="READY",resultJson=Gson().toJson(statement()))
        db.ledger().saveJob(job)
        val jobs=StatementJobs(context,db,Gson())
        jobs.restoreReview(job.id,rec.previewToken(p))
        val choices=mapOf(0 to Resolution("ignore"))
        jobs.saveReview(job.id,rec.previewToken(p),choices)
        assertEquals(choices,StatementJobs(context,db,Gson()).restoreReview(job.id,rec.previewToken(p)).first)
        val changed=jobs.restoreReview(job.id,"changed-token")
        assertTrue(changed.second);assertTrue(changed.first.isEmpty())
    }
    @Test fun commitRemovesStagingAtomicallyAndCancellationPreventsCommit()=runBlocking {
        val p=preview()
        val job=ImportJobEntity(accountId=account.id,fileHash="hash",fileName="chosen.pdf",text="",status="READY")
        db.ledger().saveJob(job)
        assertTrue(runCatching {rec.commit(p,mapOf(0 to Resolution("match","missing")),job.id)}.isFailure)
        assertNotNull(db.ledger().job(job.id));assertTrue(db.backup().statementImports().isEmpty())
        rec.commit(p,emptyMap(),job.id)
        assertNull(db.ledger().job(job.id))
        assertTrue(runCatching {rec.commit(p,emptyMap(),job.id)}.isFailure)
    }
    @Test fun cancelledParsingCannotRecreateDiscardedJob()=runBlocking {
        val job=ImportJobEntity(accountId=account.id,fileHash="cancel",fileName="cancel.pdf",text="unsupported")
        db.ledger().saveJob(job)
        val jobs=StatementJobs(context,db,Gson())
        jobs.discard(job.id);jobs.parse(job.id)
        assertNull(db.ledger().job(job.id));assertTrue(db.backup().transactions().isEmpty())
    }
    @Test fun truncatedPreviewAndUnexpectedDecisionsAreRejectedAtomically()=runBlocking {
        val p=preview()
        assertTrue(runCatching {rec.commit(p.copy(rows=p.rows.take(1)),emptyMap())}.isFailure)
        assertTrue(runCatching {rec.commit(p,mapOf(99 to Resolution("ignore")))}.isFailure)
        assertTrue(db.backup().statementImports().isEmpty())
    }
    @Test fun concurrentIngestionAndImportNeverSilentlyLoseEvidence()=runBlocking {
        val p=preview()
        val obs=statement().rows.first().observation("concurrent","4821").copy(source=Source.SBI_SMS)
        val result=coroutineScope {
            val ingestion=async(Dispatchers.IO) {rec.enqueue(obs);rec.processPending()}
            val commit=async(Dispatchers.IO) {runCatching {rec.commit(p,emptyMap())}}
            ingestion.await();commit.await()
        }
        if(result.isFailure) rec.commit(preview(),emptyMap())
        assertEquals(2,db.backup().transactions().size);assertEquals(3,db.backup().evidence().size)
        assertEquals(1,db.backup().statementImports().size)
    }
    @Test fun statementRefundsAcrossMonthsPreserveBothMovementsAndFinalReversal()=runBlocking {
        val debit=Observation(Source.SBI_SMS,"original",0,date="2026-07-31",accountLast4="4821",amountMinor=50000,
            direction=Direction.DEBIT,merchant="SHOP",reference="RETURN123",kind=EventKind.DEBIT,content="Synthetic debit")
        rec.enqueue(debit);rec.processPending()
        val original=db.backup().transactions().single()
        val rows=listOf(ParsedRow(0,"2026-08-01",null,"REFUND REF RETURN123","RETURN123",10000,Direction.CREDIT,10000),
            ParsedRow(1,"2026-08-02",null,"REVERSAL REF RETURN123","RETURN123",40000,Direction.CREDIT,50000))
        val s=ParsedStatement("4821","2026-08-01","2026-08-31",0,50000,rows,emptyList())
        rec.commit(preview(s),mapOf(0 to Resolution("new"),1 to Resolution("new")))
        assertEquals(Outcome.REVERSED,db.ledger().transaction(original.id)!!.outcome)
        assertEquals(2,db.backup().refundLinks().size)
        assertEquals(3,db.backup().transactions().size)
        val july=ledger.totals(java.time.YearMonth.of(2026,7),account.id).first()
        val august=ledger.totals(java.time.YearMonth.of(2026,8),account.id).first()
        assertEquals(50000L,july.netSpend);assertEquals(-50000L,august.netSpend);assertEquals(0L,august.income)
    }
    @Test fun lateOriginalDebitLinksPreviouslyImportedRefundWithoutDuplicateCredit()=runBlocking {
        val credit=ParsedStatement("4821","2026-08-01","2026-08-31",0,50000,
            listOf(ParsedRow(0,"2026-08-02",null,"REFUND REF LATER123","LATER123",50000,Direction.CREDIT,50000)),emptyList())
        rec.commit(preview(credit),emptyMap())
        val debit=ParsedStatement("4821","2026-07-01","2026-07-31",50000,0,
            listOf(ParsedRow(0,"2026-07-31",null,"REF LATER123 SHOP","LATER123",50000,Direction.DEBIT,0)),emptyList())
        rec.commit(preview(debit,"late-original"),mapOf(0 to Resolution("new")))
        assertEquals(2,db.backup().transactions().size);assertEquals(1,db.backup().refundLinks().size)
        assertEquals(Outcome.REFUNDED,db.backup().transactions().single {it.direction==Direction.DEBIT}.outcome)
    }
    @Test fun manualCategoryAndAllOriginalFactsSurviveVerification()=runBlocking {
        val category=db.ledger().allCategories().first().id
        val id=ledger.saveManual(ManualInput(accountId=account.id,amount="500",direction=Direction.DEBIT,
            date="2026-07-02",time="10:00",merchant="Personal name",categoryId=category,notes="Notes",tags="Work"))
        rec.commit(preview(),mapOf(0 to Resolution("match",id),1 to Resolution("new")))
        assertEquals(category,db.ledger().metadata(id)!!.categoryId)
        assertEquals("Personal name",db.ledger().metadata(id)!!.merchantDisplay)
        assertEquals(statement().rows.first().narration,db.ledger().transaction(id)!!.narration)
        assertEquals("700000000001",db.ledger().transaction(id)!!.reference)
        assertEquals(2,db.backup().transactions().size)
    }
}

/** Opt-in, read-only local sample validation. No real document or password is a fixture. */
class ProvidedStatementValidationTest {
    @Test fun validateSuppliedPdfWithoutPersistingFinancialRecords()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        val path=args.getString("statementPath")
        Assume.assumeTrue("Local sample validation requires explicit instrumentation arguments.",path!=null)
        val password=requireNotNull(args.getString("statementPassword")).toCharArray()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val (bytes,text)=PdfTextExtractor(context).read(Uri.fromFile(java.io.File(requireNotNull(path))),password)
        try {
            val s=SbiPdfStatementParser().parse(text)
            assertEquals(args.getString("expectedRows")!!.toInt(),s.rows.size)
            val memory=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
            try {
                val ledger=LedgerRepository(memory,Gson())
                ledger.initialize();ledger.addAccount("Private sample validation",s.last4,"Savings")
                val account=memory.ledger().allAccounts().single()
                val rec=ReconciliationRepository(ledger,Gson())
                val preview=rec.preview(account.id,"provided.pdf",hash(bytes),s)
                val id=rec.commit(preview,emptyMap())
                assertEquals(s.rows.size,memory.backup().transactions().size)
                assertTrue(memory.backup().transactions().all {it.verification==Verification.VERIFIED})
                assertEquals("RECONCILED",rec.report(id).imported.status)
                assertTrue(rec.preview(account.id,"provided-again.pdf",hash(bytes),s).duplicate)
            } finally {memory.close()}
            assertTrue("All row and closing balances must reconcile.",s.balanceWarnings().isEmpty())
            assertTrue(s.rows.filter {it.narration.startsWith("UPI/")}.all {it.reference.isNotBlank()})
            assertTrue(password.all {it=='\u0000'})
        } finally {bytes.fill(0)}
    }
}
