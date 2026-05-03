package com.localai.chatbot.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val title: String,
    val createdAt: Long
)
