package com.maloy.muzza.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.maloy.muzza.db.MuzzaDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = createIosDatabaseDriver(DATABASE_NAME)
}

internal fun createIosDatabaseDriver(name: String): SqlDriver =
    NativeSqliteDriver(MuzzaDatabase.Schema, name)
