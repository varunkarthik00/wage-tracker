package com.example.ui.screens

import android.content.Context
import androidx.core.content.edit
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.WageViewModel
import com.example.receiver.NotificationReceiver
import com.example.worker.DailyReminderWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WageViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Preferences setup
    val prefs = remember { context.getSharedPreferences("wage_tracker_prefs", Context.MODE_PRIVATE) }
    
    var dailyReminderEnabled by remember { 
        mutableStateOf(prefs.getBoolean("daily_reminder", true)) 
    }
    var appLockEnabled by remember { 
        mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) 
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogType by remember { mutableStateOf("SET") } // "SET" or "VERIFY_DISABLE"
    var pinValue by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf("") }

    // Backup import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importData(context, uri,
                onSuccess = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section: Backup & Restore
            Text(
                text = "Backup & Recovery",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Export card
            SettingsActionCard(
                title = "Export Data",
                description = "Backup all wage entries into a secure JSON file to share or save.",
                icon = Icons.Default.Share,
                onClick = {
                    viewModel.exportData(context,
                        onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                    )
                },
                modifier = Modifier.testTag("export_data_card")
            )

            // Import card
            SettingsActionCard(
                title = "Import Data",
                description = "Restore or merge wage entries from a previously exported JSON backup file.",
                icon = Icons.Default.Upload,
                onClick = {
                    importLauncher.launch("application/json")
                },
                modifier = Modifier.testTag("import_data_card")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section: Reminders & Notifications
            Text(
                text = "Preferences",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Reminder toggle card
            SettingsToggleCard(
                title = "Daily Reminder",
                description = "Get a reminder at 10:00 PM if you haven't logged today's wage or have pending payments.",
                icon = Icons.Default.Notifications,
                checked = dailyReminderEnabled,
                onCheckedChange = { isChecked ->
                    dailyReminderEnabled = isChecked
                    prefs.edit { putBoolean("daily_reminder", isChecked) }
                    if (isChecked) {
                        NotificationReceiver.scheduleDailyReminder(context)
                        DailyReminderWorker.scheduleDailyReminder(context)
                        Toast.makeText(context, "Daily reminder scheduled for 10:00 PM", Toast.LENGTH_SHORT).show()
                    } else {
                        NotificationReceiver.cancelDailyReminder(context)
                        DailyReminderWorker.cancelDailyReminder(context)
                        Toast.makeText(context, "Daily reminder disabled", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("daily_reminder_switch_card")
            )

            // App Lock Card
            SettingsToggleCard(
                title = "App Lock PIN",
                description = "Secure your local wage data with a 4-digit PIN on app startup.",
                icon = Icons.Default.Lock,
                checked = appLockEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        // Request to set PIN
                        pinDialogType = "SET"
                        pinValue = ""
                        pinErrorText = ""
                        showPinDialog = true
                    } else {
                        // Request current PIN to disable
                        pinDialogType = "VERIFY_DISABLE"
                        pinValue = ""
                        pinErrorText = ""
                        showPinDialog = true
                    }
                },
                modifier = Modifier.testTag("app_lock_switch_card")
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // App Lock PIN Setup Dialog
    if (showPinDialog) {
        Dialog(onDismissRequest = { showPinDialog = false }) {
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pinDialogType == "SET") "Set Security PIN" else "Verify Security PIN",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent
                    )

                    Text(
                        text = if (pinDialogType == "SET") "Enter a 4-digit security PIN to lock the app:" else "Enter your current 4-digit PIN to turn off App Lock:",
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                pinValue = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCardElevated,
                            unfocusedContainerColor = SurfaceCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_input")
                    )

                    if (pinErrorText.isNotEmpty()) {
                        Text(
                            text = pinErrorText,
                            fontSize = 12.sp,
                            color = Color(0xFFD97878),
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showPinDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pinValue.length != 4) {
                                    pinErrorText = "PIN must be exactly 4 digits."
                                    return@Button
                                }

                                if (pinDialogType == "SET") {
                                    // Save new PIN
                                    prefs.edit {
                                        putBoolean("app_lock_enabled", true)
                                        putString("app_lock_pin", pinValue)
                                    }
                                    appLockEnabled = true
                                    showPinDialog = false
                                    Toast.makeText(context, "App lock enabled successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Verify PIN to disable
                                    val savedPin = prefs.getString("app_lock_pin", "")
                                    if (pinValue == savedPin) {
                                        prefs.edit {
                                            putBoolean("app_lock_enabled", false)
                                            remove("app_lock_pin")
                                        }
                                        appLockEnabled = false
                                        showPinDialog = false
                                        Toast.makeText(context, "App lock disabled.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pinErrorText = "Incorrect PIN. Please try again."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = Color(0xFF211705)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Confirm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceCard,
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = SurfaceCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = GoldAccent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun SettingsToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceCard,
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = SurfaceCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = GoldAccent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF211705),
                    checkedTrackColor = GoldAccent,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}
