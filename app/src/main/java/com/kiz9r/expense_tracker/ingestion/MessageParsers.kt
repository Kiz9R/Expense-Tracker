package com.kiz9r.expense_tracker.ingestion

import com.kiz9r.expense_tracker.domain.*
import java.time.*
import java.util.Locale

interface MessageParser {
    fun canParse(text: String): Boolean
    fun kind(text: String): EventKind
}
class MandateParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)mandate|autopay").containsMatchIn(text)
    override fun kind(text: String) = when {
        Regex("(?i)cancel|revok").containsMatchIn(text) -> EventKind.MANDATE_CANCELLED
        Regex("(?i)created|registered|approved|set up").containsMatchIn(text) -> EventKind.MANDATE_CREATED
        Regex("(?i)will be|scheduled|pre.debit|due on|to be debited").containsMatchIn(text) -> EventKind.UNKNOWN
        Regex("(?i)debited|executed|paid successfully").containsMatchIn(text) -> EventKind.MANDATE_EXECUTED
        else -> EventKind.UNKNOWN
    }
}
class FailedParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)failed|declined|unsuccessful").containsMatchIn(text)
    override fun kind(text: String) = EventKind.FAILED
}
class ReversalParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)reversed|reversal").containsMatchIn(text)
    override fun kind(text: String) = EventKind.REVERSAL
}
class RefundParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)refund").containsMatchIn(text)
    override fun kind(text: String) = EventKind.REFUND
}
class DebitParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)debited|withdrawn|spent|paid\\s+(?:to|at)|paid successfully|payment of").containsMatchIn(text)
    override fun kind(text: String) = EventKind.DEBIT
}
class CreditParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)credited|received\\s+(?:from|₹|Rs|INR)").containsMatchIn(text)
    override fun kind(text: String) = EventKind.CREDIT
}
object SbiSmsParser {
    private val parsers = listOf(FailedParser(),MandateParser(),ReversalParser(),RefundParser(),DebitParser(),CreditParser())
    fun isSbiSender(sender: String) = Regex("(?i)^(?:[A-Z0-9]{2}-)?(?:SBI[A-Z0-9]*)(?:-[A-Z])?$").matches(sender.trim())
    fun parse(sender: String, content: String, receivedAt: Long, messageTimestamp: Long = receivedAt): Observation? {
        if (!isSbiSender(sender)) return null
        return parseFinancial(Source.SBI_SMS,content,receivedAt,messageTimestamp,hash("sms|$sender|$messageTimestamp|$content"))
    }
    fun parseFinancial(source: Source, text: String, receivedAt: Long, timestamp: Long, identity: String): Observation? {
        if (Regex("(?i)\\bOTP\\b|one.time.password|verification code|\\bPIN\\b").containsMatchIn(text)) return null
        val parser = parsers.firstOrNull { it.canParse(text) }
        if (parser == null && !Regex("(?i)transaction|debit|credit|mandate|withdrawal").containsMatchIn(text)) return null
        if (Regex("(?i)offer|cashback offer|pre.approved|statement is ready").containsMatchIn(text)) return null
        val kind = parser?.kind(text) ?: EventKind.UNKNOWN
        val amount = Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)").find(text)?.groupValues?.get(1)?.let { runCatching { Money.parse(it) }.getOrNull() }
        val last4 = Regex("(?i)(?:a/c|acct?|account)(?:\\s*(?:no\\.?|number))?\\s*[:.\\-]?\\s*[Xx*•]*(\\d{4,18})").find(text)?.groupValues?.get(1)?.takeLast(4)
        val reference = (Regex("(?i)(?:UTR|ref(?:erence)?(?:\\s*(?:no\\.?|number))?)\\s*[:#./-]?\\s*([A-Z0-9]{6,35})\\b")
            .find(text)?.groupValues?.get(1)
            ?: Regex("(?i)\\bUPI/(\\d{8,35})\\b").find(text)?.groupValues?.get(1))
            ?.uppercase(Locale.ROOT).orEmpty()
        val dateToken = Regex("\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b").find(text)?.value
        val date = dateToken?.let { runCatching { Dates.parse(it) }.getOrNull() } ?: Dates.date(timestamp)
        val timeToken = Regex("\\b(\\d{2}:\\d{2}(?::\\d{2})?)\\b").find(text)?.value
        val actualTimestamp = timeToken?.let { runCatching { date.atTime(LocalTime.parse(it)).atZone(Dates.zone).toInstant().toEpochMilli() }.getOrNull() }
            ?: if (dateToken == null) timestamp else null
        val direction = when(kind) {
            EventKind.CREDIT,EventKind.REFUND,EventKind.REVERSAL -> Direction.CREDIT
            EventKind.DEBIT,EventKind.MANDATE_EXECUTED,EventKind.FAILED -> Direction.DEBIT
            else -> null
        }
        val merchant = Regex("(?i)(?:paid to|paid at|\\bto\\b|\\bat\\b|received from)\\s+([\\w@.'& /-]+?)(?=\\s+(?:on|via|ref|UPI|from|using)\\b|[.;]|$)").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return Observation(source,identity,receivedAt,actualTimestamp,date.toString(),last4,amount,direction,
            merchant.ifBlank { "SBI transaction" },reference,detectChannel(text),kind,maskAccounts(text))
    }
}
fun detectChannel(text: String): Channel = when {
    Regex("(?i)\\bATM\\b|withdraw").containsMatchIn(text) -> Channel.ATM
    Regex("(?i)\\bUPI\\b").containsMatchIn(text) -> Channel.UPI
    Regex("(?i)\\bIMPS\\b").containsMatchIn(text) -> Channel.IMPS
    Regex("(?i)\\bNEFT\\b").containsMatchIn(text) -> Channel.NEFT
    Regex("(?i)\\bRTGS\\b").containsMatchIn(text) -> Channel.RTGS
    Regex("(?i)\\bNACH\\b|mandate").containsMatchIn(text) -> Channel.NACH
    Regex("(?i)\\bECS\\b").containsMatchIn(text) -> Channel.ECS
    Regex("(?i)card|\\bPOS\\b").containsMatchIn(text) -> Channel.CARD
    Regex("(?i)interest").containsMatchIn(text) -> Channel.INTEREST
    Regex("(?i)charge|\\bfee\\b").containsMatchIn(text) -> Channel.BANK_FEE
    else -> Channel.UNKNOWN
}
interface NotificationParser {
    val packageName: String
    fun parse(text: String, key: String, timestamp: Long): Observation?
}
class PhonePeParser : NotificationParser {
    override val packageName = "com.phonepe.app"
    override fun parse(text: String, key: String, timestamp: Long) =
        SbiSmsParser.parseFinancial(Source.PHONEPE_NOTIFICATION,text,timestamp,timestamp,hash("phonepe|$key|$text"))?.copy(channel=Channel.UPI)
}
class GooglePayParser : NotificationParser {
    override val packageName = "com.google.android.apps.nbu.paisa.user"
    override fun parse(text: String, key: String, timestamp: Long) =
        SbiSmsParser.parseFinancial(Source.GPAY_NOTIFICATION,text,timestamp,timestamp,hash("gpay|$key|$text"))?.copy(channel=Channel.UPI)
}
