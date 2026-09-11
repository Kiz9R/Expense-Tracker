package com.kiz9r.expense_tracker.ui.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiz9r.expense_tracker.data.TransactionItem
import com.kiz9r.expense_tracker.domain.*

@Composable fun Screen(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().testTag(tag).verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Field(value: String, change: (String)->Unit, label: String, tag: String,
    password: Boolean=false, numeric: Boolean=false, enabled: Boolean=true) {
    OutlinedTextField(value=value,onValueChange=change,label={ Text(label) },modifier=Modifier.fillMaxWidth().testTag(tag),
        singleLine=true,enabled=enabled,visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun Choice(label: String, selected: String, options: List<Pair<String,String>>, tag: String, change: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().testTag(tag)) {
            Text("$label: " + (options.find { it.first==selected }?.second ?: "Select"),modifier=Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach { (id,name) -> DropdownMenuItem(text={Text(name)},onClick={ change(id); expanded=false }) }
        }
    }
}
@Composable fun Notice(text: String, error: Boolean=false) {
    Surface(color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun Toggle(label: String, checked: Boolean, tag: String, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f)); Switch(checked=checked,onCheckedChange=change,modifier=Modifier.testTag(tag))
    }
}
@Composable fun TransactionCard(item: TransactionItem, open: ()->Unit) {
    val tx = item.transaction
    OutlinedCard(onClick=open,modifier=Modifier.fillMaxWidth().testTag("transactions.items."+tx.id)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(item.displayName,Modifier.weight(1f),fontWeight=FontWeight.Medium)
                Text((if(tx.direction==Direction.DEBIT) "− " else "+ ")+Money.format(tx.amountMinor),fontWeight=FontWeight.SemiBold)
            }
            Text(listOfNotNull(tx.date,item.categoryName,item.accountName).joinToString(" • "),style=MaterialTheme.typography.bodySmall)
            Text(when {
                tx.outcome!=Outcome.POSTED -> tx.outcome.name.lowercase().replace('_',' ')
                tx.verification==Verification.VERIFIED -> "Verified by statement"
                tx.manuallyCreated -> "Manual entry"
                else -> "Detected · awaiting statement"
            } + if(item.hidden) " · Hidden" else "",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable fun Confirm(title: String, text: String, confirm: ()->Unit, dismiss: ()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(text)},
        confirmButton={TextButton(onClick=confirm){Text("Confirm")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable fun LockDialog(unlock: ()->Unit) {
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false,usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Lock,contentDescription=null,modifier=Modifier.size(48.dp))
                Spacer(Modifier.height(24.dp)); Heading("Your ledger is locked")
                Spacer(Modifier.height(24.dp)); Button(onClick=unlock,modifier=Modifier.testTag("security.unlock")){Text("Unlock")}
            }
        }
    }
}
