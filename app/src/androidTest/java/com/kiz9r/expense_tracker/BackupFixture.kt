package com.kiz9r.expense_tracker

import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*

/** Every persistent record family, invented values only. Includes pending/review/ignored evidence. */
internal fun completeBackupFixture(): BackupSnapshot {
    val gson=Gson(); val time=1_789_387_200_000L; val day="2026-09-14"
    val account=AccountEntity(id="a1",nickname="Synthetic savings",last4="4821",createdAt=time)
    val row=ParsedRow(0,day,null,"UPI/600000000101/SAMPLE SHOP","600000000101",10000,Direction.DEBIT,10000)
    val statement=ParsedStatement("4821",day,day,20000,10000,listOf(row),emptyList())
    val fingerprint=statement.fingerprint(account.id)
    val identity="statement:$fingerprint:0"
    fun raw(id: String, obs: Observation, processed: Boolean=true, review: String?=null)=RawEventEntity(
        id,obs.identity,obs.source,obs.receivedAt,obs.content,gson.toJson(obs),obs.parserVersion,processed,review)
    val posted=row.observation(identity,"4821").copy(receivedAt=time)
    val refund=Observation(Source.SBI_SMS,"refund-source",time,date=day,accountLast4="4821",amountMinor=2000,
        direction=Direction.CREDIT,merchant="SAMPLE SHOP",reference="600000000102",kind=EventKind.REFUND,content="Synthetic refund")
    val mandate=refund.copy(identity="mandate-source",amountMinor=5000,direction=null,reference="MANDATE60001",kind=EventKind.MANDATE_CREATED,content="Synthetic mandate")
    val unknown=refund.copy(identity="unknown-source",amountMinor=null,direction=null,kind=EventKind.UNKNOWN,content="Synthetic unknown format")
    val pending=refund.copy(identity="pending-source",amountMinor=7500,direction=Direction.DEBIT,kind=EventKind.DEBIT,reference="600000000103",content="Synthetic pending processing")
    return BackupSnapshot(createdAt=time,
        accounts=listOf(account,account.copy(id="a2",nickname="Synthetic secondary",last4="9012")),
        categories=listOf(CategoryEntity("cat","Synthetic category")),
        transactions=listOf(
            TransactionEntity(id="purchase",accountId="a1",amountMinor=10000,direction=Direction.DEBIT,date=day,merchantOriginal="SAMPLE SHOP",reference=row.reference,
                verification=Verification.VERIFIED,outcome=Outcome.PARTIALLY_REFUNDED,createdAt=time,updatedAt=time),
            TransactionEntity(id="refund",accountId="a1",amountMinor=2000,direction=Direction.CREDIT,date=day,merchantOriginal="SAMPLE SHOP",reference=refund.reference,kind=EventKind.REFUND,createdAt=time,updatedAt=time),
            TransactionEntity(id="manual",accountId="a2",amountMinor=500,direction=Direction.DEBIT,date=day,merchantOriginal="MANUAL SAMPLE",manuallyCreated=true,createdAt=time,updatedAt=time)),
        metadata=listOf(MetadataEntity("purchase","My shop","cat","Keep note",hidden=true,userEdited=true)),
        events=listOf(raw("e1",posted),raw("e2",refund),raw("e3",mandate),raw("e4",unknown,review="Choose format"),raw("e5",pending,false),raw("e6",unknown.copy(identity="ignored-source"))),
        evidence=listOf(EvidenceEntity("ev1","purchase","e1",Source.SBI_STATEMENT,"statement-new",true),EvidenceEntity("ev2","refund","e2",Source.SBI_SMS,"new-observation")),
        tags=listOf(TagEntity("tag","Synthetic tag")),transactionTags=listOf(TransactionTagEntity("purchase","tag")),
        merchantRules=listOf(MerchantRuleEntity("rule",matchValue="SAMPLE",rename="My shop",categoryId="cat",priority=10)),
        statementImports=listOf(StatementImportEntity(id="import",accountId="a1",fileName="synthetic.pdf",fileHash="synthetic-file",logicalFingerprint=fingerprint,startDate=day,endDate=day,
            openingBalance=20000,closingBalance=10000,transactionCount=1,status="RECONCILED",importedAt=time,warningsJson="[]")),
        statementRows=listOf(StatementRowEntity("row","import",0,row.fingerprint("a1"),day,null,row.narration,row.reference,10000,Direction.DEBIT,10000,"purchase")),
        reviewDecisions=listOf(ReviewDecisionEntity("decision",identity,"new","purchase",decidedAt=time),ReviewDecisionEntity("ignore","ignored-source","ignore",null,decidedAt=time),ReviewDecisionEntity("mandate-decision","mandate-source","new",null,decidedAt=time)),
        mandates=listOf(MandateEntity("mandate","a1","SAMPLE SHOP",5000,"MANDATE60001","ACTIVE","e3")),
        refundLinks=listOf(RefundLinkEntity("purchase","refund",2000)),
        settings=listOf(SettingEntity("sms","true"),SettingEntity("notifications","true"),SettingEntity("app_lock","false"),SettingEntity("screenshots","false"),SettingEntity("sms_last_received",time.toString())))
}

internal fun backupDigest(snapshot: BackupSnapshot): String {
    val normalized=snapshot.copy(createdAt=1,settings=snapshot.settings.map {
        if(it.key in listOf("sms","notifications"))it.copy(value="false") else it
    })
    val json=Gson().toJsonTree(normalized).asJsonObject
    json.entrySet().forEach { (_,value) -> if(value.isJsonArray) {
        val sorted=value.asJsonArray.toList().sortedBy {it.toString()}
        while(value.asJsonArray.size()>0)value.asJsonArray.remove(0)
        sorted.forEach(value.asJsonArray::add)
    } }
    return hash(json.toString())
}
