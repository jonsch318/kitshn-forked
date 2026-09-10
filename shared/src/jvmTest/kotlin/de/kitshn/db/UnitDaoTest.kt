package de.kitshn.db

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.kitshn.AppDatabase
import de.kitshn.api.tandoor.model.shopping.TandoorShoppingListEntryCreatedBy
import de.kitshn.db.entity.FoodEntity
import de.kitshn.db.entity.ShoppingItemEntity
import de.kitshn.db.entity.UnitEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitDaoTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        .build()

    private val dao = db.unitDao()

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun insertShoppingItem(unitLocalId: Int): Int {
        val foodId = db.foodDao().insert(FoodEntity(name = "Olive oil")).toInt()
        return db.shoppingDao().insertReturningId(
            ShoppingItemEntity(
                food_id = foodId,
                unit_id = unitLocalId,
                amount = 1.0,
                order = 0L,
                checked = false,
                created_by = TandoorShoppingListEntryCreatedBy(0, "", ""),
            )
        ).toInt()
    }

    @Test
    fun pendingCreateFoldsIntoTheUnitTheServerReturned() = runBlocking {
        val synced = dao.insert(UnitEntity(remoteId = 7, name = "EL")).toInt()
        val stub = dao.insert(UnitEntity(name = "EL ")).toInt()
        val itemId = insertShoppingItem(stub)
        val foodId = db.foodDao()
            .insert(FoodEntity(name = "Vinegar", properties_food_unit_id = stub)).toInt()

        val surviving = dao.resolvePendingCreate(stub, UnitEntity(remoteId = 7, name = "EL"))

        assertEquals(synced, surviving)
        assertNull(dao.findByLocalId(stub))
        assertEquals(synced, db.shoppingDao().findByLocalId(itemId)?.unit_id)
        assertEquals(synced, db.foodDao().findByLocalId(foodId)?.properties_food_unit_id)
    }

    @Test
    fun pendingCreateKeepsTheStubWhenTheServerUnitIsNew() = runBlocking {
        val stub = dao.insert(UnitEntity(name = "Pck")).toInt()
        val itemId = insertShoppingItem(stub)

        val surviving = dao.resolvePendingCreate(stub, UnitEntity(remoteId = 12, name = "Pck"))

        assertEquals(stub, surviving)
        assertEquals(12, dao.findByLocalId(stub)?.remoteId)
        assertEquals(stub, db.shoppingDao().findByLocalId(itemId)?.unit_id)
    }

    @Test
    fun upsertFoldsAwayALocalRowHoldingTheServerName() = runBlocking {
        val local = dao.insert(UnitEntity(name = "Öl")).toInt()

        val surviving = dao.upsertByRemoteId(UnitEntity(remoteId = 3, name = "Öl"))

        assertEquals(local, surviving)
        assertEquals(3, dao.findByLocalId(local)?.remoteId)
        assertEquals(local, dao.findByRemoteId(3)?.localId)
    }

    @Test
    fun findOrInsertMatchesAnExistingNameExactly() = runBlocking {
        val first = dao.findOrInsert(UnitEntity(name = "Öl"))
        val second = dao.findOrInsert(UnitEntity(name = "Öl"))

        assertEquals(first, second)
    }
}
