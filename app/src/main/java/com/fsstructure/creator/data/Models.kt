package com.fsstructure.creator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Core data models for the application.
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
 * Moved `path` to the base sealed class so the executor can access it uniformly.
 */
sealed class FsOperation {
    abstract val path: String
    data class CreateDirectory(override val path: String) : FsOperation()
    data class CreateEmptyFile(override val path: String) : FsOperation()
}

/**
 * The structured response expected from the AI.
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