package com.kiz9r.expense_tracker

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SmsParserTest {
    private val sent=LocalDateTime.of(2026,9,14,14,30).atZone(Dates.zone).toInstant().toEpochMilli()
    private fun parse(text: String)=requireNotNull(SbiSmsParser.parse("AD-SBIINB",text,sent+86400000,sent))
    @Test fun separatesBalanceFromAmountInEitherOrder() {
        listOf("Avl Bal: Rs 9,500.00. Rs 500.00 debited from A/c XX4821 to SHOP",
            "Rs 500.00 debited from A/c XX4821. Available Balance INR 9,500.00").forEach {
            val event=parse(it)
            assertEquals(EventKind.DEBIT,event.kind);assertEquals(50000L,event.amountMinor)
            assertEquals(950000L,event.balanceMinor);assertNull(event.parseWarning)
        }
    }
    @Test fun extractsUpiDrReferenceAndDoesNotReadCardDigitsAsAccount() {
        val event=parse("Your A/c XXXXX4821 debited by Rs.500.00 on 14-09-26. UPI/DR/600000000101/SHOP")
        assertEquals("600000000101",event.reference);assertEquals("2026-09-14",event.date)
        val card=parse("Rs 500 spent on card XX1234 at SHOP on 14-09-2026")
        assertNull(card.accountLast4);assertEquals(Channel.CARD,card.channel)
    }
    @Test fun twoAmountsConflictingReferencesAndAccountsRequireReview() {
        listOf("Rs 500 debited, charges Rs 20 from A/c XX4821",
            "Rs 500 debited from A/c XX4821 Ref 600000000101 UTR 600000000102",
            "Rs 500 debited from A/c XX4821 to Account XX9012").forEach {
            assertEquals(it,EventKind.UNKNOWN,parse(it).kind);assertNotNull(parse(it).parseWarning)
        }
    }
    @Test fun badMoneyIsNeverTruncatedToValidPaise() {
        listOf("1.001","9,99,99","0","99999999999999999999").forEach {
            assertEquals(it,EventKind.UNKNOWN,parse("Rs "+it+" debited from A/c XX4821").kind)
        }
    }
    @Test fun invalidCalendarDatesAndClockTimesRequireReview() {
        listOf("31-02-2026","29-02-2025","14-09-2026 at 25:61").forEach {
            assertEquals(it,EventKind.UNKNOWN,parse("Rs 500 debited from A/c XX4821 on "+it).kind)
        }
    }
    @Test fun explicitDatesTimesAndLateReceiptRemainSeparate() {
        assertEquals("SHOP",parse("Rs 500 debited from A/c XX4821 to SHOP on 14-09-2026").merchant)
        val event=parse("INR 500 credited to A/c XX4821 on 14-Sep-2026 at 2:30 PM")
        assertEquals(EventKind.CREDIT,event.kind);assertEquals(sent,event.timestamp)
        assertEquals(sent+86400000,event.receivedAt)
        assertEquals("2026-09-14",parse("Rs 500 debited from A/c XX4821").date)
        assertNull(parse("Rs 500 debited from A/c XX4821 on 14/09/2026").timestamp)
    }
    @Test fun pendingRefundsReversalsAndScheduledDebitsNeverPost() {
        listOf("Rs 500 refund initiated for A/c XX4821","Rs 500 will be reversed to A/c XX4821",
            "Payment of Rs 500 pending for A/c XX4821","Rs 500 refund status unavailable for A/c XX4821",
            "Mandate Rs 500 will be debited from A/c XX4821 tomorrow").forEach {
            assertEquals(it,EventKind.UNKNOWN,parse(it).kind)
        }
    }
    @Test fun debitAndCreditInOneMessageRequireReview() {
        assertEquals(EventKind.UNKNOWN,parse("Rs 500 debited and credited for A/c XX4821").kind)
    }
    @Test fun mandatesMayOmitAmountAndExecutionIsSeparate() {
        assertEquals(EventKind.MANDATE_CANCELLED,parse("Mandate cancelled for A/c XX4821 Ref 600000000101").kind)
        assertEquals(EventKind.MANDATE_CREATED,parse("UPI mandate registered for A/c XX4821 Ref 600000000101").kind)
        assertEquals(EventKind.MANDATE_EXECUTED,parse("Mandate executed Rs 500 debited from A/c XX4821").kind)
        assertEquals(EventKind.FAILED,parse("Rs 500 transaction failed for A/c XX4821").kind)
    }
    @Test fun postedRefundsFeesAndTransfersAreClassified() {
        assertEquals(EventKind.REFUND,parse("Rs 500 refunded to A/c XX4821").kind)
        assertEquals(EventKind.REVERSAL,parse("Rs 500 reversed to A/c XX4821").kind)
        assertEquals(Channel.BANK_FEE,parse("Rs 25 debited from A/c XX4821 for fee").channel)
        assertEquals(Channel.NEFT,parse("Rs 500 credited to A/c XX4821 via NEFT UTR SBIN600000101").channel)
    }
    @Test fun multipartIdentityUsesMessageTimeAndRejectsMixedSenders() {
        val body="Rs 500 debited from A/c XX4821 to SHOP"
        val parts=listOf(SmsPart("AD-SBIINB",body.take(18),sent),SmsPart("AD-SBIINB",body.drop(18),sent))
        assertEquals(parse(body).identity,assembleSms(parts,sent+4000)!!.identity)
        assertEquals(assembleSms(parts,sent)!!.identity,assembleSms(parts,sent+9000)!!.identity)
        assertNull(assembleSms(parts.mapIndexed { i,p -> if(i==1)p.copy(sender="FRIEND") else p },sent))
        assertNull(assembleSms(List(65){parts.first()},sent))
    }
    @Test fun credentialsPromotionsAndUnrelatedSendersNeverPersist() {
        listOf("OTP 123456 transaction Rs 500","PIN 1234 payment Rs 500","Rs 500 pre-approved loan offer",
            "Your statement is ready","SBI welcomes you").forEach { assertNull(SbiSmsParser.parse("AD-SBIINB",it,sent)) }
        assertNull(SbiSmsParser.parse("+919000000000","Rs 500 debited from A/c 4821",sent))
        val message="Rs 500 debited from Account No: 12345674821"
        assertFalse(parse(message).content.contains("12345674821"))
        assertEquals(SbiSmsParser.VERSION,parse(message).parserVersion)
    }
}
