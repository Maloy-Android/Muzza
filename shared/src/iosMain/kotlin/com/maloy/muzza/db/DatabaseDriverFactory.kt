package com.maloy.muzza.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.maloy.muzza.db.MuzzaDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val fileManager = NSFileManager.defaultManager
        val applicationSupport = fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: throw IllegalStateException("Unable to resolve iOS database directory")

        val databaseUrl: NSURL = applicationSupport.URLByAppendingPathComponent(DATABASE_NAME)
            ?: throw IllegalStateException("Unable to resolve iOS database path")
        val databasePath = databaseUrl.path
            ?: throw IllegalStateException("iOS database path has no filesystem representation")

        return try {
            NativeSqliteDriver(MuzzaDatabase.Schema, databasePath)
        } catch (error: Throwable) {
            fileManager.removeItemAtURL(databaseUrl, error = null)
            NativeSqliteDriver(MuzzaDatabase.Schema, databasePath)
        }
    }
}
