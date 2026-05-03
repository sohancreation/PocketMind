package com.localai.chatbot.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class, ChatEntity::class, MemoryEntity::class], version = 4, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
}
