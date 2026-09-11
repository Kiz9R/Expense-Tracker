package com.kiz9r.expense_tracker.backup

import com.kiz9r.expense_tracker.data.*

data class BackupSnapshot(
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val metadata: List<MetadataEntity>,
    val events: List<RawEventEntity>,
    val evidence: List<EvidenceEntity>,
    val tags: List<TagEntity>,
    val transactionTags: List<TransactionTagEntity>,
    val merchantRules: List<MerchantRuleEntity>,
    val statementImports: List<StatementImportEntity>,
    val statementRows: List<StatementRowEntity>,
    val reviewDecisions: List<ReviewDecisionEntity>,
    val mandates: List<MandateEntity>,
    val refundLinks: List<RefundLinkEntity>,
    val settings: List<SettingEntity>
)
