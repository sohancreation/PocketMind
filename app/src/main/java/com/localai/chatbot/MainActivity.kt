package com.localai.chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.localai.chatbot.data.ChatRepository
import com.localai.chatbot.database.ChatDatabase
import com.localai.chatbot.memory.MemoryExtractor
import com.localai.chatbot.memory.MemoryManager
import com.localai.chatbot.prompt.PromptBuilder
import com.localai.chatbot.ui.ChatScreen
import com.localai.chatbot.viewmodel.ChatViewModel
import com.localai.chatbot.viewmodel.ChatViewModelFactory
import com.localai.chatbot.ai.LlamaEngine
import com.localai.chatbot.ui.theme.LocalAIChatbotTheme

class MainActivity : ComponentActivity() {

    private val chatDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            ChatDatabase::class.java, "chat-database"
        )
        // Only for demonstration since we updated the schema without migration:
        .fallbackToDestructiveMigration()
        .build()
    }

    private val chatRepository by lazy {
        ChatRepository(chatDatabase.chatDao(), chatDatabase.messageDao())
    }

    private val memoryManager by lazy {
        MemoryManager(chatDatabase.memoryDao())
    }

    private val memoryExtractor by lazy {
        MemoryExtractor(memoryManager)
    }

    private val promptBuilder by lazy {
        PromptBuilder()
    }

    private val llamaEngine by lazy {
        LlamaEngine()
    }

    private val modelDownloader by lazy {
        com.localai.chatbot.data.ModelDownloader(applicationContext)
    }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            chatRepository,
            memoryManager,
            memoryExtractor,
            promptBuilder,
            llamaEngine,
            modelDownloader
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalAIChatbotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(viewModel = chatViewModel)
                }
            }
        }
    }
}
