package com.kiz9r.expense_tracker.ui.features
import com.kiz9r.expense_tracker.ui.theme.financialColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import java.time.LocalTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

@Composable fun AccountScreen(vm: TrackerViewModel, onboarding: Boolean, done: ()->Unit) {
    var restoring by rememberSaveable { mutableStateOf(false) }
    if(onboarding && restoring) {
        Column { TextButton(onClick={restoring=false;vm.clearBackupPreview()}){Text("Back to account setup")}; BackupScreen(vm) }
        return
    }
    var name by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Savings") }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleting = accounts.firstOrNull { it.id == deleteId }
    if(deleting != null) AlertDialog(
        modifier=Modifier.testTag("accounts.modals.delete"),
        onDismissRequest={ if(!busy) deleteId=null },
        title={Text("Delete " + deleting.nickname + "?")},
        text={Text("Permanently remove SBI ••••" + deleting.last4 + " and all its local transactions (including verified and hidden entries), notes, tag links, linked evidence, statements, review decisions, mandates and pending imports.\n\nOther accounts, shared categories, tags, rules and unassigned observations stay. This does not close your SBI bank account.\n\nThis cannot be undone in the app. Export an encrypted backup first if needed; existing backup files are not changed.",modifier=Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(enabled=!busy,onClick={vm.deleteAccount(deleting.id){deleteId=null}},
            modifier=Modifier.testTag("accounts.modals.delete.confirm")){Text("Delete account",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(enabled=!busy,onClick={deleteId=null},
            modifier=Modifier.testTag("accounts.modals.delete.cancel")){Text("Cancel")}}
    )
    Screen("accounts") {
        Heading(if(onboarding) "A clearer view of your money" else "Your SBI accounts",
            "Your expense data stays on this device. No login, no bank password, no payments.")
        if(!onboarding) accounts.forEach { account ->
            Row(Modifier.fillMaxWidth().testTag("accounts.items."+account.id),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(account.nickname+" · SBI ••••"+account.last4+" · "+account.accountType+(if(account.active) "" else " · Archived"),Modifier.weight(1f))
                TextButton(enabled=!busy,onClick={deleteId=account.id},modifier=Modifier.testTag("accounts.items."+account.id+".delete")) {
                    Text("Delete",color=MaterialTheme.colorScheme.error)
                }
            }
        }
        if(!onboarding) accounts.forEach { account->
            Text(account.nickname); AccountMaintenance(vm,account)
        }
        if(onboarding) OutlinedButton(onClick={restoring=true},modifier=Modifier.testTag("accounts.restore")) { Text("Restore an encrypted backup") }
        Field(name,{name=it},"Account nickname","accounts.form.nickname")
        Field(digits,{digits=it.take(4)},"Last four account digits","accounts.form.last-four",numeric=true)
        Choice("Account type",type,listOf("Savings" to "Savings","Current" to "Current"),"accounts.form.type"){type=it}
        Button(onClick={vm.action { vm.ledger.addAccount(name,digits,type); name="";digits="";done() }},
            enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("accounts.create")){Text(if(onboarding) "Start tracking" else "Add account")}
        Notice("SMS tracking, notification access, and app lock are optional. Enable them separately in Settings.")
    }
}

@Composable fun AccountFilter(vm: TrackerViewModel, tag: String, selection: HistoryFilter? = null, change: ((HistoryFilter)->Unit)? = null) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val mainFilter by vm.filter.collectAsStateWithLifecycle()
    val filter=selection ?: mainFilter
    fun update(value: HistoryFilter) {if(change!=null) change(value) else vm.filter.value=value}
    Choice("Account",filter.accountId.orEmpty(),listOf("" to "All accounts")+accounts.map {it.id to (it.nickname+" ••••"+it.last4)},tag) {
        update(filter.copy(accountId=it.ifBlank {null},page=0))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TransactionsScreen(vm: TrackerViewModel, selection: HistoryFilter? = null, change: ((HistoryFilter)->Unit)? = null, open: (String)->Unit) {
    val mainFilter by vm.filter.collectAsStateWithLifecycle()
    val filter=selection ?: mainFilter
    fun update(value: HistoryFilter) {if(change!=null) change(value) else vm.filter.value=value}
    val loaded=key(filter) {
        remember(filter) {vm.ledger.history(filter.copy(limit=150)).map {HistoryLoad(it)}
            .catch {emit(HistoryLoad(error="Could not load transactions. Change or clear filters to retry."))}}
            .collectAsStateWithLifecycle(HistoryLoad()).value
    }
    val transactions=loaded.rows.orEmpty()
    val scroll=rememberLazyListState()
    var shifting by remember {mutableStateOf(false)}
    val base=filter.copy(page=0)
    LaunchedEffect(base) {scroll.scrollToItem(0)}
    LaunchedEffect(transactions) {shifting=false}
    val nearEnd by remember {derivedStateOf {scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0}}
    LaunchedEffect(nearEnd,transactions) {
        if(!shifting && transactions.size==150 && nearEnd>=140) {shifting=true;update(filter.copy(page=filter.page+1))}
    }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var expanded by rememberSaveable {mutableStateOf(false)}
    var minimum by rememberSaveable(filter.minAmount) {mutableStateOf(if(filter.minAmount==0L) "" else Money.input(filter.minAmount))}
    var maximum by rememberSaveable(filter.maxAmount) {mutableStateOf(if(filter.maxAmount==Long.MAX_VALUE) "" else Money.input(filter.maxAmount))}
    var start by rememberSaveable(filter.start) {mutableStateOf(if(filter.start=="1900-01-01") "" else filter.start)}
    var end by rememberSaveable(filter.end) {mutableStateOf(if(filter.end=="2999-12-31") "" else filter.end)}
    Column(Modifier.fillMaxSize().testTag("transactions").padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Field(filter.query,{update(filter.copy(query=it,page=0))},"Search transactions","transactions.search")
        AccountFilter(vm,"transactions.filters.account",filter,::update)
        if(filter.copy(page=0,limit=50)!=HistoryFilter()) {
            Text(historyFilterSummary(filter,categories),modifier=Modifier.testTag("transactions.filters.summary"),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={update(HistoryFilter())},modifier=Modifier.testTag("transactions.filters.clear")){Text("Clear filters")}
        }
        TextButton(onClick={expanded=true},modifier=Modifier.testTag("transactions.filters.open")){Text("Filters"+if(filter.eligible) " · Insights selection" else "")}
        if(expanded) ModalBottomSheet(onDismissRequest={expanded=false}) {
          Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Heading("Refine your history")
            if(filter.eligible) Notice("Exact Insights selection: "+filter.metric.lowercase()+" · "+filter.start+" to "+filter.end+". Reset filters to leave this selection.")
            Choice("Direction",filter.direction,listOf("" to "All","DEBIT" to "Expenses","CREDIT" to "Credits"),"transactions.filters.direction"){update(filter.copy(direction=it,page=0))}
            Choice("Verification",filter.verification,listOf("" to "All")+Verification.entries.map {it.name to it.name.lowercase().replace('_',' ')},"transactions.filters.verification"){update(filter.copy(verification=it,page=0))}
            Choice("Category",filter.category.orEmpty(),listOf("" to "All")+categories.map {it.id to it.name},"transactions.filters.category"){update(filter.copy(category=it.ifBlank{null},page=0))}
            Choice("Source",filter.source,listOf("" to "All")+Source.entries.map {it.name to it.name.replace('_',' ')},"transactions.filters.source"){update(filter.copy(source=it,page=0))}
            Field(start,{start=it},"From date (YYYY-MM-DD)","transactions.filters.start")
            Field(end,{end=it},"To date (YYYY-MM-DD)","transactions.filters.end")
            Field(minimum,{minimum=it},"Minimum amount","transactions.filters.minimum",numeric=true)
            Field(maximum,{maximum=it},"Maximum amount","transactions.filters.maximum",numeric=true)
            Button(onClick={
                runCatching {
                    val from=if(start.isBlank()) "1900-01-01" else java.time.LocalDate.parse(start).toString()
                    val to=if(end.isBlank()) "2999-12-31" else java.time.LocalDate.parse(end).toString()
                    val min=if(minimum.isBlank()) 0 else Money.parse(minimum)
                    val max=if(maximum.isBlank()) Long.MAX_VALUE else Money.parse(maximum)
                    require(from<=to && min<=max) { "Invalid date or amount range." }
                    update(filter.copy(start=from,end=to,minAmount=min,maxAmount=max,page=0));expanded=false
                }.onFailure {vm.message.value=it.message}
            }){Text("Apply range")}
            Toggle("Include hidden transactions",filter.showHidden,"transactions.filters.hidden"){update(filter.copy(showHidden=it,page=0))}
            TextButton(onClick={update(HistoryFilter());start="";end="";minimum="";maximum="";expanded=false}){Text("Reset filters")}
          }
        }
        if(loaded.error!=null) Notice(loaded.error,severity=NoticeSeverity.Error)
        else if(loaded.rows==null) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("transactions.loading"))
        else if(transactions.isEmpty()) Notice("No transactions match this view. Clear filters, add a transaction or import an SBI statement.")
        LazyColumn(state=scroll,modifier=Modifier.weight(1f),contentPadding=PaddingValues(bottom=88.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            item(key="history-start") {
                if(filter.page>0) TextButton(onClick={update(filter.copy(page=(filter.page-2).coerceAtLeast(0)))}){Text("Load earlier in this list")}
            }
            itemsIndexed(transactions,key={_,it->it.transaction.id}) {i,item->
                val date=item.transaction.date
                Column {
                    if(i==0 || transactions[i-1].transaction.date!=date) Text(
                        when(date) {Dates.today().toString()->"Today";Dates.today().minusDays(1).toString()->"Yesterday";else->date},
                        style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
                    TransactionCard(item){open(item.transaction.id)}
                }
            }
            item {if(transactions.size==150) Text("More activity loads as you scroll",style=MaterialTheme.typography.bodySmall)
                else if(transactions.isNotEmpty()) Text("You’re all caught up.",modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)}
        }
    }
}
@Composable fun TransactionForm(vm: TrackerViewModel, id: String?, done: (String)->Unit) {
    val existing by remember(id) { if(id!=null) vm.ledger.detail(id) else flowOf(null) }.collectAsStateWithLifecycle(null)
    val existingTags by remember(id) { if(id!=null) vm.ledger.tags(id) else flowOf(emptyList()) }.collectAsStateWithLifecycle<List<TagEntity>?>(null)
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var account by rememberSaveable {mutableStateOf(vm.filter.value.accountId ?: accounts.firstOrNull()?.id.orEmpty())}
    var amount by rememberSaveable {mutableStateOf("")}
    var direction by rememberSaveable {mutableStateOf("DEBIT")}
    var date by rememberSaveable {mutableStateOf(Dates.today().toString())}
    var time by rememberSaveable {mutableStateOf(LocalTime.now(Dates.zone).withSecond(0).withNano(0).toString())}
    var merchant by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    var notes by rememberSaveable {mutableStateOf("")}
    var tags by rememberSaveable {mutableStateOf("")}
    var loaded by rememberSaveable {mutableStateOf(false)}
    var optional by rememberSaveable {mutableStateOf(id!=null)}
    val context=LocalContext.current
    val pickerStyle=if(MaterialTheme.colorScheme.background.luminance()<.5f) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog
    LaunchedEffect(existing,existingTags) {
        val item=existing
        if(item!=null && existingTags!=null && !loaded) {
            val tx=item.transaction;account=tx.accountId;amount=Money.input(tx.amountMinor);direction=tx.direction.name;date=tx.date
            time=tx.timestamp?.let {java.time.Instant.ofEpochMilli(it).atZone(Dates.zone).toLocalTime().withSecond(0).withNano(0).toString()} ?: "12:00"
            merchant=item.displayName;category=item.categoryId.orEmpty();notes=item.notes
            tags=existingTags.orEmpty().joinToString(", ") {it.name}
            loaded=true
        }
    }
    Screen("transactions.form") {
        Heading(if(id==null) "Add transaction" else "Edit manual transaction")
        Field(amount,{amount=it},"Amount (INR)","transactions.form.amount",numeric=true,
            textColor=financialColors.movement(runCatching{Money.parse(amount)}.getOrDefault(1L),Direction.valueOf(direction)))
        Choice("Direction",direction,listOf("DEBIT" to "Expense","CREDIT" to "Income"),"transactions.form.direction"){direction=it}
        Choice("Account",account,accounts.map {it.id to (it.nickname+" ••••"+it.last4)},"transactions.form.account"){account=it}
        DateControl(date,{date=it},"Date","transactions.form.date")
        OutlinedButton(onClick={ val parsed=LocalTime.parse(time)
            android.app.TimePickerDialog(android.view.ContextThemeWrapper(context,pickerStyle),{_,h,m->time=LocalTime.of(h,m).toString()},parsed.hour,parsed.minute,true).show()
        },modifier=Modifier.fillMaxWidth().testTag("transactions.form.time")){Text("Time · "+time+" · India")}
        Field(merchant,{merchant=it},"Merchant or description","transactions.form.merchant")
        Choice("Category",category,listOf("" to "Other")+categories.map {it.id to it.name},"transactions.form.category"){category=it}
        TextButton(onClick={optional=!optional},modifier=Modifier.testTag("transactions.form.optional")){Text(if(optional) "Hide optional details" else "Add notes & tags")}
        if(optional) {
            Field(notes,{notes=it},"Notes","transactions.form.notes")
            Field(tags,{tags=it},"Tags (comma separated)","transactions.form.tags")
        }
        Button(onClick={vm.save(ManualInput(id,account,amount,Direction.valueOf(direction),date,time,merchant,category.ifBlank{null},notes,tags),done)},
            enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("transactions.form.save")){Text("Save transaction")}
    }
}
