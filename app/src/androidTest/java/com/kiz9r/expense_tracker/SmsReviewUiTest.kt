package com.kiz9r.expense_tracker

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SmsReviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun uncertainSmsHasNoPreselectedAccountOrFinancialAction() {
        val graph=dependencies(ApplicationProvider.getApplicationContext<Context>())
        val event=runBlocking {
            graph.ledger().addAccount("SMS UI "+newId().take(6),"8462","Savings")
            val observation=SbiSmsParser.parse("AD-SBIINB","Rs 500 refund initiated for A/c XX8462",System.currentTimeMillis())!!
                .copy(identity=newId())
            graph.reconciliation().enqueue(observation);graph.reconciliation().processPending()
            graph.ledger().db.ledger().eventByIdentity(observation.identity)!!
        }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("navigation.settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation.settings").performClick()
        compose.onNodeWithTag("settings.review").performScrollTo().performClick()
        compose.onNodeWithTag("review.items."+event.id).performScrollTo().performClick()
        compose.onNodeWithTag("review.confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("review.account").assertDoesNotExist()
        compose.onNodeWithTag("review.manual").assertExists()
        compose.onNodeWithTag("review.resolution").performScrollTo().performClick()
        compose.onNodeWithText("Ignore observation").performClick()
        compose.onNodeWithTag("review.confirm").performScrollTo().performClick()
        compose.waitUntil(15000) {runBlocking {graph.ledger().db.ledger().event(event.id)?.reviewReason==null}}
        runBlocking {assertTrue(graph.ledger().db.backup().evidence().none {it.eventId==event.id})}
    }
}
