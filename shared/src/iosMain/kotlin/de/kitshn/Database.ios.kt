package de.kitshn

import androidx.room.Room
import androidx.room.RoomDatabase
import coil3.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

actual fun getDatabasePath(context: PlatformContext): String {
    return "${documentDirectory()}/$ROOM_DB_FILE"
}

actual fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = getDatabasePath(context)
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}