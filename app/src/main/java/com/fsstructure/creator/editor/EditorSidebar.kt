package com.fsstructure.creator.editor

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

data class FlatItem(
    val item: EditorFileManager.EditorItem,
    val level: Int,
    val isExpanded: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorSidebar(
    workspaceUri: Uri?,
    onClose: () -> Unit,
    onFileOpen: (EditorFileManager.EditorItem) -> Unit,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val fileManager = remember { EditorFileManager(context) }
    val scope = rememberCoroutineScope()

    var expandedUris by remember { mutableStateOf(setOf<Uri>()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    var creatingParentUri by remember { mutableStateOf<Uri?>(null) }
    var isCreatingDir by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<EditorFileManager.EditorItem?>(null) }
    var isError by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    var showItemMenu by remember { mutableStateOf(false) }
    var longPressedItem by remember { mutableStateOf<EditorFileManager.EditorItem?>(null) }

    val pendingCreation by viewModel.pendingCreation.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCreation) {
        pendingCreation?.let { pending ->
            pending.parentUri?.let { pUri ->
                expandedUris = expandedUris + pUri
                creatingParentUri = pUri
                isCreatingDir = pending.isDir
                viewModel.clearPendingCreation()
            }
        }
    }

    // Build the tree. We DO NOT clear the list to empty during recomputation to prevent vanishing.
    val treeItems by produceState(initialValue = emptyList<FlatItem>(), key1 = workspaceUri, key2 = expandedUris, key3 = refreshKey) {
        if (workspaceUri != null) {
            val items = mutableListOf<FlatItem>()
            try {
                // Fetch Root Folder Name safely
                val rootName = fileManager.getFileName(workspaceUri) ?: "Workspace"
                val rootItem = EditorFileManager.EditorItem(rootName, workspaceUri, true)
                val isRootExp = expandedUris.contains(workspaceUri)
                items.add(FlatItem(rootItem, 0, isRootExp))

                if (isRootExp) {
                    suspend fun traverse(treeUri: Uri, uri: Uri, level: Int) {
                        val children = fileManager.listFiles(treeUri, uri)
                        for (child in children) {
                            val isExp = expandedUris.contains(child.uri)
                            items.add(FlatItem(child, level, isExp))
                            if (child.isDir && isExp) {
                                traverse(treeUri, child.uri, level + 1)
                            }
                        }
                    }
                    traverse(workspaceUri, workspaceUri, 1)
                }
                // Only commit the new list if everything succeeded
                value = items
            } catch (e: Exception) {
                // If an error happens, keep the existing value (do nothing)
            }
        }
    }

    fun triggerError() {
        scope.launch {
            isError = true
            shakeOffset.animateTo(0f, animationSpec = tween(0))
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(-10f, animationSpec = tween(50))
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            isError = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.9f)
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Explorer", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = {
                    val targetParent = treeItems.find { it.item.uri == selectedUri }?.let { selected ->
                        if (selected.item.isDir) selected.item.uri else workspaceUri
                    } ?: workspaceUri
                    
                    targetParent?.let { uri ->
                        expandedUris = expandedUris + uri
                        creatingParentUri = uri
                        isCreatingDir = true
                    }
                }) {
                    Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = {
                    val targetParent = treeItems.find { it.item.uri == selectedUri }?.let { selected ->
                        if (selected.item.isDir) selected.item.uri else workspaceUri
                    } ?: workspaceUri
                    
                    targetParent?.let { uri ->
                        expandedUris = expandedUris + uri
                        creatingParentUri = uri
                        isCreatingDir = false
                    }
                }) {
                    Icon(Icons.Filled.NoteAdd, "New File", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            items(treeItems, key = { it.item.uri.toString() + it.level }) { flatItem ->
                val isSelected = flatItem.item.uri == selectedUri
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                selectedUri = flatItem.item.uri
                                if (flatItem.item.isDir) {
                                    expandedUris = if (flatItem.isExpanded) expandedUris - flatItem.item.uri else expandedUris + flatItem.item.uri
                                } else {
                                    onFileOpen(flatItem.item)
                                    onClose()
                                }
                            },
                            onLongClick = {
                                selectedUri = flatItem.item.uri
                                longPressedItem = flatItem.item
                                showItemMenu = true
                            }
                        )
                        .padding(start = (flatItem.level * 16).dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (flatItem.item.isDir) {
                        Icon(
                            if (flatItem.isExpanded) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight,
                            contentDescription = "Toggle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(Icons.Filled.CreateNewFolder, "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    } else {
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Filled.Description, "File", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(flatItem.item.name, color = MaterialTheme.colorScheme.onSurface)
                }

                if (creatingParentUri == flatItem.item.uri && flatItem.item.isDir && flatItem.isExpanded) {
                    InlineEditorField(
                        isDir = isCreatingDir,
                        isError = isError,
                        shakeOffset = shakeOffset.value,
                        onSave = { name ->
                            creatingParentUri?.let { pUri ->
                                viewModel.createItem(pUri, name, isCreatingDir) { newUri ->
                                    if (newUri != null) {
                                        creatingParentUri = null
                                        refreshKey++
                                    } else {
                                        triggerError()
                                    }
                                }
                            }
                        },
                        onCancel = { creatingParentUri = null }
                    )
                }

                if (renamingItem?.uri == flatItem.item.uri) {
                    InlineEditorField(
                        isDir = flatItem.item.isDir,
                        isError = isError,
                        shakeOffset = shakeOffset.value,
                        initialText = flatItem.item.name,
                        onSave = { newName ->
                            renamingItem?.let { item ->
                                viewModel.renameItem(item.uri, newName) { success ->
                                    if (success) {
                                        renamingItem = null
                                        refreshKey++
                                    } else {
                                        triggerError()
                                    }
                                }
                            }
                        },
                        onCancel = { renamingItem = null }
                    )
                }
            }
        }
    }

    DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
        longPressedItem?.let { item ->
            if (item.isDir) {
                DropdownMenuItem(
                    text = { Text("New File") },
                    onClick = {
                        showItemMenu = false
                        expandedUris = expandedUris + item.uri
                        creatingParentUri = item.uri
                        isCreatingDir = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("New File") },
                    onClick = { showItemMenu = false },
                    enabled = false
                )
            }
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showItemMenu = false
                    renamingItem = item
                }
            )
        }
    }
}

@Composable
fun InlineEditorField(
    isDir: Boolean,
    isError: Boolean,
    shakeOffset: Float,
    initialText: String = "",
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
            .offset(x = shakeOffset.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isDir) Icons.Filled.CreateNewFolder else Icons.Filled.Description,
            contentDescription = "New",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(0.8f),
            singleLine = true,
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isError) Color.Red else MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (text.isNotBlank()) onSave(text)
            })
        )
        IconButton(onClick = { if (text.isNotBlank()) onSave(text) }) {
            Icon(Icons.Filled.Add, "Save", tint = MaterialTheme.colorScheme.primary)
        }
    }
}