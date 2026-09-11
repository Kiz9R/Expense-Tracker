package com.kiz9r.expense_tracker.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.LedgerDatabase
import com.kiz9r.expense_tracker.ingestion.readBytesLimited
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BackupService @Inject constructor(@ApplicationContext private val context: Context,
    private val db: LedgerDatabase, private val gson: Gson) {
    suspend fun snapshot(): BackupSnapshot = db.withTransaction {
        val dao = db.backup()
        BackupSnapshot(
            accounts=dao.accounts(),
            categories=dao.categories(),
            transactions=dao.transactions(),
            metadata=dao.metadata(),
            events=dao.events(),
            evidence=dao.evidence(),
            tags=dao.tags(),
            transactionTags=dao.transactionTags(),
            merchantRules=dao.merchantRules(),
            statementImports=dao.statementImports(),
            statementRows=dao.statementRows(),
            reviewDecisions=dao.reviewDecisions(),
            mandates=dao.mandates(),
            refundLinks=dao.refundLinks(),
            settings=dao.settings()
        )
    }
    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val snapshot = snapshot()
            validateBackup(snapshot)
            val plain = gson.toJson(snapshot).toByteArray(Charsets.UTF_8)
            try {
                val encrypted = BackupCrypto.encrypt(plain,password)
                require(encrypted.size<=64*1024*1024) { "Backup exceeds 64 MB." }
                requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { it.write(encrypted) }
            } finally { plain.fill(0) }
        } finally { password.fill('\u0000') }
    }
    suspend fun inspect(uri: Uri, password: CharArray): BackupSnapshot = withContext(Dispatchers.IO) {
        try {
            val encrypted = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesLimited(64*1024*1024) }
            val plain = BackupCrypto.decrypt(encrypted,password)
            try {
                gson.fromJson(plain.toString(Charsets.UTF_8),BackupSnapshot::class.java).also(::validateBackup)
            } finally { plain.fill(0) }
        } finally { password.fill('\u0000') }
    }
    suspend fun restore(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        validateBackup(snapshot)
        // Room serializes this replacement with every ingestion and editing transaction.
        db.withTransaction {
            val dao = db.backup()
            db.ledger().clearJobs()
            dao.clearSettingEntity()
            dao.clearRefundLinkEntity()
            dao.clearMandateEntity()
            dao.clearReviewDecisionEntity()
            dao.clearStatementRowEntity()
            dao.clearStatementImportEntity()
            dao.clearMerchantRuleEntity()
            dao.clearTransactionTagEntity()
            dao.clearTagEntity()
            dao.clearEvidenceEntity()
            dao.clearRawEventEntity()
            dao.clearMetadataEntity()
            dao.clearTransactionEntity()
            dao.clearCategoryEntity()
            dao.clearAccountEntity()
            dao.insertAccountEntity(snapshot.accounts)
            dao.insertCategoryEntity(snapshot.categories)
            dao.insertTransactionEntity(snapshot.transactions)
            dao.insertMetadataEntity(snapshot.metadata)
            dao.insertRawEventEntity(snapshot.events)
            dao.insertEvidenceEntity(snapshot.evidence)
            dao.insertTagEntity(snapshot.tags)
            dao.insertTransactionTagEntity(snapshot.transactionTags)
            dao.insertMerchantRuleEntity(snapshot.merchantRules)
            dao.insertStatementImportEntity(snapshot.statementImports)
            dao.insertStatementRowEntity(snapshot.statementRows)
            dao.insertReviewDecisionEntity(snapshot.reviewDecisions)
            dao.insertMandateEntity(snapshot.mandates)
            dao.insertRefundLinkEntity(snapshot.refundLinks)
            dao.insertSettingEntity(snapshot.settings)
            // Android permissions are device state, never restored from another installation.
            db.ledger().saveSetting(com.kiz9r.expense_tracker.data.SettingEntity("sms","false"))
            db.ledger().saveSetting(com.kiz9r.expense_tracker.data.SettingEntity("notifications","false"))
            if(!context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure)
                db.ledger().saveSetting(com.kiz9r.expense_tracker.data.SettingEntity("app_lock","false"))
        }
    }
}
