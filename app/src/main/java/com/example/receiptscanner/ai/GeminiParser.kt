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
 * يستخدم Gemini REST API مباشرة (generateContent). PDF غير مُرسَل أصلياً
 * بهذا التكامل - تُحوَّل أول صفحة منه لصورة عبر ImagePreparer.
 */
class GeminiParser : AiReceiptParser {
    override val engine = AiEngine.GEMINI

    companion object {
        private const val MODEL = "gemini-2.5-flash"
        private fun url(apiKey: String) =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
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

                val requestJson = buildJsonObject {
                    putJsonArray("contents") {
                        add(buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject {
                                    putJsonObject("inline_data") {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Data)
                                    }
                                })
                                add(buildJsonObject { put("text", AiPrompt.INSTRUCTION) })
                            }
                        })
                    }
                    putJsonObject("generationConfig") {
                        put("responseMimeType", "application/json")
                    }
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url(apiKey))
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val responseText = response.body?.string() ?: return@withContext null
                    val root = Json.parseToJsonElement(responseText).jsonObject
                    val text = root["candidates"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: return@withContext null
                    AiPrompt.parseJsonResponse(text)
                }
            } catch (e: Exception) {
                null
            }
        }
}
