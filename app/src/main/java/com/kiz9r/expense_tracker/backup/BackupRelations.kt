package com.kiz9r.expense_tracker.backup

import com.google.gson.Gson
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.balanceWarnings
import java.time.LocalDate

/** Cross-record checks for records whose ownership is not enforced by Room foreign keys. */
internal fun validateBackupRelations(s: BackupSnapshot) {
    require(s.createdAt>0)
    val txs=s.transactions.associateBy { it.id }
    val accounts=s.accounts.associateBy { it.id }
    val events=s.events.associateBy { it.id }
    val evidence=s.evidence.groupBy { it.transactionId }
    val observations=mutableMapOf<String,Observation>()
    s.accounts.forEach { require(it.nickname.isNotBlank() && it.nickname.length<=80 && it.accountType.isNotBlank() && it.createdAt>=0) }
    require(s.categories.all {it.name.isNotBlank()}); require(s.tags.all {it.name.isNotBlank()})
    s.transactions.forEach { tx ->
        requireNotNull(tx.channel)
        require(tx.createdAt>=0 && tx.updatedAt>=0 && (tx.timestamp==null || tx.timestamp>=0))
        require(tx.kind in if(tx.direction==Direction.CREDIT) listOf(EventKind.CREDIT,EventKind.REFUND,EventKind.REVERSAL)
            else listOf(EventKind.DEBIT,EventKind.FAILED,EventKind.MANDATE_EXECUTED))
        if(tx.verification==Verification.VERIFIED) require(evidence[tx.id].orEmpty().any {it.verified && it.source==Source.SBI_STATEMENT})
    }
    val secret=Regex("(?i)\\bOTP\\b|one.time.password|verification code|\\bPIN\\b")
    s.events.forEach { raw ->
        requireNotNull(raw.source); require(raw.receivedAt>=0 && raw.parserVersion.isNotBlank())
        require(!secret.containsMatchIn(raw.content)) { "Credential messages cannot be restored." }
        val obs=runCatching { Gson().fromJson(raw.parsedJson,Observation::class.java) }.getOrNull()
        if(obs==null) {
            // Existing unreadable observations can still be backed up as quarantined review items.
            require(raw.processed && !raw.reviewReason.isNullOrBlank() && s.evidence.none {it.eventId==raw.id} && s.mandates.none {it.eventId==raw.id})
        } else {
            require(obs.source==raw.source && obs.identity==raw.identity && obs.receivedAt==raw.receivedAt)
            requireNotNull(obs.kind);requireNotNull(obs.channel);requireNotNull(obs.reference);requireNotNull(obs.merchant)
            require(obs.content==raw.content && !secret.containsMatchIn(obs.content))
            LocalDate.parse(obs.date)
            require(obs.accountLast4==null || obs.accountLast4.matches(Regex("\\d{4}")))
            require(obs.timestamp==null || obs.timestamp>=0)
            // UNKNOWN can preserve an invalid/nonpositive amount for explicit review.
            if(obs.kind in listOf(EventKind.DEBIT,EventKind.CREDIT,EventKind.REFUND,EventKind.REVERSAL,EventKind.MANDATE_EXECUTED,EventKind.FAILED)) {
                require(obs.amountMinor!=null && obs.amountMinor>0)
                require(obs.direction==if(obs.kind in listOf(EventKind.CREDIT,EventKind.REFUND,EventKind.REVERSAL))Direction.CREDIT else Direction.DEBIT)
            }
            observations[raw.id]=obs
        }
    }
    s.evidence.forEach { ev ->
        val raw=events.getValue(ev.eventId); val tx=txs.getValue(ev.transactionId)
        val obs=requireNotNull(observations[ev.eventId])
        require(ev.source==raw.source && ev.method.isNotBlank() && raw.processed && raw.reviewReason==null)
        require(obs.amountMinor==tx.amountMinor && obs.direction==tx.direction)
        require(obs.accountLast4==null || obs.accountLast4==accounts.getValue(tx.accountId).last4)
        require(obs.reference.isBlank() || tx.reference.isBlank() || obs.reference==tx.reference)
        if(ev.verified) require(ev.source==Source.SBI_STATEMENT && tx.verification==Verification.VERIFIED && obs.date==tx.date)
    }
    s.merchantRules.forEach { require(it.matchType in listOf("exact","contains","startsWith") && it.matchValue.isNotBlank()) }
    s.mandates.forEach { m ->
        require(m.status in listOf("ACTIVE","CANCELLED") && (m.amountMinor==null || m.amountMinor>0))
        val obs=requireNotNull(observations[m.eventId])
        require(obs.kind in listOf(EventKind.MANDATE_CREATED,EventKind.MANDATE_CANCELLED))
        require(m.status==if(obs.kind==EventKind.MANDATE_CREATED)"ACTIVE" else "CANCELLED")
        require(m.accountId==null || obs.accountLast4==null || accounts.getValue(m.accountId).last4==obs.accountLast4)
    }
    val rowsByImport=s.statementRows.groupBy {it.importId}
    val decisionKeys=s.events.map {it.identity}.toMutableSet()
    s.statementImports.forEach { imported ->
        require(imported.status in listOf("RECONCILED","EXCEPTIONS") && imported.importedAt>=0 && imported.parserVersion.isNotBlank())
        val rows=rowsByImport[imported.id].orEmpty().sortedBy {it.sequence}
        require(rows.mapNotNull {it.transactionId}.distinct().size==rows.count {it.transactionId!=null})
        rows.forEach { row ->
            requireNotNull(row.direction);require(row.fingerprint.isNotBlank())
            val key="statement:"+imported.logicalFingerprint+":"+row.sequence
            decisionKeys.add(key)
            if(!row.ignored) require(evidence[row.transactionId].orEmpty().any {it.verified && events[it.eventId]?.identity==key})
        }
        if(imported.status=="RECONCILED") {
            val statement=ParsedStatement(accounts.getValue(imported.accountId).last4,imported.startDate,imported.endDate,
                imported.openingBalance,imported.closingBalance,rows.map { ParsedRow(it.sequence,it.date,it.valueDate,it.narration,it.reference,it.amountMinor,it.direction,it.balance) },
                imported.warningsJson?.let { Gson().fromJson(it,Array<String>::class.java).toList() }.orEmpty())
            require(rows.none {it.ignored} && statement.balanceWarnings().isEmpty()) { "Reconciled statement contains unresolved exceptions." }
        }
    }
    val evidenceTargets=s.evidence.associate {events.getValue(it.eventId).identity to it.transactionId}
    s.reviewDecisions.forEach { d ->
        require(d.observationKey.substringBefore(":revision:") in decisionKeys && d.decidedAt>=0)
        if(d.transactionId!=null) require(evidenceTargets[d.observationKey.substringBefore(":revision:")]==d.transactionId) { "Review target conflicts with retained evidence." }
        if(d.action=="ignore") require(d.transactionId==null)
        if(d.action=="match") require(d.transactionId!=null)
    }
    s.settings.forEach { setting ->
        require(setting.key in setOf("sms","notifications","app_lock","screenshots","sms_last_received")) { "Unsupported backup setting." }
        if(setting.key=="sms_last_received") require(setting.value.toLongOrNull()?.let {it>=0}==true)
        else require(setting.value in listOf("true","false"))
    }
}
