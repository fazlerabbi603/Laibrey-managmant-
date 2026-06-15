package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class StockRepository(private val stockDao: StockDao) {

    val allBooks: Flow<List<BookEntity>> = stockDao.getAllBooksFlow()
    val allCovers: Flow<List<CoverEntity>> = stockDao.getAllCoversFlow()
    val favoriteBooks: Flow<List<BookEntity>> = stockDao.getFavoriteBooksFlow()

    suspend fun getBookByName(name: String): BookEntity? = stockDao.getBookByName(name)
    suspend fun getBookById(id: Int): BookEntity? = stockDao.getBookById(id)

    suspend fun insertBook(book: BookEntity) {
        stockDao.insertBook(book)
    }

    suspend fun updateBook(book: BookEntity) {
        stockDao.updateBook(book)
    }

    suspend fun deleteBook(id: Int) {
        stockDao.deleteBook(id)
    }

    suspend fun getCoverByName(name: String): CoverEntity? = stockDao.getCoverByName(name)
    suspend fun getCoverById(id: Int): CoverEntity? = stockDao.getCoverById(id)

    suspend fun insertCover(cover: CoverEntity) {
        stockDao.insertCover(cover)
    }

    suspend fun updateCover(cover: CoverEntity) {
        stockDao.updateCover(cover)
    }

    suspend fun deleteCover(id: Int) {
        stockDao.deleteCover(id)
    }

    // Insert pre-populated brand promoted products
    suspend fun populateBrandPromotionalDraft() {
        if (stockDao.getAllBooksFlow().first().isEmpty()) {
            // "الموسوعة الفقهية" (An ocean of sublime figh insight)
            stockDao.insertBook(
                BookEntity(
                    bookName = "الموسوعة الفقهية",
                    author = "وزارة الأوقاف والشؤون الإسلامية",
                    publisher = "الكويت - কুয়েত ওয়াকফ মন্ত্রণালয়",
                    totalVolumes = 45,
                    availableVolumes = "3,7,12,18,19,24",
                    notes = "ইসলামী আইনশাস্ত্রের এক অনন্য ও রাজকীয় সংগ্রহশালা! ফিকাহ শাস্ত্রের বিশুদ্ধ জ্ঞানের সোনালী প্রদীপ।"
                )
            )

            // "সহীহ আল-বুখারী" (The ultimate premium printed binding)
            stockDao.insertBook(
                BookEntity(
                    bookName = "Sahih al-Bukhari",
                    author = "Imam Bukhari (রহঃ)",
                    publisher = "Darussalam Publication (দারুসসালাম)",
                    totalVolumes = 10,
                    availableVolumes = "1,2,3,4,5,6,7,10",
                    notes = "হাদীস শাস্ত্রের অনন্য চূড়ামণি ও সর্বোত্তম মানের বাঁধাইকৃত সংস্করণ! এক স্বর্গীয় আধ্যাত্মিক পরশ।"
                )
            )

            // "সোনার বাংলা কাব্যগ্রন্থ" (The grand pride of cultural poetry book bindings)
            stockDao.insertBook(
                BookEntity(
                    bookName = "সোনার বাংলা কাব্যগ্রন্থ",
                    author = "কবিগুরু রবীন্দ্রনাথ ঠাকুর",
                    publisher = "ঐতিহ্য প্রকাশনী, ঢাকা",
                    totalVolumes = 5,
                    availableVolumes = "1,2,5",
                    notes = "বাঙালির আত্মপরিচয় ও চমৎকার ডিজাইনের রাজকীয় সংকলন! রাজকীয় কভার ও প্রিমিয়াম ইনার পেজ ফিনিশিং।"
                )
            )
        }

        if (stockDao.getAllCoversFlow().first().isEmpty()) {
            // Covers
            stockDao.insertCover(
                CoverEntity(
                    bookName = "الموسوعة الفقهية",
                    totalVolumes = 45,
                    coverVolumes = "3:2,7:1,12:4",
                    notes = "রাজকীয় স্বর্ণালী ক্যালিগ্রাফি সম্বলিত রাজকীয় কভারের অতিরিক্ত স্টক।"
                )
            )
            stockDao.insertCover(
                CoverEntity(
                    bookName = "Sahih al-Bukhari",
                    totalVolumes = 10,
                    coverVolumes = "1:42,2:18",
                    notes = "প্রিমিয়াম লেদার বাঁধাই ফিনিশিং লেমিনেটেড কভার স্টক।"
                )
            )
        }
    }

    // Export Data into JSON String
    suspend fun exportToJson(): String {
        val booksList = stockDao.getAllBooksFlow().first()
        val coversList = stockDao.getAllCoversFlow().first()

        val root = JSONObject()
        val booksArray = JSONArray()
        for (b in booksList) {
            val jobj = JSONObject().apply {
                put("bookName", b.bookName)
                put("author", b.author)
                put("publisher", b.publisher)
                put("totalVolumes", b.totalVolumes)
                put("availableVolumes", b.availableVolumes)
                put("notes", b.notes)
                put("createdDate", b.createdDate)
                put("isFavorite", b.isFavorite)
            }
            booksArray.put(jobj)
        }

        val coversArray = JSONArray()
        for (c in coversList) {
            val jobj = JSONObject().apply {
                put("bookName", c.bookName)
                put("totalVolumes", c.totalVolumes)
                put("coverVolumes", c.coverVolumes)
                put("notes", c.notes)
                put("createdDate", c.createdDate)
            }
            coversArray.put(jobj)
        }

        root.put("books", booksArray)
        root.put("covers", coversArray)
        return root.toString(2)
    }

    // Import Data from JSON Uri
    suspend fun importFromJson(context: Context, uri: Uri): Boolean {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }
            } ?: return false

            val root = JSONObject(content)
            
            // Read books
            if (root.has("books")) {
                val booksArray = root.getJSONArray("books")
                for (i in 0 until booksArray.length()) {
                    val jobj = booksArray.getJSONObject(i)
                    val bookName = jobj.getString("bookName")
                    
                    // Check duplicate
                    val existing = stockDao.getBookByName(bookName)
                    val book = BookEntity(
                        id = existing?.id ?: 0,
                        bookName = bookName,
                        author = jobj.optString("author", ""),
                        publisher = jobj.optString("publisher", ""),
                        totalVolumes = jobj.getInt("totalVolumes"),
                        availableVolumes = jobj.getString("availableVolumes"),
                        notes = jobj.optString("notes", ""),
                        createdDate = jobj.optLong("createdDate", System.currentTimeMillis()),
                        isFavorite = jobj.optBoolean("isFavorite", false)
                    )
                    stockDao.insertBook(book)
                }
            }

            // Read covers
            if (root.has("covers")) {
                val coversArray = root.getJSONArray("covers")
                for (i in 0 until coversArray.length()) {
                    val jobj = coversArray.getJSONObject(i)
                    val bookName = jobj.getString("bookName")
                    
                    val existing = stockDao.getCoverByName(bookName)
                    val cover = CoverEntity(
                        id = existing?.id ?: 0,
                        bookName = bookName,
                        totalVolumes = jobj.getInt("totalVolumes"),
                        coverVolumes = jobj.getString("coverVolumes"),
                        notes = jobj.optString("notes", ""),
                        createdDate = jobj.optLong("createdDate", System.currentTimeMillis())
                    )
                    stockDao.insertCover(cover)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Export Data to CSV format
    suspend fun exportToCsvString(): String {
        val booksList = stockDao.getAllBooksFlow().first()
        val coversList = stockDao.getAllCoversFlow().first()

        val sb = java.lang.StringBuilder()
        sb.append("TYPE,NAME,AUTHOR_OR_TOTAL,PUBLISHER,TOTAL_VOLUMES,VOLUMES_DATA,NOTES\n")

        for (b in booksList) {
            // Escape values
            val name = escapeCsv(b.bookName)
            val auth = escapeCsv(b.author)
            val pub = escapeCsv(b.publisher)
            val notes = escapeCsv(b.notes)
            sb.append("BOOK,$name,$auth,$pub,${b.totalVolumes},${b.availableVolumes},$notes\n")
        }

        for (c in coversList) {
            val name = escapeCsv(c.bookName)
            val notes = escapeCsv(c.notes)
            sb.append("COVER,$name,Cover-Stock,,${c.totalVolumes},${c.coverVolumes},$notes\n")
        }

        return sb.toString()
    }

    // Import Data from CSV Uri
    suspend fun importFromCsv(context: Context, uri: Uri): Boolean {
        return try {
            val reader = context.contentResolver.openInputStream(uri)?.let { stream ->
                BufferedReader(InputStreamReader(stream))
            } ?: return false

            var line: String? = reader.readLine() // Header line
            while (reader.readLine().also { line = it } != null) {
                val tokens = parseCsvLine(line!!)
                if (tokens.size >= 7) {
                    val type = tokens[0]
                    val name = tokens[1]
                    val field3 = tokens[2]
                    val field4 = tokens[3]
                    val totalVols = tokens[4].toIntOrNull() ?: 1
                    val volsData = tokens[5]
                    val notes = tokens[6]

                    if (type == "BOOK") {
                        val existing = stockDao.getBookByName(name)
                        val b = BookEntity(
                            id = existing?.id ?: 0,
                            bookName = name,
                            author = field3,
                            publisher = field4,
                            totalVolumes = totalVols,
                            availableVolumes = volsData,
                            notes = notes
                        )
                        stockDao.insertBook(b)
                    } else if (type == "COVER") {
                        val existing = stockDao.getCoverByName(name)
                        val c = CoverEntity(
                            id = existing?.id ?: 0,
                            bookName = name,
                            totalVolumes = totalVols,
                            coverVolumes = volsData,
                            notes = notes
                        )
                        stockDao.insertCover(c)
                    }
                }
            }
            reader.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return if (clean.contains(",") || clean.contains("\n") || clean.contains("\"")) {
            "\"$clean\""
        } else {
            clean
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString().trim())
                    curVal = StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString().trim())
        return result
    }
}
