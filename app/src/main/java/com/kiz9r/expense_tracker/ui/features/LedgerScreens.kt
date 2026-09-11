package com.kiz9r.expense_tracker.ui.features

import androidx.compose.foundation.layout.*
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

@Composable fun AccountScreen(vm: TrackerViewModel, onboarding: Boolean, done: ()->Unit) {
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
                Text(account.nickname+" · SBI ••••"+account.last4+" · "+account.accountType,Modifier.weight(1f))
                TextButton(enabled=!busy,onClick={deleteId=account.id},modifier=Modifier.testTag("accounts.items."+account.id+".delete")) {
                    Text("Delete",color=MaterialTheme.colorScheme.error)
                }
            }
        }
        Field(name,{name=it},"Account nickname","accounts.form.nickname")
        Field(digits,{digits=it.take(4)},"Last four account digits","accounts.form.last-four",numeric=true)
        Choice("Account type",type,listOf("Savings" to "Savings","Current" to "Current"),"accounts.form.type"){type=it}
        Button(onClick={vm.action { vm.ledger.addAccount(name,digits,type); name="";digits="";done() }},
            enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("accounts.create")){Text(if(onboarding) "Start tracking" else "Add account")}
        Notice("SMS tracking, notification access, and app lock are optional. Enable them separately in Settings.")
    }
}

@Composable fun AccountFilter(vm: TrackerViewModel, tag: String) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    Choice("Account",filter.accountId.orEmpty(),listOf("" to "All accounts")+accounts.map {it.id to (it.nickname+" ••••"+it.last4)},tag) {
        vm.filter.value=filter.copy(accountId=it.ifBlank {null},page=0)
    }
}
@Composable fun DashboardScreen(vm: TrackerViewModel, open: (String)->Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val previous by vm.previousTotals.collectAsStateWithLifecycle()
    val breakdown by vm.breakdown.collectAsStateWithLifecycle()
    val grouping by vm.grouping.collectAsStateWithLifecycle()
    val largest by vm.largest.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val recent by remember(filter.accountId) { vm.ledger.history(HistoryFilter(accountId=filter.accountId)) }.collectAsStateWithLifecycle(emptyList())
    Screen("dashboard") {
        Heading("Your month, at a glance","Detected transactions are included until your statement verifies them.")
        AccountFilter(vm,"dashboard.filters.account")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={vm.month.value=month.minusMonths(1)},modifier=Modifier.testTag("dashboard.month.previous")){Text("Previous")}
            Text(month.toString(),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={vm.month.value=month.plusMonths(1)},modifier=Modifier.testTag("dashboard.month.next")){Text("Next")}
        }
        Card(Modifier.fillMaxWidth().testTag("dashboard.summary")) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Net spending",style=MaterialTheme.typography.labelLarge)
                Text(Money.format(totals.netSpend),style=MaterialTheme.typography.headlineLarge)
                Text("Income "+Money.format(totals.income)+"  ·  Net cash flow "+Money.format(totals.net))
                Text("Refunds & reversals "+Money.format(totals.refunds))
                Text(totals.count.toString()+" transactions · Previous month spent "+Money.format(previous.netSpend),style=MaterialTheme.typography.bodySmall)
            }
        }
        Heading("Spending breakdown")
        Choice("Group by",grouping,listOf("category" to "Category","merchant" to "Merchant","day" to "Day"),"dashboard.group"){vm.grouping.value=it}
        if(breakdown.isEmpty()) Text("Add your first transaction to see spending insights.")
        breakdown.take(31).forEach { row ->
            Row(Modifier.fillMaxWidth()) { Text(row.label,Modifier.weight(1f));Text(Money.format(row.amount)) }
            LinearProgressIndicator(progress={ if(totals.spend>0) (row.amount.toFloat()/totals.spend).coerceIn(0f,1f) else 0f },modifier=Modifier.fillMaxWidth())
        }
        Heading("Recent transactions")
        recent.take(5).forEach { TransactionCard(it){open(it.transaction.id)} }
        if(largest.isNotEmpty()) Heading("Largest expenses")
        largest.forEach { TransactionCard(it){open(it.transaction.id)} }
        if(recurring.isNotEmpty()) {
            Heading("Possible recurring payments","Same merchant and amount in at least three months. Suggestions only.")
            recurring.forEach { Text(it.label+" · "+Money.format(it.amount)) }
        }
        Spacer(Modifier.height(64.dp))
    }
}
@Composable fun TransactionsScreen(vm: TrackerViewModel, open: (String)->Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var expanded by rememberSaveable {mutableStateOf(false)}
    var minimum by rememberSaveable {mutableStateOf("")}
    var maximum by rememberSaveable {mutableStateOf("")}
    var start by rememberSaveable {mutableStateOf("")}
    var end by rememberSaveable {mutableStateOf("")}
    Screen("transactions") {
        Heading("Transactions")
        Field(filter.query,{vm.filter.value=filter.copy(query=it,page=0)},"Search merchant, notes, tags, amount, reference","transactions.search")
        AccountFilter(vm,"transactions.filters.account")
        TextButton(onClick={expanded=!expanded}){Text(if(expanded) "Close filters" else "More filters")}
        if(expanded) {
            Choice("Direction",filter.direction,listOf("" to "All","DEBIT" to "Expenses","CREDIT" to "Credits"),"transactions.filters.direction"){vm.filter.value=filter.copy(direction=it,page=0)}
            Choice("Verification",filter.verification,listOf("" to "All")+Verification.entries.map {it.name to it.name.lowercase().replace('_',' ')},"transactions.filters.verification"){vm.filter.value=filter.copy(verification=it,page=0)}
            Choice("Category",filter.category.orEmpty(),listOf("" to "All")+categories.map {it.id to it.name},"transactions.filters.category"){vm.filter.value=filter.copy(category=it.ifBlank{null},page=0)}
            Choice("Source",filter.source,listOf("" to "All")+Source.entries.map {it.name to it.name.replace('_',' ')},"transactions.filters.source"){vm.filter.value=filter.copy(source=it,page=0)}
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
                    vm.filter.value=filter.copy(start=from,end=to,minAmount=min,maxAmount=max,page=0)
                }.onFailure {vm.message.value=it.message}
            }){Text("Apply range")}
            Toggle("Include hidden transactions",filter.showHidden,"transactions.filters.hidden"){vm.filter.value=filter.copy(showHidden=it,page=0)}
            TextButton(onClick={vm.filter.value=HistoryFilter();start="";end="";minimum="";maximum=""}){Text("Reset filters")}
        }
        if(transactions.isEmpty()) Notice("No transactions match this view. Add a transaction or import an SBI statement.")
        transactions.forEach { TransactionCard(it){open(it.transaction.id)} }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={vm.filter.value=filter.copy(page=filter.page-1)},enabled=filter.page>0){Text("Previous page")}
            Text("Page "+(filter.page+1))
            TextButton(onClick={vm.filter.value=filter.copy(page=filter.page+1)},enabled=transactions.size==50){Text("Next page")}
        }
        Spacer(Modifier.height(64.dp))
    }
}
@Composable fun TransactionForm(vm: TrackerViewModel, id: String?, done: (String)->Unit) {
    val existing by remember(id) { if(id!=null) vm.ledger.detail(id) else flowOf(null) }.collectAsStateWithLifecycle(null)
    val existingTags by remember(id) { if(id!=null) vm.ledger.tags(id) else flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
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
    LaunchedEffect(existing,existingTags) {
        val item=existing
        if(item!=null && !loaded) {
            val tx=item.transaction;account=tx.accountId;amount=Money.input(tx.amountMinor);direction=tx.direction.name;date=tx.date
            time=tx.timestamp?.let {java.time.Instant.ofEpochMilli(it).atZone(Dates.zone).toLocalTime().withSecond(0).withNano(0).toString()} ?: "12:00"
            merchant=item.displayName;category=item.categoryId.orEmpty();notes=item.notes
            tags=existingTags.joinToString(", ") {it.name}
            if(existingTags.isNotEmpty()) loaded=true
        }
    }
    Screen("transactions.form") {
        Heading(if(id==null) "Add transaction" else "Edit manual transaction")
        Choice("Account",account,accounts.map {it.id to (it.nickname+" ••••"+it.last4)},"transactions.form.account"){account=it}
        Field(amount,{amount=it},"Amount (INR)","transactions.form.amount",numeric=true)
        Choice("Direction",direction,listOf("DEBIT" to "Expense","CREDIT" to "Income"),"transactions.form.direction"){direction=it}
        Field(date,{date=it},"Date (YYYY-MM-DD)","transactions.form.date")
        Field(time,{time=it},"Time (HH:MM, India time)","transactions.form.time")
        Field(merchant,{merchant=it},"Merchant or description","transactions.form.merchant")
        Choice("Category",category,listOf("" to "Other")+categories.map {it.id to it.name},"transactions.form.category"){category=it}
        Field(notes,{notes=it},"Notes","transactions.form.notes")
        Field(tags,{tags=it},"Tags (comma separated)","transactions.form.tags")
        Button(onClick={vm.save(ManualInput(id,account,amount,Direction.valueOf(direction),date,time,merchant,category.ifBlank{null},notes,tags),done)},
            enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("transactions.form.save")){Text("Save transaction")}
    }
}
