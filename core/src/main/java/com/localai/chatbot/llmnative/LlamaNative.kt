package com.localai.chatbot.llmnative

class LlamaNative {
    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }

    interface TokenCallback {
        fun onTokenGenerated(token: String)
    }

    external fun loadModel(modelPath: String, contextSize: Int, threads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int): String
    external fun generateStreaming(prompt: String, maxTokens: Int, callback: TokenCallback)
    external fun cancelGeneration()
}
