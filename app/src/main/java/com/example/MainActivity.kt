package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.receiver.NotificationReceiver
import com.example.worker.DailyReminderWorker
import com.example.ui.screens.AddEditScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldAccentDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.WageViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            NotificationReceiver.scheduleDailyReminder(this)
            DailyReminderWorker.scheduleDailyReminder(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule daily reminder on startup (if enabled)
        val prefs = getSharedPreferences("wage_tracker_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("daily_reminder", true)) {
            NotificationReceiver.scheduleDailyReminder(this)
            DailyReminderWorker.scheduleDailyReminder(this)
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                var isUnlocked by rememberSaveable { 
                    mutableStateOf(!prefs.getBoolean("app_lock_enabled", false)) 
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val navController = rememberNavController()
                val viewModel: WageViewModel = viewModel()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val showBottomBar = isUnlocked && currentRoute in listOf("calendar", "analytics", "settings")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isUnlocked) {
                            AppLockScreen(
                                onUnlockSuccess = { isUnlocked = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            NavHost(
                                navController = navController,
                                startDestination = "calendar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = innerPadding.calculateTopPadding())
                            ) {
                                composable(
                                    "add_edit?entryId={entryId}&initialDate={initialDate}",
                                    arguments = listOf(
                                        navArgument("entryId") {
                                            type = NavType.LongType
                                            defaultValue = -1L
                                        },
                                        navArgument("initialDate") {
                                            type = NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    )
                                ) { backStackEntry ->
                                    val entryId = backStackEntry.arguments?.getLong("entryId") ?: -1L
                                    val initialDate = backStackEntry.arguments?.getString("initialDate")
                                    AddEditScreen(
                                        entryId = entryId,
                                        initialDate = initialDate,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() },
                                        onShowUndoSnackbar = { message, onUndo ->
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = message,
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onUndo()
                                                }
                                            }
                                        }
                                    )
                                }
                                composable("calendar") {
                                    com.example.ui.screens.CalendarScreen(
                                        viewModel = viewModel,
                                        onNavigateToAddEdit = { id, initialDate ->
                                            if (id != -1L) {
                                                navController.navigate("add_edit?entryId=$id")
                                            } else if (initialDate != null) {
                                                navController.navigate("add_edit?entryId=-1&initialDate=$initialDate")
                                            } else {
                                                navController.navigate("add_edit?entryId=-1")
                                            }
                                        },
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("analytics") {
                                    AnalyticsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }

                            if (showBottomBar) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFFF2C879), // Beautiful golden shade
                                                    Color(0xFFD1A145)  // Rich warm gold
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                ) {
                                    val items = listOf(
                                        Triple("calendar", "Calendar", Icons.Default.DateRange),
                                        Triple("analytics", "Analytics", Icons.AutoMirrored.Filled.TrendingUp),
                                        Triple("settings", "Settings", Icons.Default.Settings)
                                    )
                                    items.forEach { (route, label, icon) ->
                                        val selected = currentRoute == route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                if (currentRoute != route) {
                                                    navController.navigate(route) {
                                                        popUpTo("calendar") {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (selected) Color(0xFF140F06) else Color(0xFF5C471E)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    color = if (selected) Color(0xFF140F06) else Color(0xFF5C471E),
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = Color(0xFF140F06).copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier.testTag("nav_${route}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
