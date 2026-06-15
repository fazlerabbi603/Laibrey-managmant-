package com.example.ui

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BookEntity
import com.example.data.CoverEntity
import java.util.*

enum class AppTab {
    DASHBOARD,
    BOOKS,
    COVERS,
    REPORTS,
    BACKUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockUiApp(viewModel: StockViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }

    // Forms opening state
    var showAddBookDialog by remember { mutableStateOf(false) }
    var showAddCoverDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<BookEntity?>(null) }
    var editingCover by remember { mutableStateOf<CoverEntity?>(null) }
    var selectedBookDetail by remember { mutableStateOf<BookEntity?>(null) }

    // Scanner triggering
    var showScannerDialog by remember { mutableStateOf(false) }
    var ocrTargetCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val filteredBooksList by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val filteredCoversList by viewModel.filteredCovers.collectAsStateWithLifecycle()
    val statsStateValue by viewModel.statsState.collectAsStateWithLifecycle()

    // Duplicate detection popup state
    val duplicateBookPending by viewModel.duplicateDetectedBook.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_root"),
        containerColor = Color(0xFF0F0F0F),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF161616),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(80.dp)
            ) {
                listOf(
                    Triple(AppTab.DASHBOARD, Icons.Default.Dashboard, "Home"),
                    Triple(AppTab.BOOKS, Icons.Default.Book, "Books"),
                    Triple(AppTab.COVERS, Icons.Default.MenuBook, "Covers"),
                    Triple(AppTab.REPORTS, Icons.Default.Assessment, "Reports"),
                    Triple(AppTab.BACKUP, Icons.Default.Backup, "Export")
                ).forEach { (tab, icon, label) ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color(0xFF0D6EFD) else Color(0xFF94A3B8)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D6EFD) else Color(0xFF94A3B8)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFF0D6EFD).copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> DashboardScreen(
                    statsState = statsStateValue,
                    viewModel = viewModel,
                    onOpenAddBook = {
                        ocrTargetCallback = null
                        showAddBookDialog = true
                    },
                    onOpenAddCover = {
                        showAddCoverDialog = true
                    },
                    onOpenScanShortcut = {
                        ocrTargetCallback = { scannedText ->
                            // Open add book direct and fill name
                            showAddBookDialog = true
                        }
                        showScannerDialog = true
                    },
                    onBookClick = { selectedBookDetail = it },
                    onTabSwitch = { currentTab = it }
                )

                AppTab.BOOKS -> BooksInventoryScreen(
                    books = filteredBooksList,
                    onBookClick = { selectedBookDetail = it },
                    onEditBook = {
                        editingBook = it
                        showAddBookDialog = true
                    },
                    onDeleteBook = { viewModel.deleteBookById(it.id) },
                    onAddBookClick = {
                        editingBook = null
                        showAddBookDialog = true
                    },
                    viewModel = viewModel
                )

                AppTab.COVERS -> CoversInventoryScreen(
                    covers = filteredCoversList,
                    onAddCoverClick = {
                        editingCover = null
                        showAddCoverDialog = true
                    },
                    onEditCover = {
                        editingCover = it
                        showAddCoverDialog = true
                    },
                    onDeleteCover = { viewModel.deleteCoverById(it.id) },
                    viewModel = viewModel
                )

                AppTab.REPORTS -> ReportsScreen(
                    statsState = statsStateValue,
                    viewModel = viewModel
                )

                AppTab.BACKUP -> BackupScreen(viewModel = viewModel)
            }

            // Book creation & edit Dialog
            if (showAddBookDialog) {
                AddEditBookDialog(
                    editingBook = editingBook,
                    onDismiss = {
                        showAddBookDialog = false
                        editingBook = null
                    },
                    onSave = { entity ->
                        viewModel.saveBook(entity)
                        showAddBookDialog = false
                        editingBook = null
                    },
                    onOpenScanner = { callback ->
                        ocrTargetCallback = callback
                        showScannerDialog = true
                    }
                )
            }

            // Cover creation & edit Dialog
            if (showAddCoverDialog) {
                AddEditCoverDialog(
                    editingCover = editingCover,
                    onDismiss = {
                        showAddCoverDialog = false
                        editingCover = null
                    },
                    onSave = { entity ->
                        viewModel.saveCover(entity)
                        showAddCoverDialog = false
                        editingCover = null
                    },
                    onOpenScanner = { callback ->
                        ocrTargetCallback = callback
                        showScannerDialog = true
                    },
                    viewModel = viewModel
                )
            }

            // Detail view overlay
            selectedBookDetail?.let { book ->
                BookDetailDialog(
                    book = book,
                    onDismiss = { selectedBookDetail = null },
                    onToggleFavorite = { viewModel.toggleFavorite(book) }
                )
            }

            // Live/Simulated OCR flow dialog
            if (showScannerDialog) {
                CameraOcrScannerDialog(
                    onDismiss = {
                        showScannerDialog = false
                        ocrTargetCallback = null
                    },
                    onTitleDetected = { title ->
                        ocrTargetCallback?.invoke(title)
                        showScannerDialog = false
                        ocrTargetCallback = null
                    }
                )
            }

            // Duplicate warnings detection alert
            duplicateBookPending?.let { pendingBook ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissDuplicateDialog() },
                    title = {
                        Text(
                            text = "বইটি পূর্বে থেকেই বিদ্যমান!",
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "লাইব্রেরিতে \"${pendingBook.bookName}\" নামের বইটি ইতিমধ্যে তালিকাভুক্ত রয়েছে। আপনি কি বিদ্যমান রেকর্ডটি আপডেট করতে চান নাকি নতুন হিসেবে যোগ করতে চান?",
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "💡 ব্রান্ড রিকমেন্ডেশন: ডেটা অভিন্ন রাখতে আপডেট প্যানেল ব্যবহার করাই শ্রেয়।",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.forceUpdateDuplicateBook() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD)),
                            modifier = Modifier.testTag("dup_dialog_update")
                        ) {
                            Text("হালনাগাদ / Update")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { viewModel.forceAddNewDuplicateBook() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)),
                            modifier = Modifier.testTag("dup_dialog_new")
                        ) {
                            Text("নতুন যোগ / Add New")
                        }
                    },
                    containerColor = Color(0xFF1E1E1E),
                    modifier = Modifier.testTag("duplicate_warning_dialog")
                )
            }
        }
    }
}

// FORMAT DATE helper
fun formatLongDate(time: Long): String {
    val date = Date(time)
    return DateFormat.format("dd MMM yyyy, hh:mm a", date).toString()
}

@Composable
fun DashboardScreen(
    statsState: InventoryUiState,
    viewModel: StockViewModel,
    onOpenAddBook: () -> Unit,
    onOpenAddCover: () -> Unit,
    onOpenScanShortcut: () -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onTabSwitch: (AppTab) -> Unit
) {
    val context = LocalContext.current
    var titlesCount = 0
    var volumesCount = 0
    var coversCount = 0
    var latestItems = emptyList<Any>()

    if (statsState is InventoryUiState.Success) {
        titlesCount = statsState.totalBooks
        volumesCount = statsState.totalAvailableVolumes
        coversCount = statsState.totalCovers

        // Mix books and covers, sorted by newest
        val sortedBooks = statsState.books.sortedByDescending { it.createdDate }
        val sortedCovers = statsState.covers.sortedByDescending { it.createdDate }
        latestItems = (sortedBooks + sortedCovers).sortedByDescending {
            when (it) {
                is BookEntity -> it.createdDate
                is CoverEntity -> it.createdDate
                else -> 0L
            }
        }.take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // App header & promotional banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(Color(0xFF0D6EFD), RoundedCornerShape(12.dp))
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Stock Manager",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "LIBRARY & BINDING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFFFC107)
                    )
                }
            }

            // Quick Floating Scan Trigger
            IconButton(
                onClick = onOpenScanShortcut,
                modifier = Modifier
                    .background(Color(0xFF1E1E1E), CircleShape)
                    .border(1.dp, Color(0xFF2E2E2E), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan name",
                    tint = Color(0xFFFFC107)
                )
            }
        }

        // Promotional Brand Announcement (Bengali additional requirement helper)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D6EFD).copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color(0xFF0D6EFD).copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌟",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "রাজকীয় বাঁধাই ও প্রিমিয়াম বুক কভার স্টক",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "বিশ্বখ্যাত আল-মওসূআহ সিরিজ ও বুখারী শরীফের মতো সকল লাক্সারি সংস্করণের অবশিষ্ট ভলিউম এবং স্বর্গীয় অলঙ্কৃত আর্ট-কভার স্টক ট্র্যাক করুন নিমেষেই!",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Stats Cards Grid
        Text(
            text = "পরিসংখ্যান বিবরণী / Quick Stats",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Book Titles
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSwitch(AppTab.BOOKS) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Titles", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Text("$titlesCount", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D6EFD))
                    Text("সংযোজিত বইসমূহ 📚", fontSize = 9.sp, color = Color(0xFF64748B))
                }
            }

            // Available Volumes
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSwitch(AppTab.BOOKS) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Volumes", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Text("$volumesCount", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF198754))
                    Text("মোট ভলিউম স্টক 📖", fontSize = 9.sp, color = Color(0xFF64748B))
                }
            }

            // Cover Entries
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSwitch(AppTab.COVERS) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Covers", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Text("$coversCount", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFC107))
                    Text("আর্ট কভার পিস 🎨", fontSize = 9.sp, color = Color(0xFF64748B))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Actions shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onOpenAddBook,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("add_book_shortcut")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("নতুন বই লিখুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onOpenAddCover,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("add_cover_shortcut")
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("নতুন কভার লিখুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SCAN BANNER LINK
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenScanShortcut() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFFC107).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Ocr Scanner",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ক্যামেরা স্ক্যানার চালু করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "আরবি, বাংলা এবং ইংরেজি কভার ফটো OCR দিয়ে অটো-ডিটেক্ট করুন",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recently Managed Items Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "সম্প্রতি হালনাগাদকৃত স্টক / New Arrivals",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "সব দেখুন",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D6EFD),
                modifier = Modifier.clickable { onTabSwitch(AppTab.BOOKS) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (latestItems.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "কোনো ডাটা সংযোজিত হয়নি। কভার স্ক্যানার বা বাটমে ক্লিক করে শুরু করুন!",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                latestItems.forEach { item ->
                    if (item is BookEntity) {
                        BookItemRow(
                            book = item,
                            onClick = { onBookClick(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
                    } else if (item is CoverEntity) {
                        CoverItemRow(
                            cover = item,
                            onEdit = {},
                            onDelete = {},
                            showActions = false,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// ---------------------- BOOKS INVENTORY SECTION ----------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BooksInventoryScreen(
    books: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    onEditBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onAddBookClick: () -> Unit,
    viewModel: StockViewModel
) {
    val searchQuery by viewModel.bookSearchQuery.collectAsStateWithLifecycle()
    val activeFilterVal by viewModel.activeFilter.collectAsStateWithLifecycle()
    val showFavsOnly by viewModel.showOnlyFavorites.collectAsStateWithLifecycle()
    val specificVol by viewModel.specificVolumeFilter.collectAsStateWithLifecycle()

    var showAdvancedFilters by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tab Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "সংরক্ষিত বইসমূহ (Books Inventory)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "পরপর সাজানো অবশিষ্ট গ্রন্থ ও খণ্ডের লাইভ তালিকা",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            IconButton(
                onClick = onAddBookClick,
                modifier = Modifier
                    .background(Color(0xFF0D6EFD), CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add book", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar with advanced filter access
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.bookSearchQuery.value = it },
                placeholder = { Text("বইয়ের নাম, লেখক, খণ্ড বা কীওয়ার্ড দিয়ে খুঁজুন...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.bookSearchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    disabledContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("book_search_input")
            )

            // Filter button
            IconButton(
                onClick = { showAdvancedFilters = !showAdvancedFilters },
                modifier = Modifier
                    .background(if (showAdvancedFilters) Color(0xFF0D6EFD) else Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(12.dp))
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Selection",
                    tint = if (showAdvancedFilters) Color.White else Color.LightGray
                )
            }
        }

        // Advanced filter expander
        AnimatedVisibility(
            visible = showAdvancedFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "ফিল্টার অপশন সিলেক্ট করুন:",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Row of filter chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair(SetFilter.ALL, "সব বই (All)"),
                        Pair(SetFilter.COMPLETE_SET, "পূর্ণাঙ্গ সেট"),
                        Pair(SetFilter.INCOMPLETE_SET, "অপূর্ণাঙ্গ"),
                        Pair(SetFilter.HAS_VOL_1, "১ম খণ্ড আছে"),
                        Pair(SetFilter.HAS_SPECIFIC_VOL, "নির্দিষ্ট খণ্ড"),
                        Pair(SetFilter.RECENTLY_ADDED, "সম্প্রতি যুক্ত")
                    ).forEach { (f, label) ->
                        val isSelected = activeFilterVal == f
                        Box(
                            modifier = Modifier
                                .clickable { viewModel.activeFilter.value = f }
                                .background(
                                    if (isSelected) Color(0xFF0D6EFD) else Color(0xFF222222),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isSelected) Color(0xFF0D6EFD) else Color(0xFF333333), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (isSelected) Color.White else Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }

                // Volume slider/parameter if "HAS_SPECIFIC_VOL" active
                if (activeFilterVal == SetFilter.HAS_SPECIFIC_VOL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("খণ্ড নং নির্বাচন করুন / Volume: ", color = Color.Gray, fontSize = 11.sp)
                        IconButton(
                            onClick = { if (specificVol > 1) viewModel.specificVolumeFilter.value = specificVol - 1 },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.LightGray)
                        }
                        Text(
                            "$specificVol",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { viewModel.specificVolumeFilter.value = specificVol + 1 },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle Favorite switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("শুধুমাত্র বুকমার্ককৃত বই (Favorites Only)", color = Color.LightGray, fontSize = 11.sp)
                    Switch(
                        checked = showFavsOnly,
                        onCheckedChange = { viewModel.showOnlyFavorites.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D6EFD))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Books list
        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কোনো বই খুঁজে পাওয়া যায়নি!\nচেক করুন আপনার টাইপিং বা ফিল্টার সেটআপ।",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(books) { book ->
                    var showRowMenu by remember { mutableStateOf(false) }

                    Box {
                        BookItemRow(
                            book = book,
                            onClick = { onBookClick(book) },
                            onToggleFavorite = { viewModel.toggleFavorite(book) },
                            onLongClick = { showRowMenu = true }
                        )

                        DropdownMenu(
                            expanded = showRowMenu,
                            onDismissRequest = { showRowMenu = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray) },
                                text = { Text("সংশোধন / Edit", color = Color.White) },
                                onClick = {
                                    showRowMenu = false
                                    onEditBook(book)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC3545)) },
                                text = { Text("মুছে ফেলুন / Delete", color = Color(0xFFDC3545)) },
                                onClick = {
                                    showRowMenu = false
                                    onDeleteBook(book)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItemRow(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val totalVols = book.totalVolumes
    val availableList = book.availableVolumes.split(",")
        .filter { it.isNotEmpty() }
        .mapNotNull { it.trim().toIntOrNull() }

    val progress = if (totalVols > 0) availableList.size.toFloat() / totalVols else 0f
    val isComplete = availableList.size >= totalVols && totalVols > 0

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_${book.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.bookName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    if (book.author.isNotEmpty()) {
                        Text(
                            text = "লেখক: ${book.author}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isComplete) Color(0xFF198754).copy(alpha = 0.2f) else Color(0xFFFFC107).copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isComplete) "COMPLETE" else "INCOMPLETE",
                            color = if (isComplete) Color(0xFF198754) else Color(0xFFFFC107),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Fav",
                            tint = if (book.isFavorite) Color(0xFFDC3545) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Show active volumes bubbles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "প্রাপ্ত খণ্ডসমূহ: ",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))

                if (availableList.isEmpty()) {
                    Text("কোনো ভলিউম নেই ✖", fontSize = 10.sp, color = Color(0xFFDC3545), fontWeight = FontWeight.Bold)
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableList.sorted().forEach { vol ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF0D6EFD).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "V$vol",
                                    color = Color(0xFF0D6EFD),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF0D6EFD),
                    trackColor = Color(0xFF222222)
                )

                Text(
                    text = "${availableList.size} / $totalVols Volumes",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
            }
        }
    }
}

// ---------------------- COVERS INVENTORY SECTION ----------------------

@Composable
fun CoversInventoryScreen(
    covers: List<CoverEntity>,
    onAddCoverClick: () -> Unit,
    onEditCover: (CoverEntity) -> Unit,
    onDeleteCover: (CoverEntity) -> Unit,
    viewModel: StockViewModel
) {
    val searchQuery by viewModel.coverSearchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "কভার ইনভেনটরি (Covers Stock)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "বাঁধাই প্রক্রিয়ার জন্য রক্ষিত অতিরিক্ত আর্ট কভারসমূহ",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            IconButton(
                onClick = onAddCoverClick,
                modifier = Modifier
                    .background(Color(0xFF198754), CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add cover", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search covers
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.coverSearchQuery.value = it },
            placeholder = { Text("কভারবইয়ের নাম, খণ্ড বা নোট দিয়ে অনুসন্ধান...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.coverSearchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1E1E),
                unfocusedContainerColor = Color(0xFF1E1E1E),
                disabledContainerColor = Color(0xFF1E1E1E),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.Gray,
                unfocusedPlaceholderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cover_search_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Covers list
        if (covers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কোনো অতিরিক্ত বুক কভার পাওয়া যায়নি!",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = covers) { cover ->
                    CoverItemRow(
                        cover = cover,
                        onEdit = { onEditCover(cover) },
                        onDelete = { onDeleteCover(cover) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun CoverItemRow(
    cover: CoverEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showActions: Boolean = true,
    viewModel: StockViewModel
) {
    val maps = viewModel.parseCoverVolumes(cover.coverVolumes)
    val totalPcs = maps.values.sum()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cover.bookName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "মোট কভার স্টক: ${totalPcs}টি কভার পিস",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107)
                    )
                }

                if (showActions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC3545), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Breakdowns by volume
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                maps.entries.sortedBy { it.key }.forEach { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Vol ${entry.key}",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${entry.value} pcs",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (cover.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "নোট: ${cover.notes}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ---------------------- ADD / EDIT BOOK DIALOG ----------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditBookDialog(
    editingBook: BookEntity?,
    onDismiss: () -> Unit,
    onSave: (BookEntity) -> Unit,
    onOpenScanner: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(editingBook?.bookName ?: "") }
    var author by remember { mutableStateOf(editingBook?.author ?: "") }
    var publisher by remember { mutableStateOf(editingBook?.publisher ?: "") }
    var totalVolsString by remember { mutableStateOf(editingBook?.totalVolumes?.toString() ?: "10") }
    val selectedVolsList = remember { mutableStateListOf<Int>() }
    var notes by remember { mutableStateOf(editingBook?.notes ?: "") }

    // Init selected volumes
    LaunchedEffect(editingBook) {
        if (editingBook != null) {
            val available = editingBook.availableVolumes.split(",")
                .filter { it.isNotEmpty() }
                .mapNotNull { it.trim().toIntOrNull() }
            selectedVolsList.clear()
            selectedVolsList.addAll(available)
        }
    }

    val totalVols = totalVolsString.toIntOrNull() ?: 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = if (editingBook == null) "নতুন অবশিষ্ট বই যোগ করুন" else "বইয়ের বিবরণ সংশোধন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    TextButton(
                        onClick = {
                            if (name.isEmpty()) {
                                Toast.makeText(context, "বইয়ের নাম দিতে হবে!", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val entity = BookEntity(
                                id = editingBook?.id ?: 0,
                                bookName = name.trim(),
                                author = author.trim(),
                                publisher = publisher.trim(),
                                totalVolumes = totalVols,
                                availableVolumes = selectedVolsList.sorted().joinToString(","),
                                notes = notes.trim(),
                                isFavorite = editingBook?.isFavorite ?: false,
                                createdDate = editingBook?.createdDate ?: System.currentTimeMillis()
                            )
                            onSave(entity)
                        },
                        modifier = Modifier.testTag("save_book_btn")
                    ) {
                        Text("সংরক্ষণ", color = Color(0xFF0D6EFD), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scan trigger
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenScanner { text ->
                                name = text
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFFFC107))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "📷 কভার থেকে নাম স্ক্যান করুন (OCR Scan)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input fields
                Text("বইয়ের নাম / Book Title *", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("যেমন: الموسوعة الفقهية বা সহীহ আল ফিকাহ") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_name_field")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("লেখক / Author", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = author,
                            onValueChange = { author = it },
                            placeholder = { Text("ইমাম বোখারী (র:)") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1E1E),
                                unfocusedContainerColor = Color(0xFF1E1E1E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("প্রকাশনী / Publisher", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = publisher,
                            onValueChange = { publisher = it },
                            placeholder = { Text("কুয়েত ওয়াকফ") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1E1E),
                                unfocusedContainerColor = Color(0xFF1E1E1E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Total Volumes input
                Text("সর্বমোট খণ্ড সংখ্যা / Total Volumes (যেমন 45)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = totalVolsString,
                    onValueChange = { totalVolsString = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("total_volumes_field")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // SMART VOLUME SELECTION GRID
                Text(
                    text = "প্রাপ্ত ভলিউমসমূহ সিলেক্ট করুন / Smart Volume Select *",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "খণ্ডের উপর ক্লিক করে প্রাপ্তি নিশ্চিত করুন:",
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (totalVols <= 0) {
                    Text("ভলিউম সংখ্যা ০ এর বেশি দিন!", color = Color(0xFFDC3545), fontSize = 12.sp)
                } else {
                    // Responsive Checklist
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (vol in 1..totalVols) {
                            val isChecked = selectedVolsList.contains(vol)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) Color(0xFF0D6EFD) else Color(0xFF1E1E1E),
                                border = BorderStroke(1.dp, if (isChecked) Color(0xFF0D6EFD) else Color(0xFF2E2E2E)),
                                modifier = Modifier
                                    .clickable {
                                        if (isChecked) selectedVolsList.remove(vol)
                                        else selectedVolsList.add(vol)
                                    }
                                    .size(width = 54.dp, height = 38.dp)
                                    .testTag("checkbox_vol_$vol")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$vol",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isChecked) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("মন্তব্য ও নোট / Notes (ঐচ্ছিক)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("অতিরিক্ত বাঁধাই নোট বা আলমারি রেফারেন্স...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }
    }
}

// ---------------------- ADD / EDIT COVER DIALOG ----------------------

@Composable
fun AddEditCoverDialog(
    editingCover: CoverEntity?,
    onDismiss: () -> Unit,
    onSave: (CoverEntity) -> Unit,
    onOpenScanner: ((String) -> Unit) -> Unit,
    viewModel: StockViewModel
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(editingCover?.bookName ?: "") }
    var totalVolsString by remember { mutableStateOf(editingCover?.totalVolumes?.toString() ?: "10") }
    var notes by remember { mutableStateOf(editingCover?.notes ?: "") }

    // Represent maps as local editing list
    val volumeStocks = remember { mutableStateListOf<Pair<Int, Int>>() }

    LaunchedEffect(editingCover) {
        if (editingCover != null) {
            val parsed = viewModel.parseCoverVolumes(editingCover.coverVolumes)
            volumeStocks.clear()
            volumeStocks.addAll(parsed.entries.map { Pair(it.key, it.value) })
        }
    }

    // Mappings creator helper states
    var inputVolNum by remember { mutableStateOf("1") }
    var inputQty by remember { mutableStateOf("10") }

    val totalVols = totalVolsString.toIntOrNull() ?: 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = if (editingCover == null) "নতুন কভার স্টক লিখুন" else "কভার স্টক সংশোধন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    TextButton(
                        onClick = {
                            if (name.isEmpty()) {
                                Toast.makeText(context, "বইয়ের নাম দিতে হবে!", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            if (volumeStocks.isEmpty()) {
                                Toast.makeText(context, "অন্তত একটি খণ্ড ও এর পিস সংখ্যা দিন!", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val coverString = viewModel.formatCoverVolumes(volumeStocks.toMap())
                            val entity = CoverEntity(
                                id = editingCover?.id ?: 0,
                                bookName = name.trim(),
                                totalVolumes = totalVols,
                                coverVolumes = coverString,
                                notes = notes.trim(),
                                createdDate = editingCover?.createdDate ?: System.currentTimeMillis()
                            )
                            onSave(entity)
                        },
                        modifier = Modifier.testTag("save_cover_btn")
                    ) {
                        Text("সংরক্ষণ", color = Color(0xFF198754), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scan trigger
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenScanner { text ->
                                name = text
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFFFC107))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "📷 কভার থেকে নাম স্ক্যান করুন (OCR Scan)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input fields
                Text("বইয়ের নাম / Book Title *", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("সহীহ আল-বুখারী বা আল-মওসূআহ") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cover_name_field")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Total Volumes input
                Text("সর্বমোট খণ্ড সংখ্যা / Total Volumes (যেমন 10)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = totalVolsString,
                    onValueChange = { totalVolsString = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // DYNAMIC VOLUMES ADDER
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                    border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "খণ্ডের অতিরিক্ত স্টক যুক্ত করুন / Add Volume Stock Mapping",
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Vol num
                            Column(modifier = Modifier.weight(1f)) {
                                Text("খণ্ড নং (Volume)", fontSize = 10.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextField(
                                    value = inputVolNum,
                                    onValueChange = { inputVolNum = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF222222),
                                        unfocusedContainerColor = Color(0xFF222222),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            // Qty per vol
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("স্টক পিস (Quantity)", fontSize = 10.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextField(
                                    value = inputQty,
                                    onValueChange = { inputQty = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF222222),
                                        unfocusedContainerColor = Color(0xFF222222),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            // Add button
                            IconButton(
                                onClick = {
                                    val v = inputVolNum.toIntOrNull()
                                    val q = inputQty.toIntOrNull()
                                    if (v != null && q != null && v in 1..totalVols) {
                                        // Merge if already exists or add
                                        val idx = volumeStocks.indexOfFirst { it.first == v }
                                        if (idx != -1) {
                                            volumeStocks[idx] = Pair(v, q)
                                        } else {
                                            volumeStocks.add(Pair(v, q))
                                        }
                                        inputVolNum = (v + 1).toString()
                                    } else {
                                        Toast.makeText(context, "যথাযথ বৈধ খণ্ড ও সংখ্যা দিন!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.Bottom)
                                    .background(Color(0xFF198754), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Added Volumes Table
                Text("যুক্তকৃত খণ্ড ও স্টক সমূহের তালিকা:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(6.dp))

                if (volumeStocks.isEmpty()) {
                    Text(
                        "কোনো স্টক তালিকাভুক্ত করা হয়নি! উপরের বক্সে খণ্ড যুক্ত করুন।",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        volumeStocks.sortedBy { it.first }.forEach { mapping ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFC107).copy(alpha = 0.15f), CircleShape)
                                            .size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${mapping.first}", color = Color(0xFFFFC107), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("খণ্ড নং ${mapping.first} (Volume ${mapping.first})", color = Color.White, fontSize = 12.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${mapping.second} পিস (Pieces)", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(
                                        onClick = { volumeStocks.remove(mapping) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Del", tint = Color(0xFFDC3545), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("মন্তব্য ও নোট / Notes", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("আলমারি বা ক্যান্ডি তাক রেফারেন্স...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------- BOOK DETAIL DIALOG WITH MISSING CALCULATOR ----------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val totalVols = book.totalVolumes
    val availableSet = book.availableVolumes.split(",")
        .filter { it.isNotEmpty() }
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()

    val missingList = (1..totalVols).filter { !availableSet.contains(it) }
    val isComplete = missingList.isEmpty() && totalVols > 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .testTag("book_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Toolbar Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isComplete) Color(0xFF198754).copy(alpha = 0.15f) else Color(0xFFFFC107).copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isComplete) "সম্পূর্ণ / Complete" else "অসম্পূর্ণ / Incomplete",
                            color = if (isComplete) Color(0xFF198754) else Color(0xFFFFC107),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    Row {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (book.isFavorite) Color(0xFFDC3545) else Color.Gray
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Book Title Highlight
                Text(
                    text = book.bookName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color.White,
                    lineHeight = 26.sp
                )

                if (book.author.isNotEmpty()) {
                    Text(
                        text = "লেখক: ${book.author}",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (book.publisher.isNotEmpty()) {
                    Text(
                        text = "প্রকাশনী: ${book.publisher}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E2E2E))
                Spacer(modifier = Modifier.height(14.dp))

                // Available volumes lists count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("মোট প্রত্যাশিত খণ্ড", fontSize = 10.sp, color = Color.Gray)
                        Text("${totalVols}টি খণ্ড", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("প্রাপ্ত অবশিষ্ট খণ্ড", fontSize = 10.sp, color = Color.Gray)
                        Text("${availableSet.size}টি খণ্ড", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D6EFD))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AVAILABLE CHIPS
                Text("প্রাপ্ত খণ্ডসমূহ / Available Volumes:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                if (availableSet.isEmpty()) {
                    Text("কোনো প্রাপ্ত খণ্ড নেই!", color = Color(0xFFDC3545), fontSize = 12.sp)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSet.sorted().forEach { vol ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF0D6EFD).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF0D6EFD).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Vol $vol", color = Color(0xFF0D6EFD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // MISSING CHIPS Auto computed
                Text("অনুপস্থিত খণ্ডসমূহ / Missing Volumes *", color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                if (isComplete) {
                    Text("অভিনন্দন! কোনো খণ্ড অনুপস্থিত নেই। অল-কমপ্লীট সংস্করণ। 🎉", color = Color(0xFF198754), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        missingList.forEach { vol ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFDC3545).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, Color(0xFFDC3545).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Vol $vol", color = Color(0xFFDC3545), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (book.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("কোম্পানি নোট / Shelf Notes:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = book.notes,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF2E2E2E))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "সংযোজন কাল: ${formatLongDate(book.createdDate)}",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------- REPORTS SCREEN ----------------------

@Composable
fun ReportsScreen(statsState: InventoryUiState, viewModel: StockViewModel) {
    if (statsState is InventoryUiState.Success) {
        val books = statsState.books

        val mostIncomplete = viewModel.getMostIncompleteBooks(books)
        val missingVol1 = viewModel.getBooksMissingVolume1(books)
        val highestAvailable = viewModel.getBooksWithHighestAvailableVolumes(books)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "ইনভেনটরি এনালিটিক্স ও রিপোর্ট",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(
                "লাইব্রেরি ও কভার বাঁধাই স্টকের রিয়েল টাইম বিশ্লেষণ",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // REPORT 1: MOST INCOMPLETE BOOKS
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "১. সর্বাধিক অপূর্ণাঙ্গ গ্রন্থসমূহ (Most Incomplete)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        "যেসব বইয়ের সবচেয়ে বেশি খণ্ড এখনো অনুপস্থিত রয়েছে:",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    if (mostIncomplete.isEmpty()) {
                        Text("তালিকায় কোনো অসম্পূর্ণ বই নেই!", color = Color(0xFF198754), fontSize = 12.sp)
                    } else {
                        mostIncomplete.forEach { (book, missingCount) ->
                            val total = book.totalVolumes
                            val available = total - missingCount
                            val percentage = if (total > 0) available.toFloat() / total else 0f

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = book.bookName,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${missingCount}টি খণ্ড নেই",
                                        fontSize = 11.sp,
                                        color = Color(0xFFDC3545),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LinearProgressIndicator(
                                        progress = percentage,
                                        color = Color(0xFFDC3545),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("$available / $total", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // REPORT 2: BOOKS MISSING VOLUME 1
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "২. ১ম খণ্ড অনুপস্থিত গ্রন্থসমূহ (Missing Vol 1)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        "বাঁধাই শুরু করার প্রধান অন্তরায় ১ম খণ্ড অনুপস্থিত থাকা গ্রন্থসমূহ:",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    if (missingVol1.isEmpty()) {
                        Text("সকল বইয়ের ১ম খণ্ড সংযোজিত রয়েছে! গ্রেট জব। 🎉", color = Color(0xFF198754), fontSize = 12.sp)
                    } else {
                        missingVol1.forEach { book ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(book.bookName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFDC3545).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("No Vol 1", color = Color(0xFFDC3545), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // REPORT 3: HIGHEST AVAILABLE VOLUMES
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "৩. সর্বাধিক প্রাপ্ত খণ্ড সংবলিত বই (Highest Loaded)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        "যেসব বইয়ের সর্বাধিক পরিমাণ অবশিষ্ট খণ্ড ইতিমধ্যে লাইব্রেরিতে ফিরেছে:",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    if (highestAvailable.isEmpty()) {
                        Text("কোনো বই নিবন্ধিত নেই!", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        highestAvailable.forEach { book ->
                            val count = book.availableVolumes.split(",").filter { it.isNotEmpty() }.size
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF198754), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(book.bookName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("${count}টি খণ্ড প্রাপ্ত", color = Color(0xFF198754), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF0D6EFD))
        }
    }
}

// ---------------------- EXPORT & RESTORE BACKUP SCREEN ----------------------

@Composable
fun BackupScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Import Launcher for backup files
    val jsonImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(context, "json", it) { success ->
                if (success) Toast.makeText(context, "JSON ডেটা সফলভাবে পুনরুদ্ধার করা হয়েছে! ✅", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "দুঃখিত, ব্যাকআপ ইম্পোর্ট ব্যর্থ হয়েছে। ফাইল ও ফরম্যাট চেক করুন। ❌", Toast.LENGTH_LONG).show()
            }
        }
    }

    val csvImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(context, "csv", it) { success ->
                if (success) Toast.makeText(context, "CSV ডেটা সফলভাবে পুনরুদ্ধার করা হয়েছে! ✅", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "দুঃখিত, CSV ব্যাকআপ ফাইলটি অশুদ্ধ বা রিড করা সম্ভব হয়নি। ❌", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "স্টক ডাটা ব্যাকআপ ও রিসেট",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Text(
            "আপনার মূল্যবান ইনভেনটরি এক্সপোর্ট করুন এবং অন্যান্য ডিভাইসের সাথে শেয়ার করুন",
            fontSize = 11.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 1: EXPORT SYSTEM
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "১. ডাটা ব্যাকআপ এক্সপোর্ট করুন (Export Data)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "আপনার সমস্ত স্টক ডাটা জেসন (JSON) কিংবা সিএসভি (CSV) ফাইল হিসেবে হোয়াটসঅ্যাপ, টেলিগ্রাম বা যেকোনো স্থানে শেয়ার করুন সহজে।",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.shareBackup(context, "json") { shareUri ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Library Stock (JSON)"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("export_json_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ফাইল শেয়ার করুন (JSON Backup)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.shareBackup(context, "csv") { shareUri ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Library Stock (CSV)"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("export_csv_btn")
                ) {
                    Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ফাইল শেয়ার করুন (CSV Spreadsheets)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // SECTION 2: IMPORT SYSTEM
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "২. ডাটা পুনরুদ্ধার করুন (Import Restorations)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "ইতিপূর্বে এক্সপোর্টকৃত যেকোনো জেসন বা সিএসভি রূপরেখা সিলেক্ট করে ইনভেনটরি পুনরুদ্ধার বা সংযোজন করুন নিমেষেই।",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        // Open system document picker for json
                        jsonImportLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("import_json_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("পুনরুদ্ধার করুন (JSON Import)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        // Open system document picker for csv
                        csvImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/octet-stream"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("import_csv_btn")
                ) {
                    Icon(Icons.Default.GridOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("লোকাল এক্সেল লোড (CSV Import)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
