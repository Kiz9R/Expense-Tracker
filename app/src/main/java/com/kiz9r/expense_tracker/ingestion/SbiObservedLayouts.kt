package com.kiz9r.expense_tracker.ingestion

/** Layout-specific fields validated against the supplied SBI export; fixtures contain invented values. */
internal object SbiObservedLayouts {
    private val debit = Regex("""(?i)^Dear UPI user A/C [X*•]*\d{4,18}\s+debited by ([\d,.]+)\s+on date\b""")
    private val cancellation = Regex("""(?i)^Your UPI-Mandate is successfully cancelled towards .+? for ([\d,.]+) from A/c\b""")
    fun bareAmounts(text: String): List<String> = listOfNotNull(
        debit.find(text)?.groupValues?.get(1), cancellation.find(text)?.groupValues?.get(1))

    fun merchant(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)\bSBI Debit Card [X*•]*\d+ done at (.+?) on \d"""),
            Regex("""(?i)\bhas credit for (.+?) of Rs"""),
            Regex("""(?i)\bMandate with UMRN \S+ for Rs[\d,. ]+ issued to (.+?)(?:[.]| In case|$)"""),
            Regex("""(?i)\btrf to (.+?)\s+Refno\b"""),
            Regex("""(?i)\btransfer from (.+?)\s+Ref\s*No\b"""),
            Regex("""(?i)\bby a/c linked to (.+?)\s*\((?:UPI|IMPS)\s*Ref"""),
            Regex("""(?i)\bthrough NEFT with UTR \S+ by (.+?),\s*INFO:"""),
            Regex("""(?i)\b(?:created|cancelled) towards (.+?)\s+(?:for|from A/c)\b"""),
            Regex("""(?i)\bsuccessfully Revoked\s+by (.+?)\s+for Rs"""),
            Regex("""(?i)\bcollect request from (.+?)\s+for Rs"""),
            Regex("""(?i)\bUPI AutoPay for (.+?)\s+debit of Rs""")
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.trim() }
    }

    fun isUpi(text: String): Boolean = debit.containsMatchIn(text) ||
        Regex("""(?i)^Dear SBI User, your A/c [X*•]*\d{4,18}-credited by Rs""").containsMatchIn(text)
    fun isCardDebit(text: String): Boolean =
        Regex("""(?i)\btransaction number \d+ for Rs[\d,. ]+ by SBI Debit Card [X*•]*\d+ done at """).containsMatchIn(text)

    fun informational(text: String): Boolean {
        // Do not discard a posted movement because its merchant contains a promotional word.
        if(Regex("""(?i)\bdebited\b|\bcredited\b|\bwithdrawn\b|\bspent\b|\brefunded\b|\breversed\b|mandate|autopay""").containsMatchIn(text)) return false
        return Regex("""(?i)credit card|choose SBI World Debit Card|you can withdraw cash|reward points|revoke the consent|you have permitted|invalid login attempts|free transactions|not active for|aadhaar (?:seeding|has been seeded)""").containsMatchIn(text)
    }
}
