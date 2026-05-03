package com.localai.chatbot.prompt

import com.localai.chatbot.models.Message
import com.localai.chatbot.models.Role

class PromptBuilder {

    fun buildPrompt(
        systemInstruction: String = "You are a helpful AI assistant.",
        userMemory: UserMemory,
        history: List<Message>,
        userInput: String,
        modelFileName: String? = null
    ): String {
        val contextLine = buildString {
            append(systemInstruction)
            if (userMemory.name != "Unknown") append(" User name: ${userMemory.name}.")
            if (userMemory.university != "Unknown") append(" University: ${userMemory.university}.")
            if (userMemory.interests != "Unknown") append(" Interests: ${userMemory.interests}.")
        }
        val recentHistory = history
            .filter { it.content.isNotBlank() }
            .filterNot { it.content == "..." || it.content.equals("Thinking...", ignoreCase = true) }
            .takeLast(6)
        val model = modelFileName?.lowercase().orEmpty()
        return when {
            model.contains("gemma") -> buildGemmaPrompt(contextLine, recentHistory, userInput)
            model.contains("tinyllama") -> buildChatMlPrompt(contextLine, recentHistory, userInput)
            model.contains("smollm") -> buildChatMlPrompt(contextLine, recentHistory, userInput)
            model.contains("phi-2") || model.contains("phi2") -> buildPhi2Prompt(contextLine, recentHistory, userInput)
            model.contains("phi") -> buildChatMlPrompt(contextLine, recentHistory, userInput)
            else -> buildPlainPrompt(contextLine, recentHistory, userInput)
        }
    }

    private fun buildGemmaPrompt(context: String, history: List<Message>, userInput: String): String = buildString {
        // Gemma instruction-tuned templates do not use the "system" role directly.
        append("<start_of_turn>user\n")
        append("Instruction: ")
        append(context)
        append("<end_of_turn>\n")
        history.forEach { msg ->
            val role = if (msg.role == Role.USER) "user" else "model"
            append("<start_of_turn>$role\n")
            append(msg.content)
            append("<end_of_turn>\n")
        }
        append("<start_of_turn>user\n")
        append(userInput)
        append("<end_of_turn>\n")
        append("<start_of_turn>model\n")
    }

    private fun buildChatMlPrompt(context: String, history: List<Message>, userInput: String): String = buildString {
        append("<|system|>\n")
        append(context)
        append("\n")
        history.forEach { msg ->
            val role = if (msg.role == Role.USER) "<|user|>" else "<|assistant|>"
            append(role)
            append("\n")
            append(msg.content.trim())
            append("\n")
        }
        append("<|user|>\n")
        append(userInput.trim())
        append("\n<|assistant|>\n")
    }

    private fun buildPhi2Prompt(context: String, history: List<Message>, userInput: String): String = buildString {
        // Phi-2 GGUFs often behave better with a minimal "QA" style prompt.
        // Keep it stable and avoid mixing with ChatML markers.
        append("You are a helpful assistant.\n")
        append(context)
        append("\n\n")
        history.forEach { msg ->
            val role = if (msg.role == Role.USER) "Q" else "A"
            append("$role: ${msg.content.trim()}\n")
        }
        append("Q: ")
        append(userInput.trim())
        append("\nA:")
    }

    private fun buildPlainPrompt(context: String, history: List<Message>, userInput: String): String = buildString {
        append("System: ")
        append(context)
        append("\n\n")
        history.forEach { msg ->
            val role = if (msg.role == Role.USER) "User" else "Assistant"
            append("$role: ${msg.content}\n")
        }
        append("User: $userInput\nAssistant:")
    }
}
