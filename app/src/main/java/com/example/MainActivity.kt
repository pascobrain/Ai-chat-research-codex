package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.AISettingsScreen
import com.example.ui.screens.SidebarDrawer
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.Screen
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseSyncManager.initialize(this)
        enableEdgeToEdge()

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val isDark by viewModel.isDarkTheme.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()

            // Resolve dynamic ColorScheme based on selected theme & accent color settings
            val resolvedPrimary = Color(android.graphics.Color.parseColor(accentColor.hex))
            val colorScheme = if (isDark) {
                darkColorScheme(
                    primary = resolvedPrimary,
                    onPrimary = Color.White,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF2E2E2E),
                    onBackground = Color(0xFFECECEC),
                    onSurface = Color(0xFFECECEC),
                    onSurfaceVariant = Color(0xFFB0B0B0),
                    secondaryContainer = Color(0xFF2C2C2C),
                    onSecondaryContainer = Color.White,
                    error = Color(0xFFCF6679)
                )
            } else {
                lightColorScheme(
                    primary = resolvedPrimary,
                    onPrimary = Color.White,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFF9FAFB),
                    surfaceVariant = Color(0xFFF3F4F6),
                    onBackground = Color(0xFF111827),
                    onSurface = Color(0xFF111827),
                    onSurfaceVariant = Color(0xFF4B5563),
                    secondaryContainer = Color(0xFFE5E7EB),
                    onSecondaryContainer = Color(0xFF111827),
                    error = Color(0xFFD32F2F)
                )
            }

            // Bind native Toast for simple messages inside Viewmodel snackbar channel
            val context = this
            LaunchedEffect(key1 = true) {
                viewModel.snackbarMessage.collectLatest { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val isSidebarOpen by viewModel.isSidebarOpen.collectAsState()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                // Sync VM sidebar state to Compose Drawer State
                LaunchedEffect(isSidebarOpen) {
                    if (isSidebarOpen) {
                        drawerState.open()
                    } else {
                        drawerState.close()
                    }
                }

                // Sync Compose Drawer gesture close back to VM state
                LaunchedEffect(drawerState.isOpen) {
                    if (!drawerState.isOpen && isSidebarOpen) {
                        viewModel.closeSidebar()
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            SidebarDrawer(viewModel = viewModel)
                        }
                    },
                    gesturesEnabled = currentScreen is Screen.Chat
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // High-performance, cross-fade animated Screen Switching container
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_trans"
                        ) { screen ->
                            when (screen) {
                                is Screen.Chat -> {
                                    MainChatScreen(viewModel = viewModel)
                                }
                                is Screen.Settings -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                                is Screen.AISettings -> {
                                    AISettingsScreen(viewModel = viewModel)
                                }
                                else -> {
                                    MainChatScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
