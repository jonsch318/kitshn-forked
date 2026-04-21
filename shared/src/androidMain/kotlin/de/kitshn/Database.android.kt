package de.kitshn

import androidx.room.Room
import androidx.room.RoomDatabase
import coil3.PlatformContext

actual fun getDatabasePath(context: PlatformContext): String {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(ROOM_DB_FILE)
    return dbFile.absolutePath
}

actual fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = getDatabasePath(context)
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = dbFilePath
    )
}
