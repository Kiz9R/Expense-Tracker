package com.kiz9r.expense_tracker.data
import androidx.room.Database
import androidx.room.RoomDatabase
@Database(entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
    MetadataEntity::class, RawEventEntity::class, EvidenceEntity::class, TagEntity::class,
    TransactionTagEntity::class, MerchantRuleEntity::class, StatementImportEntity::class,
    StatementRowEntity::class, ReviewDecisionEntity::class, MandateEntity::class,
    RefundLinkEntity::class, SettingEntity::class, ImportJobEntity::class], version = 2, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
    abstract fun backup(): BackupDao
}
/** Preserve financial records and unfinished parsing jobs from the first release. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE statement_imports ADD COLUMN warningsJson TEXT")
        db.execSQL("ALTER TABLE import_jobs ADD COLUMN previewToken TEXT")
        db.execSQL("ALTER TABLE import_jobs ADD COLUMN resolutionsJson TEXT")
    }
}
