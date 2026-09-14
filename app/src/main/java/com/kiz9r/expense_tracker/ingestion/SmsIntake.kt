package com.kiz9r.expense_tracker.ingestion

import androidx.room.withTransaction
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import javax.inject.Inject
import javax.inject.Singleton

data class SmsPart(val sender: String, val body: String, val timestamp: Long)

fun assembleSms(parts: List<SmsPart>, receivedAt: Long): Observation? {
    if(parts.isEmpty() || parts.size > 64 || parts.any { it.body.isEmpty() }) return null
    val sender=parts.first().sender.trim().uppercase(java.util.Locale.ROOT)
    if(parts.any { it.sender.trim().uppercase(java.util.Locale.ROOT)!=sender }) return null
    if(parts.sumOf { it.body.length } > 16384) return null
    return SbiSmsParser.parse(sender,parts.joinToString("") { it.body },receivedAt,parts.first().timestamp)
}

/** The same opt-in/permission/unlock gate is exercised by receiver integration tests. */
@Singleton
class SmsIntake @Inject constructor(private val ledger: LedgerRepository) {
    suspend fun accept(parts: List<SmsPart>, receivedAt: Long, permissionGranted: Boolean, unlocked: Boolean): Boolean {
        if(!permissionGranted || !unlocked) return false
        val observation=assembleSms(parts,receivedAt) ?: return false
        return ledger.db.withTransaction {
            if(!ledger.enabled("sms")) return@withTransaction false
            val dao=ledger.db.ledger()
            if(dao.insertEvent(ledger.raw(observation)) != -1L) {
                dao.saveSetting(SettingEntity("sms_last_received",receivedAt.toString()))
            }
            true
        }
    }
}
