package com.localai.chatbot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearHistoryForChat(chatId: String)
}
