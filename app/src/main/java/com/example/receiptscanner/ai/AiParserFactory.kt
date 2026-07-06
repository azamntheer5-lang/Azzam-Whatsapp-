package com.example.receiptscanner.ai

object AiParserFactory {
    fun create(engine: AiEngine): AiReceiptParser = when (engine) {
        AiEngine.CLAUDE -> ClaudeParser()
        AiEngine.GEMINI -> GeminiParser()
        AiEngine.GROQ -> GroqParser()
        AiEngine.HUGGINGFACE -> HuggingFaceParser()
    }
}
