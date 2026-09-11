package com.kiz9r.expense_tracker

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.*
import org.junit.Test
import org.junit.Assert.*

class RelationshipParserTest {
    private fun fixture(rows: Int = 120): String {
        val lines=mutableListOf("SBI Relationship Summary", "TRANSACTION DETAILS", "SAVING ACCOUNT",
            "XXXXXXX4821", "Date Transaction Reference Ref.No./Chq.No. Credit Debit Balance",
            "Your Opening Balance on 01-07-26: 10000.00", "null null null null null null")
        var balance=1000000L
        repeat(rows) { i ->
            if(i>0 && i%20==0) lines+=listOf("Visit https://sbi.co.in Customer Care 1800", "SBI Relationship Summary",
                "Welcome SAMPLE CUSTOMER", "Date Transaction Reference Ref.No./Chq.No. Credit Debit Balance")
            val credit=i%5==0
            balance+=if(credit) 2000 else -1000
            lines+="02-07-26 UPI/"+(if(credit) "CR" else "DR")+"/"+(700000000000L+i)+"/SAMPLE SHOP/TEST/UPI - "+
                (if(credit) "20.00 0" else "0 10.00")+" "+Money.input(balance)
        }
        lines+="Your Closing Balance on 31-07-26: "+Money.input(balance)
        return lines.joinToString("\n")
    }
    @Test fun relationshipLayoutHandlesCreditFirstZerosAndRepeatedPageHeaders() {
        val s=SbiPdfStatementParser().parse(fixture())
        assertEquals(120,s.rows.size);assertTrue(s.balanceWarnings().isEmpty())
        assertEquals("4821",s.last4);assertEquals("2026-07-01",s.start);assertEquals("2026-07-31",s.end)
        assertEquals(Direction.CREDIT,s.rows.first().direction)
        assertEquals("700000000000",s.rows.first().reference)
        assertEquals("SAMPLE SHOP",s.rows.first().observation("","4821").merchant)
        assertEquals(952000L,s.closing)
    }
    @Test fun openingBalanceMayFollowTheRowsInPdfContentOrder() {
        val original=fixture(2)
        val moved=original.replace("Your Opening Balance on 01-07-26: 10000.00\n","")
            .replace("Your Closing Balance","TRANSACTION OVERVIEW\nYour Opening Balance on 01-07-26: 10000.00\nYour Closing Balance")
        assertEquals(SbiPdfStatementParser().parse(original),SbiPdfStatementParser().parse(moved))
    }
    @Test fun relationshipMalformedColumnsAndMultipleAccountsAreRejected() {
        val parser=SbiPdfStatementParser()
        assertTrue(runCatching {parser.parse(fixture(2).replace("20.00 0","20.00 5.00"))}.isFailure)
        assertTrue(runCatching {parser.parse(fixture(2).replace("XXXXXXX4821","XXXXXXX4821\nSAVING ACCOUNT\nXXXXXXX9999"))}.isFailure)
        assertTrue(runCatching {parser.parse(fixture(2).replace("Credit Debit Balance","Debit Credit Balance"))}.isFailure)
        assertTrue(runCatching {parser.parse(fixture(2).replace("Your Closing Balance","Missing Closing Balance"))}.isFailure)
    }
    @Test fun relationshipBalanceMismatchIsDetailedAndNotSilentlyFixed() {
        val s=SbiPdfStatementParser().parse(fixture(2).replace("0 10.00 10010.00","0 10.00 9000.00"))
        assertTrue(s.balanceWarnings().any {it.contains("Row 2")})
        assertEquals(900000L,s.rows.last().balance)
    }
    @Test fun wrappedNarrationAndPageBoundaryPreserveReference() {
        val text=fixture(2).replace("SAMPLE SHOP/TEST/UPI - 20.00","SAMPLE\nSHOP/TEST/UPI - 20.00")
        val s=SbiPdfStatementParser().parse(text)
        assertEquals("700000000000",s.rows.first().reference)
        assertTrue(s.rows.first().narration.contains("SAMPLE SHOP"))
        assertEquals("CIUBH7654321",statementReference("NEFT*CIUB0000123*CIUBH7654321*SAMPLE COMPANY"))
    }
    @Test fun channelAndNarrationRankSuggestionsButNeverAuthorizeMerging() {
        val e=Observation(Source.SBI_STATEMENT,"id",0,date="2026-07-01",kind=EventKind.DEBIT,
            amountMinor=50000,direction=Direction.DEBIT,merchant="SHOP",content="SHOP groceries receipt",channel=Channel.UPI)
        val low=MatchCandidate("low","a",50000,Direction.DEBIT,e.date,null,"OTHER","",channel=Channel.CARD,narration="unrelated")
        val high=low.copy(id="high",channel=Channel.UPI,narration="SHOP groceries receipt")
        val d=MatchingEngine().match(e,"a",listOf(low,high)) as MatchDecision.Review
        assertEquals(listOf("high","low"),d.candidateIds)
    }
    @Test fun summariesSeparateIgnoredRowsFromOfficialBalances() {
        val s=SbiPdfStatementParser().parse(fixture(2))
        val preview=ImportPreview("a","synthetic.pdf","hash",s,s.rows.map {RowPreview(it,MatchDecision.New,emptyList())})
        val result=preview.summary(mapOf(1 to Resolution("ignore")))
        assertEquals(1000L,result.statementDebit);assertEquals(0L,result.linkedDebit)
        assertEquals(2000L,result.statementCredit);assertEquals(1,result.ignored)
        assertEquals(s.closing,result.calculatedClosing);assertTrue(result.warnings.isEmpty())
    }
}
