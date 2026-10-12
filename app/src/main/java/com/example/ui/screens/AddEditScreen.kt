package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WageEntry
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.WarningText
import com.example.ui.viewmodel.WageViewModel
import com.example.util.DateUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    entryId: Long,
    viewModel: WageViewModel,
    onNavigateBack: () -> Unit,
    onShowUndoSnackbar: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    initialDate: String? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isEditMode by remember { mutableStateOf(false) }
    var existingEntry: WageEntry? by remember { mutableStateOf(null) }

    // Form states
    var dateStr by remember { mutableStateOf(initialDate ?: DateUtils.getTodayDateString()) }
    var worked by remember { mutableStateOf(true) }
    var wageAmountStr by remember { mutableStateOf("") }
    var wagePaid by remember { mutableStateOf(false) }
    var extraGiven by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val previousPlaceholder by viewModel.previousWagePlaceholder.collectAsState()

    // Load existing entry if in edit mode or when date is selected
    LaunchedEffect(entryId) {
        if (entryId != -1L) {
            isEditMode = true
            val entry = viewModel.getEntryById(entryId)
            if (entry != null) {
                existingEntry = entry
                dateStr = entry.date
                worked = entry.worked
                wageAmountStr = if (entry.wageAmount == 0) "" else entry.wageAmount.toString()
                wagePaid = entry.wagePaid
                extraGiven = entry.extraGiven
                note = entry.note ?: ""
            }
        }
    }

    // Dynamic check on date change to prevent duplicate entries
    LaunchedEffect(dateStr, entryId) {
        if (entryId == -1L) { // Only run dynamic lookup when creating a new entry
            val existing = viewModel.getEntryByDate(dateStr)
            if (existing != null) {
                isEditMode = true
                existingEntry = existing
                worked = existing.worked
                wageAmountStr = if (existing.wageAmount == 0) "" else existing.wageAmount.toString()
                wagePaid = existing.wagePaid
                extraGiven = existing.extraGiven
                note = existing.note ?: ""
            } else {
                // No entry for this date, reset back to blank form defaults
                isEditMode = false
                existingEntry = null
                worked = true
                wageAmountStr = ""
                wagePaid = false
                extraGiven = false
                note = ""
            }
        }
    }

    // Date picker launcher
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            dateStr = DateUtils.formatDate(cal.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Entry" else "Add Entry",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = GoldAccent
                        )
                    }
                },
                actions = {
                    if (isEditMode && existingEntry != null) {
                        IconButton(
                            onClick = {
                                viewModel.deleteEntry(existingEntry!!) {
                                    onShowUndoSnackbar("Entry deleted") {
                                        viewModel.undoDelete { /* re-inserted */ }
                                    }
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.testTag("delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete entry",
                                tint = Color(0xFFD97878)
                            )
                        }
                    }
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
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Date Selection Card
            Column {
                Text(
                    text = "Work Date",
                    fontSize = 14.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                        .testTag("add_edit_date_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatDisplayDateWithYear(dateStr),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Pick Date",
                            tint = GoldAccent
                        )
                    }
                }
            }

            // Worked Toggle Card
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Worked",
                            fontSize = 15.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Did he work on this date?",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = worked,
                        onCheckedChange = { worked = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF211705),
                            checkedTrackColor = GoldAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.testTag("add_edit_worked_switch")
                    )
                }
            }

            // Wage Amount input & Wage Paid toggle
            AnimatedVisibility(visible = worked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Wage input
                    Column {
                        Text(
                            text = "Wage Earned (₹)",
                            fontSize = 14.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wageAmountStr,
                            onValueChange = { input ->
                                wageAmountStr = input.filter { it.isDigit() }
                            },
                            placeholder = {
                                Text(text = "₹$previousPlaceholder", color = TextMuted)
                            },
                            leadingIcon = {
                                Text(
                                    text = "₹",
                                    fontSize = 18.sp,
                                    color = GoldAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_edit_wage_input")
                        )
                    }

                    // Wage Paid Toggle
                    Surface(
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Wage Paid",
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Wages are paid the next day",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = wagePaid,
                                onCheckedChange = { wagePaid = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF211705),
                                    checkedTrackColor = GoldAccent,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = Color(0xFF333333)
                                ),
                                modifier = Modifier.testTag("add_edit_paid_switch")
                            )
                        }
                    }
                }
            }

            // Extra 100 given toggle
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Extra ₹100 given",
                            fontSize = 15.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Paid same-day (not delayed)",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = extraGiven,
                        onCheckedChange = { extraGiven = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF211705),
                            checkedTrackColor = GoldAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.testTag("add_edit_extra_switch")
                    )
                }
            }

            // Note Field
            Column {
                Text(
                    text = "Note (Optional)",
                    fontSize = 14.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = {
                        Text(text = "e.g. half day, rained, no work", color = TextMuted, fontSize = 14.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_edit_note_input")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    val finalWage = if (worked) {
                        if (wageAmountStr.isEmpty()) previousPlaceholder else wageAmountStr.toInt()
                    } else {
                        0
                    }
                    val entry = WageEntry(
                        id = if (isEditMode && existingEntry != null) existingEntry!!.id else 0,
                        date = dateStr,
                        worked = worked,
                        wageAmount = finalWage,
                        wagePaid = if (worked) wagePaid else false,
                        extraGiven = extraGiven,
                        note = if (note.isBlank()) null else note
                    )
                    viewModel.saveEntry(entry) {
                        onNavigateBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = Color(0xFF211705)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Entry",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
