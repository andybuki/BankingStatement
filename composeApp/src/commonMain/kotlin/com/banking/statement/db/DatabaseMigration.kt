package com.banking.statement.db

import app.cash.sqldelight.db.AfterVersion

/**
 * Centralized database migration callbacks for data migrations that accompany
 * schema migrations defined in .sqm files.
 *
 * Schema migrations (ALTER TABLE, CREATE INDEX, etc.) are handled by SQLDelight's
 * .sqm files automatically. This object provides AfterVersion callbacks for any
 * data migrations that need to run after a schema version upgrade.
 *
 * Migration versioning (each .sqm file increments the version by 1):
 *   1.sqm (v0 -> v1): Initial schema — all tables and indexes
 *   2.sqm (v1 -> v2): Added transactions.notes, accounts.updated_at columns
 */
object DatabaseMigration {

    /**
     * Returns migration callbacks to pass to the SQL driver.
     * Each callback runs after the corresponding .sqm migration completes.
     */
    fun getMigrationCallbacks(): Array<AfterVersion> = arrayOf(
        AfterVersion(2) { driver ->
            // Data migration after schema v1 -> v2 (2.sqm)
            // The schema changes (ALTER TABLE) are handled by 2.sqm.
            // Initialize updated_at for existing accounts.
            driver.execute(
                identifier = null,
                sql = "UPDATE accounts SET updated_at = created_at WHERE updated_at IS NULL",
                parameters = 0
            )
        }
    )
}
