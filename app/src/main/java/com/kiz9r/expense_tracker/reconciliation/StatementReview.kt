package com.kiz9r.expense_tracker.reconciliation

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.data.*

data class StatementSummary(
    val rows: Int, val matched: Int, val added: Int, val ignored: Int, val needsReview: Int,
    val refundsAndReversals: Int, val statementDebit: Long, val statementCredit: Long,
    val linkedDebit: Long, val linkedCredit: Long, val opening: Long, val closing: Long,
    val calculatedClosing: Long, val warnings: List<String>
)
data class StatementReport(val imported: StatementImportEntity, val rows: List<StatementRowEntity>,
    val summary: StatementSummary, val decisions: List<ReviewDecisionEntity>)
data class SavedResolution(val sequence: Int, val action: String, val transactionId: String? = null)
data class SavedReview(val choices: List<SavedResolution> = emptyList())

fun effectiveResolution(row: RowPreview, choices: Map<Int, Resolution>): Resolution? =
    choices[row.row.sequence] ?: when (val decision = row.decision) {
        is MatchDecision.Exact -> Resolution("match", decision.transactionId)
        is MatchDecision.New -> Resolution("new")
        is MatchDecision.Review -> null
    }

fun ImportPreview.summary(choices: Map<Int, Resolution>): StatementSummary {
    val actions = rows.map { effectiveResolution(it, choices) }
    return statementSummary(statement, actions.map { it?.action },
        rows.zip(actions).filter { it.second?.action in listOf("new", "match") }.map { it.first.row })
}

fun statementSummary(statement: ParsedStatement, actions: List<String?>, linked: List<ParsedRow>): StatementSummary {
    fun total(rows: List<ParsedRow>, direction: Direction) =
        rows.filter { it.direction == direction }.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
    val debit = total(statement.rows, Direction.DEBIT)
    val credit = total(statement.rows, Direction.CREDIT)
    return StatementSummary(statement.rows.size, actions.count { it == "match" }, actions.count { it == "new" },
        actions.count { it == "ignore" }, actions.count { it == null },
        statement.rows.count { it.observation("", statement.last4).kind in listOf(EventKind.REFUND, EventKind.REVERSAL) },
        debit, credit, total(linked, Direction.DEBIT), total(linked, Direction.CREDIT),
        statement.opening, statement.closing, Math.subtractExact(Math.addExact(statement.opening, credit), debit),
        statement.balanceWarnings())
}

/** Official arithmetic never depends on visibility or review choices. */
fun ParsedStatement.balanceWarnings(): List<String> {
    var running = opening
    val problems = mutableListOf<String>()
    rows.forEach { row ->
        val expected = if (row.direction == Direction.CREDIT) Math.addExact(running, row.amountMinor)
            else Math.subtractExact(running, row.amountMinor)
        if (expected != row.balance)
            problems += "Row " + (row.sequence + 1) + ": expected balance " + Money.format(expected) +
                ", statement shows " + Money.format(row.balance) + "."
        running = row.balance
    }
    val calculated = rows.fold(opening) { sum, row ->
        if (row.direction == Direction.CREDIT) Math.addExact(sum, row.amountMinor) else Math.subtractExact(sum, row.amountMinor)
    }
    if (calculated != closing || running != closing)
        problems += "Closing balance: calculated " + Money.format(calculated) + ", last row " +
            Money.format(running) + ", statement " + Money.format(closing) + "."
    return (problems + warnings.filterNot { it.startsWith("Running balance mismatch") ||
        it.startsWith("Opening balance + credits") }).distinct()
}

fun ParsedStatement.validateForImport() {
    require(last4.matches(Regex("\\d{4}"))) { "Invalid masked statement account." }
    val from = java.time.LocalDate.parse(start)
    val to = java.time.LocalDate.parse(end)
    require(!to.isBefore(from) && rows.isNotEmpty()) { "Invalid or empty statement period." }
    require(rows.map { it.sequence } == rows.indices.toList()) { "Invalid statement row sequence." }
    rows.forEach {
        val date = java.time.LocalDate.parse(it.date)
        require(!date.isBefore(from) && !date.isAfter(to) && it.amountMinor > 0 && it.narration.isNotBlank()) {
            "Invalid statement row " + (it.sequence + 1) + "."
        }
        it.valueDate?.let(java.time.LocalDate::parse)
    }
    balanceWarnings()
}

fun StatementRowEntity.parsed() = ParsedRow(sequence, date, valueDate, narration, reference, amountMinor, direction, balance)

