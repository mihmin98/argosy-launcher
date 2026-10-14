package com.nendo.argosy.desktop.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

abstract class LegacyMigration(startVersion: Int, endVersion: Int) : Migration(startVersion, endVersion) {

    abstract fun migrate(db: SupportSQLiteDatabase)

    final override fun migrate(connection: SQLiteConnection) {
        migrate(ConnectionBackedDb(connection))
    }
}
