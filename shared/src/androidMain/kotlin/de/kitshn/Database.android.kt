package de.kitshn

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabasePath(context: Context): String {
    return context.getDatabasePath(ROOM_DB_FILE).absolutePath
}

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = getDatabasePath(context)
    )
}
