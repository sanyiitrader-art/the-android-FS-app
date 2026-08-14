package com.fsstructure.creator.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EditorFileManager(private val context: Context) {

    data class EditorItem(
        val name: String,
        val uri: Uri,
        val isDir: Boolean,
        val parentId: String? = null
    )

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
            
            val cursor = resolver.query(childrenUri, projection, null, null, null)
            if (cursor != null) {
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(0)
                        val name = c.getString(1)
                        val mime = c.getString(2)
                        val isDir = mime == Document.MIME_TYPE_DIR
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        
                        children.add(EditorItem(name, childUri, isDir, parentDocId))
                    }
                }
                cursor.close()
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        
        children.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    suspend fun readFile(fileUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

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

    suspend fun itemExists(parentUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val children = listFiles(parentUri)
        children.any { it.name == name }
    }
}