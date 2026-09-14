package com.kiz9r.expense_tracker

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.kiz9r.expense_tracker.backup.*
import com.kiz9r.expense_tracker.data.*
import com.kiz9r.expense_tracker.ingestion.*
import com.kiz9r.expense_tracker.reconciliation.*
import com.kiz9r.expense_tracker.ui.features.*
import com.kiz9r.expense_tracker.ui.theme.ExpensetrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BackupUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun firstInstallCanValidateCancelAndConfirmRestoreWithoutCreatingAccount() {
        val context=ApplicationProvider.getApplicationContext<Context>();val gson=Gson()
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        val ledger=LedgerRepository(db,gson);val rec=ReconciliationRepository(ledger,gson)
        val service=BackupService(context,db,gson)
        val file=File(context.cacheDir,"synthetic-ui-backup.etbackup")
        file.writeBytes(BackupCrypto.encrypt(gson.toJson(completeBackupFixture()).toByteArray(),"synthetic backup password".toCharArray()))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {addDataType("*/*")},
            Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.fromFile(file))),true)
        try {
            lateinit var vm:TrackerViewModel
            compose.setContent {
                vm=androidx.compose.runtime.remember {TrackerViewModel(ledger,rec,SaveManualTransaction(ledger),PdfTextExtractor(context),service,StatementJobs(context,db,gson))}
                ExpensetrackerTheme {TrackerApp(vm)}
            }
            compose.waitUntil(15000){compose.onAllNodesWithTag("accounts.restore").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("accounts.restore").performScrollTo().performClick()
            compose.onNodeWithTag("backup.select").performScrollTo().performClick()
            compose.waitUntil(15000){runCatching {compose.onAllNodesWithTag("backup.inspect").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            compose.onNodeWithTag("backup.password").performScrollTo().performTextInput("synthetic backup password")
            compose.onNodeWithTag("backup.inspect").performScrollTo().performClick()
            compose.waitUntil(30000){compose.onAllNodesWithTag("backup.restore").fetchSemanticsNodes().isNotEmpty()}
            assertTrue(runBlocking {db.backup().accounts().isEmpty()})
            compose.onNodeWithTag("backup.restore").performScrollTo().performClick()
            compose.onNodeWithText("Cancel",useUnmergedTree=true).performClick()
            assertTrue(runBlocking {db.backup().accounts().isEmpty()})
            compose.onNodeWithTag("backup.restore").performScrollTo().performClick()
            compose.onNodeWithText("Confirm").performClick()
            compose.waitUntil(15000){compose.onAllNodesWithTag("navigation.transactions").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(backupDigest(completeBackupFixture()),backupDigest(runBlocking {service.snapshot()}))
            assertNull(vm.restorePreview.value)
        } finally {instrumentation.removeMonitor(monitor);file.delete();db.close()}
    }
}
