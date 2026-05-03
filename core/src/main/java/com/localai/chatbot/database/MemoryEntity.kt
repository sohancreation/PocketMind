package com.localai.chatbot.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory",
    indices = [Index(value = ["key"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey
    val id: String,
    val key: String,
    val value: String,
    val timestamp: Long
)
