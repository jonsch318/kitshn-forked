package de.kitshn

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.PlatformContext
import de.kitshn.db.Converters
import de.kitshn.db.KeywordDao
import de.kitshn.db.KeywordEntity
import de.kitshn.db.ShoppingDao
import de.kitshn.db.ShoppingItemEntity
import de.kitshn.db.SyncTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [ShoppingItemEntity::class, SyncTransactionEntity::class, KeywordEntity::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
    abstract fun keywordDao(): KeywordDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}


expect fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase>

expect fun getDatabasePath(context: PlatformContext): String

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations()
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
