package com.fsstructure.creator.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onOpenFileClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onNewWorkspace: (Boolean) -> Unit,
    onBackToAI: () -> Unit,
    onOpenExplorer: () -> Unit
) {
    val workspaceUri by viewModel.workspaceUri.collectAsStateWithLifecycle()
    val currentText by viewModel.currentText.collectAsStateWithLifecycle()
    val currentFile by viewModel.currentFile.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isTextSearchMode by viewModel.isTextSearchMode.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    
    val autoSave by viewModel.autoSave.collectAsStateWithLifecycle()
    val canGoBack by viewModel.navBackHistory.collectAsStateWithLifecycle()
    val canGoForward by viewModel.navForwardHistory.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.navigateBack() }, enabled = canGoBack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canGoBack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    }
                    IconButton(onClick = { viewModel.navigateForward() }, enabled = canGoForward.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canGoForward.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(48.dp),
                        interactionSource = searchInteractionSource,
                        placeholder = { Text("Workspace", color = MaterialTheme.colorScheme.surfaceVariant, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.surfaceVariant) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // Centered Search Text Button / No Result Found
                if (isSearchFocused && searchQuery.isEmpty()) {
                    TextButton(
                        onClick = { viewModel.setTextSearchMode(!isTextSearchMode) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Text(if (isTextSearchMode) "Switch to File Search" else "Search Text", color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    }
                } else if (searchQuery.isNotEmpty()) {
                    SearchResultsList(
                        results = searchResults,
                        isTextSearchMode = isTextSearchMode,
                        onResultClick = { result ->
                            if (isTextSearchMode) {
                                viewModel.setSearchQuery("")
                            } else {
                                viewModel.openFile(result as EditorFileManager.EditorItem)
                                viewModel.setSearchQuery("")
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Left Activity Bar (File, Menu, Settings)
            Column(
                modifier = Modifier.fillMaxHeight().width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column {
                    IconButton(onClick = onOpenExplorer) {
                        Icon(Icons.Filled.Description, "Explorer", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Filled.Menu, "Menu", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("New File") }, onClick = { showMenu = false; onNewWorkspace(true) })
                            DropdownMenuItem(text = { Text("New Folder") }, onClick = { showMenu = false; onNewWorkspace(false) })
                            DropdownMenuItem(text = { Text("Open File") }, onClick = { showMenu = false; onOpenFileClick() })
                            DropdownMenuItem(text = { Text("Open Folder") }, onClick = { showMenu = false; onOpenFolderClick() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Save") }, onClick = { showMenu = false; viewModel.saveCurrentFile() })
                            DropdownMenuItem(text = { Text("Save All") }, onClick = { showMenu = false; viewModel.saveAll() })
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = autoSave, onCheckedChange = { viewModel.toggleAutoSave() })
                                        Spacer(Modifier.width(8.dp))
                                        Text("Auto Save")
                                    }
                                },
                                onClick = { viewModel.toggleAutoSave() }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.weight(1f))
                
                IconButton(onClick = { /* Settings undefined */ }) {
                    Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (workspaceUri == null) {
                    EditorStartScreen(
                        onNewFile = { onNewWorkspace(true) },
                        onOpenFile = onOpenFileClick,
                        onOpenFolder = onOpenFolderClick
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (currentFile != null) {
                            BasicTextField(
                                value = currentText,
                                onValueChange = { viewModel.updateText(it) },
                                modifier = Modifier.fillMaxSize(),
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Text("Select a file from the Explorer", color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsList(
    results: List<Any>,
    isTextSearchMode: Boolean,
    onResultClick: (Any) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        LazyColumn(modifier = Modifier.heightIn(max = 150.dp).padding(8.dp)) {
            if (results.isEmpty()) {
                item { 
                    Text(
                        text = "No Result Found", 
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    ) 
                }
            } else {
                items(results) { result ->
                    val displayText = if (isTextSearchMode) result as String else (result as EditorFileManager.EditorItem).name
                    Text(
                        text = displayText,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(result) }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditorStartScreen(
    onNewFile: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit
) {
    // Centered in the remaining space to avoid overlap with the left activity bar
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Start", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        
        StartButton(text = "New File...", icon = Icons.Filled.NoteAdd, onClick = onNewFile)
        StartButton(text = "Open File...", icon = Icons.Filled.Description, onClick = onOpenFile)
        StartButton(text = "Open Folder...", icon = Icons.Filled.Folder, onClick = onOpenFolder)

        Spacer(Modifier.height(24.dp))

        Text("Recent", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        
        val recentText = buildAnnotatedString {
            append("You have no recent folders, ")
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append("open a folder")
            }
            append(" to start")
        }
        
        Text(
            text = recentText,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { onOpenFolder() },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StartButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
    }
}