package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.StockDatabase
import com.example.data.StockRepository
import com.example.ui.StockUiApp
import com.example.ui.StockViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Room Database and Repository flow
    val database = StockDatabase.getDatabase(applicationContext)
    val repository = StockRepository(database.stockDao())
    
    // Inline ViewModel factory constructor injection
    val factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StockViewModel(repository) as T
      }
    }
    
    val viewModel = ViewModelProvider(this, factory)[StockViewModel::class.java]

    setContent {
      MyApplicationTheme {
        StockUiApp(viewModel = viewModel)
      }
    }
  }
}
