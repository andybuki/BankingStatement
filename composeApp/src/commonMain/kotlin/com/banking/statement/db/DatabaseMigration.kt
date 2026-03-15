package com.banking.statement.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver

/**
 * Centralized database migration callbacks for data migrations that accompany
 * schema migrations defined in .sqm files.
 *
 * Schema migrations (ALTER TABLE, CREATE INDEX, etc.) are handled by SQLDelight's
 * .sqm files automatically. This object provides AfterVersion callbacks for any
 * data migrations that need to run after a schema version upgrade.
 *
 * Migration versioning:
 *   Version 1 -> 2: Added transactions.notes, accounts.updated_at columns (1.sqm)
 */
object DatabaseMigration {

    /** Current database schema version. Update this when adding new migrations. */
    const val CURRENT_VERSION = 2

    /**
     * Returns migration callbacks to pass to the SQL driver.
     * Each callback runs after the corresponding .sqm migration completes.
     */
    fun getMigrationCallbacks(): Array<AfterVersion> = arrayOf(
        AfterVersion(1) { driver ->
            // Data migration after schema v1 -> v2
            // The schema changes (ALTER TABLE) are handled by 1.sqm.
            // Initialize updated_at for existing accounts.
            driver.execute(
                identifier = null,
                sql = "UPDATE accounts SET updated_at = created_at WHERE updated_at IS NULL",
                parameters = 0
            )
        }
    )
}
