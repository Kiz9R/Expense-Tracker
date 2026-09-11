package com.kiz9r.expense_tracker.ingestion

import android.app.Notification
import android.content.*
import android.os.UserManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.*
import com.kiz9r.expense_tracker.data.LedgerRepository
import com.kiz9r.expense_tracker.reconciliation.ReconciliationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint @InstallIn(SingletonComponent::class)
interface IngestionEntryPoint {
    fun ledger(): LedgerRepository
    fun reconciliation(): ReconciliationRepository
    fun statementJobs(): StatementJobs
}
fun dependencies(context: Context): IngestionEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext,IngestionEntryPoint::class.java)
fun scheduleIngestion(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("financial-observations",
        ExistingWorkPolicy.APPEND_OR_REPLACE,OneTimeWorkRequestBuilder<IngestionWorker>().build())
}
class SbiSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if(messages.isEmpty()) return
        val observation = SbiSmsParser.parse(messages.first().originatingAddress.orEmpty(),
            messages.joinToString("") { it.messageBody.orEmpty() },System.currentTimeMillis(),messages.first().timestampMillis) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val graph = dependencies(context)
                    if(graph.ledger().enabled("sms")) {
                        graph.reconciliation().enqueue(observation)
                        scheduleIngestion(context)
                    }
                }
            } finally { pending.finish() }
        }
    }
}
class IngestionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        if(!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return Result.retry()
        return try { dependencies(applicationContext).reconciliation().processPending(); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
class UpiNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val parsers = listOf(PhonePeParser(),GooglePayParser())
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if(!getSystemService(UserManager::class.java).isUserUnlocked) return
        val parser = parsers.firstOrNull { it.packageName==sbn.packageName } ?: return
        if(sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val observation = parser.parse("$title $body",sbn.key,sbn.postTime) ?: return
        scope.launch {
            runCatching {
                val graph = dependencies(this@UpiNotificationService)
                if(graph.ledger().enabled("notifications")) {
                    graph.reconciliation().enqueue(observation); scheduleIngestion(this@UpiNotificationService)
                }
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
