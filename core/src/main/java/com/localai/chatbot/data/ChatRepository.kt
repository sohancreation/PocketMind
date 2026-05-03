package com.localai.chatbot.data

import com.localai.chatbot.database.ChatDao
import com.localai.chatbot.database.ChatEntity
import com.localai.chatbot.database.MessageDao
import com.localai.chatbot.database.MessageEntity
import com.localai.chatbot.models.Chat
import com.localai.chatbot.models.Message
import com.localai.chatbot.models.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {

    fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChatsWithPreview().map { tuples ->
            tuples.map { tuple ->
                Chat(
                    id = tuple.chatId,
                    title = tuple.title,
                    createdAt = tuple.createdAt,
                    lastMessage = tuple.lastMessageContent
                )
            }
        }
    }

    suspend fun createChat(title: String): Chat {
        val chat = Chat(title = title)
        chatDao.insertChat(
            ChatEntity(
                chatId = chat.id,
                title = chat.title,
                createdAt = chat.createdAt
            )
        )
        return chat
    }

    suspend fun deleteChat(chatId: String) {
        chatDao.deleteChat(chatId)
    }

    fun getMessages(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesByChatId(chatId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    chatId = entity.chatId,
                    role = Role.valueOf(entity.role),
                    content = entity.content,
                    imageUrl = entity.imageUrl,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    suspend fun addMessage(message: Message) {
        messageDao.insertMessage(
            MessageEntity(
                id = message.id,
                chatId = message.chatId,
                role = message.role.name,
                content = message.content,
                imageUrl = message.imageUrl,
                timestamp = message.timestamp
            )
        )
    }

    suspend fun updateMessageContent(messageId: String, content: String) {
        messageDao.updateMessageContent(id = messageId, content = content)
    }
}
