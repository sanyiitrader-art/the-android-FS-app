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

    // Listen for pending creation signals from Start Screen / Menu
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

    val treeItems by produceState(initialValue = emptyList<FlatItem>(), key1 = workspaceUri, key2 = expandedUris, key3 = refreshKey) {
        value = emptyList()
        if (workspaceUri != null) {
            val items = mutableListOf<FlatItem>()
            suspend fun traverse(uri: Uri, level: Int) {
                val children = fileManager.listFiles(uri)
                for (child in children) {
                    val isExp = expandedUris.contains(child.uri)
                    items.add(FlatItem(child, level, isExp))
                    if (child.isDir && isExp) {
                        traverse(child.uri, level + 1)
                    }
                }
            }
            traverse(workspaceUri, 0)
            value = items
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
            items(treeItems, key = { it.item.uri.toString() }) { flatItem ->
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
                                renamingItem = flatItem.item
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
            }

            if (creatingParentUri == workspaceUri && workspaceUri != null) {
                item {
                    InlineEditorField(
                        isDir = isCreatingDir,
                        isError = isError,
                        shakeOffset = shakeOffset.value,
                        onSave = { name ->
                            workspaceUri?.let { wsUri ->
                                viewModel.createItem(wsUri, name, isCreatingDir) { newUri ->
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
            }
        }
    }
}

@Composable
fun InlineEditorField(
    isDir: Boolean,
    isError: Boolean,
    shakeOffset: Float,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf("") }
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
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (text.isNotBlank()) onSave(text)
            })
        )
        IconButton(onClick = { if (text.isNotBlank()) onSave(text) }) {
            Icon(Icons.Filled.Add, "Save", tint = MaterialTheme.colorScheme.primary)
        }
    }
}