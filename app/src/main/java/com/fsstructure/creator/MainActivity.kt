package com.fsstructure.creator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import com.fsstructure.creator.ui.ChatScreen
import com.fsstructure.creator.ui.FSAppTheme
import com.fsstructure.creator.ui.Sidebar
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment

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
    val viewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isSidebarOpen by viewModel.isSidebarOpen.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

    var showApiDialog by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Folder Picker Launcher (SAF)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveFolderUri(uri)
        }
    }

    // File Attachment Launcher (.txt, .md)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val fileName = uri.lastPathSegment ?: "attached_file"
                    if (content != null) {
                        viewModel.sendMessage("I have attached a file named '$fileName'. Please read its contents and let me know when you are ready to create the structure based on it.", content)
                    }
                } catch (e: Exception) {
                    // Silently fail or log, per lightweight requirements
                }
            }
        }
    }

    // Animate sidebar sliding
    val sidebarWidth = 320.dp
    val sidebarX by animateDpAsState(
        targetValue = if (isSidebarOpen) 0.dp else -sidebarWidth,
        label = "SidebarAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Main Chat Screen (Bottom Layer)
        ChatScreen(
            viewModel = viewModel,
            onMenuClick = { viewModel.toggleSidebar() },
            onAttachClick = {
                filePickerLauncher.launch(arrayOf("text/plain", "text/markdown"))
            }
        )

        // 2. Overlay to catch clicks on the exposed chat area (Middle Layer)
        if (isSidebarOpen) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { viewModel.closeSidebar() }
                        )
                    }
            )
        }

        // 3. Sidebar (Top Layer - now clickable!)
        Box(
            modifier = Modifier
                .offset(x = sidebarX)
                .width(sidebarWidth)
                .fillMaxHeight()
        ) {
            Sidebar(
                viewModel = viewModel,
                onClose = { viewModel.closeSidebar() },
                onEditApiClick = { showApiDialog = true },
                onPickFolderClick = {
                    folderPickerLauncher.launch(null)
                }
            )
        }
    }

    // API Key Edit Dialog
    if (showApiDialog) {
        EditApiDialog(
            currentKey = apiKey ?: "",
            onSave = { newKey ->
                viewModel.saveApiKey(newKey)
                showApiDialog = false
            },
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
            Button(
                onClick = { if (keyText.isNotBlank()) onSave(keyText) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}