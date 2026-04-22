package de.kitshn

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDatabasePath(): String {
    return File(System.getProperty("java.io.tmpdir"), ROOM_DB_FILE).absolutePath
}

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(name = getDatabasePath())
}
