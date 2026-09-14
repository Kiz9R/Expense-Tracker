package com.kiz9r.expense_tracker.ui.features

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.domain.Dates
import com.kiz9r.expense_tracker.security.LedgerRecovery
import kotlinx.coroutines.*

@Composable fun RecoveryScreen(recovery: LedgerRecovery, retry: ()->Unit, close: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var uri by remember {mutableStateOf<android.net.Uri?>(null)}
    var password by remember {mutableStateOf("")}
    var preview by remember {mutableStateOf<BackupSnapshot?>(null)}
    var busy by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf(false)}
    var recovered by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri=it;preview=null }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            Screen("recovery") {
                Heading("Recover your ledger","The encrypted ledger could not be opened. Its existing files have been retained.")
                if(recovered) {
                    Notice("Your backup has been recovered into a new encrypted ledger. Close the app, then open it again. Review SMS, notification and app-lock settings afterward.")
                    Button(onClick=close,modifier=Modifier.testTag("recovery.close")){Text("Close app")}
                } else {
                    Text("If device keys are unavailable, an encrypted backup and its password are needed. Without a backup, the app cannot decrypt the old records. Uninstalling would remove the retained files.")
                    OutlinedButton(onClick=retry,enabled=!busy,modifier=Modifier.testTag("recovery.retry")){Text("Retry opening ledger")}
                    OutlinedButton(onClick={preview=null;picker.launch(arrayOf("*/*"))},enabled=!busy,
                        modifier=Modifier.testTag("recovery.select")){Text("Select encrypted backup")}
                    Field(password,{password=it},"Backup password","recovery.password",password=true)
                    Button(enabled=uri!=null && !busy,onClick={
                        val secret=password.toCharArray();password="";preview=null;message=null;busy=true
                        scope.launch {
                            try {preview=BackupArchive.inspect(context,requireNotNull(uri),secret,Gson())}
                            catch(e: CancellationException){throw e}
                            catch(_: javax.crypto.AEADBadTagException){message="Wrong password or damaged backup. Original files were not changed."}
                            catch(_: Exception){message="This backup could not be opened or validated. Select a complete supported backup and try again."}
                            finally {secret.fill('\u0000');busy=false}
                        }
                    },modifier=Modifier.testTag("recovery.inspect")){Text("Unlock & validate backup")}
                    preview?.let { snapshot ->
                        Notice("Backup created "+Dates.date(snapshot.createdAt)+" · "+snapshot.transactions.size+" transactions · "+snapshot.evidence.size+" evidence records · "+snapshot.statementImports.size+" statements")
                        snapshot.accounts.forEach {Text(it.nickname+" · SBI ••••"+it.last4)}
                        Button(onClick={confirm=true},enabled=!busy,modifier=Modifier.testTag("recovery.restore")){Text("Review recovery")}
                        TextButton(onClick={preview=null;uri=null},enabled=!busy){Text("Cancel")}
                    }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                }
            }
        }
    }
    if(confirm) Confirm("Use this backup as your ledger?",
        "The validated backup will become your active ledger. The inaccessible original files will remain on this device. Tracking must be enabled again after recovery.",{
            confirm=false;busy=true
            val snapshot=requireNotNull(preview)
            scope.launch {
                try { recovery.restore(snapshot);preview=null;recovered=true }
                catch(e: CancellationException){throw e}
                catch(_: Exception){message="Recovery could not finish. Original files were retained. Reopen the app before retrying."}
                finally {busy=false}
            }
        },{confirm=false})
}
