package com.kiz9r.expense_tracker.ui.features

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.data.*

@Composable fun DetailScreen(vm: TrackerViewModel, id: String, edit: (String)->Unit, back: ()->Unit) {
    val item by remember(id) {vm.ledger.detail(id)}.collectAsStateWithLifecycle(null)
    val evidence by remember(id) {vm.ledger.evidence(id)}.collectAsStateWithLifecycle(emptyList())
    val savedTags by remember(id) {vm.ledger.tags(id)}.collectAsStateWithLifecycle(emptyList())
    val categories by vm.categories.collectAsStateWithLifecycle()
    var name by rememberSaveable(id) {mutableStateOf("")}
    var category by rememberSaveable(id) {mutableStateOf("")}
    var notes by rememberSaveable(id) {mutableStateOf("")}
    var tags by rememberSaveable(id) {mutableStateOf("")}
    var delete by remember {mutableStateOf(false)}
    var refundOriginal by rememberSaveable {mutableStateOf("")}
    var showEvidence by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(item?.transaction?.id) { item?.let {name=it.displayName;category=it.categoryId.orEmpty();notes=it.notes} }
    LaunchedEffect(savedTags) {tags=savedTags.joinToString(", ") {it.name}}
    val current=item
    Screen("transactions.detail") {
        if(current==null) {Text("Loading transaction…");return@Screen}
        val tx=current.transaction
        Heading(current.displayName,Money.format(tx.amountMinor)+" · "+tx.direction.name.lowercase())
        Text(tx.date+" · "+current.accountName+" ••••"+current.accountLast4+" · "+tx.channel.name)
        Notice(if(tx.verification==Verification.VERIFIED) "Verified against an SBI statement." else "Detected or manually entered. Statement verification pending.")
        Text("Payment status: "+tx.outcome.name.lowercase().replace('_',' '))
        if(tx.reference.isNotBlank()) Text("Bank reference: "+tx.reference)
        Field(name,{name=it},"Display merchant","transactions.detail.merchant")
        Choice("Category",category,listOf("" to "Other")+categories.map {it.id to it.name},"transactions.detail.category"){category=it}
        Field(notes,{notes=it},"Notes","transactions.detail.notes")
        Field(tags,{tags=it},"Tags (comma separated)","transactions.detail.tags")
        Button(onClick={vm.action("Personal details saved."){vm.ledger.updateMetadata(id,name,category.ifBlank{null},notes,tags)}},
            modifier=Modifier.testTag("transactions.detail.save")){Text("Save personal details")}
        OutlinedButton(onClick={vm.action("Merchant rule created."){vm.ledger.rule(MerchantRuleEntity(
            matchType="exact",matchValue=tx.merchantOriginal,rename=name,categoryId=category.ifBlank{null}))}}){Text("Use these details for this merchant")}
        Toggle("Hide from tracker",current.hidden,"transactions.detail.hide"){vm.action {vm.ledger.hide(id,it)}}
        Toggle("Transfer between my own accounts",tx.ownedTransfer,"transactions.detail.owned-transfer"){vm.action {vm.ledger.ownedTransfer(id,it)}}
        if(tx.manuallyCreated && tx.verification!=Verification.VERIFIED) {
            OutlinedButton(onClick={edit(id)}){Text("Edit amount, date, or account")}
            TextButton(onClick={delete=true}){Text("Delete manual transaction")}
        }
        if(tx.direction==Direction.CREDIT) {
            Heading("Link a refund or reversal")
            Text("Enter the original debit's local transaction ID, shown at the bottom of its detail screen.")
            Field(refundOriginal,{refundOriginal=it},"Original debit ID","transactions.detail.refund.original")
            OutlinedButton(onClick={vm.action("Credit linked to original debit."){vm.reconciliation.linkRefund(refundOriginal,id)}}){Text("Link to debit")}
        }
        TextButton(onClick={showEvidence=!showEvidence}){Text(if(showEvidence) "Hide bank evidence" else "Show bank evidence")}
        if(showEvidence) {
            Text("Original narration"); Text(tx.narration.ifBlank {tx.merchantOriginal})
            evidence.forEach { raw ->
                HorizontalDivider();Text(raw.source.name.replace('_',' '),style=MaterialTheme.typography.titleSmall)
                Text(raw.content);Text("Parser "+raw.parserVersion,style=MaterialTheme.typography.bodySmall)
            }
        }
        Text("Local transaction ID: "+tx.id,style=MaterialTheme.typography.bodySmall)
    }
    if(delete) Confirm("Delete manual transaction?","This removes the local entry. Verified bank transactions can only be hidden.",
        {delete=false;vm.action{vm.ledger.delete(id);back()}},{delete=false})
}
