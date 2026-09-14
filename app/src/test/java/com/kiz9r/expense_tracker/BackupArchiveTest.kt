package com.kiz9r.expense_tracker

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.kiz9r.expense_tracker.backup.*
import org.junit.Test
import org.junit.Assert.*

class BackupArchiveTest {
    private fun emptyJson(): String {
        val names="accounts categories transactions metadata events evidence tags transactionTags merchantRules statementImports statementRows reviewDecisions mandates refundLinks settings"
        return "{\"version\":2,\"createdAt\":1000,"+names.split(' ').joinToString(","){"\"$it\":[]"}+"}"
    }
    @Test fun legacyAndCurrentArchivesDecodeButMissingCollectionsAndInvalidVersionsDoNot() {
        for(version in 1..2) assertEquals(version,BackupArchive.decode(emptyJson().replace("\"version\":2","\"version\":$version").toByteArray(),Gson()).version)
        val bad=listOf("{}",emptyJson().replace("\"accounts\":[]","\"accounts\":null"),
            emptyJson().replace("\"version\":2","\"version\":3"),emptyJson().replace("\"version\":2","\"version\":1.5"),
            emptyJson().replace("\"createdAt\":1000","\"createdAt\":9223372036854775808"),
            emptyJson().replace("\"createdAt\":1000","\"createdAt\":\"1000\""))
        bad.forEach {assertTrue(runCatching {BackupArchive.decode(it.toByteArray(),Gson())}.isFailure)}
    }
    @Test fun malformedRecordPrimitivesNeverBecomeGsonDefaults() {
        val root=JsonParser.parseString(emptyJson()).asJsonObject
        root.getAsJsonArray("accounts").add(JsonParser.parseString("""{"id":"a","nickname":"Synthetic","last4":"4821","accountType":"Savings","bankName":"SBI","currency":"INR","active":true,"createdAt":1000}"""))
        assertEquals(1,BackupArchive.decode(root.toString().toByteArray(),Gson()).accounts.size)
        val account=root.getAsJsonArray("accounts")[0].asJsonObject
        account.addProperty("active","true")
        assertTrue(runCatching {BackupArchive.decode(root.toString().toByteArray(),Gson())}.isFailure)
        account.addProperty("active",true);account.remove("createdAt")
        assertTrue(runCatching {BackupArchive.decode(root.toString().toByteArray(),Gson())}.isFailure)
    }
    @Test fun independentExportsUseFreshSaltAndNonceAndTruncatedEnvelopesFail() {
        val plain=emptyJson().toByteArray();val password="synthetic archive password".toCharArray()
        val first=BackupCrypto.encrypt(plain,password);val second=BackupCrypto.encrypt(plain,password)
        assertFalse(first.contentEquals(second));assertArrayEquals(plain,BackupCrypto.decrypt(second,password))
        listOf(0,10,40,first.size-1).forEach { size -> assertTrue(runCatching {BackupCrypto.decrypt(first.copyOf(size),password)}.isFailure) }
    }
}
