package com.localai.chatbot.memory

import com.localai.chatbot.database.MemoryDao
import com.localai.chatbot.database.MemoryEntity

class MemoryManager(private val memoryDao: MemoryDao) {

    suspend fun saveMemory(key: String, value: String) {
        val existing = memoryDao.getMemoryByKey(key)
        val memory = MemoryEntity(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            key = key,
            value = value,
            timestamp = System.currentTimeMillis()
        )
        memoryDao.insertMemory(memory)
    }

    suspend fun getMemory(key: String): String? {
        return memoryDao.getMemoryByKey(key)?.value
    }

    suspend fun getAllMemory(): Map<String, String> {
        return memoryDao.getAllMemory().associate { it.key to it.value }
    }
}
