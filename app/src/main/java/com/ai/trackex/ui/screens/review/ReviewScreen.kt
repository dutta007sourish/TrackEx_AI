package com.ai.trackex.ui.screens.review

import android.Manifest
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ai.trackex.util.toDisplayDate
import com.ai.trackex.util.toDisplayTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    imageUri: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ReviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoryEmojiMap by viewModel.categoryEmojiMap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        }
    }

    val isManualMode = imageUri.isEmpty()

    LaunchedEffect(imageUri) {
        if (isManualMode) {
            viewModel.startManualEntry()
        } else {
            viewModel.parseImage(imageUri)
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onConfirm()
    }

    LaunchedEffect(uiState.voiceError) {
        uiState.voiceError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearVoiceError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isLoading && uiState.error == null) {
                val allValid = uiState.items.all { it.amount.toDoubleOrNull() != null }
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::addItem,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Add Item")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.cancel()
                                    onCancel()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = viewModel::confirmExpenses,
                                modifier = Modifier.weight(1f),
                                enabled = uiState.items.isNotEmpty() && allValid
                            ) {
                                Text("Confirm All (${uiState.items.size})")
                            }
                            Button(
                                onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Voice Edit")
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (isManualMode) "Add Expenses" else "Review Expenses") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancel()
                        onCancel()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing bill...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to analyze bill",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = {
                            viewModel.cancel()
                            onCancel()
                        }) {
                            Text("Go Back")
                        }
                    }
                }
            }

            else -> {
                Box(modifier = Modifier.padding(innerPadding)) {
                    if (isManualMode) {
                        ManualItemsList(
                            uiState = uiState,
                            categories = categories,
                            categoryEmojiMap = categoryEmojiMap,
                            onDateChange = viewModel::updateItemDate,
                            onAmountChange = viewModel::updateItemAmount,
                            onCategoryChange = viewModel::updateItemCategory,
                            onNoteChange = viewModel::updateItemNote,
                            onRemoveItem = viewModel::removeItem,
                            onAddCategory = viewModel::addCategory,
                        )
                    } else {
                        ReviewItemsList(
                            imageUri = imageUri,
                            uiState = uiState,
                            categories = categories,
                            categoryEmojiMap = categoryEmojiMap,
                            onDateChange = viewModel::updateItemDate,
                            onAmountChange = viewModel::updateItemAmount,
                            onCategoryChange = viewModel::updateItemCategory,
                            onNoteChange = viewModel::updateItemNote,
                            onRemoveItem = viewModel::removeItem,
                            onAddCategory = viewModel::addCategory,
                        )
                    }

                    // Listening overlay
                    AnimatedVisibility(
                        visible = uiState.isListening,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Listening...",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    // Processing voice command overlay
                    AnimatedVisibility(
                        visible = uiState.isProcessingVoice,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Applying edits...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewItemsList(
    imageUri: String,
    uiState: ReviewUiState,
    categories: List<String>,
    categoryEmojiMap: Map<String, String>,
    onDateChange: (Int, Long) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onNoteChange: (Int, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var datePickerIndex by remember { mutableIntStateOf(-1) }
    var timePickerIndex by remember { mutableIntStateOf(-1) }

    // Date-only picker
    if (datePickerIndex >= 0 && datePickerIndex < uiState.items.size) {
        val item = uiState.items[datePickerIndex]
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = item.date)
        DatePickerDialog(
            onDismissRequest = { datePickerIndex = -1 },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { newDateMillis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = item.date }
                        val combined = Calendar.getInstance().apply {
                            timeInMillis = newDateMillis
                            set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onDateChange(datePickerIndex, combined.timeInMillis)
                    }
                    datePickerIndex = -1
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerIndex = -1 }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time-only picker
    if (timePickerIndex >= 0 && timePickerIndex < uiState.items.size) {
        val item = uiState.items[timePickerIndex]
        val cal = remember { Calendar.getInstance().apply { timeInMillis = item.date } }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        DatePickerDialog(
            onDismissRequest = { timePickerIndex = -1 },
            confirmButton = {
                TextButton(onClick = {
                    val combined = Calendar.getInstance().apply {
                        timeInMillis = item.date
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onDateChange(timePickerIndex, combined.timeInMillis)
                    timePickerIndex = -1
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerIndex = -1 }) {
                    Text("Cancel")
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = timePickerState)
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape: image on left, items on right
        Row(modifier = modifier.fillMaxSize()) {
            ImageSection(
                imageUri = imageUri,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            VerticalDivider()
            ItemsSection(
                uiState = uiState,
                categories = categories,
                categoryEmojiMap = categoryEmojiMap,
                onDatePickerOpen = { datePickerIndex = it },
                onTimePickerOpen = { timePickerIndex = it },
                onAmountChange = onAmountChange,
                onCategoryChange = onCategoryChange,
                onNoteChange = onNoteChange,
                onRemoveItem = onRemoveItem,
                onAddCategory = onAddCategory,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        // Portrait: image on top, items on bottom
        Column(modifier = modifier.fillMaxSize()) {
            ImageSection(
                imageUri = imageUri,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            HorizontalDivider()
            ItemsSection(
                uiState = uiState,
                categories = categories,
                categoryEmojiMap = categoryEmojiMap,
                onDatePickerOpen = { datePickerIndex = it },
                onTimePickerOpen = { timePickerIndex = it },
                onAmountChange = onAmountChange,
                onCategoryChange = onCategoryChange,
                onNoteChange = onNoteChange,
                onRemoveItem = onRemoveItem,
                onAddCategory = onAddCategory,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualItemsList(
    uiState: ReviewUiState,
    categories: List<String>,
    categoryEmojiMap: Map<String, String>,
    onDateChange: (Int, Long) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onNoteChange: (Int, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var datePickerIndex by remember { mutableIntStateOf(-1) }
    var timePickerIndex by remember { mutableIntStateOf(-1) }

    // Date picker
    if (datePickerIndex >= 0 && datePickerIndex < uiState.items.size) {
        val item = uiState.items[datePickerIndex]
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = item.date)
        DatePickerDialog(
            onDismissRequest = { datePickerIndex = -1 },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { newDateMillis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = item.date }
                        val combined = Calendar.getInstance().apply {
                            timeInMillis = newDateMillis
                            set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onDateChange(datePickerIndex, combined.timeInMillis)
                    }
                    datePickerIndex = -1
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerIndex = -1 }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker
    if (timePickerIndex >= 0 && timePickerIndex < uiState.items.size) {
        val item = uiState.items[timePickerIndex]
        val cal = Calendar.getInstance().apply { timeInMillis = item.date }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { timePickerIndex = -1 },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = item.date
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    onDateChange(timePickerIndex, newCal.timeInMillis)
                    timePickerIndex = -1
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerIndex = -1 }) {
                    Text("Cancel")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    ItemsSection(
        uiState = uiState,
        categories = categories,
        categoryEmojiMap = categoryEmojiMap,
        onDatePickerOpen = { datePickerIndex = it },
        onTimePickerOpen = { timePickerIndex = it },
        onAmountChange = onAmountChange,
        onCategoryChange = onCategoryChange,
        onNoteChange = onNoteChange,
        onRemoveItem = onRemoveItem,
        onAddCategory = onAddCategory,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun ImageSection(
    imageUri: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AsyncImage(
            model = Uri.parse(imageUri),
            contentDescription = "Bill image",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun ItemsSection(
    uiState: ReviewUiState,
    categories: List<String>,
    categoryEmojiMap: Map<String, String>,
    onDatePickerOpen: (Int) -> Unit,
    onTimePickerOpen: (Int) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onNoteChange: (Int, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Extracted Items",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.items.size} item(s) found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val total = uiState.items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            Text(
                text = "₹%.2f".format(total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            uiState.items.forEachIndexed { index, item ->
                ExpenseItemCard(
                    index = index,
                    item = item,
                    categories = categories,
                    categoryEmojiMap = categoryEmojiMap,
                    showRemove = uiState.items.size > 1,
                    onDateClick = { onDatePickerOpen(index) },
                    onTimeClick = { onTimePickerOpen(index) },
                    onAmountChange = { onAmountChange(index, it) },
                    onCategoryChange = { onCategoryChange(index, it) },
                    onNoteChange = { onNoteChange(index, it) },
                    onRemove = { onRemoveItem(index) },
                    onAddCategory = onAddCategory
                )
                if (index < uiState.items.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseItemCard(
    index: Int,
    item: ExpenseItemState,
    categories: List<String>,
    categoryEmojiMap: Map<String, String>,
    showRemove: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
    onAddCategory: (String, String, String) -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    if (showNewCategoryDialog) {
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        var newEmoji by remember { mutableStateOf("📦") }
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("Add Category") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEmoji,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 || newValue.codePointCount(0, newValue.length) <= 1) {
                                newEmoji = newValue
                            }
                        },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Category name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description (helps AI categorize)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCategory(newName, newDesc, newEmoji.ifBlank { "📦" })
                        onCategoryChange(newName.trim())
                        showNewCategoryDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Item ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.date.toDisplayDate(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = onDateClick) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.date.toDisplayTime(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time") },
                    trailingIcon = {
                        IconButton(onClick = onTimeClick) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Pick time")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = item.amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = item.amount.isNotEmpty() && item.amount.toDoubleOrNull() == null,
                supportingText = if (item.amount.isNotEmpty() && item.amount.toDoubleOrNull() == null) {
                    { Text("Enter a valid number") }
                } else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${categoryEmojiMap[item.category] ?: "\uD83D\uDCE6"} ${item.category}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text("${categoryEmojiMap[category] ?: "\uD83D\uDCE6"} $category") },
                            onClick = {
                                onCategoryChange(category)
                                categoryExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    "Add new...",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            categoryExpanded = false
                            showNewCategoryDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = item.note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
