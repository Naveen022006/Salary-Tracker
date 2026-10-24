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
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
    }
}
