package com.fsstructure.creator.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.fsstructure.creator.data.FsError
import com.fsstructure.creator.data.FsOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The hard security boundary for filesystem manipulation.
 * Executes standardized FsOperations strictly within the user-authorized folder.
 * Enforces the "empty files only" rule by never writing content to created files.
 */
class FileSystemExecutor(private val context: Context) {

    /**
     * Executes a list of operations sequentially.
     * Returns a list of errors encountered. If the list is empty, all operations succeeded.
     */
    suspend fun executeOperations(treeUri: Uri, operations: List<FsOperation>): List<FsError> {
        val errors = mutableListOf<FsError>()
        val resolver = context.contentResolver

        // Ensure operations run on a background thread to prevent UI freezing
        withContext(Dispatchers.IO) {
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )

            for (op in operations) {
                try {
                    val parts = op.path.trim('/').split('/').filter { it.isNotEmpty() }
                    if (parts.isEmpty()) {
                        errors.add(FsError.InvalidOperation)
                        continue
                    }

                    val isCreatingFile = op is FsOperation.CreateEmptyFile
                    val targetIndex = parts.lastIndex
                    var currentUri = rootDocUri

                    for (i in parts.indices) {
                        val part = parts[i]
                        val isLast = i == targetIndex
                        val isDir = if (isLast) !isCreatingFile else true

                        val existingUri = findChild(resolver, currentUri, part, isDir)
                        
                        if (existingUri != null) {
                            if (isLast) {
                                // Conflict: Target item already exists
                                errors.add(FsError.ItemAlreadyExists)
                                break // Stop processing this specific operation
                            } else {
                                // Traverse into the existing directory
                                currentUri = existingUri
                            }
                        } else {
                            // Item does not exist, create it
                            val mimeType = if (isDir) Document.MIME_TYPE_DIR else "application/octet-stream"
                            val newUri = DocumentsContract.createDocument(resolver, currentUri, mimeType, part)
                            
                            if (newUri == null) {
                                errors.add(FsError.UnknownFailure("Failed to create: $part"))
                                break
                            }
                            
                            if (isLast) {
                                // Successfully created the target file/directory.
                                // STRICT RULE: We do not write any contents to files here.
                            } else {
                                currentUri = newUri
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    errors.add(FsError.PermissionFailure(e.message ?: "Access denied"))
                } catch (e: Exception) {
                    errors.add(FsError.UnknownFailure(e.message ?: "Unknown filesystem error"))
                }
            }
        }
        return errors
    }

    /**
     * Helper function to query the ContentResolver for a child document
     * with a specific name and type (directory or file).
     */
    private fun findChild(
        resolver: android.content.ContentResolver,
        parentUri: Uri,
        name: String,
        isDir: Boolean
    ): Uri? {
        val parentDocId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocId)
        
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docName = cursor.getString(1)
                val mime = cursor.getString(2)
                
                if (docName == name) {
                    val docIsDir = mime == Document.MIME_TYPE_DIR
                    if (docIsDir == isDir) {
                        val docId = cursor.getString(0)
                        return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                    }
                }
            }
        }
        return null
    }
}