package de.kitshn.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kitshn.api.tandoor.model.TandoorKeyword

@Entity
data class KeywordEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val label: String = "",
    val description: String? = "",
    val numchild: Int = 0,
    val created_at: String,
    val updated_at: String,
    val full_name: String = ""
)

fun KeywordEntity.toTandoorEntry() = TandoorKeyword(
    id = id,
    name = name,
    label = label,
    description = description,
    numchild = numchild,
    created_at = created_at,
    updated_at = updated_at,
    full_name = full_name
)

fun TandoorKeyword.toEntry() = KeywordEntity(
    id = id,
    name = name,
    label = label,
    description = description,
    numchild = numchild,
    created_at = created_at,
    updated_at = updated_at,
    full_name = full_name
)
