package com.example.receiptscanner.ai

import android.util.Base64
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

/** يقرأ الصورة/PDF مباشرة عبر Claude API - PDF مدعوم أصلياً (لا حاجة لتحويله لصورة). */
class ClaudeParser : AiReceiptParser {
    override val engine = AiEngine.CLAUDE

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        // تحقّق من الاسم الحالي في docs.claude.com قبل الاستخدام طويل المدى
        private const val MODEL = "claude-sonnet-5"
        private const val PDF_BETA_HEADER = "pdfs-2024-09-25"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun extract(file: File, apiKey: String, isPdf: Boolean): ParsedFields? =
        withContext(Dispatchers.IO) {
            try {
                // PDF يُرسَل أصلياً كما هو (أدق من إعادة تصييره كصورة). الصور فقط
                // تمر بتحسين تباين بسيط أولاً (ImagePreparer) قبل الإرسال.
                val base64Data = if (isPdf) {
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                } else {
                    Base64.encodeToString(ImagePreparer.jpegBytesFor(file, isPdf = false), Base64.NO_WRAP)
                }

                val mediaBlock = buildJsonObject {
                    put("type", if (isPdf) "document" else "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", if (isPdf) "application/pdf" else "image/jpeg")
                        put("data", base64Data)
                    }
                }

                val requestJson = buildJsonObject {
                    put("model", MODEL)
                    put("max_tokens", 300)
                    putJsonArray("messages") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(mediaBlock)
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", AiPrompt.INSTRUCTION)
                                })
                            }
                        })
                    }
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(API_URL)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(body)
                if (isPdf) requestBuilder.addHeader("anthropic-beta", PDF_BETA_HEADER)

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val responseText = response.body?.string() ?: return@withContext null
                    val root = Json.parseToJsonElement(responseText).jsonObject
                    val content = root["content"]?.jsonArray ?: return@withContext null
                    val text = content.firstOrNull {
                        it.jsonObject["type"]?.jsonPrimitive?.content == "text"
                    }?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return@withContext null
                    AiPrompt.parseJsonResponse(text)
                }
            } catch (e: Exception) {
                null
            }
        }
}
