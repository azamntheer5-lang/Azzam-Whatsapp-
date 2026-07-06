package com.example.receiptscanner.ai

import android.util.Base64
import com.example.receiptscanner.parser.ParsedFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Groq بصيغة متوافقة مع OpenAI. PDF غير مدعوم أصلياً - يُحوَّل لصورة أولاً.
 * الموديل المستخدَم هو موديل Groq الرسمي متعدد الوسائط (نص + صورة).
 */
class GroqParser : AiReceiptParser {
    override val engine = AiEngine.GROQ

    companion object {
        private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun extract(file: File, apiKey: String, isPdf: Boolean): ParsedFields? =
        withContext(Dispatchers.IO) {
            try {
                val jpegBytes = ImagePreparer.jpegBytesFor(file, isPdf)
                val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64Data"

                val requestJson = buildJsonObject {
                    put("model", MODEL)
                    put("temperature", 0)
                    putJsonObject("response_format") { put("type", "json_object") }
                    putJsonArray("messages") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(buildJsonObject { put("type", "text"); put("text", AiPrompt.INSTRUCTION) })
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", dataUrl) }
                                })
                            }
                        })
                    }
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val responseText = response.body?.string() ?: return@withContext null
                    val root = Json.parseToJsonElement(responseText).jsonObject
                    val text = root["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content
                        ?: return@withContext null
                    AiPrompt.parseJsonResponse(text)
                }
            } catch (e: Exception) {
                null
            }
        }
}
