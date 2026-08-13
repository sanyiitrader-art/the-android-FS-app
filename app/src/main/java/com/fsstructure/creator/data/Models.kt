package com.fsstructure.creator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Core data models for the application.
 * Contains Room entities for local persistence and standardized representations
 * for filesystem operations and AI communication.
 */

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * The standardized operation representation understood by the application.
 * The AI converts natural language into these operations.
 * The filesystem engine ONLY understands this representation.
 */
sealed class FsOperation {
    data class CreateDirectory(val path: String) : FsOperation()
    data class CreateEmptyFile(val path: String) : FsOperation()
    // Note: No WriteContent operation exists here. This is the hard security boundary.
}

/**
 * The structured response expected from the AI.
 * Contains a natural language message for the user and a list of operations to execute.
 */
data class AIResponse(
    val message: String,
    val operations: List<FsOperation>
)

/**
 * Standardized error returned by the FileSystemExecutor to the AI layer.
 */
sealed class FsError(val message: String) {
    data object ItemAlreadyExists : FsError("The item already exists.")
    data object InvalidOperation : FsError("The operation is invalid.")
    data class PermissionFailure(val detail: String) : FsError("Permission failure: $detail")
    data class UnknownFailure(val detail: String) : FsError("Filesystem failure: $detail")
}