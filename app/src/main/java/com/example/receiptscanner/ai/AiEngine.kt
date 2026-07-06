package com.example.receiptscanner.ai

/** المحركات المدعومة. القيمة المخزَّنة بالإعدادات هي name (مثلاً "GEMINI"). */
enum class AiEngine(val displayName: String) {
    CLAUDE("Claude"),
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    HUGGINGFACE("Hugging Face");

    companion object {
        fun fromNameOrDefault(name: String?): AiEngine =
            entries.find { it.name == name } ?: CLAUDE
    }
}
