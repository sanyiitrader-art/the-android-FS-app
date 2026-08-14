package com.fsstructure.creator.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EditorFileManager(private val context: Context) {

    data class EditorItem(
        val name: String,
        val uri: Uri,
        val isDir: Boolean,
        val parentId: String? = null
    )

    private fun isLocalFile(uri: Uri): Boolean = uri.scheme == "file"

    suspend fun getFileName(uri: Uri): String? = withContext(Dispatchers.IO) {
        if (isLocalFile(uri)) {
            uri.path?.let { File(it).name }
        } else {
            try {
                val docId = if (DocumentsContract.isTreeUri(uri)) {
                    DocumentsContract.getTreeDocumentId(uri)
                } else {
                    DocumentsContract.getDocumentId(uri)
                }
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                context.contentResolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun listFiles(treeUri: Uri, folderUri: Uri): List<EditorItem> = withContext(Dispatchers.IO) {
        val children = mutableListOf<EditorItem>()
        try {
            if (isLocalFile(folderUri)) {
                val dir = File(folderUri.path!!)
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val childUri = Uri.fromFile(file)
                        children.add(EditorItem(file.name, childUri, file.isDirectory))
                    }
                }
            } else {
                val parentDocId = if (DocumentsContract.isTreeUri(folderUri)) {
                    DocumentsContract.getTreeDocumentId(folderUri)
                } else {
                    DocumentsContract.getDocumentId(folderUri)
                }
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                val projection = arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE
                )
                val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                cursor?.use { c ->
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
            if (isLocalFile(fileUri)) {
                File(fileUri.path!!).readText()
            } else {
                val docId = if (DocumentsContract.isTreeUri(fileUri)) {
                    DocumentsContract.getTreeDocumentId(fileUri)
                } else {
                    DocumentsContract.getDocumentId(fileUri)
                }
                val actualDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.openInputStream(actualDocUri)?.use { it.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun writeFile(treeUri: Uri, fileUri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isLocalFile(fileUri)) {
                File(fileUri.path!!).writeText(content)
                true
            } else {
                val docId = if (DocumentsContract.isTreeUri(fileUri)) {
                    DocumentsContract.getTreeDocumentId(fileUri)
                } else {
                    DocumentsContract.getDocumentId(fileUri)
                }
                val actualDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.openOutputStream(actualDocUri, "rwt")?.use { it.write(content.toByteArray()); true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createItem(treeUri: Uri, parentUri: Uri, name: String, isDir: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            if (isLocalFile(parentUri)) {
                val newFile = File(parentUri.path!!, name)
                if (isDir) newFile.mkdirs() else newFile.createNewFile()
                if (newFile.exists()) Uri.fromFile(newFile) else null
            } else {
                val docId = if (DocumentsContract.isTreeUri(parentUri)) {
                    DocumentsContract.getTreeDocumentId(parentUri)
                } else {
                    DocumentsContract.getDocumentId(parentUri)
                }
                val actualParentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val mimeType = if (isDir) Document.MIME_TYPE_DIR else "application/octet-stream"
                DocumentsContract.createDocument(context.contentResolver, actualParentDocUri, mimeType, name)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Updated to return the new Uri
    suspend fun renameItem(treeUri: Uri, itemUri: Uri, newName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            if (isLocalFile(itemUri)) {
                val file = File(itemUri.path!!)
                val newFile = File(file.parentFile, newName)
                if (file.renameTo(newFile)) Uri.fromFile(newFile) else null
            } else {
                val docId = if (DocumentsContract.isTreeUri(itemUri)) {
                    DocumentsContract.getTreeDocumentId(itemUri)
                } else {
                    DocumentsContract.getDocumentId(itemUri)
                }
                val actualDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                DocumentsContract.renameDocument(context.contentResolver, actualDocUri, newName)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun itemExists(treeUri: Uri, parentUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val children = listFiles(treeUri, parentUri)
        children.any { it.name == name }
    }
}