package com.kiz9r.expense_tracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.time.*

class SmsIntegrationTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var rec: ReconciliationRepository
    private lateinit var intake: SmsIntake
    private lateinit var account: AccountEntity
    private val sent=LocalDateTime.of(2026,9,14,14,30).atZone(Dates.zone).toInstant().toEpochMilli()
    private val debit="Rs 500 debited from A/c XX4821 to SHOP on 14-09-2026 UPI Ref 600000000101"
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LedgerDatabase::class.java).build()
        ledger=LedgerRepository(db,Gson());rec=ReconciliationRepository(ledger,Gson());intake=SmsIntake(ledger)
        ledger.initialize();ledger.addAccount("Primary","4821","Savings")
        account=db.ledger().allAccounts().single()
    }
    @After fun close(){db.close()}
    @Test fun observedDebitLayoutMatchesStatementAndKeepsCorrectPostingDate()=runBlocking {
        ledger.setting("sms","true")
        val text="Dear UPI user A/C X4821 debited by 500.00 on date 03Sep26 trf to SAMPLE SHOP Refno 600000000101 If not u? call-1800000000 for other services-18000000-SBI"
        receive(text);receive(text);rec.processPending()
        val tx=db.backup().transactions().single()
        assertEquals("2026-09-03",tx.date);assertEquals("SAMPLE SHOP",tx.merchantOriginal)
        assertEquals(50000L,tx.amountMinor);assertEquals(Channel.UPI,tx.channel)
        val statement=ParsedStatement("4821","2026-09-01","2026-09-30",100000,50000,
            listOf(ParsedRow(0,"2026-09-03",null,"UPI/600000000101/SAMPLE SHOP","600000000101",50000,Direction.DEBIT,50000)),emptyList())
        rec.commit(rec.preview(account.id,"synthetic.pdf","observed-layout",statement),emptyMap())
        assertEquals(tx.id,db.backup().transactions().single().id)
        assertEquals(Verification.VERIFIED,db.backup().transactions().single().verification)
    }
    @Test fun observedMandateUmnKeepsSameValueAuthorizationsSeparate()=runBlocking {
        ledger.setting("sms","true")
        val first="Your UPI-Mandate for Rs.299.00 is successfully created towards SAMPLE STREAM from A/c No: XXXXXX4821. UMN:sample600000001@bank -SBI"
        receive(first);receive(first.replace("600000001","600000002"),sent+1000);rec.processPending()
        receive("Your UPI-Mandate is successfully cancelled towards SAMPLE STREAM for 299.00 from A/c No.XXXXXX4821. UMN:sample600000001@bank -SBI",sent+2000)
        rec.processPending()
        assertEquals(2,db.backup().mandates().size)
        assertEquals("CANCELLED",db.backup().mandates().single {it.reference=="SAMPLE600000001@BANK"}.status)
        assertEquals("ACTIVE",db.backup().mandates().single {it.reference=="SAMPLE600000002@BANK"}.status)
        assertTrue(db.backup().transactions().isEmpty())
    }
    @Test fun cardOnlyAlertRequiresAccountChoiceEvenWhenCardSuffixMatchesAccount()=runBlocking {
        ledger.setting("sms","true")
        val text="Dear Customer, transaction number 123456 for Rs.1500.00 by SBI Debit Card X4821 done at SAMPLE SHOP on 14Sep26 at 14:30:00. Your updated available balance is Rs.100000.00."
        val parts=listOf(SmsPart("JD-ATMSBI-S",text,sent))
        intake.accept(parts,sent+1000,true,true);intake.accept(parts,sent+2000,true,true)
        rec.processPending()
        assertTrue(db.backup().transactions().isEmpty())
        val event=db.ledger().reviews().first().single()
        rec.resolveEvent(event.id,account.id,Resolution("new"))
        val tx=db.backup().transactions().single()
        assertEquals(150000L,tx.amountMinor);assertEquals(Channel.CARD,tx.channel)
        assertEquals(account.id,tx.accountId);assertEquals(Verification.PROVISIONAL,tx.verification)
        assertEquals(1,db.backup().evidence().size)
    }
    @Test fun nachExecutionAndCbsCashCreditUseMovementAmountsOnly()=runBlocking {
        ledger.setting("sms","true")
        val debit="Dear Customer, Your A/C XXXXX124821 has a debit by NACH of Rs 1,500.00 on 14/09/26. Avl Bal Rs 1,00,000.00. Download YONO - SBI"
        val credit="Your A/C XXXXX124821 Credited INR 2,000.00 on 14/09/26 -Deposit of Cash at SAMPLE BRANCH. Avl Bal INR 1,02,000.00-SBI"
        intake.accept(listOf(SmsPart("AX-CBSSBI-S",debit,sent)),sent,true,true)
        intake.accept(listOf(SmsPart("AX-CBSSBI-S",credit,sent+1000)),sent+1000,true,true)
        rec.processPending()
        val txs=db.backup().transactions()
        assertEquals(2,txs.size)
        assertEquals(150000L,txs.single {it.direction==Direction.DEBIT}.amountMinor)
        assertEquals(Channel.NACH,txs.single {it.direction==Direction.DEBIT}.channel)
        assertEquals(200000L,txs.single {it.direction==Direction.CREDIT}.amountMinor)
        assertEquals(Channel.CASH,txs.single {it.direction==Direction.CREDIT}.channel)
        assertTrue(db.backup().mandates().isEmpty())
    }
    private suspend fun receive(text: String=debit,time: Long=sent)=intake.accept(listOf(SmsPart("AD-SBIINB",text,time)),time+1000,true,true)
    @Test fun optedOutDeniedAndLockedGatesStoreNothing()=runBlocking {
        assertFalse(receive())
        ledger.setting("sms","true")
        val parts=listOf(SmsPart("AD-SBIINB",debit,sent))
        assertFalse(intake.accept(parts,sent,false,true))
        assertFalse(intake.accept(parts,sent,true,false))
        assertTrue(db.backup().events().isEmpty())
        assertNull(db.ledger().setting("sms_last_received"))
        assertFalse(receive("OTP 123456 for transaction Rs 500"))
        assertTrue(db.backup().events().isEmpty())
    }
    @Test fun multipartRetriesYieldOneTransactionAndStatementVerifiesIt()=runBlocking {
        ledger.setting("sms","true")
        val parts=listOf(SmsPart("AD-SBIINB",debit.take(23),sent),SmsPart("AD-SBIINB",debit.drop(23),sent))
        coroutineScope { (1..8).map { async { intake.accept(parts,sent+it*1000,true,true) } }.awaitAll() }
        assertEquals(1,ledger.smsStats.first().pending)
        rec.processPending();rec.processPending()
        val original=db.backup().transactions().single()
        assertEquals(Verification.PROVISIONAL,original.verification)
        ledger.updateMetadata(original.id,"My shop",null,"Keep this","Personal")
        val statement=ParsedStatement("4821","2026-09-01","2026-09-30",100000,50000,
            listOf(ParsedRow(0,"2026-09-14",null,"UPI/600000000101/SHOP","600000000101",50000,Direction.DEBIT,50000)),emptyList())
        rec.commit(rec.preview(account.id,"synthetic.pdf","sms-fixture",statement),emptyMap())
        assertEquals(original.id,db.backup().transactions().single().id)
        assertEquals(Verification.VERIFIED,db.backup().transactions().single().verification)
        assertEquals(2,db.backup().evidence().size)
        assertEquals("Keep this",db.ledger().metadata(original.id)!!.notes)
        assertEquals(0,ledger.smsStats.first().pending)
    }
    @Test fun unknownAndPendingMessagesStayOutOfTotalsAndCanBeIgnoredWithoutAccount()=runBlocking {
        ledger.setting("sms","true")
        receive("Payment of Rs 500 pending for A/c XX4821")
        receive("Rs 500 debited with fee Rs 20 from A/c XX4821",sent+100)
        rec.processPending()
        assertTrue(db.backup().transactions().isEmpty());assertEquals(2,ledger.smsStats.first().review)
        val event=db.ledger().reviews().first().first()
        assertTrue(rec.reviewCandidates(event.id,account.id).isEmpty())
        assertTrue(runCatching {rec.resolveEvent(event.id,account.id,Resolution("new"))}.isFailure)
        rec.resolveEvent(event.id,"",Resolution("ignore"))
        assertEquals(1,ledger.smsStats.first().review)
        assertTrue(runCatching {rec.resolveEvent(event.id,"",Resolution("ignore"))}.isFailure)
    }
    @Test fun collisionReviewRequiresCompatibleAccountAndPersistsCreatedTarget()=runBlocking {
        ledger.setting("sms","true")
        ledger.addAccount("Collision","4821","Savings");ledger.addAccount("Wrong","9012","Savings")
        receive();rec.processPending()
        val event=db.ledger().reviews().first().single()
        val wrong=db.ledger().allAccounts().single {it.last4=="9012"}
        assertTrue(runCatching {rec.resolveEvent(event.id,wrong.id,Resolution("new"))}.isFailure)
        assertTrue(runCatching {rec.resolveEvent(event.id,account.id,Resolution("nonsense"))}.isFailure)
        rec.resolveEvent(event.id,account.id,Resolution("new"))
        assertEquals(db.backup().transactions().single().id,db.backup().reviewDecisions().single().transactionId)
    }
    @Test fun corruptObservationDoesNotBlockFollowingSms()=runBlocking {
        ledger.setting("sms","true")
        val bad=ledger.raw(SbiSmsParser.parse("AD-SBIINB",debit,sent)!!).copy(identity="bad",parsedJson="{invalid")
        db.ledger().insertEvent(bad)
        receive();rec.processPending()
        assertEquals(1,db.backup().transactions().size)
        assertTrue(db.ledger().event(bad.id)!!.processed);assertNotNull(db.ledger().event(bad.id)!!.reviewReason)
        rec.resolveEvent(bad.id,"",Resolution("ignore"))
    }
    @Test fun mandateCancellationSurvivesLateCreationAndOnlyExecutionSpends()=runBlocking {
        ledger.setting("sms","true")
        receive("UPI mandate Rs 500 registered for A/c XX4821 Ref 600000000111",sent)
        rec.processPending()
        receive("UPI mandate cancelled for A/c XX4821 Ref 600000000111",sent+20000)
        rec.processPending()
        receive("UPI mandate Rs 500 registered for A/c XX4821 Ref 600000000111",sent-10000)
        rec.processPending()
        assertEquals("CANCELLED",db.backup().mandates().single().status)
        assertEquals(50000L,db.backup().mandates().single().amountMinor)
        assertTrue(db.backup().transactions().isEmpty())
        receive("Mandate executed Rs 500 debited from A/c XX4821 Ref 600000000112",sent+30000)
        rec.processPending()
        assertEquals(50000L,ledger.totals(YearMonth.of(2026,9),null).first().spend)
    }
    @Test fun failureThenSuccessAndLateFailurePreservePostedOutcome()=runBlocking {
        ledger.setting("sms","true")
        receive("Rs 500 payment failed for A/c XX4821 UPI Ref 600000000101")
        rec.processPending()
        assertEquals(0L,ledger.totals(YearMonth.of(2026,9),null).first().spend)
        receive();rec.processPending()
        assertEquals(EventKind.DEBIT,db.backup().transactions().single().kind)
        receive("Rs 500 transaction declined for A/c XX4821 UPI Ref 600000000101",sent-1000)
        rec.processPending()
        assertEquals(Outcome.POSTED,db.backup().transactions().single().outcome)
        assertEquals(50000L,ledger.totals(YearMonth.of(2026,9),null).first().spend)
    }
    @Test fun lateSmsAttachesToAlreadyVerifiedStatement()=runBlocking {
        val statement=ParsedStatement("4821","2026-09-01","2026-09-30",100000,50000,
            listOf(ParsedRow(0,"2026-09-14",null,"UPI/600000000101/SHOP","600000000101",50000,Direction.DEBIT,50000)),emptyList())
        rec.commit(rec.preview(account.id,"synthetic.pdf","statement-first",statement),emptyMap())
        ledger.setting("sms","true")
        receive(debit,sent+86400000*3);rec.processPending()
        assertEquals(1,db.backup().transactions().size);assertEquals(2,db.backup().evidence().size)
        assertEquals("2026-09-14",db.backup().transactions().single().date)
        assertEquals(Verification.VERIFIED,db.backup().transactions().single().verification)
    }
}
