package com.fsstructure.creator.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A lightweight Android SAF (Storage Access Framework) wrapper for the editor.
 * Handles listing directory trees, reading/writing text, and file/folder management.
 * Operates strictly within user-authorized URIs. No execution, no compilation.
 */
class EditorFileManager(private val context: Context) {

    /**
     * Represents a file or folder in the workspace tree.
     */
    data class EditorItem(
        val name: String,
        val uri: Uri,
        val isDir: Boolean,
        val parentId: String? = null
    )

    /**
     * Lists immediate children of a directory.
     */
    suspend fun listFiles(folderUri: Uri): List<EditorItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val children = mutableListOf<EditorItem>()
        
        try {
            val parentDocId = if (DocumentsContract.isTreeUri(folderUri)) {
                DocumentsContract.getTreeDocumentId(folderUri)
            } else {
                DocumentsContract.getDocumentId(folderUri)
            }
            
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentDocId)
            
            val projection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE
            )
            
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    val isDir = mime == Document.MIME_TYPE_DIR
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    
                    children.add(EditorItem(name, childUri, isDir, parentDocId))
                }
            }
        } catch (e: Exception) {
            // Return empty list on permission/query errors to avoid crashing the UI
            emptyList()
        }
        
        // Sort: Folders first, then alphabetically
        children.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    /**
     * Reads text content from a file.
     */
    suspend fun readFile(fileUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes text content to a file (Save / Save All).
     */
    suspend fun writeFile(fileUri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(fileUri, "rwt")?.use { outputStream ->
                outputStream.write(content.toByteArray())
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a new file or folder inside a parent directory.
     * Uses "application/octet-stream" for files to prevent the system from appending unwanted extensions.
     */
    suspend fun createItem(parentTreeUri: Uri, parentUri: Uri, name: String, isDir: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            val parentDocId = if (DocumentsContract.isTreeUri(parentUri)) {
                DocumentsContract.getTreeDocumentId(parentUri)
            } else {
                DocumentsContract.getDocumentId(parentUri)
            }
            
            val mimeType = if (isDir) Document.MIME_TYPE_DIR else "application/octet-stream"
            DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renames an existing file or folder.
     */
    suspend fun renameItem(treeUri: Uri, itemUri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val docId = if (DocumentsContract.isTreeUri(itemUri)) {
                DocumentsContract.getTreeDocumentId(itemUri)
            } else {
                DocumentsContract.getDocumentId(itemUri)
            }
            
            val newUri = DocumentsContract.renameDocument(context.contentResolver, itemUri, newName)
            newUri != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a file/folder with the given name already exists in the parent directory.
     */
    suspend fun itemExists(parentUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val children = listFiles(parentUri)
        children.any { it.name == name }
    }
}