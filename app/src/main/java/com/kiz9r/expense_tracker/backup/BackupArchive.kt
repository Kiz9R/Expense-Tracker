package com.kiz9r.expense_tracker.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.kiz9r.expense_tracker.ingestion.readBytesLimited
import kotlinx.coroutines.*

/** Archive authentication/decoding is independent of the database and device keys. */
object BackupArchive {
    fun decode(plain: ByteArray, gson: Gson): BackupSnapshot {
        try {
            val root=JsonParser.parseString(plain.toString(Charsets.UTF_8)).asJsonObject
            require(root.get("version")?.isJsonPrimitive==true && root.getAsJsonPrimitive("version").isNumber)
            require(root.get("createdAt")?.isJsonPrimitive==true && root.getAsJsonPrimitive("createdAt").isNumber)
            require(root.get("version")?.asBigDecimal?.intValueExact() in 1..2)
            require(root.get("createdAt")?.asBigDecimal?.longValueExact()?.let { it>0 }==true)
            // Gson can otherwise silently default absent primitives or null collections.
            val required=mapOf(
                "accounts" to "id nickname last4 accountType bankName currency active createdAt",
                "categories" to "id name system",
                "transactions" to "id accountId amountMinor direction date merchantOriginal narration reference channel currency verification outcome kind manuallyCreated ownedTransfer createdAt updatedAt",
                "metadata" to "transactionId merchantDisplay notes hidden userEdited",
                "events" to "id identity source receivedAt content parsedJson parserVersion processed",
                "evidence" to "id transactionId eventId source method verified",
                "tags" to "id name", "transactionTags" to "transactionId tagId",
                "merchantRules" to "id matchType matchValue rename priority enabled",
                "statementImports" to "id accountId fileName fileHash logicalFingerprint startDate endDate openingBalance closingBalance transactionCount status parserVersion importedAt",
                "statementRows" to "id importId sequence fingerprint date narration reference amountMinor direction balance ignored",
                "reviewDecisions" to "id observationKey action reason decidedAt",
                "mandates" to "id merchant reference status eventId",
                "refundLinks" to "originalId refundId amountMinor", "settings" to "key value")
            required.forEach { (name,fields) ->
                val array=requireNotNull(root.get(name)); require(array.isJsonArray)
                array.asJsonArray.forEach { item ->
                    require(item.isJsonObject)
                    item.asJsonObject.entrySet().forEach { (field,value) ->
                        if(field in setOf("categoryId","transactionId","accountId","valueDate","timestamp","amountMinor","warningsJson","reviewReason") && !value.isJsonNull) {
                            require(value.isJsonPrimitive)
                            if(field in setOf("timestamp","amountMinor")) {require(value.asJsonPrimitive.isNumber);value.asBigDecimal.longValueExact()}
                            else require(value.asJsonPrimitive.isString)
                        }
                    }
                    fields.split(' ').forEach { field ->
                        val value=item.asJsonObject.get(field)
                        require(value!=null && value.isJsonPrimitive)
                        val p=value.asJsonPrimitive
                        when(field) {
                            "active","system","manuallyCreated","ownedTransfer","hidden","userEdited","processed","verified","enabled","ignored" -> require(p.isBoolean)
                            "amountMinor","createdAt","updatedAt","receivedAt","priority","openingBalance","closingBalance","transactionCount","importedAt","sequence","balance","decidedAt" -> {
                                require(p.isNumber)
                                if(field in setOf("priority","transactionCount","sequence"))p.asBigDecimal.intValueExact() else p.asBigDecimal.longValueExact()
                            }
                            else -> require(p.isString)
                        }
                    }
                }
            }
            return gson.fromJson(root,BackupSnapshot::class.java).also(::validateBackup)
        } catch(e: Exception) {
            throw IllegalArgumentException("Backup contents are incomplete, unsupported or inconsistent. Existing data was not changed.",e)
        }
    }

    suspend fun inspect(context: Context, uri: Uri, password: CharArray, gson: Gson): BackupSnapshot = withContext(Dispatchers.IO) {
        try {
            val encrypted=requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesLimited(BackupCrypto.MAX_BYTES) }
            val plain=BackupCrypto.decrypt(encrypted,password)
            try { currentCoroutineContext().ensureActive(); decode(plain,gson) }
            finally { plain.fill(0) }
        } finally { password.fill('\u0000') }
    }
}
