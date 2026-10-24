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
        AssistantMessageEntity::class, CategoryLimitEntity::class,
        TxnPlaceEntity::class, CustomCategoryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun settingsDao(): SettingsDao
    abstract fun ruleDao(): RuleDao
    abstract fun personDao(): PersonDao
    abstract fun loanDao(): LoanEntryDao
    abstract fun assistantDao(): AssistantDao
    abstract fun placeDao(): PlaceDao
    abstract fun customCategoryDao(): CustomCategoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Application context, set by the first [get]; used by the sync hooks. */
        @Volatile var appContext: Context? = null
            private set

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                appContext = context.applicationContext
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "salary_tracker.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6) // never a destructive fallback: the data is real
                    .build().also { instance = it }
            }
    }
}
