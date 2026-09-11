package com.kiz9r.expense_tracker.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.kiz9r.expense_tracker.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

data class HistoryFilter(val accountId: String? = null, val query: String = "", val showHidden: Boolean = false,
    val direction: String = "", val verification: String = "", val category: String? = null,
    val start: String = "1900-01-01", val end: String = "2999-12-31", val minAmount: Long = 0,
    val maxAmount: Long = Long.MAX_VALUE, val source: String = "", val page: Int = 0)
data class ManualInput(val id: String? = null, val accountId: String, val amount: String, val direction: Direction,
    val date: String, val time: String, val merchant: String, val categoryId: String?, val notes: String, val tags: String)

@Singleton
class LedgerRepository @Inject constructor(val db: LedgerDatabase, private val gson: Gson) {
    private val dao get() = db.ledger()
    val accounts get() = dao.accounts()
    val categories get() = dao.categories()
    val imports get() = dao.imports()
    val reviews get() = dao.reviews()
    val mandates get() = dao.mandates()
    val rules get() = dao.rules()
    val settings get() = dao.settings()
    suspend fun initialize() = db.withTransaction {
        if (dao.allCategories().isEmpty()) {
            listOf("Food & Dining","Groceries","Shopping","Transportation","Fuel","Rent","Utilities","Bills","Subscriptions",
                "Entertainment","Healthcare","Education","Travel","Cash Withdrawal","Transfer","Investments","Insurance",
                "EMI / Loan","Bank Charges","Salary","Income","Interest","Refund","Gifts","Other").forEach {
                dao.saveCategory(CategoryEntity(id = "system-" + normalized(it), name = it, system = true))
            }
        }
    }
    suspend fun addAccount(nickname: String, last4: String, type: String) = db.withTransaction {
        require(nickname.isNotBlank() && nickname.length <= 80) { "Enter an account nickname." }
        require(last4.matches(Regex("\\d{4}"))) { "Enter exactly four account digits." }
        dao.saveAccount(AccountEntity(nickname = nickname.trim(), last4 = last4, accountType = type))
    }
    fun history(f: HistoryFilter): Flow<List<TransactionItem>> {
        val query = if (f.query.isBlank()) "" else "%" + (runCatching { Money.parse(f.query).toString() }.getOrNull()
            ?: f.query.trim()).replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%"
        return dao.history(f.accountId,f.showHidden,query,f.direction,f.verification,f.category,f.start,f.end,
            f.minAmount,f.maxAmount,f.source,50,f.page * 50)
    }
    fun detail(id: String) = dao.detail(id)
    fun evidence(id: String) = dao.evidenceEvents(id)
    fun tags(id: String) = dao.tags(id)
    fun statementRows(id: String) = dao.statementRows(id)
    fun totals(month: YearMonth, account: String?) = dao.totals(month.atDay(1).toString(),month.atEndOfMonth().toString(),account)
    fun breakdown(month: YearMonth, account: String?, group: String) = dao.breakdown(month.atDay(1).toString(),month.atEndOfMonth().toString(),account,group)
    fun largest(month: YearMonth, account: String?) = dao.largest(month.atDay(1).toString(),month.atEndOfMonth().toString(),account)
    fun recurring(account: String?) = dao.recurring(account,Dates.today().minusMonths(12).toString())
    suspend fun saveManual(input: ManualInput): String = db.withTransaction {
        val amount = Money.parse(input.amount)
        require(amount > 0) { "Amount must be greater than zero." }
        val date = LocalDate.parse(input.date)
        val timestamp = date.atTime(LocalTime.parse(input.time)).atZone(Dates.zone).toInstant().toEpochMilli()
        require(input.merchant.isNotBlank()) { "Enter a merchant or description." }
        require(dao.allAccounts().any { it.id == input.accountId }) { "Select an account." }
        val old = input.id?.let { requireNotNull(dao.transaction(it)) }
        require(old == null || old.manuallyCreated && old.verification != Verification.VERIFIED) { "Bank evidence cannot be edited." }
        require(old == null || dao.refundConnections(old.id)==0) { "Linked refunds preserve their financial amounts. Edit personal details instead." }
        val tx = TransactionEntity(id = old?.id ?: newId(), accountId = input.accountId, amountMinor = amount,
            direction = input.direction,date = date.toString(),timestamp = timestamp,merchantOriginal = input.merchant.trim(),
            kind = if (input.direction == Direction.DEBIT) EventKind.DEBIT else EventKind.CREDIT, manuallyCreated = true,
            createdAt = old?.createdAt ?: System.currentTimeMillis())
        dao.saveTransaction(tx)
        if (old == null) {
            val obs = Observation(Source.MANUAL,"manual:"+tx.id,System.currentTimeMillis(),timestamp,tx.date,
                amountMinor=amount,direction=tx.direction,merchant=tx.merchantOriginal,kind=tx.kind,content="Manually entered")
            val raw = raw(obs).copy(processed=true)
            dao.insertEvent(raw)
            dao.saveEvidence(EvidenceEntity(transactionId=tx.id,eventId=raw.id,source=Source.MANUAL,method="manual"))
        }
        updateMetadataInternal(tx.id,input.merchant,input.categoryId,input.notes,input.tags)
        tx.id
    }
    suspend fun updateMetadata(id: String, name: String, category: String?, notes: String, tags: String) = db.withTransaction {
        requireNotNull(dao.transaction(id))
        updateMetadataInternal(id,name,category,notes,tags)
    }
    private suspend fun updateMetadataInternal(id: String, name: String, category: String?, notes: String, tags: String) {
        require(category == null || dao.allCategories().any { it.id == category }) { "Category no longer exists." }
        dao.saveMetadata((dao.metadata(id) ?: MetadataEntity(id)).copy(merchantDisplay=name.trim(),categoryId=category,notes=notes,userEdited=true))
        dao.clearTags(id)
        val existing = dao.allTags()
        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30).forEach { name ->
            val tag = existing.firstOrNull { it.name == name } ?: TagEntity(name=name).also { dao.saveTag(it) }
            dao.saveTransactionTag(TransactionTagEntity(id,tag.id))
        }
    }
    suspend fun hide(id: String, hidden: Boolean) = db.withTransaction {
        requireNotNull(dao.transaction(id))
        dao.saveMetadata((dao.metadata(id) ?: MetadataEntity(id)).copy(hidden=hidden))
    }
    suspend fun delete(id: String) = db.withTransaction {
        require(dao.refundConnections(id)==0) { "This entry is linked to a refund. Hide it instead." }
        require(dao.deleteManual(id) == 1) { "Only unverified manual entries can be deleted." }
    }
    suspend fun ownedTransfer(id: String, enabled: Boolean) = db.withTransaction {
        val tx = requireNotNull(dao.transaction(id))
        dao.saveTransaction(tx.copy(ownedTransfer=enabled))
    }
    suspend fun category(id: String?, name: String) = db.withTransaction {
        require(name.isNotBlank()) { "Enter a category name." }
        val old = dao.allCategories().firstOrNull { it.id == id }
        require(old?.system != true) { "System categories cannot be renamed." }
        dao.saveCategory(CategoryEntity(id=old?.id ?: newId(),name=name.trim()))
    }
    suspend fun deleteCategory(id: String) = db.withTransaction {
        require(dao.deleteCategory(id) == 1) { "Only unused custom categories can be deleted." }
    }
    suspend fun rule(rule: MerchantRuleEntity) = db.withTransaction {
        require(rule.matchValue.isNotBlank() && rule.matchValue.length <= 100) { "Enter a short merchant match." }
        require(rule.matchType in listOf("exact","contains","startsWith")) { "Unsupported match type." }
        dao.saveRule(rule)
    }
    suspend fun deleteRule(id: String) = db.withTransaction { dao.deleteRule(id) }
    suspend fun setting(key: String, value: String) = db.withTransaction { dao.saveSetting(SettingEntity(key,value)) }
    suspend fun enabled(key: String) = dao.setting(key) == "true"
    fun raw(obs: Observation) = RawEventEntity(identity=obs.identity,source=obs.source,receivedAt=obs.receivedAt,
        content=maskAccounts(obs.content),parsedJson=gson.toJson(obs.copy(content=maskAccounts(obs.content))),parserVersion=obs.parserVersion)
    suspend fun applyRules(tx: TransactionEntity) {
        val meta = dao.metadata(tx.id) ?: MetadataEntity(tx.id)
        if (meta.userEdited) return
        val rule = dao.activeRules().firstOrNull { matchesRule(it,tx.merchantOriginal) }
        val category = rule?.categoryId ?: dao.allCategories().firstOrNull { it.name == when {
            tx.kind in listOf(EventKind.REFUND,EventKind.REVERSAL) -> "Refund"
            tx.channel == Channel.ATM -> "Cash Withdrawal"
            tx.channel == Channel.BANK_FEE -> "Bank Charges"
            tx.channel == Channel.INTEREST -> "Interest"
            tx.direction == Direction.CREDIT -> "Income"
            else -> "Other"
        } }?.id
        dao.saveMetadata(meta.copy(merchantDisplay=rule?.rename.orEmpty(),categoryId=category))
    }
}

fun matchesRule(rule: MerchantRuleEntity, merchant: String): Boolean {
    if (!rule.enabled) return false
    val m = normalized(merchant); val v = normalized(rule.matchValue)
    if (v.isEmpty()) return false
    return when(rule.matchType) { "exact" -> m == v; "startsWith" -> m.startsWith(v); "contains" -> m.contains(v); else -> false }
}

class SaveManualTransaction @Inject constructor(private val repository: LedgerRepository) {
    suspend operator fun invoke(input: ManualInput) = repository.saveManual(input)
}
