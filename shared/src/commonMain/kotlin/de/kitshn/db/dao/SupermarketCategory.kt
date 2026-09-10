package de.kitshn.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.kitshn.db.entity.SupermarketCategoryEntity
import de.kitshn.db.entity.SupermarketCategoryPendingDeleteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupermarketCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(categories: List<SupermarketCategoryEntity>)

    @Insert
    suspend fun insert(entity: SupermarketCategoryEntity): Long

    @Update
    suspend fun update(entity: SupermarketCategoryEntity)

    @Query("SELECT * FROM supermarket_category ORDER BY name ASC")
    fun getAllAsFlow(): Flow<List<SupermarketCategoryEntity>>

    @Query("SELECT * FROM supermarket_category WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Int): SupermarketCategoryEntity?

    @Query("SELECT * FROM supermarket_category WHERE id = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Int): SupermarketCategoryEntity?

    @Query("SELECT * FROM supermarket_category WHERE name = :name OR LOWER(name) = :lowercaseName LIMIT 1")
    suspend fun findByName(name: String, lowercaseName: String): SupermarketCategoryEntity?

    @Query("SELECT * FROM supermarket_category WHERE remoteId = :remoteId OR name = :name OR LOWER(name) = :lowercaseName")
    suspend fun findConflicting(
        remoteId: Int,
        name: String,
        lowercaseName: String,
    ): List<SupermarketCategoryEntity>

    @Query("SELECT id FROM supermarket_category WHERE remoteId = :remoteId LIMIT 1")
    suspend fun localIdByRemoteId(remoteId: Int): Int?

    @Query("SELECT remoteId FROM supermarket_category WHERE id = :localId LIMIT 1")
    suspend fun remoteIdByLocalId(localId: Int): Int?

    @Query("DELETE FROM supermarket_category WHERE id = :localId")
    suspend fun deleteByLocalId(localId: Int)

    @Query("DELETE FROM supermarket_category WHERE remoteId IS NOT NULL AND remoteId NOT IN (:serverIds)")
    suspend fun deleteSyncedNotIn(serverIds: List<Int>)

    @Transaction
    suspend fun findOrInsert(entity: SupermarketCategoryEntity): Int {
        findByName(entity.name, entity.name.lowercase())?.let { return it.localId }
        return insert(entity).toInt()
    }

    @Query("UPDATE food SET supermarket_category_id = :winnerLocalId WHERE supermarket_category_id = :loserLocalId")
    suspend fun repointFoods(loserLocalId: Int, winnerLocalId: Int)

    @Query("UPDATE supermarket_category_to_supermarket SET categoryLocalId = :winnerLocalId WHERE categoryLocalId = :loserLocalId")
    suspend fun repointSupermarketJoins(loserLocalId: Int, winnerLocalId: Int)

    /** Returns the surviving localId, which may differ from any localId passed in. */
    @Transaction
    suspend fun upsertByRemoteId(entity: SupermarketCategoryEntity): Int =
        writeServerCategory(entity, null)

    /**
     * Writes the server response for the pending create [stubLocalId]. Returns the surviving
     * localId — when the server folded the stub into a category we already hold, that is the
     * existing row and the stub is gone.
     */
    @Transaction
    suspend fun resolvePendingCreate(stubLocalId: Int, entity: SupermarketCategoryEntity): Int =
        writeServerCategory(entity, stubLocalId)

    private suspend fun writeServerCategory(entity: SupermarketCategoryEntity, stubLocalId: Int?): Int {
        val remoteId = requireNotNull(entity.remoteId) {
            "writeServerCategory requires a non-null remoteId"
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
        repointFoods(loserLocalId, winnerLocalId)
        repointSupermarketJoins(loserLocalId, winnerLocalId)
        deleteByLocalId(loserLocalId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDelete(tombstone: SupermarketCategoryPendingDeleteEntity)

    @Query("SELECT * FROM supermarket_category_pending_delete")
    suspend fun getPendingDeletes(): List<SupermarketCategoryPendingDeleteEntity>

    @Query("SELECT remoteId FROM supermarket_category_pending_delete")
    suspend fun getPendingDeleteRemoteIds(): List<Int>

    @Query("DELETE FROM supermarket_category_pending_delete WHERE remoteId = :remoteId")
    suspend fun deletePendingDelete(remoteId: Int)

    @Query("DELETE FROM supermarket_category")
    suspend fun deleteAll()

    @Query("DELETE FROM supermarket_category_pending_delete")
    suspend fun deleteAllPendingDeletes()
}
