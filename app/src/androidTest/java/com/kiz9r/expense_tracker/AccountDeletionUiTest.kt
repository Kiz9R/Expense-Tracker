package com.kiz9r.expense_tracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kiz9r.expense_tracker.domain.newId
import com.kiz9r.expense_tracker.ingestion.dependencies
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AccountDeletionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun cancellingKeepsAccountAndConfirmingDeletesIt() {
        val ledger=dependencies(ApplicationProvider.getApplicationContext<Context>()).ledger()
        val name="Delete UI "+newId().take(8)
        val account=runBlocking {
            ledger.addAccount(name,"9012","Savings")
            ledger.db.ledger().allAccounts().single { it.nickname==name }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("navigation.settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("navigation.settings").performClick()
        compose.onNodeWithTag("settings.accounts").performScrollTo().performClick()
        val deleteTag="accounts.items."+account.id+".delete"
        compose.onNodeWithTag(deleteTag).performScrollTo().performClick()
        compose.onNodeWithTag("accounts.modals.delete.cancel").performClick()
        runBlocking { assertTrue(ledger.db.ledger().allAccounts().any { it.id==account.id }) }
        compose.onNodeWithTag(deleteTag).performScrollTo().performClick()
        compose.onNodeWithTag("accounts.modals.delete.confirm").performClick()
        compose.waitUntil(15000) { runBlocking { ledger.db.ledger().allAccounts().none { it.id==account.id } } }
        compose.onNodeWithTag("accounts.modals.delete").assertDoesNotExist()
    }
}
