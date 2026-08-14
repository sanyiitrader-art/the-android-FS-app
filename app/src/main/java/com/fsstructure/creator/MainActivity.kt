package com.fsstructure.creator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsstructure.creator.editor.EditorScreen
import com.fsstructure.creator.editor.EditorSidebar
import com.fsstructure.creator.editor.EditorViewModel
import com.fsstructure.creator.editor.EditorFileManager
import com.fsstructure.creator.ui.ChatScreen
import com.fsstructure.creator.ui.FSAppTheme
import com.fsstructure.creator.ui.Sidebar
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FSAppTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
fun MainAppContent() {
    val chatViewModel: ChatViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEditorOpen by remember { mutableStateOf(false) }
    
    val isChatSidebarOpen by chatViewModel.isSidebarOpen.collectAsStateWithLifecycle()
    var isEditorSidebarOpen by remember { mutableStateOf(false) }

    val apiKey by chatViewModel.apiKey.collectAsStateWithLifecycle()
    var showApiDialog by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Chat Pickers
    val chatFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) chatViewModel.saveFolderUri(uri)
    }

    val chatFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val fileName = uri.lastPathSegment ?: "attached_file"
                    if (content != null) {
                        chatViewModel.sendMessage("I have attached a file named '$fileName'. Please read its contents and let me know when you are ready to create the structure based on it.", content)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    // Editor Pickers
    val editorFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // For Open File, we just open the file directly. 
            // Full workspace parent resolution is complex in SAF, so we open the file as a standalone item.
            val item = EditorFileManager.EditorItem(
                name = uri.lastPathSegment ?: "file",
                uri = uri,
                isDir = false
            )
            editorViewModel.openFile(item)
        }
    }

    val editorFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            editorViewModel.setWorkspace(uri)
        }
    }

    val chatSidebarWidth = 320.dp
    val chatSidebarX by animateDpAsState(
        targetValue = if (isChatSidebarOpen) 0.dp else -chatSidebarWidth,
        label = "ChatSidebarAnimation"
    )

    val editorSidebarWidth = 360.dp
    val editorSidebarX by animateDpAsState(
        targetValue = if (isEditorSidebarOpen) 0.dp else -editorSidebarWidth,
        label = "EditorSidebarAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (isEditorOpen) {
                            if (dragOffset > 100) isEditorOpen = false // Swipe right to AI
                            if (dragOffset < -100) isEditorSidebarOpen = true // Swipe left to Editor Sidebar
                        } else {
                            if (dragOffset > 100 && !isChatSidebarOpen) chatViewModel.toggleSidebar() // Swipe right to Chat Sidebar
                            if (dragOffset < -100) isEditorOpen = true // Swipe left to Editor
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            }
    ) {
        if (isEditorOpen) {
            // --- EDITOR UI ---
            EditorScreen(
                viewModel = editorViewModel,
                onOpenFileClick = { editorFilePickerLauncher.launch(arrayOf("*/*")) },
                onOpenFolderClick = { editorFolderPickerLauncher.launch(null) },
                onNewWorkspace = { isFile ->
                    // Create new workspace in app external storage to avoid permission issues
                    val baseDir = context.getExternalFilesDir(null)
                    var newFolder = File(baseDir, "new folder")
                    var i = 2
                    while (newFolder.exists()) {
                        newFolder = File(baseDir, "new folder ($i)")
                        i++
                    }
                    newFolder.mkdirs()
                    editorViewModel.setWorkspace(Uri.fromFile(newFolder))
                    isEditorSidebarOpen = true
                },
                onBackToAI = { isEditorOpen = false },
                onOpenExplorer = { isEditorSidebarOpen = true }
            )

            // Editor Sidebar Overlay
            if (isEditorSidebarOpen) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { isEditorSidebarOpen = false })
                        }
                )
            }

            // Editor Sidebar
            Box(
                modifier = Modifier
                    .offset(x = editorSidebarX)
                    .width(editorSidebarWidth)
                    .fillMaxHeight()
            ) {
                val wsUri by editorViewModel.workspaceUri.collectAsStateWithLifecycle()
                EditorSidebar(
                    workspaceUri = wsUri,
                    onClose = { isEditorSidebarOpen = false },
                    onFileOpen = { editorViewModel.openFile(it); isEditorSidebarOpen = false },
                    viewModel = editorViewModel
                )
            }
        } else {
            // --- CHAT UI ---
            ChatScreen(
                viewModel = chatViewModel,
                onMenuClick = { chatViewModel.toggleSidebar() },
                onAttachClick = { chatFilePickerLauncher.launch(arrayOf("text/plain", "text/markdown")) },
                onOpenEditor = { isEditorOpen = true } // Trigger from icon
            )

            // Chat Sidebar Overlay
            if (isChatSidebarOpen) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { chatViewModel.closeSidebar() })
                        }
                )
            }

            // Chat Sidebar
            Box(
                modifier = Modifier
                    .offset(x = chatSidebarX)
                    .width(chatSidebarWidth)
                    .fillMaxHeight()
            ) {
                Sidebar(
                    viewModel = chatViewModel,
                    onClose = { chatViewModel.closeSidebar() },
                    onEditApiClick = { showApiDialog = true },
                    onPickFolderClick = { chatFolderPickerLauncher.launch(null) }
                )
            }
        }
    }

    if (showApiDialog) {
        EditApiDialog(
            currentKey = apiKey ?: "",
            onSave = { newKey -> chatViewModel.saveApiKey(newKey); showApiDialog = false },
            onDismiss = { showApiDialog = false }
        )
    }
}

@Composable
fun EditApiDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit API Key") },
        text = {
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (keyText.isNotBlank()) onSave(keyText) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}