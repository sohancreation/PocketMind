package com.localai.chatbot.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val imageUrl: String? = null,
    val timestamp: Long
)
