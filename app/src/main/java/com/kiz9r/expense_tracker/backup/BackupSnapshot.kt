package com.kiz9r.expense_tracker.backup

import com.kiz9r.expense_tracker.data.*

data class BackupSnapshot(
    val version: Int = 6,
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
    val settings: List<SettingEntity>,
    val checkpoints: List<BalanceCheckpointEntity> = emptyList(),
    val allocations: List<AllocationEntity> = emptyList(),
    val transferPairs: List<TransferPairEntity> = emptyList(),
    val budgetExclusions: List<BudgetExclusionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetRevisions: List<BudgetRevisionEntity> = emptyList(),
    val budgetPeriods: List<BudgetPeriodEntity> = emptyList(),
    val budgetCoverage: List<BudgetCoverageEntity> = emptyList(),
    val budgetSelections: List<BudgetRevisionCategoryEntity> = emptyList(),
    val reserveAccounts: List<ReserveAccountEntity> = emptyList(),
    val reserveFunding: List<ReserveFundingEntity> = emptyList(),
    val reserveActivity: List<ReserveActivityEntity> = emptyList()
)
