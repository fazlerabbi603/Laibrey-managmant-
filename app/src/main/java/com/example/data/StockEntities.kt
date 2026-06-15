package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookName: String,
    val author: String = "",
    val publisher: String = "",
    val totalVolumes: Int,
    val availableVolumes: String, // Comma-separated list of active volumes: "1,2,5"
    val notes: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Entity(tableName = "covers")
data class CoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookName: String,
    val totalVolumes: Int,
    val coverVolumes: String, // Mapped format "volume:quantity" comma-separated: "1:15,2:4,3:42"
    val notes: String = "",
    val createdDate: Long = System.currentTimeMillis()
)
