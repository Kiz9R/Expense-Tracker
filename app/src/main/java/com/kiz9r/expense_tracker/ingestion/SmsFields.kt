package com.kiz9r.expense_tracker.ingestion

import com.kiz9r.expense_tracker.domain.*
import java.time.*
import java.time.format.*
import java.util.Locale

/** Conservative field extraction: ambiguous financial facts become review, never guesses. */
internal object SmsFields {
    private val money = Regex("""(?i)(?:INR|Rs\.?|₹)\s*([0-9][0-9,.]*)(?![0-9])""")
    private val balanceLabel = Regex("""(?i)(?:avl\.?|avail(?:able)?\.?|clear|closing|current)?\s*bal(?:ance)?\.?\s*(?:is\s*)?[:=]?\s*$""")
    private val account = Regex("""(?i)\b(?:a/?c|acct?|account)(?:\s*(?:no\.?|number))?\s*[:.\-]?\s*[Xx*•]*(\d{4,18})\b""")
    private val reference = Regex("""(?i)\b(?:UTR|UMRN|RRN|Chq|transaction number|ref(?:erence)?(?:\s*(?:no\.?|number))?|UPI\s+(?:txn|transaction)\s*(?:id|no\.?)?)\s*[:#./-]?\s*([A-Z0-9]{6,35})\b""")
    private val upi = Regex("""(?i)\bUPI/(?:DR/|CR/)?([A-Z0-9]{8,35})\b""")
    private val umn = Regex("""(?i)\bUMN:\s*([A-Z0-9._-]{6,120}@[A-Z0-9.-]{1,50})""")
    private val datePattern = Regex("""\b(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\d{1,2}[- ][A-Za-z]{3}[- ]\d{2,4}|\d{1,2}[A-Za-z]{3}\d{2,4})\b""")
    private val timePattern = Regex("""(?i)\b(\d{1,2}:\d{2}(?::\d{2})?)(?:\s*(AM|PM))?\b""")

    data class Fields(val amount: Long?, val last4: String?, val reference: String, val date: String,
        val timestamp: Long?, val merchant: String, val balance: Long?, val warning: String?)

    fun extract(text: String, sentAt: Long, requireAmount: Boolean = true): Fields {
        val warnings = mutableListOf<String>()
        val amounts = mutableListOf<Long?>()
        val balances = mutableListOf<Long?>()
        money.findAll(text).forEach { match ->
            val token = match.groupValues[1].trimEnd('.', ',')
            val validGrouping = Regex("""(?:\d+|\d{1,3}(?:,\d{3})+|\d{1,2}(?:,\d{2})*,\d{3})(?:\.\d{1,2})?""").matches(token)
            val amount = if(validGrouping) runCatching { Money.parse(token) }.getOrNull() else null
            val before = text.substring(maxOf(0,match.range.first-35),match.range.first)
            if(balanceLabel.containsMatchIn(before)) balances.add(amount) else amounts.add(amount)
        }
        SbiObservedLayouts.bareAmounts(text).forEach { token ->
            val validGrouping = Regex("""(?:\d+|\d{1,3}(?:,\d{3})+|\d{1,2}(?:,\d{2})*,\d{3})(?:\.\d{1,2})?""").matches(token)
            amounts.add(if(validGrouping) runCatching { Money.parse(token) }.getOrNull() else null)
        }
        if(requireAmount && (amounts.size != 1 || amounts.singleOrNull() == null || (amounts.singleOrNull() ?: 0) <= 0))
            warnings.add("A single positive transaction amount could not be established.")
        val accounts = account.findAll(text).map { it.groupValues[1].takeLast(4) }.distinct().toList()
        if(accounts.size > 1) warnings.add("The message contains more than one account identifier.")
        val refs = (reference.findAll(text).map { it.groupValues[1] } + upi.findAll(text).map { it.groupValues[1] } +
            umn.findAll(text).map { it.groupValues[1] })
            .map { it.uppercase(Locale.ROOT) }.filter { it.any(Char::isDigit) }.distinct().toList()
        if(refs.size > 1) warnings.add("The message contains conflicting references.")
        val dateTokens = datePattern.findAll(text).map { it.value }.distinct().toList()
        var date = Dates.date(sentAt)
        if(dateTokens.size > 1) warnings.add("The message contains multiple dates.")
        if(dateTokens.size == 1) {
            val parsed = parseDate(dateTokens.single())
            if(parsed == null) warnings.add("The transaction date is invalid.") else date = parsed
        }
        val time = timePattern.find(text)
        var instant: Long? = if(dateTokens.isEmpty()) sentAt else null
        if(time != null) {
            val clock = time.groupValues[1] + time.groupValues[2].uppercase(Locale.ROOT).let { if(it.isBlank()) "" else " "+it }
            val format = if(time.groupValues[2].isBlank()) {
                if(time.groupValues[1].count { it==':' }==2) "H:mm:ss" else "H:mm"
            } else if(time.groupValues[1].count { it==':' }==2) "h:mm:ss a" else "h:mm a"
            val parsed = runCatching { LocalTime.parse(clock,DateTimeFormatter.ofPattern(format,Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
            if(parsed == null) warnings.add("The transaction time is invalid.")
            else instant = date.atTime(parsed).atZone(Dates.zone).toInstant().toEpochMilli()
        }
        val merchant = Regex("""(?i)(?:paid to|paid at|received from|credited by|\bto\b|\bat\b|\bfrom\b)\s+(?!(?:a/c|account|acct?|your|INR|Rs|SBI)\b)([A-Za-z][\w@.'& /-]*?)(?=\s+(?:on|via|ref|UPI|from|using|a/c|account)\b|[.;]|$)""")
            .findAll(text).map { it.groupValues[1].trim() }
            .firstOrNull { !Regex("""(?i)^(?:a/c|account|acct|your|INR|Rs|SBI)\b""").containsMatchIn(it) }
            .orEmpty()
        return Fields(amounts.singleOrNull(),accounts.singleOrNull(),refs.singleOrNull().orEmpty(),
            date.toString(),instant,SbiObservedLayouts.merchant(text) ?: merchant.ifBlank { "SBI transaction" },
            balances.singleOrNull(),warnings.joinToString(" ").ifBlank { null })
    }
    private fun parseDate(value: String): LocalDate? {
        for(pattern in listOf("d-M-uuuu","d/M/uuuu","uuuu-MM-dd","d-M-uu","d/M/uu","d-MMM-uuuu","d MMM uuuu","d-MMM-uu","d MMM uu","dMMMuu","dMMMuuuu")) {
            val parsed=runCatching { LocalDate.parse(value,DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(pattern).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
            if(parsed!=null) return parsed
        }
        return null
    }
}
