package de.kitshn

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.PlatformContext
import de.kitshn.db.Converters
import de.kitshn.db.dao.ShoppingDao
import de.kitshn.db.entity.ShoppingItemEntity
import de.kitshn.db.entity.ShoppingTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO


@Database(
    entities = [
        ShoppingItemEntity::class,
        ShoppingTransactionEntity::class
    ],
    version = 1,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor: RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

expect fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase>

expect fun getDatabasePath(context: PlatformContext): String

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}