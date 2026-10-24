package com.local.salarytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TxnEntity::class, CategoryRuleEntity::class, SettingsEntity::class,
        PersonEntity::class, LoanEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun settingsDao(): SettingsDao
    abstract fun ruleDao(): RuleDao
    abstract fun personDao(): PersonDao
    abstract fun loanDao(): LoanEntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Application context, set by the first [get]; used by the sync hooks. */
        @Volatile var appContext: Context? = null
            private set

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                appContext = context.applicationContext
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "salary_tracker.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // never a destructive fallback: the data is real
                    .build().also { instance = it }
            }
    }
}
