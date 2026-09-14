package com.kiz9r.expense_tracker.reconciliation

import androidx.room.withTransaction
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RowPreview(val row: ParsedRow, val decision: MatchDecision, val candidates: List<MatchCandidate>)
data class ImportPreview(val accountId: String, val fileName: String, val fileHash: String,
    val statement: ParsedStatement, val rows: List<RowPreview>, val duplicate: Boolean = false) {
    fun token(gson: Gson): String = hash(gson.toJson(listOf(accountId,fileHash,statement,rows)))
}
data class Resolution(val action: String, val transactionId: String? = null)

@Singleton
class ReconciliationRepository @Inject constructor(private val ledger: LedgerRepository, private val gson: Gson) {
    private val db get() = ledger.db
    private val dao get() = db.ledger()
    private val matcher = MatchingEngine()
    fun previewToken(preview: ImportPreview): String = preview.token(gson)
    suspend fun enqueue(observation: Observation) = db.withTransaction {
        if (observation.kind != EventKind.IGNORE) dao.insertEvent(ledger.raw(observation))
    }
    suspend fun enqueueNotification(observation: Observation): Boolean = db.withTransaction {
        require(observation.source in listOf(Source.PHONEPE_NOTIFICATION,Source.GPAY_NOTIFICATION))
        if(dao.setting("notifications")!="true" || observation.kind==EventKind.IGNORE) return@withTransaction false
        dao.insertEvent(ledger.raw(observation))
        true
    }
    suspend fun processPending() {
        while (true) {
            val batch = dao.pendingEvents()
            if (batch.isEmpty()) break
            batch.forEach { raw -> db.withTransaction {
                val current = dao.event(raw.id) ?: return@withTransaction
                if (!current.processed) process(current)
            } }
        }
    }
    private suspend fun process(raw: RawEventEntity) {
        val observation = observation(raw)
        if(observation == null) {
            dao.saveEvent(raw.copy(processed=true,reviewReason="Stored observation could not be read. Ignore it and add a manual entry if needed."))
            return
        }
        if(observation.parseWarning != null) {
            dao.saveEvent(raw.copy(processed=true,reviewReason=observation.parseWarning))
            return
        }
        val accounts = dao.allAccounts().filter { it.last4 == observation.accountLast4 && it.active }
        if (accounts.size != 1) {
            dao.saveEvent(raw.copy(processed=true,reviewReason="Choose the SBI account for this observation."))
            return
        }
        val accountId = accounts.single().id
        if (observation.kind in listOf(EventKind.MANDATE_CREATED,EventKind.MANDATE_CANCELLED)) {
            if(observation.reference.isNotBlank() && dao.findMandates(observation.reference,accountId).size>1) {
                dao.saveEvent(raw.copy(processed=true,reviewReason="Multiple mandates share this reference.")); return
            }
            recordMandate(raw,observation,accountId)
            return
        }
        if (observation.amountMinor == null || observation.amountMinor <= 0 || observation.direction == null || observation.kind == EventKind.UNKNOWN) {
            dao.saveEvent(raw.copy(processed=true,reviewReason="Unrecognized financial format. Preserve for review; no spending recorded.")); return
        }
        val candidates = candidates(observation,accountId)
        when(val decision = matcher.match(observation,accountId,candidates.map(::candidate))) {
            is MatchDecision.Exact -> attach(raw,observation,accountId,decision.transactionId,false,"exact-reference")
            is MatchDecision.New -> attach(raw,observation,accountId,null,false,"new-observation")
            is MatchDecision.Review -> dao.saveEvent(raw.copy(processed=true,reviewReason=decision.reason))
        }
    }
    fun observation(raw: RawEventEntity): Observation? = runCatching {
        gson.fromJson(raw.parsedJson,Observation::class.java).also {
            requireNotNull(it.kind); requireNotNull(it.source); LocalDate.parse(it.date)
        }
    }.getOrNull()
    suspend fun reviewCandidates(eventId: String, accountId: String): List<MatchCandidate> {
        val event = requireNotNull(dao.event(eventId))
        val obs = observation(event) ?: return emptyList()
        if(obs.kind == EventKind.UNKNOWN || obs.parseWarning != null) return emptyList()
        return candidates(obs,accountId).filter { it.amountMinor==obs.amountMinor && it.direction==obs.direction &&
            (it.reference.isBlank() || obs.reference.isBlank() || it.reference==obs.reference) }
            .map { candidateWithEvidence(it) }.sortedByDescending { matcher.score(obs,it) }
    }
    suspend fun resolveEvent(eventId: String, accountId: String, resolution: Resolution) = db.withTransaction {
        val raw = requireNotNull(dao.event(eventId))
        require(raw.reviewReason != null) { "This observation has already been resolved." }
        require(resolution.action in listOf("new","match","ignore") &&
            ((resolution.action=="match") == (resolution.transactionId!=null))) { "Choose a valid resolution." }
        var transactionId: String? = null
        if (resolution.action == "ignore") {
            dao.saveEvent(raw.copy(processed=true,reviewReason=null))
        } else {
            val observation = requireNotNull(observation(raw)) { "This observation cannot be read. Ignore it and add a manual entry." }
            val account = requireNotNull(dao.allAccounts().find { it.id==accountId && it.active }) { "Choose an account." }
            require(observation.accountLast4 == null || observation.accountLast4 == account.last4) { "Account digits do not match this message." }
            require(observation.kind != EventKind.UNKNOWN && observation.parseWarning == null) {
                "Uncertain financial facts cannot be converted automatically. Add a manual entry, then ignore this observation."
            }
            if (observation.kind in listOf(EventKind.MANDATE_CREATED,EventKind.MANDATE_CANCELLED)) {
                require(resolution.action=="new") { "A mandate is separate from a transaction." }
                recordMandate(raw,observation,accountId)
            } else {
                transactionId = attach(raw,observation,accountId,resolution.transactionId,false,"user-confirmed")
            }
        }
        dao.saveDecision(ReviewDecisionEntity(observationKey=raw.identity,action=resolution.action,transactionId=transactionId))
    }
    private suspend fun recordMandate(raw: RawEventEntity, observation: Observation, accountId: String) {
        val existing = if(observation.reference.isBlank()) emptyList() else dao.findMandates(observation.reference,accountId)
        require(existing.size<=1) { "Multiple mandates share this reference. Keep this observation for review." }
        val previous=existing.singleOrNull()
        val previousEvent=previous?.let { dao.event(it.eventId) }
        val previousTime=previousEvent?.let { this.observation(it)?.timestamp ?: it.receivedAt } ?: Long.MIN_VALUE
        if((observation.timestamp ?: raw.receivedAt)>=previousTime) {
            dao.saveMandate(MandateEntity(id=previous?.id ?: newId(),accountId=accountId,
                merchant=observation.merchant,amountMinor=observation.amountMinor ?: previous?.amountMinor,reference=observation.reference,
                status=if(observation.kind==EventKind.MANDATE_CREATED) "ACTIVE" else "CANCELLED",eventId=raw.id))
        }
        dao.saveEvent(raw.copy(processed=true,reviewReason=null))
    }
    suspend fun preview(accountId: String, fileName: String, fileHash: String, statement: ParsedStatement): ImportPreview = db.withTransaction {
        statement.validateForImport()
        val account = requireNotNull(dao.allAccounts().find { it.id == accountId })
        require(account.last4 == statement.last4) { "Statement account does not match the selected account." }
        if (dao.duplicateImport(fileHash,statement.fingerprint(accountId)) != null)
            return@withTransaction ImportPreview(accountId,fileName,fileHash,statement,emptyList(),true)
        val used = mutableSetOf<String>()
        val results = statement.rows.map { row ->
            val event = row.observation("preview",account.last4)
            val options = candidates(event,accountId).filter { it.id !in used }.map { candidateWithEvidence(it) }
            val previousRows = dao.matchingRows(row.fingerprint(accountId))
            val matching = previousRows.filter { !it.ignored && it.transactionId != null }
                .mapNotNull { it.transactionId }.distinct().filter { it !in used }
            val decision = if(matching.size == 1) MatchDecision.Exact(matching.single())
                else if(previousRows.any {it.ignored}) MatchDecision.Review(emptyList(),"This row was ignored in an earlier import. Review it explicitly.")
                else matcher.match(event,accountId,options)
            if (decision is MatchDecision.Exact) used += decision.transactionId
            RowPreview(row,decision,options.sortedWith(compareByDescending<MatchCandidate> { matcher.score(event,it) }.thenBy { it.id }))
        }
        ImportPreview(accountId,fileName,fileHash,statement,results)
    }
    suspend fun commit(preview: ImportPreview, resolutions: Map<Int,Resolution>, jobId: String? = null): String = db.withTransaction {
        if (jobId != null) {
            val job = requireNotNull(dao.job(jobId)) { "Import was cancelled or already completed." }
            require(job.fileHash == preview.fileHash && job.accountId == preview.accountId && job.status == "READY") { "Import job no longer matches this preview." }
        }
        require(!preview.duplicate) { "Statement already imported." }
        val statement = preview.statement
        val account = requireNotNull(dao.allAccounts().find { it.id == preview.accountId })
        require(account.last4 == statement.last4)
        require(dao.duplicateImport(preview.fileHash,statement.fingerprint(account.id)) == null) { "Statement already imported. No changes made." }
        // Rebuild every decision under the same write transaction; preview state is never trusted at commit.
        val current = preview(account.id,preview.fileName,preview.fileHash,statement)
        require(current.rows.size == preview.rows.size && current.rows.zip(preview.rows).all { (now,before) ->
            now.row == before.row &&
            now.decision==before.decision && now.candidates.toSet()==before.candidates.toSet()
        }) { "The ledger changed after this preview. Refresh matches before committing." }
        require(resolutions.keys.all { key -> current.rows.any { it.row.sequence == key } }) { "Unknown statement row decision." }
        val used = mutableSetOf<String>()
        val decisions = current.rows.map { row ->
            val explicit = resolutions[row.row.sequence]
            val resolution = explicit ?: when(val match = row.decision) {
                is MatchDecision.Exact -> Resolution("match",match.transactionId)
                is MatchDecision.New -> Resolution("new")
                is MatchDecision.Review -> error("New or unresolved ambiguity. Refresh the preview and review all rows.")
            }
            require(resolution.action in listOf("match","new","ignore"))
            require((resolution.action == "match") == (resolution.transactionId != null)) { "Invalid resolution target." }
            if (resolution.action == "match") {
                require(resolution.transactionId != null && used.add(resolution.transactionId)) { "Two statement rows cannot use the same transaction." }
                validateTarget(requireNotNull(dao.transaction(resolution.transactionId)),row.row.observation("",account.last4),account.id)
            }
            row.row to resolution
        }
        val imported = StatementImportEntity(accountId=account.id,fileName=preview.fileName,fileHash=preview.fileHash,
            logicalFingerprint=statement.fingerprint(account.id),startDate=statement.start,endDate=statement.end,
            openingBalance=statement.opening,closingBalance=statement.closing,transactionCount=statement.rows.size,
            status=if(statement.balanceWarnings().isEmpty() && decisions.none { it.second.action == "ignore" }) "RECONCILED" else "EXCEPTIONS",
            parserVersion=statement.parserVersion ?: "1.0.0",warningsJson=gson.toJson(statement.balanceWarnings()))
        dao.saveImport(imported)
        decisions.forEach { (row,resolution) ->
            val identity = "statement:" + imported.logicalFingerprint + ":" + row.sequence
            var txId: String? = null
            if (resolution.action != "ignore") {
                val obs = row.observation(identity,account.last4).copy(parserVersion=statement.parserVersion ?: "1.0.0")
                val raw = ledger.raw(obs)
                dao.insertEvent(raw)
                txId = attach(raw,obs,account.id,resolution.transactionId,true,"statement-" + resolution.action,row.valueDate)
            }
            dao.saveRow(StatementRowEntity(importId=imported.id,sequence=row.sequence,fingerprint=row.fingerprint(account.id),
                date=row.date,valueDate=row.valueDate,narration=row.narration,reference=row.reference,
                amountMinor=row.amountMinor,direction=row.direction,balance=row.balance,transactionId=txId,ignored=resolution.action=="ignore"))
            dao.saveDecision(ReviewDecisionEntity(observationKey=identity,action=resolution.action,transactionId=txId))
        }
        if(jobId != null) dao.deleteJob(jobId)
        imported.id
    }
    suspend fun report(importId: String): StatementReport = db.withTransaction {
        val imported = requireNotNull(dao.statementImport(importId)) { "Statement no longer exists." }
        val rows = dao.importRows(importId)
        val last4 = requireNotNull(dao.allAccounts().find { it.id == imported.accountId }).last4
        val warnings = imported.warningsJson?.let { gson.fromJson(it, Array<String>::class.java).toList() }.orEmpty()
        val statement = ParsedStatement(last4, imported.startDate, imported.endDate, imported.openingBalance,
            imported.closingBalance, rows.map { it.parsed() }, warnings)
        val histories = rows.associate { row ->
            val key = "statement:" + imported.logicalFingerprint + ":" + row.sequence
            row.sequence to dao.decisionHistory(key, "$key:revision:%")
        }
        val actions = rows.map { row ->
            if(row.ignored) "ignore" else histories.getValue(row.sequence).lastOrNull()?.action
                ?: if(row.transactionId != null) "match" else null
        }
        val linked = rows.mapNotNull { row -> row.transactionId?.let { dao.transaction(it) }?.let { tx ->
            row.parsed().copy(amountMinor=tx.amountMinor,direction=tx.direction)
        } }
        StatementReport(imported,rows,statementSummary(statement,actions,linked),histories.values.flatten())
    }

    suspend fun ignoredRowCandidates(rowId: String): List<MatchCandidate> = db.withTransaction {
        val row = requireNotNull(dao.statementRow(rowId))
        require(row.ignored) { "This row is already resolved." }
        val imported = requireNotNull(dao.statementImport(row.importId))
        val account = requireNotNull(dao.allAccounts().find { it.id == imported.accountId })
        val used = dao.importRows(row.importId).mapNotNull { it.transactionId }.toSet()
        val obs = row.parsed().observation("",account.last4)
        candidates(obs,account.id).filter { it.id !in used }.map { candidateWithEvidence(it) }
            .sortedWith(compareByDescending<MatchCandidate> { matcher.score(obs,it) }.thenBy { it.id })
    }

    /** Ignoring is reversible; official facts and the initial ignore decision remain unchanged. */
    suspend fun resolveIgnoredRow(rowId: String, resolution: Resolution) = db.withTransaction {
        val row = requireNotNull(dao.statementRow(rowId))
        require(row.ignored && row.transactionId == null) { "This row was already resolved. Refresh the statement." }
        require(resolution.action in listOf("match","new") &&
            ((resolution.action=="match") == (resolution.transactionId!=null))) { "Choose a match or a new transaction." }
        val imported = requireNotNull(dao.statementImport(row.importId))
        val account = requireNotNull(dao.allAccounts().find { it.id == imported.accountId })
        require(dao.importRows(row.importId).none { resolution.transactionId!=null && it.transactionId==resolution.transactionId }) {
            "Another row in this statement already uses that transaction."
        }
        val identity = "statement:" + imported.logicalFingerprint + ":" + row.sequence
        val obs = row.parsed().observation(identity,account.last4).copy(parserVersion=imported.parserVersion)
        val raw = ledger.raw(obs)
        require(dao.eventByIdentity(identity)==null) { "This row already has evidence. Refresh before retrying." }
        dao.insertEvent(raw)
        val txId = attach(raw,obs,account.id,resolution.transactionId,true,"statement-" + resolution.action,row.valueDate)
        dao.saveRow(row.copy(transactionId=txId,ignored=false))
        val previousTime=dao.decisionHistory(identity,identity+":revision:%").maxOfOrNull {it.decidedAt} ?: 0L
        dao.saveDecision(ReviewDecisionEntity(observationKey=identity+":revision:"+newId(),
            action=resolution.action,transactionId=txId,reason="Resolved previously ignored statement row.",
            decidedAt=maxOf(System.currentTimeMillis(),Math.addExact(previousTime,1L))))
        val remaining = dao.importRows(imported.id)
        val warnings = report(imported.id).summary.warnings
        dao.saveImport(imported.copy(status=if(warnings.isEmpty() && remaining.none { it.ignored }) "RECONCILED" else "EXCEPTIONS",
            warningsJson=gson.toJson(warnings)))
    }

    private suspend fun candidateWithEvidence(tx: TransactionEntity) = candidate(tx).copy(
        sources=dao.evidence(tx.id).map { it.source }.distinct().sortedBy { it.name })

    private suspend fun candidates(obs: Observation, accountId: String): List<TransactionEntity> {
        val date = LocalDate.parse(obs.date)
        return dao.candidates(accountId,obs.reference,date.minusDays(3).toString(),date.plusDays(3).toString())
    }
    private fun validateTarget(tx: TransactionEntity, obs: Observation, accountId: String) {
        require(tx.accountId == accountId && tx.currency == "INR" && tx.amountMinor == obs.amountMinor && tx.direction == obs.direction) {
            "The match conflicts with account, amount, or direction. Create a separate transaction."
        }
        require(tx.reference.isBlank() || obs.reference.isBlank() || tx.reference == obs.reference) { "Reference numbers conflict." }
        if (tx.verification == Verification.VERIFIED && obs.source == Source.SBI_STATEMENT) {
            require(tx.date == obs.date) { "Verified statement dates conflict." }
        }
    }
    private suspend fun attach(raw: RawEventEntity, obs: Observation, accountId: String, targetId: String?,
        verified: Boolean, method: String, valueDate: String? = null): String {
        require(obs.amountMinor != null && obs.amountMinor > 0 && obs.direction != null) { "Observation has no valid amount or direction." }
        val existing = targetId?.let { requireNotNull(dao.transaction(it)) }
        if (existing != null) validateTarget(existing,obs,accountId)
        var tx = existing ?: TransactionEntity(accountId=accountId,amountMinor=obs.amountMinor,direction=obs.direction,
            date=obs.date,timestamp=obs.timestamp,merchantOriginal=obs.merchant,narration=obs.content,
            reference=obs.reference,channel=obs.channel,kind=obs.kind,outcome=if(obs.kind==EventKind.FAILED) Outcome.FAILED else Outcome.POSTED)
        if (verified) {
            val linkedRefund = tx.direction==Direction.CREDIT && dao.refundLink(tx.id)!=null
            tx = tx.copy(date=obs.date,valueDate=valueDate,merchantOriginal=obs.merchant,narration=obs.content,
                reference=obs.reference.ifBlank { tx.reference },verification=Verification.VERIFIED,
                outcome=if(tx.outcome==Outcome.FAILED) Outcome.POSTED else tx.outcome,
                kind=if(linkedRefund) tx.kind else obs.kind,
                channel=obs.channel,updatedAt=System.currentTimeMillis())
        } else if (existing != null && tx.verification != Verification.VERIFIED) {
            tx = tx.copy(reference=tx.reference.ifBlank { obs.reference },verification=Verification.LIKELY_MATCHED,
                outcome=if(obs.kind==EventKind.FAILED) tx.outcome else Outcome.POSTED,
                kind=if(tx.kind==EventKind.FAILED && obs.kind!=EventKind.FAILED) obs.kind else tx.kind,
                updatedAt=System.currentTimeMillis())
        }
        dao.saveTransaction(tx)
        ledger.applyRules(tx)
        dao.saveEvidence(EvidenceEntity(transactionId=tx.id,eventId=raw.id,source=obs.source,method=method,verified=verified))
        dao.saveEvent(raw.copy(processed=true,reviewReason=null))
        autoLinkRefund(tx)
        return tx.id
    }
    private suspend fun autoLinkRefund(tx: TransactionEntity) {
        if(tx.direction==Direction.DEBIT && tx.reference.isNotBlank()) {
            dao.candidates(tx.accountId,tx.reference,"1900-01-01","1900-01-01")
                .filter {it.direction==Direction.CREDIT && it.kind in listOf(EventKind.REFUND,EventKind.REVERSAL)}
                .forEach {autoLinkRefund(it)}
            return
        }
        if(tx.kind !in listOf(EventKind.REFUND,EventKind.REVERSAL) || tx.reference.isBlank() || dao.refundLink(tx.id)!=null) return
        val matches = dao.candidates(tx.accountId,tx.reference,"1900-01-01","1900-01-01")
            .filter { it.direction == Direction.DEBIT && it.reference == tx.reference && it.outcome != Outcome.FAILED }
        if(matches.size == 1 && matches.single().amountMinor >= tx.amountMinor + dao.refundedAmount(matches.single().id))
            linkRefundInternal(matches.single(),tx)
    }
    suspend fun linkRefund(originalId: String, refundId: String) = db.withTransaction {
        linkRefundInternal(requireNotNull(dao.transaction(originalId)),requireNotNull(dao.transaction(refundId)))
    }
    private suspend fun linkRefundInternal(original: TransactionEntity, refund: TransactionEntity) {
        require(original.accountId == refund.accountId && original.direction == Direction.DEBIT &&
            refund.direction == Direction.CREDIT && original.outcome != Outcome.FAILED && refund.outcome != Outcome.FAILED)
        require(dao.refundLink(refund.id)==null) { "Credit is already linked." }
        val total = Math.addExact(dao.refundedAmount(original.id),refund.amountMinor)
        require(total <= original.amountMinor) { "Refunds exceed the original debit." }
        dao.saveRefund(RefundLinkEntity(original.id,refund.id,refund.amountMinor))
        dao.saveTransaction(refund.copy(kind=if(refund.kind==EventKind.REVERSAL) EventKind.REVERSAL else EventKind.REFUND))
        val metadata=dao.metadata(refund.id) ?: MetadataEntity(refund.id)
        if(!metadata.userEdited) dao.saveMetadata(metadata.copy(categoryId=dao.metadata(original.id)?.categoryId))
        dao.saveTransaction(original.copy(outcome=if(total < original.amountMinor) Outcome.PARTIALLY_REFUNDED
            else if(refund.kind==EventKind.REVERSAL) Outcome.REVERSED else Outcome.REFUNDED))
    }
}
fun candidate(tx: TransactionEntity) = MatchCandidate(tx.id,tx.accountId,tx.amountMinor,tx.direction,tx.date,
    tx.timestamp,tx.merchantOriginal,tx.reference,tx.currency,tx.channel,tx.narration,verification=tx.verification,outcome=tx.outcome)
