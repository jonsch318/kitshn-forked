package de.kitshn.repo

import co.touchlab.kermit.Logger
import de.kitshn.AppDatabase
import de.kitshn.api.tandoor.TandoorClient
import de.kitshn.api.tandoor.model.TandoorKeyword
import de.kitshn.db.KeywordEntity
import de.kitshn.db.toEntry
import de.kitshn.db.toTandoorEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KeywordRepository(
    db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val dao = db.keywordDao()

    fun observerAll(): Flow<List<TandoorKeyword>> = dao.observeAll().map { list ->
        list.map(
            KeywordEntity::toTandoorEntry
        )
    }

    suspend fun getAll(limit: Int = 100): List<TandoorKeyword> =
        dao.getAllLimit(limit).map(KeywordEntity::toTandoorEntry)


    /**
     * A text search for keywords, including substrings and potentially
     * fuzzy matching
     */
    suspend fun search(query: String, limit: Int = 100): List<TandoorKeyword> =
        dao.search(query, limit).map(KeywordEntity::toTandoorEntry)

    /**
     * Searches for the Keyword with that exact name
     */
    suspend fun getByName(name: String): TandoorKeyword? =
        dao.getByName(name)?.toTandoorEntry()

    suspend fun getById(id: Int): TandoorKeyword? =
        dao.getById(id)?.toTandoorEntry()

    /**
     * Tries to get the keyword from the local database.
     * If not found, fetches it from the server and caches it.
     */
    suspend fun getOrFetch(client: TandoorClient, id: Int): TandoorKeyword {
        // Check local DB
        val local = getById(id)
        if (local != null) return local

        // Fallback to API
        val network = client.keyword.retrieve(id)

        // Cache for future use
        dao.upsert(listOf(network.toEntry()))

        return network
    }

    suspend fun sync(client: TandoorClient){
        val response = client.keyword.retrieve()
        val entities = response.results.map(TandoorKeyword::toEntry)

        dao.upsert(entities)
        // we simply do not remove
    }

    /**
     * Asynchronously fetch keywords in the background
     */
    fun syncAsync(client: TandoorClient){
        scope.launch { sync(client) }
    }

    /**
     * Replace all keywords with the state of the server
     */
    suspend fun syncAsyncAndDelete(client: TandoorClient){
        val response = client.keyword.retrieve()
        val entities = response.results.map(TandoorKeyword::toEntry)
        dao.replaceAll(entities)
        try {
            val response = client.keyword.retrieve()
            val entities = response.results.map(TandoorKeyword::toEntry)

            // This replaces the old data with the new data atomically
            dao.replaceAll(entities)
        } catch (e: Exception) {
            Logger.e(throwable = e, tag = "KeywordRepository") { "Failed to sync keywords" }
        }
    }

    /**
     * Creates a new keyword
     */
    suspend fun create(
        client: TandoorClient,
        name: String,
        description: String
    ): TandoorKeyword {
        val keyword = client.keyword.create(name, description)
        dao.upsert(listOf(keyword.toEntry()))
        return keyword
    }

    /**
     * Ensures a keyword exists on the server
     */
    suspend fun ensureCreated(client: TandoorClient, name: String, description: String): TandoorKeyword {
        val local = getByName(name)
        if (local != null) {
            return local
        }

        return create(client, name, name)
    }
}