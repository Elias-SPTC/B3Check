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
            // Modelo Gemini 2.5 Flash: Versão identificada com cota disponível no console, otimizando o acesso analítico
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val prompt = if (ticker == "GLOBAL") question else "Ativo: $ticker. Pergunta: $question. Responda de forma curta e objetiva."
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
