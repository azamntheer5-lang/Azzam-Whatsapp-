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

/**
 * Hugging Face عبر موجّه "Inference Providers" الموحَّد المتوافق مع OpenAI
 * (router.huggingface.co) - هذا أحدث معمارية HF الرسمية، ويوجّه تلقائياً
 * لأسرع مزوّد متاح لهذا الموديل. الأقل اختباراً بين المحركات الأربعة نظراً
 * لتنوّع النماذج على هذه المنصة، فاخترنا موديلاً مؤكَّداً لدعم الصور.
 */
class HuggingFaceParser : AiReceiptParser {
    override val engine = AiEngine.HUGGINGFACE

    companion object {
        private const val API_URL = "https://router.huggingface.co/v1/chat/completions"
        private const val MODEL = "meta-llama/Llama-3.2-11B-Vision-Instruct"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // بعض مزوّدي HF أبطأ عند "تشغيل" نموذج غير نشط
        .build()

    override suspend fun extract(file: File, apiKey: String, isPdf: Boolean): EngineCallResult =
        withContext(Dispatchers.IO) {
            try {
                val jpegBytes = ImagePreparer.jpegBytesFor(file, isPdf)
                val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64Data"

                val requestJson = buildJsonObject {
                    put("model", MODEL)
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
                    if (!response.isSuccessful) return@withContext EngineCallResult(null, apiCallSucceeded = false)
                    val responseText = response.body?.string()
                        ?: return@withContext EngineCallResult(null, apiCallSucceeded = true)
                    val root = Json.parseToJsonElement(responseText).jsonObject
                    val text = root["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content
                    EngineCallResult(text?.let { AiPrompt.parseJsonResponse(it) }, apiCallSucceeded = true)
                }
            } catch (e: Exception) {
                EngineCallResult(null, apiCallSucceeded = false)
            }
        }
}
