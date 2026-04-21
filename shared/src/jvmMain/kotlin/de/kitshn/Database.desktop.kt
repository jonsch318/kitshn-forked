package de.kitshn

import androidx.room.Room
import androidx.room.RoomDatabase
import coil3.PlatformContext
import java.io.File

actual fun getDatabasePath(context: PlatformContext): String {
    return File(System.getProperty("java.io.tmpdir"), ROOM_DB_FILE).absolutePath
}

actual fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = getDatabasePath(context)
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}