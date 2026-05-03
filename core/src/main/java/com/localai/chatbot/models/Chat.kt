package com.localai.chatbot.models

data class Chat(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessage: String? = null
)
