package de.kitshn.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.kitshn.db.entity.ShoppingItemEntity
import de.kitshn.db.entity.ShoppingTransactionEntity
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
    suspend fun insertTransaction(tx: ShoppingTransactionEntity)

    @Query("SELECT * FROM ShoppingTransactionEntity ORDER BY id ASC")
    suspend fun getPendingTransactions(): List<ShoppingTransactionEntity>

    @Query("DELETE FROM ShoppingTransactionEntity WHERE id = :id")
    suspend fun deleteTransaction(id: Int)
}