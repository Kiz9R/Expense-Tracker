package com.kiz9r.expense_tracker

import android.app.Application
import android.content.Context
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.LedgerDatabase
import com.kiz9r.expense_tracker.security.DatabaseKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class ExpenseApplication : Application() {
    override fun onCreate() { super.onCreate(); com.kiz9r.expense_tracker.planning.monitorBudgets(this) }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context, keys: DatabaseKeys): LedgerDatabase {
        return com.kiz9r.expense_tracker.security.EncryptedLedger.open(context,keys)
    }
    @Provides @Singleton fun gson(): Gson = Gson()
}
