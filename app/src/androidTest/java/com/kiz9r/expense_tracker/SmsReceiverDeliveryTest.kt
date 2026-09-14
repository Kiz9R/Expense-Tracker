package com.kiz9r.expense_tracker

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/** Opt-in: the host sends synthetic long SMS through the emulator console after the ready status. */
class SmsReceiverDeliveryTest {
    @Test fun emulatorBroadcastReachesEncryptedLedger()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("sms_delivery")=="true")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val ledger=dependencies(context).ledger()
        val account=AccountEntity(nickname="Synthetic SMS delivery",last4="8391")
        ledger.db.ledger().saveAccount(account)
        ledger.setting("sms","true")
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("sms_fixture","ready")})
            withTimeout(45000) {
                while(ledger.db.backup().transactions().none {it.accountId==account.id}) delay(200)
            }
            delay(1500)
            val transactions=ledger.db.backup().transactions().filter {it.accountId==account.id}
            assertEquals(1,transactions.size)
            assertEquals(50000L,transactions.single().amountMinor)
            assertEquals(Verification.PROVISIONAL,transactions.single().verification)
            assertEquals("600000009191",transactions.single().reference)
            val evidence=ledger.db.ledger().evidence(transactions.single().id)
            assertTrue(evidence.isNotEmpty());assertTrue(evidence.all {it.source==Source.SBI_SMS})
            val raw=ledger.db.ledger().event(evidence.first().eventId)!!
            assertTrue(raw.content.length>160);assertTrue(raw.content.contains("automated testing"))
            assertTrue(ledger.db.backup().events().none {it.content.contains("SYNTHETIC-OTP-IGNORE")})
        } finally {ledger.deleteAccount(account.id);ledger.setting("sms","false")}
    }
}
