package de.kitshn.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.kitshn.db.entity.FoodEntity
import de.kitshn.db.entity.FoodPendingDeleteEntity
import de.kitshn.db.entity.FoodWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(entity: FoodEntity): Long

    @Update
    suspend fun update(entity: FoodEntity)

    @Transaction
    suspend fun findOrInsert(entity: FoodEntity): Int {
        entity.remoteId?.let { findByRemoteId(it) }?.let { return it.localId }
        findByName(entity.name.lowercase())?.let { return it.localId }
        return insert(entity).toInt()
    }

    @Query("UPDATE ShoppingItemEntity SET food_id = :winnerLocalId WHERE food_id = :loserLocalId")
    suspend fun repointShoppingItems(loserLocalId: Int, winnerLocalId: Int)

    /**
     * Updates preserving existing values that are `null`. Returns the surviving localId,
     * which may differ from any localId passed in.
     */
    @Transaction
    suspend fun upsertByRemoteId(entity: FoodEntity): Int = writeServerFood(entity, null)

    /**
     * Writes the server response for the pending create [stubLocalId]. Returns the surviving
     * localId — when the server folded the stub into a food we already hold, that is the
     * existing row and the stub is gone.
     */
    @Transaction
    suspend fun resolvePendingCreate(stubLocalId: Int, entity: FoodEntity): Int =
        writeServerFood(entity, stubLocalId)

    private suspend fun writeServerFood(entity: FoodEntity, stubLocalId: Int?): Int {
        val remoteId = requireNotNull(entity.remoteId) {
            "writeServerFood requires a non-null remoteId"
        }
        val stub = stubLocalId?.let { findByLocalId(it) }
        val existing = findByRemoteId(remoteId)
            ?: stub
            ?: findStubByName(entity.name.lowercase())
            ?: return insert(entity).toInt()
        if (stub != null && stub.localId != existing.localId) absorb(stub.localId, existing.localId)
        update(entity.withFallbacksFrom(existing))
        return existing.localId
    }

    private suspend fun absorb(loserLocalId: Int, winnerLocalId: Int) {
        repointShoppingItems(loserLocalId, winnerLocalId)
        deleteByLocalId(loserLocalId)
    }

    @Query("SELECT * FROM food WHERE id = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Int): FoodEntity?

    @Query("SELECT * FROM food WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Int): FoodEntity?

    // Prefers a synced row over a stub when both exist.
    @Query("SELECT * FROM food WHERE LOWER(name) = :lowercaseName ORDER BY remoteId IS NULL ASC LIMIT 1")
    suspend fun findByName(lowercaseName: String): FoodEntity?

    @Query("SELECT * FROM food WHERE remoteId IS NULL AND LOWER(name) = :lowercaseName LIMIT 1")
    suspend fun findStubByName(lowercaseName: String): FoodEntity?

    @Query("SELECT remoteId FROM food WHERE id = :localId LIMIT 1")
    suspend fun remoteIdByLocalId(localId: Int): Int?

    @Query("SELECT id FROM food WHERE remoteId = :remoteId LIMIT 1")
    suspend fun localIdByRemoteId(remoteId: Int): Int?

    @Query("SELECT remoteId FROM food WHERE LOWER(name) = :lowercaseName ORDER BY remoteId IS NULL ASC LIMIT 1")
    suspend fun remoteIdByName(lowercaseName: String): Int?

    @Query("SELECT * FROM food WHERE remoteId IS NULL ORDER BY id ASC")
    suspend fun getPendingCreates(): List<FoodEntity>

    @Transaction
    @Query("SELECT * FROM food WHERE id = :localId")
    suspend fun getWithRelationsByLocalId(localId: Int): FoodWithRelations?

    @Transaction
    @Query("SELECT * FROM food ORDER BY name ASC")
    fun getAllWithRelations(): Flow<List<FoodWithRelations>>

    @Query("DELETE FROM food WHERE remoteId IS NOT NULL AND remoteId NOT IN (:serverIds)")
    suspend fun deleteSyncedNotIn(serverIds: List<Int>)

    @Query("DELETE FROM food WHERE id = :localId")
    suspend fun deleteByLocalId(localId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDelete(tombstone: FoodPendingDeleteEntity)

    @Query("SELECT * FROM food_pending_delete")
    suspend fun getPendingDeletes(): List<FoodPendingDeleteEntity>

    @Query("SELECT remoteId FROM food_pending_delete")
    suspend fun getPendingDeleteRemoteIds(): List<Int>

    @Query("DELETE FROM food_pending_delete WHERE remoteId = :remoteId")
    suspend fun deletePendingDelete(remoteId: Int)

    @Query("DELETE FROM food")
    suspend fun deleteAll()

    @Query("DELETE FROM food_pending_delete")
    suspend fun deleteAllPendingDeletes()

    @Transaction
    @Query("""
        SELECT * FROM food
        WHERE name LIKE '%' || :query || '%' OR plural_name LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN LOWER(name) = LOWER(:query) THEN 0
                WHEN LOWER(name) LIKE LOWER(:query) || '%' THEN 1
                ELSE 2
            END,
            LENGTH(name) ASC
        LIMIT 50
    """)
    fun searchWithRelations(query: String): Flow<List<FoodWithRelations>>
}

private fun FoodEntity.withFallbacksFrom(existing: FoodEntity) = copy(
    localId = existing.localId,
    plural_name = plural_name ?: existing.plural_name,
    description = description ?: existing.description,
    url = url ?: existing.url,
    recipe_id = recipe_id ?: existing.recipe_id,
    recipe_name = recipe_name ?: existing.recipe_name,
    recipe_url = recipe_url ?: existing.recipe_url,
    properties_food_amount = properties_food_amount ?: existing.properties_food_amount,
    properties_food_unit_id = properties_food_unit_id ?: existing.properties_food_unit_id,
    fdc_id = fdc_id ?: existing.fdc_id,
    full_name = full_name ?: existing.full_name,
    supermarket_category_id = supermarket_category_id ?: existing.supermarket_category_id,
    ignore_shopping = ignore_shopping || existing.ignore_shopping,
    open_data_slug = open_data_slug ?: existing.open_data_slug,
)
