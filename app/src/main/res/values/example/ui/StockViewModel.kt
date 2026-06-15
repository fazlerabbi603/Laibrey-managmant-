package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed interface InventoryUiState {
    object Loading : InventoryUiState
    data class Success(
        val books: List<BookEntity>,
        val covers: List<CoverEntity>,
        val totalBooks: Int,
        val totalAvailableVolumes: Int,
        val totalCovers: Int
    ) : InventoryUiState
}

enum class SetFilter {
    ALL,
    COMPLETE_SET,
    INCOMPLETE_SET,
    HAS_VOL_1,
    HAS_SPECIFIC_VOL,
    RECENTLY_ADDED
}

class StockViewModel(private val repository: StockRepository) : ViewModel() {

    // Filtering & Searching state
    val bookSearchQuery = MutableStateFlow("")
    val coverSearchQuery = MutableStateFlow("")
    val activeFilter = MutableStateFlow(SetFilter.ALL)
    val specificVolumeFilter = MutableStateFlow(1) // for specific volume search

    // Duplicate detection state
    val duplicateDetectedBook = MutableStateFlow<BookEntity?>(null)

    // Favorite Filter
    val showOnlyFavorites = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.populateBrandPromotionalDraft()
        }
    }

    // Processed Books stream combining filters + search
    val filteredBooks: StateFlow<List<BookEntity>> = combine(
        repository.allBooks,
        bookSearchQuery,
        activeFilter,
        specificVolumeFilter,
        showOnlyFavorites
    ) { books, query, filter, specVol, onlyFavs ->
        var list = books

        if (onlyFavs) {
            list = list.filter { it.isFavorite }
        }

        // Apply Search
        if (query.isNotEmpty()) {
            list = list.filter { book ->
                book.bookName.contains(query, ignoreCase = true) ||
                        book.author.contains(query, ignoreCase = true) ||
                        book.publisher.contains(query, ignoreCase = true) ||
                        book.notes.contains(query, ignoreCase = true) ||
                        hasVolumeInBook(book, query)
            }
        }

        // Apply Advanced Filters
        list = when (filter) {
            SetFilter.ALL -> list
            SetFilter.COMPLETE_SET -> list.filter { isCompleteSet(it) }
            SetFilter.INCOMPLETE_SET -> list.filter { !isCompleteSet(it) }
            SetFilter.HAS_VOL_1 -> list.filter { isVolumeAvailable(it, 1) }
            SetFilter.HAS_SPECIFIC_VOL -> list.filter { isVolumeAvailable(it, specVol) }
            SetFilter.RECENTLY_ADDED -> list.sortedByDescending { it.createdDate }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Processed Covers stream combining search
    val filteredCovers: StateFlow<List<CoverEntity>> = combine(
        repository.allCovers,
        coverSearchQuery
    ) { covers, query ->
        if (query.isEmpty()) {
            covers
        } else {
            covers.filter { cover ->
                cover.bookName.contains(query, ignoreCase = true) ||
                        cover.notes.contains(query, ignoreCase = true) ||
                        hasCoverVolume(cover, query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Interactive Stats and Reports
    val statsState: StateFlow<InventoryUiState> = combine(
        repository.allBooks,
        repository.allCovers
    ) { books, covers ->
        val totalBooks = books.size
        
        // Sum total available volumes across all books
        val totalVols = books.sumOf { book ->
            if (book.availableVolumes.trim().isEmpty()) 0 
            else book.availableVolumes.split(",").filter { it.isNotEmpty() }.size
        }

        // Count total pieces of covers from coverVolumes mapping: "3:2,7:1" -> 2 + 1 = 3 pieces
        val totalCoversCount = covers.sumOf { cover ->
            parseCoverVolumes(cover.coverVolumes).values.sum()
        }

        InventoryUiState.Success(
            books = books,
            covers = covers,
            totalBooks = totalBooks,
            totalAvailableVolumes = totalVols,
            totalCovers = totalCoversCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryUiState.Loading)

    // Interactive Actions: Books
    fun saveBook(book: BookEntity, forceAddNew: Boolean = false) {
        viewModelScope.launch {
            if (!forceAddNew) {
                // Check duplicate by name
                val existing = repository.getBookByName(book.bookName)
                if (existing != null && existing.id != book.id) {
                    // Trigger alert dialog
                    duplicateDetectedBook.value = book
                    return@launch
                }
            }
            // Safe to insert/update
            repository.insertBook(book)
            duplicateDetectedBook.value = null
        }
    }

    fun dismissDuplicateDialog() {
        duplicateDetectedBook.value = null
    }

    fun forceUpdateDuplicateBook() {
        val pending = duplicateDetectedBook.value ?: return
        viewModelScope.launch {
            val existing = repository.getBookByName(pending.bookName)
            if (existing != null) {
                val updated = pending.copy(id = existing.id)
                repository.insertBook(updated)
            }
            duplicateDetectedBook.value = null
        }
    }

    fun forceAddNewDuplicateBook() {
        val pending = duplicateDetectedBook.value ?: return
        viewModelScope.launch {
            // Force unique ID
            val brandNew = pending.copy(id = 0)
            repository.insertBook(brandNew)
            duplicateDetectedBook.value = null
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.insertBook(book.copy(isFavorite = !book.isFavorite))
        }
    }

    fun deleteBookById(id: Int) {
        viewModelScope.launch {
            repository.deleteBook(id)
        }
    }

    // Interactive Actions: Covers
    fun saveCover(cover: CoverEntity) {
        viewModelScope.launch {
            repository.insertCover(cover)
        }
    }

    fun deleteCoverById(id: Int) {
        viewModelScope.launch {
            repository.deleteCover(id)
        }
    }

    // Export Helpers
    fun shareBackup(context: Context, format: String, onUriReady: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                val fileContent: String
                val fileName: String
                if (format.equals("csv", ignoreCase = true)) {
                    fileContent = repository.exportToCsvString()
                    fileName = "Library_Stock_Backup.csv"
                } else {
                    fileContent = repository.exportToJson()
                    fileName = "Library_Stock_Backup.json"
                }

                val cacheFile = File(context.cacheDir, fileName)
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(fileContent.toByteArray())
                }

                // FileProvider Uri
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                onUriReady(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Import Helpers
    fun importBackup(context: Context, format: String, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = if (format.equals("csv", ignoreCase = true)) {
                repository.importFromCsv(context, uri)
            } else {
                repository.importFromJson(context, uri)
            }
            onResult(success)
        }
    }

    // UI calculation utility methods
    fun isCompleteSet(book: BookEntity): Boolean {
        if (book.totalVolumes <= 0) return false
        val availableSet = book.availableVolumes.split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        return availableSet.size >= book.totalVolumes
    }

    fun isVolumeAvailable(book: BookEntity, vol: Int): Boolean {
        val availableSet = book.availableVolumes.split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        return availableSet.contains(vol)
    }

    private fun hasVolumeInBook(book: BookEntity, query: String): Boolean {
        val targetVol = query.toIntOrNull() ?: return false
        return isVolumeAvailable(book, targetVol)
    }

    private fun hasCoverVolume(cover: CoverEntity, query: String): Boolean {
        val targetVol = query.toIntOrNull() ?: return false
        val map = parseCoverVolumes(cover.coverVolumes)
        return map.containsKey(targetVol)
    }

    fun parseCoverVolumes(raw: String): Map<Int, Int> {
        if (raw.trim().isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, Int>()
        // Format can be comma-separated key:value like "1:15,2:4,3:42" OR raw text lines like "Volume 1 = 15 pieces"
        // Let's support both robustly!
        val parts = raw.split(",")
        for (part in parts) {
            if (part.contains(":")) {
                val kv = part.split(":")
                if (kv.size == 2) {
                    val k = kv[0].trim().toIntOrNull()
                    val v = kv[1].trim().toIntOrNull()
                    if (k != null && v != null) result[k] = v
                }
            } else if (part.contains("=")) {
                val kv = part.split("=")
                if (kv.size == 2) {
                    val k = kv[0].replace(Regex("[^0-9]"), "").toIntOrNull()
                    val v = kv[1].replace(Regex("[^0-9]"), "").toIntOrNull()
                    if (k != null && v != null) result[k] = v
                }
            }
        }
        return result
    }

    fun formatCoverVolumes(mapping: Map<Int, Int>): String {
        return mapping.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    fun formatCoverVolumesDisplay(raw: String): String {
        val parsed = parseCoverVolumes(raw)
        if (parsed.isEmpty()) return "No covers available"
        return parsed.entries.sortedBy { it.key }.joinToString(", ") { "Vol ${it.key} (${it.value} pcs)" }
    }

    // Reports Generation
    fun getMostIncompleteBooks(books: List<BookEntity>): List<Pair<BookEntity, Int>> {
        // Return books with highest number of missing volumes
        return books.filter { !isCompleteSet(it) }
            .map { book ->
                val availableCount = book.availableVolumes.split(",").filter { it.isNotEmpty() }.size
                val missingCount = book.totalVolumes - availableCount
                Pair(book, missingCount)
            }
            .sortedByDescending { it.second }
            .take(5)
    }

    fun getBooksMissingVolume1(books: List<BookEntity>): List<BookEntity> {
        return books.filter { !isVolumeAvailable(it, 1) }.take(5)
    }

    fun getBooksWithHighestAvailableVolumes(books: List<BookEntity>): List<BookEntity> {
        return books.sortedByDescending { book ->
            book.availableVolumes.split(",").filter { it.isNotEmpty() }.size
        }.take(5)
    }
}
