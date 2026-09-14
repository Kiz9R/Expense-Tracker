package com.kiz9r.expense_tracker.backup

import com.kiz9r.expense_tracker.domain.*
import java.time.LocalDate

fun validateBackup(s: BackupSnapshot) {
    try { validateContents(s) }
    catch(e: Exception) { throw IllegalArgumentException("Backup records are inconsistent or unsupported. Existing data was not changed.",e) }
}
private fun validateContents(s: BackupSnapshot) {
    require(s.version in 1..2) { "Unsupported backup version." }
    fun unique(values: List<String>) { require(values.none { it.isBlank() } && values.distinct().size==values.size) { "Duplicate or missing record identities." } }
    unique(s.accounts.map { it.id }); unique(s.categories.map { it.id }); unique(s.categories.map { it.name })
    unique(s.transactions.map { it.id }); unique(s.metadata.map { it.transactionId })
    unique(s.events.map { it.id }); unique(s.events.map { it.identity })
    unique(s.evidence.map { it.id }); unique(s.evidence.map { it.eventId })
    unique(s.tags.map { it.id }); unique(s.tags.map { it.name })
    unique(s.merchantRules.map { it.id }); unique(s.statementImports.map { it.id })
    unique(s.statementImports.map { it.fileHash }); unique(s.statementImports.map { it.logicalFingerprint })
    unique(s.statementRows.map { it.id }); unique(s.reviewDecisions.map { it.id })
    unique(s.reviewDecisions.map { it.observationKey }); unique(s.mandates.map { it.id })
    unique(s.refundLinks.map { it.refundId }); unique(s.settings.map { it.key })
    unique(s.transactionTags.map { it.transactionId+"|"+it.tagId })
    val accounts = s.accounts.map { it.id }.toSet()
    val transactions = s.transactions.associateBy { it.id }
    val events = s.events.map { it.id }.toSet()
    val categories = s.categories.map { it.id }.toSet()
    val tags = s.tags.map { it.id }.toSet()
    val imports = s.statementImports.map { it.id }.toSet()
    require(s.accounts.all { it.last4.matches(Regex("\\d{4}")) && it.currency=="INR" && it.bankName=="SBI" })
    s.transactions.forEach {
        require(it.accountId in accounts && it.amountMinor>0 && it.currency=="INR")
        requireNotNull(it.direction); requireNotNull(it.verification); requireNotNull(it.kind); requireNotNull(it.outcome)
        LocalDate.parse(it.date); it.valueDate?.let(LocalDate::parse)
    }
    require(s.metadata.all { it.transactionId in transactions && (it.categoryId==null || it.categoryId in categories) })
    require(s.evidence.all { it.transactionId in transactions && it.eventId in events })
    require(s.transactionTags.all { it.transactionId in transactions && it.tagId in tags })
    require(s.merchantRules.all { it.categoryId==null || it.categoryId in categories })
    require(s.statementImports.all { it.accountId in accounts && it.transactionCount>=0 })
    require(s.statementRows.all { it.importId in imports && it.amountMinor>0 && (it.transactionId==null || it.transactionId in transactions) })
    require(s.mandates.all { (it.accountId==null || it.accountId in accounts) && it.eventId in events })
    s.refundLinks.forEach {
        val debit = requireNotNull(transactions[it.originalId]); val credit = requireNotNull(transactions[it.refundId])
        require(debit.direction==Direction.DEBIT && credit.direction==Direction.CREDIT &&
            debit.accountId==credit.accountId && it.amountMinor==credit.amountMinor && it.amountMinor>0)
    }
    s.refundLinks.groupBy { it.originalId }.forEach { (id,links) ->
        require(links.fold(0L) { total,link -> Math.addExact(total,link.amountMinor) } <= transactions.getValue(id).amountMinor)
    }
    require(s.reviewDecisions.all { it.action in listOf("new","match","ignore") &&
        (it.transactionId==null || it.transactionId in transactions) }) { "Invalid review decision target." }
    val rowsByImport=s.statementRows.groupBy {it.importId}
    s.statementImports.forEach { imported ->
        val from=LocalDate.parse(imported.startDate)
        val until=LocalDate.parse(imported.endDate)
        require(!until.isBefore(from))
        imported.warningsJson?.let { json ->
            val array=com.google.gson.JsonParser.parseString(json)
            require(array.isJsonArray && array.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) {
                "Invalid reconciliation warnings."
            }
        }
        val rows = rowsByImport[imported.id].orEmpty()
        require(rows.size==imported.transactionCount && rows.map { it.sequence }.sorted()==rows.indices.toList())
        rows.forEach { row ->
            val date=LocalDate.parse(row.date)
            require(!date.isBefore(from) && !date.isAfter(until))
            row.valueDate?.let(LocalDate::parse)
            require(row.ignored == (row.transactionId==null))
            row.transactionId?.let { id ->
                val tx=transactions.getValue(id)
                require(tx.accountId==imported.accountId && tx.amountMinor==row.amountMinor && tx.direction==row.direction && tx.date==row.date)
            }
        }
    }
    validateBackupRelations(s)
    require(s.events.none { Regex("(?i)\\bOTP\\b|one.time.password|verification code").containsMatchIn(it.content) })
}
