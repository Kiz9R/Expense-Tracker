package com.kiz9r.expense_tracker.ui.features

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*

@Composable fun SettingsScreen(vm: TrackerViewModel, navigate: (String)->Unit) {
    val context=LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val stats by vm.smsStats.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    fun hasSmsPermission()=ContextCompat.checkSelfPermission(context,Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED
    var smsGranted by remember { mutableStateOf(hasSmsPermission()) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) smsGranted=hasSmsPermission() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun enabled(key: String)=settings.any {it.key==key && it.value=="true"}
    val sms=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsGranted=it
        vm.setting("sms",it)
        if(!it) vm.message.value="SMS access was not granted. Manual tracking and statement imports still work."
    }
    Screen("settings") {
        Heading("Settings","Private by default. Your financial data is stored in an encrypted database on this device.")
        OutlinedButton(onClick={navigate("accounts")},modifier=Modifier.testTag("settings.accounts")){Text("Manage SBI accounts")}
        OutlinedButton(onClick={navigate("review")},modifier=Modifier.testTag("settings.review")){Text("Needs review ("+reviews.size+")")}
        OutlinedButton(onClick={navigate("mandates")}){Text("Mandates")}
        OutlinedButton(onClick={navigate("rules")}){Text("Merchant rules")}
        OutlinedButton(onClick={navigate("backup")}){Text("Encrypted backup & restore")}
        HorizontalDivider()
        Heading("Optional tracking")
        Text("SMS access detects new SBI financial messages. Other messages and OTPs are discarded. Past SMS history is never scanned.")
        Toggle("Track new SBI SMS",enabled("sms") && smsGranted,"settings.sms") {
            if(!it) vm.setting("sms",false)
            else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED) vm.setting("sms",true)
            else sms.launch(Manifest.permission.RECEIVE_SMS)
        }
        Text(when {
            enabled("sms") && !smsGranted -> "SMS tracking paused: Android permission is missing."
            enabled("sms") -> "SMS tracking enabled for new incoming messages."
            else -> "SMS tracking is off."
        },modifier=Modifier.testTag("settings.sms.status"))
        Text("Stored financial SMS: "+stats.received+" · Pending: "+stats.pending+" · Needs review: "+stats.review)
        settings.firstOrNull { it.key=="sms_last_received" }?.value?.toLongOrNull()?.let {
            Text("Last eligible SMS received: "+java.time.Instant.ofEpochMilli(it).atZone(Dates.zone).toLocalDateTime())
        }
        if(!smsGranted) OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,("package:"+context.packageName).toUri()))},
            modifier=Modifier.testTag("settings.sms.permissions")){Text("Open Android app permissions")}
        if(stats.pending>0) OutlinedButton(onClick={vm.action("Pending observations processed."){vm.reconciliation.processPending()}},
            modifier=Modifier.testTag("settings.sms.retry")){Text("Retry stored observations")}
        Text("Only newly delivered messages are read. After a reboot, tracking resumes after your first device unlock. Missed activity can be recovered from a statement. Unknown formats stay in Needs Review.",style=MaterialTheme.typography.bodySmall)
        Text("Experimental PhonePe and Google Pay support. Notification access is separate from SMS access. Uncertain accounts require review.")
        Toggle("Track supported UPI notifications",enabled("notifications"),"settings.notifications"){vm.setting("notifications",it)}
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}){Text("Open Android notification access")}
        HorizontalDivider()
        Heading("Security")
        Toggle("Require biometric or device unlock",enabled("app_lock"),"settings.app-lock") {
            if(it && !context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
                vm.message.value="Set up a screen lock on this device before enabling app lock."
            else vm.setting("app_lock",it)
        }
        Toggle("Allow screenshots on financial screens",enabled("screenshots"),"settings.screenshots"){vm.setting("screenshots",it)}
        Notice("Uninstalling removes this device's ledger. Export an encrypted backup first and keep its password somewhere safe.")
        Text("No cloud sync, ads, bank login, payment initiation, or financial-data network access.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun CategoriesScreen(vm: TrackerViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var name by rememberSaveable {mutableStateOf("")}
    var editId by rememberSaveable {mutableStateOf<String?>(null)}
    Screen("categories") {
        Heading("Categories")
        Field(name,{name=it},if(editId==null) "New category" else "Rename category","categories.form.name")
        Button(onClick={vm.action("Category saved."){vm.ledger.category(editId,name);name="";editId=null}},
            modifier=Modifier.testTag("categories.save")){Text(if(editId==null) "Add category" else "Save rename")}
        if(editId!=null) TextButton(onClick={editId=null;name=""}){Text("Cancel")}
        categories.forEach { category ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(category.name,Modifier.weight(1f))
                if(!category.system) {
                    TextButton(onClick={editId=category.id;name=category.name}){Text("Rename")}
                    TextButton(onClick={vm.action("Category removed."){vm.ledger.deleteCategory(category.id)}}){Text("Delete")}
                }
            }
        }
        Notice("Only unused custom categories can be deleted. Filter the transaction list by category to see its history.")
    }
}
@Composable fun RulesScreen(vm: TrackerViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    var match by rememberSaveable {mutableStateOf("")}
    var type by rememberSaveable {mutableStateOf("contains")}
    var rename by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    var priority by rememberSaveable {mutableStateOf("0")}
    Screen("rules") {
        Heading("Merchant rules","Rules apply to new or reconciled transactions. Your saved personal edits always take precedence.")
        Field(match,{match=it},"Merchant match","rules.form.match")
        Choice("Match type",type,listOf("contains" to "Contains","exact" to "Exact","startsWith" to "Starts with"),"rules.form.type"){type=it}
        Field(rename,{rename=it},"Display name (optional)","rules.form.rename")
        Choice("Category",category,listOf("" to "Default")+categories.map {it.id to it.name},"rules.form.category"){category=it}
        Field(priority,{priority=it},"Priority (higher first)","rules.form.priority",numeric=true)
        Button(onClick={vm.action("Rule added."){vm.ledger.rule(MerchantRuleEntity(matchType=type,matchValue=match,rename=rename,categoryId=category.ifBlank{null},priority=priority.toInt()));match="";rename=""}},
            modifier=Modifier.testTag("rules.create")){Text("Add rule")}
        rules.forEach {rule ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(rule.matchType+" "+rule.matchValue+" → "+rule.rename.ifBlank{"Keep merchant"}+" · priority "+rule.priority)
                    Toggle("Enabled",rule.enabled,"rules.items."+rule.id+".enabled"){vm.action{vm.ledger.rule(rule.copy(enabled=it))}}
                    TextButton(onClick={vm.action{vm.ledger.deleteRule(rule.id)}}){Text("Remove rule")}
                }
            }
        }
    }
}
@Composable fun MandatesScreen(vm: TrackerViewModel) {
    val mandates by vm.mandates.collectAsStateWithLifecycle()
    Screen("mandates") {
        Heading("Mandates","A mandate is an authorization record. Only an actual debit counts as spending.")
        if(mandates.isEmpty()) Notice("No mandates detected.")
        mandates.forEach {mandate ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(mandate.merchant,style=MaterialTheme.typography.titleMedium)
                    Text((mandate.amountMinor?.let(Money::format) ?: "Amount unknown")+" · "+mandate.status.lowercase())
                    if(mandate.reference.isNotBlank()) Text("Reference "+mandate.reference)
                }
            }
        }
    }
}
@Composable fun BackupScreen(vm: TrackerViewModel) {
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var password by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var exportPassword by remember {mutableStateOf<CharArray?>(null)}
    var restoreUri by remember {mutableStateOf<Uri?>(null)}
    var confirmRestore by remember {mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri ->
        val secret=exportPassword
        exportPassword=null
        if(uri!=null && secret!=null) vm.export(uri,secret) else secret?.fill('\u0000')
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){vm.clearBackupPreview();confirmRestore=false;restoreUri=it}
    DisposableEffect(Unit) {onDispose {exportPassword?.fill('\u0000');vm.clearBackupPreview()}}
    Screen("backup") {
        Heading("Encrypted backup","Keep the password separately. The app cannot recover a forgotten backup password.")
        Field(password,{password=it},"Backup password (12+ characters)","backup.password",password=true)
        Field(confirmation,{confirmation=it},"Confirm password for export","backup.confirm-password",password=true)
        Button(onClick={
            if(password.length<12 || password!=confirmation) vm.message.value="Use at least 12 characters and matching passwords."
            else {
                exportPassword=password.toCharArray();password="";confirmation=""
                exporter.launch("expense_tracker_backup_"+Dates.today()+".etbackup")
            }
        },enabled=!busy,modifier=Modifier.testTag("backup.export")){Text("Export encrypted backup")}
        HorizontalDivider()
        Heading("Restore a backup")
        OutlinedButton(onClick={vm.clearBackupPreview();importer.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.testTag("backup.select")){Text("Select backup file")}
        if(restoreUri!=null) Button(onClick={
            val secret=password.toCharArray();password="";confirmation=""
            vm.inspectBackup(requireNotNull(restoreUri),secret)
        },enabled=!busy,modifier=Modifier.testTag("backup.inspect")){Text("Unlock & validate backup")}
        preview?.let { snapshot ->
            Notice("Backup created "+Dates.date(snapshot.createdAt)+"\n"+
                snapshot.accounts.size+" accounts · "+snapshot.transactions.size+" transactions · "+
                snapshot.statementImports.size+" statement imports.\nRestoring replaces this device's current ledger.")
            snapshot.accounts.forEach { Text(it.nickname+" · SBI ••••"+it.last4) }
            Text(snapshot.evidence.size.toString()+" evidence records · "+snapshot.reviewDecisions.size+" review decisions · "+snapshot.mandates.size+" mandates")
            Button(onClick={confirmRestore=true},enabled=!busy,modifier=Modifier.testTag("backup.restore")){Text("Review replacement")}
            TextButton(onClick={vm.clearBackupPreview();restoreUri=null}){Text("Cancel restore")}
        }
    }
    if(confirmRestore) Confirm("Replace this device's ledger?","All current tracking data will be replaced by the validated backup. Export your current ledger first if you need it.",
        {confirmRestore=false;vm.restore()},{confirmRestore=false})
}
