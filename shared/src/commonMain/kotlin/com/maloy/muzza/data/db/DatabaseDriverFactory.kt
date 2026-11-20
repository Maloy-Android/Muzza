package com.maloy.muzza.data.db

import app.cash.sqldelight.db.SqlDriver
import com.maloy.muzza.db.MuzzaDatabase

const val DATABASE_NAME = "Muzza.db"

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): MuzzaDatabase {
    val driver = driverFactory.createDriver()
    return MuzzaDatabase(driver)
}
