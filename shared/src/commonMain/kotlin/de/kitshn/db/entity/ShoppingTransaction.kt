package de.kitshn.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ShoppingListEntryOfflineActions {
    CHECK,
    UNCHECK,
    DELETE
}

@Entity
data class ShoppingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryId: Int,
    val action: ShoppingListEntryOfflineActions,
    val timestamp: Long = 0L
)