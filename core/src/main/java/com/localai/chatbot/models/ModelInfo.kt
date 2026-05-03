package com.localai.chatbot.models

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val size: String,
    val fileName: String,
    var isDownloaded: Boolean = false,
    var progress: Float = 0f,
    var isDownloading: Boolean = false
)

object ModelList {
    val models = listOf(
        ModelInfo(
            id = "smollm2-360m-mini",
            name = "SmolLM2 360M (Mini)",
            description = "Built-in offline model for basic Q&A. Good for emulator/low-end devices.",
            url = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf?download=true",
            size = "270 MB",
            fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        ),
        ModelInfo(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B",
            description = "Lightweight and fast, ideal for mid-range phones.",
            url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf?download=true",
            size = "669 MB",
            fileName = "tinyllama-1.1b-chat.Q4_K_M.gguf"
        ),
        ModelInfo(
            id = "phi-2-2.7b",
            name = "Microsoft Phi-2 (2.7B)",
            description = "High quality reasoning. Requires 8GB+ RAM.",
            url = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf?download=true",
            size = "1.6 GB",
            fileName = "phi-2.Q4_K_M.gguf"
        ),
        ModelInfo(
            id = "gemma-2b",
            name = "Google Gemma 2B",
            description = "Google's optimized mobile AI model.",
            url = "https://huggingface.co/lmstudio-ai/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-q4_k_m.gguf?download=true",
            size = "1.5 GB",
            fileName = "gemma-2b-it-q4_k_m.gguf"
        )
    )
}
