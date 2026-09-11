package com.kiz9r.expense_tracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.domain.*
import com.kiz9r.expense_tracker.ingestion.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class StatementUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun resumeReviewCommitAndResolveIgnoredRowThroughCompose() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val ledger=dependencies(context).ledger()
        val filename="Synthetic UI "+newId().take(8)+".pdf"
        val jobId=runBlocking {
            ledger.initialize()
            ledger.addAccount("PDF UI "+newId().take(8),"7284","Savings")
            val account=ledger.db.ledger().allAccounts().last {it.last4=="7284"}
            val s=ParsedStatement("7284","2026-07-01","2026-07-31",100000,0,
                listOf(ParsedRow(0,"2026-07-02",null,"SYNTHETIC FIRST","UIFIRST123",50000,Direction.DEBIT,50000),
                    ParsedRow(1,"2026-07-02",null,"SYNTHETIC SECOND","UISECOND123",50000,Direction.DEBIT,0)),emptyList())
            val job=ImportJobEntity(accountId=account.id,fileHash=newId(),fileName=filename,text="",
                status="READY",resultJson=Gson().toJson(s))
            ledger.db.ledger().saveJob(job)
            ledger.setting("screenshots","true")
            job.id
        }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("navigation.statements").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation.statements").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithTag("statements.jobs.resume."+jobId).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("statements.jobs.resume."+jobId).performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithTag("statements.summary").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("statements.review.row.1").performScrollTo().performClick()
        compose.onNodeWithText("Ignore row (keep exception)").performClick()
        compose.waitUntil(15000) {runBlocking {ledger.db.ledger().job(jobId)?.resolutionsJson?.contains("ignore")==true}}
        compose.onNodeWithTag("statements.commit").performScrollTo().performClick()
        compose.waitUntil(15000) {runBlocking {ledger.db.ledger().job(jobId)==null}}
        compose.onNodeWithText(filename).performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithTag("statements.detail.resolve.1").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("statements.detail.resolve.1").performScrollTo().assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("statements.detail.resolution").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("statements.detail.resolution").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.waitUntil(15000) {compose.onAllNodesWithText("Create new verified transaction").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Create new verified transaction").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.onNodeWithTag("statements.detail.confirm").performScrollTo().assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.waitUntil(15000) {compose.onAllNodesWithText("Confirm").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirm").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.waitUntil(15000) {runBlocking {
            ledger.db.backup().statementImports().find {it.fileName==filename}?.status=="RECONCILED"
        }}
        runBlocking {
            val imported=ledger.db.backup().statementImports().single {it.fileName==filename}
            val report=dependencies(context).reconciliation().report(imported.id)
            assertEquals(2,report.rows.mapNotNull {it.transactionId}.distinct().size)
            assertEquals(1,report.decisions.count {it.action=="ignore"})
        }
    }
}
