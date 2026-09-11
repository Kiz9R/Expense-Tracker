package com.kiz9r.expense_tracker.ui.features

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.reconciliation.*

@Composable fun ReconciliationSummary(summary: StatementSummary, preview: Boolean) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("statements.summary")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(if(preview) "After these review choices" else "Reconciliation summary",style=MaterialTheme.typography.titleMedium)
            Text("Rows: "+summary.rows+" · Matched: "+summary.matched+" · New: "+summary.added)
            Text("Needs review: "+summary.needsReview+" · Ignored: "+summary.ignored)
            Text("Refund / reversal rows: "+summary.refundsAndReversals)
            Text("Statement debits: "+Money.format(summary.statementDebit))
            Text("Statement credits: "+Money.format(summary.statementCredit))
            Text((if(preview) "Planned linked debits: " else "Linked ledger debits: ")+Money.format(summary.linkedDebit))
            Text((if(preview) "Planned linked credits: " else "Linked ledger credits: ")+Money.format(summary.linkedCredit))
            Text("Opening: "+Money.format(summary.opening))
            Text("Calculated closing: "+Money.format(summary.calculatedClosing))
            Text("Statement closing: "+Money.format(summary.closing))
            Text("Official totals include hidden items and owned transfers.",style=MaterialTheme.typography.bodySmall)
        }
    }
    summary.warnings.forEach { Notice(it,true) }
    if(summary.ignored>0) Notice("Ignored rows remain official evidence and keep this statement in Exceptions.")
}

private fun candidateLabel(candidate: MatchCandidate): String {
    val time=candidate.timestamp?.let { java.time.Instant.ofEpochMilli(it).atZone(Dates.zone).toLocalTime().toString() }.orEmpty()
    return candidate.merchant+" · "+candidate.date+" "+time+" · "+Money.format(candidate.amountMinor)+" "+candidate.direction+
        " · "+candidate.channel+" · "+candidate.sources.joinToString { it.name.replace('_',' ') }+
        " · Ref "+candidate.reference.ifBlank { "not available" }+" · "+candidate.verification
}

@Composable fun StatementsScreen(vm: TrackerViewModel, open: (String)->Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val imports by vm.imports.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val resolutions by vm.resolutions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val jobs by vm.importJobs.collectAsStateWithLifecycle()
    var account by rememberSaveable {mutableStateOf(accounts.firstOrNull()?.id.orEmpty())}
    var password by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf<Uri?>(null)}
    var page by rememberSaveable {mutableIntStateOf(0)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){selected=it}
    LaunchedEffect(preview?.fileHash) { page=0 }
    Screen("statements") {
        Heading("SBI statements","Verify detected transactions and recover missed activity from your statement.")
        Notice("Supports the SBI Relationship Summary savings-account layout and the documented text-table layout. Other layouts are rejected. Review the account and totals before committing.")
        if(preview==null && !importing) {
            Choice("Account",account,accounts.map {it.id to (it.nickname+" ••••"+it.last4)},"statements.account"){account=it}
            OutlinedButton(onClick={picker.launch(arrayOf("application/pdf"))},enabled=!busy,modifier=Modifier.testTag("statements.select")){
                Text(if(selected==null) "Select PDF statement" else "Choose a different PDF")
            }
            if(selected!=null) {
                Field(password,{password=it},"PDF password (if required)","statements.password",password=true)
                Button(onClick={val secret=password.toCharArray();password="";vm.importPdf(requireNotNull(selected),secret,account)},
                    enabled=!busy && account.isNotBlank(),modifier=Modifier.testTag("statements.preview")){Text("Read statement & preview")}
            }
        }
        if(importing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Reading and checking statement…")
            TextButton(onClick={vm.cancelPreview();selected=null;password=""},modifier=Modifier.testTag("statements.cancel")){Text("Cancel import")}
        }
        preview?.let { data ->
            HorizontalDivider()
            val owner=accounts.find {it.id==data.accountId}
            Heading(data.fileName,(owner?.nickname ?: "SBI")+" ••••"+data.statement.last4+" · "+data.statement.start+" to "+data.statement.end)
            if(data.duplicate) Notice("This statement is already imported. No duplicate transactions will be created.")
            else {
                val summary=data.summary(resolutions)
                ReconciliationSummary(summary,true)
                val assigned=data.rows.mapNotNull { effectiveResolution(it,resolutions)?.takeIf {it.action=="match"}?.transactionId }
                val duplicateTargets=assigned.distinct().size!=assigned.size
                if(duplicateTargets) Notice("Two rows use the same transaction. Choose a different match or create a separate transaction.",true)
                data.rows.drop(page*20).take(20).forEach { rowPreview ->
                    val row=rowPreview.row
                    val choice=effectiveResolution(rowPreview,resolutions)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Row "+(row.sequence+1)+" · "+row.date+" · "+Money.format(row.amountMinor)+" "+row.direction)
                            Text(row.narration)
                            Text("Ref: "+row.reference.ifBlank {"not available"}+" · Balance: "+Money.format(row.balance))
                            row.valueDate?.let {Text("Value date: "+it)}
                            if(rowPreview.decision is MatchDecision.Review) Notice(rowPreview.decision.reason,true)
                            val candidates=rowPreview.candidates.associateBy {it.id}
                            val ids=candidates.keys.toMutableSet()
                            (rowPreview.decision as? MatchDecision.Exact)?.let {ids+=it.transactionId}
                            val options=listOf("" to "Review required","new" to "Create new transaction","ignore" to "Ignore row (keep exception)")+
                                ids.map {id -> id to (candidates[id]?.let(::candidateLabel) ?: "Existing verified transaction")}
                            Choice("Decision",choice?.let {it.transactionId ?: it.action}.orEmpty(),options,"statements.review.row."+row.sequence) {
                                if(!busy) vm.resolve(row.sequence,when(it) {
                                    "" -> Resolution("review")
                                    "new","ignore" -> Resolution(it)
                                    else -> Resolution("match",it)
                                })
                            }
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick={page--},enabled=page>0){Text("Previous rows")}
                    Text("Page "+(page+1)+" / "+((data.rows.size+19)/20))
                    TextButton(onClick={page++},enabled=(page+1)*20<data.rows.size){Text("Next rows")}
                }
                Button(onClick=vm::commitImport,enabled=!busy && summary.needsReview==0 && !duplicateTargets,modifier=Modifier.testTag("statements.commit")){
                    Text(if(summary.needsReview>0) summary.needsReview.toString()+" rows need review" else "Commit reviewed import")
                }
                TextButton(onClick=vm::refreshPreview,enabled=!busy,modifier=Modifier.testTag("statements.refresh")){Text("Refresh matches")}
                Text("Review choices are saved locally. Resume this import after restarting the app.",style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={vm.cancelPreview();selected=null},enabled=!busy,modifier=Modifier.testTag("statements.discard")){Text("Discard import")}
        }
        if(preview==null && !importing) jobs.forEach {job ->
            Text(job.fileName+" · "+job.status.lowercase())
            job.error?.let {Notice(it,true)}
            Row {
                if(job.status!="FAILED") TextButton(onClick={vm.resumeImport(job.id)},enabled=!busy,modifier=Modifier.testTag("statements.jobs.resume."+job.id)){Text("Resume")}
                TextButton(onClick={vm.action {vm.statementJobs.discard(job.id)}},enabled=!busy){Text("Discard")}
            }
        }
        Heading("Import history")
        if(imports.isEmpty()) Text("No statements imported yet.")
        imports.forEach { imported ->
            OutlinedCard(onClick={open(imported.id)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(imported.fileName)
                    Text(imported.startDate+" – "+imported.endDate)
                    Text(imported.transactionCount.toString()+" transactions · "+imported.status.lowercase())
                    Text("Imported "+Dates.date(imported.importedAt))
                }
            }
        }
    }
}

@Composable fun StatementDetailScreen(vm: TrackerViewModel, id: String, open: (String)->Unit) {
    val rows by remember(id){vm.ledger.statementRows(id)}.collectAsStateWithLifecycle(emptyList())
    val imports by vm.imports.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var report by remember(id){mutableStateOf<StatementReport?>(null)}
    var error by remember(id){mutableStateOf<String?>(null)}
    var page by rememberSaveable {mutableIntStateOf(0)}
    var reviewRow by remember {mutableStateOf<String?>(null)}
    var candidates by remember {mutableStateOf(emptyList<MatchCandidate>())}
    var target by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf(false)}
    LaunchedEffect(id,rows,imports) {
        try { report=vm.reconciliation.report(id);error=null }
        catch(e: kotlinx.coroutines.CancellationException) {throw e}
        catch(e: Exception) {error=e.message ?: "Unable to read statement."}
    }
    Screen("statements.detail") {
        Heading("Statement evidence",report?.imported?.fileName)
        error?.let {Notice(it,true)}
        report?.let { data ->
            Text(data.imported.startDate+" – "+data.imported.endDate+" · "+data.imported.status.lowercase())
            Text("Imported "+Dates.date(data.imported.importedAt)+" · Parser "+data.imported.parserVersion)
            ReconciliationSummary(data.summary,false)
            if(data.summary.warnings.isNotEmpty())
                Notice("Balance discrepancies cannot be dismissed. Check the original PDF or obtain a corrected statement; official rows stay unchanged.")
            rows.drop(page*30).take(30).forEach {row ->
                Text("Row "+(row.sequence+1)+" · "+row.date+" · "+Money.format(row.amountMinor)+" "+row.direction)
                Text(row.narration)
                Text("Ref "+row.reference.ifBlank {"not available"}+" · Balance "+Money.format(row.balance))
                if(row.ignored) {
                    Notice("Ignored during review; retained as an exception.")
                    TextButton(onClick={vm.action {
                        candidates=vm.reconciliation.ignoredRowCandidates(row.id);reviewRow=row.id;target=""
                    }},enabled=!busy,modifier=Modifier.testTag("statements.detail.resolve."+row.sequence)){Text("Resolve ignored row")}
                    if(reviewRow==row.id) {
                        Choice("Resolution",target,listOf("" to "Choose resolution","new" to "Create new verified transaction")+
                            candidates.map {it.id to candidateLabel(it)},"statements.detail.resolution"){if(!busy) target=it}
                        Button(onClick={confirmation=true},enabled=!busy && target.isNotBlank(),
                            modifier=Modifier.testTag("statements.detail.confirm")){Text("Apply resolution")}
                    }
                }
                row.transactionId?.let { tx -> TextButton(onClick={open(tx)}){Text("Open transaction")} }
                val key="statement:"+data.imported.logicalFingerprint+":"+row.sequence
                data.decisions.filter {it.observationKey==key || it.observationKey.startsWith(key+":revision:")}.forEach {
                    Text("Decision: "+it.action+" · "+Dates.date(it.decidedAt),style=MaterialTheme.typography.bodySmall)
                    if(it.reason.isNotBlank()) Text(it.reason,style=MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
        Row {
            TextButton(onClick={page--},enabled=page>0){Text("Previous")}
            TextButton(onClick={page++},enabled=(page+1)*30<rows.size){Text("Next")}
        }
    }
    if(confirmation) Confirm("Resolve ignored row","This will attach official evidence and update your ledger. The original ignore decision will remain in history.",
        confirm={confirmation=false;vm.action("Statement row resolved.") {
            vm.reconciliation.resolveIgnoredRow(requireNotNull(reviewRow),if(target=="new") Resolution("new") else Resolution("match",target))
            reviewRow=null;report=vm.reconciliation.report(id)
        }},dismiss={confirmation=false})
}

@Composable fun ReviewScreen(vm: TrackerViewModel) {
    val events by vm.reviews.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var eventId by rememberSaveable {mutableStateOf("")}
    var account by rememberSaveable {mutableStateOf(accounts.firstOrNull()?.id.orEmpty())}
    var target by rememberSaveable {mutableStateOf("new")}
    var candidates by remember {mutableStateOf(emptyList<MatchCandidate>())}
    LaunchedEffect(eventId,account) {
        candidates=if(eventId.isNotBlank() && account.isNotBlank())
            runCatching {vm.reconciliation.reviewCandidates(eventId,account)}.getOrDefault(emptyList()) else emptyList()
    }
    Screen("review") {
        Heading("Needs review","Ambiguous observations stay here until you resolve them.")
        if(events.isEmpty()) Notice("Nothing needs review.")
        events.forEach {event ->
            OutlinedCard(onClick={eventId=event.id;target="new"},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(event.source.name.replace('_',' '))
                    Text(event.content)
                    Text(event.reviewReason.orEmpty(),color=MaterialTheme.colorScheme.error)
                }
            }
            if(eventId==event.id) {
                Choice("Account",account,accounts.map {it.id to (it.nickname+" ••••"+it.last4)},"review.account"){account=it}
                Choice("Resolution",target,listOf("new" to "Create new / record mandate","ignore" to "Ignore observation")+
                    candidates.map {it.id to (it.merchant+" · "+it.date+" · "+Money.format(it.amountMinor)+" "+it.direction)},
                    "review.resolution"){target=it}
                Button(onClick={vm.action("Observation resolved.") {
                    vm.reconciliation.resolveEvent(event.id,account,if(target in listOf("new","ignore")) Resolution(target) else Resolution("match",target))
                    eventId=""
                }},modifier=Modifier.testTag("review.confirm")){Text("Confirm resolution")}
            }
        }
    }
}
