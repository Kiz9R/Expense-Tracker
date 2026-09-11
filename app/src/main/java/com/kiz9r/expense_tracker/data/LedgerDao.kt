package com.kiz9r.expense_tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val ITEM_QUERY = """SELECT t.*, COALESCE(NULLIF(m.merchantDisplay, ''), t.merchantOriginal) AS displayName,
    c.name AS categoryName, m.categoryId AS categoryId, COALESCE(m.notes,'') AS notes,
    COALESCE(m.hidden,0) AS hidden, a.nickname AS accountName, a.last4 AS accountLast4
    FROM transactions t JOIN accounts a ON a.id=t.accountId
    LEFT JOIN metadata m ON m.transactionId=t.id LEFT JOIN categories c ON c.id=m.categoryId """
@Dao
interface LedgerDao {
    @Upsert suspend fun saveJob(job: ImportJobEntity)
    @Query("SELECT * FROM import_jobs WHERE id=:id") suspend fun job(id: String): ImportJobEntity?
    @Query("SELECT * FROM import_jobs WHERE id=:id") fun observeJob(id: String): Flow<ImportJobEntity?>
    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC") fun jobs(): Flow<List<ImportJobEntity>>
    @Query("DELETE FROM import_jobs WHERE id=:id") suspend fun deleteJob(id: String)
    @Query("DELETE FROM import_jobs") suspend fun clearJobs()
    @Query("SELECT * FROM accounts ORDER BY nickname") fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<AccountEntity>
    @Upsert suspend fun saveAccount(value: AccountEntity)
    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY name") suspend fun allCategories(): List<CategoryEntity>
    @Upsert suspend fun saveCategory(value: CategoryEntity)
    @Query("DELETE FROM categories WHERE id=:id AND system=0 AND NOT EXISTS(SELECT 1 FROM metadata WHERE categoryId=:id) AND NOT EXISTS(SELECT 1 FROM merchant_rules WHERE categoryId=:id)") suspend fun deleteCategory(id: String): Int
    @Upsert suspend fun saveTransaction(value: TransactionEntity)
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun transaction(id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE accountId=:accountId AND (reference=:reference AND :reference != '' OR date BETWEEN :start AND :end)") suspend fun candidates(accountId: String, reference: String, start: String, end: String): List<TransactionEntity>
    @Query(ITEM_QUERY + """WHERE (:accountId IS NULL OR t.accountId=:accountId)
      AND (:showHidden OR COALESCE(m.hidden,0)=0)
      AND (:direction='' OR t.direction=:direction) AND (:verification='' OR t.verification=:verification)
      AND (:category IS NULL OR m.categoryId=:category) AND t.date BETWEEN :start AND :end
      AND t.amountMinor BETWEEN :minAmount AND :maxAmount
      AND (:source='' OR EXISTS(SELECT 1 FROM evidence e WHERE e.transactionId=t.id AND e.source=:source))
      AND (:query='' OR t.merchantOriginal LIKE :query ESCAPE '\' OR m.merchantDisplay LIKE :query ESCAPE '\'
        OR t.narration LIKE :query ESCAPE '\' OR m.notes LIKE :query ESCAPE '\' OR t.reference LIKE :query ESCAPE '\'
        OR c.name LIKE :query ESCAPE '\' OR CAST(t.amountMinor AS TEXT) LIKE :query ESCAPE '\'
        OR EXISTS(SELECT 1 FROM transaction_tags tt JOIN tags g ON tt.tagId=g.id WHERE tt.transactionId=t.id AND g.name LIKE :query ESCAPE '\'))
      ORDER BY t.date DESC, COALESCE(t.timestamp,t.createdAt) DESC, t.id LIMIT :limit OFFSET :offset""")
    fun history(accountId: String?, showHidden: Boolean, query: String, direction: String, verification: String,
        category: String?, start: String, end: String, minAmount: Long, maxAmount: Long, source: String, limit: Int, offset: Int): Flow<List<TransactionItem>>
    @Query(ITEM_QUERY + "WHERE t.id=:id") fun detail(id: String): Flow<TransactionItem?>
    @Query("DELETE FROM transactions WHERE id=:id AND manuallyCreated=1 AND verification!='VERIFIED'") suspend fun deleteManual(id: String): Int
    @Query("SELECT COUNT(*) FROM refund_links WHERE originalId=:id OR refundId=:id") suspend fun refundConnections(id: String): Int
    @Upsert suspend fun saveMetadata(value: MetadataEntity)
    @Query("SELECT * FROM metadata WHERE transactionId=:id") suspend fun metadata(id: String): MetadataEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(value: RawEventEntity): Long
    @Upsert suspend fun saveEvent(value: RawEventEntity)
    @Query("SELECT * FROM events WHERE identity=:identity") suspend fun eventByIdentity(identity: String): RawEventEntity?
    @Query("SELECT * FROM events WHERE id=:id") suspend fun event(id: String): RawEventEntity?
    @Query("SELECT * FROM events WHERE processed=0 LIMIT 100") suspend fun pendingEvents(): List<RawEventEntity>
    @Query("SELECT * FROM events WHERE reviewReason IS NOT NULL ORDER BY receivedAt DESC LIMIT 200") fun reviews(): Flow<List<RawEventEntity>>
    @Upsert suspend fun saveEvidence(value: EvidenceEntity)
    @Query("SELECT * FROM evidence WHERE transactionId=:id") suspend fun evidence(id: String): List<EvidenceEntity>
    @Query("SELECT r.* FROM events r JOIN evidence e ON r.id=e.eventId WHERE e.transactionId=:id") fun evidenceEvents(id: String): Flow<List<RawEventEntity>>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<TagEntity>
    @Upsert suspend fun saveTag(value: TagEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveTransactionTag(value: TransactionTagEntity)
    @Query("DELETE FROM transaction_tags WHERE transactionId=:id") suspend fun clearTags(id: String)
    @Query("SELECT g.* FROM tags g JOIN transaction_tags tt ON tt.tagId=g.id WHERE tt.transactionId=:id") fun tags(id: String): Flow<List<TagEntity>>
    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC, id") fun rules(): Flow<List<MerchantRuleEntity>>
    @Query("SELECT * FROM merchant_rules WHERE enabled=1 ORDER BY priority DESC, id") suspend fun activeRules(): List<MerchantRuleEntity>
    @Upsert suspend fun saveRule(value: MerchantRuleEntity)
    @Query("DELETE FROM merchant_rules WHERE id=:id") suspend fun deleteRule(id: String)
    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC") fun imports(): Flow<List<StatementImportEntity>>
    @Query("SELECT * FROM statement_imports WHERE fileHash=:fileHash OR logicalFingerprint=:logical") suspend fun duplicateImport(fileHash: String, logical: String): StatementImportEntity?
    @Query("SELECT * FROM statement_imports WHERE id=:id") suspend fun statementImport(id: String): StatementImportEntity?
    @Query("SELECT * FROM statement_rows WHERE importId=:id ORDER BY sequence") suspend fun importRows(id: String): List<StatementRowEntity>
    @Query("SELECT * FROM statement_rows WHERE id=:id") suspend fun statementRow(id: String): StatementRowEntity?
    @Query("SELECT * FROM review_decisions WHERE observationKey=:key OR observationKey LIKE :history ORDER BY decidedAt, id")
    suspend fun decisionHistory(key: String, history: String): List<ReviewDecisionEntity>
    @Upsert suspend fun saveImport(value: StatementImportEntity)
    @Upsert suspend fun saveRow(value: StatementRowEntity)
    @Query("SELECT * FROM statement_rows WHERE fingerprint=:fingerprint") suspend fun matchingRows(fingerprint: String): List<StatementRowEntity>
    @Query("SELECT * FROM statement_rows WHERE importId=:id ORDER BY sequence") fun statementRows(id: String): Flow<List<StatementRowEntity>>
    @Upsert suspend fun saveDecision(value: ReviewDecisionEntity)
    @Upsert suspend fun saveMandate(value: MandateEntity)
    @Query("SELECT * FROM mandates ORDER BY merchant") fun mandates(): Flow<List<MandateEntity>>
    @Query("SELECT * FROM mandates WHERE reference=:reference AND accountId=:accountId") suspend fun findMandates(reference: String, accountId: String?): List<MandateEntity>
    @Upsert suspend fun saveRefund(value: RefundLinkEntity)
    @Query("SELECT * FROM refund_links WHERE refundId=:id") suspend fun refundLink(id: String): RefundLinkEntity?
    @Query("SELECT COALESCE(SUM(amountMinor),0) FROM refund_links WHERE originalId=:id") suspend fun refundedAmount(id: String): Long
    @Upsert suspend fun saveSetting(value: SettingEntity)
    @Query("SELECT * FROM settings") fun settings(): Flow<List<SettingEntity>>
    @Query("SELECT value FROM settings WHERE key=:key") suspend fun setting(key: String): String?
    @Query("""SELECT COALESCE(SUM(CASE WHEN t.direction='DEBIT' THEN t.amountMinor ELSE 0 END),0) AS spend,
      COALESCE(SUM(CASE WHEN t.direction='CREDIT' AND t.kind NOT IN ('REFUND','REVERSAL') THEN t.amountMinor ELSE 0 END),0) AS income,
      COALESCE(SUM(CASE WHEN t.direction='CREDIT' AND t.kind IN ('REFUND','REVERSAL') THEN t.amountMinor ELSE 0 END),0) AS refunds,
      COUNT(*) AS count FROM transactions t LEFT JOIN metadata m ON m.transactionId=t.id
      WHERE t.date BETWEEN :start AND :end AND (:accountId IS NULL OR t.accountId=:accountId)
      AND COALESCE(m.hidden,0)=0 AND t.outcome!='FAILED' AND t.ownedTransfer=0""")
    fun totals(start: String, end: String, accountId: String?): Flow<MonthlyTotals>
    @Query("""SELECT CASE :grouping WHEN 'merchant' THEN COALESCE(NULLIF(m.merchantDisplay,''),t.merchantOriginal)
      WHEN 'day' THEN t.date ELSE COALESCE(c.name,'Other') END AS label,
      SUM(CASE WHEN t.direction='DEBIT' THEN t.amountMinor ELSE -t.amountMinor END) AS amount
      FROM transactions t LEFT JOIN metadata m ON m.transactionId=t.id LEFT JOIN categories c ON c.id=m.categoryId
      WHERE t.date BETWEEN :start AND :end AND (:accountId IS NULL OR t.accountId=:accountId)
      AND COALESCE(m.hidden,0)=0 AND t.outcome!='FAILED' AND t.ownedTransfer=0
      AND (t.direction='DEBIT' OR t.kind IN ('REFUND','REVERSAL')) GROUP BY label ORDER BY amount DESC""")
    fun breakdown(start: String, end: String, accountId: String?, grouping: String): Flow<List<Breakdown>>
    @Query(ITEM_QUERY + """WHERE t.date BETWEEN :start AND :end AND (:accountId IS NULL OR t.accountId=:accountId)
      AND t.direction='DEBIT' AND t.outcome!='FAILED' AND t.ownedTransfer=0 AND COALESCE(m.hidden,0)=0
      ORDER BY t.amountMinor DESC LIMIT 5""")
    fun largest(start: String, end: String, accountId: String?): Flow<List<TransactionItem>>
    @Query("""SELECT COALESCE(NULLIF(m.merchantDisplay,''),t.merchantOriginal) AS label, MAX(t.amountMinor) AS amount
      FROM transactions t LEFT JOIN metadata m ON m.transactionId=t.id
      WHERE t.direction='DEBIT' AND t.outcome!='FAILED' AND COALESCE(m.hidden,0)=0 AND t.ownedTransfer=0
      AND (:accountId IS NULL OR t.accountId=:accountId) AND t.date>=:since
      GROUP BY label, t.amountMinor HAVING COUNT(DISTINCT SUBSTR(t.date,1,7))>=3 ORDER BY amount DESC LIMIT 20""")
    fun recurring(accountId: String?, since: String): Flow<List<Breakdown>>
}
