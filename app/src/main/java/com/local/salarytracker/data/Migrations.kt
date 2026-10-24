package com.local.salarytracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration v1 -> v2 (cloud sync). Kept as plain SQL strings so a JVM test can run them on a real SQLite.
 * Existing rows get a random UUID, are marked dirty (so they upload on first sign-in) and are not deleted.
 */
object MigrationSql {
    /** SQLite expression for a random version-4 UUID (lower case, 8-4-4-4-12). */
    const val UUID_EXPR =
        "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' || " +
            "substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

    val V1_TO_V2: List<String> = listOf(
        "ALTER TABLE `txn` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `txn` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `txn` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `txn` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1",
        "UPDATE `txn` SET `remoteId` = $UUID_EXPR",
        "UPDATE `txn` SET `updatedAt` = `createdAt`",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_remoteId` ON `txn` (`remoteId`)",
        "ALTER TABLE `category_rule` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `category_rule` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `category_rule` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1",
        "UPDATE `category_rule` SET `updatedAt` = CAST(strftime('%s', 'now') AS INTEGER) * 1000",
    )

    /**
     * v2 -> v3 (Lending): two new tables only, nothing existing is touched. The statements are identical to the
     * createSql of the exported schemas/.../3.json (MigrationSqlTest checks that).
     */
    val V2_TO_V3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` TEXT NOT NULL, " +
            "`name` TEXT NOT NULL, `phone` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_person_remoteId` ON `person` (`remoteId`)",
        "CREATE TABLE IF NOT EXISTS `loan_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` TEXT NOT NULL, " +
            "`personRemoteId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `method` TEXT NOT NULL, " +
            "`appName` TEXT, `occurredAt` INTEGER NOT NULL, `dueDate` TEXT, `note` TEXT, `updatedAt` INTEGER NOT NULL, " +
            "`deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_entry_remoteId` ON `loan_entry` (`remoteId`)",
        "CREATE INDEX IF NOT EXISTS `index_loan_entry_personRemoteId` ON `loan_entry` (`personRemoteId`)",
    )

    /** v3 -> v4 (Ask tab): chat history and category limits. Only new tables; both stay on the phone. */
    val V3_TO_V4: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `assistant_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`fromUser` INTEGER NOT NULL, `text` TEXT NOT NULL, `payload` TEXT, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_assistant_message_createdAt` ON `assistant_message` (`createdAt`)",
        "CREATE TABLE IF NOT EXISTS `category_limit` (`category` TEXT NOT NULL, `limitPaise` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`category`))",
    )

    /** v4 -> v5 (Spending map): where an entry was paid. New table only; stays on the phone. */
    val V4_TO_V5: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `txn_place` (`txnRemoteId` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
            "`accuracyM` REAL NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`txnRemoteId`))",
    )

    /** v5 -> v6 (own categories): the category name on each entry + the look of each own category. */
    val V5_TO_V6: List<String> = listOf(
        "ALTER TABLE `txn` ADD COLUMN `customCategory` TEXT DEFAULT NULL",
        "CREATE TABLE IF NOT EXISTS `custom_category` (`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`name`))",
    )
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V2_TO_V3.forEach { db.execSQL(it) }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V3_TO_V4.forEach { db.execSQL(it) }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V4_TO_V5.forEach { db.execSQL(it) }
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V5_TO_V6.forEach { db.execSQL(it) }
    }
}
