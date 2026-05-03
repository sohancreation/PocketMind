package com.localai.chatbot.viewmodel

import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localai.chatbot.data.ChatRepository
import com.localai.chatbot.memory.MemoryExtractor
import com.localai.chatbot.memory.MemoryManager
import com.localai.chatbot.models.Chat
import com.localai.chatbot.models.Message
import com.localai.chatbot.models.Role
import com.localai.chatbot.prompt.PromptBuilder
import com.localai.chatbot.prompt.UserMemory
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import com.localai.chatbot.ai.LlamaEngine

class ChatViewModel(
    private val repository: ChatRepository,
    private val memoryManager: MemoryManager,
    private val memoryExtractor: MemoryExtractor,
    private val promptBuilder: PromptBuilder,
    private val llamaEngine: LlamaEngine,
    private val modelDownloader: com.localai.chatbot.data.ModelDownloader
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()
    
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private var messageJob: kotlinx.coroutines.Job? = null
    private val repairedThinkingMessageIds = mutableSetOf<String>()

    private val _availableModels = MutableStateFlow(com.localai.chatbot.models.ModelList.models)
    val availableModels: StateFlow<List<com.localai.chatbot.models.ModelInfo>> = _availableModels.asStateFlow()

    private val _status = MutableStateFlow("PocketMind AI Initialization...")
    val status: StateFlow<String> = _status.asStateFlow()
    private var loadedModelFileName: String? = null

    init {
        loadChats()
        // If a model is bundled as an APK asset, install it once so it doesn't need downloading.
        installBundledModels()
        refreshModelStates()
        autoLoadModel()
    }

    private fun installBundledModels() {
        viewModelScope.launch {
            // Best-effort; no-op if asset is missing.
            val smolFile = com.localai.chatbot.models.ModelList.models
                .firstOrNull { it.id == "smollm2-360m-mini" }
                ?.fileName
            if (smolFile != null) {
                modelDownloader.installBundledModelIfMissing(smolFile)
            }

            val tinyllamaFile = com.localai.chatbot.models.ModelList.models
                .firstOrNull { it.id == "tinyllama-1.1b" }
                ?.fileName
            if (tinyllamaFile != null) {
                modelDownloader.installBundledModelIfMissing(tinyllamaFile)
            }
            refreshModelStates()
        }
    }

    private fun refreshModelStates() {
        _availableModels.value = _availableModels.value.map { model ->
            model.copy(isDownloaded = modelDownloader.isModelDownloaded(model.fileName))
        }
    }

    private fun autoLoadModel() {
        viewModelScope.launch {
            val preferredOrder = listOf(
                "smollm2-360m-mini", // bundled default
                "tinyllama-1.1b",
                "gemma-2b",
                "phi-2-2.7b"
            )
            val downloadedModel =
                preferredOrder.firstNotNullOfOrNull { id -> _availableModels.value.find { it.id == id && it.isDownloaded } }
                    ?: _availableModels.value.find { it.isDownloaded }
            if (downloadedModel != null) {
                _status.value = "Loading model: ${downloadedModel.name}..."
                val path = modelDownloader.getModelPath(downloadedModel.fileName)
                val isEmu = isLikelyEmulator()
                val threads = if (isEmu) 4 else Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                // Emulator fast mode: much smaller context to avoid long prefill time.
                // (x86_64 emulators are dramatically slower at prompt prefill than real phones.)
                val contextSize = if (isEmu) 256 else 2048
                val success = llamaEngine.loadModel(path, contextSize = contextSize, threads = threads)
                if (success) {
                    loadedModelFileName = downloadedModel.fileName
                    _status.value = if (downloadedModel.id == "smollm2-360m-mini") {
                        "PocketMind AI is ready. Download other AI models for better performance."
                    } else {
                        "PocketMind AI is ready."
                    }
                } else {
                    _status.value = "Failed to load model. Check storage."
                }
            } else {
                _status.value = "No model found. Please download one."
            }
        }
    }

    fun downloadModel(model: com.localai.chatbot.models.ModelInfo) {
        viewModelScope.launch {
            _availableModels.value = _availableModels.value.map { 
                if (it.id == model.id) it.copy(isDownloading = true, progress = 0f) else it 
            }
            
            val success = modelDownloader.downloadModel(model) { progress ->
                _availableModels.value = _availableModels.value.map {
                    if (it.id == model.id) it.copy(progress = progress) else it
                }
            }
            
            if (success) {
                refreshModelStates()
                autoLoadModel()
            } else {
                _availableModels.value = _availableModels.value.map {
                    if (it.id == model.id) it.copy(isDownloading = false) else it
                }
            }
        }
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            repository.getAllChats().collectLatest { chatList ->
                _chats.value = chatList
                if (chatList.isEmpty()) {
                    val newChat = repository.createChat("New Chat")
                    _currentChatId.value = newChat.id
                    observeMessages(newChat.id)
                } else if (_currentChatId.value == null) {
                    val latestChat = chatList.first()
                    _currentChatId.value = latestChat.id
                    observeMessages(latestChat.id)
                }
            }
        }
    }
    
    private fun observeMessages(chatId: String) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            repository.getMessages(chatId).collectLatest { msgs ->
                _messages.value = msgs

                // If the app was killed while generating, the last assistant message can remain "Thinking..." forever.
                // Repair those stale placeholders so the UI doesn't look stuck.
                val now = System.currentTimeMillis()
                msgs.asSequence()
                    .filter { it.role == Role.ASSISTANT && it.content == "Thinking..." }
                    .filter { now - it.timestamp > 30_000L }
                    .filter { repairedThinkingMessageIds.add(it.id) }
                    .forEach { stale ->
                        repository.addMessage(
                            stale.copy(content = "Previous response was interrupted. Please resend your last message.")
                        )
                    }

                if (msgs.isEmpty()) {
                    repository.addMessage(
                        Message(
                            chatId = chatId,
                            role = Role.ASSISTANT,
                            content = "Hello! I'm your offline AI assistant. SmolLM2 Mini is included for offline chat. For better performance and quality, open Manage Models and download a larger AI model (TinyLlama, Gemma, or Phi-2)."
                        )
                    )
                }
            }
        }
    }

    fun loadChat(chatId: String) {
        _currentChatId.value = chatId
        observeMessages(chatId)
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
            if (_currentChatId.value == chatId) {
                val remainingChats = _chats.value.filter { it.id != chatId }
                if (remainingChats.isNotEmpty()) {
                    loadChat(remainingChats.first().id)
                } else {
                    createNewChat()
                }
            }
        }
    }

    fun createNewChat() {
        viewModelScope.launch {
             val newChat = repository.createChat("New Chat")
             _currentChatId.value = newChat.id
             observeMessages(newChat.id)
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val chatId = _currentChatId.value ?: return

        val userMessage = Message(chatId = chatId, role = Role.USER, content = content.trim())
        
        viewModelScope.launch {
            // Memory Extraction Phase
            memoryExtractor.extractAndSaveMemory(userMessage.content)

            // Save user message
            repository.addMessage(userMessage)
            
            // Process to LLM
            processUserInput(userMessage)
        }
    }

    private fun processUserInput(userMessage: Message) {
        viewModelScope.launch {
            _isTyping.value = true
            
            // Image Generation Mock
            if (userMessage.content.lowercase().startsWith("/image ")) {
                val mockImageUrl = "https://picsum.photos/400/400?random=${System.currentTimeMillis()}"
                
                // Simulate AI processing delay for image
                delay(2000)
                
                val aiResponse = Message(
                    chatId = userMessage.chatId,
                    role = Role.ASSISTANT,
                    content = "Here is your generated image:",
                    imageUrl = mockImageUrl
                )
                
                repository.addMessage(aiResponse)
                _isTyping.value = false
                return@launch
            }
            
            _isTyping.value = true

            if (!llamaEngine.isModelLoaded()) {
                repository.addMessage(
                    Message(
                        chatId = userMessage.chatId,
                        role = Role.ASSISTANT,
                        content = "No AI model is loaded yet. Open Manage Models, download one, then try again."
                    )
                )
                _isTyping.value = false
                return@launch
            }

            // Deterministic offline answers for common queries (math, app model list, etc.).
            // This prevents obvious wrong replies from small models.
            LocalAnswerer.tryAnswer(userMessage.content)?.let { local ->
                repository.addMessage(
                    Message(
                        chatId = userMessage.chatId,
                        role = Role.ASSISTANT,
                        content = local
                    )
                )
                _isTyping.value = false
                return@launch
            }

            // Generate Prompt using stored memories
            val name = memoryManager.getMemory("name") ?: "Unknown"
            val university = memoryManager.getMemory("university") ?: "Unknown"
            val interests = memoryManager.getMemory("interests") ?: "Unknown"
            val userMemory = UserMemory(name, university, interests)
            
            val currentHistory = _messages.value
                .filterNot { it.id == userMessage.id }
                .filterNot { it.role == Role.ASSISTANT && it.content.equals("Thinking...", ignoreCase = true) }
                .let { history ->
                    // Emulator fast mode: keep fewer turns to reduce prompt prefill time.
                    if (isLikelyEmulator()) emptyList() else history
                }
            
            val fullPrompt = promptBuilder.buildPrompt(
                userMemory = userMemory,
                history = currentHistory,
                userInput = userMessage.content,
                modelFileName = loadedModelFileName
            )

            val aiResponseId = java.util.UUID.randomUUID().toString()
            val initialAiResponse = Message(
                id = aiResponseId,
                chatId = userMessage.chatId,
                role = Role.ASSISTANT,
                content = "Thinking..."
            )

            repository.addMessage(initialAiResponse)

            var aiResponseContent = ""
            var firstToken = true
            val isEmu = isLikelyEmulator()
            val maxTokens = if (isEmu) 32 else 192
            val timeoutMs = if (isEmu) 60_000L else 120_000L

            // Stream updates to UI so "Thinking..." doesn't sit there for 60s on slow devices/emulators.
            var lastUiUpdateMs = 0L
            suspend fun pushPartialToUi(force: Boolean = false) {
                val now = SystemClock.elapsedRealtime()
                if (!force && now - lastUiUpdateMs < 250L) return
                lastUiUpdateMs = now

                val partial = sanitizeModelOutput(aiResponseContent, userMessage.content)
                withContext(Dispatchers.IO) {
                    repository.updateMessageContent(
                        messageId = aiResponseId,
                        content = if (partial.isBlank()) "..." else partial
                    )
                }
            }

            val streamJob = launch {
                llamaEngine.generateStreaming(fullPrompt, maxTokens = maxTokens).collect { token ->
                    if (firstToken) {
                        aiResponseContent = ""
                        firstToken = false
                        // Remove "Thinking..." as soon as we start receiving output.
                        pushPartialToUi(force = true)
                    }
                    aiResponseContent += token
                    pushPartialToUi()
                }
            }
            try {
                // Wait for the streaming job with a timeout. Do not rely on native returning promptly.
                kotlinx.coroutines.withTimeout(timeoutMs) { streamJob.join() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                println("PocketMind_Log: ViewModel Timeout: ${e.message}")
                llamaEngine.cancelGeneration()
                streamJob.cancel()
                if (firstToken) {
                    aiResponseContent = if (isLikelyEmulator()) {
                        "Emulator fast mode timed out. Try a shorter question or use a real phone for full speed."
                    } else {
                        "This device is too slow for quality offline mode on the selected model. Use TinyLlama on a physical phone for faster replies."
                    }
                }
            } catch (e: Exception) {
                println("PocketMind_Log: ViewModel Error: ${e.message}")
                llamaEngine.cancelGeneration()
                streamJob.cancel()
                if (firstToken) {
                    aiResponseContent = "Offline generation failed. Please switch model and try again."
                }
            } finally {
                // The timeout exception cancels the coroutine; ensure we still persist the final message.
                withContext(NonCancellable) {
                    streamJob.cancel()
                    _isTyping.value = false
                    if (firstToken && aiResponseContent.isBlank()) {
                        aiResponseContent = "No output generated. Try TinyLlama model first."
                    }
                    val cleanedResponse = sanitizeModelOutput(aiResponseContent, userMessage.content)
                    val finalMessage = initialAiResponse.copy(
                        content = if (cleanedResponse.isBlank()) {
                            "I could not generate a clean response. Please retry with TinyLlama or Gemma."
                        } else {
                            cleanedResponse
                        }
                    )
                    repository.addMessage(finalMessage)
                }
            }
        }
    }

    private fun sanitizeModelOutput(raw: String, userInput: String): String {
        var text = raw.replace("\u0000", "").trim()
        if (text.isBlank()) return text

        text = extractTaggedAssistantText(text)

        if (text.startsWith(userInput, ignoreCase = true)) {
            text = text.removePrefix(userInput).trimStart('\n', '\r', ' ', ':')
        }

        val leadingMarkers = listOf(
            "<|assistant|>", "<|system|>", "<|user|>",
            "<start_of_turn>model", "<start_of_turn>user", "<start_of_turn>system",
            "Assistant:", "System:"
        )
        var changed = true
        while (changed) {
            changed = false
            for (marker in leadingMarkers) {
                if (text.startsWith(marker)) {
                    text = text.removePrefix(marker).trimStart('\n', '\r', ' ')
                    changed = true
                }
            }
        }

        val stopMarkers = listOf(
            "\n<|user|>", "\n<|assistant|>", "\n<|system|>",
            "\n<start_of_turn>user", "\n<start_of_turn>model", "\n<start_of_turn>system",
            "\nUser:", "\nSystem:"
        )
        val stopIndex = stopMarkers
            .map { marker -> text.indexOf(marker) }
            .filter { it >= 0 }
            .minOrNull()

        if (stopIndex != null) {
            text = text.substring(0, stopIndex).trim()
        }

        return text
            .replace("<end_of_turn>", "")
            .replace("<|end|>", "")
            // Remove any leaked/incomplete role tags (some models emit partial markers).
            .replace(Regex("<\\|(system|user|assistant)(\\|>)?"), "")
            .replace(Regex("<start_of_turn>(system|user|model)"), "")
            .trim()
    }

    private fun extractTaggedAssistantText(text: String): String {
        val markerRegex = Regex("(<\\|(system|user|assistant)\\|>|<start_of_turn>(system|user|model))")
        val matches = markerRegex.findAll(text).toList()
        if (matches.isEmpty()) return text

        val assistantChunks = mutableListOf<String>()
        for ((index, match) in matches.withIndex()) {
            val marker = match.value
            val role = when {
                marker.contains("<|assistant|>") -> "assistant"
                marker.contains("<start_of_turn>model") -> "assistant"
                marker.contains("<|user|>") || marker.contains("<start_of_turn>user") -> "user"
                else -> "system"
            }

            if (role == "assistant") {
                val start = match.range.last + 1
                val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
                if (start in 0..text.length && end in 0..text.length && start < end) {
                    val chunk = text.substring(start, end).trim()
                    if (chunk.isNotBlank()) assistantChunks.add(chunk)
                 }
             }
         }

         return assistantChunks.lastOrNull() ?: text
     }

     private fun isLikelyEmulator(): Boolean {
         val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
         val model = Build.MODEL.lowercase(Locale.ROOT)
         val product = Build.PRODUCT.lowercase(Locale.ROOT)
         return fingerprint.contains("generic") ||
             fingerprint.contains("emulator") ||
             model.contains("emulator") ||
             model.contains("sdk") ||
             product.contains("sdk")
     }
 }
