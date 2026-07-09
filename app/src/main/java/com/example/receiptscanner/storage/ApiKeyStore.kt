package com.example.receiptscanner.storage

import android.content.Context
import com.example.receiptscanner.ai.AiEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ApiKeyEntry(
    val key: String,
    val consecutiveFailures: Int = 0,
    val lastFailureAt: Long = 0L
)

/**
 * يخزّن قائمة مفاتيح (وليس مفتاحاً واحداً) لكل محرك + المحرك النشط، كل قيمة
 * مشفَّرة عبر SecureStorage. يتتبّع فشل كل مفتاح على حدة لتفعيل التدوير
 * التلقائي: بعد FAILURE_THRESHOLD فشلاً متتالياً لمفتاح معيّن، يتخطاه النظام
 * تلقائياً للمفتاح التالي (أو المحرك التالي إن لم يتبقَّ مفتاح صالح)، مع
 * تصفير تلقائي للعداد بعد مرور 24 ساعة (تحسّباً لحصص تتجدد يومياً).
 */
object ApiKeyStore {
    const val FAILURE_THRESHOLD = 5
    private const val FAILURE_RESET_WINDOW_MS = 24 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }

    private fun keysFile(context: Context, engine: AiEngine) =
        File(context.filesDir, "api_keys_${engine.name.lowercase()}.enc")

    private fun activeEngineFile(context: Context) =
        File(context.filesDir, "active_engine.enc")

    fun getKeys(context: Context, engine: AiEngine): List<ApiKeyEntry> {
        val text = SecureStorage.readAndDecrypt(keysFile(context, engine)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ApiKeyEntry>>(text) }.getOrDefault(emptyList())
    }

    private fun saveKeys(context: Context, engine: AiEngine, keys: List<ApiKeyEntry>) {
        SecureStorage.encryptAndWrite(keysFile(context, engine), json.encodeToString(keys))
    }

    /** يستبدل كل مفاتيح هذا المحرك دفعة واحدة (يُستخدم من شاشة الإعدادات - حقل متعدد الأسطر). */
    fun replaceKeys(context: Context, engine: AiEngine, rawKeys: List<String>) {
        val existing = getKeys(context, engine).associateBy { it.key }
        val cleaned = rawKeys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        // احتفظ بعداد الفشل للمفاتيح الموجودة مسبقاً؛ مفتاح جديد يبدأ من صفر
        val updated = cleaned.map { key -> existing[key] ?: ApiKeyEntry(key = key) }
        saveKeys(context, engine, updated)
    }

    fun hasAnyKey(context: Context, engine: AiEngine): Boolean = getKeys(context, engine).isNotEmpty()

    /** أول مفتاح "صالح للاستخدام" (لم يتجاوز حد الفشل، أو تجاوز نافذة إعادة التصفير). */
    fun nextUsableKey(context: Context, engine: AiEngine): String? {
        val now = System.currentTimeMillis()
        return getKeys(context, engine).firstOrNull { entry ->
            entry.consecutiveFailures < FAILURE_THRESHOLD ||
                (now - entry.lastFailureAt) > FAILURE_RESET_WINDOW_MS
        }?.key
    }

    fun recordSuccess(context: Context, engine: AiEngine, usedKey: String) {
        val keys = getKeys(context, engine)
        val updated = keys.map { if (it.key == usedKey) it.copy(consecutiveFailures = 0) else it }
        if (updated != keys) saveKeys(context, engine, updated)
    }

    fun recordFailure(context: Context, engine: AiEngine, usedKey: String) {
        val now = System.currentTimeMillis()
        val keys = getKeys(context, engine)
        val updated = keys.map {
            if (it.key == usedKey) {
                // نافذة 24 ساعة انقضت منذ آخر فشل؟ ابدأ العدّ من جديد بدل التراكم من فشل قديم
                val baseline = if (now - it.lastFailureAt > FAILURE_RESET_WINDOW_MS) 0 else it.consecutiveFailures
                it.copy(consecutiveFailures = baseline + 1, lastFailureAt = now)
            } else it
        }
        saveKeys(context, engine, updated)
    }

    fun getActiveEngine(context: Context): AiEngine =
        AiEngine.fromNameOrDefault(SecureStorage.readAndDecrypt(activeEngineFile(context)))

    fun setActiveEngine(context: Context, engine: AiEngine) {
        SecureStorage.encryptAndWrite(activeEngineFile(context), engine.name)
    }
}
