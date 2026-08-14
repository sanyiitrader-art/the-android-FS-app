package com.fsstructure.creator.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state holder for the integrated code editor.
 * Manages session-only navigation history, workspace state, and file editing state.
 * DOES NOT persist history or recent files across app launches.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = EditorFileManager(application)

    // Workspace State
    private val _workspaceUri = MutableStateFlow<Uri?>(null)
    val workspaceUri: StateFlow<Uri?> = _workspaceUri.asStateFlow()

    // Current File State
    private val _currentFile = MutableStateFlow<EditorFileManager.EditorItem?>(null)
    val currentFile: StateFlow<EditorFileManager.EditorItem?> = _currentFile.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    // Unsaved files map (URI -> Text) for Save All functionality
    private val unsavedChanges = mutableMapOf<Uri, String>()

    // Navigation History (Session only)
    private val _navBackHistory = MutableStateFlow<List<EditorFileManager.EditorItem>>(emptyList())
    val navBackHistory: StateFlow<List<EditorFileManager.EditorItem>> = _navBackHistory.asStateFlow()

    private val _navForwardHistory = MutableStateFlow<List<EditorFileManager.EditorItem>>(emptyList())
    val navForwardHistory: StateFlow<List<EditorFileManager.EditorItem>> = _navForwardHistory.asStateFlow()

    // Settings
    private val _autoSave = MutableStateFlow(false)
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isTextSearchMode = MutableStateFlow(false)
    val isTextSearchMode: StateFlow<Boolean> = _isTextSearchMode.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Any>>(emptyList())
    val searchResults: StateFlow<List<Any>> = _searchResults.asStateFlow()

    fun setWorkspace(uri: Uri?) {
        _workspaceUri.value = uri
        _currentFile.value = null
        _currentText.value = ""
        _isDirty.value = false
        unsavedChanges.clear()
        clearNavigationHistory()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch()
    }

    fun setTextSearchMode(enabled: Boolean) {
        _isTextSearchMode.value = enabled
        performSearch()
    }

    private fun performSearch() {
        val query = _searchQuery.value
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            if (_isTextSearchMode.value) {
                // Search in current text
                val text = _currentText.value
                val indices = text.split(query).dropLast(1).runningFold(0) { acc, str -> acc + str.length + query.length }
                val results = indices.map { index ->
                    val start = index
                    val end = (index + 30).coerceAtMost(text.length)
                    "..." + text.substring(start, end).replace("\n", " ") + "..."
                }
                _searchResults.value = results
            } else {
                // Search files in workspace
                val rootUri = _workspaceUri.value ?: return@launch
                val results = withContext(Dispatchers.IO) {
                    val foundFiles = mutableListOf<EditorFileManager.EditorItem>()
                    searchDirectory(rootUri, rootUri, query, foundFiles)
                    foundFiles.take(20) // Limit results for performance
                }
                _searchResults.value = results
            }
        }
    }

    private suspend fun searchDirectory(treeUri: Uri, currentDirUri: Uri, query: String, results: MutableList<EditorFileManager.EditorItem>) {
        val children = fileManager.listFiles(currentDirUri)
        for (child in children) {
            if (child.name.contains(query, ignoreCase = true)) {
                results.add(child)
            }
            if (child.isDir) {
                searchDirectory(treeUri, child.uri, query, results)
            }
        }
    }

    fun openFile(item: EditorFileManager.EditorItem) {
        if (_currentFile.value != null && _isDirty.value) {
            // Store unsaved changes before switching
            unsavedChanges[_currentFile.value!!.uri] = _currentText.value
        }

        // Update navigation history
        _currentFile.value?.let { current ->
            _navBackHistory.update { it + current }
        }
        _navForwardHistory.value = emptyList() // Clear forward history on new open

        _currentFile.value = item
        _isDirty.value = false

        viewModelScope.launch {
            val content = fileManager.readFile(item.uri) ?: ""
            // Restore unsaved changes if they exist
            _currentText.value = unsavedChanges[item.uri] ?: content
            _isDirty.value = unsavedChanges.containsKey(item.uri)
        }
    }

    fun navigateBack() {
        if (_navBackHistory.value.isEmpty()) return

        // Current goes to forward
        _currentFile.value?.let { current ->
            _navForwardHistory.update { it + current }
        }

        val previous = _navBackHistory.value.last()
        _navBackHistory.update { it.dropLast(1) }

        _currentFile.value = previous
        _isDirty.value = false

        viewModelScope.launch {
            val content = fileManager.readFile(previous.uri) ?: ""
            _currentText.value = unsavedChanges[previous.uri] ?: content
            _isDirty.value = unsavedChanges.containsKey(previous.uri)
        }
    }

    fun navigateForward() {
        if (_navForwardHistory.value.isEmpty()) return

        _currentFile.value?.let { current ->
            _navBackHistory.update { it + current }
        }

        val next = _navForwardHistory.value.last()
        _navForwardHistory.update { it.dropLast(1) }

        _currentFile.value = next
        _isDirty.value = false

        viewModelScope.launch {
            val content = fileManager.readFile(next.uri) ?: ""
            _currentText.value = unsavedChanges[next.uri] ?: content
            _isDirty.value = unsavedChanges.containsKey(next.uri)
        }
    }

    private fun clearNavigationHistory() {
        _navBackHistory.value = emptyList()
        _navForwardHistory.value = emptyList()
    }

    fun updateText(newText: String) {
        if (_currentText.value != newText) {
            _currentText.value = newText
            _isDirty.value = true
            _currentFile.value?.let { unsavedChanges[it.uri] = newText }

            if (_autoSave.value) {
                saveCurrentFile()
            }
        }
    }

    fun saveCurrentFile() {
        val current = _currentFile.value ?: return
        val text = _currentText.value

        viewModelScope.launch {
            val success = fileManager.writeFile(current.uri, text)
            if (success) {
                _isDirty.value = false
                unsavedChanges.remove(current.uri)
            }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            val entries = unsavedChanges.toMap() // Copy to avoid concurrent modification
            for ((uri, text) in entries) {
                val success = fileManager.writeFile(uri, text)
                if (success) {
                    unsavedChanges.remove(uri)
                }
            }
            // If current file was saved, update dirty flag
            _currentFile.value?.let {
                if (!unsavedChanges.containsKey(it.uri)) {
                    _isDirty.value = false
                }
            }
        }
    }

    fun toggleAutoSave() {
        _autoSave.value = !_autoSave.value
    }

    /**
     * Creates a new file or folder.
     * @param parentUri The directory to create the item in.
     * @param name The name of the new item.
     * @param isDir True for folder, false for file.
     * @param onResult Callback with the new URI if successful, null otherwise (e.g., duplicate).
     */
    fun createItem(parentUri: Uri, name: String, isDir: Boolean, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val exists = fileManager.itemExists(parentUri, name)
            if (exists) {
                onResult(null)
                return@launch
            }

            val treeUri = _workspaceUri.value ?: return@launch
            val newUri = fileManager.createItem(treeUri, parentUri, name, isDir)
            onResult(newUri)
        }
    }

    /**
     * Renames an existing file or folder.
     */
    fun renameItem(itemUri: Uri, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val treeUri = _workspaceUri.value ?: return@launch
            val success = fileManager.renameItem(treeUri, itemUri, newName)
            onResult(success)
        }
    }
}