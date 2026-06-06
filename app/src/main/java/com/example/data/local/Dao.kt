package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Conversations ---
    @Query("SELECT * FROM conversations ORDER BY lastActiveAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, lastActiveAt = :lastActiveAt, previewText = :previewText WHERE id = :id")
    suspend fun updateConversationMeta(id: Long, title: String, lastActiveAt: Long, previewText: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    // --- Messages ---
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    // --- Knowledge Entries ---
    @Query("SELECT * FROM knowledge_entries ORDER BY timestamp DESC")
    fun getAllKnowledgeEntries(): Flow<List<KnowledgeEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeEntry(entry: KnowledgeEntryEntity): Long

    @Query("DELETE FROM knowledge_entries WHERE id = :id")
    suspend fun deleteKnowledgeEntryById(id: Long)

    @Query("SELECT * FROM knowledge_entries WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchKnowledgeEntries(query: String): Flow<List<KnowledgeEntryEntity>>

    // --- App Settings ---
    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<AppSettingsEntity>>

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSettingByKey(key: String): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSettingsEntity)
}
