package com.localai.chatbot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ChatPreviewTuple(
    val chatId: String,
    val title: String,
    val createdAt: Long,
    val lastMessageContent: String?
)

@Dao
interface ChatDao {
    @Query("""
        SELECT 
            c.chatId, 
            c.title, 
            c.createdAt, 
            (SELECT content FROM messages WHERE chatId = c.chatId ORDER BY timestamp DESC LIMIT 1) as lastMessageContent 
        FROM chats c 
        ORDER BY c.createdAt DESC
    """)
    fun getAllChatsWithPreview(): Flow<List<ChatPreviewTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)
}
