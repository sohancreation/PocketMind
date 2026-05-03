package com.localai.chatbot.models

enum class Role {
    USER, ASSISTANT, SYSTEM
}

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chatId: String,
    val role: Role,
    val content: String,
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
