package com.kiz9r.expense_tracker.ui.features

import com.kiz9r.expense_tracker.ui.theme.financialColors
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
        .padding(horizontal=20.dp,vertical=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Field(value: String, change: (String)->Unit, label: String, tag: String,
    password: Boolean=false, numeric: Boolean=false, enabled: Boolean=true,
    textColor: androidx.compose.ui.graphics.Color=androidx.compose.ui.graphics.Color.Unspecified) {
    OutlinedTextField(value=value,onValueChange=change,label={ Text(label) },modifier=Modifier.fillMaxWidth().testTag(tag),
        shape=MaterialTheme.shapes.small,singleLine=true,textStyle=(if(tag=="transactions.form.amount") MaterialTheme.typography.headlineLarge else MaterialTheme.typography.bodyLarge).copy(color=textColor),enabled=enabled,visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun Choice(label: String, selected: String, options: List<Pair<String,String>>, tag: String, compact: Boolean=false, change: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag(tag)) {
            val name=options.find {it.first==selected}?.second ?: "Select"
            Text((if(compact) "" else "$label: ")+name,modifier=Modifier.weight(1f).semantics {contentDescription="$label: $name"},maxLines=if(compact) 1 else 3,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(20.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach { (id,name) -> DropdownMenuItem(text={Text(name)},onClick={ change(id); expanded=false }) }
        }
    }
}
enum class NoticeSeverity { Information, Warning, Error, Success }
@Composable fun Notice(text: String, modifier: Modifier=Modifier, severity: NoticeSeverity=NoticeSeverity.Information) {
    val colors=financialColors
    val foreground=when(severity) {
        NoticeSeverity.Warning -> colors.warning
        NoticeSeverity.Error -> colors.spending
        NoticeSeverity.Success -> colors.credit
        NoticeSeverity.Information -> colors.neutral
    }
    val background=when(severity) {
        NoticeSeverity.Warning -> colors.warningContainer
        NoticeSeverity.Error -> colors.spendingContainer
        NoticeSeverity.Success -> colors.creditContainer
        NoticeSeverity.Information -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val icon=when(severity) {
        NoticeSeverity.Warning -> Icons.Outlined.WarningAmber
        NoticeSeverity.Error -> Icons.Outlined.ErrorOutline
        NoticeSeverity.Success -> Icons.Outlined.CheckCircle
        NoticeSeverity.Information -> Icons.Outlined.Info
    }
    Surface(color=background,contentColor=foreground,shape=MaterialTheme.shapes.medium,modifier=modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,contentDescription=severity.name,modifier=Modifier.size(24.dp))
            Text(text,Modifier.weight(1f),color=foreground,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun Toggle(label: String, checked: Boolean, tag: String, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f)); Switch(checked=checked,onCheckedChange=change,modifier=Modifier.testTag(tag))
    }
}
@Composable fun TransactionCard(item: TransactionItem, open: ()->Unit) {
    val tx=item.transaction
    val refund=tx.kind in listOf(EventKind.REFUND,EventKind.REVERSAL)
    val color=financialColors.movement(tx.amountMinor,tx.direction)
    val status=when {
        tx.outcome!=Outcome.POSTED -> tx.outcome.name.lowercase().replace('_',' ')
        tx.verification==Verification.VERIFIED -> "Statement verified"
        tx.manuallyCreated -> "Manual"
        else -> "Provisional"
    } + if(item.hidden) " · Hidden" else ""
    val largeFont=LocalDensity.current.fontScale>1.3f
    Surface(onClick=open,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth().testTag("transactions.items."+tx.id)) {
        Row(Modifier.padding(vertical=12.dp,horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(color=color.copy(alpha=.12f),shape=MaterialTheme.shapes.small) {
                Icon(if(refund) Icons.AutoMirrored.Outlined.Undo else if(tx.direction==Direction.DEBIT) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest,
                    null,tint=color,modifier=Modifier.padding(12.dp).size(20.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(largeFont) {
                    Text(item.displayName,style=MaterialTheme.typography.titleSmall)
                    Text((if(tx.direction==Direction.DEBIT) "− " else "+ ")+Money.format(tx.amountMinor),color=color,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("transactions.items."+tx.id+".amount"))
                } else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(item.displayName,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                    Text((if(tx.direction==Direction.DEBIT) "− " else "+ ")+Money.format(tx.amountMinor),color=color,style=MaterialTheme.typography.labelLarge,modifier=Modifier.testTag("transactions.items."+tx.id+".amount"))
                }
                item.allocationMinor?.let{Text(Money.format(it)+" allocated of "+Money.format(tx.amountMinor),style=MaterialTheme.typography.labelLarge,color=color)}
                Text(listOfNotNull(item.categoryName,item.accountName).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text((if(tx.verification==Verification.VERIFIED) "✓ " else "◷ ")+status,style=MaterialTheme.typography.labelSmall,color=if(tx.verification==Verification.NEEDS_REVIEW || tx.verification==Verification.PROVISIONAL) financialColors.warning else MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
