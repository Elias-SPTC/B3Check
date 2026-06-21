package com.example.b3check

interface AiService {
    suspend fun ask(ticker: String, question: String, apiKey: String): String
}

expect fun getAiService(): AiService
