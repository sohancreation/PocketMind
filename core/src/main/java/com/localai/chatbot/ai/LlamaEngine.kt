package com.localai.chatbot.ai

import com.localai.chatbot.llmnative.LlamaNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LlamaEngine {
    private val llamaNative = LlamaNative()
    @Volatile
    private var modelLoaded = false

    suspend fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean = withContext(Dispatchers.Default) {
        println("PocketMind_Log: Loading model from $modelPath")
        val success = llamaNative.loadModel(modelPath, contextSize, threads)
        println("PocketMind_Log: Load success: $success")
        modelLoaded = success
        success
    }

    fun isModelLoaded(): Boolean = modelLoaded

    suspend fun generate(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.Default) {
        llamaNative.generate(prompt, maxTokens)
    }

    fun generateStreaming(prompt: String, maxTokens: Int = 512): Flow<String> = callbackFlow {
        println("PocketMind_Log: Starting streaming...")
        val job = launch(Dispatchers.Default) {
            try {
                llamaNative.generateStreaming(prompt, maxTokens, object : LlamaNative.TokenCallback {
                    override fun onTokenGenerated(token: String) {
                        trySend(token) // Thread-safe send
                    }
                })
            } catch (e: Exception) {
                println("PocketMind_Log: Native error: ${e.message}")
            } finally {
                println("PocketMind_Log: Streaming finished.")
                close()
            }
        }
        awaitClose { 
            println("PocketMind_Log: Flow closed.")
            llamaNative.cancelGeneration()
            job.cancel() 
        }
    }.buffer(Channel.UNLIMITED) // Ensure we don't block the native thread

    fun cancelGeneration() {
        llamaNative.cancelGeneration()
    }
}
