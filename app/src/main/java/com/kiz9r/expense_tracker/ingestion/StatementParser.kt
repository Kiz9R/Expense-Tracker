package com.kiz9r.expense_tracker.ingestion

import com.kiz9r.expense_tracker.domain.*

data class ParsedRow(val sequence: Int, val date: String, val valueDate: String?, val narration: String,
    val reference: String, val amountMinor: Long, val direction: Direction, val balance: Long) {
    fun fingerprint(accountId: String) = hash("$accountId|$date|$valueDate|${normalized(narration)}|$reference|$amountMinor|$direction|$balance")
    fun observation(identity: String, last4: String) = Observation(Source.SBI_STATEMENT,identity,System.currentTimeMillis(),
        date=date,accountLast4=last4,amountMinor=amountMinor,direction=direction,merchant=merchantName(narration),
        reference=reference,channel=detectChannel(narration),kind=when {
            direction == Direction.CREDIT && Regex("(?i)reversal|reversed").containsMatchIn(narration) -> EventKind.REVERSAL
            direction == Direction.CREDIT && Regex("(?i)refund").containsMatchIn(narration) -> EventKind.REFUND
            direction == Direction.CREDIT -> EventKind.CREDIT
            else -> EventKind.DEBIT
        },content=narration)
}
data class ParsedStatement(val last4: String, val start: String, val end: String,
    val opening: Long, val closing: Long, val rows: List<ParsedRow>, val warnings: List<String>,
    val parserVersion: String? = SbiPdfStatementParser.VERSION) {
    fun fingerprint(accountId: String) = hash("$accountId|$start|$end|$opening|$closing|${rows.size}|" +
        rows.map { it.fingerprint(accountId) }.sorted().joinToString("|"))
}
interface StatementParser { fun parse(text: String): ParsedStatement }
/** Strict experimental SBI table parser. Unrecognized layouts are rejected instead of guessed. */
class SbiPdfStatementParser : StatementParser {
    companion object { const val VERSION = "2.0.0" }
    private val money = Regex("(?<![\\w.])-?\\d[\\d,]*\\.\\d{2}(?!\\d)")
    private val date = "(?:\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{1,2} [A-Za-z]{3} \\d{4}|\\d{4}-\\d{2}-\\d{2})"
    override fun parse(text: String): ParsedStatement {
        if (SbiRelationshipStatementParser.recognizes(text)) return SbiRelationshipStatementParser().parse(text)
        require(text.length <= 8_000_000) { "Statement is too large." }
        require(Regex("(?i)state bank of india|\\bSBI\\b").containsMatchIn(text)) { "Unable to recognize this SBI statement format." }
        require(Regex("(?i)debit|withdrawal").containsMatchIn(text) && Regex("(?i)credit|deposit").containsMatchIn(text)) { "Missing statement column headings." }
        val account = Regex("(?i)(?:account|a/c)(?:\\s*(?:number|no\\.?))?\\s*[:\\-]?\\s*[Xx*•]*(\\d{4,18})").find(text)?.groupValues?.get(1)?.takeLast(4)
            ?: throw IllegalArgumentException("Could not identify the statement account.")
        fun balance(label: String): Long {
            val line = text.lineSequence().firstOrNull { Regex("(?i)$label\\s+balance").containsMatchIn(it) }
                ?: throw IllegalArgumentException("Missing $label balance. This PDF layout is not yet supported.")
            return money.findAll(line).lastOrNull()?.value?.let { signedMoney(it) }
                ?: throw IllegalArgumentException("Invalid $label balance.")
        }
        val opening = balance("opening")
        val closing = balance("closing")
        val period = Regex("(?i)(?:statement\\s+(?:period|from)|period|from)\\s*:?\\s*($date)\\s*(?:to|-)\\s*($date)").find(text)
            ?: throw IllegalArgumentException("Could not identify statement period.")
        val start = Dates.parse(period.groupValues[1]); val end = Dates.parse(period.groupValues[2])
        require(!end.isBefore(start)) { "Invalid statement period." }
        val rowStart = Regex("^\\s*($date)(?:\\s+($date))?\\s+(.+)$")
        val rows = mutableListOf<ParsedRow>()
        var pending: String? = null
        val warnings = mutableListOf<String>()
        var running = opening
        fun consume(line: String) {
            val match = rowStart.find(line) ?: error("Malformed statement row.")
            val day = Dates.parse(match.groupValues[1])
            require(!day.isBefore(start) && !day.isAfter(end)) { "A row is outside the statement period." }
            val rest = match.groupValues[3]
            val amounts = money.findAll(rest).toList()
            require(amounts.size >= 2) { "Unable to read all amounts in a statement row." }
            val ending = signedMoney(amounts.last().value)
            val change = Math.subtractExact(ending,running)
            val cells = if (amounts.size >= 3 && rest.substring(amounts[amounts.size-3].range.last+1,amounts[amounts.size-2].range.first).isBlank()) amounts.takeLast(3) else amounts.takeLast(2)
            val amount: Long
            val direction: Direction
            if (cells.size == 3) {
                val debit = signedMoney(cells[0].value); val credit = signedMoney(cells[1].value)
                require(debit >= 0 && credit >= 0 && (debit > 0) != (credit > 0)) { "Ambiguous debit/credit columns." }
                direction = if (debit > 0) Direction.DEBIT else Direction.CREDIT
                amount = if (debit > 0) debit else credit
                if (change != credit-debit) warnings += "Running balance mismatch at row ${rows.size+1}."
            } else {
                amount = signedMoney(cells[0].value)
                require(amount > 0 && change != Long.MIN_VALUE && kotlin.math.abs(change) == amount) { "Cannot infer debit/credit from balance progression." }
                direction = if (change < 0) Direction.DEBIT else Direction.CREDIT
            }
            val prefix = rest.substring(0,cells.first().range.first).trim().trimEnd('-').trim()
            val continuation = rest.substring(amounts.last().range.last+1).trim()
            val narration = listOf(prefix,continuation).filter {it.isNotBlank()}.joinToString(" ")
            require(narration.isNotBlank()) { "Missing narration." }
            val ref = statementReference(narration)
            rows += ParsedRow(rows.size,day.toString(),match.groupValues[2].takeIf { it.isNotBlank() }?.let { Dates.parse(it).toString() },
                maskAccounts(narration),ref,amount,direction,ending)
            running = ending
        }
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (rowStart.containsMatchIn(line)) { pending?.let(::consume); pending = line }
            else if (pending != null && line.isNotBlank() &&
                !Regex("(?i)^(page\\b|date\\b|txn date\\b|value date\\b|transaction date\\b|debit\\b|credit\\b|balance\\b|closing balance\\b|opening balance\\b|total\\b|statement\\b|state bank\\b|account\\b|end of\\b)").containsMatchIn(line)) {
                pending += " " + line
            }
        }
        pending?.let(::consume)
        require(rows.isNotEmpty()) { "No supported rows found. Scanned PDFs are unsupported." }
        val calculated = rows.fold(opening) { total,row -> if (row.direction == Direction.CREDIT) Math.addExact(total,row.amountMinor) else Math.subtractExact(total,row.amountMinor) }
        if (calculated != closing || running != closing) warnings += "Opening balance + credits − debits does not equal closing balance."
        return ParsedStatement(account,start.toString(),end.toString(),opening,closing,rows,warnings.distinct())
    }
    private fun signedMoney(value: String) = if(value.startsWith("-")) -Money.parse(value.drop(1)) else Money.parse(value)
}
fun merchantName(narration: String): String {
    val tokens = narration.split("/")
    if(tokens.size >= 4 && tokens[0].equals("UPI",true) && tokens[1].uppercase() in listOf("DR","CR")) return tokens[3].trim().ifBlank { narration }
    return if (tokens.firstOrNull()?.equals("UPI",true) == true && tokens.size >= 3) tokens[2].trim().ifBlank { narration }
        else narration.take(100)
}
fun statementReference(narration: String): String =
    (Regex("(?i)\\bUPI/(?:DR/|CR/)?(\\d{6,35})\\b").find(narration)?.groupValues?.get(1)
        ?: Regex("(?i)\\b(?:REF|UTR)[: /-]+([A-Z0-9]{6,35})\\b").find(narration)?.groupValues?.get(1)
        ?: Regex("(?i)\\bNEFT\\*[^*]+\\*([A-Z0-9]{6,35})\\*").find(narration)?.groupValues?.get(1))
        ?.uppercase(java.util.Locale.ROOT).orEmpty()
