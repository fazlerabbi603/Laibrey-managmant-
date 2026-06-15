package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // Books Management
    @Query("SELECT * FROM books ORDER BY createdDate DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE bookName = :name LIMIT 1")
    suspend fun getBookByName(name: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Int)

    @Query("SELECT * FROM books WHERE isFavorite = 1")
    fun getFavoriteBooksFlow(): Flow<List<BookEntity>>

    // Covers Management
    @Query("SELECT * FROM covers ORDER BY createdDate DESC")
    fun getAllCoversFlow(): Flow<List<CoverEntity>>

    @Query("SELECT * FROM covers WHERE bookName = :name LIMIT 1")
    suspend fun getCoverByName(name: String): CoverEntity?

    @Query("SELECT * FROM covers WHERE id = :id LIMIT 1")
    suspend fun getCoverById(id: Int): CoverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCover(cover: CoverEntity)

    @Update
    suspend fun updateCover(cover: CoverEntity)

    @Query("DELETE FROM covers WHERE id = :id")
    suspend fun deleteCover(id: Int)
}
