package com.kiz9r.expense_tracker.ingestion

import android.app.Notification
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
    fun smsIntake(): SmsIntake
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
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if(messages.isEmpty()) return
        val parts=messages.map { SmsPart(it.originatingAddress.orEmpty(),it.messageBody.orEmpty(),it.timestampMillis) }
        val receivedAt=System.currentTimeMillis()
        val pending = goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val graph = dependencies(context)
                    if(graph.smsIntake().accept(parts,receivedAt,
                            ContextCompat.checkSelfPermission(context,Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED,
                            context.getSystemService(UserManager::class.java).isUserUnlocked)) {
                        scheduleIngestion(context)
                    }
                }
            } catch (_: Exception) {
                // No message payloads in logs or WorkManager. Recover any already-persisted observation.
                runCatching { scheduleIngestion(context) }
            } finally { pending.finish() }
        }
    }
}
class IngestionRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED) ||
            !context.getSystemService(UserManager::class.java).isUserUnlocked) return
        runCatching { scheduleIngestion(context) }
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
