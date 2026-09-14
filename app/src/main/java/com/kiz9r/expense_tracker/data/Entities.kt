package com.kiz9r.expense_tracker.data

import androidx.room.*
import com.kiz9r.expense_tracker.domain.*

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String = newId(), val nickname: String, val last4: String,
    val accountType: String = "Savings", val bankName: String = "SBI", val currency: String = "INR",
    val active: Boolean = true, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String = newId(), val name: String, val system: Boolean = false)
@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = AccountEntity::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountId"), Index("date"), Index("reference"), Index("verification")])
data class TransactionEntity(
    @PrimaryKey val id: String = newId(), val accountId: String, val amountMinor: Long,
    val direction: Direction, val date: String, val timestamp: Long? = null, val valueDate: String? = null,
    val merchantOriginal: String, val narration: String = "", val reference: String = "",
    val channel: Channel = Channel.UNKNOWN, val currency: String = "INR",
    val verification: Verification = Verification.PROVISIONAL, val outcome: Outcome = Outcome.POSTED,
    val kind: EventKind = EventKind.DEBIT, val manuallyCreated: Boolean = false,
    val ownedTransfer: Boolean = false, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Entity(tableName = "metadata", foreignKeys = [ForeignKey(entity = TransactionEntity::class,
    parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("categoryId")])
data class MetadataEntity(@PrimaryKey val transactionId: String, val merchantDisplay: String = "",
    val categoryId: String? = null, val notes: String = "", val hidden: Boolean = false, val userEdited: Boolean = false)
@Entity(tableName = "events", indices = [Index(value = ["identity"], unique = true), Index("processed")])
data class RawEventEntity(@PrimaryKey val id: String = newId(), val identity: String, val source: Source,
    val receivedAt: Long, val content: String, val parsedJson: String, val parserVersion: String,
    val processed: Boolean = false, val reviewReason: String? = null)
@Entity(tableName = "evidence", foreignKeys = [
    ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = RawEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("transactionId"), Index(value = ["eventId"], unique = true)])
data class EvidenceEntity(@PrimaryKey val id: String = newId(), val transactionId: String, val eventId: String,
    val source: Source, val method: String, val verified: Boolean = false)
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(@PrimaryKey val id: String = newId(), val name: String)
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], foreignKeys = [
    ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("tagId")])
data class TransactionTagEntity(val transactionId: String, val tagId: String)
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(@PrimaryKey val id: String = newId(), val matchType: String = "contains",
    val matchValue: String, val rename: String = "", val categoryId: String? = null, val priority: Int = 0, val enabled: Boolean = true)
@Entity(tableName = "statement_imports", indices = [Index(value = ["fileHash"], unique = true),
    Index(value = ["logicalFingerprint"], unique = true), Index("accountId")])
data class StatementImportEntity(@PrimaryKey val id: String = newId(), val accountId: String, val fileName: String,
    val fileHash: String, val logicalFingerprint: String, val startDate: String, val endDate: String,
    val openingBalance: Long, val closingBalance: Long, val transactionCount: Int,
    val status: String, val parserVersion: String = "1.0.0", val importedAt: Long = System.currentTimeMillis(),
    val warningsJson: String? = null)
@Entity(tableName = "statement_rows", foreignKeys = [
    ForeignKey(entity = StatementImportEntity::class, parentColumns = ["id"], childColumns = ["importId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("importId"), Index("transactionId"), Index("fingerprint")])
data class StatementRowEntity(@PrimaryKey val id: String = newId(), val importId: String, val sequence: Int,
    val fingerprint: String, val date: String, val valueDate: String?, val narration: String, val reference: String,
    val amountMinor: Long, val direction: Direction, val balance: Long, val transactionId: String?,
    val ignored: Boolean = false)
@Entity(tableName = "review_decisions", indices = [Index(value = ["observationKey"], unique = true)])
data class ReviewDecisionEntity(@PrimaryKey val id: String = newId(), val observationKey: String,
    val action: String, val transactionId: String?, val reason: String = "", val decidedAt: Long = System.currentTimeMillis())
@Entity(tableName = "mandates")
data class MandateEntity(@PrimaryKey val id: String = newId(), val accountId: String?, val merchant: String,
    val amountMinor: Long?, val reference: String, val status: String, val eventId: String)
@Entity(tableName = "refund_links", primaryKeys = ["originalId", "refundId"], indices = [Index(value = ["refundId"], unique = true)])
data class RefundLinkEntity(val originalId: String, val refundId: String, val amountMinor: Long)
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "import_jobs")
data class ImportJobEntity(@PrimaryKey val id: String = newId(), val accountId: String, val fileHash: String,
    val fileName: String, val text: String, val status: String = "QUEUED", val resultJson: String? = null,
    val error: String? = null, val createdAt: Long = System.currentTimeMillis(),
    val previewToken: String? = null, val resolutionsJson: String? = null)
data class TransactionItem(
    @Embedded val transaction: TransactionEntity, val displayName: String, val categoryName: String?,
    val categoryId: String?, val notes: String, val hidden: Boolean, val accountName: String, val accountLast4: String
)
data class MonthlyTotals(val spend: Long = 0, val income: Long = 0, val refunds: Long = 0, val count: Int = 0) {
    val netSpend get() = spend - refunds
    val net get() = income - netSpend
}
data class Breakdown(val label: String, val amount: Long)
data class SmsStats(val received: Int = 0, val pending: Int = 0, val review: Int = 0)
