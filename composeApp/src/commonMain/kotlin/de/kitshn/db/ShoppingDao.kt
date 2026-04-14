package de.kitshn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM ShoppingItemEntity ORDER BY `order` ASC")
    fun getAllAsFlow(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM ShoppingItemEntity")
    suspend fun deleteAll()

    @Transaction
    suspend fun syncRemoteItems(items: List<ShoppingItemEntity>) {
        deleteAll()
        insertAll(items)
    }

    @Query("UPDATE ShoppingItemEntity SET checked = :checked WHERE id = :id")
    suspend fun updateChecked(id: Int, checked: Boolean)

    @Query("DELETE FROM ShoppingItemEntity WHERE id = :id")
    suspend fun delete(id: Int)

    // Sync Transactions
    @Insert
    suspend fun insertTransaction(tx: SyncTransactionEntity)

    @Query("SELECT * FROM SyncTransactionEntity ORDER BY id ASC")
    suspend fun getPendingTransactions(): List<SyncTransactionEntity>

    @Query("DELETE FROM SyncTransactionEntity WHERE id = :id")
    suspend fun deleteTransaction(id: Int)
}
