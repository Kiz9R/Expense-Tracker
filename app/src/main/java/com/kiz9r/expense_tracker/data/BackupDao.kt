package com.kiz9r.expense_tracker.data

import androidx.room.*

@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Insert suspend fun insertAccountEntity(items: List<AccountEntity>)
    @Query("DELETE FROM accounts") suspend fun clearAccountEntity()
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Insert suspend fun insertCategoryEntity(items: List<CategoryEntity>)
    @Query("DELETE FROM categories") suspend fun clearCategoryEntity()
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Insert suspend fun insertTransactionEntity(items: List<TransactionEntity>)
    @Query("DELETE FROM transactions") suspend fun clearTransactionEntity()
    @Query("SELECT * FROM metadata") suspend fun metadata(): List<MetadataEntity>
    @Insert suspend fun insertMetadataEntity(items: List<MetadataEntity>)
    @Query("DELETE FROM metadata") suspend fun clearMetadataEntity()
    @Query("SELECT * FROM events") suspend fun events(): List<RawEventEntity>
    @Insert suspend fun insertRawEventEntity(items: List<RawEventEntity>)
    @Query("DELETE FROM events") suspend fun clearRawEventEntity()
    @Query("SELECT * FROM evidence") suspend fun evidence(): List<EvidenceEntity>
    @Insert suspend fun insertEvidenceEntity(items: List<EvidenceEntity>)
    @Query("DELETE FROM evidence") suspend fun clearEvidenceEntity()
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Insert suspend fun insertTagEntity(items: List<TagEntity>)
    @Query("DELETE FROM tags") suspend fun clearTagEntity()
    @Query("SELECT * FROM transaction_tags") suspend fun transactionTags(): List<TransactionTagEntity>
    @Insert suspend fun insertTransactionTagEntity(items: List<TransactionTagEntity>)
    @Query("DELETE FROM transaction_tags") suspend fun clearTransactionTagEntity()
    @Query("SELECT * FROM merchant_rules") suspend fun merchantRules(): List<MerchantRuleEntity>
    @Insert suspend fun insertMerchantRuleEntity(items: List<MerchantRuleEntity>)
    @Query("DELETE FROM merchant_rules") suspend fun clearMerchantRuleEntity()
    @Query("SELECT * FROM statement_imports") suspend fun statementImports(): List<StatementImportEntity>
    @Insert suspend fun insertStatementImportEntity(items: List<StatementImportEntity>)
    @Query("DELETE FROM statement_imports") suspend fun clearStatementImportEntity()
    @Query("SELECT * FROM statement_rows") suspend fun statementRows(): List<StatementRowEntity>
    @Insert suspend fun insertStatementRowEntity(items: List<StatementRowEntity>)
    @Query("DELETE FROM statement_rows") suspend fun clearStatementRowEntity()
    @Query("SELECT * FROM review_decisions") suspend fun reviewDecisions(): List<ReviewDecisionEntity>
    @Insert suspend fun insertReviewDecisionEntity(items: List<ReviewDecisionEntity>)
    @Query("DELETE FROM review_decisions") suspend fun clearReviewDecisionEntity()
    @Query("SELECT * FROM mandates") suspend fun mandates(): List<MandateEntity>
    @Insert suspend fun insertMandateEntity(items: List<MandateEntity>)
    @Query("DELETE FROM mandates") suspend fun clearMandateEntity()
    @Query("SELECT * FROM refund_links") suspend fun refundLinks(): List<RefundLinkEntity>
    @Insert suspend fun insertRefundLinkEntity(items: List<RefundLinkEntity>)
    @Query("DELETE FROM refund_links") suspend fun clearRefundLinkEntity()
    @Query("SELECT * FROM settings") suspend fun settings(): List<SettingEntity>
    @Insert suspend fun insertSettingEntity(items: List<SettingEntity>)
    @Query("DELETE FROM settings") suspend fun clearSettingEntity()
}
