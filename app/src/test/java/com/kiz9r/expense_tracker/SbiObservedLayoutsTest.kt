package com.kiz9r.expense_tracker

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** Invented values reproducing bank layout families from the supplied XML, not copied records. */
class SbiObservedLayoutsTest {
    private val now=LocalDateTime.of(2026,9,14,12,0).atZone(Dates.zone).toInstant().toEpochMilli()
    private fun sms(body: String)=requireNotNull(SbiSmsParser.parse("JD-SBIUPI-S",body,now,now))
    @Test fun debitWithoutCurrencyHasExactAmountDatePartyAndReference() {
        val event=sms("Dear UPI user A/C X4821 debited by 125.5 on date 03Oct25 trf to SAMPLE CAFE Refno 600000000101 If not u? call-1800000000 for other services-18000000-SBI")
        assertEquals(EventKind.DEBIT,event.kind);assertEquals(12550L,event.amountMinor)
        assertEquals("2025-10-03",event.date);assertNull(event.timestamp)
        assertEquals("SAMPLE CAFE",event.merchant);assertEquals("600000000101",event.reference)
        assertEquals(Channel.UPI,event.channel);assertEquals("4821",event.accountLast4)
    }
    @Test fun creditCompactDatePreservesTransferSource() {
        val event=sms("Dear SBI User, your A/c X4821-credited by Rs.750 on 05Jan26 transfer from SAMPLE PERSON Ref No 600000000102 -SBI")
        assertEquals(75000L,event.amountMinor);assertEquals(Direction.CREDIT,event.direction)
        assertEquals("2026-01-05",event.date);assertEquals("SAMPLE PERSON",event.merchant)
        assertEquals(Channel.UPI,event.channel)
    }
    @Test fun linkedImpsCreditAndNeftKeepCorrectChannelsAndParties() {
        val imps=sms("Dear Customer, Your a/c no. XXXXXXXX4821 is credited by Rs.25.00 on 03-04-26 by a/c linked to SAMPLE PERSON (IMPS Ref# 600000000103)-SBI")
        assertEquals(Channel.IMPS,imps.channel);assertEquals("SAMPLE PERSON",imps.merchant)
        assertEquals("600000000103",imps.reference)
        val neft=sms("Dear Customer, INR 12,345.00 credited to your A/c No XX4821 on 05/04/2026 through NEFT with UTR BANKH60000000001 by SAMPLE EMPLOYER LIMITED, INFO: BATCHID:0000 /ATTN/ //SALARY-SBI")
        assertEquals("SAMPLE EMPLOYER LIMITED",neft.merchant);assertEquals(Channel.NEFT,neft.channel)
        assertEquals(1234500L,neft.amountMinor)
    }
    @Test fun mandateWithoutCurrencyPreservesAmountAndUniqueMandateNumber() {
        val umn="sample600000001@samplebank"
        val event=sms("Your UPI-Mandate is successfully cancelled towards SAMPLE STREAM for 299.00 from A/c No.XXXXXX4821. UMN:"+umn+" -SBI")
        assertEquals(EventKind.MANDATE_CANCELLED,event.kind);assertEquals(29900L,event.amountMinor)
        assertEquals("SAMPLE STREAM",event.merchant);assertEquals(umn.uppercase(),event.reference)
        val created=sms("Your UPI-Mandate for Rs.299.00 is successfully created towards SAMPLE STREAM from A/c No: XXXXXX4821. UMN:"+umn+" If not you, kindly report on 18000000. -SBI")
        assertEquals(EventKind.MANDATE_CREATED,created.kind);assertEquals(event.reference,created.reference)
    }
    @Test fun unassignedMandatesAndScheduledCollectRequestsNeverBecomeDebits() {
        val created=sms("UPI-Mandate successfully created towards SAMPLE STORE for Rs1500.00 -SBI")
        assertNull(created.accountLast4);assertEquals(EventKind.MANDATE_CREATED,created.kind)
        val request=sms("Dear Customer, You have received a UPI-Mandate collect request from SAMPLE STORE for Rs.299.00. Login into Google Pay application to authorize. -SBI")
        assertEquals(EventKind.UNKNOWN,request.kind);assertNull(request.direction)
        val scheduled=sms("Dear UPI User, UPI AutoPay for SAMPLE STORE debit of Rs.299.00 is scheduled on .15/09/26, sample600000001@bank Please ensure sufficient balance in your account. -SBI")
        assertEquals(EventKind.UNKNOWN,scheduled.kind);assertNull(scheduled.direction)
    }
    @Test fun serviceAndMarketingMessagesAreDiscardedWithoutDroppingPostedPayments() {
        listOf("Dear Customer, Choose SBI World Debit Card and enjoy lots of features curated specially for you!",
            "Dear Customer, You can withdraw Cash securely from SBI ATMs without ATM/ Debit card. Open YONO APP.",
            "You have successfully submitted your application for SBI Credit Card (App No. 6000000000000).",
            "Alert!!-Dear Customer, you have permitted SAMPLE CONSENT to access transaction details. Please revoke the consent if not you.",
            "Dear Customer, Earn reward points every time you spend.").forEach {
            assertNull(SbiSmsParser.parse("JD-SBIBNK-P",it,now))
        }
        assertEquals(EventKind.DEBIT,sms("Rs 500 debited from A/c XX4821 to REWARD POINTS SHOP").kind)
    }
    @Test fun cardSenderKeepsCardDigitsOutOfAccountAssignmentAndSeparatesBalance() {
        val text="Dear Customer, transaction number 123456 for Rs.1500.00 by SBI Debit Card X4821 done at SAMPLE SHOP on 03Oct25 at 14:30:00. Your updated available balance is Rs.100000.00. If not done by you, forward this SMS to the bank."
        val event=requireNotNull(SbiSmsParser.parse("JD-ATMSBI-S",text,now))
        assertEquals(EventKind.DEBIT,event.kind);assertNull(event.accountLast4)
        assertEquals(150000L,event.amountMinor);assertEquals(10000000L,event.balanceMinor)
        assertEquals("123456",event.reference);assertEquals("SAMPLE SHOP",event.merchant)
        assertEquals(Channel.CARD,event.channel);assertEquals("2025-10-03",event.date)
        assertEquals(LocalDateTime.of(2025,10,3,14,30).atZone(Dates.zone).toInstant().toEpochMilli(),event.timestamp)
        assertNull(SbiSmsParser.parse("JD-ATMSBI-S",text.replace("SAMPLE SHOP","MAC999999 SHOP"),now)!!.accountLast4)
        assertEquals("MAC999999 SHOP",maskAccounts("MAC999999 SHOP"))
        assertNull(SbiSmsParser.parse("FAKE-ATMSBI",text,now))
        assertNull(SbiSmsParser.parse("JD-NOTSBI-S",text,now))
    }
    @Test fun cbsCreditsNachAndCashSeparatePostingAmountFromAvailableBalance() {
        val bodies=listOf(
            "Your A/C XXXXX124821 has credit for SALARY of Rs 1500.00 on 03/04/26. Avl Bal Rs 1,00,000.00.-SBI",
            "Dear Customer, Your A/C XXXXX124821 has a debit by NACH of Rs 1,500.00 on 03/04/26. Avl Bal Rs 1,00,000.00. Download YONO - SBI",
            "Your A/C XXXXX124821 Credited INR 1,500.00 on 03/04/26 -Deposit of Cash at SAMPLE BRANCH. Avl Bal INR 1,00,000.00-SBI",
            "Dear Customer, Your A/C XXXXX124821 has a credit by Cheque of Rs 1,500.00 on 03/04/26. Avl Bal Rs 1,00,000.00.-SBI")
        bodies.forEachIndexed { index,text ->
            val event=requireNotNull(SbiSmsParser.parse("AX-CBSSBI-S",text,now))
            assertEquals(if(index==1)EventKind.DEBIT else EventKind.CREDIT,event.kind)
            assertEquals(150000L,event.amountMinor);assertEquals(10000000L,event.balanceMinor)
            assertEquals("4821",event.accountLast4);assertEquals("2026-04-03",event.date)
            assertFalse(event.content.contains("124821"));assertEquals(event.content,maskAccounts(event.content))
            if(index==1)assertEquals(Channel.NACH,event.channel)
            if(index==2)assertEquals(Channel.CASH,event.channel)
        }
    }
    @Test fun clearingReferenceAndUmrnAreDistinctFromAuthorizationSpending() {
        val clearing=sms("Chq 123456 for INR 1,500.00 recd in clearing debited from AC XXXXX124821, Avl bal INR 1,00,000.00 - SBI")
        assertEquals("4821",clearing.accountLast4);assertFalse(clearing.content.contains("124821"))
        assertEquals(EventKind.DEBIT,clearing.kind);assertEquals("123456",clearing.reference)
        assertEquals(150000L,clearing.amountMinor);assertEquals(10000000L,clearing.balanceMinor)
        val mandate=sms("Dear Customer, Mandate with UMRN SBIN6000000000000000 for Rs 1,500.00 issued to SAMPLE SERVICES. In case of any issues please contact branch-SBI")
        assertEquals(EventKind.MANDATE_CREATED,mandate.kind);assertNull(mandate.direction)
        assertEquals("SBIN6000000000000000",mandate.reference)
        assertEquals("SAMPLE SERVICES",mandate.merchant);assertNull(mandate.accountLast4)
    }
    @Test fun cardAndCbsServiceAlertsAreNotFinancialEvidence() {
        listOf("Dear Customer, your SBI Debit Card is not active for online transactions.",
            "Dear Customer, you have used your free transactions at ATMs.",
            "Dear Customer, Aadhaar has been seeded to your account.").forEach {
            assertNull(SbiSmsParser.parse("AD-CBSSBI-S",it,now))
        }
    }
    @Test fun malformedUnprefixedAmountsDatesAndConflictingReferencesStayInReview() {
        listOf("125.001","500,00").forEach { value ->
            assertEquals(EventKind.UNKNOWN,sms("Dear UPI user A/C X4821 debited by "+value+" on date 03Oct25 trf to SAMPLE Refno 600000000101").kind)
        }
        assertEquals(EventKind.UNKNOWN,sms("Dear UPI user A/C X4821 debited by 500 on date 31Feb26 trf to SAMPLE Refno 600000000101").kind)
        val conflict=sms("Your UPI-Mandate is successfully cancelled towards SAMPLE for 299.00 from A/c No.XXXXXX4821. UMN:sample600000001@bank Ref 600000000102 -SBI")
        assertEquals(EventKind.UNKNOWN,conflict.kind)
    }
}
