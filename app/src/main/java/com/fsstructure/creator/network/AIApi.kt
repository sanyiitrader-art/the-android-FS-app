package com.fsstructure.creator.network

import com.fsstructure.creator.data.AIResponse
import com.fsstructure.creator.data.FsOperation
import com.fsstructure.creator.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The networking layer responsible for communicating with the AI API.
 * Uses OkHttp for lightweight, fast, and asynchronous network operations.
 * The AI is strictly instructed to return a JSON object containing both a natural
 * language message and a list of standardized filesystem operations.
 */
class AIApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        // Using a standard OpenAI-compatible endpoint structure.
        // The user's API key authorizes this.
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o" // Or any compatible model the user's key supports

        // The strict system prompt that enforces application rules.
        private val SYSTEM_PROMPT = """
            You are the FS Structure Creator, an AI-powered filesystem structure creation assistant.
            Your ONLY purpose is to help the user create folders/directories and empty files.
            
            STRICT RULES:
            1. NEVER write contents into files. If the user asks to write content, explain that you can only create the empty file.
            2. NEVER invent functionality, add recommendations, or create files/folders the user did not explicitly request.
            3. File names are literal. The filesystem engine does not have hardcoded file types.
            4. You must convert the user's natural language request into a standardized JSON operation format alongside your conversational response.
            5. If there are filesystem errors from previous operations, explain them to the user naturally and wait for their correction.
            
            You MUST respond with valid JSON ONLY, using this exact structure:
            {
              "message": "Your natural language response to the user.",
              "operations": [
                {"type": "CreateDirectory", "path": "path/to/folder"},
                {"type": "CreateEmptyFile", "path": "path/to/folder/file.ext"}
              ]
            }
            
            If no operations are needed (e.g., just answering a question or acknowledging readiness), return an empty "operations" array.
            Do NOT wrap the JSON in markdown code blocks. Return raw JSON.
        """.trimIndent()
    }

    /**
     * Sends the conversation history to the AI API and parses the response.
     * @param apiKey The user's active API key.
     * @param messages The conversation history including the latest user input/attachments.
     * @param errorContext Optional string containing filesystem errors from the previous execution attempt.
     * @return AIResponse containing the natural language message and standardized operations.
     */
    suspend fun fetchResponse(
        apiKey: String,
        messages: List<Message>,
        errorContext: String? = null
    ): AIResponse {
        return withContext(Dispatchers.IO) {
            val jsonBody = buildJsonBody(messages, errorContext)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("API Error: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response from API")

                parseAIResponse(responseBody)
            }
        }
    }

    private fun buildJsonBody(messages: List<Message>, errorContext: String?): String {
        val messagesArray = JSONArray()

        // System Prompt
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        })

        // Error Context (if any execution errors occurred, inject as system message)
        if (!errorContext.isNullOrBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "Previous execution result: $errorContext. Please inform the user and ask how to proceed.")
            })
        }

        // Conversation History
        for (msg in messages) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
            put("temperature", 0.2) // Low temperature for strict, deterministic JSON output
        }

        return requestBody.toString()
    }

    private fun parseAIResponse(rawResponse: String): AIResponse {
        // Extract the actual JSON content from the API's chat completion wrapper
        val apiJson = JSONObject(rawResponse)
        val contentStr = apiJson.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        // Parse the AI's specific JSON structure
        val aiJson = JSONObject(contentStr.trim())

        val message = aiJson.optString("message", "")
        val operations = mutableListOf<FsOperation>()

        val opsArray = aiJson.optJSONArray("operations")
        if (opsArray != null) {
            for (i in 0 until opsArray.length()) {
                val op = opsArray.getJSONObject(i)
                val type = op.getString("type")
                val path = op.getString("path")
                when (type) {
                    "CreateDirectory" -> operations.add(FsOperation.CreateDirectory(path))
                    "CreateEmptyFile" -> operations.add(FsOperation.CreateEmptyFile(path))
                }
            }
        }

        return AIResponse(message = message, operations = operations)
    }
}