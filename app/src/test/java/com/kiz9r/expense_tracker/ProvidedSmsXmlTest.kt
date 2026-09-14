package com.kiz9r.expense_tracker

import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import org.junit.*
import org.junit.Assert.*
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/** Opt-in host-only validation. No SMS bodies, identifiers, or XML copies are written by this test. */
class ProvidedSmsXmlTest {
    @Test fun suppliedSbiMessagesMatchExpectedFinancialFamilies() {
        val path=System.getenv("SBI_SMS_FIXTURE_PATH")
        Assume.assumeTrue(!path.isNullOrBlank())
        val counts=sortedMapOf<String,Int>()
        val mismatches=sortedMapOf<String,Int>()
        var sbiCount=0
        fun check(family: String, condition: Boolean) {
            if(!condition) mismatches[family]=(mismatches[family] ?: 0)+1
        }
        val factory=SAXParserFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        }
        factory.newSAXParser().parse(File(requireNotNull(path)),object:DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                if(qName!="sms" || a.getValue("type")!="1") return
                val sender=a.getValue("address").orEmpty()
                if(!SbiSmsParser.isSbiSender(sender)) return
                sbiCount++
                val body=a.getValue("body").orEmpty()
                val received=a.getValue("date").toLong()
                val sent=a.getValue("date_sent")?.toLongOrNull()?.takeIf {it>0} ?: received
                val family=when {
                    body.contains("transaction number") && body.contains("SBI Debit Card") -> "card_debit"
                    body.contains("has credit for") -> "cbs_credit"
                    body.startsWith("Chq ") -> "cheque_debit"
                    body.contains("has a debit by NACH") -> "nach_debit"
                    body.contains("Deposit of Cash") -> "cash_credit"
                    body.contains("has a credit by Cheque") -> "cheque_credit"
                    body.contains("Mandate with UMRN") -> "umrn_created"
                    body.startsWith("Dear UPI user A/C") -> "upi_debit"
                    body.startsWith("Dear SBI User, your A/c") -> "upi_credit"
                    body.contains("through NEFT with UTR") -> "neft_credit"
                    body.contains("is credited by") && body.contains("linked to") -> "linked_imps_credit"
                    body.contains("UPI AutoPay") -> "autopay_reminder"
                    body.contains("Mandate") -> if(body.contains("collect request")) "mandate_request"
                        else if(Regex("(?i)cancelled|revoked").containsMatchIn(body)) "mandate_cancelled" else "mandate_created"
                    else -> "informational"
                }
                counts[family]=(counts[family] ?: 0)+1
                val parsed=SbiSmsParser.parse(sender,body,received,sent)
                if(family=="informational") {check(family,parsed==null);return}
                if(family in setOf("card_debit","cbs_credit","cheque_debit","nach_debit","cash_credit","cheque_credit","umrn_created")) {
                    check(family,parsed!=null)
                    if(parsed==null)return
                    check(family+".kind",parsed.kind==when(family) {
                        "card_debit","cheque_debit","nach_debit" -> EventKind.DEBIT
                        "umrn_created" -> EventKind.MANDATE_CREATED
                        else -> EventKind.CREDIT
                    })
                    check(family+".warning",parsed.parseWarning==null)
                    val amount=Regex("""(?:Rs\.?|INR)\s*([\d,.]+)""").find(body)?.groupValues?.get(1)
                    check(family+".amount",amount!=null && parsed.amountMinor==Money.parse(amount.trimEnd('.', ',')))
                    val acct=Regex("""(?i)(?:a/?c|account)\s*[Xx]*(\d{4,18})""").find(body)?.groupValues?.get(1)?.takeLast(4)
                    // Card numbers are never account identifiers.
                    check(family+".account",parsed.accountLast4==acct)
                    val token=Regex("""\b(?:\d{2}[A-Za-z]{3}\d{2}|\d{2}/\d{2}/\d{2,4})\b""").find(body)?.value
                    val date=token?.let { if(it.contains("/"))Dates.parse(it) else LocalDate.parse(it,DateTimeFormatter.ofPattern("ddMMMyy",Locale.ENGLISH)) }
                    check(family+".date",parsed.date==(date ?: Dates.date(sent)).toString())
                    val ref=Regex("""(?i)(?:transaction number|Chq|UMRN)\s+([A-Z0-9]+)""").find(body)?.groupValues?.get(1).orEmpty()
                    check(family+".reference",parsed.reference==ref.uppercase(Locale.ROOT))
                    val bal=Regex("""(?i)(?:Avl Bal|available balance is)\s*(?:Rs\.?|INR)\s*([\d,.]+)""").find(body)?.groupValues?.get(1)
                    check(family+".balance",parsed.balanceMinor==bal?.let { Money.parse(it.trimEnd('.', ',')) })
                    if(family=="card_debit") {
                        val merchant=Regex("""done at (.+?) on \d""").find(body)?.groupValues?.get(1)
                        check(family+".merchant",parsed.merchant==merchant)
                        check(family+".channel",parsed.channel==Channel.CARD)
                    }
                    if(family=="nach_debit" || family=="umrn_created")check(family+".channel",parsed.channel==Channel.NACH)
                    if(family=="cash_credit")check(family+".channel",parsed.channel==Channel.CASH)
                    return
                }
                check(family,parsed!=null)
                if(parsed==null)return
                val expected=when(family) {
                    "upi_debit" -> EventKind.DEBIT
                    "upi_credit","neft_credit","linked_imps_credit" -> EventKind.CREDIT
                    "mandate_created" -> EventKind.MANDATE_CREATED
                    "mandate_cancelled" -> EventKind.MANDATE_CANCELLED
                    else -> EventKind.UNKNOWN
                }
                check(family+".kind",parsed.kind==expected)
                if(expected in listOf(EventKind.DEBIT,EventKind.CREDIT)) {
                    check(family+".warning",parsed.parseWarning==null)
                    val amount=if(family=="upi_debit") Regex("""debited by ([\d.]+) on date""").find(body)?.groupValues?.get(1)
                        else Regex("""(?:Rs\.?|INR)\s*([\d,.]+)""").find(body)?.groupValues?.get(1)
                    check(family+".amount",amount!=null && parsed.amountMinor==Money.parse(amount.trimEnd('.', ',')))
                    val account=Regex("""(?i)(?:a/c|account)(?:\s*no[.:]?)?\s*[Xx]*(\d{4})""").find(body)?.groupValues?.get(1)
                    check(family+".account",account!=null && parsed.accountLast4==account)
                    val compact=Regex("""\b(\d{2}[A-Za-z]{3}\d{2})\b""").find(body)?.value
                    val date=if(compact!=null) LocalDate.parse(compact,DateTimeFormatter.ofPattern("ddMMMyy",Locale.ENGLISH))
                        else Regex("""\b\d{2}[-/]\d{2}[-/]\d{2,4}\b""").find(body)?.value?.let(Dates::parse)
                    check(family+".date",date!=null && parsed.date==date.toString())
                    val ref=Regex("""(?i)(?:Ref\s*no|Ref#|UTR)\s*([A-Z0-9]+)""").find(body)?.groupValues?.get(1)
                    check(family+".reference",ref!=null && parsed.reference==ref.uppercase(Locale.ROOT))
                    val expectedMerchant=when(family) {
                        "upi_debit" -> Regex("""trf to (.+?) Refno""").find(body)?.groupValues?.get(1)
                        "upi_credit" -> Regex("""transfer from (.+?) Ref No""").find(body)?.groupValues?.get(1)
                        "neft_credit" -> Regex("""with UTR \S+ by (.+?), INFO:""").find(body)?.groupValues?.get(1)
                        else -> Regex("""linked to (.+?)\s*\(IMPS Ref""").find(body)?.groupValues?.get(1)
                    }
                    check(family+".merchant",expectedMerchant!=null && parsed.merchant==expectedMerchant.trim())
                    check(family+".channel",parsed.channel==when(family) {
                        "neft_credit" -> Channel.NEFT
                        "linked_imps_credit" -> Channel.IMPS
                        else -> Channel.UPI
                    })
                }
                if(family=="mandate_created" || family=="mandate_cancelled") {
                    val umn=Regex("""UMN:(\S+)""").find(body)?.groupValues?.get(1)
                    if(umn!=null) check(family+".umn",parsed.reference==umn.uppercase(Locale.ROOT))
                    val amount=Regex("""(?:Rs\.?\s*|for )([\d.]+)""").find(body)?.groupValues?.get(1)
                    check(family+".amount",amount!=null && parsed.amountMinor==Money.parse(amount.trimEnd('.', ',')))
                    val account=Regex("""(?i)A/c No[.:]\s*[Xx]*(\d{4})""").find(body)?.groupValues?.get(1)
                    check(family+".account",parsed.accountLast4==account)
                    val merchant=Regex("""(?:towards |Revoked\s+by )(.+?) (?:for|from A/c)""").find(body)?.groupValues?.get(1)
                    check(family+".merchant",merchant!=null && parsed.merchant==merchant.trim())
                }
            }
        })
        println("Private XML audit: SBI messages="+sbiCount+"; families="+counts+"; mismatch counts="+mismatches)
        assertEquals("Unexpected SBI sample count",299,sbiCount)
        assertTrue("Private XML field/category mismatches: "+mismatches,mismatches.isEmpty())
    }
}
