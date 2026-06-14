package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val previewText: String = ""
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResearchDone: Boolean = false,
    val researchLinksJson: String = "", // JSON list of ResearchLink
    val provider: String = "",
    val latencyMs: Long = 0L
)

@Entity(tableName = "knowledge_entries")
data class KnowledgeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
