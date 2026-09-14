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
        Regex("(?i)created|registered|approved|set up|issued to").containsMatchIn(text) -> EventKind.MANDATE_CREATED
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
    override fun kind(text: String) = if(Regex("(?i)reversed|credited").containsMatchIn(text)) EventKind.REVERSAL else EventKind.UNKNOWN
}
class RefundParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)refund").containsMatchIn(text)
    override fun kind(text: String) = if(Regex("(?i)refunded|credited|received").containsMatchIn(text)) EventKind.REFUND else EventKind.UNKNOWN
}
class DebitParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)debited|has a debit by|withdrawn|spent|paid\\s+(?:to|at)|paid successfully|payment of").containsMatchIn(text) || SbiObservedLayouts.isCardDebit(text)
    override fun kind(text: String) = EventKind.DEBIT
}
class CreditParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)credited|has (?:a )?credit (?:for|by)|received\\s+(?:from|₹|Rs|INR)").containsMatchIn(text)
    override fun kind(text: String) = EventKind.CREDIT
}
class PendingParser : MessageParser {
    override fun canParse(text: String) = Regex("(?i)pending|processing|initiated|will be|to be (?:debited|credited|reversed|refunded)|scheduled|refund requested|refund initiated|reversal requested").containsMatchIn(text)
    override fun kind(text: String) = EventKind.UNKNOWN
}
object SbiSmsParser {
    const val VERSION = "2.1.0"
    private val parsers = listOf(FailedParser(),PendingParser(),MandateParser(),ReversalParser(),RefundParser(),DebitParser(),CreditParser())
    fun isSbiSender(sender: String) = Regex("(?i)^(?:[A-Z0-9]{2}-)?(?:SBI[A-Z0-9]*|ATMSBI|CBSSBI)(?:-[A-Z])?$").matches(sender.trim())
    fun parse(sender: String, content: String, receivedAt: Long, messageTimestamp: Long = receivedAt): Observation? {
        if (!isSbiSender(sender)) return null
        return parseFinancial(Source.SBI_SMS,content,receivedAt,messageTimestamp,hash("sms|"+sender.trim().uppercase(Locale.ROOT)+"|$messageTimestamp|$content"))
    }
    fun parseFinancial(source: Source, text: String, receivedAt: Long, timestamp: Long, identity: String): Observation? {
        if (text.isBlank() || text.length > 16384) return null
        if (SbiObservedLayouts.informational(text)) return null
        if (Regex("(?i)\\bOTP\\b|one.time.password|verification code|\\bPIN\\b").containsMatchIn(text)) return null
        if (Regex("(?i)\\boffer\\b|pre.approved|statement is ready|apply now|click.*(?:loan|offer)").containsMatchIn(text)) return null
        val parser = parsers.firstOrNull { it.canParse(text) }
        if (parser == null && !Regex("(?i)transaction|debit|credit|mandate|withdrawal|refund|reversal").containsMatchIn(text)) return null
        var kind = parser?.kind(text) ?: EventKind.UNKNOWN
        val movement = kind !in listOf(EventKind.MANDATE_CREATED,EventKind.MANDATE_CANCELLED)
        val fields = SmsFields.extract(text,timestamp,movement)
        var warning = fields.warning
        if(kind == EventKind.UNKNOWN && warning == null) warning = "Payment is pending, scheduled, or its financial state is not recognized."
        if(kind in listOf(EventKind.DEBIT,EventKind.CREDIT) &&
            Regex("(?i)\\bdebited\\b").containsMatchIn(text) && Regex("(?i)\\bcredited\\b").containsMatchIn(text)) {
            warning = "Both debit and credit movements appear in this message."
        }
        if(warning != null) kind = EventKind.UNKNOWN
        val direction = when(kind) {
            EventKind.CREDIT,EventKind.REFUND,EventKind.REVERSAL -> Direction.CREDIT
            EventKind.DEBIT,EventKind.MANDATE_EXECUTED,EventKind.FAILED -> Direction.DEBIT
            else -> null
        }
        return Observation(source,identity,receivedAt,fields.timestamp,fields.date,fields.last4,fields.amount,direction,
            fields.merchant,fields.reference,detectChannel(text),kind,maskAccounts(text),
            parserVersion=VERSION,parseWarning=warning,balanceMinor=fields.balance)
    }
}
fun detectChannel(text: String): Channel = when {
    Regex("(?i)deposit of cash").containsMatchIn(text) -> Channel.CASH
    Regex("(?i)\\bATM\\b|withdraw").containsMatchIn(text) -> Channel.ATM
    Regex("(?i)\\bUPI\\b|\\(UPI\\s*Ref").containsMatchIn(text) || SbiObservedLayouts.isUpi(text) -> Channel.UPI
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
