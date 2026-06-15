package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.DetectionHistoryRepository
import com.example.data.image.ModelDownloader
import com.example.data.image.ObjectDetectorEngine
import com.example.data.settings.SettingsManager
import com.example.ui.screens.CameraTab
import com.example.ui.screens.HistoryTab
import com.example.ui.screens.SettingsTab
import com.example.ui.screens.UploadTab
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize offline database & assets pipeline
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = DetectionHistoryRepository(database.detectionHistoryDao())
        val engine = ObjectDetectorEngine(applicationContext)
        val downloader = ModelDownloader(applicationContext)
        val settings = SettingsManager(applicationContext)

        // 2. Initialize unified MVVM ViewModel
        val factory = MainViewModelFactory(repository, engine, downloader, settings)
        val viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            val selectedTheme by viewModel.activeTheme.collectAsState()
            val isDark = selectedTheme == "DARK"

            MyApplicationTheme(darkTheme = isDark) {
                var currentTab by remember { mutableStateOf("CAMERA") }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            // Camera Nav
                            NavigationBarItem(
                                selected = currentTab == "CAMERA",
                                onClick = { currentTab = "CAMERA" },
                                modifier = Modifier.testTag("nav_camera"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "Camera Tab Selector"
                                    )
                                },
                                label = { Text("Camera", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )

                            // Media Nav
                            NavigationBarItem(
                                selected = currentTab == "UPLOAD",
                                onClick = { currentTab = "UPLOAD" },
                                modifier = Modifier.testTag("nav_upload"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload Tab Selector"
                                    )
                                },
                                label = { Text("Media", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )

                            // History Nav
                            NavigationBarItem(
                                selected = currentTab == "HISTORY",
                                onClick = { currentTab = "HISTORY" },
                                modifier = Modifier.testTag("nav_history"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History Tab Selector"
                                    )
                                },
                                label = { Text("History", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )

                            // Settings Nav
                            NavigationBarItem(
                                selected = currentTab == "SETTINGS",
                                onClick = { currentTab = "SETTINGS" },
                                modifier = Modifier.testTag("nav_settings"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings Tab Selector"
                                    )
                                },
                                label = { Text("Settings", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Dynamically render selected Screen tab with sliding transitions
                        when (currentTab) {
                            "CAMERA" -> CameraTab(viewModel)
                            "UPLOAD" -> UploadTab(viewModel)
                            "HISTORY" -> HistoryTab(viewModel)
                            "SETTINGS" -> SettingsTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}
