package com.kiz9r.expense_tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.security.DatabaseKeys
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.encryption.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.*
import org.junit.Assert.*

class PrivacyAndUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun manualTrackingFlowRunsOnEncryptedDatabase() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("accounts.form.nickname").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("navigation.transactions").fetchSemanticsNodes().isNotEmpty()
        }
        if(compose.onAllNodesWithTag("accounts.form.nickname").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("accounts.form.nickname").performTextInput("My SBI")
            compose.onNodeWithTag("accounts.form.last-four").performTextInput("4821")
            compose.onNodeWithTag("accounts.create").performScrollTo().performClick()
        }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("transactions.create").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("transactions.create").performClick()
        compose.onNodeWithTag("transactions.form.amount").performScrollTo().performTextInput("1249.50")
        compose.onNodeWithTag("transactions.form.merchant").performScrollTo().performTextInput("Synthetic Groceries")
        compose.onNodeWithTag("transactions.form.notes").performScrollTo().performTextInput("UI regression")
        compose.onNodeWithTag("transactions.form.save").performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithTag("transactions.detail.merchant").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("transactions.detail.merchant").assertTextContains("Synthetic Groceries")
        compose.onNodeWithTag("transactions.detail.notes").assertTextContains("UI regression")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val header=context.getDatabasePath("ledger.db").inputStream().use {val bytes=ByteArray(16);it.read(bytes);bytes}
        assertFalse(header.toString(Charsets.US_ASCII).startsWith("SQLite format"))
        // Enable capture only for synthetic test data; the production default remains secure.
        runBlocking {dependencies(context).ledger().setting("screenshots","true")}
    }
}

class PdfSecurityIntegrationTest {
    @Test fun scannedInvalidAndOversizeInputsFailAndClearPasswords()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val file=java.io.File(context.cacheDir,"synthetic-empty-test.pdf")
        try {
            PDDocument().use {it.addPage(PDPage());it.save(file)}
            val password="temporary".toCharArray()
            assertTrue(runCatching {PdfTextExtractor(context).read(android.net.Uri.fromFile(file),password)}.isFailure)
            assertTrue(password.all {it=='\u0000'})
            file.writeText("not a PDF")
            val wrong="temporary".toCharArray()
            assertTrue(runCatching {PdfTextExtractor(context).read(android.net.Uri.fromFile(file),wrong)}.isFailure)
            assertTrue(wrong.all {it=='\u0000'})
            assertTrue(runCatching { java.io.ByteArrayInputStream(ByteArray(9)).readBytesLimited(8) }.isFailure)
        } finally {file.delete()}
    }
    @Test fun protectedPdfExtractsOnlyWithCorrectPassword() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val file=java.io.File(context.cacheDir,"synthetic-protected-test.pdf")
        try {
            PDDocument().use {doc ->
                val page=PDPage();doc.addPage(page)
                PDPageContentStream(doc,page).use {stream ->
                    stream.beginText();stream.setFont(PDType1Font.HELVETICA,12f);stream.newLineAtOffset(40f,740f)
                    stream.showText("State Bank of India synthetic test statement");stream.endText()
                }
                val permission=AccessPermission()
                doc.protect(StandardProtectionPolicy("owner-test-password","reader-test-password",permission).apply {encryptionKeyLength=128})
                doc.save(file)
            }
            val extractor=PdfTextExtractor(context)
            val secret="reader-test-password".toCharArray()
            val result=extractor.read(android.net.Uri.fromFile(file),secret)
            assertTrue(result.second.contains("synthetic test statement"))
            assertTrue(secret.all {it=='\u0000'})
            assertTrue(runCatching{extractor.read(android.net.Uri.fromFile(file),"wrong".toCharArray())}.isFailure)
        } finally {file.delete()}
    }
}

class BackgroundImportTest {
    @Test fun workManagerParsesEncryptedStagingAndClearsRawText()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val jobs=dependencies(context).statementJobs()
        val text="State Bank of India\nAccount Number: 12345674821\n"+
            "Statement Period: 01-09-2026 to 30-09-2026\nOpening Balance: 10000.00\n"+
            "Date Value Date Narration Debit Credit Balance\n"+
            "09-09-2026 09-09-2026 UPI/600000000001/SHOP 10.00 0.00 9990.00\nClosing Balance: 9990.00"
        val ledger=dependencies(context).ledger()
        val account=com.kiz9r.expense_tracker.data.AccountEntity(nickname="Synthetic worker",last4="4821")
        ledger.db.ledger().saveAccount(account)
        val id=jobs.enqueue(account.id,"synthetic-job-hash",text)
        try {
            val (job,statement)=kotlinx.coroutines.withTimeout(30000){jobs.await(id)}
            assertEquals("READY",job.status);assertEquals("",job.text)
            assertEquals("4821",statement.last4);assertEquals(1,statement.rows.size)
        } finally {jobs.discard(id);ledger.deleteAccount(account.id)}
    }
}
