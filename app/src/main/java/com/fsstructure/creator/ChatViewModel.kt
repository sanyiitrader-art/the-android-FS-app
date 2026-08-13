package com.fsstructure.creator

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fsstructure.creator.data.AIResponse
import com.fsstructure.creator.data.AppDatabase
import com.fsstructure.creator.data.Conversation
import com.fsstructure.creator.data.Message
import com.fsstructure.creator.fs.FileSystemExecutor
import com.fsstructure.creator.network.AIApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "fs_settings")

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val aiApi = AIApi()
    private val fsExecutor = FileSystemExecutor(application)

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val FOLDER_URI = stringPreferencesKey("folder_uri")
    }

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _folderUri = MutableStateFlow<Uri?>(null)
    val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val conversations = database.conversationDao().getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = _activeConversationId.flatMapLatest { id ->
        if (id != null) database.messageDao().getMessagesForConversation(id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                _apiKey.value = prefs[API_KEY]
                prefs[FOLDER_URI]?.let { _folderUri.value = Uri.parse(it) }
            }
        }
    }

    fun toggleSidebar() {
        _isSidebarOpen.value = !_isSidebarOpen.value
    }

    fun closeSidebar() {
        _isSidebarOpen.value = false
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[API_KEY] = key
            }
            _apiKey.value = key
        }
    }

    fun saveFolderUri(uri: Uri) {
        viewModelScope.launch {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
            
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[FOLDER_URI] = uri.toString()
            }
            _folderUri.value = uri
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val newConvId = database.conversationDao().insertConversation(
                Conversation(title = "New Chat")
            )
            _activeConversationId.value = newConvId
            closeSidebar()
        }
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
        closeSidebar()
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            database.messageDao().deleteMessagesForConversation(id)
            database.conversationDao().deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun searchConversations(query: String): kotlinx.coroutines.flow.Flow<List<Conversation>> {
        return database.conversationDao().searchConversations(query)
    }

    fun sendMessage(text: String, attachmentContent: String? = null) {
        val currentConvId = _activeConversationId.value
        val key = _apiKey.value
        val treeUri = _folderUri.value

        viewModelScope.launch {
            _isProcessing.value = true
            
            // 1. Ensure conversation exists
            val convId = if (currentConvId == null) {
                database.conversationDao().insertConversation(Conversation(title = text.take(30)))
            } else {
                currentConvId
            }
            _activeConversationId.value = convId

            // 2. Save User Message
            val fullText = if (attachmentContent.isNullOrEmpty()) text else "$text\n\n[Attached File Content]:\n$attachmentContent"
            val userMsg = Message(conversationId = convId, role = "user", content = fullText)
            database.messageDao().insertMessage(userMsg)

            // 3. Check for API Key (No longer fails silently)
            if (key.isNullOrBlank()) {
                val errorMsg = Message(conversationId = convId, role = "assistant", content = "Please set your API Key in the sidebar first.")
                database.messageDao().insertMessage(errorMsg)
                _isProcessing.value = false
                return@launch
            }

            // 4. Fetch History and Proceed
            val history = withContext(Dispatchers.IO) {
                database.messageDao().getMessagesForConversation(convId).first()
            }

            try {
                val aiResponse: AIResponse = aiApi.fetchResponse(key, history)
                
                var errorContext: String? = null
                if (aiResponse.operations.isNotEmpty()) {
                    if (treeUri == null) {
                        errorContext = "Filesystem Error: No authorized folder selected."
                    } else {
                        val errors = fsExecutor.executeOperations(treeUri, aiResponse.operations)
                        if (errors.isNotEmpty()) {
                            errorContext = errors.joinToString("; ") { it.message }
                        }
                    }
                }

                val finalMessage: String
                if (errorContext != null) {
                    val errorResolutionResponse = aiApi.fetchResponse(key, history, errorContext)
                    finalMessage = errorResolutionResponse.message
                } else {
                    finalMessage = aiResponse.message
                }

                val aiMsg = Message(conversationId = convId, role = "assistant", content = finalMessage)
                database.messageDao().insertMessage(aiMsg)

            } catch (e: Exception) {
                val errorMsg = Message(conversationId = convId, role = "assistant", content = "Error: ${e.message}")
                database.messageDao().insertMessage(errorMsg)
            } finally {
                _isProcessing.value = false
            }
        }
    }
}