package com.localai.chatbot.memory

class MemoryExtractor(private val memoryManager: MemoryManager) {

    // A simple regex-based extractor
    suspend fun extractAndSaveMemory(userInput: String) {
        // "My name is ..."
        val nameMatch = Regex("(?i)my name is ([a-zA-Z\\s]+)").find(userInput)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim()
            memoryManager.saveMemory("name", name)
        }

        // "I study at ..."
        val universityMatch = Regex("(?i)i study at ([a-zA-Z\\s]+)").find(userInput)
        if (universityMatch != null) {
            val university = universityMatch.groupValues[1].trim()
            memoryManager.saveMemory("university", university)
        }

        // "I like ..."
        val interestsMatch = Regex("(?i)i like ([a-zA-Z\\s,]+)").find(userInput)
        if (interestsMatch != null) {
            val interests = interestsMatch.groupValues[1].trim()
            
            // For interests, we might want to append rather than overwrite.
            // But simple overwrite works per the requirements. Or we append.
            val existingInterests = memoryManager.getMemory("interests")
            val newInterests = if (!existingInterests.isNullOrBlank()) {
                val current = existingInterests.split(", ").toMutableSet()
                current.add(interests)
                current.joinToString(", ")
            } else {
                interests
            }
            memoryManager.saveMemory("interests", newInterests)
        }
    }
}
