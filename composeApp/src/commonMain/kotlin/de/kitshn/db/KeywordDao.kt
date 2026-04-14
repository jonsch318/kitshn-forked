package de.kitshn.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Upsert
    suspend fun upsert(keywords: List<KeywordEntity>)

    @Query("SELECT * FROM KeywordEntity ORDER BY name ASC")
    fun observeAll(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM KeywordEntity ORDER BY name ASC LIMIT :limit")
    suspend fun getAllLimit(limit: Int): List<KeywordEntity>

    @Query("SELECT * FROM KeywordEntity WHERE id = :id")
    suspend fun getById(id: Int): KeywordEntity?

    @Query("SELECT * FROM KeywordEntity WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun getByName(name: String): KeywordEntity?

    @Query("SELECT * FROM KeywordEntity WHERE name LIKE :query || '%' OR full_name LIKE :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<KeywordEntity>

    @Query("DELETE FROM KeywordEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM KeywordEntity")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(keywords: List<KeywordEntity>) {
        deleteAll()
        upsert(keywords)
    }
}