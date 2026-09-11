package com.kiz9r.expense_tracker.ingestion

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.maskAccounts
import com.kiz9r.expense_tracker.reconciliation.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager receives only a random job ID. Statement text/results remain in encrypted Room. */
@Singleton
class StatementJobs @Inject constructor(@param:ApplicationContext private val context: Context,
    private val db: LedgerDatabase, private val gson: Gson) {
    val jobs get()=db.ledger().jobs()
    suspend fun enqueue(accountId: String, fileHash: String, text: String, fileName: String = "SBI statement.pdf"): String {
        val job=ImportJobEntity(accountId=accountId,fileHash=fileHash,fileName=maskAccounts(fileName.take(160)),text=maskAccounts(text))
        db.withTransaction {db.ledger().saveJob(job)}
        schedule(job.id)
        return job.id
    }
    suspend fun restoreReview(id: String, token: String): Pair<Map<Int,Resolution>,Boolean> = db.withTransaction {
        val job=requireNotNull(db.ledger().job(id)) { "Import no longer exists." }
        require(job.status=="READY")
        val stale=job.previewToken!=null && job.previewToken!=token
        val saved=if(!stale && job.resolutionsJson!=null) gson.fromJson(job.resolutionsJson,SavedReview::class.java).choices
            .associate { it.sequence to Resolution(it.action,it.transactionId) } else emptyMap()
        db.ledger().saveJob(job.copy(previewToken=token,resolutionsJson=gson.toJson(SavedReview(
            saved.map { (sequence,resolution) -> SavedResolution(sequence,resolution.action,resolution.transactionId) }))))
        saved to stale
    }
    suspend fun saveReview(id: String, token: String, choices: Map<Int,Resolution>) = db.withTransaction {
        val job=requireNotNull(db.ledger().job(id)) { "Import no longer exists." }
        require(job.status=="READY" && job.previewToken==token) { "Preview changed. Refresh before reviewing." }
        db.ledger().saveJob(job.copy(resolutionsJson=gson.toJson(SavedReview(choices.map { (sequence,resolution) ->
            SavedResolution(sequence,resolution.action,resolution.transactionId) }))))
    }
    fun schedule(id: String) {
        val request=OneTimeWorkRequestBuilder<StatementParseWorker>().setInputData(workDataOf("job_id" to id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("statement-"+id,ExistingWorkPolicy.KEEP,request)
    }
    suspend fun await(id: String): Pair<ImportJobEntity,ParsedStatement> {
        val job=requireNotNull(db.ledger().observeJob(id).first {it==null || it.status in listOf("READY","FAILED")}) { "Import was cancelled." }
        require(job.status=="READY") {job.error ?: "Unable to parse statement."}
        return job to gson.fromJson(job.resultJson,ParsedStatement::class.java)
    }
    suspend fun parse(id: String) {
        val job=db.ledger().job(id) ?: return
        if(job.status in listOf("READY","FAILED")) return
        try {
            val parsed=SbiPdfStatementParser().parse(job.text)
            db.withTransaction {
                if(db.ledger().job(id)!=null) db.ledger().saveJob(job.copy(text="",status="READY",resultJson=gson.toJson(parsed)))
            }
        } catch(e: CancellationException) {throw e}
        catch(e: Exception) {
            db.withTransaction {
                if(db.ledger().job(id)!=null) db.ledger().saveJob(job.copy(text="",status="FAILED",error=e.message?.take(240) ?: "Unsupported statement."))
            }
        }
    }
    suspend fun discard(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("statement-"+id)
        db.withTransaction {db.ledger().deleteJob(id)}
    }
}
class StatementParseWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val id=inputData.getString("job_id") ?: return Result.failure()
        return try { dependencies(applicationContext).statementJobs().parse(id); Result.success() }
        catch(e: CancellationException) {throw e}
        catch(_: Exception) {Result.retry()}
    }
}
