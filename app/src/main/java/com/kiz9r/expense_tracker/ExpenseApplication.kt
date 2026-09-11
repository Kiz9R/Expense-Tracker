package com.kiz9r.expense_tracker

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.LedgerDatabase
import com.kiz9r.expense_tracker.security.DatabaseKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@HiltAndroidApp
class ExpenseApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context, keys: DatabaseKeys): LedgerDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
            .openHelperFactory(SupportOpenHelperFactory(keys.databasePassword()))
            .addMigrations(com.kiz9r.expense_tracker.data.MIGRATION_1_2)
            .build()
    }
    @Provides @Singleton fun gson(): Gson = Gson()
}
