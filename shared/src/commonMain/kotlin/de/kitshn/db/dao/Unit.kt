package de.kitshn.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.kitshn.db.entity.UnitEntity
import de.kitshn.db.entity.UnitPendingDeleteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Insert
    suspend fun insert(entity: UnitEntity): Long

    @Transaction
    suspend fun findOrInsert(entity: UnitEntity): Int {
        findByName(entity.name, entity.name.lowercase())?.let { return it.localId }
        return insert(entity).toInt()
    }

    @Update
    suspend fun update(entity: UnitEntity)

    @Query("SELECT * FROM unit ORDER BY name ASC")
    fun getAllAsFlow(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM unit WHERE id = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Int): UnitEntity?

    @Query("SELECT * FROM unit WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Int): UnitEntity?

    @Query("SELECT * FROM unit WHERE name = :name OR LOWER(name) = :lowercaseName LIMIT 1")
    suspend fun findByName(name: String, lowercaseName: String): UnitEntity?

    @Query("SELECT * FROM unit WHERE remoteId = :remoteId OR name = :name OR LOWER(name) = :lowercaseName")
    suspend fun findConflicting(remoteId: Int, name: String, lowercaseName: String): List<UnitEntity>

    @Query("SELECT id FROM unit WHERE remoteId = :remoteId LIMIT 1")
    suspend fun localIdByRemoteId(remoteId: Int): Int?

    @Query("SELECT remoteId FROM unit WHERE id = :localId LIMIT 1")
    suspend fun remoteIdByLocalId(localId: Int): Int?

    @Query("SELECT remoteId FROM unit WHERE LOWER(name) = :lowercaseName LIMIT 1")
    suspend fun remoteIdByName(lowercaseName: String): Int?

    @Query("SELECT * FROM unit WHERE remoteId IS NULL ORDER BY id ASC")
    suspend fun getPendingCreates(): List<UnitEntity>

    @Query("DELETE FROM unit WHERE id = :localId")
    suspend fun deleteByLocalId(localId: Int)

    @Query("DELETE FROM unit WHERE remoteId IS NOT NULL AND remoteId NOT IN (:serverIds)")
    suspend fun deleteSyncedNotIn(serverIds: List<Int>)

    @Query("UPDATE ShoppingItemEntity SET unit_id = :winnerLocalId WHERE unit_id = :loserLocalId")
    suspend fun repointShoppingItems(loserLocalId: Int, winnerLocalId: Int)

    @Query("UPDATE food SET properties_food_unit_id = :winnerLocalId WHERE properties_food_unit_id = :loserLocalId")
    suspend fun repointFoods(loserLocalId: Int, winnerLocalId: Int)

    /** Returns the surviving localId, which may differ from any localId passed in. */
    @Transaction
    suspend fun upsertByRemoteId(entity: UnitEntity): Int = writeServerUnit(entity, null)

    /**
     * Writes the server response for the pending create [stubLocalId]. Returns the surviving
     * localId — when the server folded the stub into a unit we already hold, that is the
     * existing row and the stub is gone.
     */
    @Transaction
    suspend fun resolvePendingCreate(stubLocalId: Int, entity: UnitEntity): Int =
        writeServerUnit(entity, stubLocalId)

    private suspend fun writeServerUnit(entity: UnitEntity, stubLocalId: Int?): Int {
        val remoteId = requireNotNull(entity.remoteId) {
            "writeServerUnit requires a non-null remoteId"
        }
        val stub = stubLocalId?.let { findByLocalId(it) }
        val rows =
            (listOfNotNull(stub) + findConflicting(remoteId, entity.name, entity.name.lowercase()))
                .distinctBy { it.localId }
        val winner = rows.firstOrNull { it.remoteId == remoteId }
            ?: stub
            ?: rows.firstOrNull()
            ?: return insert(entity).toInt()
        rows.forEach { if (it.localId != winner.localId) absorb(it.localId, winner.localId) }
        update(entity.copy(localId = winner.localId))
        return winner.localId
    }

    private suspend fun absorb(loserLocalId: Int, winnerLocalId: Int) {
        repointShoppingItems(loserLocalId, winnerLocalId)
        repointFoods(loserLocalId, winnerLocalId)
        deleteByLocalId(loserLocalId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDelete(tombstone: UnitPendingDeleteEntity)

    @Query("SELECT * FROM unit_pending_delete")
    suspend fun getPendingDeletes(): List<UnitPendingDeleteEntity>

    @Query("SELECT remoteId FROM unit_pending_delete")
    suspend fun getPendingDeleteRemoteIds(): List<Int>

    @Query("DELETE FROM unit_pending_delete WHERE remoteId = :remoteId")
    suspend fun deletePendingDelete(remoteId: Int)

    @Query("DELETE FROM unit")
    suspend fun deleteAll()

    @Query("DELETE FROM unit_pending_delete")
    suspend fun deleteAllPendingDeletes()
}
