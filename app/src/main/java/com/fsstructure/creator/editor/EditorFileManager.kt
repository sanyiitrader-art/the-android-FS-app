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

    // Helper to safely get Document ID from either Tree URI or Document URI
    private fun getDocId(uri: Uri): String {
        return if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }
    }

    // Helper to build the correct Document URI from a Tree URI and any child URI
    private fun buildDocUri(treeUri: Uri, uri: Uri): Uri {
        val docId = getDocId(uri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    suspend fun listFiles(treeUri: Uri, folderUri: Uri): List<EditorItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val children = mutableListOf<EditorItem>()
        
        try {
            val parentDocId = getDocId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            
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
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        
                        children.add(EditorItem(name, childUri, isDir, parentDocId))
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        
        children.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    suspend fun readFile(treeUri: Uri, fileUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val actualDocUri = buildDocUri(treeUri, fileUri)
            context.contentResolver.openInputStream(actualDocUri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun writeFile(treeUri: Uri, fileUri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val actualDocUri = buildDocUri(treeUri, fileUri)
            context.contentResolver.openOutputStream(actualDocUri, "rwt")?.use { outputStream ->
                outputStream.write(content.toByteArray())
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createItem(treeUri: Uri, parentUri: Uri, name: String, isDir: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            val actualParentDocUri = buildDocUri(treeUri, parentUri)
            val mimeType = if (isDir) Document.MIME_TYPE_DIR else "application/octet-stream"
            DocumentsContract.createDocument(context.contentResolver, actualParentDocUri, mimeType, name)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun renameItem(treeUri: Uri, itemUri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val actualDocUri = buildDocUri(treeUri, itemUri)
            val newUri = DocumentsContract.renameDocument(context.contentResolver, actualDocUri, newName)
            newUri != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun itemExists(treeUri: Uri, parentUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val children = listFiles(treeUri, parentUri)
        children.any { it.name == name }
    }
}