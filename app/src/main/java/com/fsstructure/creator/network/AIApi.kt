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

class AIApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MODEL = "gemini-3.5-flash"

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

    suspend fun fetchResponse(
        apiKey: String,
        messages: List<Message>,
        errorContext: String? = null
    ): AIResponse {
        return withContext(Dispatchers.IO) {
            val jsonBody = buildJsonBody(messages, errorContext)
            val urlWithKey = "${API_URL}$MODEL:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(urlWithKey)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw Exception("API Error: ${response.code}. $errorBody")
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response from API")

                parseAIResponse(responseBody)
            }
        }
    }

    private fun buildJsonBody(messages: List<Message>, errorContext: String?): String {
        val contentsArray = JSONArray()

        for (msg in messages) {
            val role = if (msg.role == "assistant") "model" else "user"
            val content = JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            }
            contentsArray.put(content)
        }

        if (!errorContext.isNullOrBlank()) {
            val errorContent = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "Previous execution result: $errorContext. Please inform the user and ask how to proceed.")))
            }
            contentsArray.put(errorContent)
        }

        val requestBody = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        return requestBody.toString()
    }

    private fun parseAIResponse(rawResponse: String): AIResponse {
        val apiJson = JSONObject(rawResponse)
        val contentStr = apiJson.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        // Clean potential markdown wrappers
        var jsonString = contentStr
        if (jsonString.startsWith("```json")) {
            jsonString = jsonString.substring(7)
        } else if (jsonString.startsWith("```")) {
            jsonString = jsonString.substring(3)
        }
        if (jsonString.endsWith("```")) {
            jsonString = jsonString.dropLast(3)
        }
        jsonString = jsonString.trim()

        val operations = mutableListOf<FsOperation>()
        val message: String

        try {
            // Attempt strict JSON parsing
            val aiJson = JSONObject(jsonString)
            message = aiJson.optString("message", "")

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
        } catch (e: Exception) {
            // Fallback if AI makes a JSON syntax error (like missing a comma)
            val messageRegex = """"message"\s*:\s*"(.*?)"\s*""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = messageRegex.find(jsonString)
            message = match?.groupValues?.get(1) ?: "I'm ready to help you create your structure. What would you like to build?"
        }

        return AIResponse(message = message, operations = operations)
    }
}