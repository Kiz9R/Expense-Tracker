package com.kiz9r.expense_tracker

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.backup.BackupCrypto
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinanceTest {
    private val now=1_788_940_800_000L
    private fun sms(text: String)=SbiSmsParser.parse("AD-SBIINB",text,now)!!
    @Test fun moneyIsExactAndRejectsFractionalPaise() {
        assertEquals(124950L,Money.parse("₹1,249.50"))
        assertEquals(10L,Money.parse("0.10"))
        listOf("1.001","-1","NaN","1e5","922337203685477581").forEach { value ->
            assertTrue(value,runCatching{Money.parse(value)}.isFailure)
        }
    }
    @Test fun parsesDebitCreditAndChannels() {
        val debit=sms("Rs.850.25 debited from A/c XX4821 to SWIGGY on 09-09-2026 at 14:31 UPI Ref 625189208332")
        assertEquals(85025L,debit.amountMinor);assertEquals(Direction.DEBIT,debit.direction)
        assertEquals("4821",debit.accountLast4);assertEquals("625189208332",debit.reference)
        assertEquals(Channel.UPI,debit.channel);assertEquals("2026-09-09",debit.date)
        assertEquals(Direction.CREDIT,sms("INR 5,000 credited to A/c XX4821 on 09/09/2026").direction)
        assertEquals(Channel.ATM,sms("Rs 500 withdrawn at ATM from A/c XX4821").channel)
        assertEquals(Channel.CARD,sms("Rs 700 spent on card at SHOP from A/c XX4821").channel)
        assertEquals(Channel.BANK_FEE,sms("Rs 25 debited from A/c XX4821 for service fee").channel)
    }
    @Test fun financialKindsAreClassifiedBeforeSpending() {
        assertEquals("",sms("UPI transaction Rs 500 debited from A/c XX4821").reference)
        assertEquals(EventKind.UNKNOWN,sms("UPI mandate Rs 999 will be debited from A/c XX4821 tomorrow").kind)
        assertEquals(EventKind.MANDATE_CREATED,sms("UPI mandate for Rs 999 created for A/c XX4821").kind)
        assertEquals(EventKind.MANDATE_CANCELLED,sms("Mandate Rs 999 cancelled for A/c XX4821").kind)
        assertEquals(EventKind.MANDATE_EXECUTED,sms("Mandate executed Rs 999 debited from A/c XX4821").kind)
        assertEquals(EventKind.FAILED,sms("Rs 500 transaction declined for A/c XX4821").kind)
        assertEquals(EventKind.REFUND,sms("Rs 500 refund credited to A/c XX4821").kind)
        assertEquals(EventKind.REVERSAL,sms("Rs 1200 reversed to A/c XX4821").kind)
        assertEquals(EventKind.UNKNOWN,sms("SBI transaction status unavailable for A/c XX4821").kind)
    }
    @Test fun discardsOtpsPromotionsAndUnrelatedSenders() {
        listOf("OTP 123456 for transaction Rs 100","Your statement is ready","Special offer on debit card").forEach {
            assertNull(SbiSmsParser.parse("AD-SBIINB",it,now))
        }
        assertNull(SbiSmsParser.parse("FRIEND","Rs 100 debited from A/c 4821",now))
        assertFalse(SbiSmsParser.isSbiSender("+919999999999"))
    }
    @Test fun rawContentMasksFullAccountNumber() {
        val event=sms("Rs 100 debited from Account No: 12345674821")
        assertFalse(event.content.contains("12345674821"));assertTrue(event.content.contains("4821"))
    }
    @Test fun duplicateSmsIdentityIsStableButTimestampMatters() {
        val text="Rs 100 debited from A/c XX4821"
        assertEquals(sms(text).identity,sms(text).identity)
        assertNotEquals(sms(text).identity,SbiSmsParser.parse("AD-SBIINB",text,now+1000)!!.identity)
    }
    @Test fun notificationParsersRecognizeProvidersAndIgnoreOtp() {
        assertEquals(Source.PHONEPE_NOTIFICATION,PhonePeParser().parse("₹420 paid to Domino's","key",now)!!.source)
        assertEquals(Source.GPAY_NOTIFICATION,GooglePayParser().parse("₹420 paid to Domino's","key",now)!!.source)
        assertNull(PhonePeParser().parse("OTP 123456 transaction","key",now))
        assertEquals(PhonePeParser().parse("₹420 paid to Domino's","key",now)!!.identity,
            PhonePeParser().parse("₹420 paid to Domino's","key",now+100)!!.identity)
    }
    private fun observation(reference: String="ABC123")=Observation(Source.SBI_SMS,"identity",now,date="2026-09-09",
        amountMinor=50000,direction=Direction.DEBIT,merchant="Amazon",reference=reference,kind=EventKind.DEBIT,content="")
    private fun candidate(id: String="a",reference: String="ABC123",amount: Long=50000,account: String="account")=
        MatchCandidate(id,account,amount,Direction.DEBIT,"2026-09-09",now,"Amazon",reference)
    @Test fun exactReferenceIsOnlyAutomaticMerge() {
        val engine=MatchingEngine()
        assertEquals(MatchDecision.Exact("a"),engine.match(observation(),"account",listOf(candidate())))
        assertTrue(engine.match(observation(""),"account",listOf(candidate(reference=""))) is MatchDecision.Review)
        assertTrue(engine.match(observation(),"account",listOf(candidate(amount=40000))) is MatchDecision.Review)
        assertEquals(MatchDecision.New,engine.match(observation(),"account",listOf(candidate(account="other"))))
    }
    @Test fun repeatedSameValuePurchasesAreNeverAutoMerged() {
        assertTrue(MatchingEngine().match(observation(""),"account",listOf(candidate("a"),candidate("b"))) is MatchDecision.Review)
        assertTrue(MatchingEngine().match(observation(),"account",listOf(candidate("a"),candidate("b"))) is MatchDecision.Review)
    }
    @Test fun syntheticStatementOver100RowsAndLogicalFingerprint() {
        val parser=SbiPdfStatementParser()
        val statement=parser.parse(syntheticStatement(120))
        assertEquals(120,statement.rows.size);assertTrue(statement.warnings.isEmpty())
        assertEquals(880000L,statement.closing)
        assertEquals(statement.fingerprint("account"),parser.parse(syntheticStatement(120)+"\nPage 9").fingerprint("account"))
        assertNotEquals(statement.fingerprint("account"),statement.copy(rows=statement.rows+statement.rows.last()).fingerprint("account"))
    }
    @Test fun badAndUnknownStatementLayoutsFailWithoutPartialResults() {
        assertTrue(runCatching{SbiPdfStatementParser().parse("random text")}.isFailure)
        assertTrue(runCatching{SbiPdfStatementParser().parse(syntheticStatement(2).replace("10.00 0.00 9990.00","bad row"))}.isFailure)
        assertTrue(SbiPdfStatementParser().parse(syntheticStatement(2).replace("Closing Balance: 9980.00","Closing Balance: 9000.00")).warnings.isNotEmpty())
    }
    @Test fun wrappedNarrationIsPreservedAfterTheMoneyColumns() {
        val text=syntheticStatement(1).replace("10.00 0.00 9990.00","10.00 0.00 9990.00\nContinued merchant location")
        assertTrue(SbiPdfStatementParser().parse(text).rows.single().narration.contains("Continued merchant location"))
    }
    @Test fun merchantRulesAreDeterministic() {
        val rule=MerchantRuleEntity(matchValue="swiggy")
        assertTrue(matchesRule(rule,"SWIGGY / INDIA"))
        assertFalse(matchesRule(rule.copy(enabled=false),"SWIGGY"))
        assertFalse(matchesRule(rule.copy(matchType="exact"),"SWIGGY INDIA"))
        assertTrue(matchesRule(rule.copy(matchType="startsWith"),"Swiggy India"))
    }
    @Test fun backupAuthenticatesPasswordAndEveryByte() {
        val secret="a long test passphrase".toCharArray()
        val original="private financial evidence".toByteArray()
        val encrypted=BackupCrypto.encrypt(original,secret)
        assertArrayEquals(original,BackupCrypto.decrypt(encrypted,secret))
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("private financial"))
        assertTrue(runCatching{BackupCrypto.decrypt(encrypted,"wrong password".toCharArray())}.isFailure)
        val tampered=encrypted.copyOf();tampered[tampered.lastIndex]=(tampered.last().toInt() xor 1).toByte()
        assertTrue(runCatching{BackupCrypto.decrypt(tampered,secret)}.isFailure)
        assertTrue(runCatching{BackupCrypto.decrypt(encrypted.copyOf(20),secret)}.isFailure)
    }
}
fun syntheticStatement(count: Int): String {
    val rows=(1..count).joinToString("\n") { i ->
        "09-09-2026 09-09-2026 UPI/" + (600000000000L+i) + "/MERCHANT" + i + " 10.00 0.00 " + Money.input(1_000_000L-i*1000L)
    }
    return "State Bank of India\nAccount Number: 12345674821\nStatement Period: 01-09-2026 to 30-09-2026\nOpening Balance: 10000.00\nDate Value Date Narration Debit Credit Balance\n"+
        rows+"\nClosing Balance: "+Money.input(1_000_000L-count*1000L)
}
