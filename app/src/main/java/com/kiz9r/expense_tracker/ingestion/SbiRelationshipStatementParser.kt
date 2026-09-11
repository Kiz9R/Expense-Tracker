package com.kiz9r.expense_tracker.ingestion

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.reconciliation.balanceWarnings
import com.kiz9r.expense_tracker.reconciliation.validateForImport

/** SBI Relationship Summary: dated opening/closing, explicit Credit Debit Balance columns. */
class SbiRelationshipStatementParser : StatementParser {
    companion object {
        fun recognizes(text: String) = text.contains("TRANSACTION DETAILS",true) &&
            Regex("(?i)Your\\s+Opening\\s+Balance\\s+on").containsMatchIn(text)
    }
    override fun parse(text: String): ParsedStatement {
        require(text.length <= 8_000_000) { "Statement is too large." }
        require(Regex("(?i)state bank of india|\\bSBI\\b|sbi\\.co\\.in").containsMatchIn(text)) { "Unable to identify SBI." }
        val accounts = Regex("(?im)^\\s*SAVINGS? ACCOUNT\\s*\\n\\s*([Xx*•]+\\d{4}|\\d{8,18})\\s*$")
            .findAll(text).map { it.groupValues[1].takeLast(4) }.toList()
        require(accounts.size == 1) { "Select a statement containing one savings account. Multiple-account or unknown layouts are unsupported." }
        val day = "\\d{2}-\\d{2}-\\d{2}(?:\\d{2})?"
        val amount = "-?\\d[\\d,]*(?:\\.\\d{2})?"
        fun boundary(label: String): Pair<String,Long> {
            val matches = Regex("(?i)Your\\s+$label\\s+Balance\\s+on\\s+($day)\\s*:\\s*(?:₹|Rs\\.?|INR)?\\s*($amount)")
                .findAll(text).toList()
            require(matches.size==1) { "Missing or ambiguous $label balance/date." }
            val match=matches.single()
            return Dates.parse(match.groupValues[1]).toString() to signed(match.groupValues[2])
        }
        val opening=boundary("Opening")
        val closing=boundary("Closing")
        val rowStart=Regex("^($day)\\s+")
        val row=Regex("^($day)\\s+(.+?)\\s+($amount)\\s+($amount)\\s+($amount)\\s*$")
        val header=Regex("(?i)^Date\\s+Transaction Reference\\s+Ref\\.No\\./Chq\\.No\\.\\s+Credit\\s+Debit\\s+Balance$")
        val rows=mutableListOf<ParsedRow>()
        var inTable=false
        var pending: String?=null
        fun consume() {
            val line=pending ?: return
            val m=row.matchEntire(line) ?: error("Could not read all columns in statement row " + (rows.size+1) + ". No rows were imported.")
            val credit=signed(m.groupValues[3]); val debit=signed(m.groupValues[4])
            require(credit>=0 && debit>=0 && (credit>0)!=(debit>0)) { "Ambiguous credit/debit columns at row " + (rows.size+1) }
            val narration=m.groupValues[2].trim().removeSuffix(" -").trim()
            require(narration.isNotBlank()) { "Missing transaction narration." }
            rows+=ParsedRow(rows.size,Dates.parse(m.groupValues[1]).toString(),null,maskAccounts(narration),
                statementReference(narration),if(debit>0) debit else credit,
                if(debit>0) Direction.DEBIT else Direction.CREDIT,signed(m.groupValues[5]))
            pending=null
        }
        text.lineSequence().forEach { raw ->
            val line=raw.trim().replace(Regex("\\s+")," ")
            when {
                header.matches(line) -> inTable=true
                line.startsWith("Your Closing Balance",true) -> { consume(); inTable=false }
                line.startsWith("Your Opening Balance",true) -> Unit
                line.equals("TRANSACTION OVERVIEW",true) -> { consume(); inTable=false }
                line.startsWith("Visit https://sbi.co.in",true) || line.startsWith("*All dates",true) -> inTable=false
                inTable && Regex("(?i)^null(?: null){5}$").matches(line) -> Unit // Empty PDF table artifact.
                inTable && rowStart.containsMatchIn(line) -> { consume(); pending=line }
                inTable && line.isNotBlank() -> {
                    require(pending!=null) { "Unrecognized transaction-table content. No rows were imported." }
                    pending += " " + line
                }
            }
        }
        consume()
        val parsed=ParsedStatement(accounts.single(),opening.first,closing.first,opening.second,closing.second,rows,emptyList())
        parsed.validateForImport()
        return parsed.copy(warnings=parsed.balanceWarnings())
    }
    private fun signed(value: String) = if(value.startsWith("-")) -Money.parse(value.drop(1)) else Money.parse(value)
}
