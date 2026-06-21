package com.example.b3check

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DesktopAiService : AiService {
    private val gson = Gson()

    override suspend fun ask(ticker: String, question: String, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            // Modelo Gemini Pro no canal v1: O setup mais estável e compatível globalmente para evitar o erro 404
            val url = URL("https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            val prompt = "Ativo: $ticker. Pergunta: $question. Responda de forma curta e objetiva."
            val body = mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                )
            )

            connection.outputStream.use { it.write(gson.toJson(body).toByteArray()) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = gson.fromJson(response, Map::class.java)
                val candidates = jsonResponse["candidates"] as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                firstPart?.get("text") as? String ?: "Sem resposta"
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Erro: ${connection.responseCode}. Detalhes: $errorBody"
            }
        } catch (e: Exception) {
            "Erro: ${e.localizedMessage}"
        }
    }
}

actual fun getAiService(): AiService = DesktopAiService()
