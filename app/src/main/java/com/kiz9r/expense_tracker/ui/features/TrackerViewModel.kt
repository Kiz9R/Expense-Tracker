package com.kiz9r.expense_tracker.ui.features

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackerViewModel @Inject constructor(
    val ledger: LedgerRepository, val reconciliation: ReconciliationRepository,
    private val saveManual: SaveManualTransaction, private val pdf: PdfTextExtractor, private val backup: BackupService,
    val statementJobs: StatementJobs
) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(5000)
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val filter = MutableStateFlow(HistoryFilter())
    val month = MutableStateFlow(YearMonth.from(Dates.today()))
    private val accountState = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = accountState
    private var accountObserver: Job? = null
    val categories = ledger.categories.stateIn(viewModelScope,sharing,emptyList())
    // Fail closed while encrypted settings load, so a locked ledger never flashes on screen.
    val settings = ledger.settings.stateIn(viewModelScope,sharing,listOf(SettingEntity("app_lock","true")))
    val transactions = filter.flatMapLatest(ledger::history).stateIn(viewModelScope,sharing,emptyList())
    val totals = combine(month,filter) { m,f -> m to f.accountId }.flatMapLatest { (m,a) -> ledger.totals(m,a) }.stateIn(viewModelScope,sharing,MonthlyTotals())
    val previousTotals = combine(month,filter) { m,f -> m.minusMonths(1) to f.accountId }.flatMapLatest { (m,a) -> ledger.totals(m,a) }.stateIn(viewModelScope,sharing,MonthlyTotals())
    val grouping = MutableStateFlow("category")
    val breakdown = combine(month,filter,grouping) { m,f,g -> Triple(m,f.accountId,g) }.flatMapLatest { (m,a,g) -> ledger.breakdown(m,a,g) }.stateIn(viewModelScope,sharing,emptyList())
    val largest = combine(month,filter) { m,f -> m to f.accountId }.flatMapLatest { (m,a) -> ledger.largest(m,a) }.stateIn(viewModelScope,sharing,emptyList())
    val recurring = filter.flatMapLatest { ledger.recurring(it.accountId) }.stateIn(viewModelScope,sharing,emptyList())
    val imports = ledger.imports.stateIn(viewModelScope,sharing,emptyList())
    val reviews = ledger.reviews.stateIn(viewModelScope,sharing,emptyList())
    val mandates = ledger.mandates.stateIn(viewModelScope,sharing,emptyList())
    val rules = ledger.rules.stateIn(viewModelScope,sharing,emptyList())
    val importing = MutableStateFlow(false)
    private var currentAction: Job? = null
    val preview = MutableStateFlow<ImportPreview?>(null)
    val resolutions = MutableStateFlow<Map<Int,Resolution>>(emptyMap())
    val restorePreview = MutableStateFlow<BackupSnapshot?>(null)
    val importJobs=statementJobs.jobs.stateIn(viewModelScope,sharing,emptyList())
    private var activeImportJob: String? = null
    init { initialize() }
    fun initialize() = action {
        ledger.initialize()
        // Read the first real account result before selecting onboarding vs. the ledger.
        accountState.value=ledger.accounts.first()
        ready.value=true
        if(accountObserver==null) accountObserver=viewModelScope.launch {
            ledger.accounts.collect { accounts ->
                accountState.value=accounts
                if(filter.value.accountId != null && accounts.none { it.id == filter.value.accountId })
                    filter.value=filter.value.copy(accountId=null,page=0)
                if(preview.value?.accountId?.let { id -> accounts.none { it.id == id } } == true) {
                    activeImportJob=null; preview.value=null; resolutions.value=emptyMap()
                }
            }
        }
        reconciliation.processPending()
    }
    fun action(success: String? = null, block: suspend () -> Unit) {
        if(busy.value) return
        busy.value=true
        currentAction=viewModelScope.launch {
            try { block(); if(success!=null) message.value=success }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message.value = when(e) {
                    is javax.crypto.AEADBadTagException -> "Wrong backup password or damaged backup. Existing data was not changed."
                    is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException -> "Incorrect PDF password. Please try again."
                    else -> e.message?.take(240) ?: "Could not complete the operation. Please try again."
                }
            } finally { busy.value=false }
        }
    }
    fun save(input: ManualInput, done: (String) -> Unit) = action { done(saveManual(input)) }
    fun deleteAccount(id: String, done: () -> Unit) = action("Account and its local records deleted.") {
        ledger.deleteAccount(id)
        done()
    }
    fun importPdf(uri: Uri, password: CharArray, accountId: String) {
        if(busy.value) { password.fill('\u0000'); return }
        action {
            importing.value=true
            try {
                require(accounts.value.any { it.id==accountId }) { "Choose an SBI account." }
                val (bytes,text)=pdf.read(uri,password)
                try {
                    val id=statementJobs.enqueue(accountId,hash(bytes),text,pdf.displayName(uri))
                    activeImportJob=id
                    resumeImportInternal(id)
                } finally { bytes.fill(0) }
            } finally { password.fill('\u0000'); importing.value=false }
        }
    }
    private suspend fun resumeImportInternal(id: String) {
        activeImportJob=id
        statementJobs.schedule(id)
        val (job,statement)=statementJobs.await(id)
        val result=reconciliation.preview(job.accountId,job.fileName,job.fileHash,statement)
        val (choices,stale)=statementJobs.restoreReview(id,reconciliation.previewToken(result))
        preview.value=result
        resolutions.value=choices
        if(stale) message.value="The ledger changed. Saved choices were cleared; review this refreshed preview."
    }
    fun resumeImport(id: String)=action {
        importing.value=true
        try { resumeImportInternal(id) } finally { importing.value=false }
    }
    fun refreshPreview() { activeImportJob?.let(::resumeImport) }
    fun cancelPreview() {
        if(busy.value && !importing.value) return
        viewModelScope.launch {
            if(importing.value) currentAction?.cancelAndJoin()
            activeImportJob?.let { statementJobs.discard(it) }
            activeImportJob=null;preview.value=null;resolutions.value=emptyMap()
        }
    }
    fun resolve(sequence: Int, resolution: Resolution)=action {
        val data=requireNotNull(preview.value)
        val updated=if(resolution.action=="review") resolutions.value - sequence else resolutions.value + (sequence to resolution)
        statementJobs.saveReview(requireNotNull(activeImportJob),reconciliation.previewToken(data),updated)
        resolutions.value=updated
    }
    fun commitImport() = action("Statement imported.") {
        reconciliation.commit(requireNotNull(preview.value),resolutions.value,requireNotNull(activeImportJob))
        activeImportJob=null
        preview.value=null; resolutions.value=emptyMap()
    }
    fun export(uri: Uri, password: CharArray) = action("Encrypted backup saved.") { backup.export(uri,password) }
    fun inspectBackup(uri: Uri, password: CharArray) = action { restorePreview.value=backup.inspect(uri,password) }
    fun restore() = action("Backup restored. Review tracking and security settings; app lock requires a device screen lock.") {
        backup.restore(requireNotNull(restorePreview.value))
        restorePreview.value=null; preview.value=null; resolutions.value=emptyMap(); filter.value=HistoryFilter()
    }
    fun setting(key: String, enabled: Boolean) = action { ledger.setting(key,enabled.toString()) }
}
