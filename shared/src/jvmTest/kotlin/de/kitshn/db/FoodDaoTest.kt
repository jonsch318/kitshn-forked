package de.kitshn.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.kitshn.AppDatabase
import de.kitshn.api.tandoor.model.shopping.TandoorShoppingListEntryCreatedBy
import de.kitshn.db.entity.FoodEntity
import de.kitshn.db.entity.ShoppingItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoodDaoTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        .build()

    private val dao = db.foodDao()

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun insertShoppingItem(foodLocalId: Int): Int =
        db.shoppingDao().insertReturningId(
            ShoppingItemEntity(
                food_id = foodLocalId,
                amount = 1.0,
                order = 0L,
                checked = false,
                created_by = TandoorShoppingListEntryCreatedBy(0, "", ""),
            )
        ).toInt()

    @Test
    fun pendingCreateFoldsIntoTheFoodTheServerReturned() = runBlocking {
        val synced = dao.insert(FoodEntity(remoteId = 42, name = "Milch")).toInt()
        val stub = dao.insert(FoodEntity(name = "Milch ")).toInt()
        val itemId = insertShoppingItem(stub)

        val surviving = dao.resolvePendingCreate(stub, FoodEntity(remoteId = 42, name = "Milch"))

        assertEquals(synced, surviving)
        assertNull(dao.findByLocalId(stub))
        assertEquals(synced, db.shoppingDao().findByLocalId(itemId)?.food_id)
    }

    @Test
    fun pendingCreateKeepsTheStubWhenTheServerFoodIsNew() = runBlocking {
        val stub = dao.insert(FoodEntity(name = "Mehl")).toInt()
        val itemId = insertShoppingItem(stub)

        val surviving = dao.resolvePendingCreate(stub, FoodEntity(remoteId = 9, name = "Mehl"))

        assertEquals(stub, surviving)
        assertEquals(9, dao.findByLocalId(stub)?.remoteId)
        assertEquals(stub, db.shoppingDao().findByLocalId(itemId)?.food_id)
    }

    @Test
    fun upsertKeepsExistingValuesTheServerLeftNull() = runBlocking {
        val local = dao.insert(
            FoodEntity(remoteId = 5, name = "Butter", description = "kept", ignore_shopping = true)
        ).toInt()

        dao.upsertByRemoteId(FoodEntity(remoteId = 5, name = "Butter", plural_name = "Butter"))

        val row = dao.findByLocalId(local)
        assertEquals("kept", row?.description)
        assertEquals("Butter", row?.plural_name)
        assertEquals(true, row?.ignore_shopping)
    }
}
