package com.localai.chatbot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory WHERE key = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memory")
    suspend fun getAllMemory(): List<MemoryEntity>
    
    @Query("SELECT * FROM memory")
    fun getAllMemoryFlow(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)
}
