package com.kiz9r.expense_tracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Direction { DEBIT, CREDIT }
enum class EventKind { DEBIT, CREDIT, REFUND, REVERSAL, FAILED, MANDATE_CREATED, MANDATE_CANCELLED, MANDATE_EXECUTED, UNKNOWN, IGNORE }
enum class Verification { PROVISIONAL, LIKELY_MATCHED, VERIFIED, NEEDS_REVIEW }
enum class Outcome { POSTED, FAILED, REVERSED, PARTIALLY_REFUNDED, REFUNDED }
enum class Channel { UPI, ATM, IMPS, NEFT, RTGS, CARD, ECS, NACH, CASH, INTEREST, BANK_FEE, INTERNAL_TRANSFER, UNKNOWN }
enum class Source { SBI_SMS, PHONEPE_NOTIFICATION, GPAY_NOTIFICATION, SBI_STATEMENT, MANUAL }

object Money {
    fun parse(text: String): Long {
        val clean = text.replace(Regex("(?i)(INR|Rs\\.?|₹|,)"), "").trim()
        require(Regex("\\d+(\\.\\d{1,2})?").matches(clean)) { "Enter an amount with at most two decimal places." }
        return BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }
    fun format(paise: Long): String = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(BigDecimal.valueOf(paise, 2))
    fun input(paise: Long): String = BigDecimal.valueOf(paise, 2).toPlainString()
}
object Dates {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    fun today(): LocalDate = LocalDate.now(zone)
    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun parse(value: String): LocalDate {
        for (format in listOf("d-M-yyyy", "d/M/yyyy", "d.M.yyyy", "d MMM yyyy", "dd-MMM-yyyy", "d-M-yy", "d/M/yy", "yyyy-MM-dd")) {
            runCatching { return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(format, Locale.ENGLISH)) }
        }
        throw IllegalArgumentException("Unrecognized date: $value")
    }
}
fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun hash(text: String): String = hash(text.toByteArray(Charsets.UTF_8))
fun normalized(text: String): String = text.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
fun newId(): String = java.util.UUID.randomUUID().toString()
fun maskAccounts(text: String): String = text.replace(
    Regex("(?i)((?:a/c|acct?|account)(?:\\s*(?:no\\.?|number))?\\s*[:.\\-]?\\s*)(\\d{8,18})")
) { it.groupValues[1] + "••••" + it.groupValues[2].takeLast(4) }
data class Observation(
    val source: Source, val identity: String, val receivedAt: Long, val timestamp: Long? = null,
    val date: String, val accountLast4: String? = null, val amountMinor: Long? = null,
    val direction: Direction? = null, val merchant: String = "", val reference: String = "",
    val channel: Channel = Channel.UNKNOWN, val kind: EventKind, val content: String,
    val parserVersion: String = "1.0.0"
)
data class MatchCandidate(
    val id: String, val accountId: String, val amountMinor: Long, val direction: Direction,
    val date: String, val timestamp: Long?, val merchant: String, val reference: String,
    val currency: String = "INR", val channel: Channel = Channel.UNKNOWN,
    val narration: String = "", val sources: List<Source> = emptyList(),
    val verification: Verification = Verification.PROVISIONAL, val outcome: Outcome = Outcome.POSTED
)
sealed interface MatchDecision {
    data class Exact(val transactionId: String) : MatchDecision
    data class Review(val candidateIds: List<String>, val reason: String) : MatchDecision
    data object New : MatchDecision
}
/** Fuzzy scores are suggestions, never authorization to silently merge financial records. */
class MatchingEngine {
    fun score(event: Observation, candidate: MatchCandidate): Int {
        fun tokens(value: String) = value.uppercase(Locale.ROOT).split(Regex("[^A-Z0-9]+"))
            .filter { it.length > 2 }.toSet()
        val source = tokens(event.content)
        val target = tokens(candidate.narration)
        val similarity = if(source.isEmpty() || target.isEmpty()) 0 else
            (15 * source.intersect(target).size / source.union(target).size)
        return (if(candidate.date == event.date) 20 else 0) +
            (if(normalized(candidate.merchant)==normalized(event.merchant) && event.merchant.isNotBlank()) 20 else 0) +
            (if(candidate.timestamp!=null && event.timestamp!=null && kotlin.math.abs(candidate.timestamp-event.timestamp)<=600_000) 25 else 0) +
            (if(candidate.channel!=Channel.UNKNOWN && candidate.channel==event.channel) 5 else 0) + similarity
    }

    fun match(event: Observation, accountId: String, candidates: List<MatchCandidate>): MatchDecision {
        val scoped = candidates.filter { it.accountId == accountId && it.currency == "INR" }
        val refs = scoped.filter { event.reference.isNotBlank() && it.reference == event.reference }
        if (refs.isNotEmpty()) {
            val compatible = refs.filter { it.amountMinor == event.amountMinor && it.direction == event.direction }
            if (compatible.size == 1 && refs.size == 1) return MatchDecision.Exact(compatible.single().id)
            return MatchDecision.Review(refs.map { it.id }, "Reference conflicts with amount, direction, or multiple records.")
        }
        val possible = scoped.filter {
            it.amountMinor == event.amountMinor && it.direction == event.direction &&
                kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it.date), LocalDate.parse(event.date))) <= 3
        }.sortedWith(compareByDescending<MatchCandidate> { score(event,it) }.thenBy { it.id })
        return if (possible.isEmpty()) MatchDecision.New else MatchDecision.Review(possible.map { it.id }, "Similar amount and date. Confirm the payment.")
    }
}
