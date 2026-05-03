package com.localai.chatbot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.localai.chatbot.data.ChatRepository
import com.localai.chatbot.memory.MemoryExtractor
import com.localai.chatbot.memory.MemoryManager
import com.localai.chatbot.prompt.PromptBuilder

import com.localai.chatbot.ai.LlamaEngine

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val memoryManager: MemoryManager,
    private val memoryExtractor: MemoryExtractor,
    private val promptBuilder: PromptBuilder,
    private val llamaEngine: LlamaEngine,
    private val modelDownloader: com.localai.chatbot.data.ModelDownloader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                repository,
                memoryManager,
                memoryExtractor,
                promptBuilder,
                llamaEngine,
                modelDownloader
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
