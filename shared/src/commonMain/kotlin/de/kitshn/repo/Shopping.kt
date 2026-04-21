package de.kitshn.repo

import co.touchlab.kermit.Logger
import de.kitshn.AppDatabase
import de.kitshn.api.tandoor.TandoorClient
import de.kitshn.api.tandoor.model.shopping.TandoorShoppingListEntry
import de.kitshn.db.entity.ShoppingListEntryOfflineActions
import de.kitshn.db.entity.ShoppingTransactionEntity
import de.kitshn.db.entity.toAPI
import de.kitshn.db.entity.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.collections.map

class ShoppingRepo(
    db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val dao = db.shoppingDao()
    private val refreshMutex = Mutex()

    fun observe(): Flow<List<TandoorShoppingListEntry>> =
        dao.getAllAsFlow().map { entities -> entities.map { it.toAPI() }}

    suspend fun refresh(client: TandoorClient) {
        if (refreshMutex.isLocked) return

        refreshMutex.withLock {
            try {
                // sync pending first
                syncPending(client)

                val remoteItems = client.shopping.fetchAll()
                withContext(Dispatchers.IO) {
                    dao.syncRemoteItems(remoteItems.map { it.toEntity() })
                }
            } catch (e: Exception) {
                Logger.e(e, tag = "ShoppingRepository") { "Failed to refresh shopping items" }
            }
        }
    }

    suspend fun toggleCheck(client: TandoorClient?, entryId: Int, checked: Boolean) {
        withContext(Dispatchers.IO) {
            // update local DB
            dao.updateChecked(entryId, checked)

            // log transaction
            val action = if (checked) ShoppingListEntryOfflineActions.CHECK else ShoppingListEntryOfflineActions.UNCHECK
            dao.insertTransaction(ShoppingTransactionEntity(entryId = entryId, action = action))
        }

        // attempt sync if online
        if (client != null) {
            scope.launch { syncPending(client) }
        }
    }

    suspend fun delete(client: TandoorClient?, entryId: Int) {
        withContext(Dispatchers.IO) {
            // update local DB
            dao.delete(entryId)

            // log transaction
            dao.insertTransaction(
                ShoppingTransactionEntity(
                    entryId = entryId,
                    action = ShoppingListEntryOfflineActions.DELETE
                )
            )
        }

        // attempt sync if online
        if (client != null) {
            scope.launch { syncPending(client) }
        }
    }

    /**
     * syncPending publishes the transaction log to the server
     */
    suspend fun syncPending(client: TandoorClient) {
        val transactions = withContext(Dispatchers.IO) {
            dao.getPendingTransactions()
        }

        if (transactions.isEmpty()) return

        // We can just take the last transaction from
        // the log of each item and take that as our final state.
        // But in the future maybe even more consolidation e.g. CHECK -> UNCHECK cancels :D
        val consolidated = transactions.groupBy { it.entryId }.mapValues { (_, txs) ->
            txs.last().action
        }

        consolidated.forEach { (entryId, action) ->
            try {
                when (action) {
                    ShoppingListEntryOfflineActions.CHECK -> client.shopping.check(setOf(entryId))
                    ShoppingListEntryOfflineActions.UNCHECK -> client.shopping.uncheck(setOf(entryId))
                    ShoppingListEntryOfflineActions.DELETE -> client.shopping.delete(entryId)
                }

                // Delete all transactions for this entry on success
                val idsToRemove = transactions.filter { it.entryId == entryId }.map { it.id }
                withContext(Dispatchers.IO) {
                    idsToRemove.forEach { dao.deleteTransaction(it) }
                }
            } catch (e: Exception) {
                Logger.e(e, tag = "ShoppingRepository") { "Failed to sync consolidated action $action for entry $entryId" }
            }
        }
    }
}